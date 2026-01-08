package tr.testodasi.heuristic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage-1 room setpoint assignment optimizer.
 *
 * <p>This is an exact solver (branch-and-bound) for the following model:
 *
 * <ul>
 *   <li>Each chamber is assigned to exactly one demanded {@link Env}.</li>
 *   <li>Each demanded env must get at least one chamber.</li>
 *   <li>Each env that is demanded by at least one voltage-requiring project must get at least one
 *       voltage-capable chamber.</li>
 *   <li>Humidity 85% envs can only be assigned to humidity-adjustable chambers.</li>
 * </ul>
 *
 * <p>Objective (same as the previous heuristic target balancing, but now solved optimally):
 *
 * <pre>
 *   minimize  sum_e (S_e - T_e)^2  + wVolt * sum_e (V_e - TV_e)^2
 * </pre>
 *
 * where:
 * - S_e  = total stations assigned to env e
 * - V_e  = total voltage-capable stations assigned to env e
 * - T_e  = workload-proportional target stations (total)
 * - TV_e = workload-proportional target stations (voltage-capable)
 *
 * <p>Notes:
 * - This objective is a proxy to balance workload before scheduling.
 * - Stage-3 local search may still improve true schedule objective (lateness).
 */
public final class RoomAssignmentOptimizer {
  private RoomAssignmentOptimizer() {}

  public static Map<String, Env> solveExact(
      List<ChamberSpec> chambers,
      List<Env> demandedEnvs,
      boolean[] demandedVoltEnv,
      double[] targetStationsTotal,
      double[] targetStationsVolt,
      double wVolt
  ) {
    Objects.requireNonNull(chambers);
    Objects.requireNonNull(demandedEnvs);
    Objects.requireNonNull(demandedVoltEnv);
    Objects.requireNonNull(targetStationsTotal);
    Objects.requireNonNull(targetStationsVolt);
    if (demandedEnvs.isEmpty()) throw new IllegalArgumentException("demandedEnvs is empty");
    if (demandedVoltEnv.length != demandedEnvs.size()) throw new IllegalArgumentException("demandedVoltEnv length mismatch");
    if (targetStationsTotal.length != demandedEnvs.size()) throw new IllegalArgumentException("targetStationsTotal length mismatch");
    if (targetStationsVolt.length != demandedEnvs.size()) throw new IllegalArgumentException("targetStationsVolt length mismatch");

    int m = demandedEnvs.size();

    // IMPORTANT: chambers contain many identical rooms (same stations/volt/hum). If we branch on each
    // room individually, we get massive symmetry and the exact search can explode.
    //
    // Instead we solve using "room types" (stations, voltCapable, humidityAdjustable) as integer counts.
    // This keeps the model exact but drastically reduces the search space.
    List<RoomType> types = buildRoomTypes(chambers);
    // Harder types first (more restrictive / larger impact).
    types.sort(Comparator
        .comparingInt((RoomType t) -> t.stations * t.count).reversed()
        .thenComparing(t -> t.voltCapable, Comparator.reverseOrder())
        .thenComparing(t -> t.humidityAdjustable, Comparator.reverseOrder()));

    int tN = types.size();

    boolean[][] typeFeasible = new boolean[tN][m];
    for (int ti = 0; ti < tN; ti++) {
      RoomType t = types.get(ti);
      for (int e = 0; e < m; e++) {
        Env env = demandedEnvs.get(e);
        typeFeasible[ti][e] = !(env.humidity == Humidity.H85 && !t.humidityAdjustable);
      }
    }

    int[] remStationsFrom = new int[tN + 1];
    int[] remVoltStationsFrom = new int[tN + 1];
    int[][] remFeasibleRoomsFrom = new int[tN + 1][m];
    int[][] remFeasibleVoltRoomsFrom = new int[tN + 1][m];
    for (int ti = tN - 1; ti >= 0; ti--) {
      RoomType t = types.get(ti);
      remStationsFrom[ti] = remStationsFrom[ti + 1] + t.count * t.stations;
      remVoltStationsFrom[ti] = remVoltStationsFrom[ti + 1] + (t.voltCapable ? t.count * t.stations : 0);
      for (int e = 0; e < m; e++) {
        remFeasibleRoomsFrom[ti][e] = remFeasibleRoomsFrom[ti + 1][e] + (typeFeasible[ti][e] ? t.count : 0);
        remFeasibleVoltRoomsFrom[ti][e] = remFeasibleVoltRoomsFrom[ti + 1][e] + ((typeFeasible[ti][e] && t.voltCapable) ? t.count : 0);
      }
    }

    // Greedy initial upper bound (still type-based, only for pruning).
    int[][] greedyAlloc = greedyInitialAllocation(types, typeFeasible, demandedVoltEnv, targetStationsTotal, targetStationsVolt, wVolt);
    Best best = new Best();
    if (greedyAlloc != null) {
      best.bestObj = objectiveFromTypeAlloc(types, greedyAlloc, targetStationsTotal, targetStationsVolt, wVolt);
      best.bestAlloc = greedyAlloc;
    }

    int[][] alloc = new int[tN][m]; // alloc[ti][e] = number of rooms of type ti assigned to env e
    int[] sByEnv = new int[m];
    int[] vByEnv = new int[m];
    int[] roomsByEnv = new int[m];
    int[] voltRoomsByEnv = new int[m];

    dfsTypes(
        0,
        types,
        typeFeasible,
        demandedVoltEnv,
        targetStationsTotal,
        targetStationsVolt,
        wVolt,
        remStationsFrom,
        remVoltStationsFrom,
        remFeasibleRoomsFrom,
        remFeasibleVoltRoomsFrom,
        alloc,
        sByEnv,
        vByEnv,
        roomsByEnv,
        voltRoomsByEnv,
        best
    );

    if (best.bestAlloc == null) {
      throw new IllegalStateException("No feasible room assignment found (check humidity/voltage constraints).");
    }

    // Materialize per-chamber mapping using the type allocations.
    Map<String, Env> out = new LinkedHashMap<>();
    for (int ti = 0; ti < tN; ti++) {
      RoomType t = types.get(ti);
      int idx = 0;
      for (int e = 0; e < m; e++) {
        int k = best.bestAlloc[ti][e];
        for (int r = 0; r < k; r++) {
          out.put(t.chamberIds.get(idx++), demandedEnvs.get(e));
        }
      }
      if (idx != t.chamberIds.size()) {
        throw new IllegalStateException("Internal error: allocation does not cover type chambers");
      }
    }
    return out;
  }

  private static final class RoomType {
    final int stations;
    final boolean voltCapable;
    final boolean humidityAdjustable;
    final int count;
    final List<String> chamberIds;

    RoomType(int stations, boolean voltCapable, boolean humidityAdjustable, int count, List<String> chamberIds) {
      this.stations = stations;
      this.voltCapable = voltCapable;
      this.humidityAdjustable = humidityAdjustable;
      this.count = count;
      this.chamberIds = chamberIds;
    }
  }

  private static List<RoomType> buildRoomTypes(List<ChamberSpec> chambers) {
    record Key(int stations, boolean volt, boolean hum) {}
    Map<Key, List<String>> ids = new HashMap<>();
    for (ChamberSpec c : chambers) {
      ids.computeIfAbsent(new Key(c.stations, c.voltageCapable, c.humidityAdjustable), k -> new ArrayList<>()).add(c.id);
    }
    List<RoomType> out = new ArrayList<>();
    for (var e : ids.entrySet()) {
      Key k = e.getKey();
      List<String> list = e.getValue();
      list.sort(String::compareTo);
      out.add(new RoomType(k.stations, k.volt, k.hum, list.size(), list));
    }
    return out;
  }

  private static final class Best {
    double bestObj = Double.POSITIVE_INFINITY;
    int[][] bestAlloc = null;
  }

  private static void dfsTypes(
      int ti,
      List<RoomType> types,
      boolean[][] typeFeasible,
      boolean[] demandedVoltEnv,
      double[] targetTot,
      double[] targetVolt,
      double wVolt,
      int[] remStationsFrom,
      int[] remVoltStationsFrom,
      int[][] remFeasibleRoomsFrom,
      int[][] remFeasibleVoltRoomsFrom,
      int[][] alloc,
      int[] sByEnv,
      int[] vByEnv,
      int[] roomsByEnv,
      int[] voltRoomsByEnv,
      Best best
  ) {
    int tN = types.size();
    int m = sByEnv.length;

    // Coverage feasibility pruning.
    for (int e = 0; e < m; e++) {
      if (roomsByEnv[e] == 0 && remFeasibleRoomsFrom[ti][e] == 0) return;
      if (demandedVoltEnv[e] && voltRoomsByEnv[e] == 0 && remFeasibleVoltRoomsFrom[ti][e] == 0) return;
    }

    // Lower bound pruning (continuous relaxation).
    double lbTot = relaxedLowerBound(sByEnv, targetTot, remStationsFrom[ti]);
    double lbVolt = relaxedLowerBound(vByEnv, targetVolt, remVoltStationsFrom[ti]);
    double lb = lbTot + wVolt * lbVolt;
    if (lb >= best.bestObj - 1e-9) return;

    if (ti == tN) {
      for (int e = 0; e < m; e++) {
        if (roomsByEnv[e] <= 0) return;
        if (demandedVoltEnv[e] && voltRoomsByEnv[e] <= 0) return;
      }
      double obj = objectiveFromAggregates(sByEnv, vByEnv, targetTot, targetVolt, wVolt);
      if (obj < best.bestObj - 1e-9) {
        best.bestObj = obj;
        int[][] snap = new int[tN][m];
        for (int i = 0; i < tN; i++) snap[i] = Arrays.copyOf(alloc[i], m);
        best.bestAlloc = snap;
      }
      return;
    }

    RoomType t = types.get(ti);
    int count = t.count;
    int st = t.stations;
    boolean isVolt = t.voltCapable;

    // Prepare feasible env list for this type.
    int[] feas = new int[m];
    int feasN = 0;
    for (int e = 0; e < m; e++) if (typeFeasible[ti][e]) feas[feasN++] = e;
    if (feasN == 0) return;

    // Enumerate all compositions of 'count' rooms across feasible envs.
    // We order envs by current "need" to cover unassigned envs / volt coverage and by objective delta.
    Integer[] orderedFeasBoxed = new Integer[feasN];
    for (int k = 0; k < feasN; k++) orderedFeasBoxed[k] = feas[k];
    Arrays.sort(orderedFeasBoxed, (a, b) -> {
      double wa = envWeightForOrdering(a, roomsByEnv, voltRoomsByEnv, demandedVoltEnv);
      double wb = envWeightForOrdering(b, roomsByEnv, voltRoomsByEnv, demandedVoltEnv);
      // higher weight first
      int c1 = Double.compare(wb, wa);
      if (c1 != 0) return c1;
      // then by which one reduces current deviation more (rough heuristic ordering, does not affect optimality)
      double da = Math.abs((sByEnv[a] - targetTot[a])) + wVolt * Math.abs((vByEnv[a] - targetVolt[a]));
      double db = Math.abs((sByEnv[b] - targetTot[b])) + wVolt * Math.abs((vByEnv[b] - targetVolt[b]));
      return Double.compare(db, da);
    });
    int[] orderedFeas = new int[feasN];
    for (int k = 0; k < feasN; k++) orderedFeas[k] = orderedFeasBoxed[k];

    // composition recursion
    int[] x = new int[m]; // only entries for feasible envs used
    Arrays.fill(alloc[ti], 0);
    composeAndRecurse(
        0,
        orderedFeas,
        feasN,
        count,
        x,
        ti,
        types,
        typeFeasible,
        demandedVoltEnv,
        targetTot,
        targetVolt,
        wVolt,
        remStationsFrom,
        remVoltStationsFrom,
        remFeasibleRoomsFrom,
        remFeasibleVoltRoomsFrom,
        alloc,
        sByEnv,
        vByEnv,
        roomsByEnv,
        voltRoomsByEnv,
        best,
        st,
        isVolt
    );
  }

  private static double envWeightForOrdering(int e, int[] roomsByEnv, int[] voltRoomsByEnv, boolean[] demandedVoltEnv) {
    double w = 0.0;
    if (roomsByEnv[e] == 0) w += 1000.0;
    if (demandedVoltEnv[e] && voltRoomsByEnv[e] == 0) w += 500.0;
    return w;
  }

  private static void composeAndRecurse(
      int pos,
      int[] feas,
      int feasN,
      int remaining,
      int[] x,
      int ti,
      List<RoomType> types,
      boolean[][] typeFeasible,
      boolean[] demandedVoltEnv,
      double[] targetTot,
      double[] targetVolt,
      double wVolt,
      int[] remStationsFrom,
      int[] remVoltStationsFrom,
      int[][] remFeasibleRoomsFrom,
      int[][] remFeasibleVoltRoomsFrom,
      int[][] alloc,
      int[] sByEnv,
      int[] vByEnv,
      int[] roomsByEnv,
      int[] voltRoomsByEnv,
      Best best,
      int st,
      boolean isVolt
  ) {
    int m = sByEnv.length;
    if (pos == feasN - 1) {
      int eLast = feas[pos];
      int k = remaining;
      // apply allocation for this type
      alloc[ti][eLast] = k;
      for (int j = 0; j < pos; j++) alloc[ti][feas[j]] = x[j];

      // apply aggregates
      for (int j = 0; j < feasN; j++) {
        int e = feas[j];
        int c = alloc[ti][e];
        if (c == 0) continue;
        sByEnv[e] += c * st;
        roomsByEnv[e] += c;
        if (isVolt) {
          vByEnv[e] += c * st;
          voltRoomsByEnv[e] += c;
        }
      }

      dfsTypes(
          ti + 1,
          types,
          typeFeasible,
          demandedVoltEnv,
          targetTot,
          targetVolt,
          wVolt,
          remStationsFrom,
          remVoltStationsFrom,
          remFeasibleRoomsFrom,
          remFeasibleVoltRoomsFrom,
          alloc,
          sByEnv,
          vByEnv,
          roomsByEnv,
          voltRoomsByEnv,
          best
      );

      // undo
      for (int j = 0; j < feasN; j++) {
        int e = feas[j];
        int c = alloc[ti][e];
        if (c == 0) continue;
        sByEnv[e] -= c * st;
        roomsByEnv[e] -= c;
        if (isVolt) {
          vByEnv[e] -= c * st;
          voltRoomsByEnv[e] -= c;
        }
      }
      // clear row
      for (int j = 0; j < feasN; j++) alloc[ti][feas[j]] = 0;
      return;
    }

    int eCur = feas[pos];

    // Iterate possible counts assigned to current env.
    // Heuristic: try assigning more to high deviation envs first (better UB earlier).
    // This does not affect correctness.
    for (int k = remaining; k >= 0; k--) {
      x[pos] = k;
      composeAndRecurse(
          pos + 1,
          feas,
          feasN,
          remaining - k,
          x,
          ti,
          types,
          typeFeasible,
          demandedVoltEnv,
          targetTot,
          targetVolt,
          wVolt,
          remStationsFrom,
          remVoltStationsFrom,
          remFeasibleRoomsFrom,
          remFeasibleVoltRoomsFrom,
          alloc,
          sByEnv,
          vByEnv,
          roomsByEnv,
          voltRoomsByEnv,
          best,
          st,
          isVolt
      );
    }
  }

  private static double objectiveFromAggregates(int[] sByEnv, int[] vByEnv, double[] tTot, double[] tVolt, double wVolt) {
    double obj = 0.0;
    for (int e = 0; e < sByEnv.length; e++) {
      double dt = sByEnv[e] - tTot[e];
      obj += dt * dt;
      double dv = vByEnv[e] - tVolt[e];
      obj += wVolt * (dv * dv);
    }
    return obj;
  }

  /**
   * Continuous relaxation lower bound:
   *
   * <p>Given current integer totals L_e (non-decreasing), targets T_e (double) and remaining integer
   * capacity R, we allow distributing R continuously across envs (d_e >= 0, sum d_e = R) to minimize:
   * sum (L_e + d_e - T_e)^2.
   *
   * <p>This is a projection onto an affine hyperplane with lower bounds; solved by water-filling
   * (binary search on lambda).
   */
  private static double relaxedLowerBound(int[] lower, double[] target, int remaining) {
    double sumLower = 0.0;
    for (int x : lower) sumLower += x;
    double requiredSum = sumLower + remaining;

    // If requiredSum equals sumLower, nothing to distribute.
    if (remaining <= 0) {
      double obj = 0.0;
      for (int i = 0; i < lower.length; i++) {
        double d = lower[i] - target[i];
        obj += d * d;
      }
      return obj;
    }

    double lo = -1e6;
    double hi = 1e6;

    // Ensure bounds bracket the solution: sum(max(lower, target-lambda)) decreases with lambda.
    for (int it = 0; it < 80; it++) {
      double mid = (lo + hi) / 2.0;
      double s = 0.0;
      for (int i = 0; i < lower.length; i++) {
        double fi = Math.max(lower[i], target[i] - mid);
        s += fi;
      }
      if (s > requiredSum) {
        lo = mid; // need larger lambda (reduce sum)
      } else {
        hi = mid;
      }
    }

    double lambda = hi;
    double obj = 0.0;
    double s = 0.0;
    double[] f = new double[lower.length];
    for (int i = 0; i < lower.length; i++) {
      f[i] = Math.max(lower[i], target[i] - lambda);
      s += f[i];
    }

    // Numerical fix: adjust the slack on a single env that is not at lower bound if needed.
    double slack = requiredSum - s;
    if (Math.abs(slack) > 1e-6) {
      int idx = -1;
      for (int i = 0; i < lower.length; i++) {
        if (f[i] > lower[i] + 1e-9) { idx = i; break; }
      }
      if (idx < 0) idx = 0;
      f[idx] += slack;
    }

    for (int i = 0; i < lower.length; i++) {
      double d = f[i] - target[i];
      obj += d * d;
    }
    return obj;
  }

  private static int[][] greedyInitialAllocation(
      List<RoomType> types,
      boolean[][] typeFeasible,
      boolean[] demandedVoltEnv,
      double[] targetTot,
      double[] targetVolt,
      double wVolt
  ) {
    int tN = types.size();
    int m = targetTot.length;
    int[][] alloc = new int[tN][m];

    int[] sByEnv = new int[m];
    int[] vByEnv = new int[m];
    int[] roomsByEnv = new int[m];
    int[] voltRoomsByEnv = new int[m];

    // (1) Cover each env with at least 1 room.
    for (int e = 0; e < m; e++) {
      int bestTi = -1;
      double bestDelta = Double.POSITIVE_INFINITY;
      for (int ti = 0; ti < tN; ti++) {
        RoomType t = types.get(ti);
        if (!typeFeasible[ti][e]) continue;
        // do we have an unassigned room in this type?
        int used = 0;
        for (int ee = 0; ee < m; ee++) used += alloc[ti][ee];
        if (used >= t.count) continue;

        // delta if we add 1 room of this type to env e
        double dt0 = sByEnv[e] - targetTot[e];
        double dt1 = (sByEnv[e] + t.stations) - targetTot[e];
        double d = (dt1 * dt1) - (dt0 * dt0);
        if (t.voltCapable) {
          double dv0 = vByEnv[e] - targetVolt[e];
          double dv1 = (vByEnv[e] + t.stations) - targetVolt[e];
          d += wVolt * ((dv1 * dv1) - (dv0 * dv0));
        }
        if (d < bestDelta) {
          bestDelta = d;
          bestTi = ti;
        }
      }
      if (bestTi < 0) return null;
      RoomType t = types.get(bestTi);
      alloc[bestTi][e] += 1;
      sByEnv[e] += t.stations;
      roomsByEnv[e] += 1;
      if (t.voltCapable) {
        vByEnv[e] += t.stations;
        voltRoomsByEnv[e] += 1;
      }
    }

    // (2) Ensure each voltage-demanded env has at least 1 voltage-capable room.
    for (int e = 0; e < m; e++) {
      if (!demandedVoltEnv[e]) continue;
      if (voltRoomsByEnv[e] > 0) continue;
      int bestTi = -1;
      double bestDelta = Double.POSITIVE_INFINITY;
      for (int ti = 0; ti < tN; ti++) {
        RoomType t = types.get(ti);
        if (!t.voltCapable) continue;
        if (!typeFeasible[ti][e]) continue;
        int used = 0;
        for (int ee = 0; ee < m; ee++) used += alloc[ti][ee];
        if (used >= t.count) continue;
        double dt0 = sByEnv[e] - targetTot[e];
        double dt1 = (sByEnv[e] + t.stations) - targetTot[e];
        double d = (dt1 * dt1) - (dt0 * dt0);
        double dv0 = vByEnv[e] - targetVolt[e];
        double dv1 = (vByEnv[e] + t.stations) - targetVolt[e];
        d += wVolt * ((dv1 * dv1) - (dv0 * dv0));
        if (d < bestDelta) {
          bestDelta = d;
          bestTi = ti;
        }
      }
      if (bestTi < 0) return null;
      RoomType t = types.get(bestTi);
      alloc[bestTi][e] += 1;
      sByEnv[e] += t.stations;
      roomsByEnv[e] += 1;
      vByEnv[e] += t.stations;
      voltRoomsByEnv[e] += 1;
    }

    // (3) Fill remaining rooms greedily (one by one) with best delta.
    boolean progress = true;
    while (progress) {
      progress = false;
      int bestTi = -1, bestE = -1;
      double bestDelta = Double.POSITIVE_INFINITY;
      for (int ti = 0; ti < tN; ti++) {
        RoomType t = types.get(ti);
        int used = 0;
        for (int ee = 0; ee < m; ee++) used += alloc[ti][ee];
        if (used >= t.count) continue;
        for (int e = 0; e < m; e++) {
          if (!typeFeasible[ti][e]) continue;
          double dt0 = sByEnv[e] - targetTot[e];
          double dt1 = (sByEnv[e] + t.stations) - targetTot[e];
          double d = (dt1 * dt1) - (dt0 * dt0);
          if (t.voltCapable) {
            double dv0 = vByEnv[e] - targetVolt[e];
            double dv1 = (vByEnv[e] + t.stations) - targetVolt[e];
            d += wVolt * ((dv1 * dv1) - (dv0 * dv0));
          }
          if (d < bestDelta) {
            bestDelta = d;
            bestTi = ti;
            bestE = e;
          }
        }
      }
      if (bestTi >= 0) {
        RoomType t = types.get(bestTi);
        alloc[bestTi][bestE] += 1;
        sByEnv[bestE] += t.stations;
        roomsByEnv[bestE] += 1;
        if (t.voltCapable) {
          vByEnv[bestE] += t.stations;
          voltRoomsByEnv[bestE] += 1;
        }
        progress = true;
      }
    }

    // quick sanity: all rooms assigned?
    for (int ti = 0; ti < tN; ti++) {
      int used = 0;
      for (int e = 0; e < m; e++) used += alloc[ti][e];
      if (used != types.get(ti).count) return null;
    }
    return alloc;
  }

  private static double objectiveFromTypeAlloc(List<RoomType> types, int[][] alloc, double[] tTot, double[] tVolt, double wVolt) {
    int m = tTot.length;
    int[] sBy = new int[m];
    int[] vBy = new int[m];
    for (int ti = 0; ti < types.size(); ti++) {
      RoomType t = types.get(ti);
      for (int e = 0; e < m; e++) {
        int c = alloc[ti][e];
        if (c == 0) continue;
        sBy[e] += c * t.stations;
        if (t.voltCapable) vBy[e] += c * t.stations;
      }
    }
    return objectiveFromAggregates(sBy, vBy, tTot, tVolt, wVolt);
  }
}

