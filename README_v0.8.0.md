# FOTONMAP Hat Seçici v0.8.0

## Canlı konum

Bu sürüm en yakın hat hesabı yapmaz ve otomatik hat seçmez.

Sadece:

- tabletin canlı GNSS/GPS konumunu haritada mavi nokta olarak gösterir,
- Android'in verdiği yatay konum doğruluğunu `GPS ±3,8 m` gibi gösterir,
- `◎ FOKUS` düğmesi ile haritayı kullanıcı konumuna ortalar.

Harita konumu otomatik takip etmez.
Kullanıcı isterse FOKUS'a basar.

## GPS doğruluk rengi

- `≤ 5 m` : yeşil
- `5–12 m`: sarı
- `> 12 m` : kırmızı

Bu yalnız saha farkındalığı içindir, ölçme sonucu değildir.

## Koordinat dönüşümü

Varsayılan saha projeksiyonu:

- ITRF / TUREF
- TM30
- EPSG:5254
- GRS80
- merkezi meridyen 30°
- ölçek 1.0
- false easting 500000 m

Eski FOTONMAP dosyalarında projection alanı yoksa merkezi meridyen 30°
varsayılır; dosyanın `origin` koordinatı kullanılmaya devam eder.

## Korunan v0.7 özellikleri

- renkli FOTONMAP
- kaynak yeşil -> turkuaz
- tamamlanan hat -> yeşil
- aynı `.fotonmap` dosyasına completed ID kaydı
- son proje otomatik açma
- FOTON Türkçe Q klavye
- Son Seçilen Hat / YAZ
- klavyeyi gizleme
- PDF
- zoom / pan / + / - / SIĞDIR

Beklenen APK:
`FOTONMAP-Hat-Secici-v0.8.0-debug.apk`
