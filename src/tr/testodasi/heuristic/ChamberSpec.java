package tr.testodasi.heuristic;

import java.util.Objects;

public final class ChamberSpec {
  public final String id;
  public final int stations;
  public final boolean temperatureAdjustable;
  public final boolean humidityAdjustable;
  public final boolean voltageCapable;

  public ChamberSpec(String id, int stations, boolean temperatureAdjustable, boolean humidityAdjustable, boolean voltageCapable) {
    this.id = Objects.requireNonNull(id);
    if (stations <= 0) throw new IllegalArgumentException("stations must be positive");
    this.stations = stations;
    this.temperatureAdjustable = temperatureAdjustable;
    this.humidityAdjustable = humidityAdjustable;
    this.voltageCapable = voltageCapable;
  }

  @Override
  public String toString() {
    return id + " stations=" + stations + " humAdj=" + humidityAdjustable + " volt=" + voltageCapable;
  }
}
