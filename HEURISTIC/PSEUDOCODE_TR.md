# HEURISTIC – Pseudocode (TR)

Bu doküman, `HEURISTIC/` altındaki Java implementasyonunun **4 ana parçasını** pseudocode olarak özetler:

- **Dış döngü**: `HeuristicSolver.solve()`
- **Set atama (oda/env atama)**: `HeuristicSolver.stage1_assignRooms(...)`
- **Scheduling (JOB_BASED + EDD)**: `Scheduler.evaluateJobBased(...)`
- **Sample artırma (local search)**: `HeuristicSolver.stage2_increaseSamples(...)`

---

## 1) Dış Döngü Pseudocode (Outer Loop)

**Amaç**: Oda setlerini (env atamaları) üret, gerekiyorsa oda-local-search uygula, sample sayılarını iyileştir, schedule üret ve total lateness’i ölç. Oda atamaları değişmezse erken dur.

**Girdi**: Proje matrisi → `projects = Data.buildProjects(Data.INITIAL_SAMPLES)`  
**Çıktı**: Her iterasyon için `Solution(iter, totalLateness, projects, roomAssignment, results, schedule)` listesi

```text
SOLVE():
  projects ← buildProjects(INITIAL_SAMPLES)
  solutions ← []
  prevRoomAssignment ← null

  currentProjects ← deepCopy(projects)

  FOR iter = 1..5:
    # Stage1: oda/env set ataması
    room ← STAGE1_ASSIGN_ROOMS(currentProjects)

    # Opsiyonel: oda setlerini schedule objective ile lokal arama (swap/move)
    IF ENABLE_ROOM_LOCAL_SEARCH:
      baseScore ← SCORE_ROOM(room, currentProjects)        # total lateness
      improvedRoom ← IMPROVE_ROOMS_BY_LOCAL_SEARCH(currentProjects, room, baseScore)
      IF SCORE_ROOM(improvedRoom, currentProjects) < baseScore:
        room ← improvedRoom

    # Konverjans: oda setleri değişmiyorsa dur
    IF prevRoomAssignment != null AND prevRoomAssignment == room:
      BREAK

    # Stage2: sample artırma (opsiyonel) + scheduling değerlendirmesi
    IF ENABLE_SAMPLE_INCREASE:
      improvedProjects ← STAGE2_INCREASE_SAMPLES(room, currentProjects)
    ELSE:
      improvedProjects ← deepCopy(currentProjects)

    eval ← SCHEDULER_EVALUATE(improvedProjects, room)      # schedule + total lateness
    solutions.ADD( Solution(iter, eval.totalLateness, improvedProjects, room, eval.projectResults, eval.schedule) )

    # Opsiyonel: schedule doğrulama (ihlal varsa hata)
    IF ENABLE_SCHEDULE_VALIDATION:
      ASSERT VALIDATE_SCHEDULE(improvedProjects, room, eval.schedule) is empty

    prevRoomAssignment ← room
    currentProjects ← deepCopy(improvedProjects)

  RETURN solutions
```

---

## 2) Set Atama Pseudocode (Stage1 – Oda/ENV Atama)

**Amaç**: Her odayı tek bir `Env` (sıcaklık + nem) ile set et. Talep edilen env’ler “boşta” kalmasın; voltaj ihtiyacı olan işler için ilgili env’de en az bir voltajlı oda olsun; %85 nem gerektiren env sadece `humidityAdjustable=true` odalara gitsin.

**Temel fikir**: *Load balancing* – “talep / atanmış istasyon” oranı en yüksek env’i seç.

**Tanımlar**:
- `work(env) = Σ (jobs × durationDays)`  
  - `jobs = 1` (GAS/OTHER/CU)  
  - `jobs = samples` (PULLDOWN çünkü her sample için job var)
- `demandTotal[env]`: tüm iş yükü (job-day)
- `demandVolt[env]`: sadece `needsVoltage=true` projelerin iş yükü (job-day)

```text
STAGE1_ASSIGN_ROOMS(projects):
  demandedEnvs ← ∅
  demandTotal[env] ← 0
  demandVolt[env] ← 0

  # 1) Env bazlı iş yüklerini hesapla
  FOR each project p in projects:
    FOR each test t in TESTS:
      IF p requires t:
        jobs ← (t.category == PULLDOWN) ? p.samples : 1
        w ← jobs * t.durationDays
        demandTotal[t.env] += w
        IF p.needsVoltage:
          demandVolt[t.env] += w
        demandedEnvs.ADD(t.env)

  IF demandedEnvs is empty: ERROR

  assignedStationsTotal[env] ← 0
  assignedStationsVolt[env]  ← 0

  voltRooms    ← CHAMBERS where voltageCapable=true  (stations desc)
  nonVoltRooms ← CHAMBERS where voltageCapable=false (stations desc)

  assignment[chamberId] ← null

  # 2) Önce voltajlı odaları ata (primary = demandVolt)
  FOR each chamber c in voltRooms:
    env ← PICK_BEST_ENV(
            chamber=c,
            envCandidates=demandedEnvs,
            primaryDemand=demandVolt,
            fallbackDemand=demandTotal,
            primaryAssignedStations=assignedStationsVolt,
            totalAssignedStations=assignedStationsTotal,
            prioritizePrimary=true
          )
    assignment[c.id] ← env
    assignedStationsTotal[env] += c.stations
    assignedStationsVolt[env]  += c.stations

  # 3) Sonra non-volt odaları ata (volt demand zaten volt odalarda karşılanmaya çalışıldı)
  FOR each chamber c in nonVoltRooms:
    env ← PICK_BEST_ENV(
            chamber=c,
            envCandidates=demandedEnvs,
            primaryDemand=demandTotal,      # non-volt için primary = total
            fallbackDemand=demandTotal,
            primaryAssignedStations=assignedStationsTotal,
            totalAssignedStations=assignedStationsTotal,
            prioritizePrimary=false
          )
    assignment[c.id] ← env
    assignedStationsTotal[env] += c.stations

  # 4) Repair-1: Voltaj isteyen env’ler için en az 1 voltajlı oda garanti et
  FOR each env in demandedEnvs:
    IF demandVolt[env] > 0 AND assignedStationsVolt[env] == 0:
      ch* ← choose a voltage-capable chamber to reassign to env
            (must satisfy humidity feasibility; choose minimal “loss”)
      assignment[ch*.id] ← env

  # 5) Repair-2: Talep edilen her env için en az 1 oda garanti et
  FOR each env in demandedEnvs:
    IF demandTotal[env] > 0 AND env not in assignment.values:
      ch* ← choose a chamber currently assigned to the “least critical” env
            (e.g., minimize curDemand / (curStations+1))
            (must satisfy humidity feasibility)
      assignment[ch*.id] ← env

  RETURN assignment
```

**Env seçme skoru (`PICK_BEST_ENV`)**:

```text
PICK_BEST_ENV(chamber, envCandidates, primaryDemand, fallbackDemand,
              primaryAssignedStations, totalAssignedStations, prioritizePrimary):
  bestEnv ← null
  bestScore ← -∞

  FOR each env in envCandidates:
    IF env.humidity == H85 AND chamber.humidityAdjustable == false:
      CONTINUE

    # demand = (voltage rooms için) önce demandVolt varsa onu kullan, yoksa total’a düş
    d1 ← primaryDemand[env]
    d2 ← fallbackDemand[env]
    demand ← (prioritizePrimary AND d1 > 0) ? d1 : d2

    assigned ← prioritizePrimary ? primaryAssignedStations[env] : totalAssignedStations[env]
    score ← demand / (assigned + 1)   # load-balancing

    IF score > bestScore:
      bestScore ← score
      bestEnv ← env

  IF bestEnv == null: ERROR
  RETURN bestEnv
```

---

## 3) Scheduling Pseudocode (JOB_BASED + EDD)

**Amaç**: Oda/env setleri sabitken, tüm projelerin tüm “job”larını çizelgele; kapasite = (chamber × station).  
**Kural**: Global dispatch “EDD”: due date küçük olan proje job’ı öncelik alır (tie-break: daha erken başlayabilen).

### 3.1 State model (ProjectState)

Her proje için:
- `sampleAvail[s]`: sample `s` tekrar kullanılabilir olacağı en erken zaman
- Gas: `gasScheduled`, `gasEnd`
- Pulldown: `pulldownScheduledBySample[s]`, `pulldownDoneCount`, `pulldownEndMax`
- Other: `remainingOthers`, `otherStartedCount`, `maxStartOther`
- CU: `remainingCu`
- `completionMax`: projenin tüm scheduled işlerinin max end’i

### 3.2 Candidate üretimi (ready jobs)

Her iterasyonda her proje için “hazır” işlerden en iyi adayı üret:
- GAS: her zaman `release = 0`
- PULLDOWN: GAS bittiyse, her sample için `release = gasEnd`
- OTHER:
  - Eğer `OTHER_TESTS_WAIT_FOR_ALL_PULLDOWNS=true` ise: tüm pulldown’lar bitmeden OTHER başlamaz (`release = pulldownEndMax`)
  - Aksi halde: gas sonrası başlayabilir (`release = gasEnd`), sampleAvail zaten sample bazlı çakışmayı engeller
- CU:
  - Önce tüm OTHER testlerin **başlamış** olması gerekir (bitmesi değil)
  - Ayrıca pulldown fazı da tamamlanmış olmalı
  - `release = max(maxStartOther, pulldownEndMax veya gasEnd)`

Her candidate için planlama:
- Doğru `env`’ye sahip chamber’lar arasından en erken başlayabilen `(chamber, station)` bulunur
- `needsVoltage=true` ise sadece `voltageCapable=true` chamber’lar uygundur
- `start = max(earliest, stationAvail[station])`
- `end = start + durationDays`

### 3.3 Global dispatch (EDD)

```text
EVALUATE_JOB_BASED(projects, chamberEnvAssignment):
  chambers ← build ChamberInstance list from CHAMBERS:
              each has env=assignment[chamberId], stationAvail[stations]=0

  stateByProject ← { p.id -> new ProjectState(p) }
  schedule ← []

  WHILE true:
    best ← null

    # her projeden 1 "en iyi hazır candidate"
    FOR each projectState st in stateByProject:
      cand ← st.BEST_READY_CANDIDATE(chambers)
      IF cand is null: continue
      IF best is null OR cand is betterThan(best):
        best ← cand

    IF best is null:
      BREAK   # planlanacak job kalmadı

    # Candidate uygula: istasyon ve sample availability güncelle
    APPLY(best.planned):
      best.planned.chamber.stationAvail[stationIdx] ← end
      st.sampleAvail[sampleIdx] ← end

    st.ON_SCHEDULED(jobKind, test, planned)   # remaining listeleri azaltır, faz state’lerini günceller
    schedule.ADD(best.toScheduledJob())

  # sonuçlar
  totalLateness ← 0
  results ← []
  FOR each st in stateByProject:
    completion ← st.completionMax
    lateness ← max(0, completion - st.projectDueDate)
    totalLateness += lateness
    results.ADD(ProjectResult(p.id, completion, due, lateness))

  RETURN (totalLateness, results, schedule)
```

**`betterThan` (EDD skoru)**:

```text
Candidate.score():
  # due küçük olsun, ayrıca erken başlayabilen öne geçsin
  return -(dueDate * 1e6) - startTime

Candidate.betterThan(other):
  if score different: higher score wins   # daha küçük due + daha küçük start
  else: earlier start wins
  else: smaller due wins
```

---

## 4) Sample Artırma Pseudocode (Stage2 – Local Search)

**Amaç**: Oda setleri sabitken, `samples` sayısını proje bazında artırıp/azaltarak total lateness’i iyileştirmek.  
**Sınırlar**: `samples ∈ [MIN_SAMPLES, SAMPLE_MAX]`  
**Bütçe**: toplam `scheduler.evaluate(...)` çağrısı ≤ `SAMPLE_SEARCH_MAX_EVALS` (en az 500)

**Hareket seti**: Her proje için denenecek delta’lar:
- Eğer `samples == MIN_SAMPLES`: `{+1, +2}`
- Aksi halde: `{+1, +2, -1, -2}`

**Kabul kuralı**:
- Daha düşük `totalLateness` her zaman kabul
- Eşit lateness’te daha az sample tercih (çözümü minimal tutmak için)

```text
STAGE2_INCREASE_SAMPLES(roomAssignment, startProjects):
  current ← deepCopy(startProjects)

  # global minimum enforce
  FOR each project p in current:
    p.samples ← max(p.samples, MIN_SAMPLES)

  baseEval ← SCHEDULER_EVALUATE(current, roomAssignment)

  evalBudget ← max(500, SAMPLE_SEARCH_MAX_EVALS)
  evals ← 0
  passes ← 0

  WHILE evals < evalBudget:
    improvedAny ← false
    passes++

    FOR i = 0..current.size-1 and evals < evalBudget:
      p ← current[i]
      curS ← p.samples

      bestS ← curS
      bestEval ← baseEval

      deltas ← (curS <= MIN_SAMPLES) ? [+1,+2] : [+1,+2,-1,-2]

      FOR each d in deltas and evals < evalBudget:
        ns ← curS + d
        IF ns < MIN_SAMPLES OR ns > SAMPLE_MAX OR ns == curS:
          CONTINUE

        cand ← deepCopy(current)
        cand[i].samples ← ns
        e ← SCHEDULER_EVALUATE(cand, roomAssignment)
        evals++

        IF e.totalLateness < bestEval.totalLateness OR
           (e.totalLateness == bestEval.totalLateness AND ns < bestS):
          bestEval ← e
          bestS ← ns

      accept ← (bestEval.totalLateness < baseEval.totalLateness) OR
               (bestEval.totalLateness == baseEval.totalLateness AND bestS < curS)

      IF accept AND bestS != curS:
        p.samples ← bestS
        baseEval ← bestEval
        improvedAny ← true

    IF improvedAny == false: BREAK
    IF passes > 200: BREAK   # safety

  RETURN current
```

