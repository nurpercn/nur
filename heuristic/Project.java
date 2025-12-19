package tr.testodasi.heuristic;

import java.util.Arrays;
import java.util.Objects;

public final class Project {
  public final String id;
  public final int dueDateDays;
  public final boolean needsVoltage;
  /** required[i] corresponds to Data.TESTS.get(i) */
  public final boolean[] required;
  public final int samples;

  public Project(String id, int dueDateDays, boolean needsVoltage, boolean[] required, int samples) {
    this.id = Objects.requireNonNull(id);
    if (dueDateDays < 0) throw new IllegalArgumentException("dueDateDays must be >= 0");
    this.dueDateDays = dueDateDays;
    this.needsVoltage = needsVoltage;
    this.required = Objects.requireNonNull(required);
    if (samples <= 0) throw new IllegalArgumentException("samples must be positive");
    this.samples = samples;
  }

  public Project copy() {
    return new Project(id, dueDateDays, needsVoltage, Arrays.copyOf(required, required.length), samples);
  }
}