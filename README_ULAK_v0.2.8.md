# ULAK v0.2.8

## Yeni: Favoriler
- Ana ekrandaki FAVORİLER kartı aktif.
- Kanal bilgi panelinden `☆ FAVORİYE EKLE` / `★ FAVORİDEN ÇIKAR`.
- Favoriler cihazda SharedPreferences ile kalıcı tutulur.
- Favori kanallar kanal listesinde yıldızla işaretlenir.
- Favoriler ekranından oynatıp BACK ile tekrar Favoriler'e dönülür.

## Yayın testi geliştirildi
- HTTP yol testi sonrası 200/206 veren ilk yol için `ÇALIŞAN YOLU OYNAT` düğmesi görünür.
- Bu düğme sadece başarılı URL'yi oynatıcıya verir; diğer URL'lere geçmez.
- Teknik panelde gerçek Video/Ses algısı gözlemlenebilir. Böylece HTTP 200 olması ile gerçek ses/video oynatımı ayrı ayrı doğrulanır.

## Sürüm
- versionCode: 28
- versionName: 0.2.8

Not: Bu çalışma ortamında Gradle wrapper internet erişimi olmadığı için derleme doğrulanamadı. Android Studio'da APK oluştururken hata çıkarsa logu paylaşın.
