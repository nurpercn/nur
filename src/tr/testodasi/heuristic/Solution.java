package tr.testodasi.heuristic;

import java.util.List;
import java.util.Map;

public final class Solution {
  public final int iteration;
  public final int totalLateness;
  public final List<Project> projects; // includes final samples
  public final Map<String, Env> chamberEnv; // chamberId -> assigned env
  public final List<ProjectResult> results;

  public Solution(int iteration, int totalLateness, List<Project> projects, Map<String, Env> chamberEnv, List<ProjectResult> results) {
    this.iteration = iteration;
    this.totalLateness = totalLateness;
    this.projects = projects;
    this.chamberEnv = chamberEnv;
    this.results = results;
  }
}
