# ULAK v0.3.5 — RAW VLC Motoru

Bu sürüm RAW kanalların oynatılmasını normal Media3 ekranından ayırır.

- Normal kanallar: Media3 + mevcut FFmpeg Audio extension.
- RAW kanallar: ayrı `RawVlcPlayerActivity` + LibVLC tam TS demux/codec zinciri.
- RAW oynatıcı ayrı Android process (`:rawplayer`) içinde çalışır; native VLC çökerse ana ULAK süreci izole kalır.
- VLC artık Compose `AndroidView` içine attach/detach edilmez; klasik `VLCVideoLayout` yaşam döngüsü kullanılır.
- MPEG-1/2 Audio için VLC'nin kendi demux ve decoder zinciri kullanılır.
- RAW oynatıcıda sol/sağ ile alternatif Xtream URL yolları denenebilir.

Not: XCIPTV'den kaynak kod veya binary kopyalanmamıştır. Yalnızca gözlenen çift-oynatıcı mimarisi ULAK'ta bağımsız olarak uygulanmıştır.
