package tr.testodasi.heuristic;

import java.util.Objects;

public final class TestDef {
  public final String id;
  public final String name;
  public final Env env;
  public final int durationDays;
  public final TestCategory category;

  public TestDef(String id, String name, Env env, int durationDays, TestCategory category) {
    this.id = Objects.requireNonNull(id);
    this.name = Objects.requireNonNull(name);
    this.env = Objects.requireNonNull(env);
    if (durationDays <= 0) throw new IllegalArgumentException("durationDays must be positive");
    this.durationDays = durationDays;
    this.category = Objects.requireNonNull(category);
  }

  @Override
  public String toString() {
    return id + "(" + env + "," + durationDays + "d," + category + ")";
  }
}