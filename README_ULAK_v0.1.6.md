# ULAK v0.1.6

## Video motoru
- Media3 / ExoPlayer ana motor olarak korunur.
- Bir yayın READY olup ses üretmesine rağmen 5 saniye içinde video track/decoder oluşturamazsa aynı URL otomatik olarak LibVLC 3.7.5 ile açılır.
- VLC fallback yalnızca gerçek "ses var, video yok" durumunda devreye girer; HTTP 403/404 gibi kaynak hatalarında Xtream URL alternatifleri normal şekilde denenmeye devam eder.
- Kumanda ile yukarı/aşağı kanal değiştirme hem Media3 hem VLC görünümünde korunur.

## Profil kaydı
- SharedPreferences yazımı `apply()` yerine senkron `commit()` kullanır.
- Kayıttan hemen sonra şifre çözülerek profil doğrulanır.
- Kayıt başarısız olursa uygulama artık sessizce kanal ekranına geçmez; ana ekranda kayıt hatasını gösterir.
- Parola Android Keystore AES/GCM ile şifreli tutulmaya devam eder.

## Not
LibVLC kendi codec/native kütüphanelerini taşıdığı için APK boyutu önceki sürümlere göre belirgin şekilde artacaktır. Bu değişiklik özellikle Android TV/emülatörde Media3'ün video decoder oluşturamadığı IPTV akışları için ek uyumluluk sağlar.

Bu ortamda Gradle 9.3.0 dağıtımı services.gradle.org üzerinden indirilemediği için proje burada tam derlenemedi. Android Studio'da Gradle Sync sonrası Run/Build APK ile doğrulanmalıdır.
