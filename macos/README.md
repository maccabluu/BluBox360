# BluBox 360 macOS

BluBox 360 macOS is the native Mac edition of BluBox 360. It is being developed separately from the Android release so Android updates stay stable while the Mac frontend and emulator core are ported.

## Current stage

Version: `0.1.0-preview`

Target: Apple Silicon Macs running macOS 13 or newer.

The first preview contains:

- A native SwiftUI BluBox 360 application.
- BluBox-style library interface.
- ISO, ZAR and XEX file selection.
- Persistent local game-library entries.
- Finder integration for imported game files.
- Apple Silicon `.app`, `.zip` and `.dmg` packaging.

Game emulation is **not enabled yet** in this first preview. The Android JNI libraries cannot run as a native macOS emulator core. The Xenia-derived core and graphics backend need a proper macOS port before the Play button launches Xbox 360 games.

## Port milestones

1. Native Mac application and package build.
2. macOS-native core bridge instead of Android JNI.
3. PowerPC JIT validation on Apple Silicon.
4. Metal-compatible graphics path for Xbox 360 rendering.
5. Audio, controller and save/profile integration.
6. First game boot tests.
7. Signed and notarized public preview release beside the Android APK.

## Build the native preview

From the repository root on a Mac with Xcode command-line tools installed:

```bash
chmod +x macos/build-macos.sh
./macos/build-macos.sh
```

Outputs are written to `macos/dist/`:

- `BluBox 360.app`
- `BluBox-360-Mac-0.1.0-preview-Apple-Silicon.zip`
- `BluBox-360-Mac-0.1.0-preview-Apple-Silicon.dmg`
- `SHA256SUMS.txt`

The development build uses ad-hoc signing. A public downloadable release should use Apple Developer ID signing and notarization.
