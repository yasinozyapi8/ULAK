# ULAK v0.2.9

## RAW TS ses testi
- RAW kanal/gruplarında `/live/*.ts` MPEG-TS yolu artık ilk oynatma adayıdır.
- Yayın Testi ekranında `TS /live` HTTP 200/206 ise özel **TS /LIVE OYNAT** düğmesi görünür.
- Teknik panelde **Audio track: 1 • var / 0 • yok** satırı eklendi.
- Hedef: HLS'de görüntü olup ses olmayan RAW kanallarda TS kaynağında gerçek audio track bulunup bulunmadığını kesinleştirmek.
- TS'de de audio track 0 ise çalışan video korunur; gereksiz VLC geçişi yapılmaz.

versionCode: 29
versionName: 0.2.9
