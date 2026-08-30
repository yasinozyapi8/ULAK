# ULAK v0.1.4.4 — Şimdilik son test sürümü

Bu sürüm v0.1.4.3 üzerine HTTP tanılama ve yayın isteği uyumluluğu ekler.

- Media3 yayın isteklerinde Android TV uyumlu User-Agent kullanılır.
- `Accept: */*` ve keep-alive başlıkları eklenir.
- HTTP / HTTPS yönlendirmelerine izin verilir.
- `ERROR_CODE_IO_BAD_HTTP_STATUS` durumunda gerçek HTTP kodu gösterilir.
- 401 / 403 / 404 / 5xx hataları Türkçe kısa açıklamayla görünür.
- direct_source → tercih edilen Xtream formatı → alternatif `.ts/.m3u8` fallback akışı korunur.
- ↑/↓ kanal değiştirme, kalıcı Xtream profili ve ULAK player overlay korunur.

Not: Sağlayıcının kapalı/bozuk yayını veya hesap bazlı erişim kısıtı uygulama tarafından düzeltilemez.
