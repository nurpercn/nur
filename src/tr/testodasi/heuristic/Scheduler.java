package tr.testodasi.heuristic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Scheduler {
  public static final class ScheduledJob {
    public final String projectId;
    public final String testId;
    public final TestCategory category;
    public final Env env;
    public final int durationDays;
    public final String chamberId;
    public final int stationIdx;
    public final int sampleIdx;
    public final int start;
    public final int end;

    public ScheduledJob(
        String projectId,
        String testId,
        TestCategory category,
        Env env,
        int durationDays,
        String chamberId,
        int stationIdx,
        int sampleIdx,
        int start,
        int end
    ) {
      this.projectId = projectId;
      this.testId = testId;
      this.category = category;
      this.env = env;
      this.durationDays = durationDays;
      this.chamberId = chamberId;
      this.stationIdx = stationIdx;
      this.sampleIdx = sampleIdx;
      this.start = start;
      this.end = end;
    }
  }

  public static final class EvalResult {
    public final int totalLateness;
    public final List<ProjectResult> projectResults;
    public final List<ScheduledJob> schedule;

    public EvalResult(int totalLateness, List<ProjectResult> projectResults, List<ScheduledJob> schedule) {
      this.totalLateness = totalLateness;
      this.projectResults = projectResults;
      this.schedule = schedule;
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

  private static final class Planned {
    final ChamberInstance chamber;
    final int stationIdx;
    final int sampleIdx;
    final int start;
    final int end;

    Planned(ChamberInstance chamber, int stationIdx, int sampleIdx, int start, int end) {
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

    List<Project> remaining = new ArrayList<>(projects.stream().map(Project::copy).toList());

    int totalLateness = 0;
    List<ProjectResult> results = new ArrayList<>();
    List<ScheduledJob> schedule = new ArrayList<>();

    while (!remaining.isEmpty()) {
      Project p = pickNextProject(remaining, chambers);
      int[] sampleAvail = new int[p.samples];
      int projectCompletion = 0;

      // 1) GAS
      int gasEnd = 0;
      if (isRequired(p, "GAS_43")) {
        TestDef gas = get("GAS_43");
        Assignment a = assignBest(chambers, p.needsVoltage, gas.env, gas.durationDays, 0, 0, sampleAvail);
        gasEnd = a.end;
        projectCompletion = Math.max(projectCompletion, a.end);
        schedule.add(new ScheduledJob(p.id, gas.id, gas.category, gas.env, gas.durationDays, a.chamber.spec.id, a.stationIdx, a.sampleIdx, a.start, a.end));
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
          schedule.add(new ScheduledJob(p.id, pd.id, pd.category, pd.env, pd.durationDays, a.chamber.spec.id, a.stationIdx, a.sampleIdx, a.start, a.end));
        }
        pulldownEnd = maxEnd;
      }

      // 3) OTHER TESTS (Energy/Performance/Freezing/Temperature Rise)
      List<TestDef> others = new ArrayList<>();
      for (TestDef t : Data.TESTS) {
        if (t.category == TestCategory.OTHER && isRequired(p, t.id)) others.add(t);
      }

      // Other testlerin faz başlangıcı: iki farklı yorum desteklenir.
      // - WAIT_FOR_ALL_PULLDOWNS=true: tüm pulldown bitmeden other test başlamaz.
      // - false: her sample kendi pulldown'u biter bitmez other test alabilir (sampleAvail bunu zaten zorlar).
      int otherPhaseEarliest = Data.OTHER_TESTS_WAIT_FOR_ALL_PULLDOWNS ? pulldownEnd : gasEnd;

      // List scheduling: her adımda (kalan testler içinden) en erken başlayabileni seç.
      int maxStartOther = otherPhaseEarliest;
      List<TestDef> remainingOtherTests = new ArrayList<>(others);
      while (!remainingOtherTests.isEmpty()) {
        Planned best = null;
        int bestIdx = -1;
        for (int i = 0; i < remainingOtherTests.size(); i++) {
          TestDef t = remainingOtherTests.get(i);
          Planned cand = planBestOverSamples(chambers, p.needsVoltage, t.env, t.durationDays, otherPhaseEarliest, sampleAvail);
          if (best == null
              || cand.start < best.start
              || (cand.start == best.start && cand.end < best.end)) {
            best = cand;
            bestIdx = i;
          }
        }
        if (best == null || bestIdx < 0) {
          throw new IllegalStateException("Failed to plan other test assignment for project " + p.id);
        }

        TestDef chosen = remainingOtherTests.remove(bestIdx);
        apply(best, sampleAvail);
        maxStartOther = Math.max(maxStartOther, best.start);
        projectCompletion = Math.max(projectCompletion, best.end);
        schedule.add(new ScheduledJob(
            p.id, chosen.id, chosen.category, chosen.env, chosen.durationDays,
            best.chamber.spec.id, best.stationIdx, best.sampleIdx, best.start, best.end
        ));
      }

      // 4) CONSUMER USAGE: start >= maxStartOther
      for (TestDef t : Data.TESTS) {
        if (t.category != TestCategory.CONSUMER_USAGE) continue;
        if (!isRequired(p, t.id)) continue;
        int earliest = Math.max(otherPhaseEarliest, maxStartOther);
        Assignment a = assignBestOverSamples(chambers, p.needsVoltage, t.env, t.durationDays, earliest, sampleAvail);
        projectCompletion = Math.max(projectCompletion, a.end);
        schedule.add(new ScheduledJob(p.id, t.id, t.category, t.env, t.durationDays, a.chamber.spec.id, a.stationIdx, a.sampleIdx, a.start, a.end));
      }

      int lateness = Math.max(0, projectCompletion - p.dueDateDays);
      totalLateness += lateness;
      results.add(new ProjectResult(p.id, projectCompletion, p.dueDateDays, lateness));
    }

    return new EvalResult(totalLateness, results, schedule);
  }

  private static Project pickNextProject(List<Project> remaining, List<ChamberInstance> chambers) {
    if (remaining.size() == 1) {
      return remaining.remove(0);
    }

    if (Data.PROJECT_DISPATCH_RULE == Data.ProjectDispatchRule.EDD) {
      int bestIdx = 0;
      int bestDue = remaining.get(0).dueDateDays;
      for (int i = 1; i < remaining.size(); i++) {
        int d = remaining.get(i).dueDateDays;
        if (d < bestDue) {
          bestDue = d;
          bestIdx = i;
        }
      }
      return remaining.remove(bestIdx);
    }

    // ATC (Apparent Tardiness Cost) - dinamik: t, p, p_bar ile skorla.
    double pBar = averageWork(remaining);
    double k = Data.ATC_K <= 0 ? 3.0 : Data.ATC_K;

    int bestIdx = 0;
    double bestScore = Double.NEGATIVE_INFINITY;
    int bestDue = Integer.MAX_VALUE;
    for (int i = 0; i < remaining.size(); i++) {
      Project p = remaining.get(i);
      int t = earliestProjectStart(p, chambers);
      double proc = Math.max(1.0, estimatedWork(p));
      double slack = p.dueDateDays - t - proc;
      double urgency = Math.max(0.0, -slack); // tardy/near-tardy -> larger
      // ATC skor: 1/proc * exp( - max(0, slack) / (k * pBar) )
      double expTerm = Math.exp(-(Math.max(0.0, slack)) / (k * Math.max(1.0, pBar)));
      double score = (1.0 / proc) * expTerm + 1e-9 * urgency;

      if (score > bestScore || (score == bestScore && p.dueDateDays < bestDue)) {
        bestScore = score;
        bestIdx = i;
        bestDue = p.dueDateDays;
      }
    }

    return remaining.remove(bestIdx);
  }

  /** Projenin ilk başlayabileceği tahmini en erken zaman (ATC için). */
  private static int earliestProjectStart(Project p, List<ChamberInstance> chambers) {
    // İlk test env: Gas varsa Gas env, yoksa Pulldown env, yoksa herhangi bir required test env.
    Env firstEnv = null;
    Integer gasIdx = Data.TEST_INDEX.get("GAS_43");
    if (gasIdx != null && p.required[gasIdx]) firstEnv = Data.TESTS.get(gasIdx).env;
    if (firstEnv == null) {
      Integer pdIdx = Data.TEST_INDEX.get("PULLDOWN_43");
      if (pdIdx != null && p.required[pdIdx]) firstEnv = Data.TESTS.get(pdIdx).env;
    }
    if (firstEnv == null) {
      for (int ti = 0; ti < Data.TESTS.size(); ti++) {
        if (p.required[ti]) { firstEnv = Data.TESTS.get(ti).env; break; }
      }
    }
    if (firstEnv == null) return 0;

    int best = Integer.MAX_VALUE;
    for (ChamberInstance ch : chambers) {
      if (!ch.env.equals(firstEnv)) continue;
      if (p.needsVoltage && !ch.spec.voltageCapable) continue;
      for (int a : ch.stationAvail) {
        if (a < best) best = a;
      }
    }
    return best == Integer.MAX_VALUE ? 0 : best;
  }

  private static double averageWork(List<Project> ps) {
    double sum = 0.0;
    for (Project p : ps) sum += estimatedWork(p);
    return ps.isEmpty() ? 1.0 : (sum / ps.size());
  }

  /**
   * Proje iş yükü tahmini (ATC için): tüm required test süreleri + pulldown (S*dur) + CU testleri.
   * Bu sadece öncelik hesabı içindir; gerçek completion hesaplaması scheduler tarafından yapılır.
   */
  private static double estimatedWork(Project p) {
    double w = 0.0;
    for (int ti = 0; ti < Data.TESTS.size(); ti++) {
      if (!p.required[ti]) continue;
      TestDef t = Data.TESTS.get(ti);
      if (t.category == TestCategory.PULLDOWN) {
        w += (double) p.samples * t.durationDays;
      } else {
        w += t.durationDays;
      }
    }
    return w;
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

  private static Planned planBestOverSamples(
      List<ChamberInstance> chambers,
      boolean needsVoltage,
      Env env,
      int duration,
      int earliest,
      int[] sampleAvail
  ) {
    Planned best = null;
    for (int s = 0; s < sampleAvail.length; s++) {
      int e = Math.max(earliest, sampleAvail[s]);
      Planned cand = planBestSingleSample(chambers, needsVoltage, env, duration, e, s);
      if (cand == null) continue;
      if (best == null || cand.start < best.start || (cand.start == best.start && cand.end < best.end)) {
        best = cand;
      }
    }
    if (best == null) {
      throw new IllegalStateException("No eligible chamber for env=" + env + " needsVoltage=" + needsVoltage);
    }
    return best;
  }

  private static Planned planBestSingleSample(
      List<ChamberInstance> chambers,
      boolean needsVoltage,
      Env env,
      int duration,
      int earliest,
      int sampleIdx
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
    if (bestCh == null) return null;
    int start = bestStart;
    int end = start + duration;
    return new Planned(bestCh, bestStation, sampleIdx, start, end);
  }

  private static void apply(Planned p, int[] sampleAvail) {
    p.chamber.stationAvail[p.stationIdx] = p.end;
    sampleAvail[p.sampleIdx] = p.end;
  }
}
