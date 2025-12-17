package tr.testodasi.heuristic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tüm veriler kodun içindedir.
 *
 * Not: Proje matriksi ekranda görünen desene göre programatik üretilmiştir.
 * İsterseniz {@link #buildProjects(int)} metodunu kendi 50 satırlık gerçek verinizle birebir değiştirebilirsiniz.
 */
public final class Data {
  private Data() {}

  /**
   * Pulldown sonrası \"Other Tests\" başlangıç kuralı.
   *
   * - true  => Projedeki TÜM pulldown job'ları bitmeden hiçbir other test başlamaz (metindeki ifade: \"Pulldown tamamlanmadan...\").
   * - false => Sadece ilgili sample'ın pulldown'u bittiyse o sample üzerinde other test başlayabilir (manuel hesaplarda sık görülen yorum).
   */
  public static final boolean OTHER_TESTS_WAIT_FOR_ALL_PULLDOWNS = true;

  /** Tüm projelerin due date'ine eklenecek sabit offset (gün). */
  public static final int DUE_DATE_OFFSET_DAYS = 30;

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

  /** 50 proje: başlangıç sample = 2. */
  public static List<Project> buildProjects(int initialSamples) {
    if (initialSamples <= 0) throw new IllegalArgumentException("initialSamples must be positive");

    // Due date deseni (ekrandaki değerler): 30,60,95,120,50,70 tekrar.
    int[] dueCycle = new int[]{30, 60, 95, 120, 50, 70};

    List<Project> projects = new ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      String id = "P" + i;
      int due = dueCycle[(i - 1) % dueCycle.length] + DUE_DATE_OFFSET_DAYS;

      // Ekran görüntüsündeki görünen desene göre: 2 projede voltaj=1, sonraki 2 projede 0, tekrar.
      boolean needsVolt = ((i - 1) / 2) % 2 == 0;

      boolean[] req = new boolean[TESTS.size()];
      // Gas + Pulldown hepsi 1 görünüyor.
      req[TEST_INDEX.get("GAS_43")] = true;
      req[TEST_INDEX.get("PULLDOWN_43")] = true;

      // Energy: tek bir varyant seçiliyor (odd->EE32+EE16, even->EE25)
      if (i % 2 == 1) {
        req[TEST_INDEX.get("EE_32")] = true;
        req[TEST_INDEX.get("EE_16")] = true;
      } else {
        req[TEST_INDEX.get("EE_25")] = true;
      }

      // Performance: PERF_25 her projede 1 görünüyor. Diğerleri sırayla.
      req[TEST_INDEX.get("PERF_25")] = true;
      if (i % 2 == 1) {
        req[TEST_INDEX.get("PERF_43")] = true;
        req[TEST_INDEX.get("PERF_16")] = true;
      } else {
        req[TEST_INDEX.get("PERF_38")] = true;
        req[TEST_INDEX.get("PERF_10")] = true;
      }
      // PERF_32 ekranda hep 0 göründüğü için kapalı bırakıldı.

      // İlk 10 projede Freezing + Temperature Rise = 1 (ekrandaki ilk blok gibi)
      if (i <= 10) {
        req[TEST_INDEX.get("FREEZE_25")] = true;
        req[TEST_INDEX.get("TEMP_RISE_25")] = true;
      }

      // Consumer Usage kolonlarında ekranda hep 1 göründüğü için hepsi aktif.
      req[TEST_INDEX.get("CU_10")] = true;
      req[TEST_INDEX.get("CU_25")] = true;
      req[TEST_INDEX.get("CU_32")] = true;

      projects.add(new Project(id, due, needsVolt, req, initialSamples));
    }

    return projects;
  }

  private static Map<String, Integer> indexById(List<TestDef> tests) {
    Map<String, Integer> m = new LinkedHashMap<>();
    for (int i = 0; i < tests.size(); i++) {
      m.put(tests.get(i).id, i);
    }
    return m;
  }
}
