package tr.testodasi.heuristic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Scheduler {
  public static final class EvalResult {
    public final int totalLateness;
    public final List<ProjectResult> projectResults;

    public EvalResult(int totalLateness, List<ProjectResult> projectResults) {
      this.totalLateness = totalLateness;
      this.projectResults = projectResults;
    }
  }

  private static final class ChamberInstance {
    final ChamberSpec spec;
    final Env env;
    final int[] stationAvail;

    ChamberInstance(ChamberSpec spec, Env env) {
      this.spec = spec;
      this.env = env;
      this.stationAvail = new int[spec.stations];
    }

    int bestStartAtOrAfter(int earliest) {
      int best = Integer.MAX_VALUE;
      for (int a : stationAvail) {
        best = Math.min(best, Math.max(a, earliest));
      }
      return best;
    }
  }

  private static final class Assignment {
    final ChamberInstance chamber;
    final int stationIdx;
    final int sampleIdx;
    final int start;
    final int end;

    Assignment(ChamberInstance chamber, int stationIdx, int sampleIdx, int start, int end) {
      this.chamber = chamber;
      this.stationIdx = stationIdx;
      this.sampleIdx = sampleIdx;
      this.start = start;
      this.end = end;
    }
  }

  public EvalResult evaluate(List<Project> projects, Map<String, Env> chamberEnv) {
    Objects.requireNonNull(projects);
    Objects.requireNonNull(chamberEnv);

    List<ChamberInstance> chambers = new ArrayList<>();
    for (ChamberSpec spec : Data.CHAMBERS) {
      Env env = chamberEnv.get(spec.id);
      if (env == null) throw new IllegalArgumentException("Missing env for chamber " + spec.id);
      if (env.humidity == Humidity.H85 && !spec.humidityAdjustable) {
        throw new IllegalArgumentException("Chamber " + spec.id + " cannot be assigned to 85% humidity");
      }
      chambers.add(new ChamberInstance(spec, env));
    }

    List<Project> ordered = projects.stream().map(Project::copy)
        .sorted(Comparator.comparingInt(p -> p.dueDateDays))
        .toList();

    int totalLateness = 0;
    List<ProjectResult> results = new ArrayList<>();

    for (Project p : ordered) {
      int[] sampleAvail = new int[p.samples];
      int projectCompletion = 0;

      // 1) GAS
      int gasEnd = 0;
      if (isRequired(p, "GAS_43")) {
        TestDef gas = get("GAS_43");
        Assignment a = assignBest(chambers, p.needsVoltage, gas.env, gas.durationDays, 0, 0, sampleAvail);
        gasEnd = a.end;
        projectCompletion = Math.max(projectCompletion, a.end);
      }

      // 2) PULLDOWN: S adet paralel job (sample başına 1)
      int pulldownEnd = gasEnd;
      if (isRequired(p, "PULLDOWN_43")) {
        TestDef pd = get("PULLDOWN_43");
        int maxEnd = gasEnd;
        for (int s = 0; s < p.samples; s++) {
          int earliest = Math.max(gasEnd, sampleAvail[s]);
          Assignment a = assignBest(chambers, p.needsVoltage, pd.env, pd.durationDays, earliest, s, sampleAvail);
          maxEnd = Math.max(maxEnd, a.end);
          projectCompletion = Math.max(projectCompletion, a.end);
        }
        pulldownEnd = maxEnd;
      }

      // 3) OTHER TESTS (Energy/Performance/Freezing/Temperature Rise)
      List<TestDef> others = new ArrayList<>();
      for (TestDef t : Data.TESTS) {
        if (t.category == TestCategory.OTHER && isRequired(p, t.id)) others.add(t);
      }
      others.sort(Comparator.comparingInt((TestDef t) -> t.durationDays).reversed());

      int maxStartOther = pulldownEnd;
      for (TestDef t : others) {
        Assignment a = assignBestOverSamples(chambers, p.needsVoltage, t.env, t.durationDays, pulldownEnd, sampleAvail);
        maxStartOther = Math.max(maxStartOther, a.start);
        projectCompletion = Math.max(projectCompletion, a.end);
      }

      // 4) CONSUMER USAGE: start >= maxStartOther
      for (TestDef t : Data.TESTS) {
        if (t.category != TestCategory.CONSUMER_USAGE) continue;
        if (!isRequired(p, t.id)) continue;
        int earliest = Math.max(pulldownEnd, maxStartOther);
        Assignment a = assignBestOverSamples(chambers, p.needsVoltage, t.env, t.durationDays, earliest, sampleAvail);
        projectCompletion = Math.max(projectCompletion, a.end);
      }

      int lateness = Math.max(0, projectCompletion - p.dueDateDays);
      totalLateness += lateness;
      results.add(new ProjectResult(p.id, projectCompletion, p.dueDateDays, lateness));
    }

    return new EvalResult(totalLateness, results);
  }

  private static boolean isRequired(Project p, String testId) {
    Integer idx = Data.TEST_INDEX.get(testId);
    return idx != null && p.required[idx];
  }

  private static TestDef get(String testId) {
    Integer idx = Data.TEST_INDEX.get(testId);
    if (idx == null) throw new IllegalArgumentException("Unknown test id: " + testId);
    return Data.TESTS.get(idx);
  }

  private static Assignment assignBestOverSamples(
      List<ChamberInstance> chambers,
      boolean needsVoltage,
      Env env,
      int duration,
      int earliest,
      int[] sampleAvail
  ) {
    Assignment best = null;
    for (int s = 0; s < sampleAvail.length; s++) {
      int e = Math.max(earliest, sampleAvail[s]);
      Assignment cand = assignBest(chambers, needsVoltage, env, duration, e, s, sampleAvail);
      if (best == null) {
        best = cand;
      } else if (cand.start < best.start || (cand.start == best.start && cand.end < best.end)) {
        best = cand;
      }
    }
    if (best == null) throw new IllegalStateException("No samples available");
    return best;
  }

  private static Assignment assignBest(
      List<ChamberInstance> chambers,
      boolean needsVoltage,
      Env env,
      int duration,
      int earliest,
      int sampleIdx,
      int[] sampleAvail
  ) {
    ChamberInstance bestCh = null;
    int bestStation = -1;
    int bestStart = Integer.MAX_VALUE;

    for (ChamberInstance ch : chambers) {
      if (!ch.env.equals(env)) continue;
      if (needsVoltage && !ch.spec.voltageCapable) continue;

      for (int i = 0; i < ch.stationAvail.length; i++) {
        int st = Math.max(ch.stationAvail[i], earliest);
        if (st < bestStart) {
          bestStart = st;
          bestCh = ch;
          bestStation = i;
        }
      }
    }

    if (bestCh == null) {
      throw new IllegalStateException("No eligible chamber for env=" + env + " needsVoltage=" + needsVoltage);
    }

    int start = bestStart;
    int end = start + duration;

    bestCh.stationAvail[bestStation] = end;
    sampleAvail[sampleIdx] = end;

    return new Assignment(bestCh, bestStation, sampleIdx, start, end);
  }
}
