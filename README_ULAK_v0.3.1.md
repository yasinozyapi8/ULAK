# ULAK v0.3.1

## HLS vs TS gerçek ses karşılaştırması

- Yayın Testi ekranında kabul edilen **HLS /live** ve **TS /live** yolları artık ayrı ayrı oynatılabilir.
- `HLS /LIVE OYNAT` ve `TS /LIVE OYNAT` butonları aynı kanalın iki kaynağını doğrudan karşılaştırmak için eklendi.
- Oynatma sırasında Teknik Bilgiler panelindeki **Audio track** satırı ile ses akışının gerçekten bulunup bulunmadığı görülebilir.
- Ekrandaki sürüm etiketi v0.3.1 olarak düzeltildi.
- versionCode 31 / versionName 0.3.1.

### Test
1. YAYIN TESTİ'ne gir.
2. RAW bir kanal seç ve TEST ET.
3. HLS /live ve TS /live HTTP 200/206 ise önce HLS /LIVE OYNAT, sonra geri dönüp TS /LIVE OYNAT.
4. Her iki testte Teknik Bilgiler > Ses ve Audio track satırlarını karşılaştır.
