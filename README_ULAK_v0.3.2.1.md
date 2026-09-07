# ULAK v0.3.4 — RAW Yayın Stabilizasyonu

- RAW kanalda görüntü başladıktan sonra ses track'i oluşmazsa yayın URL'sine ikinci PSI/PMT HTTP bağlantısı açılmaz.
- RAW kanalda çalışan video korunur; sırf ses bulunamadı diye HLS/TS alternatifleri arasında otomatik sıçrama yapılmaz.
- Önceki v0.3.2 teşhisinde PMT içinde MPEG-1 Audio PID bulunduğu doğrulandı. Bu hotfix, sonraki gerçek demux/decoder çözümüne geçmeden önce canlı görüntünün kesilmesini engellemek içindir.
- Normal kanallardaki alternatif URL davranışı korunur.
