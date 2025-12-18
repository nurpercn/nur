package tr.testodasi.heuristic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tüm veriler kodun içindedir.
 *
 * Not: Proje matriksi Excel'deki gibi 0/1 tablo olarak aşağıda tutulur.
 * Yeni instance denemek için sadece {@link #PROJECT_MATRIX}, {@link #NEEDS_VOLT}, {@link #DUE_DATES} dizilerini değiştirmeniz yeterli.
 */
public final class Data {
  private Data() {}

  public enum SchedulingMode {
    /** Mevcut: proje seç -> proje içi tüm işleri yerleştir. */
    PROJECT_BASED,
    /** Yeni: tüm projelerden hazır job havuzu -> job seç -> yerleştir. */
    JOB_BASED
  }

  public enum ProjectDispatchRule {
    /** Earliest Due Date (mevcut baseline). */
    EDD,
    /** Apparent Tardiness Cost (dinamik öncelik). */
    ATC
  }

  public enum JobDispatchRule {
    /** Proje due date bazlı EDD (job'larda proje due kullanılır). */
    EDD,
    /** Job-level ATC (tahmini start time üzerinden). */
    ATC,
    /** Minimum slack (due - start - p). */
    MIN_SLACK
  }

  /**
   * Pulldown sonrası \"Other Tests\" başlangıç kuralı.
   *
   * - true  => Projedeki TÜM pulldown job'ları bitmeden hiçbir other test başlamaz (metindeki ifade: \"Pulldown tamamlanmadan...\").
   * - false => Sadece ilgili sample'ın pulldown'u bittiyse o sample üzerinde other test başlayabilir (manuel hesaplarda sık görülen yorum).
   */
  public static final boolean OTHER_TESTS_WAIT_FOR_ALL_PULLDOWNS = true;

  /** Stage2 proje çizelgeleme sıralama kuralı. */
  /** Varsayılan: EDD. Denemeler için Main argümanıyla değiştirilebilir. */
  public static ProjectDispatchRule PROJECT_DISPATCH_RULE = ProjectDispatchRule.EDD;

  /** ATC parametresi (tipik 2..4). */
  public static double ATC_K = 3.0;

  /** Scheduling mode: PROJECT_BASED veya JOB_BASED. */
  public static SchedulingMode SCHEDULING_MODE = SchedulingMode.JOB_BASED;

  /** Job-based dispatch kuralı. */
  public static JobDispatchRule JOB_DISPATCH_RULE = JobDispatchRule.ATC;

  /** Job-based ATC k parametresi. */
  public static double JOB_ATC_K = 3.0;

  /** EDD sırasını local search (adjacent swap) ile iyileştir. */
  public static boolean ENABLE_ORDER_LOCAL_SEARCH = true;

  /** Local search maksimum tur sayısı (adjacent swap pass). */
  public static int ORDER_LS_MAX_PASSES = 2;

  /** Local search hareket penceresi (insertion/move). Örn 5 => i konumundan i±5 arası denenir. */
  public static int ORDER_LS_WINDOW = 5;

  /** Local search toplam değerlendirme limiti (performans için). */
  public static int ORDER_LS_MAX_EVALS = 2000;

  /** Room env local search: oda setlerini schedule objective ile iyileştir. */
  public static boolean ENABLE_ROOM_LOCAL_SEARCH = true;

  /** Room local search max neighbor evaluations. */
  public static int ROOM_LS_MAX_EVALS = 80;

  /** Room local search: swap hamlesini dene. */
  public static boolean ROOM_LS_ENABLE_SWAP = true;

  /** Room local search: move hamlesini dene (oda env değiştir). */
  public static boolean ROOM_LS_ENABLE_MOVE = true;

  /** Room LS scoring: sample artırmayı da dahil et (daha pahalı). */
  public static boolean ROOM_LS_INCLUDE_SAMPLE_HEURISTIC = false;

  /** Schedule doğrulama (ihlal varsa exception). */
  public static boolean ENABLE_SCHEDULE_VALIDATION = true;

  /** Tüm projelerin due date'ine eklenecek sabit offset (gün). */
  public static final int DUE_DATE_OFFSET_DAYS = 0;

  // Test sırasi: ekrandaki kolon sırasını takip eder.
  public static final List<TestDef> TESTS = List.of(
      new TestDef("GAS_43", "Gas Amount Determination", new Env(43, Humidity.NORMAL), 10, TestCategory.GAS),
      new TestDef("PULLDOWN_43", "Pulldown", new Env(43, Humidity.NORMAL), 5, TestCategory.PULLDOWN),
      new TestDef("EE_32", "Energy Efficiency", new Env(32, Humidity.NORMAL), 15, TestCategory.OTHER),
      new TestDef("EE_25", "Energy Efficiency", new Env(25, Humidity.NORMAL), 16, TestCategory.OTHER),
      new TestDef("EE_16", "Energy Efficiency", new Env(16, Humidity.NORMAL), 15, TestCategory.OTHER),
      new TestDef("PERF_25", "Performance", new Env(25, Humidity.NORMAL), 10, TestCategory.OTHER),
      new TestDef("PERF_43", "Performance", new Env(43, Humidity.NORMAL), 10, TestCategory.OTHER),
      new TestDef("PERF_38", "Performance", new Env(38, Humidity.NORMAL), 10, TestCategory.OTHER),
      new TestDef("PERF_32", "Performance", new Env(32, Humidity.NORMAL), 10, TestCategory.OTHER),
      new TestDef("PERF_16", "Performance", new Env(16, Humidity.NORMAL), 10, TestCategory.OTHER),
      new TestDef("PERF_10", "Performance", new Env(10, Humidity.NORMAL), 10, TestCategory.OTHER),
      new TestDef("FREEZE_25", "Freezing Capacity", new Env(25, Humidity.NORMAL), 10, TestCategory.OTHER),
      new TestDef("TEMP_RISE_25", "Temperature Rise", new Env(25, Humidity.NORMAL), 5, TestCategory.OTHER),
      new TestDef("CU_10", "Consumer Usage", new Env(10, Humidity.NORMAL), 15, TestCategory.CONSUMER_USAGE),
      new TestDef("CU_25", "Consumer Usage", new Env(25, Humidity.H85), 15, TestCategory.CONSUMER_USAGE),
      new TestDef("CU_32", "Consumer Usage", new Env(32, Humidity.H85), 15, TestCategory.CONSUMER_USAGE)
  );

  public static final Map<String, Integer> TEST_INDEX = indexById(TESTS);

  /**
   * Excel kolon sırası ile birebir (PROJECT_MATRIX sütunları).
   * Bu sıra {@link #TESTS} sırası ile aynı olmak zorunda.
   */
  public static final String[] MATRIX_COLUMNS = new String[] {
      "GasAmount",
      "Pulldown",
      "EE_32",
      "EE_25",
      "EE_16",
      "Perf_25",
      "Perf_43",
      "Perf_38",
      "Perf_32",
      "Perf_16",
      "Perf_10",
      "FreezingC",
      "TemperatureRise",
      "CU_10",
      "CU_25",
      "CU_32"
  };

  /**
   * Proje test matrisi: satır = proje, sütun = {@link #MATRIX_COLUMNS}.
   * Değerler 0/1 olmalı.
   *
   * Varsayılan: örnek placeholder (50 proje x 16 test).
   * Burayı Excel'deki tablonuzla birebir değiştirin.
   */
  public static final int[][] PROJECT_MATRIX = new int[][]{
      // Gas,PD, EE32,EE25,EE16, P25,P43,P38,P32,P16,P10, Frz,TR, CU10,CU25,CU32
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P1
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P2
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P3
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P4
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P5
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P6
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P7
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P8
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P9
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P10

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P11
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P12
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P13
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P14
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P15
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P16
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P17
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P18
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P19
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P20

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P21
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P22
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P23
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P24
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P25
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P26
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P27
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P28
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P29
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P30

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P31
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P32
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P33
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P34
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P35
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P36
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P37
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P38
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P39
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P40

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P41
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P42
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P43
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P44
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P45
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P46
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P47
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P48
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P49
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P50
  };

  /** Proje bazlı voltaj ihtiyacı (Excel NeedsVolt kolonu). Satır sayısı PROJECT_MATRIX ile aynı olmalı. */
  public static final int[] NEEDS_VOLT = new int[]{
      1, 1, 0, 0, 1, 1, 0, 0, 1, 1,
      0, 0, 1, 1, 0, 0, 1, 1, 0, 0,
      1, 1, 0, 0, 1, 1, 0, 0, 1, 1,
      0, 0, 1, 1, 0, 0, 1, 1, 0, 0,
      1, 1, 0, 0, 1, 1, 0, 0, 1, 0
  };

  /** Proje bazlı due date (Excel due date kolonu). Satır sayısı PROJECT_MATRIX ile aynı olmalı. */
  public static final int[] DUE_DATES = new int[]{
      60, 50, 95, 120, 50, 70, 50, 60, 95, 120,
      50, 70, 30, 60, 95, 120, 50, 70, 30, 60,
      95, 120, 50, 70, 30, 60, 95, 120, 50, 70,
      30, 60, 95, 120, 50, 70, 60, 95, 120, 50,
      70, 30, 60, 95, 120, 50, 70, 30, 60, 95
  };

  public static final List<ChamberSpec> CHAMBERS = List.of(
      // id, stations, tempAdj, humAdj, voltAdj (voltCapable)
      new ChamberSpec("T001", 8, true, false, false),
      new ChamberSpec("T002", 8, true, true, true),
      new ChamberSpec("T003", 8, true, true, true),
      new ChamberSpec("T004", 8, true, true, true),
      new ChamberSpec("T005", 8, true, true, true),
      new ChamberSpec("T006", 8, true, false, false),
      new ChamberSpec("T007", 8, true, false, false),
      new ChamberSpec("T008", 12, true, false, true),
      new ChamberSpec("T009", 12, true, false, false),
      new ChamberSpec("T010", 12, true, true, false),
      new ChamberSpec("T011", 12, true, true, false),
      new ChamberSpec("T012", 12, true, true, false),
      new ChamberSpec("T013", 12, true, true, false),
      new ChamberSpec("T014", 6, true, true, false),
      new ChamberSpec("T015", 6, true, true, false),
      new ChamberSpec("T016", 6, true, false, true),
      new ChamberSpec("T017", 6, true, false, true),
      new ChamberSpec("T018", 6, true, false, true),
      new ChamberSpec("T019", 6, true, false, true),
      new ChamberSpec("T020", 6, true, false, true),
      new ChamberSpec("T021", 6, true, false, true),
      new ChamberSpec("T022", 6, true, false, true),
      new ChamberSpec("T023", 6, true, false, true),
      new ChamberSpec("T024", 8, true, true, false),
      new ChamberSpec("T025", 8, true, true, true),
      new ChamberSpec("T026", 12, true, false, true),
      new ChamberSpec("T027", 12, true, false, true),
      new ChamberSpec("T028", 12, true, false, true)
  );

  /** Projeleri doğrudan matristen üretir. */
  public static List<Project> buildProjects(int initialSamples) {
    if (initialSamples <= 0) throw new IllegalArgumentException("initialSamples must be positive");

    validateMatrix();

    List<Project> projects = new ArrayList<>();
    for (int r = 0; r < PROJECT_MATRIX.length; r++) {
      String id = "P" + (r + 1);
      int due = DUE_DATES[r] + DUE_DATE_OFFSET_DAYS;
      boolean needsVolt = NEEDS_VOLT[r] == 1;

      boolean[] req = new boolean[TESTS.size()];
      for (int c = 0; c < TESTS.size(); c++) {
        req[c] = PROJECT_MATRIX[r][c] == 1;
      }
      projects.add(new Project(id, due, needsVolt, req, initialSamples));
    }

    return projects;
  }

  private static void validateMatrix() {
    if (MATRIX_COLUMNS.length != TESTS.size()) {
      throw new IllegalStateException("MATRIX_COLUMNS length must match TESTS size. " +
          "MATRIX_COLUMNS=" + MATRIX_COLUMNS.length + " TESTS=" + TESTS.size());
    }
    if (PROJECT_MATRIX.length == 0) throw new IllegalStateException("PROJECT_MATRIX is empty");
    if (NEEDS_VOLT.length != PROJECT_MATRIX.length) {
      throw new IllegalStateException("NEEDS_VOLT length must match PROJECT_MATRIX rows. " +
          "NEEDS_VOLT=" + NEEDS_VOLT.length + " rows=" + PROJECT_MATRIX.length);
    }
    if (DUE_DATES.length != PROJECT_MATRIX.length) {
      throw new IllegalStateException("DUE_DATES length must match PROJECT_MATRIX rows. " +
          "DUE_DATES=" + DUE_DATES.length + " rows=" + PROJECT_MATRIX.length);
    }
    for (int r = 0; r < PROJECT_MATRIX.length; r++) {
      if (PROJECT_MATRIX[r].length != TESTS.size()) {
        throw new IllegalStateException("PROJECT_MATRIX row " + r + " length must be " + TESTS.size() +
            " but was " + PROJECT_MATRIX[r].length);
      }
      if (NEEDS_VOLT[r] != 0 && NEEDS_VOLT[r] != 1) {
        throw new IllegalStateException("NEEDS_VOLT[" + r + "] must be 0/1");
      }
      if (DUE_DATES[r] < 0) {
        throw new IllegalStateException("DUE_DATES[" + r + "] must be >= 0");
      }
      for (int c = 0; c < PROJECT_MATRIX[r].length; c++) {
        int v = PROJECT_MATRIX[r][c];
        if (v != 0 && v != 1) {
          throw new IllegalStateException("PROJECT_MATRIX[" + r + "][" + c + "] must be 0/1");
        }
      }
    }
  }

  private static Map<String, Integer> indexById(List<TestDef> tests) {
    Map<String, Integer> m = new LinkedHashMap<>();
    for (int i = 0; i < tests.size(); i++) {
      m.put(tests.get(i).id, i);
    }
    return m;
  }
}
