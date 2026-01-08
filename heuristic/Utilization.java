package tr.testodasi.heuristic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Shared utilization calculations for schedules. */
public final class Utilization {
  private Utilization() {}

  public static final class StationKey {
    public final String chamberId;
    public final int stationIdx;

    public StationKey(String chamberId, int stationIdx) {
      this.chamberId = chamberId;
      this.stationIdx = stationIdx;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof StationKey other)) return false;
      return stationIdx == other.stationIdx && Objects.equals(chamberId, other.chamberId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(chamberId, stationIdx);
    }
  }

  public static final class StationUtil {
    public final long busyDays;
    public final double utilization;

    public StationUtil(long busyDays, double utilization) {
      this.busyDays = busyDays;
      this.utilization = utilization;
    }
  }

  public static final class ChamberUtil {
    public final String chamberId;
    public final int stations;
    public final long busyDaysTotal;
    public final long capacityDays;
    public final double utilization;

    public ChamberUtil(String chamberId, int stations, long busyDaysTotal, long capacityDays, double utilization) {
      this.chamberId = chamberId;
      this.stations = stations;
      this.busyDaysTotal = busyDaysTotal;
      this.capacityDays = capacityDays;
      this.utilization = utilization;
    }
  }

  public static final class Summary {
    public final int horizonDays;
    public final Map<StationKey, StationUtil> byStation;
    public final Map<String, ChamberUtil> byChamber;
    public final double avgStationUtilization;
    public final double maxStationUtilization;
    public final double avgChamberUtilization;

    public Summary(
        int horizonDays,
        Map<StationKey, StationUtil> byStation,
        Map<String, ChamberUtil> byChamber,
        double avgStationUtilization,
        double maxStationUtilization,
        double avgChamberUtilization
    ) {
      this.horizonDays = horizonDays;
      this.byStation = byStation;
      this.byChamber = byChamber;
      this.avgStationUtilization = avgStationUtilization;
      this.maxStationUtilization = maxStationUtilization;
      this.avgChamberUtilization = avgChamberUtilization;
    }
  }

  public static Summary compute(Solution sol) {
    int horizon = 0;
    for (Scheduler.ScheduledJob j : sol.schedule) {
      horizon = Math.max(horizon, j.end);
    }
    if (horizon <= 0) horizon = 1;

    Map<StationKey, Long> busyByStation = new HashMap<>();
    for (Scheduler.ScheduledJob j : sol.schedule) {
      StationKey k = new StationKey(j.chamberId, j.stationIdx);
      long dur = (long) (j.end - j.start);
      busyByStation.merge(k, dur, Long::sum);
    }

    Map<StationKey, StationUtil> stationUtil = new HashMap<>();
    double sumU = 0.0;
    double maxU = 0.0;
    for (Map.Entry<StationKey, Long> e : busyByStation.entrySet()) {
      double u = e.getValue() / (double) horizon;
      stationUtil.put(e.getKey(), new StationUtil(e.getValue(), u));
      sumU += u;
      maxU = Math.max(maxU, u);
    }
    double avgU = stationUtil.isEmpty() ? 0.0 : (sumU / stationUtil.size());

    // Chamber utilization (aggregate station capacity)
    Map<String, Integer> stationsCount = new HashMap<>();
    for (ChamberSpec c : Data.CHAMBERS) {
      stationsCount.put(c.id, c.stations);
    }
    Map<String, Long> busyByChamber = new HashMap<>();
    for (Map.Entry<StationKey, Long> e : busyByStation.entrySet()) {
      busyByChamber.merge(e.getKey().chamberId, e.getValue(), Long::sum);
    }
    Map<String, ChamberUtil> chamberUtil = new HashMap<>();
    double sumChU = 0.0;
    for (Map.Entry<String, Long> e : busyByChamber.entrySet()) {
      String chamberId = e.getKey();
      int stations = stationsCount.getOrDefault(chamberId, 0);
      long cap = (long) stations * (long) horizon;
      double u = cap == 0 ? 0.0 : (e.getValue() / (double) cap);
      chamberUtil.put(chamberId, new ChamberUtil(chamberId, stations, e.getValue(), cap, u));
      sumChU += u;
    }
    double avgChU = chamberUtil.isEmpty() ? 0.0 : (sumChU / chamberUtil.size());

    return new Summary(horizon, stationUtil, chamberUtil, avgU, maxU, avgChU);
  }
}

