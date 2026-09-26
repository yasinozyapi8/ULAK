# ULAK v0.3.9 — Xtream Direct Stream

- Xtream `direct_source` varsa birincil yayın kaynağı olarak doğrudan kullanılır.
- `direct_source` yoksa kanal doğrudan standart `/live/<user>/<pass>/<stream_id>.ts` adresiyle açılır.
- Kanal açılmadan önce HLS/TS yolları arasında ön tarama yapılmaz.
- Alternatif HLS/TS ve rewrite adresleri yalnızca birincil kaynak oynatılamazsa fallback olarak denenir.
- Son çalışan kaynak hafızası birincil Xtream kaynağını artık geçersiz kılmaz; yalnızca fallback sırasını hızlandırır.
- Normal kanallar Media3, RAW kanallar mevcut çalışan LibVLC yolunu kullanmaya devam eder.
- v0.3.8 oynatıcı/EPG/kumanda davranışları korunur.
