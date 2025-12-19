package tr.testodasi.heuristic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public final class Main {
  public static void main(String[] args) {
    boolean verbose = false;
    String dumpProjectId = null;
    boolean dumpFirst10 = false;
    String csvDir = null;
    boolean csvFlag = false;
    boolean diagnose = false;
    for (int idx = 0; idx < args.length; idx++) {
      String a = args[idx];
      if ("--verbose".equalsIgnoreCase(a) || "-v".equalsIgnoreCase(a)) {
        verbose = true;
      }
      if (a != null && a.startsWith("--dumpProject=")) {
        dumpProjectId = a.substring("--dumpProject=".length()).trim();
      }
      if ("--dumpFirst10".equalsIgnoreCase(a)) {
        dumpFirst10 = true;
      }
      if ("--diagnose".equalsIgnoreCase(a) || "--diag".equalsIgnoreCase(a)) {
        diagnose = true;
      }
      // CSV arg parsing (case-insensitive, supports both --csvDir=path and --csvDir path)
      if (a != null && startsWithIgnoreCase(a, "--csvdir=")) {
        csvDir = a.substring("--csvdir=".length()).trim();
      } else if (a != null && startsWithIgnoreCase(a, "--csv=")) {
        csvDir = a.substring("--csv=".length()).trim();
      } else if ("--csv".equalsIgnoreCase(a)) {
        csvFlag = true;
      } else if ("--csvDir".equalsIgnoreCase(a) || "--csvdir".equalsIgnoreCase(a)) {
        // next token is dir if present
        if (idx + 1 < args.length) {
          csvDir = args[idx + 1].trim();
          idx++;
        } else {
          csvFlag = true;
        }
      }
      if (a != null && a.startsWith("--dispatch=")) {
        String v = a.substring("--dispatch=".length()).trim().toUpperCase();
        if ("EDD".equals(v)) Data.PROJECT_DISPATCH_RULE = Data.ProjectDispatchRule.EDD;
        if ("ATC".equals(v)) Data.PROJECT_DISPATCH_RULE = Data.ProjectDispatchRule.ATC;
      }
      if (a != null && a.startsWith("--mode=")) {
        String v = a.substring("--mode=".length()).trim().toUpperCase();
        if ("JOB".equals(v) || "JOB_BASED".equals(v)) Data.SCHEDULING_MODE = Data.SchedulingMode.JOB_BASED;
        if ("PROJECT".equals(v) || "PROJECT_BASED".equals(v)) Data.SCHEDULING_MODE = Data.SchedulingMode.PROJECT_BASED;
      }
      if (a != null && a.startsWith("--jobRule=")) {
        String v = a.substring("--jobRule=".length()).trim().toUpperCase();
        if ("EDD".equals(v)) Data.JOB_DISPATCH_RULE = Data.JobDispatchRule.EDD;
        if ("ATC".equals(v)) Data.JOB_DISPATCH_RULE = Data.JobDispatchRule.ATC;
        if ("MIN_SLACK".equals(v) || "SLACK".equals(v)) Data.JOB_DISPATCH_RULE = Data.JobDispatchRule.MIN_SLACK;
      }
      if (a != null && a.startsWith("--jobK=")) {
        try {
          Data.JOB_ATC_K = Double.parseDouble(a.substring("--jobK=".length()).trim());
        } catch (NumberFormatException ignored) {
          // ignore invalid
        }
      }
      if (a != null && a.startsWith("--atcK=")) {
        try {
          Data.ATC_K = Double.parseDouble(a.substring("--atcK=".length()).trim());
        } catch (NumberFormatException ignored) {
          // ignore invalid
        }
      }
      if (a != null && a.startsWith("--orderLS=")) {
        String v = a.substring("--orderLS=".length()).trim();
        Data.ENABLE_ORDER_LOCAL_SEARCH = "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
      }
      if (a != null && a.startsWith("--orderLSPasses=")) {
        try {
          Data.ORDER_LS_MAX_PASSES = Integer.parseInt(a.substring("--orderLSPasses=".length()).trim());
        } catch (NumberFormatException ignored) {
          // ignore invalid
        }
      }
      if (a != null && a.startsWith("--orderLSWindow=")) {
        try {
          Data.ORDER_LS_WINDOW = Integer.parseInt(a.substring("--orderLSWindow=".length()).trim());
        } catch (NumberFormatException ignored) {
          // ignore invalid
        }
      }
      if (a != null && a.startsWith("--orderLSMaxEvals=")) {
        try {
          Data.ORDER_LS_MAX_EVALS = Integer.parseInt(a.substring("--orderLSMaxEvals=".length()).trim());
        } catch (NumberFormatException ignored) {
          // ignore invalid
        }
      }
      if (a != null && a.startsWith("--roomLS=")) {
        String v = a.substring("--roomLS=".length()).trim();
        Data.ENABLE_ROOM_LOCAL_SEARCH = "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
      }
      if (a != null && a.startsWith("--roomLSMaxEvals=")) {
        try {
          Data.ROOM_LS_MAX_EVALS = Integer.parseInt(a.substring("--roomLSMaxEvals=".length()).trim());
        } catch (NumberFormatException ignored) {
          // ignore invalid
        }
      }
      if (a != null && a.startsWith("--roomLSSwap=")) {
        String v = a.substring("--roomLSSwap=".length()).trim();
        Data.ROOM_LS_ENABLE_SWAP = "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
      }
      if (a != null && a.startsWith("--roomLSMove=")) {
        String v = a.substring("--roomLSMove=".length()).trim();
        Data.ROOM_LS_ENABLE_MOVE = "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
      }
      if (a != null && a.startsWith("--roomLSIncludeSample=")) {
        String v = a.substring("--roomLSIncludeSample=".length()).trim();
        Data.ROOM_LS_INCLUDE_SAMPLE_HEURISTIC = "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
      }
      if (a != null && a.startsWith("--validate=")) {
        String v = a.substring("--validate=".length()).trim();
        Data.ENABLE_SCHEDULE_VALIDATION = "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
      }
      if (a != null && a.startsWith("--samples=")) {
        try {
          Data.INITIAL_SAMPLES = Integer.parseInt(a.substring("--samples=".length()).trim());
        } catch (NumberFormatException ignored) {
          // ignore invalid
        }
      }
    }
    HeuristicSolver solver = new HeuristicSolver(verbose);
    List<Solution> sols = solver.solve();

    Solution best = sols.stream().min(Comparator.comparingInt(s -> s.totalLateness)).orElseThrow();

    for (Solution s : sols) {
      printSolution(s);
      System.out.println();
    }

    System.out.println("====================");
    System.out.println("BEST (min total lateness): iter=" + best.iteration + " totalLateness=" + best.totalLateness);

    if (dumpProjectId != null && !dumpProjectId.isBlank()) {
      dumpProject(best, dumpProjectId);
    }

    // İstenen çıktı: 10 proje için test başlangıç/bitiş ve istasyon ataması.
    // Varsayılan olarak argüman verilirse yazdırır: --dumpFirst10
    if (dumpFirst10) {
      dumpFirstNProjects(best, 10);
    }

    // CSV export (best solution schedule)
    // Kullanım: --csvDir=output (klasör yoksa oluşturulur)
    // Alternatif: --csv (varsayılan ./csv_out)
    if ((csvDir != null && !csvDir.isBlank()) || csvFlag) {
      if (csvDir == null || csvDir.isBlank()) csvDir = "csv_out";
      try {
        int rows = exportCsv(best, Paths.get(csvDir));
        System.out.println();
        System.out.println("CSV exported to: " + Paths.get(csvDir).toAbsolutePath());
        System.out.println("- schedule_by_project.csv");
        System.out.println("- schedule_by_station.csv");
        System.out.println("Rows written: " + rows);
      } catch (IOException e) {
        throw new RuntimeException("Failed to export CSV to dir=" + csvDir, e);
      }
    }

    if (diagnose) {
      printDiagnostics(best);
    }
  }

  private static void printSolution(Solution s) {
    System.out.println("====================");
    System.out.println("ITERATION " + s.iteration);
    System.out.println("Total lateness = " + s.totalLateness);

    System.out.println("\nChamber setpoints (fixed for this iteration):");
    for (var c : Data.CHAMBERS) {
      Env env = s.chamberEnv.get(c.id);
      System.out.println("- " + c.id + " stations=" + c.stations + " volt=" + c.voltageCapable + " humAdj=" + c.humidityAdjustable + " => " + env);
    }

    System.out.println("\nProject sample counts:");
    s.projects.stream()
        .sorted(Comparator.comparing(p -> p.id))
        .forEach(p -> System.out.println("- " + p.id + " samples=" + p.samples + " due=" + p.dueDateDays + " needsVolt=" + p.needsVoltage));

    System.out.println("\nProject results:");
    s.results.stream()
        .sorted(Comparator.comparing(r -> r.projectId))
        .forEach(r -> System.out.println("- " + r.projectId + " completion=" + r.completionDay + " due=" + r.dueDate + " lateness=" + r.lateness));
  }

  private static void dumpProject(Solution sol, String projectId) {
    Objects.requireNonNull(sol);
    Objects.requireNonNull(projectId);
    System.out.println();
    System.out.println("====================");
    System.out.println("SCHEDULE DUMP for " + projectId + " (iter=" + sol.iteration + ")");
    sol.schedule.stream()
        .filter(j -> projectId.equals(j.projectId))
        .sorted(Comparator.comparingInt((Scheduler.ScheduledJob j) -> j.start).thenComparing(j -> j.testId))
        .forEach(j -> System.out.println(
            j.testId + " " + j.env +
                " sample=" + j.sampleIdx +
                " " + j.chamberId + "[st" + j.stationIdx + "]" +
                " start=" + j.start + " end=" + j.end
        ));
  }

  private static void dumpFirstNProjects(Solution sol, int n) {
    Objects.requireNonNull(sol);
    if (n <= 0) return;

    // P1..P10 (id sırasına göre) dump
    Set<String> wanted = new TreeSet<>((a, b) -> {
      int ia = parseProjectNum(a);
      int ib = parseProjectNum(b);
      return Integer.compare(ia, ib);
    });
    for (int i = 1; i <= n; i++) {
      wanted.add("P" + i);
    }

    System.out.println();
    System.out.println("====================");
    System.out.println("FIRST " + n + " PROJECTS - DETAILED SCHEDULE (iter=" + sol.iteration + ")");

    for (String pid : wanted) {
      System.out.println();
      System.out.println("---- " + pid + " ----");
      sol.schedule.stream()
          .filter(j -> pid.equals(j.projectId))
          .sorted(Comparator.comparingInt((Scheduler.ScheduledJob j) -> j.start).thenComparing(j -> j.testId))
          .forEach(j -> System.out.println(
              j.testId +
                  " env=" + j.env +
                  " sample=" + j.sampleIdx +
                  " room=" + j.chamberId +
                  " station=" + j.stationIdx +
                  " start=" + j.start +
                  " end=" + j.end
          ));
    }
  }

  private static int parseProjectNum(String pid) {
    if (pid == null) return Integer.MAX_VALUE;
    if (!pid.startsWith("P")) return Integer.MAX_VALUE;
    try {
      return Integer.parseInt(pid.substring(1));
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  private static int exportCsv(Solution sol, Path dir) throws IOException {
    Objects.requireNonNull(sol);
    Objects.requireNonNull(dir);
    Files.createDirectories(dir);

    Map<String, Project> projectById = new HashMap<>();
    for (Project p : sol.projects) {
      projectById.put(p.id, p);
    }

    // 1) By project
    Path byProject = dir.resolve("schedule_by_project.csv");
    try (BufferedWriter w = Files.newBufferedWriter(byProject)) {
      w.write("iteration,projectId,testId,category,tempC,humidity,durationDays,needsVoltage,dueDateDays,samples,sampleIdx,chamberId,stationIdx,startDay,endDay");
      w.newLine();
      sol.schedule.stream()
          .sorted(Comparator.comparing((Scheduler.ScheduledJob j) -> j.projectId)
              .thenComparingInt(j -> j.start)
              .thenComparing(j -> j.testId))
          .forEach(j -> writeRow(w, sol.iteration, j, projectById.get(j.projectId)));
    }

    // 2) By station (chamber+station timeline)
    Path byStation = dir.resolve("schedule_by_station.csv");
    try (BufferedWriter w = Files.newBufferedWriter(byStation)) {
      w.write("iteration,chamberId,stationIdx,startDay,endDay,projectId,testId,category,tempC,humidity,durationDays,needsVoltage,dueDateDays,samples,sampleIdx");
      w.newLine();
      sol.schedule.stream()
          .sorted(Comparator.comparing((Scheduler.ScheduledJob j) -> j.chamberId)
              .thenComparingInt(j -> j.stationIdx)
              .thenComparingInt(j -> j.start)
              .thenComparing(j -> j.projectId)
              .thenComparing(j -> j.testId))
          .forEach(j -> writeRowStation(w, sol.iteration, j, projectById.get(j.projectId)));
    }

    // 3) Utilization by station
    Path utilByStation = dir.resolve("utilization_by_station.csv");
    UtilizationSummary util = computeUtilization(sol);
    try (BufferedWriter w = Files.newBufferedWriter(utilByStation)) {
      w.write("iteration,horizonDays,chamberId,stationIdx,busyDays,utilization");
      w.newLine();
      util.byStation.entrySet().stream()
          .sorted((a, b) -> Double.compare(b.getValue().utilization, a.getValue().utilization))
          .forEach(e -> {
            StationKey k = e.getKey();
            StationUtil u = e.getValue();
            try {
              w.write(sol.iteration + "," + util.horizonDays + "," + k.chamberId + "," + k.stationIdx + "," +
                  u.busyDays + "," + fmt(u.utilization));
              w.newLine();
            } catch (IOException ex) {
              throw new RuntimeException(ex);
            }
          });
    }

    // 4) Utilization by chamber
    Path utilByChamber = dir.resolve("utilization_by_chamber.csv");
    try (BufferedWriter w = Files.newBufferedWriter(utilByChamber)) {
      w.write("iteration,horizonDays,chamberId,stations,busyDaysTotal,capacityDays,utilization");
      w.newLine();
      util.byChamber.entrySet().stream()
          .sorted((a, b) -> Double.compare(b.getValue().utilization, a.getValue().utilization))
          .forEach(e -> {
            ChamberUtil u = e.getValue();
            try {
              w.write(sol.iteration + "," + util.horizonDays + "," + u.chamberId + "," + u.stations + "," +
                  u.busyDaysTotal + "," + u.capacityDays + "," + fmt(u.utilization));
              w.newLine();
            } catch (IOException ex) {
              throw new RuntimeException(ex);
            }
          });
    }

    // 5) ENV summary (effective capacity vs workload)
    Path envSummary = dir.resolve("env_summary.csv");
    try (BufferedWriter w = Files.newBufferedWriter(envSummary)) {
      w.write("iteration,env,tempC,humidity,stations,workDays,workDaysPerStation");
      w.newLine();
      List<EnvRow> envRows = computeEnvSummary(sol, projectById);
      envRows.sort((a, b) -> Double.compare(b.workDaysPerStation, a.workDaysPerStation));
      for (EnvRow r : envRows) {
        w.write(sol.iteration + "," + r.env + "," + r.env.temperatureC + "," + r.env.humidity + "," +
            r.stations + "," + r.workDays + "," + fmt(r.workDaysPerStation));
        w.newLine();
      }
    }

    return sol.schedule.size();
  }

  private static void writeRow(BufferedWriter w, int iteration, Scheduler.ScheduledJob j, Project p) {
    try {
      w.write(iteration + "," + j.projectId + "," + j.testId + "," + j.category + "," +
          j.env.temperatureC + "," + j.env.humidity + "," + j.durationDays + "," +
          (p != null && p.needsVoltage) + "," + (p != null ? p.dueDateDays : "") + "," + (p != null ? p.samples : "") + "," +
          j.sampleIdx + "," + j.chamberId + "," + j.stationIdx + "," + j.start + "," + j.end);
      w.newLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void writeRowStation(BufferedWriter w, int iteration, Scheduler.ScheduledJob j, Project p) {
    try {
      w.write(iteration + "," + j.chamberId + "," + j.stationIdx + "," + j.start + "," + j.end + "," +
          j.projectId + "," + j.testId + "," + j.category + "," +
          j.env.temperatureC + "," + j.env.humidity + "," + j.durationDays + "," +
          (p != null && p.needsVoltage) + "," + (p != null ? p.dueDateDays : "") + "," + (p != null ? p.samples : "") + "," +
          j.sampleIdx);
      w.newLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean startsWithIgnoreCase(String s, String prefix) {
    if (s == null || prefix == null) return false;
    if (s.length() < prefix.length()) return false;
    return s.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  private static void printDiagnostics(Solution best) {
    System.out.println();
    System.out.println("====================");
    System.out.println("DIAGNOSTICS (why lateness can happen with many stations)");

    // 1) Effective stations per env (because chambers are fixed to 1 env)
    Map<Env, Integer> stationsByEnv = new HashMap<>();
    for (ChamberSpec c : Data.CHAMBERS) {
      Env env = best.chamberEnv.get(c.id);
      if (env == null) continue;
      stationsByEnv.merge(env, c.stations, Integer::sum);
    }
    System.out.println("Effective station capacity by ENV (only stations in matching rooms can run that test):");
    stationsByEnv.entrySet().stream()
        .sorted(Map.Entry.<Env, Integer>comparingByValue().reversed())
        .forEach(e -> System.out.println("- " + e.getKey() + " stations=" + e.getValue()));

    // 2) Demand per env using final sample counts (pulldown jobs = S)
    Map<String, Project> proj = new HashMap<>();
    for (Project p : best.projects) proj.put(p.id, p);
    Map<Env, Long> workByEnv = new HashMap<>();
    for (Project p : best.projects) {
      for (int ti = 0; ti < Data.TESTS.size(); ti++) {
        if (!p.required[ti]) continue;
        TestDef t = Data.TESTS.get(ti);
        int jobs = 1;
        if (t.category == TestCategory.PULLDOWN) jobs = p.samples;
        workByEnv.merge(t.env, (long) jobs * t.durationDays, Long::sum);
      }
    }
    System.out.println("\nENV workload (jobDays) / station (rough bottleneck indicator):");
    workByEnv.entrySet().stream()
        .sorted((a, b) -> {
          double ra = a.getValue() / (stationsByEnv.getOrDefault(a.getKey(), 1) * 1.0);
          double rb = b.getValue() / (stationsByEnv.getOrDefault(b.getKey(), 1) * 1.0);
          return Double.compare(rb, ra);
        })
        .forEach(e -> {
          int cap = stationsByEnv.getOrDefault(e.getKey(), 0);
          double ratio = cap == 0 ? Double.POSITIVE_INFINITY : (e.getValue() / (double) cap);
          System.out.println("- " + e.getKey() + " workDays=" + e.getValue() + " stations=" + cap + " workDaysPerStation=" + String.format("%.2f", ratio));
        });

    // 3) Theoretical lower bound with infinite stations AND infinite samples
    // LB∞ = gas + pulldownPhase + max(duration of any OTHER/CU test) because all can run in parallel.
    int impossible = 0;
    for (Project p : best.projects) {
      int gas = p.required[Data.TEST_INDEX.get("GAS_43")] ? Data.TESTS.get(Data.TEST_INDEX.get("GAS_43")).durationDays : 0;
      int pd = p.required[Data.TEST_INDEX.get("PULLDOWN_43")] ? Data.TESTS.get(Data.TEST_INDEX.get("PULLDOWN_43")).durationDays : 0;
      int maxDur = 0;
      for (int ti = 0; ti < Data.TESTS.size(); ti++) {
        if (!p.required[ti]) continue;
        TestDef t = Data.TESTS.get(ti);
        if (t.category == TestCategory.GAS || t.category == TestCategory.PULLDOWN) continue;
        maxDur = Math.max(maxDur, t.durationDays);
      }
      int lb = gas + pd + maxDur;
      if (p.dueDateDays < lb) impossible++;
    }
    System.out.println("\nLower-bound check (even with infinite stations & samples):");
    System.out.println("- Projects with dueDate < (gas + pulldown + max(other/cu duration)) : " + impossible + " / " + best.projects.size());
    System.out.println("  (If this count > 0, some lateness is mathematically unavoidable.)");

    // 4) Utilization report
    UtilizationSummary util = computeUtilization(best);
    System.out.println("\nUtilization summary (based on produced schedule):");
    System.out.println("- Horizon (max end) = " + util.horizonDays + " days");
    System.out.println("- Avg station utilization = " + fmt(util.avgStationUtilization));
    System.out.println("- Max station utilization = " + fmt(util.maxStationUtilization));
    System.out.println("- Avg chamber utilization = " + fmt(util.avgChamberUtilization));

    System.out.println("\nTop 10 busiest stations:");
    util.byStation.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue().utilization, a.getValue().utilization))
        .limit(10)
        .forEach(e -> {
          StationKey k = e.getKey();
          StationUtil u = e.getValue();
          System.out.println("- " + k.chamberId + "[st" + k.stationIdx + "] busyDays=" + u.busyDays + " util=" + fmt(u.utilization));
        });
  }

  private static String fmt(double x) {
    return String.format(Locale.ROOT, "%.4f", x);
  }

  private static final class StationKey {
    final String chamberId;
    final int stationIdx;

    StationKey(String chamberId, int stationIdx) {
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

  private static final class StationUtil {
    final long busyDays;
    final double utilization;

    StationUtil(long busyDays, double utilization) {
      this.busyDays = busyDays;
      this.utilization = utilization;
    }
  }

  private static final class ChamberUtil {
    final String chamberId;
    final int stations;
    final long busyDaysTotal;
    final long capacityDays;
    final double utilization;

    ChamberUtil(String chamberId, int stations, long busyDaysTotal, long capacityDays, double utilization) {
      this.chamberId = chamberId;
      this.stations = stations;
      this.busyDaysTotal = busyDaysTotal;
      this.capacityDays = capacityDays;
      this.utilization = utilization;
    }
  }

  private static final class UtilizationSummary {
    final int horizonDays;
    final Map<StationKey, StationUtil> byStation;
    final Map<String, ChamberUtil> byChamber;
    final double avgStationUtilization;
    final double maxStationUtilization;
    final double avgChamberUtilization;

    UtilizationSummary(
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

  private static UtilizationSummary computeUtilization(Solution sol) {
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

    return new UtilizationSummary(horizon, stationUtil, chamberUtil, avgU, maxU, avgChU);
  }

  private static final class EnvRow {
    final Env env;
    final int stations;
    final long workDays;
    final double workDaysPerStation;

    EnvRow(Env env, int stations, long workDays) {
      this.env = env;
      this.stations = stations;
      this.workDays = workDays;
      this.workDaysPerStation = stations == 0 ? Double.POSITIVE_INFINITY : (workDays / (double) stations);
    }
  }

  private static List<EnvRow> computeEnvSummary(Solution sol, Map<String, Project> projectById) {
    Map<Env, Integer> stationsByEnv = new HashMap<>();
    for (ChamberSpec c : Data.CHAMBERS) {
      Env env = sol.chamberEnv.get(c.id);
      if (env == null) continue;
      stationsByEnv.merge(env, c.stations, Integer::sum);
    }
    Map<Env, Long> workByEnv = new HashMap<>();
    for (Project p : sol.projects) {
      for (int ti = 0; ti < Data.TESTS.size(); ti++) {
        if (!p.required[ti]) continue;
        TestDef t = Data.TESTS.get(ti);
        int jobs = 1;
        if (t.category == TestCategory.PULLDOWN) jobs = p.samples;
        workByEnv.merge(t.env, (long) jobs * t.durationDays, Long::sum);
      }
    }
    List<EnvRow> rows = new ArrayList<>();
    for (Env env : stationsByEnv.keySet()) {
      rows.add(new EnvRow(env, stationsByEnv.getOrDefault(env, 0), workByEnv.getOrDefault(env, 0L)));
    }
    // include envs that have work but zero stations (shouldn't happen in feasible solution)
    for (Env env : workByEnv.keySet()) {
      if (!stationsByEnv.containsKey(env)) {
        rows.add(new EnvRow(env, 0, workByEnv.getOrDefault(env, 0L)));
      }
    }
    return rows;
  }
}