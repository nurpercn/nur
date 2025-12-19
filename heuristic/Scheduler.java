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

    if (Data.SCHEDULING_MODE == Data.SchedulingMode.JOB_BASED) {
      EvalResult r = evaluateJobBased(projects, chambers);
      return r;
    }

    // Varsayılan evaluator: dispatch rule'a göre proje seçimi (EDD/ATC).
    List<Project> remaining = new ArrayList<>(projects.stream().map(Project::copy).toList());
    int totalLateness = 0;
    List<ProjectResult> results = new ArrayList<>();
    List<ScheduledJob> schedule = new ArrayList<>();

    while (!remaining.isEmpty()) {
      Project p = pickNextProject(remaining, chambers);
      ProjectSchedule ps = scheduleSingleProject(p, chambers);
      totalLateness += ps.lateness;
      results.add(new ProjectResult(p.id, ps.completionDay, p.dueDateDays, ps.lateness));
      schedule.addAll(ps.jobs);
    }

    return new EvalResult(totalLateness, results, schedule);
  }

  /** Job-based: tüm projelerden hazır job havuzu ile çizelgele. */
  private EvalResult evaluateJobBased(List<Project> projects, List<ChamberInstance> chambers) {
    List<Project> ps = projects.stream().map(Project::copy).toList();

    Map<String, ProjectState> stateByProject = new HashMap<>();
    for (Project p : ps) {
      stateByProject.put(p.id, new ProjectState(p));
    }

    // Remaining jobs as counters/sets in state; loop until all scheduled.
    List<ScheduledJob> schedule = new ArrayList<>();

    int safety = 0;
    while (true) {
      if (safety++ > 200000) {
        throw new IllegalStateException("Job-based scheduling did not converge (safety limit hit)");
      }

      Candidate best = null;
      for (ProjectState st : stateByProject.values()) {
        Candidate cand = st.bestReadyCandidate(chambers);
        if (cand == null) continue;
        if (best == null || cand.betterThan(best)) best = cand;
      }

      if (best == null) break; // no more jobs

      // apply chosen candidate
      best.apply();
      schedule.add(best.toScheduledJob());
    }

    // compute results
    int totalLateness = 0;
    List<ProjectResult> results = new ArrayList<>();
    for (ProjectState st : stateByProject.values()) {
      int completion = st.projectCompletion();
      int lateness = Math.max(0, completion - st.p.dueDateDays);
      totalLateness += lateness;
      results.add(new ProjectResult(st.p.id, completion, st.p.dueDateDays, lateness));
    }

    return new EvalResult(totalLateness, results, schedule);
  }

  private static final class Candidate {
    final ProjectState st;
    final JobKind kind;
    final TestDef test;
    final Planned planned;
    final int release;

    Candidate(ProjectState st, JobKind kind, TestDef test, Planned planned, int release) {
      this.st = st;
      this.kind = kind;
      this.test = test;
      this.planned = planned;
      this.release = release;
    }

    int start() { return planned.start; }
    int end() { return planned.end; }

    double score() {
      // lower start and due pressure should win.
      int due = st.p.dueDateDays;
      int p = test.durationDays;
      int slack = due - start() - p;
      if (Data.JOB_DISPATCH_RULE == Data.JobDispatchRule.EDD) {
        // smaller due is better; tie-break by earlier start
        return -due * 1e6 - start();
      }
      if (Data.JOB_DISPATCH_RULE == Data.JobDispatchRule.MIN_SLACK) {
        return -slack * 1e6 - start();
      }
      // ATC
      double k = Data.JOB_ATC_K <= 0 ? 3.0 : Data.JOB_ATC_K;
      double pBar = st.avgRemainingDuration();
      double expTerm = Math.exp(-(Math.max(0.0, slack)) / (k * Math.max(1.0, pBar)));
      return (1.0 / Math.max(1.0, p)) * expTerm;
    }

    boolean betterThan(Candidate other) {
      double s1 = this.score();
      double s2 = other.score();
      if (s1 != s2) return s1 > s2;
      if (this.start() != other.start()) return this.start() < other.start();
      return this.st.p.dueDateDays < other.st.p.dueDateDays;
    }

    void apply() {
      Scheduler.apply(planned, st.sampleAvail);
      st.onScheduled(kind, test, planned);
    }

    ScheduledJob toScheduledJob() {
      return new ScheduledJob(
          st.p.id,
          test.id,
          test.category,
          test.env,
          test.durationDays,
          planned.chamber.spec.id,
          planned.stationIdx,
          planned.sampleIdx,
          planned.start,
          planned.end
      );
    }
  }

  private enum JobKind { GAS, PULLDOWN, OTHER, CU }

  private static final class ProjectState {
    final Project p;
    final int[] sampleAvail;

    boolean gasScheduled;
    int gasEnd;

    boolean pulldownRequired;
    boolean[] pulldownScheduledBySample;
    int pulldownDoneCount;
    int pulldownEndMax;

    // remaining other tests (indices in Data.TESTS)
    final List<TestDef> remainingOthers = new ArrayList<>();
    int otherStartedCount;
    int otherRequiredCount;
    int maxStartOther;

    final List<TestDef> remainingCu = new ArrayList<>();

    int completionMax;

    ProjectState(Project p) {
      this.p = p;
      this.sampleAvail = new int[p.samples];

      // Gas
      gasScheduled = !isRequired(p, "GAS_43");
      gasEnd = 0;

      // Pulldown
      pulldownRequired = isRequired(p, "PULLDOWN_43");
      pulldownScheduledBySample = new boolean[p.samples];
      pulldownDoneCount = pulldownRequired ? 0 : p.samples;
      pulldownEndMax = 0;

      // Others
      for (TestDef t : Data.TESTS) {
        if (t.category == TestCategory.OTHER && isRequired(p, t.id)) {
          remainingOthers.add(t);
        }
      }
      otherRequiredCount = remainingOthers.size();
      otherStartedCount = 0;
      maxStartOther = 0;

      // CU
      for (TestDef t : Data.TESTS) {
        if (t.category == TestCategory.CONSUMER_USAGE && isRequired(p, t.id)) {
          remainingCu.add(t);
        }
      }

      completionMax = 0;
    }

    int projectCompletion() { return completionMax; }

    double avgRemainingDuration() {
      int n = 0;
      int sum = 0;
      if (!gasScheduled) { sum += get("GAS_43").durationDays; n++; }
      if (pulldownRequired) {
        TestDef pd = get("PULLDOWN_43");
        for (boolean b : pulldownScheduledBySample) {
          if (!b) { sum += pd.durationDays; n++; }
        }
      }
      for (TestDef t : remainingOthers) { sum += t.durationDays; n++; }
      for (TestDef t : remainingCu) { sum += t.durationDays; n++; }
      return n == 0 ? 1.0 : (sum / (double) n);
    }

    Candidate bestReadyCandidate(List<ChamberInstance> chambers) {
      Candidate best = null;

      // GAS ready at 0
      if (!gasScheduled) {
        TestDef gas = get("GAS_43");
        int release = 0;
        Planned pl = planBestOverSamples(chambers, p.needsVoltage, gas.env, gas.durationDays, release, sampleAvail);
        best = new Candidate(this, JobKind.GAS, gas, pl, release);
      }

      // PULLDOWN ready after gas
      if (pulldownRequired && gasScheduled) {
        TestDef pd = get("PULLDOWN_43");
        for (int s = 0; s < pulldownScheduledBySample.length; s++) {
          if (pulldownScheduledBySample[s]) continue;
          int release = gasEnd;
          int earliest = Math.max(release, sampleAvail[s]);
          Planned pl = planBestSingleSample(chambers, p.needsVoltage, pd.env, pd.durationDays, earliest, s);
          Candidate c = new Candidate(this, JobKind.PULLDOWN, pd, pl, release);
          if (best == null || c.betterThan(best)) best = c;
        }
      }

      // OTHER ready after pulldown phase (strict) or sample-wise
      if (!remainingOthers.isEmpty()) {
        for (int i = 0; i < remainingOthers.size(); i++) {
          TestDef t = remainingOthers.get(i);
          int release;
          if (Data.OTHER_TESTS_WAIT_FOR_ALL_PULLDOWNS && pulldownRequired) {
            if (pulldownDoneCount < p.samples) continue;
            release = pulldownEndMax;
          } else {
            // relaxed: just after gas; sampleAvail will enforce sample pulldown completion if scheduled there
            if (!gasScheduled) continue;
            release = gasEnd;
          }
          Planned pl = planBestOverSamples(chambers, p.needsVoltage, t.env, t.durationDays, release, sampleAvail);
          Candidate c = new Candidate(this, JobKind.OTHER, t, pl, release);
          if (best == null || c.betterThan(best)) best = c;
        }
      }

      // CU ready after all other tests have started (not finished) and after pulldown phase.
      if (!remainingCu.isEmpty()) {
        if (otherStartedCount < otherRequiredCount) {
          // not ready yet
        } else {
          int release = Math.max(maxStartOther, (pulldownRequired ? pulldownEndMax : gasEnd));
          for (TestDef t : remainingCu) {
            Planned pl = planBestOverSamples(chambers, p.needsVoltage, t.env, t.durationDays, release, sampleAvail);
            Candidate c = new Candidate(this, JobKind.CU, t, pl, release);
            if (best == null || c.betterThan(best)) best = c;
          }
        }
      }

      return best;
    }

    void onScheduled(JobKind kind, TestDef test, Planned pl) {
      completionMax = Math.max(completionMax, pl.end);
      switch (kind) {
        case GAS -> {
          gasScheduled = true;
          gasEnd = pl.end;
        }
        case PULLDOWN -> {
          pulldownScheduledBySample[pl.sampleIdx] = true;
          pulldownDoneCount++;
          pulldownEndMax = Math.max(pulldownEndMax, pl.end);
        }
        case OTHER -> {
          // remove one instance of this test def
          for (int i = 0; i < remainingOthers.size(); i++) {
            if (remainingOthers.get(i).id.equals(test.id)) {
              remainingOthers.remove(i);
              break;
            }
          }
          otherStartedCount++;
          maxStartOther = Math.max(maxStartOther, pl.start);
        }
        case CU -> {
          for (int i = 0; i < remainingCu.size(); i++) {
            if (remainingCu.get(i).id.equals(test.id)) {
              remainingCu.remove(i);
              break;
            }
          }
        }
      }
    }
  }

  /** Verilen proje sırasını aynen kullanarak çizelgele. */
  public EvalResult evaluateFixedOrder(List<Project> orderedProjects, Map<String, Env> chamberEnv) {
    Objects.requireNonNull(orderedProjects);
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

    int totalLateness = 0;
    List<ProjectResult> results = new ArrayList<>();
    List<ScheduledJob> schedule = new ArrayList<>();

    for (Project p0 : orderedProjects) {
      Project p = p0.copy();
      ProjectSchedule ps = scheduleSingleProject(p, chambers);
      totalLateness += ps.lateness;
      results.add(new ProjectResult(p.id, ps.completionDay, p.dueDateDays, ps.lateness));
      schedule.addAll(ps.jobs);
    }

    return new EvalResult(totalLateness, results, schedule);
  }

  private static final class ProjectSchedule {
    final int completionDay;
    final int lateness;
    final List<ScheduledJob> jobs;

    ProjectSchedule(int completionDay, int lateness, List<ScheduledJob> jobs) {
      this.completionDay = completionDay;
      this.lateness = lateness;
      this.jobs = jobs;
    }
  }

  private static ProjectSchedule scheduleSingleProject(Project p, List<ChamberInstance> chambers) {
    int[] sampleAvail = new int[p.samples];
    int projectCompletion = 0;
    List<ScheduledJob> jobs = new ArrayList<>();

      // 1) GAS
      int gasEnd = 0;
      if (isRequired(p, "GAS_43")) {
        TestDef gas = get("GAS_43");
        Assignment a = assignBest(chambers, p.needsVoltage, gas.env, gas.durationDays, 0, 0, sampleAvail);
        gasEnd = a.end;
        projectCompletion = Math.max(projectCompletion, a.end);
        jobs.add(new ScheduledJob(p.id, gas.id, gas.category, gas.env, gas.durationDays, a.chamber.spec.id, a.stationIdx, a.sampleIdx, a.start, a.end));
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
          jobs.add(new ScheduledJob(p.id, pd.id, pd.category, pd.env, pd.durationDays, a.chamber.spec.id, a.stationIdx, a.sampleIdx, a.start, a.end));
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
        jobs.add(new ScheduledJob(
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
        jobs.add(new ScheduledJob(p.id, t.id, t.category, t.env, t.durationDays, a.chamber.spec.id, a.stationIdx, a.sampleIdx, a.start, a.end));
      }

    int lateness = Math.max(0, projectCompletion - p.dueDateDays);
    return new ProjectSchedule(projectCompletion, lateness, jobs);
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

  /**
   * Schedule doğrulama:
   * - istasyon çakışması yok
   * - sample çakışması yok (proje içinde)
   * - oda/env uyumluluğu + voltaj kısıtı
   * - precedence: Gas -> Pulldown -> Other -> CU (CU: diğerlerinin başlamasını bekler)
   */
  public static List<String> validateSchedule(List<Project> projects, Map<String, Env> chamberEnv, List<ScheduledJob> schedule) {
    Objects.requireNonNull(projects);
    Objects.requireNonNull(chamberEnv);
    Objects.requireNonNull(schedule);

    Map<String, ChamberSpec> chamberById = new HashMap<>();
    for (ChamberSpec c : Data.CHAMBERS) chamberById.put(c.id, c);

    Map<String, Project> projectById = new HashMap<>();
    for (Project p : projects) projectById.put(p.id, p);

    List<String> violations = new ArrayList<>();

    // (1) compatibility + required check
    for (ScheduledJob j : schedule) {
      Project p = projectById.get(j.projectId);
      if (p == null) {
        violations.add("Unknown projectId in schedule: " + j.projectId);
        continue;
      }
      Integer idx = Data.TEST_INDEX.get(j.testId);
      if (idx == null) {
        violations.add("Unknown testId in schedule: " + j.testId + " for project " + j.projectId);
      } else if (!p.required[idx]) {
        violations.add("Scheduled test not required: " + j.projectId + " test=" + j.testId);
      }

      ChamberSpec ch = chamberById.get(j.chamberId);
      if (ch == null) {
        violations.add("Unknown chamberId in schedule: " + j.chamberId);
        continue;
      }
      Env assigned = chamberEnv.get(j.chamberId);
      if (assigned == null) {
        violations.add("Missing chamber env: " + j.chamberId);
      } else if (!assigned.equals(j.env)) {
        violations.add("Env mismatch: job " + j.projectId + "/" + j.testId + " requires " + j.env + " but chamber " + j.chamberId + " is " + assigned);
      }
      if (j.env.humidity == Humidity.H85 && !ch.humidityAdjustable) {
        violations.add("Humidity mismatch: chamber " + j.chamberId + " not humAdj but job requires 85%");
      }
      if (p.needsVoltage && !ch.voltageCapable) {
        violations.add("Voltage mismatch: project " + p.id + " needsVoltage but scheduled in non-voltage chamber " + j.chamberId);
      }
      if (j.end <= j.start) {
        violations.add("Non-positive duration interval: " + j.projectId + "/" + j.testId + " start=" + j.start + " end=" + j.end);
      }
      if (j.sampleIdx < 0 || j.sampleIdx >= p.samples) {
        violations.add("Invalid sampleIdx: " + j.projectId + " sample=" + j.sampleIdx + " samples=" + p.samples);
      }
      if (j.stationIdx < 0 || j.stationIdx >= ch.stations) {
        violations.add("Invalid stationIdx: " + j.chamberId + " station=" + j.stationIdx + " stations=" + ch.stations);
      }
    }

    // (2) station overlap
    Map<String, List<ScheduledJob>> byStation = new HashMap<>();
    for (ScheduledJob j : schedule) {
      String key = j.chamberId + "#" + j.stationIdx;
      byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(j);
    }
    for (var e : byStation.entrySet()) {
      List<ScheduledJob> jobs = e.getValue();
      jobs.sort(Comparator.comparingInt(a -> a.start));
      for (int i = 1; i < jobs.size(); i++) {
        ScheduledJob prev = jobs.get(i - 1);
        ScheduledJob cur = jobs.get(i);
        if (cur.start < prev.end) {
          violations.add("Station overlap at " + e.getKey() + ": " +
              prev.projectId + "/" + prev.testId + "[" + prev.start + "," + prev.end + ") overlaps " +
              cur.projectId + "/" + cur.testId + "[" + cur.start + "," + cur.end + ")");
          break;
        }
      }
    }

    // (3) sample overlap per project
    Map<String, List<ScheduledJob>> bySample = new HashMap<>();
    for (ScheduledJob j : schedule) {
      String key = j.projectId + "#S" + j.sampleIdx;
      bySample.computeIfAbsent(key, k -> new ArrayList<>()).add(j);
    }
    for (var e : bySample.entrySet()) {
      List<ScheduledJob> jobs = e.getValue();
      jobs.sort(Comparator.comparingInt(a -> a.start));
      for (int i = 1; i < jobs.size(); i++) {
        ScheduledJob prev = jobs.get(i - 1);
        ScheduledJob cur = jobs.get(i);
        if (cur.start < prev.end) {
          violations.add("Sample overlap at " + e.getKey() + ": " +
              prev.testId + "[" + prev.start + "," + prev.end + ") overlaps " +
              cur.testId + "[" + cur.start + "," + cur.end + ")");
          break;
        }
      }
    }

    // (4) precedence + completeness per project
    Map<String, List<ScheduledJob>> byProject = new HashMap<>();
    for (ScheduledJob j : schedule) {
      byProject.computeIfAbsent(j.projectId, k -> new ArrayList<>()).add(j);
    }
    for (Project p : projects) {
      List<ScheduledJob> jobs = byProject.getOrDefault(p.id, List.of());
      // index by test id
      Map<String, List<ScheduledJob>> byTest = new HashMap<>();
      for (ScheduledJob j : jobs) byTest.computeIfAbsent(j.testId, k -> new ArrayList<>()).add(j);

      int gasEnd = 0;
      boolean gasReq = isRequired(p, "GAS_43");
      if (gasReq) {
        List<ScheduledJob> g = byTest.getOrDefault("GAS_43", List.of());
        if (g.size() != 1) {
          violations.add("Project " + p.id + ": GAS_43 count=" + g.size() + " expected 1");
        } else {
          gasEnd = g.get(0).end;
        }
      }

      boolean pdReq = isRequired(p, "PULLDOWN_43");
      int pulldownEndMax = gasEnd;
      if (pdReq) {
        List<ScheduledJob> pds = byTest.getOrDefault("PULLDOWN_43", List.of());
        if (pds.size() != p.samples) {
          violations.add("Project " + p.id + ": PULLDOWN_43 count=" + pds.size() + " expected " + p.samples);
        }
        for (ScheduledJob j : pds) {
          if (j.start < gasEnd) {
            violations.add("Project " + p.id + ": Pulldown starts before Gas end");
            break;
          }
          pulldownEndMax = Math.max(pulldownEndMax, j.end);
        }
      }

      int maxStartOther = (pdReq ? pulldownEndMax : gasEnd);
      // Other tests required counts
      for (TestDef t : Data.TESTS) {
        if (t.category != TestCategory.OTHER) continue;
        if (!isRequired(p, t.id)) continue;
        List<ScheduledJob> ot = byTest.getOrDefault(t.id, List.of());
        if (ot.size() != 1) {
          violations.add("Project " + p.id + ": OTHER test " + t.id + " count=" + ot.size() + " expected 1");
          continue;
        }
        ScheduledJob j = ot.get(0);
        if (j.start < gasEnd) {
          violations.add("Project " + p.id + ": OTHER " + t.id + " starts before Gas end");
        }
        if (Data.OTHER_TESTS_WAIT_FOR_ALL_PULLDOWNS && pdReq && j.start < pulldownEndMax) {
          violations.add("Project " + p.id + ": OTHER " + t.id + " starts before Pulldown phase ends");
        }
        maxStartOther = Math.max(maxStartOther, j.start);
      }

      for (TestDef t : Data.TESTS) {
        if (t.category != TestCategory.CONSUMER_USAGE) continue;
        if (!isRequired(p, t.id)) continue;
        List<ScheduledJob> cu = byTest.getOrDefault(t.id, List.of());
        if (cu.size() != 1) {
          violations.add("Project " + p.id + ": CU test " + t.id + " count=" + cu.size() + " expected 1");
          continue;
        }
        ScheduledJob j = cu.get(0);
        if (j.start < maxStartOther) {
          violations.add("Project " + p.id + ": CU " + t.id + " starts before all other tests have started (start=" + j.start + " maxStartOther=" + maxStartOther + ")");
        }
        if (pdReq && j.start < pulldownEndMax) {
          violations.add("Project " + p.id + ": CU " + t.id + " starts before Pulldown phase ends");
        }
      }
    }

    return violations;
  }
}