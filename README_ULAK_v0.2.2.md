# ULAK v0.2.2 — Akıllı Oynatıcı

Bu sürüm v0.2.1 OTA tabanı üzerine akıllı oynatıcı seçimini ekler.

## Oynatma kararları
- AVC / H.264 algılanır ve video karesi oluşursa Media3 / ExoPlayer devam eder.
- HEVC / H.265 algılandığında TV'nin yerel decoder'ına kısa bir fırsat verilir. İlk kare gelirse ExoPlayer korunur; gelmezse aynı yayın LibVLC ile denenir.
- Ses var fakat video track/decoder yoksa otomatik LibVLC fallback yapılır.
- LibVLC de başarısız olursa kanalın sıradaki Xtream yayın alternatifi denenir.
- Teknik panelde algılanan codec, aktif motor ve akıllı seçim kararı gösterilir.

## Sürüm
- versionName: 0.2.2
- versionCode: 22

Bu paket geliştirme/test sürümüdür. Gerçek Android TV üzerinde test edilmesi önerilir.
