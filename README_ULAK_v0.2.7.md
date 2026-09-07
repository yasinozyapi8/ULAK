# ULAK v0.2.7

Bu sürüm iki tanılama/kanal düzeni geliştirmesi içerir.

## Xtream kategori sırası
- Kanal tarayıcısında kategoriler artık alfabetik değil, `get_live_categories` yanıtındaki sağlayıcı sırasına göre gösterilir.
- `Tümü` görünümünde kanallar önce `categoryOrder`, sonra sunucudan geliş sırasına (`serverOrder`) göre dizilir.
- Kategori içindeki kanal sırası sağlayıcının `get_live_streams` sırasını korur.

## Yayın Testi
- Ana ekrana `YAYIN TESTİ` eklendi.
- Seçilen kanalın `direct_source`, HLS ve TS aday URL'leri ayrı ayrı yoklanır.
- Her yol için HTTP kodu, Content-Type ve kısa teşhis gösterilir.
- 200/206 başarılı yol; 401/403/404 ve 5xx ayrı teşhis edilir.
- Test yalnızca kısa bir veri parçası okur; tam yayını indirmez.

## Sürüm
- versionName: 0.2.7
- versionCode: 27

Not: Bu ortamda Gradle bağımlılıkları internetten indirilemediği için APK derlemesi doğrulanamadı. Android Studio'da build sonucu esas alınmalıdır.
