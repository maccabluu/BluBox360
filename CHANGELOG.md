# Changelog

## 0.12.0 public alpha

- Added a selectable 60 FPS target for compatible Xbox 360 games
- Kept a 30 FPS option and the safer 24 FPS Fable II recovery preset
- Added live FPS to the AYN Thor lower-screen achievement dashboard
- Added live device temperature with Android battery-sensor fallback
- Added battery percentage and charging state to the lower display
- Added colour changes for low frame rate, elevated heat, and low battery
- Moved thermal sensor file reads off the Android user-interface thread

## 0.11.0 public alpha

- Prepared the first BluBox 360 release for public testing
- Added public compatibility, bug, and feature-request forms
- Added testing, contribution, security, privacy, conduct, and roadmap documents
- Added a GitHub-ready project description and release announcement
- Added a fresh v0.11 launcher component and controller-B icon resource
- Kept the full v0.10 controller profile, macro, haptics, trigger, and stick tuning set
- Kept the conservative Fable II recovery preset and Android severe-heat guard

## 0.10.0 alpha

- Added four saved Xbox 360 controller profiles to the Controls tab
- Added four bindable macros with editable multi-button Xbox 360 combinations
- Added controller haptics, vibration strength, and a rumble test
- Added analogue trigger pressure and trigger deadzone tuning
- Added left and right stick modes for analogue, face buttons, D-pad, and custom outputs
- Added axis swap, separate X/Y inversion, D-pad-to-left-stick, and sprint assist
- Added separate left and right response curves, inner and outer deadzones,
  anti-deadzone, sensitivity, and acceleration controls
- Connected every Player 1 setting to the live Android controller input path
- Registered a fresh v0.10 launcher component and controller-B icon resources

## 0.9.0 alpha

- Reworked controller input with radial deadzones, response curves, motion-history
  processing, analogue triggers, and support for Z/RZ and RX/RY right-stick layouts
- Added an automatic Harry Potter precision-aiming curve for steadier spell attacks
- Added a Mods tab for importing, enabling, disabling, and removing Xenia
  `.patch.toml` files
- Added full portrait cover-image import from each game's long-press options
- Rebuilt automatic cases as green retail-style Xbox 360 boxes with full-height artwork
- Added cover artwork and patch mods to BluBox app-data backups
- Registered a new launcher component and v0.9 icon resources to refresh the
  controller-B Android icon

## 0.8.0 alpha

- Added Low Heat Mode for every game with native rendering, a 30 FPS ceiling,
  two background shader workers, and no maximum-clock request
- Kept Fable II on its safer 24 FPS recovery preset with memory patches disabled
- Added ClusterTune installation detection, setup guidance, and a direct open button
  for automatic BluBox 360 app profiles on supported AYN handhelds
- Expanded the Add Games menu with single-file import and a persistent game-folder picker
- Added recursive ISO, ZAR, and `default.xex` folder scanning
- Added a Refresh Library button which finds new games placed in the selected folder
- Registered a fresh launcher component so Android refreshes the controller-B app icon

## 0.7.3 alpha

- Added automatic recovery for a Xenia configuration damaged by an interrupted write
- Removed the duplicate frontend graphics-configuration write before every launch
- Made failed metadata extraction non-blocking so a readable game still starts
- Replaced null preparation errors with the exception type or a useful message
- Kept the 0.7.2 low-load Fable preset and severe-heat guard

## 0.7.2 alpha

- Removed the automatic Fable II shader-cache deletion introduced in 0.7.1
- Reduced Fable II pipeline compilation from four blocking workers to two background workers
- Disabled pipeline preloading, incomplete-draw waits, and BluBox memory patches for Fable II
- Reduced the Fable II frame ceiling to 24 FPS for lower sustained GPU load
- Added an Android thermal guard that closes emulation at severe thermal status
- Registered a fresh launcher component to refresh the controller-B app icon

## 0.7.1 alpha

- Replaced Fable II's Disable MSAA preset after blue and black texture corruption
- Enabled the credited Disable Texture Morphing workaround for broken hero and dog textures
- Added a one-time Fable II shader and Vulkan pipeline cache repair
- Stopped incomplete shader draws and restored the geometry-shader path for Fable II
- Registered a new launcher component and icon resource so Android refreshes the app icon

## 0.7.0 alpha

- Added an automatic Fable II handheld preset for title ID `4D5307F1`
- Added the credited Disable MSAA patch to reduce Fable II GPU load
- Kept Fable II at native internal scale with bilinear output, UMA readback,
  the performance render-target path, cached shaders, and a 30 FPS ceiling
- Kept the community 60 FPS and High Tick Rate patches disabled for stability
- Replaced profile busts with original full-body cartoon characters, outfits,
  shoes, poses, six hair styles, and bright character-card backgrounds
- Replaced the Android launcher artwork with the new blue controller-B icon

## 0.6.1 alpha

- Remade the 0.6 Renderer update as a fresh installable package
- Kept every photo-based Xbox 360 Renderer control from 0.6.0
- Kept the same app identity and signing certificate for data-safe updates

## 0.6.0 alpha

- Rebuilt the Renderer tab around Xbox 360 settings supported by the Xenia core
- Added native through 7× internal resolution choices with safe whole-number limits
- Added 16:9, 4:3, stretch, progressive, and interlaced compatibility controls
- Added Off, FXAA, and FXAA Extreme anti-aliasing plus None, CAS, and FSR output filters
- Added game-default through forced 16× anisotropic texture filtering
- Added asynchronous shader compilation, worker count, pipeline preloading, and smooth pop-in controls
- Added unified-memory, fast, accurate, and disabled GPU readback modes
- Added safe per-game shader and Vulkan pipeline cache clearing without touching saves
- Added AdrenoTools-compatible Turnip driver import, selection, and deletion on Adreno devices
- Kept Vulkan as the only graphics API because the Xbox 360 core has no OpenGL or software renderer

## 0.5.2 alpha

- Removed the falling-logo crash, floor cracks, debris, lightning, and vibration
- Restored the earlier clean BluBox logo fade, scale, and title intro
- Kept the boot-animation setting in Settings → App

## 0.5.1 alpha

- Rebuilt the optional boot animation around the BluBox logo
- Added an accelerating fall from above the screen
- Added a compressed floor impact, bounce, shockwave, cracks, and blue debris
- Added three timed white and blue lightning strikes around the logo
- Added a short impact vibration and delayed BluBox title reveal

## 0.5.0 alpha

- Added automatic AYN Thor secondary-display detection during gameplay
- Added a controller-friendly lower-screen achievement dashboard
- Added local Xbox 360 GPD achievement parsing with names, descriptions, icons,
  gamerscore, unlock status, and timestamps
- Added live unlock notifications and per-game progress on the lower display
- Added a full achievement-history page for the active BluBox profile
- Added Achievements settings for lower-screen use and locked-item visibility
- Kept achievement progress separate for every local BluBox profile

## 0.4.3 alpha

- Added an optional animated BluBox logo intro at app startup
- Added App-tab backup to a portable `.zip` containing profiles, saves, artwork,
  library data, and settings while excluding games and firmware
- Added validated restore with archive path and size safety checks
- Added a settings-only reset that keeps games, profiles, and save progress
- Standardized adaptive, round, and legacy launcher icons on the BluBox logo

## 0.4.2 alpha

- Added a Library display button with 3D Shelf, 3D Grid, and Compact 3D List
- Added automatic blue Xbox 360 case rendering for every imported game
- Added a category-based Settings hub for App, Performance, Renderer, Audio,
  Controls, Hotkeys, and Storage
- Saved the selected Library display mode between launches

## 0.4.1 alpha

- Replaced the overlapping top navigation buttons with a responsive header
- Added a hamburger options drawer inspired by a console library interface
- Added fast game-title and title-ID search
- Added direct menu entries for storage, profiles, saves, controls, HD graphics,
  compatibility patches, and diagnostics
- Added compact header controls for narrower landscape screens

## 0.4.0 alpha

- Added a real Android ARM64 Xbox 360 core based on XenDroid and Xenia
- Added ISO, XEX, and ZAR launch from internal storage and microSD
- Added physical controller mapping for AYN-style handhelds
- Added blue BluBox game library, cover metadata, and diagnostics
- Added eight local profiles, custom avatars, and active-profile game saves
- Added Performance, Balanced, and HD graphics presets with FSR
- Added bundled game patches, FPS display, pause menu, and guest dialogs
