# ULAK v0.1.1

Bu sürümde M3U URL üzerinden canlı TV akışı eklendi.

## Akış
1. PROFİL EKLE
2. M3U / URL
3. Yetkili M3U playlist adresini gir
4. BAĞLAN
5. Kategori ve kanal seç
6. Kanalı Media3 / ExoPlayer ile oynat

## Eklenenler
- Tek geçişli, büyük listelere daha uygun M3U parser
- HTTP/HTTPS playlist indirme ve temel hata yönetimi
- M3U kategori gruplama (`group-title`)
- Android TV kanal listesi
- Media3 ExoPlayer tam ekran oynatma
- v0.1.1 sürüm numarası

## Sonraki sürüm
- Xtream Codes: sunucu + kullanıcı adı + parola
- Profil kaydetme
- Kanal arama ve favoriler
- EPG

Not: Bu çalışma ortamında Gradle dağıtımı internetten indirilemediği için `assembleDebug` burada tamamlanamadı. Android Studio'da Gradle Sync sonrası derleme yapılmalıdır.
