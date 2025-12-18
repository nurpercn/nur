package com.scheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * TEST ODASI ÇİZELGELEME VE SAMPLE SAYISI OPTİMİZASYONU
 *
 * - Aşama 1: Oda set değerlerinin belirlenmesi (sıcaklık/nem sabit)
 * - Aşama 2: EDD tabanlı çizelgeleme (non-preemptive, istasyon + sample kısıtları)
 *
 * Çıktılar:
 * - output/room_settings.csv
 * - output/project_schedule.csv
 * - output/station_schedule.csv
 * - output/project_lateness.csv
 */
public class Main {
  // --------------------------- PROJECT INPUTS (Excel) ---------------------------

  /**
   * Proje test matrisi (Excel).
   * Kolon sırası:
   * Gas,PD, EE32,EE25,EE16, P25,P43,P38,P32,P16,P10, Frz,TR, CU10,CU25,CU32
   */
  public static final int[][] PROJECT_MATRIX = new int[][]{
      // Gas,PD, EE32,EE25,EE16, P25,P43,P38,P32,P16,P10, Frz,TR, CU10,CU25,CU32
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P1
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P2
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P3
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P4
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P5
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P6
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P7
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P8
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P9
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P10

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P11
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P12
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P13
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P14
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P15
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P16
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P17
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P18
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P19
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P20

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P21
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P22
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P23
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P24
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P25
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P26
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P27
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P28
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P29
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P30

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P31
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P32
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P33
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P34
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P35
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P36
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P37
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P38
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P39
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P40

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P41
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P42
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P43
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P44
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P45
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P46
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P47
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P48
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P49
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P50
  };

  /** Proje bazlı voltaj ihtiyacı (Excel NeedsVolt kolonu). Satır sayısı PROJECT_MATRIX ile aynı olmalı. */
  public static final int[] NEEDS_VOLT = new int[]{
      1, 1, 0, 0, 1, 1, 0, 0, 1, 1,
      0, 0, 1, 1, 0, 0, 1, 1, 0, 0,
      1, 1, 0, 0, 1, 1, 0, 0, 1, 1,
      0, 0, 1, 1, 0, 0, 1, 1, 0, 0,
      1, 1, 0, 0, 1, 1, 0, 0, 1, 0
  };

  /** Proje bazlı due date (Excel due date kolonu). Satır sayısı PROJECT_MATRIX ile aynı olmalı. */
  public static final int[] DUE_DATES = new int[]{
      140, 160, 120, 150, 185, 210, 140, 160,
      120, 150, 80, 100, 60, 90, 125, 150, 80,
      100, 60, 90, 125, 150, 80, 100, 60, 90,
      125, 150, 80, 100, 60, 90, 125, 150, 80,
      100, 90, 125, 150, 80, 100, 60, 90, 125,
      150, 80, 100, 60, 90, 125
  };

  public static void main(String[] args) throws Exception {
    Locale.setDefault(Locale.ROOT);

    var data = DataSet.create();

    // Stage 1: assign fixed room setpoints (temp/humidity) for whole horizon
    var stage1 = new Stage1RoomAssigner();
    stage1.assignRoomSetpoints(data);

    // Stage 2: EDD scheduling with per-project sample count search
    var scheduler = new Stage2EddScheduler();
    var result = scheduler.schedule(data, 3, 6, 365);

    // Outputs
    var outDir = Path.of("output");
    Files.createDirectories(outDir);
    CsvWriters.writeRoomSettings(outDir.resolve("room_settings.csv"), data.chambers());
    CsvWriters.writeProjectSchedule(outDir.resolve("project_schedule.csv"), result.allJobAllocations());
    CsvWriters.writeStationSchedule(outDir.resolve("station_schedule.csv"), result.allJobAllocations(), data);
    CsvWriters.writeProjectLateness(outDir.resolve("project_lateness.csv"), result.projectSummaries());

    System.out.println("Done.");
    System.out.println("Total lateness = " + result.totalLateness());
    System.out.println("Outputs written under: " + outDir.toAbsolutePath());
  }

  // --------------------------- DATA MODEL ---------------------------

  enum Humidity {
    NORMAL,
    H85
  }

  enum TestKind {
    GAS,
    PULLDOWN,
    OTHER,
    CONSUMER_USAGE
  }

  record TestSpec(
      String id,
      String name,
      int temperatureC,
      Humidity humidity,
      int durationDays,
      TestKind kind
  ) {
    TestSpec {
      Objects.requireNonNull(id);
      Objects.requireNonNull(name);
      Objects.requireNonNull(humidity);
      Objects.requireNonNull(kind);
    }
  }

  record RequirementKey(int temperatureC, Humidity humidity) {
    @Override public String toString() {
      return temperatureC + "C_" + humidity;
    }
  }

  static final class Project {
    final String id;
    final int dueDateDay;
    final boolean needsVoltage;
    final Map<String, Integer> requiredTests; // testId -> multiplicity in matrix (0/1)

    Project(String id, int dueDateDay, boolean needsVoltage, Map<String, Integer> requiredTests) {
      this.id = id;
      this.dueDateDay = dueDateDay;
      this.needsVoltage = needsVoltage;
      this.requiredTests = new LinkedHashMap<>(requiredTests);
    }
  }

  static final class Chamber {
    final String id;
    final int stations;
    final boolean tempAdjustable;
    final boolean humidityAdjustable;
    final boolean voltageAvailable;

    // Stage-1 결정leri:
    RequirementKey fixedSetpoint; // assigned

    Chamber(String id, int stations, boolean tempAdjustable, boolean humidityAdjustable, boolean voltageAvailable) {
      this.id = id;
      this.stations = stations;
      this.tempAdjustable = tempAdjustable;
      this.humidityAdjustable = humidityAdjustable;
      this.voltageAvailable = voltageAvailable;
    }

    boolean canRun(RequirementKey req, boolean projectNeedsVoltage) {
      if (fixedSetpoint == null) return false;
      if (!fixedSetpoint.equals(req)) return false;
      if (req.humidity() == Humidity.H85 && !humidityAdjustable) return false;
      if (projectNeedsVoltage && !voltageAvailable) return false;
      return true;
    }
  }

  record DataSet(List<TestSpec> tests, List<Chamber> chambers, List<Project> projects) {
    Map<String, TestSpec> testById() {
      return tests.stream().collect(Collectors.toMap(TestSpec::id, t -> t));
    }

    static DataSet create() {
      var tests = new ArrayList<TestSpec>();

      // --- TEST TABLOSU (görselden) ---
      tests.add(new TestSpec("GAS_43_N", "Gas Amount Determination", 43, Humidity.NORMAL, 10, TestKind.GAS));
      tests.add(new TestSpec("PD_43_N", "Pulldown", 43, Humidity.NORMAL, 5, TestKind.PULLDOWN));

      tests.add(new TestSpec("EE_32_N", "Energy Efficiency", 32, Humidity.NORMAL, 15, TestKind.OTHER));
      tests.add(new TestSpec("EE_25_N", "Energy Efficiency", 25, Humidity.NORMAL, 16, TestKind.OTHER));
      tests.add(new TestSpec("EE_16_N", "Energy Efficiency", 16, Humidity.NORMAL, 15, TestKind.OTHER));

      tests.add(new TestSpec("PERF_25_N", "Performance", 25, Humidity.NORMAL, 10, TestKind.OTHER));
      tests.add(new TestSpec("PERF_43_N", "Performance", 43, Humidity.NORMAL, 10, TestKind.OTHER));
      tests.add(new TestSpec("PERF_38_N", "Performance", 38, Humidity.NORMAL, 10, TestKind.OTHER));
      tests.add(new TestSpec("PERF_32_N", "Performance", 32, Humidity.NORMAL, 10, TestKind.OTHER));
      tests.add(new TestSpec("PERF_16_N", "Performance", 16, Humidity.NORMAL, 10, TestKind.OTHER));
      tests.add(new TestSpec("PERF_10_N", "Performance", 10, Humidity.NORMAL, 10, TestKind.OTHER));

      tests.add(new TestSpec("FRZ_25_N", "Freezing Capacity", 25, Humidity.NORMAL, 10, TestKind.OTHER));
      tests.add(new TestSpec("TR_25_N", "Temperature Rise", 25, Humidity.NORMAL, 5, TestKind.OTHER));

      tests.add(new TestSpec("CU_10_N", "Consumer Usage", 10, Humidity.NORMAL, 15, TestKind.CONSUMER_USAGE));
      tests.add(new TestSpec("CU_25_85", "Consumer Usage", 25, Humidity.H85, 15, TestKind.CONSUMER_USAGE));
      tests.add(new TestSpec("CU_32_85", "Consumer Usage", 32, Humidity.H85, 15, TestKind.CONSUMER_USAGE));

      // --- CHAMBER TABLOSU (görselden) ---
      var chambers = new ArrayList<Chamber>();
      chambers.add(new Chamber("T001", 8, true, false, false));
      chambers.add(new Chamber("T002", 8, true, true, true));
      chambers.add(new Chamber("T003", 8, true, true, true));
      chambers.add(new Chamber("T004", 8, true, true, true));
      chambers.add(new Chamber("T005", 8, true, true, true));
      chambers.add(new Chamber("T006", 8, true, false, false));
      chambers.add(new Chamber("T007", 8, true, false, false));
      chambers.add(new Chamber("T008", 12, true, false, true));
      chambers.add(new Chamber("T009", 12, true, false, false));
      chambers.add(new Chamber("T010", 12, true, true, false));
      chambers.add(new Chamber("T011", 12, true, true, false));
      chambers.add(new Chamber("T012", 12, true, true, false));
      chambers.add(new Chamber("T013", 12, true, true, false));
      chambers.add(new Chamber("T014", 6, true, true, false));
      chambers.add(new Chamber("T015", 6, true, true, false));
      chambers.add(new Chamber("T016", 6, true, false, true));
      chambers.add(new Chamber("T017", 6, true, false, true));
      chambers.add(new Chamber("T018", 6, true, false, true));
      chambers.add(new Chamber("T019", 6, true, false, true));
      chambers.add(new Chamber("T020", 6, true, false, true));
      chambers.add(new Chamber("T021", 6, true, false, true));
      chambers.add(new Chamber("T022", 6, true, false, true));
      chambers.add(new Chamber("T023", 6, true, false, true));
      chambers.add(new Chamber("T024", 8, true, true, false));
      chambers.add(new Chamber("T025", 8, true, true, true));
      chambers.add(new Chamber("T026", 12, true, false, true));
      chambers.add(new Chamber("T027", 12, true, false, true));
      chambers.add(new Chamber("T028", 12, true, false, true));

      // --- PROJECT TEST MATRİSİ (Excel) ---
      var projects = new ArrayList<Project>();

      if (PROJECT_MATRIX.length != NEEDS_VOLT.length || PROJECT_MATRIX.length != DUE_DATES.length) {
        throw new IllegalStateException("PROJECT_MATRIX / NEEDS_VOLT / DUE_DATES length mismatch. "
            + "matrix=" + PROJECT_MATRIX.length
            + " needsVolt=" + NEEDS_VOLT.length
            + " dueDates=" + DUE_DATES.length);
      }

      final String[] columns = new String[]{
          "GAS_43_N",  // Gas
          "PD_43_N",   // PD
          "EE_32_N",   // EE32
          "EE_25_N",   // EE25
          "EE_16_N",   // EE16
          "PERF_25_N", // P25
          "PERF_43_N", // P43
          "PERF_38_N", // P38
          "PERF_32_N", // P32
          "PERF_16_N", // P16
          "PERF_10_N", // P10
          "FRZ_25_N",  // Frz
          "TR_25_N",   // TR
          "CU_10_N",   // CU10
          "CU_25_85",  // CU25
          "CU_32_85"   // CU32
      };

      for (int i = 0; i < PROJECT_MATRIX.length; i++) {
        String pid = "P" + (i + 1);
        int[] row = PROJECT_MATRIX[i];
        if (row.length != columns.length) {
          throw new IllegalStateException("PROJECT_MATRIX row " + (i + 1) + " has " + row.length
              + " columns, expected " + columns.length);
        }

        var req = new LinkedHashMap<String, Integer>();
        for (int c = 0; c < columns.length; c++) {
          int v = row[c];
          if (v != 0 && v != 1) {
            throw new IllegalStateException("PROJECT_MATRIX[" + (i + 1) + "][" + c + "] must be 0/1, got " + v);
          }
          req.put(columns[c], v);
        }

        int due = DUE_DATES[i];
        boolean needsV = NEEDS_VOLT[i] == 1;
        projects.add(new Project(pid, due, needsV, req));
      }

      return new DataSet(tests, chambers, projects);
    }
  }

  // --------------------------- SCHEDULE MODEL ---------------------------

  record Interval(int startDay, int endDay) {
    Interval {
      if (startDay < 0 || endDay < 0 || endDay <= startDay) {
        throw new IllegalArgumentException("Invalid interval: " + startDay + " - " + endDay);
      }
    }

    boolean overlaps(int s, int e) {
      return s < endDay && e > startDay;
    }
  }

  static final class Calendar {
    private final List<Interval> busy = new ArrayList<>();

    Calendar copy() {
      var c = new Calendar();
      c.busy.addAll(this.busy);
      return c;
    }

    boolean isFree(int start, int dur) {
      int end = start + dur;
      for (var it : busy) {
        if (it.overlaps(start, end)) return false;
      }
      return true;
    }

    int nextConflictEnd(int start, int dur) {
      int end = start + dur;
      int next = -1;
      for (var it : busy) {
        if (it.overlaps(start, end)) {
          next = Math.max(next, it.endDay());
        }
      }
      return next;
    }

    void add(int start, int dur) {
      int end = start + dur;
      if (!isFree(start, dur)) {
        throw new IllegalStateException("Calendar overlap at " + start + " dur " + dur);
      }
      busy.add(new Interval(start, end));
    }
  }

  static final class StationKey {
    final String chamberId;
    final int stationIndex; // 1-based for output readability

    StationKey(String chamberId, int stationIndex) {
      this.chamberId = chamberId;
      this.stationIndex = stationIndex;
    }

    @Override public String toString() {
      return chamberId + "_S" + stationIndex;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof StationKey that)) return false;
      return stationIndex == that.stationIndex && Objects.equals(chamberId, that.chamberId);
    }

    @Override public int hashCode() {
      return Objects.hash(chamberId, stationIndex);
    }
  }

  record JobAllocation(
      String projectId,
      String jobId,
      String testId,
      String testName,
      int temperatureC,
      Humidity humidity,
      boolean needsVoltage,
      String chamberId,
      int stationIndex,
      int sampleIndex,
      int startDay,
      int endDay,
      int durationDays
  ) {}

  record ProjectSummary(String projectId, int dueDateDay, int completionDay, int lateness, int samplesUsed) {}

  record ScheduleResult(
      List<JobAllocation> allJobAllocations,
      List<ProjectSummary> projectSummaries,
      int totalLateness
  ) {}

  static final class ScheduleState {
    final Map<StationKey, Calendar> stationCalendars;

    ScheduleState(Map<StationKey, Calendar> stationCalendars) {
      this.stationCalendars = stationCalendars;
    }

    ScheduleState copy() {
      var m = new HashMap<StationKey, Calendar>();
      for (var e : stationCalendars.entrySet()) {
        m.put(e.getKey(), e.getValue().copy());
      }
      return new ScheduleState(m);
    }
  }

  // --------------------------- STAGE 1 ---------------------------

  static final class Stage1RoomAssigner {
    void assignRoomSetpoints(DataSet data) {
      var testById = data.testById();

      // All required requirement keys (from all tests that appear in any project)
      var requiredKeys = new TreeMap<String, RequirementKey>();
      for (var p : data.projects()) {
        for (var e : p.requiredTests.entrySet()) {
          if (e.getValue() == null || e.getValue() <= 0) continue;
          var t = testById.get(e.getKey());
          if (t == null) continue;
          var key = new RequirementKey(t.temperatureC(), t.humidity());
          requiredKeys.put(key.toString(), key);
        }
      }

      if (requiredKeys.isEmpty()) {
        throw new IllegalStateException("No required tests found.");
      }

      // Estimate load per requirement (station-days), split by voltage/non-voltage
      record Load(long nonVolt, long volt) {}
      var loadMap = new LinkedHashMap<RequirementKey, Load>();
      for (var key : requiredKeys.values()) {
        long nv = 0, vv = 0;
        for (var p : data.projects()) {
          boolean needsV = p.needsVoltage;
          for (var e : p.requiredTests.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            var t = testById.get(e.getKey());
            if (t == null) continue;
            var k2 = new RequirementKey(t.temperatureC(), t.humidity());
            if (!k2.equals(key)) continue;

            // multiplicity: Pulldown -> 3 jobs, else 1 job (matrix has 0/1)
            int mult = (t.kind() == TestKind.PULLDOWN) ? 3 : 1;
            long add = (long) mult * t.durationDays();
            if (needsV) vv += add; else nv += add;
          }
        }
        loadMap.put(key, new Load(nv, vv));
      }

      // Build chamber lists
      var chambers = new ArrayList<>(data.chambers());

      // Feasibility: for each key at least one chamber eligible (humidity 85 -> humidityAdjustable)
      for (var key : requiredKeys.values()) {
        boolean ok = chambers.stream().anyMatch(c -> c.tempAdjustable && (key.humidity() != Humidity.H85 || c.humidityAdjustable));
        if (!ok) {
          throw new IllegalStateException("No feasible chamber for requirement: " + key);
        }
      }

      // Greedy assignment: first guarantee one chamber per key, then distribute remaining by load/stations.
      // Sort keys by total load desc
      var keysByLoad = loadMap.entrySet().stream()
          .sorted(Comparator.<Map.Entry<RequirementKey, Load>>comparingLong(e -> e.getValue().nonVolt() + e.getValue().volt()).reversed())
          .map(Map.Entry::getKey)
          .toList();

      // Initialize tracking
      var assignedStationsTotal = new HashMap<RequirementKey, Integer>();
      var assignedStationsVoltCapable = new HashMap<RequirementKey, Integer>();
      for (var k : loadMap.keySet()) {
        assignedStationsTotal.put(k, 0);
        assignedStationsVoltCapable.put(k, 0);
      }

      // Helper to pick best key for a chamber (balance load per station)
      var unassigned = new ArrayList<>(chambers);

      // Step-1: ensure each key has at least one chamber
      for (var key : keysByLoad) {
        Chamber pick = null;
        // prefer voltage chamber if there is voltage load
        boolean preferVolt = loadMap.get(key).volt() > 0;
        for (var c : unassigned) {
          if (key.humidity() == Humidity.H85 && !c.humidityAdjustable) continue;
          if (!c.tempAdjustable) continue;
          if (preferVolt && c.voltageAvailable) {
            pick = c;
            break;
          }
          if (pick == null) pick = c;
        }
        if (pick == null) {
          // should not happen due to feasibility check
          throw new IllegalStateException("Unable to assign chamber for requirement: " + key);
        }
        pick.fixedSetpoint = key;
        unassigned.remove(pick);
        assignedStationsTotal.merge(key, pick.stations, Integer::sum);
        if (pick.voltageAvailable) assignedStationsVoltCapable.merge(key, pick.stations, Integer::sum);
      }

      // Step-2: assign remaining chambers to best key to balance ratios
      for (var c : unassigned) {
        RequirementKey bestKey = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (var key : keysByLoad) {
          if (key.humidity() == Humidity.H85 && !c.humidityAdjustable) continue;
          if (!c.tempAdjustable) continue;

          var load = loadMap.get(key);
          int sTot = assignedStationsTotal.getOrDefault(key, 0);
          int sVolt = assignedStationsVoltCapable.getOrDefault(key, 0);

          // score = max( nonVoltLoad/(stations), voltLoad/(voltStations) ) after assignment
          int newTot = sTot + c.stations;
          int newVolt = sVolt + (c.voltageAvailable ? c.stations : 0);

          double rNon = newTot > 0 ? (double) load.nonVolt() / newTot : Double.POSITIVE_INFINITY;
          double rVolt;
          if (load.volt() == 0) rVolt = 0.0;
          else rVolt = newVolt > 0 ? (double) load.volt() / newVolt : Double.POSITIVE_INFINITY;

          double score = Math.max(rNon, rVolt);
          if (score < bestScore) {
            bestScore = score;
            bestKey = key;
          }
        }

        if (bestKey == null) {
          // fallback (shouldn't happen)
          bestKey = keysByLoad.get(0);
        }
        c.fixedSetpoint = bestKey;
        assignedStationsTotal.merge(bestKey, c.stations, Integer::sum);
        if (c.voltageAvailable) assignedStationsVoltCapable.merge(bestKey, c.stations, Integer::sum);
      }
    }
  }

  // --------------------------- STAGE 2 ---------------------------

  static final class Stage2EddScheduler {
    ScheduleResult schedule(DataSet data, int minSamples, int maxSamples, int maxHorizonDays) {
      var testById = data.testById();
      var edd = data.projects().stream()
          .sorted(Comparator.comparingInt((Project p) -> p.dueDateDay).thenComparing(p -> p.id))
          .toList();

      // Global station calendars (per chamber station)
      var stationCalendars = new HashMap<StationKey, Calendar>();
      for (var ch : data.chambers()) {
        for (int s = 1; s <= ch.stations; s++) {
          stationCalendars.put(new StationKey(ch.id, s), new Calendar());
        }
      }
      var state = new ScheduleState(stationCalendars);

      var allAlloc = new ArrayList<JobAllocation>();
      var summaries = new ArrayList<ProjectSummary>();
      int totalLate = 0;

      for (var p : edd) {
        // try different sample counts for this project, pick best (min completion => min lateness)
        Attempt best = null;
        for (int samples = Math.max(3, minSamples); samples <= maxSamples; samples++) {
          var attempt = scheduleSingleProject(data, state.copy(), p, testById, samples, maxHorizonDays);
          if (attempt.isEmpty()) continue;
          if (best == null) best = attempt.get();
          else {
            if (attempt.get().completionDay < best.completionDay) best = attempt.get();
            else if (attempt.get().completionDay == best.completionDay && attempt.get().samplesUsed < best.samplesUsed) best = attempt.get();
          }
        }

        if (best == null) {
          throw new IllegalStateException("No feasible schedule for project " + p.id + " within horizon " + maxHorizonDays);
        }

        // commit best into global state
        state = best.committedState;
        allAlloc.addAll(best.allocations);

        int lateness = Math.max(0, best.completionDay - p.dueDateDay);
        totalLate += lateness;
        summaries.add(new ProjectSummary(p.id, p.dueDateDay, best.completionDay, lateness, best.samplesUsed));
      }

      return new ScheduleResult(allAlloc, summaries, totalLate);
    }

    private record Attempt(ScheduleState committedState, List<JobAllocation> allocations, int completionDay, int samplesUsed) {}

    private Optional<Attempt> scheduleSingleProject(
        DataSet data,
        ScheduleState state,
        Project project,
        Map<String, TestSpec> testById,
        int samples,
        int maxHorizonDays
    ) {
      // Sample calendars are per-project
      var sampleCals = new ArrayList<Calendar>();
      for (int i = 0; i < samples; i++) sampleCals.add(new Calendar());

      var allocations = new ArrayList<JobAllocation>();
      int jobSeq = 1;

      // Build required tests (expand multiplicity)
      List<TestSpec> gas = new ArrayList<>();
      List<TestSpec> pulldown = new ArrayList<>();
      List<TestSpec> other = new ArrayList<>();
      List<TestSpec> cu = new ArrayList<>();

      for (var e : project.requiredTests.entrySet()) {
        if (e.getValue() == null || e.getValue() <= 0) continue;
        var t = testById.get(e.getKey());
        if (t == null) continue;
        switch (t.kind()) {
          case GAS -> gas.add(t);
          case PULLDOWN -> pulldown.add(t);
          case OTHER -> other.add(t);
          case CONSUMER_USAGE -> cu.add(t);
        }
      }

      // GAS: first, 1 job if present
      int gasCompletion = 0;
      if (!gas.isEmpty()) {
        var t = gas.get(0);
        var alloc = allocateEarliest(state, data, project, sampleCals, t, 0, Optional.empty(), maxHorizonDays);
        if (alloc == null) return Optional.empty();
        allocations.add(new JobAllocation(
            project.id, "J" + (jobSeq++), t.id(), t.name(), t.temperatureC(), t.humidity(),
            project.needsVoltage, alloc.chamberId, alloc.stationIndex, alloc.sampleIndex, alloc.startDay, alloc.endDay, t.durationDays()
        ));
        gasCompletion = alloc.endDay;
      }

      // PULLDOWN: 3 parallel jobs, distinct samples (at least 3 required)
      int pulldownComplete = gasCompletion;
      if (!pulldown.isEmpty()) {
        if (samples < 3) return Optional.empty();
        var t = pulldown.get(0);
        int latestEnd = gasCompletion;
        for (int i = 0; i < 3; i++) {
          int fixedSample = i; // distinct sample
          var alloc = allocateEarliest(state, data, project, sampleCals, t, gasCompletion, Optional.of(fixedSample), maxHorizonDays);
          if (alloc == null) return Optional.empty();
          allocations.add(new JobAllocation(
              project.id, "J" + (jobSeq++), t.id(), t.name(), t.temperatureC(), t.humidity(),
              project.needsVoltage, alloc.chamberId, alloc.stationIndex, alloc.sampleIndex, alloc.startDay, alloc.endDay, t.durationDays()
          ));
          latestEnd = Math.max(latestEnd, alloc.endDay);
        }
        pulldownComplete = latestEnd;
      }

      // OTHER: no precedence among themselves, schedule after pulldown complete
      other.sort(Comparator.comparingInt(TestSpec::durationDays).reversed());
      int maxStartOther = pulldownComplete;
      for (var t : other) {
        var alloc = allocateEarliest(state, data, project, sampleCals, t, pulldownComplete, Optional.empty(), maxHorizonDays);
        if (alloc == null) return Optional.empty();
        maxStartOther = Math.max(maxStartOther, alloc.startDay);
        allocations.add(new JobAllocation(
            project.id, "J" + (jobSeq++), t.id(), t.name(), t.temperatureC(), t.humidity(),
            project.needsVoltage, alloc.chamberId, alloc.stationIndex, alloc.sampleIndex, alloc.startDay, alloc.endDay, t.durationDays()
        ));
      }

      // CONSUMER USAGE: after all other tests have started (maxStartOther)
      // CU tests may overlap with other tests.
      for (var t : cu) {
        int earliest = Math.max(pulldownComplete, maxStartOther);
        var alloc = allocateEarliest(state, data, project, sampleCals, t, earliest, Optional.empty(), maxHorizonDays);
        if (alloc == null) return Optional.empty();
        allocations.add(new JobAllocation(
            project.id, "J" + (jobSeq++), t.id(), t.name(), t.temperatureC(), t.humidity(),
            project.needsVoltage, alloc.chamberId, alloc.stationIndex, alloc.sampleIndex, alloc.startDay, alloc.endDay, t.durationDays()
        ));
      }

      int completion = allocations.stream().mapToInt(JobAllocation::endDay).max().orElse(0);
      return Optional.of(new Attempt(state, allocations, completion, samples));
    }

    private static final class AllocationCore {
      final String chamberId;
      final int stationIndex;
      final int sampleIndex;
      final int startDay;
      final int endDay;

      AllocationCore(String chamberId, int stationIndex, int sampleIndex, int startDay, int endDay) {
        this.chamberId = chamberId;
        this.stationIndex = stationIndex;
        this.sampleIndex = sampleIndex;
        this.startDay = startDay;
        this.endDay = endDay;
      }
    }

    private AllocationCore allocateEarliest(
        ScheduleState state,
        DataSet data,
        Project project,
        List<Calendar> sampleCals,
        TestSpec test,
        int earliestStart,
        Optional<Integer> fixedSample,
        int maxHorizonDays
    ) {
      var req = new RequirementKey(test.temperatureC(), test.humidity());

      // Eligible stations (match chamber setpoint + voltage constraint)
      List<StationKey> eligible = new ArrayList<>();
      Map<String, Chamber> chamberById = data.chambers().stream().collect(Collectors.toMap(c -> c.id, c -> c));
      for (var sk : state.stationCalendars.keySet()) {
        var ch = chamberById.get(sk.chamberId);
        if (ch == null) continue;
        if (ch.canRun(req, project.needsVoltage)) eligible.add(sk);
      }

      if (eligible.isEmpty()) return null;

      List<Integer> sampleChoices;
      if (fixedSample.isPresent()) sampleChoices = List.of(fixedSample.get());
      else {
        sampleChoices = new ArrayList<>();
        for (int i = 0; i < sampleCals.size(); i++) sampleChoices.add(i);
      }

      AllocationCore best = null;

      for (var sk : eligible) {
        var stationCal = state.stationCalendars.get(sk);
        for (int sIdx : sampleChoices) {
          var sampleCal = sampleCals.get(sIdx);

          int t = earliestStart;
          while (t <= maxHorizonDays) {
            if (stationCal.isFree(t, test.durationDays()) && sampleCal.isFree(t, test.durationDays())) {
              int end = t + test.durationDays();
              if (best == null || t < best.startDay) {
                best = new AllocationCore(sk.chamberId, sk.stationIndex, sIdx + 1, t, end);
              }
              break; // earliest for this pair
            }

            int nextS = stationCal.nextConflictEnd(t, test.durationDays());
            int nextP = sampleCal.nextConflictEnd(t, test.durationDays());
            int next = Math.max(t + 1, Math.max(nextS, nextP));
            if (nextS < 0 && nextP < 0) next = t + 1;
            t = next;
          }
        }
      }

      if (best == null) return null;

      // commit reservations
      state.stationCalendars.get(new StationKey(best.chamberId, best.stationIndex)).add(best.startDay, test.durationDays());
      sampleCals.get(best.sampleIndex - 1).add(best.startDay, test.durationDays());
      return best;
    }
  }

  // --------------------------- CSV OUTPUT ---------------------------

  static final class CsvWriters {
    static void writeRoomSettings(Path path, List<Chamber> chambers) throws IOException {
      var lines = new ArrayList<String>();
      lines.add("chamber,stations,humidityAdjustable,voltageAvailable,temperatureC,humidity");
      for (var c : chambers) {
        var sp = c.fixedSetpoint;
        lines.add(String.join(",",
            c.id,
            Integer.toString(c.stations),
            Boolean.toString(c.humidityAdjustable),
            Boolean.toString(c.voltageAvailable),
            Integer.toString(sp.temperatureC()),
            sp.humidity().toString()
        ));
      }
      Files.write(path, lines);
    }

    static void writeProjectSchedule(Path path, List<JobAllocation> allocs) throws IOException {
      var lines = new ArrayList<String>();
      lines.add("project,jobId,testId,testName,tempC,humidity,needsVoltage,chamber,station,sample,startDay,endDay,durationDays");
      for (var a : allocs.stream()
          .sorted(Comparator.comparingInt((JobAllocation x) -> projectNum(x.projectId()))
              .thenComparing(JobAllocation::projectId)
              .thenComparingInt(JobAllocation::startDay))
          .toList()) {
        lines.add(String.join(",",
            a.projectId(),
            a.jobId(),
            a.testId(),
            escape(a.testName()),
            Integer.toString(a.temperatureC()),
            a.humidity().toString(),
            Boolean.toString(a.needsVoltage()),
            a.chamberId(),
            Integer.toString(a.stationIndex()),
            Integer.toString(a.sampleIndex()),
            Integer.toString(a.startDay()),
            Integer.toString(a.endDay()),
            Integer.toString(a.durationDays())
        ));
      }
      Files.write(path, lines);
    }

    static void writeStationSchedule(Path path, List<JobAllocation> allocs, DataSet data) throws IOException {
      var lines = new ArrayList<String>();
      lines.add("chamber,station,project,jobId,testId,startDay,endDay");
      for (var a : allocs.stream()
          .sorted(Comparator.comparing(JobAllocation::chamberId).thenComparingInt(JobAllocation::stationIndex).thenComparingInt(JobAllocation::startDay))
          .toList()) {
        lines.add(String.join(",",
            a.chamberId(),
            Integer.toString(a.stationIndex()),
            a.projectId(),
            a.jobId(),
            a.testId(),
            Integer.toString(a.startDay()),
            Integer.toString(a.endDay())
        ));
      }
      Files.write(path, lines);
    }

    static void writeProjectLateness(Path path, List<ProjectSummary> sums) throws IOException {
      var lines = new ArrayList<String>();
      lines.add("project,dueDateDay,completionDay,lateness,samplesUsed");
      for (var s : sums.stream()
          .sorted(Comparator.comparingInt((ProjectSummary x) -> projectNum(x.projectId()))
              .thenComparing(ProjectSummary::projectId))
          .toList()) {
        lines.add(String.join(",",
            s.projectId(),
            Integer.toString(s.dueDateDay()),
            Integer.toString(s.completionDay()),
            Integer.toString(s.lateness()),
            Integer.toString(s.samplesUsed())
        ));
      }
      Files.write(path, lines);
    }

    private static String escape(String s) {
      if (s.contains(",") || s.contains("\"")) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
      }
      return s;
    }

    private static int projectNum(String projectId) {
      // expects "P<number>"
      if (projectId == null || projectId.length() < 2) return Integer.MAX_VALUE;
      try {
        return Integer.parseInt(projectId.substring(1));
      } catch (NumberFormatException ignored) {
        return Integer.MAX_VALUE;
      }
    }
  }
}

