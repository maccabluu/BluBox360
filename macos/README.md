# BluBox 360 macOS

BluBox 360 macOS is the native Mac edition of BluBox 360. It is developed on the separate `macos-preview` branch so Android releases remain independent while the Mac frontend and emulator core are ported.

## Current stage

Version: `2.2.0-preview`

Target: Apple Silicon and Intel Macs running macOS 13 or newer.

The 2.2 preview brings the current Mac work together into one universal build:

- Native SwiftUI BluBox 360 application with verified startup self-test.
- Universal arm64 and x86_64 application binary.
- Library, Recently Played, Favorites, Profile, Settings and Diagnostics pages.
- ISO, ZAR and XEX file selection and recursive folder scanning.
- Search, sorting, library refresh and missing-file cleanup.
- Persistent game library with custom cover artwork.
- File size and source-folder information on game cards.
- Local BluBox profile name and profile data folder.
- Per-game save folders ready for native core integration.
- Shader cache, log, profile, saves and core directories under Application Support.
- 30 FPS and 60 FPS launch targets.
- Performance, Balanced and Quality graphics presets.
- Native and HD+ render-scale choices.
- FPS display request passed to the native core bridge.
- Smart Heat Guard launch policy for serious thermal pressure and Low Power Mode.
- Metal GPU, architecture, macOS version, memory, controller, thermal and Low Power Mode diagnostics.
- Connected-controller names and counts.
- Native core bridge with process output capture, stop control and core log.
- Core launch environment for profile, save, shader cache, log and graphics settings.
- Developer override support through `BLUBOX360_MAC_CORE`.
- Universal `.app`, `.zip` and `.dmg` packaging.

Game emulation is **not enabled yet**. The Android JNI libraries cannot be used as native macOS binaries. The remaining emulator milestone is a native PowerPC/Xenia-derived core plus a Mac-compatible graphics backend.

## Core bridge

BluBox looks for a native executable named `blubox360-core` in this order:

1. Path supplied through `BLUBOX360_MAC_CORE` for development.
2. Inside the app's `Contents/Resources` directory.
3. `~/Library/Application Support/BluBox 360/Core/blubox360-core`.

When a native core is present, BluBox launches the selected game and supplies environment values including:

- `BLUBOX360_PLATFORM=macOS`
- `BLUBOX360_VERSION=2.2`
- `BLUBOX360_TARGET_FPS`
- `BLUBOX360_GRAPHICS_PRESET`
- `BLUBOX360_RENDER_SCALE`
- `BLUBOX360_SHOW_FPS`
- `BLUBOX360_SMART_HEAT_GUARD`
- `BLUBOX360_PROFILE`
- `BLUBOX360_SAVE_PATH`
- `BLUBOX360_SHADER_CACHE`
- `BLUBOX360_LOG_PATH`

Core stdout and stderr are shown under Diagnostics > Core Log.

## Port milestones

1. Native Mac application and package build. **Done**
2. Universal Apple Silicon and Intel frontend. **Done**
3. macOS-native core bridge instead of Android JNI. **Frontend bridge done**
4. Apple Silicon executable-memory/JIT host probe. **Done**
5. PowerPC emulator-core port and validation. **In development**
6. Vulkan/MoltenVK or native Metal-compatible graphics path.
7. Audio, controller, save/profile and achievement events from the native core.
8. First real Xbox 360 game boot tests.
9. Developer ID signing and notarized public preview beside the Android APK.

## Build the 2.2 preview

From the repository root on a Mac with Xcode command-line tools installed:

```bash
chmod +x macos/build-macos.sh
./macos/build-macos.sh
```

Outputs are written to `macos/dist/`:

- `BluBox 360.app`
- `BluBox-360-Mac-2.2.0-preview-Universal.zip`
- `BluBox-360-Mac-2.2.0-preview-Universal.dmg`
- `SHA256SUMS.txt`

The development build uses ad-hoc signing. A wider public Mac release should use Apple Developer ID signing and notarization.
