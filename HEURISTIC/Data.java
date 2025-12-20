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

  /** Bu sürüm sade: sadece JOB_BASED + EDD. */
  public enum SchedulingMode { JOB_BASED }

  /** Bu sürüm sade: sadece EDD. */
  public enum JobDispatchRule { EDD }

  /**
   * Pulldown sonrası \"Other Tests\" başlangıç kuralı.
   *
   * - true  => Projedeki TÜM pulldown job'ları bitmeden hiçbir other test başlamaz (metindeki ifade: \"Pulldown tamamlanmadan...\").
   * - false => Sadece ilgili sample'ın pulldown'u bittiyse o sample üzerinde other test başlayabilir (manuel hesaplarda sık görülen yorum).
   */
  public static final boolean OTHER_TESTS_WAIT_FOR_ALL_PULLDOWNS = true;

  /** Scheduling mode: sadece JOB_BASED. */
  public static SchedulingMode SCHEDULING_MODE = SchedulingMode.JOB_BASED;

  /** Job-based dispatch kuralı: sadece EDD. */
  public static JobDispatchRule JOB_DISPATCH_RULE = JobDispatchRule.EDD;

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

  /**
   * Min sample sayısı (her proje için).
   *
   * Bu çalışma için sample sayısı sabit 3 olacak şekilde kilitlendi.
   */
  public static final int MIN_SAMPLES = 3;

  /**
   * Başlangıç sample sayısı (tüm projeler için).
   *
   * Bu çalışma için sample sayısı sabit 3 olacak şekilde kilitlendi.
   */
  public static int INITIAL_SAMPLES = 3;

  /**
   * Sample artırma/azaltma heuristiği.
   *
   * Bu çalışma için kapalı: sample sayısı sabit 3.
   */
  public static boolean ENABLE_SAMPLE_INCREASE = false;

  /**
   * Sample üst sınırı.
   *
   * Bu çalışma için sample sayısı sabit 3.
   */
  public static int SAMPLE_MAX = 3;

  /** Sample artırma toplam deneme bütçesi (değerlendirme sayısı). */
  public static int SAMPLE_SEARCH_MAX_EVALS = 8000;

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
  // Previous matrix kept for reference/comparison.
  public static final int[][] PROJECT_MATRIX_PREV = new int[][]{
      // G  P 32 25 16 P25 P43 P38 P32 P16 P10 Frz TR C10 C25 C32
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
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}  // P50
  };

  public static final int[][] PROJECT_MATRIX_V9 = new int[][]{
      // Gas PD EE32 EE25 EE16 P25 P43 P38 P32 P16 P10 Frz TR CU10 CU25 CU32
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P1
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P2
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P3
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P4
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P5
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P6
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P7
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P8
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P9
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1, 1, 1}, // P10
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1}, // P11
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P12
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P13
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P14
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P15
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P16
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P17
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P18
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P19
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P20
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P21
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P22
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1, 1, 1}, // P23
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P24
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P25
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P26
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P27
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P28
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P29
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P30
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P31
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P32
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P33
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P34
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P35
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P36
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P37
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P38
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P39
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P40
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P41
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P42
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P43
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P44
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P45
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P46
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1}, // P47
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P48
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P49
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}  // P50
  };

  /**
   * Active matrix selector (runtime):
   * -DmatrixScenario=1 => PROJECT_MATRIX_PREV
   * -DmatrixScenario=2 => PROJECT_MATRIX_V9
   * -DmatrixScenario=3 => PROJECT_MATRIX_LATEST (default)
   *
   * Not: Sohbette paylaştığınız 10 matrisi otomatik koşturabilmek için tüm matrislerin
   * bu dosyada ayrı sabitler olarak bulunması gerekir.
   */
  private static final int MATRIX_SCENARIO = Integer.getInteger("matrixScenario", 3);

  public static final int[][] PROJECT_MATRIX_LATEST = new int[][]{
      // Gas PD EE32 EE25 EE16 P25 P43 P38 P32 P16 P10 Frz TR CU10 CU25 CU32
      // G  P 32 25 16 P25 P43 P38 P32 P16 P10 Frz TR C10 C25 C32
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P1
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P2
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P3
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P4
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P5
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P6
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P7
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P8
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P9
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1}, // P10

      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P11
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P12
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P13
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P14
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P15
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P16
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P17
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P18
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P19
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P20

      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P21
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P22
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P23
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P24
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P25
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P26
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P27
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P28
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P29
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1}, // P30

      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P31
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P32
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P33
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P34
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P35
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P36
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P37
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P38
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P39
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P40

      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P41
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P42
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P43
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1}, // P44
      {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P45
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 1}, // P46
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P47
      {1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1}, // P48
      {1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}, // P49
      {1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1}  // P50
  };

  // Active matrix for current experiments:
  public static final int[][] PROJECT_MATRIX = switch (MATRIX_SCENARIO) {
    case 1 -> PROJECT_MATRIX_PREV;
    case 2 -> PROJECT_MATRIX_V9;
    case 3 -> PROJECT_MATRIX_LATEST;
    default -> throw new IllegalStateException("Unknown matrixScenario=" + MATRIX_SCENARIO);
  };


  /** Proje bazlı voltaj ihtiyacı (Excel NeedsVolt kolonu). Satır sayısı PROJECT_MATRIX ile aynı olmalı. */
  // Scenario selector (for experiments in the paper):
  // - VOLT_SCENARIO=1 => 10 adet "1"
  // - VOLT_SCENARIO=2 => 17 adet "1"
  // - VOLT_SCENARIO=3 => 25 adet "1"
  //
  // Not: Bu dosyada tek bir NEEDS_VOLT dizisi olmalı; senaryoları aşağıdaki sabit belirler.
  // You can override at runtime:
  //   java -DvoltScenario=3 -cp out tr.testodasi.heuristic.Main
  // Default: 1
  // Override to force all-1 or all-0 NEEDS_VOLT:
  // -DvoltAll=1 => all projects need voltage
  // -DvoltAll=0 => no project needs voltage
  private static final int VOLT_ALL = Integer.getInteger("voltAll", -1);

  private static final int VOLT_SCENARIO = Integer.getInteger("voltScenario", 1);

  public static final int[] NEEDS_VOLT = switch (VOLT_ALL) {
    case 0 -> allVolt(0);
    case 1 -> allVolt(1);
    case -1 -> switch (VOLT_SCENARIO) {
      case 1 -> new int[]{
          0, 1, 0, 0, 1, 0, 0, 0, 1, 0,
          0, 0, 0, 1, 0, 0, 0, 1, 0, 0,
          0, 0, 1, 0, 0, 0, 1, 0, 0, 0,
          0, 0, 1, 0, 0, 0, 0, 0, 0, 0,
          1, 0, 0, 0, 0, 0, 0, 1, 0, 0
      };
      case 2 -> new int[]{
          1, 0, 0, 1, 0, 1, 0, 1, 0, 0,
          0, 1, 0, 0, 1, 0, 0, 0, 1, 0,
          1, 0, 0, 1, 0, 0, 0, 1, 0, 1,
          0, 0, 0, 1, 0, 0, 1, 0, 0, 1,
          0, 0, 0, 1, 0, 1, 0, 0, 0, 1
      };
      case 3 -> new int[]{
          0, 1, 1, 0, 1, 0, 1, 0, 1, 1,
          0, 1, 0, 1, 0, 1, 0, 1, 0, 1,
          1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
          1, 1, 0, 0, 1, 0, 1, 0, 1, 0,
          1, 0, 1, 0, 0, 1, 0, 0, 1, 0
      };
      default -> throw new IllegalStateException("Unknown voltScenario=" + VOLT_SCENARIO);
    };
    default -> throw new IllegalStateException("voltAll must be -1/0/1 but was " + VOLT_ALL);
  };

  private static int[] allVolt(int v) {
    int[] a = new int[PROJECT_MATRIX.length];
    for (int i = 0; i < a.length; i++) a[i] = v;
    return a;
  }

  /** Proje bazlı due date (Excel due date kolonu). Satır sayısı PROJECT_MATRIX ile aynı olmalı. */
  // Due-date scenario selector (for experiments):
  // - dueScenario=1 => UNIFORM (40..120 balanced)
  // - dueScenario=2 => mostly close to 120 (110/115/120 band)
  // - dueScenario=3 => mostly close to 40 (40/45/50/55 band)
  private static final int DUE_SCENARIO = Integer.getInteger("dueScenario", 1);

  public static final int[] DUE_DATES = switch (DUE_SCENARIO) {
    case 1 -> new int[]{ // UNIFORM: 40..120 aralığına dengeli dağılmış (karışık sırada)
        70, 115, 45, 100, 55, 120, 80, 95, 40, 110,
        60, 85, 50, 105, 65, 90, 75, 115, 45, 100,
        55, 40, 80, 95, 110, 60, 85, 50, 105, 65,
        90, 75, 115, 45, 100, 55, 40, 80, 95, 110,
        60, 85, 50, 105, 65, 90, 75, 70, 120, 70
    };
    case 2 -> new int[]{ // 120'ye yakın çoğunluk (karışık sırada)
        90, 120, 70, 115, 100, 110, 85, 120, 95, 105,
        120, 115, 110, 100, 120, 115, 105, 110, 95, 120,
        115, 110, 100, 120, 115, 110, 85, 105, 120, 115,
        110, 95, 120, 115, 110, 120, 115, 105, 100, 120,
        115, 110, 120, 120, 115, 80, 120, 75, 90, 120
    };
    case 3 -> new int[]{ // 40'a yakın çoğunluk (karışık sırada)
        90, 40, 120, 45, 70, 50, 110, 55, 80, 40,
        100, 45, 75, 50, 95, 40, 105, 45, 60, 50,
        85, 40, 45, 55, 65, 40, 50, 45, 40, 55,
        45, 50, 40, 45, 40, 55, 50, 45, 40, 50,
        45, 40, 55, 40, 45, 40, 50, 40, 40, 40
    };
    default -> throw new IllegalStateException("Unknown dueScenario=" + DUE_SCENARIO);
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
    int samples = Math.max(MIN_SAMPLES, initialSamples);

    validateMatrix();

    List<Project> projects = new ArrayList<>();
    for (int r = 0; r < PROJECT_MATRIX.length; r++) {
      String id = "P" + (r + 1);
      int due = DUE_DATES[r];
      boolean needsVolt = NEEDS_VOLT[r] == 1;

      boolean[] req = new boolean[TESTS.size()];
      for (int c = 0; c < TESTS.size(); c++) {
        req[c] = PROJECT_MATRIX[r][c] == 1;
      }
      projects.add(new Project(id, due, needsVolt, req, samples));
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