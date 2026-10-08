# FOTON DXF Hat Seçici v0.1.0

Android tablet için bağımsız DXF hat seçme yardımcısıdır. Mevcut Robot Kontrol Sistemi uygulamasının kaynak koduna dokunmaz.

## Amaç

- Proje DXF dosyasını tablette açmak
- Hatlara dokunarak iki uçtaki baca adlarını DXF geometrisinden otomatik bulmak
- `A41-A40` gibi Boru Kesit No üretmek
- Geometrik hat uzunluğunu hesaplamak
- Başlangıç / bitiş bacasını göstermek ve gerekirse ters çevirmek
- Onaylanan hattı yeşil göstermek
- Tamamlanan hatları DXF bazında tablette saklamak
- Accessibility Service aktifse Robot Kontrol Sistemi içindeki `Boru Kesit No`, `Başlangıç Bacası`, `Bitiş Bacası`, `Boru Uzunluğu` alanlarına otomatik aktarmayı denemek
- Otomatik aktarım mümkün değilse Boru Kesit No'yu panoya kopyalamak

## Test edilen gerçek DXF

`leda_atıksu(1).dxf` ile parser testi yapılmıştır:

- 92 seçilebilir hat segmenti
- 100 CIRCLE
- 197 TEXT
- Örnek çözüm: `A41-A40`, uzunluk `34.92 m`
- Örnek çözüm: `A39-A38`, uzunluk `9.92 m`

DXF dosyasındaki LINE uçlarının baca merkezlerine oturması bu dosyada doğrulanmıştır.

## Desteklenen DXF varlıkları

- LINE
- CIRCLE
- TEXT
- MTEXT
- LWPOLYLINE
- POLYLINE + VERTEX

## Tablet kullanımı

1. APK'yı kur.
2. Uygulamada `ROBOT ERİŞİMİ` düğmesine basıp FOTON Robot Yardımcısı erişilebilirlik servisini aç.
3. `DXF SEÇ` ile proje DXF'ini bir kez seç.
4. Robot Kontrol Sistemi'nde `Boru Kesit No` alanına dokun.
5. Android erişilebilirlik ağacı alanı görünür kılıyorsa FOTON DXF ekranı otomatik açılır.
6. Hat üzerine dokun.
7. Gerekirse `TERS ÇEVİR`.
8. `ONAYLA`.
9. Hat yeşil kalır ve bilgiler Robot uygulamasına aktarılmaya çalışılır.

Not: Robot Kontrol Sistemi özel çizilmiş bir arayüz kullanıyor ve alanları Android Accessibility'ye göstermiyorsa otomatik tetikleme/yazma çalışmayabilir. DXF seçme ve yeşil ilerleme sistemi yine çalışır; Boru Kesit No panoya kopyalanır.

## GitHub Actions ile APK

Projeyi repo köküne yükleyin. `main` branch'e push edilince veya Actions ekranından elle çalıştırılınca:

`FOTON-DXF-Hat-Secici-v0.1.0-debug.apk`

artifact olarak üretilir.
