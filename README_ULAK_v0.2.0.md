# ULAK v0.2.0

TV-first product UI release.

- Keeps the v0.1.6 playback foundation: Media3 + LibVLC compatibility fallback.
- New compact premium live-TV overlay.
- Technical diagnostics are hidden during normal watching.
- Press OK/Enter to open/close the right-side Technical Information panel.
- Up/Channel Up: previous channel; Down/Channel Down: next channel.
- Profile persistence remains encrypted with Android Keystore.
- Version bumped to 0.2.0 / versionCode 20.

## Development deployment
During development, connect the real Android TV to Android Studio using ADB over the local network. Android Studio Run then installs the new debug build over the existing app, preserving app data as long as the applicationId and signing key remain unchanged.
