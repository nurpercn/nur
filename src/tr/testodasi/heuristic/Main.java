package tr.testodasi.heuristic;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class Main {
  public static void main(String[] args) {
    boolean verbose = false;
    String dumpProjectId = null;
    for (String a : args) {
      if ("--verbose".equalsIgnoreCase(a) || "-v".equalsIgnoreCase(a)) {
        verbose = true;
      }
      if (a != null && a.startsWith("--dumpProject=")) {
        dumpProjectId = a.substring("--dumpProject=".length()).trim();
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
}
