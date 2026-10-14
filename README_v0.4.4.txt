FOTONMAP v0.4.4 - TAM EKRAN KLAVYE DUZELTMESI
=============================================

Belirti:
Tablet yatay moddayken FOTON Klavye açıldığında Android,
Robot Kontrol Sistemi yerine büyük siyah bir "extract editor"
ekranı gösteriyor ve sağda "İLERİ" düğmesi çıkarıyordu.

Sonuç:
- Yazı Robot alanına gidebiliyordu.
- Ancak kullanıcı Robot ekranını göremiyordu.
- "İLERİ" basılırsa odak bir sonraki alana (örn. Başlangıç Bacası) kayabiliyordu.

Düzeltme:
FotonInputMethodService içinde:
    override fun onEvaluateFullscreenMode(): Boolean = false

Böylece:
- Robot Kontrol Sistemi ekranda kalır.
- FOTON Klavye altta sabit kalır.
- Tam ekran siyah extract editor açılmaz.
- Haritadan gelen A41-A42 gibi seçim doğrudan Robot ekranındaki alanda görülür.
- Normal FOTON Türkçe Q klavye yazımı devam eder.
- v0.4.3'teki bütün seçim/yazma mantığı korunur.

Beklenen APK:
FOTONMAP-Hat-Secici-v0.4.4-debug.apk
