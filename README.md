# FOTONMAP Hat Seçici v0.4.1

Android tablet için FOTONMAP tabanlı hat seçme yardımcısıdır.
Robot Kontrol Sistemi'nin kaynak koduna dokunmaz.

## Güncel mimari

- AutoCAD / Civil 3D: `FOTONMAPEXPORT` ile `.fotonmap` üretilir.
- Android tablet: `.fotonmap` doğrudan açılır; tablette DXF parser kullanılmaz.
- FOTON Klavye: Türkçe Q klavye olarak normal yazı yazabilir.
- `HARİTADAN HAT SEÇ`: seçilen hattın `A41-A40` benzeri kesit numarasını
  Robot Kontrol Sistemi'ndeki aktif yazı alanına aktarır.
- Tamamlanan hatlar yeşil tutulur.
- Durum planı PDF olarak üretilebilir.
- İnternet gerekmez.

## v0.4.1 audit düzeltmeleri

- v0.3.3 kalıcı seçim köprüsü korunur.
- Yeni harita seçimi başlarken eski bekleyen kesit temizlenir.
- Haritadan geri çıkılırsa yarım kalmış IME seçim isteği iptal edilir.
- Activity tekrar kullanıldığında `launchedFromIme` yanlışlıkla true kalmaz.
- IME batch edit işlemleri `finally` ile dengeli kapatılır.
- Enter tuşunda gerçek `sendKeyEvent` sonucu kontrol edilir.
- FOTONMAP'te yalnız bir ucu isimli hatlarda eksik uç için geometrik/text fallback denenir.
- Format sürümü, bounds ve boş segment listesi doğrulanır.
- Eski Accessibility servis kodu kaldırıldı.
- Eski tablet-DXF parser kaldırıldı. Tablet artık yalnız FOTONMAP kullanır.
- Zoom: pinch, çift dokunma, `+`, `-`, `SIĞDIR` devam eder.
- FOTON Klavye Türkçe Q olarak kalıcı varsayılan klavye seçilebilir.
  Android güvenlik modeli nedeniyle uygulama kendisini kullanıcı onayı olmadan
  varsayılan klavye yapamaz; ilk kurulumda kullanıcı bir kez seçmelidir.

## Test edilen FOTONMAP

`leda_atıksu(1).fotonmap`:
- 100 node/baca
- 91 tıklanabilir segment
- 88 segment iki ucu isimli
- A41-A40 = 34.92 m
- A39-A38 = 9.92 m

## GitHub Actions

`main` branch push veya elle workflow çalıştırma sonucunda:
`FOTONMAP-Hat-Secici-v0.4.1-debug.apk`
üretilir.
