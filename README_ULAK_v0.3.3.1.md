# ULAK v0.3.3.1

Hotfix after v0.3.3 native LibVLC crash on channel open.

- Restores the stable v0.3.2 player flow.
- Keeps the PC-style Xtream `get.php?...type=m3u_plus&output=ts` lookup.
- If the M3U returns a real channel URL, that URL is tried first with Media3.
- Does **not** force LibVLC for the PC M3U URL.
- Existing safe diagnostics / RAW PSI-PMT analyzer remain available.

Goal: test whether the exact M3U URL itself fixes RAW audio without triggering the old-TV LibVLC native crash.
