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
      List<Project> improved = Data.ENABLE_SAMPLE_INCREASE ? stage2_increaseSamples(room, current) : deepCopy(current);
      Scheduler.EvalResult eval = scheduler.evaluate(improved, room);

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
      List<Project> improved = stage2_increaseSamples(room, projects);
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

  private List<Project> stage2_increaseSamples(Map<String, Env> room, List<Project> startProjects) {
    List<Project> current = deepCopy(startProjects);

    // enforce minimum samples
    for (Project p : current) {
      if (p.samples < Data.MIN_SAMPLES) p.samples = Data.MIN_SAMPLES;
    }

    Scheduler.EvalResult baseEval = scheduler.evaluate(current, room);
    if (verbose) {
      System.out.println("INFO: Stage2 initial total lateness = " + baseEval.totalLateness);
    }

    int evalBudget = Math.max(500, Data.SAMPLE_SEARCH_MAX_EVALS);
    int evals = 0;
    int passes = 0;

    // Per-project local search: try +1, +2, -1, -2 and accept best (min lateness).
    // Tie-break: if lateness equal, prefer fewer samples (keeps solution minimal).
    while (evals < evalBudget) {
      boolean improvedAny = false;
      passes++;

      for (int i = 0; i < current.size() && evals < evalBudget; i++) {
        Project p0 = current.get(i);
        int curSamples = p0.samples;

        int bestSamples = curSamples;
        Scheduler.EvalResult bestEval = baseEval;

        int[] deltas = (curSamples <= Data.MIN_SAMPLES)
            ? new int[]{+1, +2}
            : new int[]{+1, +2, -1, -2};
        for (int d : deltas) {
          if (evals >= evalBudget) break;
          int ns = curSamples + d;
          if (ns < Data.MIN_SAMPLES) continue;
          if (ns > Data.SAMPLE_MAX) continue;
          if (ns == curSamples) continue;

          List<Project> cand = deepCopy(current);
          cand.get(i).samples = ns;
          Scheduler.EvalResult e = scheduler.evaluate(cand, room);
          evals++;

          if (e.totalLateness < bestEval.totalLateness ||
              (e.totalLateness == bestEval.totalLateness && ns < bestSamples)) {
            bestEval = e;
            bestSamples = ns;
          }
        }

        boolean accept =
            bestEval.totalLateness < baseEval.totalLateness ||
                (bestEval.totalLateness == baseEval.totalLateness && bestSamples < curSamples);

        if (accept && bestSamples != curSamples) {
          p0.samples = bestSamples;
          baseEval = bestEval;
          improvedAny = true;
          if (verbose) {
            System.out.println("INFO: Stage2 accept sample move => " + p0.id +
                " " + curSamples + " -> " + bestSamples +
                " totalLateness=" + baseEval.totalLateness);
          }
        }
      }

      if (!improvedAny) break;
      if (passes > 200) break; // safety
    }

    if (verbose) {
      System.out.println("INFO: Stage2 sample-search passes=" + passes + " evals=" + evals + " budget=" + evalBudget +
          " finalTotal=" + baseEval.totalLateness);
    }
    return current;
  }

  /**
   * Aşama 1: Oda set değerlerini belirle (sıcaklık/nem sabit kalır).
   *
   * Amaç:
   * - Tüm iş yükünü (env bazında jobDays) ODALAR arasında tek bir global dengeleme ile dağıt.
   * - Voltaj gerektiren iş yükü için voltaj-capable odalarda env kapasitesi ayır (en az 1 volt oda / volt-env).
   * - 85% nem isteyen env sadece humAdj odalara atanabilir.
   *
   * Not: Burada job'ları tek tek odaya atamıyoruz; her oda 1 env'e sabitleniyor.
   * Scheduler daha sonra bu oda/env set değerleri üzerinde job'ları istasyonlara yerleştiriyor.
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

    // Hangi env'lerde voltaj talebi var?
    Set<Env> demandedVoltEnvs = new LinkedHashSet<>();
    for (Env env : demandedEnvs) {
      if (demandVolt.getOrDefault(env, 0L) > 0) demandedVoltEnvs.add(env);
    }

    // assigned station counts per env
    Map<Env, Integer> assignedStationsTotal = new HashMap<>();
    Map<Env, Integer> assignedStationsVolt = new HashMap<>();

    // Büyük odaları önce atamak dengeyi iyileştirir.
    List<ChamberSpec> chambers = Data.CHAMBERS.stream()
        .sorted(Comparator.comparingInt((ChamberSpec c) -> c.stations).reversed())
        .toList();

    int remainingVoltageCapable = (int) chambers.stream().filter(c -> c.voltageCapable).count();

    Map<String, Env> assignment = new LinkedHashMap<>();
    for (int idx = 0; idx < chambers.size(); idx++) {
      ChamberSpec c = chambers.get(idx);

      int remainingChambers = chambers.size() - idx; // includes current
      int missingTotal = 0;
      for (Env env : demandedEnvs) {
        if (assignedStationsTotal.getOrDefault(env, 0) <= 0) missingTotal++;
      }

      int remainingVolt = remainingVoltageCapable; // includes current if voltCapable
      int missingVolt = 0;
      for (Env env : demandedVoltEnvs) {
        if (assignedStationsVolt.getOrDefault(env, 0) <= 0) missingVolt++;
      }

      // Feasible env list for this chamber (humidity constraint)
      List<Env> feasible = new ArrayList<>();
      for (Env env : demandedEnvs) {
        if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;
        feasible.add(env);
      }
      if (feasible.isEmpty()) {
        throw new IllegalStateException("No feasible env for chamber=" + c.id + " (humidity constraints block all demanded envs)");
      }

      // If we are running out of chambers to cover all demanded envs, prioritize envs not yet covered.
      List<Env> candidates = feasible;
      if (remainingChambers <= missingTotal) {
        List<Env> missingFeasible = new ArrayList<>();
        for (Env env : feasible) {
          if (assignedStationsTotal.getOrDefault(env, 0) <= 0) missingFeasible.add(env);
        }
        if (!missingFeasible.isEmpty()) candidates = missingFeasible;
      }

      // If we are running out of VOLT chambers to cover volt-demand envs, force coverage there.
      if (c.voltageCapable && missingVolt > 0 && remainingVolt <= missingVolt) {
        List<Env> missingVoltFeasible = new ArrayList<>();
        for (Env env : candidates) {
          if (demandVolt.getOrDefault(env, 0L) <= 0) continue;
          if (assignedStationsVolt.getOrDefault(env, 0) <= 0) missingVoltFeasible.add(env);
        }
        if (missingVoltFeasible.isEmpty()) {
          // if our current candidate set can't satisfy voltage coverage, relax and at least cover a missing-volt env
          for (Env env : feasible) {
            if (demandVolt.getOrDefault(env, 0L) <= 0) continue;
            if (assignedStationsVolt.getOrDefault(env, 0) <= 0) missingVoltFeasible.add(env);
          }
        }
        if (!missingVoltFeasible.isEmpty()) candidates = missingVoltFeasible;
      }

      Env best = null;
      double bestScore = Double.NEGATIVE_INFINITY;
      for (Env env : candidates) {
        double score = scoreEnvForChamber(env, c, demandTotal, demandVolt, assignedStationsTotal, assignedStationsVolt);
        if (score > bestScore) {
          bestScore = score;
          best = env;
        }
      }
      if (best == null) throw new IllegalStateException("No feasible env for chamber=" + c.id);

      assignment.put(c.id, best);
      assignedStationsTotal.merge(best, c.stations, Integer::sum);
      if (c.voltageCapable) {
        assignedStationsVolt.merge(best, c.stations, Integer::sum);
        remainingVoltageCapable--;
      }
    }

    // Safety repair: ensure every demanded env has >=1 room and every volt-demand env has >=1 volt room.
    // Recompute counts from the final assignment to avoid drift.
    recomputeAssignedStations(assignment, chambers, assignedStationsTotal, assignedStationsVolt);
    repairMissingDemandedEnvs(assignment, demandedEnvs, demandTotal, chambers);
    recomputeAssignedStations(assignment, chambers, assignedStationsTotal, assignedStationsVolt);
    repairMissingVoltEnvs(assignment, demandedVoltEnvs, demandVolt, chambers);
    recomputeAssignedStations(assignment, chambers, assignedStationsTotal, assignedStationsVolt);

    // Final feasibility checks
    for (Env env : demandedEnvs) {
      if (assignedStationsTotal.getOrDefault(env, 0) <= 0) {
        throw new IllegalStateException("Room assignment infeasible: env " + env + " has 0 rooms");
      }
    }
    for (Env env : demandedVoltEnvs) {
      if (assignedStationsVolt.getOrDefault(env, 0) <= 0) {
        throw new IllegalStateException("Room assignment infeasible: volt-demand env " + env + " has 0 voltage-capable rooms");
      }
    }

    return assignment;
  }

  private static double scoreEnvForChamber(
      Env env,
      ChamberSpec chamber,
      Map<Env, Long> demandTotal,
      Map<Env, Long> demandVolt,
      Map<Env, Integer> assignedStationsTotal,
      Map<Env, Integer> assignedStationsVolt
  ) {
    long tot = demandTotal.getOrDefault(env, 0L);
    long volt = demandVolt.getOrDefault(env, 0L);

    double totalPressure = tot / (assignedStationsTotal.getOrDefault(env, 0) + 1.0);
    double score = totalPressure;

    if (chamber.voltageCapable) {
      // voltaj iş yükünü voltajlı odalara "çek"
      double voltPressure = volt / (assignedStationsVolt.getOrDefault(env, 0) + 1.0);
      score += 2.0 * voltPressure;
    }

    // küçük bir tie-break: daha yüksek toplam talep biraz öne gelsin
    score += 1e-6 * tot;
    return score;
  }

  private static void recomputeAssignedStations(
      Map<String, Env> assignment,
      List<ChamberSpec> chambers,
      Map<Env, Integer> assignedStationsTotal,
      Map<Env, Integer> assignedStationsVolt
  ) {
    assignedStationsTotal.clear();
    assignedStationsVolt.clear();
    for (ChamberSpec c : chambers) {
      Env env = assignment.get(c.id);
      if (env == null) continue;
      assignedStationsTotal.merge(env, c.stations, Integer::sum);
      if (c.voltageCapable) assignedStationsVolt.merge(env, c.stations, Integer::sum);
    }
  }

  private static void repairMissingDemandedEnvs(
      Map<String, Env> assignment,
      Set<Env> demandedEnvs,
      Map<Env, Long> demandTotal,
      List<ChamberSpec> chambers
  ) {
    Map<String, ChamberSpec> chamberById = new HashMap<>();
    for (ChamberSpec c : chambers) chamberById.put(c.id, c);

    // Track room counts + station totals dynamically as we repair.
    Map<Env, Integer> roomCount = new HashMap<>();
    Map<Env, Integer> stationTotal = new HashMap<>();
    for (ChamberSpec c : chambers) {
      Env env0 = assignment.get(c.id);
      if (env0 == null) continue;
      roomCount.merge(env0, 1, Integer::sum);
      stationTotal.merge(env0, c.stations, Integer::sum);
    }

    for (Env env : demandedEnvs) {
      if (roomCount.getOrDefault(env, 0) > 0) continue;

      String bestChId = null;
      double bestLoss = Double.POSITIVE_INFINITY;
      for (ChamberSpec c : chambers) {
        if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;
        Env cur = assignment.get(c.id);
        if (cur == null) continue;
        if (cur.equals(env)) continue;

        // Don't remove the last room from another demanded env.
        if (demandedEnvs.contains(cur) && roomCount.getOrDefault(cur, 0) <= 1) continue;

        int curStations = stationTotal.getOrDefault(cur, 0);
        int afterStations = curStations - c.stations;
        if (curStations <= 0 || afterStations <= 0) continue;

        double before = demandTotal.getOrDefault(cur, 0L) / (double) curStations;
        double after = demandTotal.getOrDefault(cur, 0L) / (double) afterStations;
        double loss = after - before; // how much worse the "pressure" gets for cur env

        if (loss < bestLoss) {
          bestLoss = loss;
          bestChId = c.id;
        }
      }
      if (bestChId == null) {
        throw new IllegalStateException("Feasible oda ataması yok: env " + env + " için oda çevrilemiyor");
      }
      Env prev = assignment.put(bestChId, env);
      ChamberSpec moved = chamberById.get(bestChId);
      if (prev != null && moved != null) {
        roomCount.merge(prev, -1, Integer::sum);
        stationTotal.merge(prev, -moved.stations, Integer::sum);
      }
      if (moved != null) {
        roomCount.merge(env, 1, Integer::sum);
        stationTotal.merge(env, moved.stations, Integer::sum);
      }
    }
  }

  private static void repairMissingVoltEnvs(
      Map<String, Env> assignment,
      Set<Env> demandedVoltEnvs,
      Map<Env, Long> demandVolt,
      List<ChamberSpec> chambers
  ) {
    if (demandedVoltEnvs.isEmpty()) return;

    Map<String, ChamberSpec> chamberById = new HashMap<>();
    for (ChamberSpec c : chambers) chamberById.put(c.id, c);

    // Track voltage-room counts dynamically.
    Map<Env, Integer> voltRoomCount = new HashMap<>();
    for (ChamberSpec c : chambers) {
      if (!c.voltageCapable) continue;
      Env env0 = assignment.get(c.id);
      if (env0 == null) continue;
      voltRoomCount.merge(env0, 1, Integer::sum);
    }

    for (Env env : demandedVoltEnvs) {
      if (voltRoomCount.getOrDefault(env, 0) > 0) continue;

      String bestChId = null;
      double bestLoss = Double.POSITIVE_INFINITY;
      for (ChamberSpec c : chambers) {
        if (!c.voltageCapable) continue;
        if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;

        Env cur = assignment.get(c.id);
        if (cur == null) continue;
        if (cur.equals(env)) continue;

        // Don't steal the last voltage-capable room from another volt-demand env.
        if (demandedVoltEnvs.contains(cur) && voltRoomCount.getOrDefault(cur, 0) <= 1) continue;

        // prefer stealing a volt-room from an env with low volt demand
        double curNeed = demandVolt.getOrDefault(cur, 0L);
        double targetNeed = demandVolt.getOrDefault(env, 0L);
        double loss = Math.max(0.0, curNeed - targetNeed);
        if (loss < bestLoss) {
          bestLoss = loss;
          bestChId = c.id;
        }
      }
      if (bestChId == null) {
        throw new IllegalStateException("Feasible oda ataması yok: voltajlı env " + env + " için uygun voltaj odası bulunamadı");
      }
      Env prev = assignment.put(bestChId, env);
      if (prev != null) voltRoomCount.merge(prev, -1, Integer::sum);
      voltRoomCount.merge(env, 1, Integer::sum);
    }
  }

  private static List<Project> deepCopy(List<Project> ps) {
    List<Project> out = new ArrayList<>();
    for (Project p : ps) out.add(p.copy());
    return out;
  }

}