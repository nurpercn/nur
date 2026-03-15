package tr.testodasi.heuristic;

import java.util.Objects;

public final class Env {
  public final int temperatureC;
  public final Humidity humidity;

  public Env(int temperatureC, Humidity humidity) {
    this.temperatureC = temperatureC;
    this.humidity = Objects.requireNonNull(humidity);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Env other)) return false;
    return temperatureC == other.temperatureC && humidity == other.humidity;
  }

  @Override
  public int hashCode() {
    return Objects.hash(temperatureC, humidity);
  }

  @Override
  public String toString() {
    return temperatureC + "C/" + humidity;
  }
}
