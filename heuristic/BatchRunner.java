package tr.testodasi.heuristic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runs multiple instances defined in a single CSV and writes one summary CSV.
 *
 * <p>Expected CSV header columns (case-insensitive):
 * - instanceId
 * - projectId
 * - dueDateDays
 * - needsVolt
 * - test matrix columns as in {@link Data#MATRIX_COLUMNS} (0/1)
 *
 * <p>Optional columns:
 * - samples (per-project initial samples)
 * - initialSamples (per-instance default samples)
 * - enableSampleIncrease, sampleMax, sampleSearchMaxEvals
 * - enableRoomLS, roomLSMaxEvals, roomLSSwap, roomLSMove, roomLSIncludeSample
 * - validate
 */
public final class BatchRunner {
  private BatchRunner() {}

  public static void run(Path instancesCsv, Path outCsv, boolean verbose) throws IOException {
    run(instancesCsv, outCsv, null, false, verbose);
  }

  /**
   * @param detailsDir if non-null, writes additional detailed CSVs into this directory
   * @param includeSchedule if true, also writes the full schedule rows (can be large)
   */
  public static void run(Path instancesCsv, Path outCsv, Path detailsDir, boolean includeSchedule, boolean verbose) throws IOException {
    Objects.requireNonNull(instancesCsv);
    Objects.requireNonNull(outCsv);

    List<String> lines = Files.readAllLines(instancesCsv);
    if (lines.isEmpty()) throw new IllegalArgumentException("instances CSV is empty: " + instancesCsv);

    List<String> header = parseCsvLine(lines.get(0));
    Map<String, Integer> col = new HashMap<>();
    for (int i = 0; i < header.size(); i++) {
      col.put(norm(header.get(i)), i);
    }

    require(col, "instanceid");
    require(col, "projectid");
    require(col, "duedatedays");
    require(col, "needsvolt");
    for (String mc : Data.MATRIX_COLUMNS) require(col, norm(mc));

    Map<String, List<List<String>>> rowsByInstance = new LinkedHashMap<>();
    for (int li = 1; li < lines.size(); li++) {
      String raw = lines.get(li).trim();
      if (raw.isEmpty()) continue;
      if (raw.startsWith("#")) continue;
      List<String> row = parseCsvLine(lines.get(li));
      String inst = get(row, col, "instanceid");
      if (inst == null || inst.isBlank()) throw new IllegalArgumentException("Missing instanceId at line " + (li + 1));
      rowsByInstance.computeIfAbsent(inst, k -> new ArrayList<>()).add(row);
    }
    if (rowsByInstance.isEmpty()) throw new IllegalArgumentException("No instance rows found in: " + instancesCsv);

    DataSnapshot snap = DataSnapshot.capture();
    Files.createDirectories(outCsv.toAbsolutePath().getParent() == null ? Path.of(".") : outCsv.toAbsolutePath().getParent());

    BufferedWriter projW = null;
    BufferedWriter envW = null;
    BufferedWriter schedW = null;
    if (detailsDir != null) {
      Files.createDirectories(detailsDir);
      projW = Files.newBufferedWriter(detailsDir.resolve("batch_project_results.csv"));
      envW = Files.newBufferedWriter(detailsDir.resolve("batch_chamber_env.csv"));
      if (includeSchedule) {
        schedW = Files.newBufferedWriter(detailsDir.resolve("batch_schedule.csv"));
      }

      projW.write("instanceId,iteration,projectId,dueDateDays,needsVolt,samples,completionDay,lateness");
      projW.newLine();
      envW.write("instanceId,iteration,chamberId,stations,voltageCapable,humidityAdjustable,envTempC,envHumidity");
      envW.newLine();
      if (schedW != null) {
        schedW.write("instanceId,iteration,projectId,testId,category,tempC,humidity,durationDays,needsVoltage,dueDateDays,samples,sampleIdx,chamberId,stationIdx,startDay,endDay");
        schedW.newLine();
      }
    }

    try (BufferedWriter w = Files.newBufferedWriter(outCsv)) {
      w.write("instanceId,projects,bestIteration,totalLateness,totalSamples,horizonDays,avgStationUtil,maxStationUtil,avgChamberUtil,runtimeMs");
      w.newLine();

      for (var e : rowsByInstance.entrySet()) {
        String instanceId = e.getKey();
        List<List<String>> rows = e.getValue();

        snap.restore(); // reset global knobs each instance
        InstanceParams ip = InstanceParams.from(rows, col);
        ip.applyToData();

        List<Project> projects = buildProjectsFromRows(rows, col, ip);

        long t0 = System.currentTimeMillis();
        HeuristicSolver solver = new HeuristicSolver(verbose);
        List<Solution> sols = solver.solveWithProjects(projects);
        Solution best = sols.stream().min(java.util.Comparator.comparingInt(s -> s.totalLateness)).orElseThrow();
        long t1 = System.currentTimeMillis();

        Utilization.Summary util = Utilization.compute(best);
        int totalSamples = 0;
        for (Project p : best.projects) totalSamples += p.samples;

        w.write(csv(instanceId) + "," +
            best.projects.size() + "," +
            best.iteration + "," +
            best.totalLateness + "," +
            totalSamples + "," +
            util.horizonDays + "," +
            fmt(util.avgStationUtilization) + "," +
            fmt(util.maxStationUtilization) + "," +
            fmt(util.avgChamberUtilization) + "," +
            (t1 - t0));
        w.newLine();

        if (projW != null) {
          // project results
          Map<String, Project> projectById = new HashMap<>();
          for (Project p : best.projects) projectById.put(p.id, p);
          for (ProjectResult r : best.results) {
            Project p = projectById.get(r.projectId);
            projW.write(csv(instanceId) + "," + best.iteration + "," + csv(r.projectId) + "," +
                (p != null ? p.dueDateDays : r.dueDate) + "," +
                (p != null && p.needsVoltage) + "," +
                (p != null ? p.samples : "") + "," +
                r.completionDay + "," +
                r.lateness);
            projW.newLine();
          }

          // chamber env assignment
          for (ChamberSpec c : Data.CHAMBERS) {
            Env env = best.chamberEnv.get(c.id);
            envW.write(csv(instanceId) + "," + best.iteration + "," + c.id + "," + c.stations + "," +
                c.voltageCapable + "," + c.humidityAdjustable + "," +
                (env != null ? env.temperatureC : "") + "," +
                (env != null ? env.humidity : ""));
            envW.newLine();
          }

          // full schedule (optional)
          if (schedW != null) {
            for (Scheduler.ScheduledJob j : best.schedule) {
              Project p = projectById.get(j.projectId);
              schedW.write(csv(instanceId) + "," + best.iteration + "," +
                  csv(j.projectId) + "," + j.testId + "," + j.category + "," +
                  j.env.temperatureC + "," + j.env.humidity + "," + j.durationDays + "," +
                  (p != null && p.needsVoltage) + "," + (p != null ? p.dueDateDays : "") + "," + (p != null ? p.samples : "") + "," +
                  j.sampleIdx + "," + j.chamberId + "," + j.stationIdx + "," + j.start + "," + j.end);
              schedW.newLine();
            }
          }
        }

        if (verbose) {
          System.out.println("INFO: Batch instance=" + instanceId +
              " bestIter=" + best.iteration +
              " totalLateness=" + best.totalLateness +
              " runtimeMs=" + (t1 - t0));
        }
      }
    } finally {
      if (projW != null) try { projW.close(); } catch (IOException ignored) {}
      if (envW != null) try { envW.close(); } catch (IOException ignored) {}
      if (schedW != null) try { schedW.close(); } catch (IOException ignored) {}
      snap.restore();
    }
  }

  private static List<Project> buildProjectsFromRows(List<List<String>> rows, Map<String, Integer> col, InstanceParams ip) {
    List<Project> projects = new ArrayList<>();
    int defaultSamples = Math.max(Data.MIN_SAMPLES, ip.initialSamples != null ? ip.initialSamples : Data.INITIAL_SAMPLES);

    for (List<String> row : rows) {
      String pid = get(row, col, "projectid");
      if (pid == null || pid.isBlank()) throw new IllegalArgumentException("Missing projectId for instanceId=" + get(row, col, "instanceid"));
      int due = parseInt(get(row, col, "duedatedays"), "dueDateDays projectId=" + pid);
      boolean needsVolt = parseBool(get(row, col, "needsvolt"));

      Integer perProjectSamples = null;
      if (col.containsKey("samples")) {
        String s = get(row, col, "samples");
        if (s != null && !s.isBlank()) perProjectSamples = parseInt(s, "samples projectId=" + pid);
      }
      int samples = perProjectSamples != null ? perProjectSamples : defaultSamples;
      samples = Math.max(Data.MIN_SAMPLES, Math.min(Data.SAMPLE_MAX, samples));
      if (Data.FORCE_FIXED_SAMPLES) {
        samples = Data.FIXED_SAMPLES_PER_PROJECT;
      }

      boolean[] req = new boolean[Data.TESTS.size()];
      for (int ti = 0; ti < Data.MATRIX_COLUMNS.length; ti++) {
        String mc = Data.MATRIX_COLUMNS[ti];
        int v = parseInt(get(row, col, mc), "matrix " + mc + " projectId=" + pid);
        req[ti] = (v == 1);
      }
      projects.add(new Project(pid, due, needsVolt, req, samples));
    }
    return projects;
  }

  private static final class InstanceParams {
    final Integer initialSamples;
    final Boolean enableSampleIncrease;
    final Integer sampleMax;
    final Integer sampleSearchMaxEvals;

    final Boolean enableRoomLS;
    final Integer roomLSMaxEvals;
    final Boolean roomLSSwap;
    final Boolean roomLSMove;
    final Boolean roomLSIncludeSample;

    final Boolean validate;

    private InstanceParams(
        Integer initialSamples,
        Boolean enableSampleIncrease,
        Integer sampleMax,
        Integer sampleSearchMaxEvals,
        Boolean enableRoomLS,
        Integer roomLSMaxEvals,
        Boolean roomLSSwap,
        Boolean roomLSMove,
        Boolean roomLSIncludeSample,
        Boolean validate
    ) {
      this.initialSamples = initialSamples;
      this.enableSampleIncrease = enableSampleIncrease;
      this.sampleMax = sampleMax;
      this.sampleSearchMaxEvals = sampleSearchMaxEvals;
      this.enableRoomLS = enableRoomLS;
      this.roomLSMaxEvals = roomLSMaxEvals;
      this.roomLSSwap = roomLSSwap;
      this.roomLSMove = roomLSMove;
      this.roomLSIncludeSample = roomLSIncludeSample;
      this.validate = validate;
    }

    static InstanceParams from(List<List<String>> rows, Map<String, Integer> col) {
      return new InstanceParams(
          firstInt(rows, col, "initialsamples"),
          firstBool(rows, col, "enablesampleincrease"),
          firstInt(rows, col, "samplemax"),
          firstInt(rows, col, "samplesearchmaxevals"),
          firstBool(rows, col, "enableroomls"),
          firstInt(rows, col, "roomlsmaxevals"),
          firstBool(rows, col, "roomlsswap"),
          firstBool(rows, col, "roomlsmove"),
          firstBool(rows, col, "roomlsincludesample"),
          firstBool(rows, col, "validate")
      );
    }

    void applyToData() {
      if (initialSamples != null) Data.INITIAL_SAMPLES = initialSamples;
      if (enableSampleIncrease != null) Data.ENABLE_SAMPLE_INCREASE = enableSampleIncrease;
      if (sampleMax != null) Data.SAMPLE_MAX = sampleMax;
      if (sampleSearchMaxEvals != null) Data.SAMPLE_SEARCH_MAX_EVALS = sampleSearchMaxEvals;

      if (enableRoomLS != null) Data.ENABLE_ROOM_LOCAL_SEARCH = enableRoomLS;
      if (roomLSMaxEvals != null) Data.ROOM_LS_MAX_EVALS = roomLSMaxEvals;
      if (roomLSSwap != null) Data.ROOM_LS_ENABLE_SWAP = roomLSSwap;
      if (roomLSMove != null) Data.ROOM_LS_ENABLE_MOVE = roomLSMove;
      if (roomLSIncludeSample != null) Data.ROOM_LS_INCLUDE_SAMPLE_HEURISTIC = roomLSIncludeSample;

      if (validate != null) Data.ENABLE_SCHEDULE_VALIDATION = validate;

      // Hard requirement: fixed sample count per project.
      if (Data.FORCE_FIXED_SAMPLES) {
        Data.INITIAL_SAMPLES = Data.FIXED_SAMPLES_PER_PROJECT;
        Data.ENABLE_SAMPLE_INCREASE = false;
        Data.SAMPLE_MAX = Data.FIXED_SAMPLES_PER_PROJECT;
      }
    }
  }

  private static final class DataSnapshot {
    final int initialSamples = Data.INITIAL_SAMPLES;
    final boolean enableSampleIncrease = Data.ENABLE_SAMPLE_INCREASE;
    final int sampleMax = Data.SAMPLE_MAX;
    final int sampleSearchMaxEvals = Data.SAMPLE_SEARCH_MAX_EVALS;

    final boolean enableRoomLS = Data.ENABLE_ROOM_LOCAL_SEARCH;
    final int roomLSMaxEvals = Data.ROOM_LS_MAX_EVALS;
    final boolean roomLSSwap = Data.ROOM_LS_ENABLE_SWAP;
    final boolean roomLSMove = Data.ROOM_LS_ENABLE_MOVE;
    final boolean roomLSIncludeSample = Data.ROOM_LS_INCLUDE_SAMPLE_HEURISTIC;

    final boolean validate = Data.ENABLE_SCHEDULE_VALIDATION;

    static DataSnapshot capture() { return new DataSnapshot(); }

    void restore() {
      Data.INITIAL_SAMPLES = initialSamples;
      Data.ENABLE_SAMPLE_INCREASE = enableSampleIncrease;
      Data.SAMPLE_MAX = sampleMax;
      Data.SAMPLE_SEARCH_MAX_EVALS = sampleSearchMaxEvals;

      Data.ENABLE_ROOM_LOCAL_SEARCH = enableRoomLS;
      Data.ROOM_LS_MAX_EVALS = roomLSMaxEvals;
      Data.ROOM_LS_ENABLE_SWAP = roomLSSwap;
      Data.ROOM_LS_ENABLE_MOVE = roomLSMove;
      Data.ROOM_LS_INCLUDE_SAMPLE_HEURISTIC = roomLSIncludeSample;

      Data.ENABLE_SCHEDULE_VALIDATION = validate;
    }
  }

  // ---- CSV helpers ----

  private static String fmt(double x) {
    return String.format(Locale.ROOT, "%.6f", x);
  }

  private static void require(Map<String, Integer> col, String name) {
    if (!col.containsKey(name)) throw new IllegalArgumentException("Missing required CSV column: " + name);
  }

  private static String norm(String s) {
    return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
  }

  private static String get(List<String> row, Map<String, Integer> col, String key) {
    Integer i = col.get(norm(key));
    if (i == null) return null;
    return i < row.size() ? row.get(i).trim() : "";
  }

  private static int parseInt(String s, String ctx) {
    if (s == null || s.isBlank()) throw new IllegalArgumentException("Missing int for " + ctx);
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid int for " + ctx + ": " + s);
    }
  }

  private static boolean parseBool(String s) {
    if (s == null) return false;
    String v = s.trim().toLowerCase(Locale.ROOT);
    return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "y".equals(v);
  }

  private static Integer firstInt(List<List<String>> rows, Map<String, Integer> col, String key) {
    if (!col.containsKey(key)) return null;
    for (List<String> r : rows) {
      String v = get(r, col, key);
      if (v != null && !v.isBlank()) return parseInt(v, key);
    }
    return null;
  }

  private static Boolean firstBool(List<List<String>> rows, Map<String, Integer> col, String key) {
    if (!col.containsKey(key)) return null;
    for (List<String> r : rows) {
      String v = get(r, col, key);
      if (v != null && !v.isBlank()) return parseBool(v);
    }
    return null;
  }

  // Minimal CSV parser (supports quotes and commas inside quotes).
  private static List<String> parseCsvLine(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (ch == ',' && !inQuotes) {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(ch);
      }
    }
    out.add(cur.toString());
    return out;
  }

  private static String csv(String s) {
    if (s == null) return "";
    boolean need = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
    if (!need) return s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}

