# Heuristic Scheduler (Java)

Bu repo, test odası/oda-atama + çizelgeleme problemi için basit bir sezgisel çözücü içerir:

- **Stage 1**: Odalara (chamber) çevresel set değeri (sıcaklık/nem) atama
- **Stage 2**: Job-based EDD çizelgeleme (bu repoda sample sayısı her proje için sabit: 3)
- **Stage 3**: Oda atamasında opsiyonel local-search (swap/move)

Kod giriş noktası: `tr.testodasi.heuristic.Main`

## Çalıştırma

Projede build aracı yok; doğrudan `javac/java` ile çalıştırabilirsiniz.

```bash
mkdir -p out
javac -d out HEURISTIC/*.java
java -cp out tr.testodasi.heuristic.Main --help
```

## Örnekler

```bash
# Varsayılan çalıştırma
java -cp out tr.testodasi.heuristic.Main

# Daha fazla log + CSV çıktı
java -cp out tr.testodasi.heuristic.Main --verbose --csv

# Belirli proje detay dökümü + CSV dizini
java -cp out tr.testodasi.heuristic.Main --dumpProject=P1 --csvDir=csv_out
```

## Batch (Excel/CSV ile 40 instance)

Excel’den tek bir CSV export edip, aynı komutla tüm instance’ları çalıştırabilirsiniz.

### `instances.csv` formatı

Zorunlu kolonlar (case-insensitive):

- `instanceId`
- `projectId`
- `dueDateDays`
- `needsVolt` (0/1 veya true/false)
- Test matrisi kolonları: `Data.MATRIX_COLUMNS` ile aynı isim/sıra (0/1)

Opsiyonel kolonlar:

- `samples` (proje bazlı başlangıç sample sayısı) *(bu repoda sabit sample=3 zorlanır, kolon varsa yok sayılır)*
- `initialSamples` (instance bazlı varsayılan sample; `samples` yoksa kullanılır) *(bu repoda yok sayılır)*
- `enableSampleIncrease`, `sampleMax`, `sampleSearchMaxEvals`
- `enableRoomLS`, `roomLSMaxEvals`, `roomLSSwap`, `roomLSMove`, `roomLSIncludeSample`
- `validate`

### Çalıştırma

```bash
java -cp out tr.testodasi.heuristic.Main --batch instances.csv --batchOut batch_results.csv
```

Çıktı: `batch_results.csv` (tek dosya; satır başına 1 instance özeti)

### Detaylı batch çıktı gösterimi

```bash
# Özet + detay (proje sonuçları + oda env atamaları)
java -cp out tr.testodasi.heuristic.Main --batch instances.csv --batchOut batch_results.csv --batchDetails batch_details

# İstersen tüm schedule satırlarını da ekle (dosya büyük olabilir)
java -cp out tr.testodasi.heuristic.Main --batch instances.csv --batchOut batch_results.csv --batchDetails batch_details --batchSchedule
```

Detay klasörü içinde:
- `batch_project_results.csv`
- `batch_chamber_env.csv`
- `batch_schedule.csv` (sadece `--batchSchedule` ile)

## Veri / Instance güncelleme

Tüm veri gömülü halde `HEURISTIC/Data.java` içindedir.

- **Proje matrisi**: `Data.PROJECT_MATRIX`
- **Voltaj ihtiyacı**: `Data.NEEDS_VOLT`
- **Due date**: `Data.DUE_DATES`
- **Oda (chamber) tanımları**: `Data.CHAMBERS`

Bu dizileri güncellediğinizde, solver yeni instance üzerinden çalışır.
