# FOTONMAP Hat Seçici v0.7.0

## Karmaşık/eski dosya uyumluluğu

- Dosya açılır açılmaz artık otomatik yeniden yazılmaz.
- Bozuk / eksik bounds varsa segmentlerden tekrar hesaplanır.
- Tek bir bozuk segment bütün dosyayı düşürmez; o segment atlanır.
- `segments` yanında eski `lines` alanı da okunabilir.
- FOTONMAP version > 1 olsa bile bilinen alanlar best-effort okunur.
- v1.0 MBCS / Windows-1254 dosyaları için encoding fallback eklendi.
- Aynı dosyaya kayıt sırasında `rwt + truncate` denenir; eski byte artıklarının
  JSON'u bozması engellenir.

## Renkli hat desteği

Yeni exporter her entity için:
- `colorRgb`
- `colorAci`

bilgisi yazabilir.

Aynı FOTONMAP içinde farklı altyapı / hat renkleri korunur.

### Yeşil kuralı

Kaynak CAD'deki yeşil, FOTON'da kaynak renk olarak ASLA yeşil gösterilmez.
Otomatik turkuaza çevrilir.

Yeşil yalnızca:
**Görüntüleme tamamlandı**
durumuna ayrılmıştır.

Eski veya elle hazırlanmış FOTONMAP yeşil renk taşısa bile Android tarafında
ikinci güvenlik olarak yine turkuaza çevrilir.

## PDF

Tamamlanan hat: yeşil.
Bekleyen hat: kaynak CAD rengi.
Kaynak beyaz, beyaz PDF üzerinde kaybolmasın diye koyu griye çevrilir.

Beklenen APK:
`FOTONMAP-Hat-Secici-v0.7.0-debug.apk`
