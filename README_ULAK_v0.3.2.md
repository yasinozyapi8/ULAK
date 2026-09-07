# ULAK v0.3.2 — RAW Ses Çözümleme

Bu sürüm, görüntü geldiği halde Media3 ses track'i göremediğinde yayın akışının MPEG-TS PAT/PMT tablolarını otomatik inceler.

## Yeni tanılama
- HLS ise ilk medya segmentini çözüp MPEG-TS içeriğini inceler.
- Doğrudan TS ise akışın ilk bölümünü inceler.
- PAT üzerinden PMT PID'yi bulur.
- PMT içindeki video, ses ve özel PES PID'lerini sınıflandırır.
- H.264/H.265, MPEG Audio, AAC, AC-3 ve E-AC-3 için temel tanıma yapar.
- Teknik panelde `PSI / PMT`, `PMT PID`, `Video PID`, `Audio PID` ve kaynak tipi görünür.

## Karar mantığı
- PMT içinde ses PID'si varsa ama Media3 `Audio track: 0` diyorsa sorun URL/HLS/TS seçimi değil; demux/codec/özel yayın işleme tarafındadır.
- PMT içinde ses PID'si de yoksa seçili yayın yolunun kendisi sessizdir ve sağlayıcının/diğer oynatıcının farklı bir kaynak yolu kullandığı anlaşılır.

Analiz normal sesli kanallarda gereksiz ağ isteği oluşturmaz; yalnızca görüntü var + ses yok durumu kesinleştikten sonra başlar.
