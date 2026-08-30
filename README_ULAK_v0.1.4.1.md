# ULAK v0.1.4.1

Player stabilite güncellemesi.

- Oynatıcıda DPAD ↑ / CHANNEL_UP ile önceki kanal
- DPAD ↓ / CHANNEL_DOWN ile sonraki kanal
- Kanal değişiminde player stop + clearMediaItems + prepare ile temiz yeniden başlatma
- Media3 decoder fallback etkin
- Canlı yayın için daha güvenli buffer ayarları
- Video codec / çözünürlük teşhisi
- Ses codec / örnekleme hızı / kanal sayısı teşhisi
- TS ↔ M3U8 fallback korunur
- Player overlay otomatik kaybolur; OK ile tekrar görünür
