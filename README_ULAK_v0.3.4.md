# ULAK v0.3.4 — FFmpeg MPEG Audio Kurtarma

Bu sürüm RAW IPTV yayınlarında PMT içinde görülen MPEG-1/2 Audio (audio/mpeg-L2) sesini Android TV'nin platform codec'ine bırakmak yerine Media3 FFmpeg audio renderer ile çözmeyi dener.

## Teknik değişiklikler
- Media3 1.9.0 ile hizalı `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` eklendi.
- `DefaultRenderersFactory` extension renderer modu `PREFER` yapıldı.
- Decoder fallback etkinleştirildi.
- Video tarafı MediaCodec/Media3 üzerinde kalır; FFmpeg öncelikle ses renderer olarak devreye girer.
- RAW kanallarda çalışan video korunur; otomatik ikinci PSI bağlantısı açılmaz.

## Test
RAW kanalı normal açın. Ses gelirse teknik panelde Audio track artık dolu olmalıdır ve Motor satırı `Media3 + FFmpeg Audio` görünür.

## Lisans notu
Bu test sürümündeki Jellyfin FFmpeg AAR GPLv3 lisanslıdır. Kalıcı dağıtım öncesinde lisans uyumluluğu değerlendirilmelidir. Gerekirse AndroidX Media3 resmi FFmpeg extension'ı kaynak koddan uygun FFmpeg lisans seçenekleriyle derlenebilir.
