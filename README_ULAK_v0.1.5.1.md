# ULAK v0.1.5.1

Video regression hotfix.

- Removed global forced MPEG-TS extractor flags and forced MIME type from v0.1.5.
- Restored Media3 automatic container sniffing/default extractors.
- Restored HLS-first preference when Xtream advertises HLS.
- Retained /live and rewrite Xtream URL fallbacks.
- Added audio-only watchdog: if audio is present but no video format appears after 5 seconds, ULAK automatically tries the next stream candidate.
- Keeps HTTP diagnostics and TV-friendly request headers.
