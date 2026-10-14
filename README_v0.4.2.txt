FOTONMAP v0.4.2 - ROBOT ALANINA DONANIM TUSU YAZMA
===================================================

Belirti:
- FOTON Klavye açılıyor.
- Harita ve hat seçimi çalışıyor.
- Bekleyen seçim geliyor.
- Ancak Robot Kontrol Sistemi'nin Boru Kesit No alanı commitText() ile yazılmıyor.

Yeni yaklaşım:
- Haritadan gelen A41-A40 / A38-A38-1 gibi kesit numaraları artık
  Android InputConnection.commitText() ile değil,
  FIZIKSEL KLAVYE TUS OLAYLARI gibi gönderilir.
- Örn A41-A40 için:
  Shift+A, 4, 1, -, Shift+A, 4, 0
  tuşlarına basılmış gibi KeyEvent gönderilir.
- Yazmadan önce alanın sonuna gidip eski metni DEL ile temizlemeyi dener.
- KeyEvent yolu kabul edilmezse son emniyet olarak clipboard + paste denenir.
- Normal FOTON Q klavye yazımı değişmedi.
- v0.4.1 seçim köprüsü, zoom/pan ve FOTONMAP mantığı aynen korunur.

Test:
1) Robot -> Boru Kesit No alanına dokun.
2) HARİTADAN HAT SEÇ.
3) Bir hat seç -> ONAYLA.
4) Robot'a dön.
5) Otomatik yazmazsa SEÇİMİ YAZ.
6) Beklenen: A41-A40 gibi değer normal donanım klavyesinden yazılmış gibi alana düşer.

Sürüm: 0.4.2
Beklenen APK:
FOTONMAP-Hat-Secici-v0.4.2-debug.apk
