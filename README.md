# Heuristic Scheduler (Java)

Bu repo, test odası/oda-atama + çizelgeleme problemi için basit bir sezgisel çözücü içerir:

- **Stage 1**: Odalara (chamber) çevresel set değeri (sıcaklık/nem) atama
- **Stage 2**: Job-based EDD çizelgeleme + opsiyonel sample sayısı yerel araması
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

## Veri / Instance güncelleme

Tüm veri gömülü halde `HEURISTIC/Data.java` içindedir.

- **Proje matrisi**: `Data.PROJECT_MATRIX`
- **Voltaj ihtiyacı**: `Data.NEEDS_VOLT`
- **Due date**: `Data.DUE_DATES`
- **Oda (chamber) tanımları**: `Data.CHAMBERS`

Bu dizileri güncellediğinizde, solver yeni instance üzerinden çalışır.
