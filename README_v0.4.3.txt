FOTONMAP v0.4.3 - KOTLIN DERLEME DUZELTMESI
============================================

GitHub Actions hatasi:
FotonInputMethodService.kt:303
'if' must have both main and 'else' branches if used as an expression

Sebep:
clearEditorText() icindeki runCatching/try blogunun son ifadesi bir if/else idi.
Kotlin bu blogu deger ureten expression olarak yorumladigi icin icteki
if (before > 0 || after > 0) blogunda else istedi.

Duzeltme:
try blogunun sonuna acikca Unit eklendi.
Böylece if bloklari statement olarak kalir ve Kotlin expression kurali tetiklenmez.

Bu surum v0.4.2'deki:
- FOTON Turkce Q klavye
- Haritadan Hat Sec
- Donanim tusu gibi Robot'a yazma
- Clipboard/paste fallback
- FOTONMAP
- Zoom/pan
ozelliklerini aynen korur.

Beklenen APK:
FOTONMAP-Hat-Secici-v0.4.3-debug.apk
