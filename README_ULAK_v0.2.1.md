# ULAK v0.2.1 — GitHub OTA Güncelleme

Repo: `yasinozyapi8/ULAK`

## Yeni
- Ana ekranda **GÜNCELLEME** bölümü.
- GitHub `releases/latest` API üzerinden yeni sürüm kontrolü.
- Release içindeki ilk `.apk` assetini bulma.
- APK indirme ilerlemesi.
- Android TV paket yükleyicisini açma.
- Android 8+ için “Bilinmeyen uygulamaları yükle” izin ekranına yönlendirme.
- v0.2.0 player/profil özellikleri korunur.

## GitHub Release kuralı
1. Release tag sürüm numarası olmalı: ör. `v0.2.2`.
2. Release asset olarak bir `.apk` eklenmeli: ör. `ULAK-v0.2.2.apk`.
3. Release **Latest** olmalı; draft olmamalı.
4. Repo public ise GitHub API için token gerekmez.

## İlk OTA testi
v0.2.1'i TV'ye bir kez normal APK olarak kur. Ardından ileride `v0.2.2` release'i ve APK'sını GitHub'a yükle. ULAK > GÜNCELLEME > GÜNCELLEMELERİ KONTROL ET ile OTA akışı test edilir.

## İmza notu
Android, mevcut uygulamanın üstüne güncelleme kurulabilmesi için yeni APK'nın aynı uygulama kimliği ve aynı imza anahtarıyla imzalanmasını ister. Android Studio debug APK'larını aynı bilgisayarda üretirsen genellikle aynı debug keystore kullanılır. Kalıcı dağıtım için ileride sabit bir release signing key kullanılmalıdır.
