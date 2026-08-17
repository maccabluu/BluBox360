# BluBox 360

BluBox 360 is a public alpha Xbox 360 emulator for ARM64 Android handhelds,
created by Macca and the BluBox team. The project focuses on modern Snapdragon
and Adreno devices, with the AYN Thor Pro as the main development target.

> Public alpha: compatibility varies by game. Expect graphical issues, crashes,
> and higher device temperatures in demanding titles. Keep backups of important
> saves and read [PUBLIC_ALPHA_TESTING.md](PUBLIC_ALPHA_TESTING.md) before testing.

## Latest release

### BluBox 360 0.15.2 public alpha

[Download BluBox 360 0.15.2-alpha for ARM64 Android](https://github.com/maccabluu/BluBox360/releases/download/v0.15.2-alpha/BluBox-360-0.15.2-alpha-arm64.apk)

Android 10 or newer, ARM64, and Vulkan are required.

APK SHA-256:
`56f0ba9acf20188fd876a97bf1414f335fe00d9cab133b243d621b92adef7267`

[View the v0.15.2-alpha release](https://github.com/maccabluu/BluBox360/releases/tag/v0.15.2-alpha)

## What changed in 0.15.2

- Fixed the Settings > App screen showing the old `0.12.0` version.
- The app version shown in Settings is now injected automatically during release builds.
- Strengthened the built-in updater version check.
- Old releases are ignored when checking for updates.
- The update popup appears only when a published GitHub release is newer than the installed version.
- The updater re-checks the installed version before showing a prompt or downloading an APK.
- GitHub release checks bypass stale cache responses.
- SHA-256 verification remains enabled before an update is installed.
- Uses the same permanent BluBox signing certificate as 0.15.0 and 0.15.1.

When 0.15.2 is installed and 0.15.2 is the newest published release, the update
popup should stay hidden. It should appear again only after a newer BluBox
release is published.

## Built-in updates

BluBox checks the official GitHub Releases feed after launch. When a newer alpha
is available, the app offers:

- **Update now** to download the APK inside BluBox.
- **What's new** to read the release notes.
- **Later** to continue using the current version.

BluBox verifies the downloaded APK against the release SHA-256 file when one is
supplied. Android's Package Installer then handles the final installation
confirmation.

Version 0.15.0 established the permanent BluBox release certificate. Versions
0.15.1 and 0.15.2 use the same certificate. Future releases must continue using
this certificate so Android accepts them as normal updates and keeps installed
BluBox data.

## Install or update

1. Download `BluBox-360-0.15.2-alpha-arm64.apk`.
2. If you already have 0.15.0 or 0.15.1 installed, install 0.15.2 directly over it.
3. Android should treat it as an update because the permanent signing certificate matches.
4. Open BluBox and confirm **Settings > App** shows `BluBox 360 0.15.2 public alpha`.
5. Keep your profiles and saves backed up while testing alpha builds.

Moving directly from 0.14.0 still requires the one final uninstall because
0.14.0 used a temporary signing certificate.

## Main features

- ARM64 Android Xbox 360 emulation based on a Xenia-derived core.
- PowerPC JIT and Vulkan rendering.
- ISO, `default.xex`, and ZAR launch support from internal storage or microSD.
- Single-game import, game-folder scanning, and library refresh.
- Three game library views: 3D Shelf, 3D Grid, and Compact 3D List.
- Custom game cover import.
- Performance, Balanced, HD, and custom renderer modes.
- Native through 7× internal resolution options.
- FXAA, CAS/FSR, anisotropic filtering, shader-worker controls, and pipeline preloading.
- Rootless AdrenoTools-compatible Turnip driver import on supported Adreno devices.
- Selectable 30 FPS or 60 FPS target for compatible games.
- Fable II safety preset with a 24 FPS ceiling.
- Low Heat Mode and Android thermal protection.
- ClusterTune support for per-app CPU and GPU profiles.
- Xbox 360 controller mapping for AYN and standard Android gamepads.
- Controller profiles, macros, stick tuning, trigger tuning, vibration, and Harry Potter precision aiming.
- Automatic per-profile save-game data.
- Up to eight BluBox profiles.
- User-uploaded profile photos instead of the old avatar creator.
- Profile photos shown in profile cards, account badge, side menu, and local Xbox profile artwork.
- Local achievement tracking and gamerscore.
- AYN Thor lower-screen FPS, temperature, battery, charging, and achievement display.
- Mods tab for importing and managing Xenia `.patch.toml` files.
- Portable BluBox backup and restore for profiles, saves, artwork, and settings.
- Original BluBox startup chime and optional boot animation.

## Profile photos

Open **Profiles**, choose a profile, then select **Choose profile photo**. BluBox
opens Android's image picker and stores a local copy for the selected profile.
Use **Remove photo** to return to the initial-based fallback image.

The selected photo is also used when BluBox prepares local Xbox 360 profile
artwork for the emulation core.

## Adding games

Open the Library and press **+**. Choose a supported game file or a folder
containing legal backups you own. BluBox can scan ISO, ZAR, and extracted
`default.xex` files directly from internal storage or microSD.

Large game images stay in their original storage location. BluBox does not copy
them into app storage.

## Renderer and performance

Open **Settings > Performance** to choose frame-rate and low-heat options. Open
**Settings > Renderer** for resolution, aspect ratio, anti-aliasing, sharpening,
anisotropic filtering, shader compilation, GPU readback, shader cache, and
custom Vulkan driver controls.

The general 60 FPS option is a ceiling. Games with their own internal 30 FPS
lock remain at their original rate unless a compatible game-specific patch
exists.

Fable II title ID `4D5307F1` uses a conservative automatic recovery preset with
native rendering, UMA readback, two shader workers, and a 24 FPS ceiling.

## AYN Thor lower screen

Open **Settings > Achievements** to enable or disable the lower-screen panel.
During gameplay BluBox can show live FPS, device temperature, battery percentage,
charging state, active profile, gamerscore, achievement progress, icons, and new
unlocks on the lower display while gameplay stays on the main screen.

## Backups

Open **Settings > App** to create or restore a BluBox backup. Backups include:

- BluBox profiles and profile photos.
- Xbox 360 save content.
- Library data and artwork.
- App and emulator settings.

Game images, Xbox firmware, encryption keys, and Microsoft code are not included.

## Public testing and contributions

Read [PUBLIC_ALPHA_TESTING.md](PUBLIC_ALPHA_TESTING.md) for the test process and
compatibility ratings. Use the GitHub issue forms for bug reports, game reports,
and feature requests. Do not attach games, firmware, encryption keys, or other
copyrighted files.

Development priorities are listed in [ROADMAP.md](ROADMAP.md). Source changes
are welcome under [CONTRIBUTING.md](CONTRIBUTING.md).

## Source and licences

The BluBox frontend is GPL-2.0. The Xenia-derived core is BSD-3-Clause, with
additional third-party licences listed in `licenses/THIRD-PARTY-NOTICES.html`.
See `UPSTREAM_SOURCES.md` and `BUILD.md` for source revisions and build setup.
