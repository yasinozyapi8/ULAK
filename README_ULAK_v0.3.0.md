# ULAK v0.3.0 — Kaynak Derin Tanılama

- versionCode 30 / versionName 0.3.0
- Xtream kanal nesnesindeki `direct_source`, `stream_source`, `stream_type`, `container_extension` ve `custom_sid` ipuçlarını saklar.
- YAYIN TESTİ ekranında kaynak derin tanılama kartı gösterir.
- `direct_source` varsa testte ayrı etiketlenir.
- RAW kanallarda `direct_source` artık TS/HLS türetilmiş URL'lerden önce denenir.
- Amaç: başka uygulamalarda sesli çalışan RAW kanalların sağlayıcı tarafından verilen gerçek kaynağını bulmak.

## Test
1. YAYIN TESTİ → sorunlu RAW kanal.
2. Kaynak Derin Tanılama kartında `direct_source` / `stream_source` durumuna bak.
3. TEST ET.
4. `direct_source` 200/206 ise onu oynat.
5. Teknik panelde Audio track sonucunu kontrol et.
