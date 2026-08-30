# ULAK v0.1.4.3

Akıllı stream tanılama ve çoklu URL fallback güncellemesi.

- Xtream canlı kanallarda `direct_source` varsa önce onu dener.
- Ardından sağlayıcının tercih ettiği standart canlı URL (`.ts` veya `.m3u8`) denenir.
- Son olarak diğer standart biçim denenir.
- Oynatma hatası olduğunda Media3 hata kodu ve temel cause adı ekranda görünür.
- Tüm adaylar başarısız olursa son üç denemenin özeti gösterilir.
- ↑ / ↓ kanal değiştirme korunmuştur.
- Profil kaydı ve Keystore parola koruması korunmuştur.

Not: Tanılama ekranı kullanıcı adı/parola veya tam stream URL'sini göstermez.
