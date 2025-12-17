package tr.testodasi.heuristic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HeuristicSolver {
  private final Scheduler scheduler = new Scheduler();
  private final boolean verbose;

  public HeuristicSolver() {
    this(false);
  }

  public HeuristicSolver(boolean verbose) {
    this.verbose = verbose;
  }

  public List<Solution> solve() {
    List<Project> projects = Data.buildProjects(2);

    List<Solution> solutions = new ArrayList<>();
    Map<String, Env> prevRoom = null;

    List<Project> current = deepCopy(projects);

    for (int iter = 1; iter <= 5; iter++) {
      Map<String, Env> room = stage1_assignRooms(current);

      // Eğer oda setleri artık değişmiyorsa sabit noktaya geldik: tekrar üretmek yerine dur.
      if (prevRoom != null && prevRoom.equals(room)) {
        if (verbose) {
          System.out.println("INFO: Stage3 converged (room set unchanged). Stopping at iter=" + (iter - 1));
        }
        break;
      }

      // Stage2: EDD scheduling + sample artırma
      List<Project> improved = stage2_increaseSamples(room, current);
      Scheduler.EvalResult eval = scheduler.evaluate(improved, room);

      // Ek iyileştirme: proje sırasını local search ile iyileştir (EDD tabanlı).
      if (Data.ENABLE_ORDER_LOCAL_SEARCH && Data.PROJECT_DISPATCH_RULE == Data.ProjectDispatchRule.EDD) {
        Scheduler.EvalResult lsEval = improveOrderByLocalSearch(improved, room, eval.totalLateness);
        if (lsEval.totalLateness < eval.totalLateness) {
          if (verbose) {
            System.out.println("INFO: Order local-search improved total lateness: " +
                eval.totalLateness + " -> " + lsEval.totalLateness);
          }
          eval = lsEval;
        } else if (verbose) {
          System.out.println("INFO: Order local-search no improvement (baseline=" + eval.totalLateness + ")");
        }
      }

      solutions.add(new Solution(iter, eval.totalLateness, deepCopy(improved), room, eval.projectResults, eval.schedule));

      prevRoom = room;
      current = deepCopy(improved);
    }

    return solutions;
  }

  private List<Project> stage2_increaseSamples(Map<String, Env> room, List<Project> startProjects) {
    List<Project> current = deepCopy(startProjects);

    Scheduler.EvalResult baseEval = scheduler.evaluate(current, room);
    if (verbose) {
      System.out.println("INFO: Stage2 initial total lateness = " + baseEval.totalLateness);
    }

    while (true) {
      int bestImprovement = 0;
      int bestProjectIdx = -1;
      Scheduler.EvalResult bestEval = null;

      for (int i = 0; i < current.size(); i++) {
        List<Project> cand = deepCopy(current);
        cand.get(i).samples += 1;

        Scheduler.EvalResult e = scheduler.evaluate(cand, room);
        int improvement = baseEval.totalLateness - e.totalLateness;
        if (improvement > bestImprovement) {
          bestImprovement = improvement;
          bestProjectIdx = i;
          bestEval = e;
        }
      }

      if (bestImprovement <= 0 || bestProjectIdx < 0 || bestEval == null) {
        if (verbose) {
          System.out.println("INFO: Stage2 no further improvement. Final total lateness = " + baseEval.totalLateness);
        }
        break;
      }

      current.get(bestProjectIdx).samples += 1;
      if (verbose) {
        Project p = current.get(bestProjectIdx);
        System.out.println(
            "INFO: Stage2 accept +1 sample => " + p.id +
                " samples=" + p.samples +
                " improvement=" + bestImprovement +
                " newTotal=" + bestEval.totalLateness
        );
      }
      baseEval = bestEval;
    }

    return current;
  }

  /**
   * Aşama 1: Oda set değerlerini belirle (sıcaklık/nem sabit kalır).
   * Basit yük dengeleme heuristiği:
   * - Voltaj ihtiyacı olan iş yükünü önce voltajlı odalara dağıt.
   * - 85% nem gerektiren işler sadece humAdj odalara atanabilir.
   */
  private Map<String, Env> stage1_assignRooms(List<Project> projects) {
    Objects.requireNonNull(projects);

    Map<Env, Long> demandTotal = new HashMap<>();
    Map<Env, Long> demandVolt = new HashMap<>();

    // İş listesi -> env bazlı toplam iş yükü (jobCount * durationDays).
    // Bu set sadece gerçekten talep olan env'leri içerir (dengeyi bozan gereksiz atamaları engeller).
    Set<Env> demandedEnvs = new LinkedHashSet<>();

    for (Project p : projects) {
      for (int ti = 0; ti < Data.TESTS.size(); ti++) {
        if (!p.required[ti]) continue;
        TestDef t = Data.TESTS.get(ti);

        int jobs = 1;
        if (t.category == TestCategory.PULLDOWN) jobs = p.samples;

        long w = (long) jobs * (long) t.durationDays;
        // Pulldown için ekstra ağırlık kullanılmıyor.
        demandTotal.merge(t.env, w, Long::sum);
        if (p.needsVoltage) demandVolt.merge(t.env, w, Long::sum);
        demandedEnvs.add(t.env);
      }
    }

    if (demandedEnvs.isEmpty()) {
      throw new IllegalStateException("No demanded environments; check project test matrix.");
    }

    // assigned station counts per env
    Map<Env, Integer> assignedStationsTotal = new HashMap<>();
    Map<Env, Integer> assignedStationsVolt = new HashMap<>();

    List<ChamberSpec> voltRooms = Data.CHAMBERS.stream().filter(c -> c.voltageCapable)
        .sorted(Comparator.comparingInt((ChamberSpec c) -> c.stations).reversed())
        .toList();
    List<ChamberSpec> nonVoltRooms = Data.CHAMBERS.stream().filter(c -> !c.voltageCapable)
        .sorted(Comparator.comparingInt((ChamberSpec c) -> c.stations).reversed())
        .toList();

    Map<String, Env> assignment = new LinkedHashMap<>();

    // 1) volt rooms
    for (ChamberSpec c : voltRooms) {
      Env best = pickBestEnv(c, demandedEnvs, demandVolt, demandTotal, assignedStationsVolt, assignedStationsTotal, true);
      assignment.put(c.id, best);
      assignedStationsTotal.merge(best, c.stations, Integer::sum);
      assignedStationsVolt.merge(best, c.stations, Integer::sum);
    }

    // 2) non-volt rooms
    for (ChamberSpec c : nonVoltRooms) {
      // non-volt room'lar 85% destekliyorsa yine seçebilir; voltaj iş yükü zaten volt odalara gitti.
      Env best = pickBestEnv(c, demandedEnvs, demandTotal, demandTotal, assignedStationsTotal, assignedStationsTotal, false);
      assignment.put(c.id, best);
      assignedStationsTotal.merge(best, c.stations, Integer::sum);
    }

    // Feasibility repair: voltaj ihtiyacı olan env'ler için en az 1 voltajlı oda olmalı
    for (Env env : demandedEnvs) {
      long dv = demandVolt.getOrDefault(env, 0L);
      if (dv <= 0) continue;
      int asg = assignedStationsVolt.getOrDefault(env, 0);
      if (asg > 0) continue;

      // bir volt odasını bu env'e çek
      String bestChId = null;
      long bestLoss = Long.MAX_VALUE;
      for (ChamberSpec c : voltRooms) {
        Env cur = assignment.get(c.id);
        if (cur.equals(env)) { bestChId = c.id; bestLoss = 0; break; }
        if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;

        long curNeed = demandVolt.getOrDefault(cur, 0L);
        long targetNeed = demandVolt.getOrDefault(env, 0L);
        long loss = Math.max(0, curNeed - targetNeed);
        if (loss < bestLoss) {
          bestLoss = loss;
          bestChId = c.id;
        }
      }
      if (bestChId == null) {
        throw new IllegalStateException("Feasible oda ataması yok: voltajlı env " + env + " için uygun voltaj odası bulunamadı");
      }
      assignment.put(bestChId, env);
    }

    // Feasibility check: her env talep varsa en az 1 oda
    for (Env env : demandedEnvs) {
      if (demandTotal.getOrDefault(env, 0L) <= 0) continue;
      boolean any = assignment.values().stream().anyMatch(e -> e.equals(env));
      if (!any) {
        // En düşük toplam skorlu (talep/istasyon) env'ye atanmış bir odayı çevir (minimal zarar).
        String bestSwapId = null;
        double bestSwapScore = Double.POSITIVE_INFINITY;
        for (ChamberSpec c : Data.CHAMBERS) {
          Env cur = assignment.get(c.id);
          if (cur == null) continue;
          if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;

          long curDemand = demandTotal.getOrDefault(cur, 0L);
          int curStations = assignedStationsTotal.getOrDefault(cur, 0);
          double curScore = curDemand / (curStations + 1.0);
          if (curScore < bestSwapScore) {
            bestSwapScore = curScore;
            bestSwapId = c.id;
          }
        }
        if (bestSwapId == null) {
          throw new IllegalStateException("Feasible oda ataması yok: env " + env + " için oda çevrilemiyor");
        }
        assignment.put(bestSwapId, env);
      }
    }

    return assignment;
  }

  private static Env pickBestEnv(
      ChamberSpec chamber,
      Set<Env> allEnvs,
      Map<Env, Long> primaryDemand,
      Map<Env, Long> fallbackDemand,
      Map<Env, Integer> primaryAssignedStations,
      Map<Env, Integer> totalAssignedStations,
      boolean prioritizePrimary
  ) {
    Env best = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (Env env : allEnvs) {
      if (env.humidity == Humidity.H85 && !chamber.humidityAdjustable) continue;

      long d1 = primaryDemand.getOrDefault(env, 0L);
      long d2 = fallbackDemand.getOrDefault(env, 0L);

      // Eğer primary demand boşsa total'a bak.
      long demand = (prioritizePrimary && d1 > 0) ? d1 : d2;

      int assigned = prioritizePrimary ? primaryAssignedStations.getOrDefault(env, 0) : totalAssignedStations.getOrDefault(env, 0);
      double score = demand / (assigned + 1.0);

      if (score > bestScore) {
        bestScore = score;
        best = env;
      }
    }

    if (best == null) {
      // teorik olarak mümkün değil
      throw new IllegalStateException("No feasible env for chamber=" + chamber.id);
    }

    return best;
  }

  private static List<Project> deepCopy(List<Project> ps) {
    List<Project> out = new ArrayList<>();
    for (Project p : ps) out.add(p.copy());
    return out;
  }

  private Scheduler.EvalResult improveOrderByLocalSearch(List<Project> projects, Map<String, Env> room, int baseline) {
    // Başlangıç: EDD sırası
    List<Project> order = projects.stream().map(Project::copy)
        .sorted(Comparator.comparingInt(p -> p.dueDateDays))
        .toList();
    order = new ArrayList<>(order);

    Scheduler.EvalResult best = scheduler.evaluateFixedOrder(order, room);
    int passes = Math.max(1, Data.ORDER_LS_MAX_PASSES);

    for (int pass = 0; pass < passes; pass++) {
      boolean improved = false;
      for (int i = 0; i < order.size() - 1; i++) {
        swap(order, i, i + 1);
        Scheduler.EvalResult cand = scheduler.evaluateFixedOrder(order, room);
        if (cand.totalLateness < best.totalLateness) {
          best = cand;
          improved = true;
        } else {
          swap(order, i, i + 1); // geri al
        }
      }
      if (!improved) break;
    }

    return best.totalLateness < baseline ? best : best; // return best (caller compares)
  }

  private static void swap(List<Project> list, int i, int j) {
    Project tmp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, tmp);
  }
}
