# BluBox 360

BluBox 360 is a public alpha Xbox 360 emulator for ARM64 Android handhelds,
created by Macca and the BluBox team. The project focuses on modern Snapdragon
and Adreno devices, with the AYN Thor Pro as the main development target.

This package installs beside the original-Xbox BluBox app, so the original app
and its saves are not replaced.

> Public alpha: expect incomplete compatibility, graphical errors, crashes,
> and higher device temperatures in demanding games. Keep backups of important
> saves and read [PUBLIC_ALPHA_TESTING.md](PUBLIC_ALPHA_TESTING.md) before testing.

## Download the public alpha

[Download BluBox 360 0.15.0-alpha for ARM64 Android](https://github.com/maccabluu/BluBox360/releases/download/v0.15.0-alpha/BluBox-360-0.15.0-alpha-arm64.apk)

Android 10 or newer, ARM64, and Vulkan are required. Back up profiles and saves
before installing an alpha build. BluBox 360 0.15.0 is the first release signed
with the permanent BluBox release certificate. Moving from 0.14.0 requires one
final uninstall because 0.14.0 used a temporary certificate. From 0.15.0 onward,
future releases signed with the same permanent certificate are designed to
install as normal updates without removing BluBox data.

APK SHA-256:
`9e68ef1ef2244429064550ac75e7329b8768ae903feb709de29e407c50a7f6bf`

## Included in 0.15.0 public alpha

- Built-in update checking against the official BluBox GitHub Releases feed
- Update prompts with **Update now**, **What's new**, and **Later** choices
- In-app APK download with progress and SHA-256 verification before installation
- Android Package Installer handoff for the final update confirmation
- Permanent BluBox release signing certificate for future in-place updates
- Fixed and strengthened the BluBox startup chime
- Startup sound continues when the boot animation is disabled
- Profile-photo upload replaces the old skin, hair, outfit, expression, and background avatar creator
- Profile photos appear in profile cards, the account badge, the side menu, and local Xbox 360 profile artwork
- First public tester package, with compatibility and bug-report forms
- Public testing, contribution, privacy, security, and roadmap documentation
- Fresh Android launcher component and icon resource for the controller-B logo
- Selectable 60 FPS target for compatible games, with a 30 FPS option
- Live FPS, device temperature, battery percentage, and charging state on the
  AYN Thor lower-screen achievement dashboard
- Real Xenia-derived Xbox 360 emulation core with ARM64 JIT and Vulkan output
- ISO, `default.xex`, and ZAR launch support from internal storage or microSD
- Single-game import plus recursive game-folder scanning and one-button library refresh
- AYN/Android physical-controller mapping, including sticks and triggers
- Smooth radial stick response, analogue triggers, and both common Android
  right-stick axis layouts
- Four saved Xbox 360 controller profiles with separate left and right stick tuning
- Four bindable controller macros with multi-button Xbox 360 output combinations
- Stick mode, axis swap, X/Y inversion, D-pad-to-left-stick, and sprint-assist controls
- Linear, Light, Medium, and Strong response curves plus inner deadzone,
  outer deadzone, anti-deadzone, sensitivity, and acceleration sliders
- Trigger pressure and deadzone controls, button haptics, vibration strength,
  and a controller rumble test
- Automatic Harry Potter precision aiming for steadier spell attacks
- Automatic game saves through local Xbox 360 profiles
- Up to eight BluBox accounts with separate profile photos and profile data
- Local Xbox 360 achievement tracking from each profile's GPD records
- AYN Thor dual-screen mode with live progress and unlocks on the lower display
- Achievement history with per-game progress, gamerscore, icons, and dates
- Blue controller-first library UI with cover and title metadata extraction
- Responsive Library header, game search, and slide-out options menu
- Three persistent game views: 3D Shelf, 3D Grid, and Compact 3D List
- Full green retail-style Xbox 360 cases plus optional portrait cover-image import
- Mods tab for importing, enabling, disabling, and removing Xenia patch mods
- Category-based emulator settings for app, performance, renderer, audio,
  controls, hotkeys, achievements, and storage
- Optional animated BluBox logo intro and controller-B Android launcher icon
- Portable app-data backup and restore for profiles, saves, artwork, and settings
- Safe settings reset that keeps the game library, profiles, and save progress
- Performance, Balanced, and HD graphics modes
- Low Heat Mode for every game with native rendering, two shader workers,
  and maximum-clock requests disabled
- ClusterTune setup and status for automatic per-app CPU and GPU frequency profiles
- FSR sharpening, 2x internal rendering in HD mode, FPS diagnostics, XMA audio
- Full Xbox 360 Renderer tab with native through 7× internal resolution,
  display ratio, FXAA, CAS/FSR, anisotropic filtering, shader workers,
  pipeline preloading, readback accuracy, and safe shader-cache clearing
- Rootless AdrenoTools-compatible Turnip driver import, selection, and deletion
  on supported Adreno devices, with the Android system driver as the default
- Automatic Fable II recovery preset with native rendering, UMA readback,
  a 24 FPS ceiling, two background shader workers, and no memory patches
- Severe-heat guard using Android thermal status during emulation
- Automatic repair for an unreadable Xenia configuration after an interrupted write
- Bundled game patches and guest keyboard/message-box handling

## Install and use

1. Install `BluBox-360-0.15.0-alpha-arm64.apk` on an ARM64 Android device.
2. Allow installation from your browser or file manager if Android asks.
3. Open BluBox 360 and grant All files access. Large disc images remain on your
   microSD card and are not copied into the app.
4. Press **+**, then choose one game file or a folder containing legal backups
   you own.
5. After placing another ISO, ZAR, or extracted `default.xex` in the selected
   folder, press **Refresh Library**.
6. Tap a game or focus it with the controller and press A.

## Built-in updates

BluBox checks the official GitHub Releases feed after launch. When a newer alpha
is available, choose **Update now** to download it inside BluBox, **What's new**
to read the release notes, or **Later** to continue using the current version.
BluBox verifies the downloaded APK against the release SHA-256 file when one is
supplied, then Android's Package Installer handles the final installation step.

Version 0.15.0 establishes the permanent BluBox signing certificate. Future
releases must use the same certificate so Android accepts them as updates and
preserves installed BluBox data.

## Profile photos

Open **Profiles**, choose a profile, then select **Choose profile photo**. BluBox
opens Android's image picker, crops a local square copy for the profile, and
keeps the original image untouched. **Remove photo** returns the profile to its
initial-based fallback image.

The selected photo is also used when BluBox prepares the local Xbox 360 profile
artwork for the emulation core.

## Covers and mods

Hold a game tile, or focus it and press Y, to choose portrait cover artwork.
BluBox copies and scales the selected JPG, PNG, or WEBP into app storage. Reset
automatic cover returns to the title icon without touching the game file.

Open **Settings → Mods** to import a valid Xenia `.patch.toml` file. BluBox
normalizes the filename to the game's title ID, then offers file-level enable,
disable, and removal controls. Imported patch files and custom covers are
included in BluBox app-data backups.

## Advanced Xbox 360 controls

Open **Settings → Controls** for Xbox 360 controller profiles, macros, trigger
pressure, stick mapping, axis inversion, deadzones, sensitivity, acceleration,
and vibration testing. Changes start with the next game launch.

Player 1 is the active native controller slot in this alpha. Player 2 through
Player 4 settings are saved as profiles for a later native multiplayer update.
Android button feedback uses the connected controller's vibration motor when
available and falls back to the AYN Thor motor.

## ClusterTune and lower heat

Low Heat Mode starts enabled in **Settings → Performance**. It uses native
rendering, reduces parallel shader work, and leaves maximum-clock forcing
disabled. Choose a 30 or 60 FPS ceiling in **Settings → Performance → Frame
Rate**. Fable II keeps its safer 24 FPS recovery limit.

For hardware frequency limits, install ClusterTune, open it once, make a lower
CPU and GPU profile, and assign BluBox 360 under its app profiles. ClusterTune
then applies its profile while BluBox is the foreground app. BluBox detects the
installed ClusterTune version and opens it from **Settings → Performance**.

The Android document picker must expose a real internal-storage or microSD path.
Cloud-only providers are intentionally rejected because the emulator needs
random access to multi-gigabyte game images.

## Back up or restore app data

Open **Settings → App** to turn the BluBox boot animation on or off, create a
portable backup, restore one, or reset only the app settings. A backup includes
the library database and artwork, BluBox profiles and profile photos, Xbox 360
save content, and app/emulator preferences. Game images and Xbox firmware are
intentionally left out, so keep those legal files separately on internal
storage or microSD.

## AYN Thor lower-screen achievements

Open **Settings → Achievements** to enable or disable the lower-screen panel and
choose whether locked achievements are shown. During a game, BluBox uses
Android's secondary-display support to keep gameplay on the main screen while
the lower screen shows actual FPS, device temperature, battery percentage,
charging state, the active profile, gamerscore, current-game progress,
achievement icons, descriptions, and new unlocks. The **Achievements** drawer
entry opens the full local history.

Achievement information comes from the Xbox 360 GPD data produced by the local
emulator profile. Xbox Live is not contacted, and progress is separate for each
BluBox profile.

## Important compatibility note

This is an alpha emulator, not a promise that every Xbox 360 game will run or
run at full speed. Compatibility and frame rate vary by title. Start with
Performance mode, then try Balanced or HD. No games, Xbox firmware, encryption
keys, or Microsoft code are included.

Fable II title ID `4D5307F1` receives a conservative automatic preset at launch.
Version 0.15.0 keeps the existing shader cache, compiles missing pipelines with
two background workers, skips unfinished draws, and limits output to 24 FPS.
BluBox memory patches and the title-specific Fable II 60 FPS and High Tick Rate
patches remain off.

The general 60 FPS setting is a ceiling, not a speed-up patch. Games with an
internal 30 FPS lock stay at their original rate. The lower-screen FPS tile
shows the rate produced by the emulator core.

HD mode raises the internal render resolution and applies FSR sharpening. It
does not install community replacement-texture packs.

The Renderer tab only exposes settings implemented by the Xbox 360 core.
Vulkan is required. PS2-specific options such as deinterlacing and fractional
internal scales are intentionally not shown. Native or 2× is the practical
starting point on an AYN Thor Pro. Higher scales use sharply more memory and
power.

Custom Vulkan driver packages run native GPU code. Import only trusted
AdrenoTools-compatible packages made for the device GPU. If a custom driver
causes a black screen or crash, select the Android system driver again and
clear the shader cache.

The APK was compiled and structurally verified, but it was not tested on a
physical AYN Thor Pro in the build environment. Device testing and per-game
reports are the next step.

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
See `UPSTREAM_SOURCES.md` and `BUILD.md` for the pinned source revisions and
reproducible setup.
