# ULAK v0.1.5 – Video Uyumluluğu

- Xtream canlı yayınlarında hem `/live/user/pass/id.ext` hem `/user/pass/id.ext` rewrite URL biçimleri denenir.
- Hesap TS destekliyorsa TS öncelikli denenir; HLS yine fallback olarak korunur.
- MPEG-TS yayınlarında AUD/IDR eksikliği için Media3 TS extractor uyumluluk bayrakları etkinleştirildi.
- `.ts` ve `.m3u8` adaylarında MIME tipi Media3'e açıkça verilir.
- Mevcut profil kaydı, kanal listesi, hata teşhisi ve kumanda ile kanal değiştirme korunur.
