# ULAK v0.2.6 — Xtream Kanal Sırası Tanılama

Bu test sürümü, IPTV sağlayıcısının Xtream `get_live_streams` yanıtındaki kanal sırasını doğrudan TV ekranında incelemek için hazırlanmıştır.

## Yeni: SIRA TESTİ
Ana ekranda kayıtlı Xtream profil varken **SIRA TESTİ** düğmesi görünür.

Ekran iki liste gösterir:
- **Sunucudan Gelen İlk 30**: `get_live_streams` JSON dizisinin ham ilk 30 kaydı.
- **beIN Eşleşmeleri**: adında `beIN` geçen kanallar.

Her kanal için:
- `Sıra`: JSON dizisindeki gerçek geliş pozisyonu (`i + 1`)
- `num`: sağlayıcının Xtream `num` alanı
- `kat`: `get_live_categories` içindeki kategori pozisyonu
- `id`: Xtream `stream_id`
- kategori adı

gösterilir.

Bu ekran hiçbir sıralama uygulamaz; amacı başka IPTV uygulamalarının hangi alanı kullanıyor olabileceğini teşhis etmektir.

## Sürüm
- versionName: 0.2.6
- versionCode: 26

## Test
Android Studio'da APK oluşturup TV'ye yükleyin. Ana ekrandan **SIRA TESTİ** seçeneğine girip ekranın fotoğrafını alın. Özellikle sağ taraftaki beIN listesindeki `Sıra` ve `num` değerleri karşılaştırma için önemlidir.

Not: Bu ortamda Gradle dağıtımı internetten indirilemediği için derleme burada doğrulanamamıştır; Android Studio tarafında Gradle sync/build yapılmalıdır.
