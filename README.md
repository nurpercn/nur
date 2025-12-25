# Chamber Environment Assignment (Voltage-aware)

Bu repo, testlerin **çevresel koşul (Env: sıcaklık/nem)** ve **voltaj ayarı** kısıtları altında odalara (chamber) atanması + çizelgelenmesi için bir sezgisel çözüm içerir.

## Problem özeti

- Her test bir `Env` ister (örn. `25C/NORMAL`, `32C/85%`).
- `Humidity.H85` isteyen testler **sadece** `humidityAdjustable=true` odalarda çalışabilir.
- Voltaj isteyen projelerin testleri **sadece** `voltageCapable=true` odalarda çalışabilir.
- Voltaj-capable odalar **esnek kaynaktır**: hem voltajlı hem voltajsız işleri çalıştırabilir.

## Revize oda-env atama sezgiseli (başlangıç çözümü)

Önce env bazında iş yükü hesaplanır:

- \(D_{env}\): toplam iş yükü (job-days)
- \(DV_{env}\): voltaj gerektiren projelerden gelen iş yükü (job-days)

Atama hedefi, hem toplam darboğazı hem voltaj darboğazını birlikte dengelemektir:

- \(S_{env}\): `env`'ye atanmış **toplam istasyon** sayısı
- \(SV_{env}\): `env`'ye atanmış **voltaj-capable istasyon** sayısı

\[
\text{bottleneck} = \max\Big(\max_{env}\frac{D_{env}}{S_{env}},\ \max_{env}\frac{DV_{env}}{SV_{env}}\Big)
\]

Bu, “voltajlı/voltajsız işleri ayrı ayrı ele alıp kilitleme” yerine **tüm iş yükünü birlikte** görür; ama voltaj işini yalnızca voltaj-capable kapasiteye bağlayan kısıtı da doğrudan hesaba katar.

Uygulama (`HEURISTIC/HeuristicSolver.java` -> `stage1_assignRooms`):

- Voltaj talebi olan her `env` için en az 1 voltaj-capable oda **garanti edilir** (humidity kısıtı korunur).
- Kalan tüm odalar, her adımda `bottleneck` değerini en çok düşüren (ve/veya kapsama eksiklerini kapatan) `env`'ye atanır.
- Sonunda, talep olan her `env` için en az 1 oda ve voltaj talebi olan her `env` için en az 1 voltajlı oda olacak şekilde küçük bir “repair” yapılır.

## Çalıştırma

Kaynaklar paket isimli olduğu için derlerken `-d` kullanın:

```bash
mkdir -p classes
javac -d classes HEURISTIC/*.java
java -cp classes tr.testodasi.heuristic.Main --validate=true
```
