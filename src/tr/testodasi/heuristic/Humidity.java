package tr.testodasi.heuristic;

public enum Humidity {
  NORMAL,
  H85;

  @Override
  public String toString() {
    return this == NORMAL ? "NORMAL" : "85%";
  }
}
