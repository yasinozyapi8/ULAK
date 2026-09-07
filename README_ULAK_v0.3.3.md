# ULAK v0.3.3 — PC VLC Uyumluluk Modu

Bu sürüm RAW kanallarda PC'deki çalışan IPTV uygulamasının davranışını taklit etmek için hazırlandı.

- Xtream oturumundan sonra `get.php?type=m3u_plus&output=ts` M3U listesi uygulama içinden `VLC/3.0.18` User-Agent ile istenir.
- M3U içindeki kanal URL'leri stream_id ve kanal adına göre Xtream kanal kayıtlarıyla eşleştirilir.
- RAW kanalda M3U'dan gelen gerçek URL bulunduysa ilk aday olarak kullanılır.
- Bu gerçek M3U URL'si Media3'te yeniden oluşturulmadan doğrudan Android LibVLC'ye verilir.
- LibVLC için PC programındaki değerler taklit edilir: `VLC/3.0.18 LibVLC/3.0.18` User-Agent ve 1000 ms network cache.
- M3U çağrısı başarısız olursa mevcut Xtream API oynatma yolları çalışmaya devam eder.
- Teknik panelde `PC M3U: aktif / hazır / bulunamadı` bilgisi görünür.

Test: RAW bir kanal aç. Teknik panelde `PC M3U: aktif` ve `Motor: LibVLC` görünüyorsa PC uyumluluk yolu devrededir. Ses gelirse sorun çözüldü. Gelmezse v0.3.2 tabanına geri dönüp FFmpeg/demux çözümüne geçilebilir.
