package tr.testodasi.heuristic;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class Main {
  public static void main(String[] args) {
    // verbose=true => sample artırma adımlarını da yazdırır
    HeuristicSolver solver = new HeuristicSolver(true);
    List<Solution> sols = solver.solve();

    Solution best = sols.stream().min(Comparator.comparingInt(s -> s.totalLateness)).orElseThrow();

    for (Solution s : sols) {
      printSolution(s);
      System.out.println();
    }

    System.out.println("====================");
    System.out.println("BEST (min total lateness): iter=" + best.iteration + " totalLateness=" + best.totalLateness);
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
}
