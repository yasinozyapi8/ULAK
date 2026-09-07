# ULAK v0.2.5

## Sessiz yayın güvenlik düzeltmesi

- Görüntü var ancak 4.5 saniye içinde ses akışı algılanmazsa artık doğrudan LibVLC'ye geçilmez.
- Önce sıradaki Xtream HLS/TS yayın alternatifi Media3 ile denenir.
- Tüm alternatiflerde görüntü var ama ses yoksa çalışan video korunur; uygulama kapanmaz ve teknik panel ses akışının bulunamadığını bildirir.
- LibVLC fallback, ses var/görüntü yok veya video decoder uyumsuzluğu senaryolarında kullanılmaya devam eder.

Bu değişiklik özellikle eski Android TV cihazlarında sessiz HLS yayınından LibVLC'ye geçiş sırasında görülebilen native çökme riskini azaltır.
