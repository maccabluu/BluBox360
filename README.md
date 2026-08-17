# BluBox 360

BluBox 360 is a public alpha Xbox 360 emulator for ARM64 Android handhelds,
created by Macca and the BluBox team. The project focuses on modern Snapdragon
and Adreno devices, with the AYN Thor Pro as the main development target.

> Public alpha: compatibility varies by game. Expect graphical issues, crashes,
> and higher device temperatures in demanding titles. Keep backups of important
> saves and read [PUBLIC_ALPHA_TESTING.md](PUBLIC_ALPHA_TESTING.md) before testing.

## Latest release

### BluBox 360 0.16.3 public alpha

[Download BluBox 360 0.16.3-alpha for ARM64 Android](https://github.com/maccabluu/BluBox360/releases/download/v0.16.3-alpha/BluBox-360-0.16.3-alpha-arm64.apk)

Android 10 or newer, ARM64, and Vulkan are required.

APK SHA-256:
`b8b3b6731b8c53e44522fd00579d6cec0191efa73d109ef8d55e3e2ed5ef566d`

[View the v0.16.3-alpha release](https://github.com/maccabluu/BluBox360/releases/tag/v0.16.3-alpha)

## What changed in 0.16.3

- Added **Settings > App > Check for updates**.
- Automatic update checks keep the existing 15-minute cooldown.
- Manual update checks run immediately and report when BluBox is already current.
- Added Smart Heat Guard using Android thermal status and thermal headroom.
- When Android reports thermal pressure at game launch, BluBox can apply the low-heat preset automatically.
- A 60 FPS target can step down to a 30 FPS safety ceiling when the device is already approaching thermal throttling.
- Shader creation is reduced to one worker under thermal pressure.
- The existing severe-heat shutdown protection remains enabled during gameplay.
- Fable II keeps its 24 FPS safety ceiling.
- Uses the permanent BluBox signing certificate for normal in-place updates.

## Built-in updates

BluBox checks the official GitHub Releases feed after launch. Automatic checks
use a 15-minute cooldown. **Settings > App > Check for updates** runs a manual
check immediately.

When a genuinely newer canonical alpha is available, the app offers:

- **Update now** to download the APK inside BluBox.
- **What's new** to read the release notes.
- **Later** to continue using the current version.

BluBox verifies the downloaded APK against the release SHA-256 file when one is
supplied. Android's Package Installer then handles the final installation
confirmation.

Version 0.15.0 established the permanent BluBox release certificate. Versions
0.15.1, 0.15.2, 0.16.0, 0.16.1, 0.16.2, and 0.16.3 use the same certificate.
Future releases must keep using this certificate so Android accepts them as
normal updates and keeps installed BluBox data.

## Install or update

1. Download `BluBox-360-0.16.3-alpha-arm64.apk`.
2. If you already have 0.15.0 or newer permanent-signed BluBox installed, install 0.16.3 directly over it.
3. Android should treat it as an update because the permanent signing certificate matches.
4. Open BluBox and confirm **Settings > App** shows `BluBox 360 0.16.3 public alpha`.
5. Keep your profiles and saves backed up while testing alpha builds.

Moving directly from 0.14.0 still requires the one final uninstall because
0.14.0 used a temporary signing certificate.

## Smart Heat Guard

Low Heat Mode keeps native rendering, reduced shader work, and maximum-clock
requests disabled. In 0.16.3, Smart Heat Guard also checks Android thermal status
and thermal headroom when a game launches. If Android reports that the device is
already approaching thermal throttling, BluBox can apply the cooler preset,
reduce a 60 FPS target to 30 FPS, and use one shader worker.

The existing in-game thermal guard continues to close the game if Android
reports severe heat. Smart Heat Guard reduces sustained load, but Xbox 360
emulation is demanding and no software setting can guarantee a handheld stays
completely cool in every game.

## Xbox services

BluBox currently uses local Xbox 360 profiles, local achievement tracking, and
local gamerscore. Real Xbox network sign-in and official Xbox achievement syncing
are not enabled in 0.16.3. Microsoft Xbox services require an approved Partner
Center title configuration and service identifiers before a title can authenticate
users or update Xbox achievements.

## HD+ graphics

Open **Settings > Performance** and choose **HD+** for the enhanced graphics
preset. HD+ uses 2x internal rendering, FSR sharpening, FXAA Extreme, and 16x
anisotropic filtering to make surfaces, signs, clothing, roads, scenery, and
other original game textures look cleaner where the game and GPU allow it.

HD+ uses more GPU power than Performance or Balanced mode and automatically
turns Low Heat Mode off. If a game runs too hot or loses too much performance,
switch back to Balanced, HD, or Performance.

Fable II keeps its existing safety and recovery settings where required.

## Flat game covers

Hold a game tile, or focus it and press Y, then choose **Find retail cover online**.
BluBox opens the Xbox 360 section of The Cover Project in your browser. Find the
cover for your game and download it to the device, then return to BluBox and use
**Choose cover from device**.

BluBox accepts portrait cover artwork and full retail wraparound scans. Wide
wraparound scans are automatically cropped to the front panel before being shown
as flat front-cover artwork in the library. Use **Reset automatic cover** to
return to the artwork found inside the game file.

## Main features

- ARM64 Android Xbox 360 emulation based on a Xenia-derived core.
- PowerPC JIT and Vulkan rendering.
- ISO, `default.xex`, and ZAR launch support from internal storage or microSD.
- Single-game import, game-folder scanning, and library refresh.
- Three game library views with consistent flat front-cover artwork.
- Retail-style and custom game cover import with wraparound-scan front cropping.
- Performance, Balanced, HD, HD+, and custom renderer modes.
- Native through 7x internal resolution options.
- FXAA, CAS/FSR, anisotropic filtering, shader-worker controls, and pipeline preloading.
- Rootless AdrenoTools-compatible Turnip driver import on supported Adreno devices.
- Selectable 30 FPS or 60 FPS target for compatible games.
- Smart Heat Guard, Low Heat Mode, and Android thermal protection.
- Fable II safety preset with a 24 FPS ceiling.
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
- Automatic and manual in-app update checks.

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
