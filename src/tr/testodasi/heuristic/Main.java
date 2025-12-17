package tr.testodasi.heuristic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class Main {
  public static void main(String[] args) {
    boolean verbose = false;
    String dumpProjectId = null;
    boolean dumpFirst10 = false;
    String csvDir = null;
    for (String a : args) {
      if ("--verbose".equalsIgnoreCase(a) || "-v".equalsIgnoreCase(a)) {
        verbose = true;
      }
      if (a != null && a.startsWith("--dumpProject=")) {
        dumpProjectId = a.substring("--dumpProject=".length()).trim();
      }
      if ("--dumpFirst10".equalsIgnoreCase(a)) {
        dumpFirst10 = true;
      }
      if (a != null && a.startsWith("--csvDir=")) {
        csvDir = a.substring("--csvDir=".length()).trim();
      }
      if (a != null && a.startsWith("--dispatch=")) {
        String v = a.substring("--dispatch=".length()).trim().toUpperCase();
        if ("EDD".equals(v)) Data.PROJECT_DISPATCH_RULE = Data.ProjectDispatchRule.EDD;
        if ("ATC".equals(v)) Data.PROJECT_DISPATCH_RULE = Data.ProjectDispatchRule.ATC;
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
    if (csvDir != null && !csvDir.isBlank()) {
      try {
        exportCsv(best, Paths.get(csvDir));
        System.out.println();
        System.out.println("CSV exported to: " + Paths.get(csvDir).toAbsolutePath());
        System.out.println("- schedule_by_project.csv");
        System.out.println("- schedule_by_station.csv");
      } catch (IOException e) {
        throw new RuntimeException("Failed to export CSV to dir=" + csvDir, e);
      }
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

  private static void exportCsv(Solution sol, Path dir) throws IOException {
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
}
