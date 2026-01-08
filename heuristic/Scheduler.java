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

    // Sade sürüm: sadece JOB_BASED evaluator.
    return evaluateJobBased(projects, chambers);
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

    Candidate(ProjectState st, JobKind kind, TestDef test, Planned planned) {
      this.st = st;
      this.kind = kind;
      this.test = test;
      this.planned = planned;
    }

    int start() { return planned.start; }

    double score() {
      // lower start and due pressure should win.
      int due = st.p.dueDateDays;
      // EDD: smaller due is better; tie-break by earlier start
      return -due * 1e6 - start();
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

    Candidate bestReadyCandidate(List<ChamberInstance> chambers) {
      Candidate best = null;

      // GAS ready at 0
      if (!gasScheduled) {
        TestDef gas = get("GAS_43");
        int release = 0;
        Planned pl = planBestOverSamples(chambers, p.needsVoltage, gas.env, gas.durationDays, release, sampleAvail);
        best = new Candidate(this, JobKind.GAS, gas, pl);
      }

      // PULLDOWN ready after gas
      if (pulldownRequired && gasScheduled) {
        TestDef pd = get("PULLDOWN_43");
        for (int s = 0; s < pulldownScheduledBySample.length; s++) {
          if (pulldownScheduledBySample[s]) continue;
          int release = gasEnd;
          int earliest = Math.max(release, sampleAvail[s]);
          Planned pl = planBestSingleSample(chambers, p.needsVoltage, pd.env, pd.durationDays, earliest, s);
          Candidate c = new Candidate(this, JobKind.PULLDOWN, pd, pl);
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
          Candidate c = new Candidate(this, JobKind.OTHER, t, pl);
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
            Candidate c = new Candidate(this, JobKind.CU, t, pl);
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

  private static boolean isRequired(Project p, String testId) {
    Integer idx = Data.TEST_INDEX.get(testId);
    return idx != null && p.required[idx];
  }

  private static TestDef get(String testId) {
    Integer idx = Data.TEST_INDEX.get(testId);
    if (idx == null) throw new IllegalArgumentException("Unknown test id: " + testId);
    return Data.TESTS.get(idx);
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