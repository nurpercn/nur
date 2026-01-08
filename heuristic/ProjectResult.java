package tr.testodasi.heuristic;

public final class ProjectResult {
  public final String projectId;
  public final int completionDay;
  public final int dueDate;
  public final int lateness;

  public ProjectResult(String projectId, int completionDay, int dueDate, int lateness) {
    this.projectId = projectId;
    this.completionDay = completionDay;
    this.dueDate = dueDate;
    this.lateness = lateness;
  }
}