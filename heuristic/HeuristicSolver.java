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
    List<Project> projects = Data.buildProjects(Data.INITIAL_SAMPLES);

    List<Solution> solutions = new ArrayList<>();
    Map<String, Env> prevRoom = null;

    List<Project> current = deepCopy(projects);

    for (int iter = 1; iter <= 5; iter++) {
      Map<String, Env> room = stage1_assignRooms(current);

      if (Data.ENABLE_ROOM_LOCAL_SEARCH) {
        RoomScore base = scoreRoom(room, current);
        Map<String, Env> improvedRoom = improveRoomsByLocalSearch(current, room, base.totalLateness);
        RoomScore after = scoreRoom(improvedRoom, current);
        if (after.totalLateness < base.totalLateness) {
          if (verbose) {
            System.out.println("INFO: Room local-search improved total lateness: " +
                base.totalLateness + " -> " + after.totalLateness);
          }
          room = improvedRoom;
        } else if (verbose) {
          System.out.println("INFO: Room local-search no improvement (baseline=" + base.totalLateness + ")");
        }
      }

      // Eğer oda setleri artık değişmiyorsa sabit noktaya geldik: tekrar üretmek yerine dur.
      if (prevRoom != null && prevRoom.equals(room)) {
        if (verbose) {
          System.out.println("INFO: Stage3 converged (room set unchanged). Stopping at iter=" + (iter - 1));
        }
        break;
      }

      // Stage2: EDD scheduling + sample artırma
      List<Project> improved = stage2_adjustSamples(room, current);
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

      if (Data.ENABLE_SCHEDULE_VALIDATION) {
        List<String> violations = Scheduler.validateSchedule(improved, room, eval.schedule);
        if (!violations.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          sb.append("Schedule validation failed (").append(violations.size()).append(" violations). First 20:\n");
          for (int i = 0; i < Math.min(20, violations.size()); i++) {
            sb.append("- ").append(violations.get(i)).append("\n");
          }
          throw new IllegalStateException(sb.toString());
        }
      }

      prevRoom = room;
      current = deepCopy(improved);
    }

    return solutions;
  }

  private record RoomScore(int totalLateness) {}

  private RoomScore scoreRoom(Map<String, Env> room, List<Project> projects) {
    if (Data.ROOM_LS_INCLUDE_SAMPLE_HEURISTIC) {
      List<Project> improved = stage2_adjustSamples(room, projects);
      Scheduler.EvalResult eval = scheduler.evaluate(improved, room);
      return new RoomScore(eval.totalLateness);
    }
    Scheduler.EvalResult eval = scheduler.evaluate(projects, room);
    return new RoomScore(eval.totalLateness);
  }

  private Map<String, Env> improveRoomsByLocalSearch(List<Project> projects, Map<String, Env> startRoom, int baseline) {
    int maxEvals = Math.max(10, Data.ROOM_LS_MAX_EVALS);
    Map<String, Env> best = new LinkedHashMap<>(startRoom);
    int bestScore = baseline;

    // demanded env set from current projects
    Set<Env> demanded = new LinkedHashSet<>();
    Set<Env> demandedByVolt = new LinkedHashSet<>();
    for (Project p : projects) {
      for (int ti = 0; ti < Data.TESTS.size(); ti++) {
        if (!p.required[ti]) continue;
        Env env = Data.TESTS.get(ti).env;
        demanded.add(env);
        if (p.needsVoltage) demandedByVolt.add(env);
      }
    }
    if (demanded.isEmpty()) return best;

    int evals = 0;
    boolean improvedAny;
    do {
      improvedAny = false;

      // SWAP neighbors
      if (Data.ROOM_LS_ENABLE_SWAP) {
        for (int i = 0; i < Data.CHAMBERS.size() && evals < maxEvals; i++) {
          for (int j = i + 1; j < Data.CHAMBERS.size() && evals < maxEvals; j++) {
            ChamberSpec ci = Data.CHAMBERS.get(i);
            ChamberSpec cj = Data.CHAMBERS.get(j);
            Env ei = best.get(ci.id);
            Env ej = best.get(cj.id);
            if (ei == null || ej == null || ei.equals(ej)) continue;
            if (!canAssign(ci, ej) || !canAssign(cj, ei)) continue;

            Map<String, Env> candRoom = new LinkedHashMap<>(best);
            candRoom.put(ci.id, ej);
            candRoom.put(cj.id, ei);
            if (!isRoomFeasible(candRoom, demanded, demandedByVolt)) continue;
            evals++;
            int s = scoreRoom(candRoom, projects).totalLateness;
            if (s < bestScore) {
              best = candRoom;
              bestScore = s;
              improvedAny = true;
              break;
            }
          }
          if (improvedAny) break;
        }
      }

      // MOVE neighbors
      if (!improvedAny && Data.ROOM_LS_ENABLE_MOVE) {
        for (int i = 0; i < Data.CHAMBERS.size() && evals < maxEvals; i++) {
          ChamberSpec c = Data.CHAMBERS.get(i);
          Env cur = best.get(c.id);
          for (Env target : demanded) {
            if (evals >= maxEvals) break;
            if (target.equals(cur)) continue;
            if (!canAssign(c, target)) continue;
            Map<String, Env> candRoom = new LinkedHashMap<>(best);
            candRoom.put(c.id, target);
            if (!isRoomFeasible(candRoom, demanded, demandedByVolt)) continue;
            evals++;
            int s = scoreRoom(candRoom, projects).totalLateness;
            if (s < bestScore) {
              best = candRoom;
              bestScore = s;
              improvedAny = true;
              break;
            }
          }
          if (improvedAny) break;
        }
      }

    } while (improvedAny && evals < maxEvals);

    if (verbose) {
      System.out.println("INFO: Room local-search evals=" + evals + " best=" + bestScore + " baseline=" + baseline);
    }
    return best;
  }

  private static boolean canAssign(ChamberSpec chamber, Env env) {
    if (env.humidity == Humidity.H85 && !chamber.humidityAdjustable) return false;
    return true;
  }

  private static boolean isRoomFeasible(Map<String, Env> room, Set<Env> demanded, Set<Env> demandedByVolt) {
    // (1) each demanded env has at least 1 room
    Map<Env, Integer> count = new HashMap<>();
    Map<Env, Integer> countVoltRooms = new HashMap<>();
    Map<String, ChamberSpec> chamberById = new HashMap<>();
    for (ChamberSpec c : Data.CHAMBERS) chamberById.put(c.id, c);

    for (var e : room.entrySet()) {
      Env env = e.getValue();
      if (env == null) continue;
      ChamberSpec ch = chamberById.get(e.getKey());
      if (ch == null) continue;
      // humidity feasibility
      if (env.humidity == Humidity.H85 && !ch.humidityAdjustable) return false;
      count.merge(env, 1, Integer::sum);
      if (ch.voltageCapable) countVoltRooms.merge(env, 1, Integer::sum);
    }
    for (Env env : demanded) {
      if (count.getOrDefault(env, 0) <= 0) return false;
    }
    // (2) env demanded by voltage projects should have at least 1 voltage-capable room
    for (Env env : demandedByVolt) {
      if (countVoltRooms.getOrDefault(env, 0) <= 0) return false;
    }
    return true;
  }

  private List<Project> stage2_adjustSamples(Map<String, Env> room, List<Project> startProjects) {
    List<Project> current = deepCopy(startProjects);

    Scheduler.EvalResult baseEval = scheduler.evaluate(current, room);
    if (verbose) {
      System.out.println("INFO: Stage2 initial total lateness = " + baseEval.totalLateness);
    }

    int evalBudget = Math.max(500, Data.SAMPLE_SEARCH_MAX_EVALS);
    int evals = 0;

    // Local search: her projede {+1,+2,-1,-2} hamlelerini dene; iyileştiren en iyiyi kabul et.
    // Döngü: bir turda en az 1 iyileştirme olursa yeni tur.
    int passes = 0;
    while (evals < evalBudget) {
      passes++;
      boolean improvedAny = false;

      for (int i = 0; i < current.size() && evals < evalBudget; i++) {
        Project p0 = current.get(i);

        int[] moves = new int[]{+1, +2, -1, -2};
        int bestSamples = p0.samples;
        Scheduler.EvalResult best = baseEval;

        for (int d : moves) {
          int newSamples = p0.samples + d;
          if (newSamples < Data.SAMPLE_MIN || newSamples > Data.SAMPLE_MAX) continue;

          List<Project> cand = deepCopy(current);
          cand.get(i).samples = newSamples;
          Scheduler.EvalResult e = scheduler.evaluate(cand, room);
          evals++;

          if (e.totalLateness < best.totalLateness) {
            best = e;
            bestSamples = newSamples;
          } else if (e.totalLateness == best.totalLateness && newSamples < bestSamples) {
            // aynı lateness: daha az sample'ı tercih et (iş yükünü gereksiz artırma).
            best = e;
            bestSamples = newSamples;
          }
        }

        boolean improvesObjective = best.totalLateness < baseEval.totalLateness;
        boolean keepsObjectiveButReducesSamples = best.totalLateness == baseEval.totalLateness && bestSamples < p0.samples;
        if (bestSamples != p0.samples && (improvesObjective || keepsObjectiveButReducesSamples)) {
          p0.samples = bestSamples;
          baseEval = best;
          improvedAny = true;
          if (verbose) {
            System.out.println("INFO: Stage2 accept samples move => " + p0.id +
                " samples=" + p0.samples + " total=" + baseEval.totalLateness);
          }
        }
      }

      if (!improvedAny) {
        if (verbose) {
          System.out.println("INFO: Stage2 no further improvement. Final total lateness = " + baseEval.totalLateness);
        }
        break;
      }
    }

    if (verbose) {
      System.out.println("INFO: Stage2 sample-search evals=" + evals + " budget=" + evalBudget + " passes=" + passes);
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
    int window = Math.max(1, Data.ORDER_LS_WINDOW);
    int maxEvals = Math.max(50, Data.ORDER_LS_MAX_EVALS);
    int evals = 0;

    // VNS benzeri: insertion/move (i -> j) ile first-improvement, birkaç pass.
    for (int pass = 0; pass < passes && evals < maxEvals; pass++) {
      boolean improved = false;

      outer:
      for (int i = 0; i < order.size() && evals < maxEvals; i++) {
        int from = i;
        int lo = Math.max(0, from - window);
        int hi = Math.min(order.size() - 1, from + window);

        for (int to = lo; to <= hi && evals < maxEvals; to++) {
          if (to == from) continue;

          Project moved = order.remove(from);
          order.add(to, moved);

          Scheduler.EvalResult cand = scheduler.evaluateFixedOrder(order, room);
          evals++;

          if (cand.totalLateness < best.totalLateness) {
            best = cand;
            improved = true;
            // İlk iyileştirmeyi kabul edip baştan başla (first-improvement).
            break outer;
          } else {
            // geri al
            order.remove(to);
            order.add(from, moved);
          }
        }
      }

      if (!improved) break;
    }

    if (verbose) {
      System.out.println("INFO: Order local-search evals=" + evals +
          " window=" + window + " passes=" + passes +
          " best=" + best.totalLateness + " baseline=" + baseline);
    }
    return best;
  }

  private static void swap(List<Project> list, int i, int j) {
    Project tmp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, tmp);
  }
}