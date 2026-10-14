FOTONMAP v0.5.1 - KOTLIN DERLEME DÜZELTMESİ
=============================================

GitHub Actions hatası:
FotonInputMethodService.kt:737
'if' must have both main and 'else' branches if used as an expression

Sebep:
clearCurrentConnectionText() içindeki runCatching bloğunun son ifadesi
iç içe bir if yapısıydı. Kotlin bunu expression olarak yorumladığı için
en içteki if için else istedi.

Düzeltme:
runCatching bloğunun sonunda açıkça Unit döndürülüyor.

v0.5.0'daki bütün özellikler korunur:
- Yeni FOTON klavye tasarımı
- Son Seçilen Hat kartı
- A41-A42 YAZ kısayolu
- Karakter karakter normal commitText yazma
- Haritadan Hat Seç
- Zoom / pan
- Tamamlanan hatları yeşil gösterme
- PDF
- Tam ekran extract editor kapalı

Beklenen APK:
FOTONMAP-Hat-Secici-v0.5.1-debug.apk
