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

  private record RoomObjective(
      int uncoveredVoltEnvs,
      int uncoveredTotalEnvs,
      double bottleneck,      // max(maxVoltRatio, maxTotalRatio)
      double bottleneckVolt,  // max over env of demandVolt/stationsVolt
      double bottleneckTotal  // max over env of demandTotal/stationsTotal
  ) implements Comparable<RoomObjective> {
    @Override
    public int compareTo(RoomObjective o) {
      if (uncoveredVoltEnvs != o.uncoveredVoltEnvs) return Integer.compare(uncoveredVoltEnvs, o.uncoveredVoltEnvs);
      if (uncoveredTotalEnvs != o.uncoveredTotalEnvs) return Integer.compare(uncoveredTotalEnvs, o.uncoveredTotalEnvs);
      if (bottleneck != o.bottleneck) return Double.compare(bottleneck, o.bottleneck);
      if (bottleneckVolt != o.bottleneckVolt) return Double.compare(bottleneckVolt, o.bottleneckVolt);
      return Double.compare(bottleneckTotal, o.bottleneckTotal);
    }
  }

  private static RoomObjective objectiveFor(
      Set<Env> demandedEnvs,
      Set<Env> demandedByVolt,
      Map<Env, Long> demandTotal,
      Map<Env, Long> demandVolt,
      Map<Env, Integer> stationsTotal,
      Map<Env, Integer> stationsVolt
  ) {
    int uncoveredTotal = 0;
    int uncoveredVolt = 0;

    double maxTotal = 0.0;
    double maxVolt = 0.0;

    for (Env env : demandedEnvs) {
      long dt = demandTotal.getOrDefault(env, 0L);
      if (dt <= 0) continue;
      int st = stationsTotal.getOrDefault(env, 0);
      if (st <= 0) {
        uncoveredTotal++;
      } else {
        maxTotal = Math.max(maxTotal, dt / (double) st);
      }
    }

    for (Env env : demandedByVolt) {
      long dv = demandVolt.getOrDefault(env, 0L);
      if (dv <= 0) continue;
      int sv = stationsVolt.getOrDefault(env, 0);
      if (sv <= 0) {
        uncoveredVolt++;
      } else {
        maxVolt = Math.max(maxVolt, dv / (double) sv);
      }
    }

    double bottleneck = Math.max(maxTotal, maxVolt);
    return new RoomObjective(uncoveredVolt, uncoveredTotal, bottleneck, maxVolt, maxTotal);
  }

  private static RoomObjective objectiveIfAssign(
      Set<Env> demandedEnvs,
      Set<Env> demandedByVolt,
      Map<Env, Long> demandTotal,
      Map<Env, Long> demandVolt,
      Map<Env, Integer> stationsTotal,
      Map<Env, Integer> stationsVolt,
      ChamberSpec chamber,
      Env targetEnv
  ) {
    int addTotal = chamber.stations;
    int addVolt = chamber.voltageCapable ? chamber.stations : 0;

    int uncoveredTotal = 0;
    int uncoveredVolt = 0;

    double maxTotal = 0.0;
    double maxVolt = 0.0;

    for (Env env : demandedEnvs) {
      long dt = demandTotal.getOrDefault(env, 0L);
      if (dt <= 0) continue;
      int st = stationsTotal.getOrDefault(env, 0) + (env.equals(targetEnv) ? addTotal : 0);
      if (st <= 0) {
        uncoveredTotal++;
      } else {
        maxTotal = Math.max(maxTotal, dt / (double) st);
      }
    }

    for (Env env : demandedByVolt) {
      long dv = demandVolt.getOrDefault(env, 0L);
      if (dv <= 0) continue;
      int sv = stationsVolt.getOrDefault(env, 0) + (env.equals(targetEnv) ? addVolt : 0);
      if (sv <= 0) {
        uncoveredVolt++;
      } else {
        maxVolt = Math.max(maxVolt, dv / (double) sv);
      }
    }

    double bottleneck = Math.max(maxTotal, maxVolt);
    return new RoomObjective(uncoveredVolt, uncoveredTotal, bottleneck, maxVolt, maxTotal);
  }

  private static Env chooseBestEnvForChamber(
      ChamberSpec chamber,
      Set<Env> demandedEnvs,
      Set<Env> demandedByVolt,
      Map<Env, Long> demandTotal,
      Map<Env, Long> demandVolt,
      Map<Env, Integer> stationsTotal,
      Map<Env, Integer> stationsVolt
  ) {
    Env bestEnv = null;
    RoomObjective bestObj = null;

    for (Env env : demandedEnvs) {
      if (env.humidity == Humidity.H85 && !chamber.humidityAdjustable) continue;
      RoomObjective obj = objectiveIfAssign(demandedEnvs, demandedByVolt, demandTotal, demandVolt, stationsTotal, stationsVolt, chamber, env);
      if (bestObj == null || obj.compareTo(bestObj) < 0) {
        bestObj = obj;
        bestEnv = env;
      }
    }

    if (bestEnv == null) {
      throw new IllegalStateException("No feasible env for chamber=" + chamber.id);
    }
    return bestEnv;
  }

  private static Map<Env, Integer> recomputeStationsTotal(Map<String, Env> assignment) {
    Map<String, ChamberSpec> chamberById = new HashMap<>();
    for (ChamberSpec c : Data.CHAMBERS) chamberById.put(c.id, c);
    Map<Env, Integer> out = new HashMap<>();
    for (var e : assignment.entrySet()) {
      Env env = e.getValue();
      ChamberSpec c = chamberById.get(e.getKey());
      if (env == null || c == null) continue;
      out.merge(env, c.stations, Integer::sum);
    }
    return out;
  }

  private static Map<Env, Integer> recomputeStationsVolt(Map<String, Env> assignment) {
    Map<String, ChamberSpec> chamberById = new HashMap<>();
    for (ChamberSpec c : Data.CHAMBERS) chamberById.put(c.id, c);
    Map<Env, Integer> out = new HashMap<>();
    for (var e : assignment.entrySet()) {
      Env env = e.getValue();
      ChamberSpec c = chamberById.get(e.getKey());
      if (env == null || c == null) continue;
      if (c.voltageCapable) out.merge(env, c.stations, Integer::sum);
    }
    return out;
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
   * Revize başlangıç heuristiği (kısıt + birlikte dengeleme):
   * - Voltaj gerektiren işler SADECE voltaj-capable odalarda koşabilir (kısıt).
   * - Voltaj-capable odalar aynı zamanda voltajsız işler için de kapasite sağlar (esnek kaynak).
   * - Bu yüzden oda-env atamasını sadece DV (volt iş yükü) veya sadece D (toplam iş yükü) ile değil,
   *   birlikte optimize eden bir minimax hedefle yapıyoruz:
   *     bottleneck = max( max_env (D_env / S_env),  max_env (DV_env / SV_env) )
   *   burada S_env toplam istasyon sayısı, SV_env voltaj-capable istasyon sayısı.
   * - Ayrıca talep olan her env için en az 1 oda, voltaj talebi olan her env için en az 1 voltajlı oda garantilenir.
   * - 85% nem env'leri sadece humidityAdjustable odalara atanabilir.
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

    // demanded-by-voltage envs (needs at least one voltage-capable room)
    Set<Env> demandedByVolt = new LinkedHashSet<>();
    for (Env env : demandedEnvs) {
      if (demandVolt.getOrDefault(env, 0L) > 0L) demandedByVolt.add(env);
    }

    // Greedy construction with integrated objective
    Map<Env, Integer> stationsTotal = new HashMap<>();
    Map<Env, Integer> stationsVolt = new HashMap<>();
    Map<String, Env> assignment = new LinkedHashMap<>();

    // (0) Coverage: ensure each voltage-demanded env has >=1 voltage room (humidity feasibility still applies).
    List<Env> voltEnvs = demandedByVolt.stream()
        .sorted((a, b) -> Long.compare(demandVolt.getOrDefault(b, 0L), demandVolt.getOrDefault(a, 0L)))
        .toList();
    List<ChamberSpec> availableVoltRooms = Data.CHAMBERS.stream()
        .filter(c -> c.voltageCapable)
        .sorted(Comparator.comparingInt((ChamberSpec c) -> c.stations).reversed())
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

    for (Env env : voltEnvs) {
      ChamberSpec chosen = null;
      for (ChamberSpec c : availableVoltRooms) {
        if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;
        chosen = c;
        break; // rooms already sorted by stations desc
      }
      if (chosen == null) {
        throw new IllegalStateException("Feasible oda ataması yok: voltaj talebi olan env " + env +
            " için uygun voltaj+humidity oda bulunamadı");
      }
      availableVoltRooms.remove(chosen);
      assignment.put(chosen.id, env);
      stationsTotal.merge(env, chosen.stations, Integer::sum);
      stationsVolt.merge(env, chosen.stations, Integer::sum);
    }

    // (1) Assign remaining rooms (both volt and non-volt) using minimax objective on total+volt bottlenecks.
    List<ChamberSpec> remaining = Data.CHAMBERS.stream()
        .filter(c -> !assignment.containsKey(c.id))
        .sorted(Comparator.comparingInt((ChamberSpec c) -> c.stations).reversed())
        .toList();

    for (ChamberSpec c : remaining) {
      Env best = chooseBestEnvForChamber(c, demandedEnvs, demandedByVolt, demandTotal, demandVolt, stationsTotal, stationsVolt);
      assignment.put(c.id, best);
      stationsTotal.merge(best, c.stations, Integer::sum);
      if (c.voltageCapable) stationsVolt.merge(best, c.stations, Integer::sum);
    }

    // (2) Repair: enforce feasibility in terms of demanded env coverage (rooms) and voltage env coverage.
    // Recompute to be safe (repairs below may change env assignments).
    Set<Env> demandedRooms = demandedEnvs;
    if (!isRoomFeasible(assignment, demandedRooms, demandedByVolt)) {
      // A) fix missing voltage envs (need at least one voltage room)
      for (Env env : demandedByVolt) {
        boolean hasVoltRoom = false;
        for (ChamberSpec c : Data.CHAMBERS) {
          if (!c.voltageCapable) continue;
          Env cur = assignment.get(c.id);
          if (env.equals(cur)) { hasVoltRoom = true; break; }
        }
        if (hasVoltRoom) continue;

        String bestMoveCh = null;
        RoomObjective bestObj = null;
        for (ChamberSpec c : Data.CHAMBERS) {
          if (!c.voltageCapable) continue;
          Env cur = assignment.get(c.id);
          if (cur == null) continue;
          if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;

          // don't steal the only voltage room from another voltage-demanded env
          if (demandedByVolt.contains(cur)) {
            int count = 0;
            for (ChamberSpec c2 : Data.CHAMBERS) {
              if (!c2.voltageCapable) continue;
              if (cur.equals(assignment.get(c2.id))) count++;
            }
            if (count <= 1) continue;
          }

          Map<String, Env> cand = new LinkedHashMap<>(assignment);
          cand.put(c.id, env);
          if (!isRoomFeasible(cand, demandedRooms, demandedByVolt)) continue;
          Map<Env, Integer> stT = recomputeStationsTotal(cand);
          Map<Env, Integer> stV = recomputeStationsVolt(cand);
          RoomObjective obj = objectiveFor(demandedRooms, demandedByVolt, demandTotal, demandVolt, stT, stV);
          if (bestObj == null || obj.compareTo(bestObj) < 0) {
            bestObj = obj;
            bestMoveCh = c.id;
          }
        }
        if (bestMoveCh == null) {
          throw new IllegalStateException("Repair failed: env " + env + " için voltaj odası bulunamadı");
        }
        assignment.put(bestMoveCh, env);
      }

      // B) fix missing demanded envs (need at least one room)
      for (Env env : demandedRooms) {
        if (demandTotal.getOrDefault(env, 0L) <= 0L) continue;
        boolean anyRoom = assignment.values().stream().anyMatch(e -> env.equals(e));
        if (anyRoom) continue;

        String bestMoveCh = null;
        RoomObjective bestObj = null;
        for (ChamberSpec c : Data.CHAMBERS) {
          Env cur = assignment.get(c.id);
          if (cur == null) continue;
          if (env.humidity == Humidity.H85 && !c.humidityAdjustable) continue;

          // don't break voltage coverage by moving a voltage room away from a voltage-demanded env
          if (c.voltageCapable && demandedByVolt.contains(cur)) {
            int count = 0;
            for (ChamberSpec c2 : Data.CHAMBERS) {
              if (!c2.voltageCapable) continue;
              if (cur.equals(assignment.get(c2.id))) count++;
            }
            if (count <= 1) continue;
          }

          Map<String, Env> cand = new LinkedHashMap<>(assignment);
          cand.put(c.id, env);
          if (!isRoomFeasible(cand, demandedRooms, demandedByVolt)) continue;
          Map<Env, Integer> stT = recomputeStationsTotal(cand);
          Map<Env, Integer> stV = recomputeStationsVolt(cand);
          RoomObjective obj = objectiveFor(demandedRooms, demandedByVolt, demandTotal, demandVolt, stT, stV);
          if (bestObj == null || obj.compareTo(bestObj) < 0) {
            bestObj = obj;
            bestMoveCh = c.id;
          }
        }
        if (bestMoveCh == null) {
          throw new IllegalStateException("Repair failed: env " + env + " için oda atanamıyor");
        }
        assignment.put(bestMoveCh, env);
      }
    }

    return assignment;
  }

  private static List<Project> deepCopy(List<Project> ps) {
    List<Project> out = new ArrayList<>();
    for (Project p : ps) out.add(p.copy());
    return out;
  }

}