# FOTONMAP Hat Seçici v0.6.0

Bu sürüm saha kullanım mantığını tamamlar.

## 1. FOTON Klavye aşağı indirilebilir

Klavyenin üst barında:

`⌄ GİZLE`

düğmesi vardır. Robot Kontrol Sistemi tamamen görünür olur.
Bir yazı alanına tekrar dokunulduğunda FOTON Klavye yeniden açılır.

## 2. Son açılan FOTONMAP otomatik açılır

Bir `.fotonmap` dosyası bir kez seçildiğinde Android SAF URI erişimi
READ + WRITE olarak kalıcı alınmaya çalışılır.

Uygulama tekrar açıldığında aynı dosya otomatik yüklenir.
Yeni proje için yalnızca `FOTONMAP AÇ` ile başka dosya seçilir.

## 3. Tamamlanan hat aynı .fotonmap dosyasına kaydolur

ONAYLA ile tamamlanan hat için dosyaya yalnızca segment ID yazılır:

```json
"completed":["1DA-0","1DB-0","1DC-0"]
```

Tarih, saat, "tamamlandı" metni vb. tekrar yazılmaz.
Dosya gereksiz büyümez.

`İŞARETİ KALDIR` da aynı listeyi günceller.

## 4. Aynı dosyanın üzerine otomatik kayıt

Kullanıcı ayrıca KAYDET demez.

Her ONAYLA / İŞARETİ KALDIR işleminde seçilmiş mevcut `.fotonmap`
aynı URI üzerinden güncellenir.

Yazmadan önce uygulamanın private alanında son sağlam JSON yedeği tutulur.
Yazma sonrası JSON ve `completed` listesi geri okunup doğrulanır.
Hata olursa kaynak dosyayı son sağlam haline geri döndürmeye çalışır.

## 5. Eski sürüm uyumluluğu

Eski `.fotonmap` dosyasında `completed` alanı yoksa:
- önceki tablet içi CompletionStore kaydı okunur,
- mevcut tamamlanan hatlar korunur,
- ilk fırsatta yeni dosya içi `completed` alanına taşınır.

## 6. FOTON Klavye akışı

Harita:
`Hattı seç -> ONAYLA`

Robot:
`Boru Kesit No -> FOTON Klavye -> Son Seçilen Hat -> A41-A42 YAZ`

Otomatik olarak yanlış alana yazma yapılmaz.

## 7. Koordinat / altlık

Kaynak AutoCAD koordinat kökeni `.fotonmap` içinde korunmaya devam eder.
Sahada ITRF kullanılıyor.

Gerçek OSM/uydu altlığını doğru çakıştırmak için ITRF'nin projeksiyon
detayı (ör. TM dilimi / merkezi meridyen veya kesin EPSG tanımı) ayrıca
sabitlenmelidir. Bu sürümde sahte bir altlık dönüşümü yapılmamıştır.

Beklenen APK:
`FOTONMAP-Hat-Secici-v0.6.0-debug.apk`
