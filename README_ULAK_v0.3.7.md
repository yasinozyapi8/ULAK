# ULAK v0.3.7 — Birleşik TV Kumanda + EPG + Oynatma İyileştirmeleri

Bu sürümde normal Media3 kanalları ile RAW/LibVLC kanallarının kumanda davranışları birbirine yaklaştırıldı.

## Eklenenler
- ↑ / ↓: önceki / sonraki kanal
- ← / →: ses azalt / artır
- OK: teknik bilgi
- Uzun OK: favoriye ekle / favoriden çıkar
- Son kanal / önceki kanal davranışı: KEYCODE_LAST_CHANNEL ve MEDIA_PREVIOUS desteklenir
- RAW oynatıcı kanal listesini Xtream profilinden arka planda yükler; kanal değiştirirken LibVLC oturumu korunur
- Çalışan yayın yolu kanal bazında hatırlanır ve sonraki açılışta önce denenir
- EPG kısa program bilgisi: Şimdi / Sırada
- Çözünürlük ve FPS bilgisi: Media3 doğrudan, LibVLC tarafında track bilgisinden otomatik okunmaya çalışılır
- Buffer süresi teknik panelde gösterilir
- 12 saniyeyi aşan buffer durumunda bir kez aynı kaynağa yeniden bağlanır; devam ederse alternatif yayın yoluna geçer
- RAW ses çözümü LibVLC tam TS demux + codec zinciriyle korunur
- Kanal bilgi kartı normal ve RAW oynatıcıda aynı hiyerarşiye yaklaştırıldı

## Not
Gradle wrapper bu çalışma ortamında Gradle 9.3.0 dağıtımını services.gradle.org üzerinden indiremediği için APK derlemesi burada doğrulanamadı. Android Studio'da derlenmelidir.

versionCode: 38
versionName: 0.3.7
