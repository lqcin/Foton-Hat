# FOTONMAP Hat Seçici v0.9.0

## Yeni: Canlı hedef çizgisi

Haritada bir hatta dokunulduğunda:

- o hat mevcut seçim olarak kalır,
- GPS konumundan seçilen hattın en yakın noktasına düz bir rehber çizgisi çizilir,
- çizgi mor/kesikli gösterilir,
- mesafe canlı olarak metre cinsinden güncellenir.

Örnek:

`A41-A42 • 18,6 m`

Kullanıcı yaklaştıkça değer azalır.

3 metre ve altına gelince yalnız bilgi amaçlı:

`A41-A42 • 2,4 m • YAKLAŞTIN`

yazısı gösterilir.

Bu sistem:
- rota hesabı yapmaz,
- yol tarifi yapmaz,
- en yakın hattı otomatik seçmez,
- hattı kendi kendine değiştirmez.

Yalnız düz mesafe görselidir.

## KAPAT

Hedef bilgi çubuğundaki `KAPAT` tuşu:

- rehber çizgisini kaldırır,
- canlı mesafe göstergesini kapatır,
- seçilmiş hattı ve ONAYLA işlemini bozmaz.

Aynı veya başka hatta tekrar dokunulursa rehber tekrar açılır.

## Korunan özellikler

- canlı GPS konumu
- `◎ FOKUS`
- GPS doğruluğu
- renkli FOTONMAP
- kaynak yeşil -> turkuaz
- tamamlanan hat -> yeşil
- aynı FOTONMAP dosyasına completed kaydı
- son proje otomatik açma
- FOTON Klavye
- Son Seçilen Hat / YAZ
- klavyeyi gizleme
- PDF
- zoom / pan

Beklenen APK:
`FOTONMAP-Hat-Secici-v0.9.0-debug.apk`
