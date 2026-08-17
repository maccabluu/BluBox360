# BluBox 360 0.16.0 public alpha

BluBox 360 0.16.0 focuses on clearer game rendering and better retail-style cover artwork.

## New graphics option

- Added **HD+** graphics mode.
- HD+ uses 2x internal rendering, FSR scaling/sharpening, FXAA Extreme and 16x anisotropic texture filtering.
- HD+ uses stronger sharpening than the normal HD preset to improve fine texture clarity.
- Selecting HD+ turns Low Heat Mode off so the higher-resolution settings are not replaced by the low-heat launch preset.
- Performance, Balanced, HD and Custom modes remain available.
- Fable II keeps its existing safety/recovery settings where required.

HD+ improves the appearance of the original Xbox 360 textures and rendering. It does not bundle replacement texture packs and does not change the original game files.

## Better game covers

- Game options now include **Find retail cover online**.
- The button opens the Xbox 360 section of The Cover Project in the browser.
- Downloaded artwork can then be imported with **Choose cover from device**.
- Full retail wraparound scans are detected automatically and BluBox crops the front panel for the 3D Xbox 360 case.
- Cover imports now keep up to a 2048px edge and save at higher JPEG quality.
- Existing custom-cover reset and library removal options remain available.

The Cover Project is an independent external website. BluBox does not bundle or redistribute its cover scans. Users choose and download artwork themselves.

## Updates and signing

- 0.16.0 uses the same permanent BluBox release certificate as 0.15.0, 0.15.1 and 0.15.2.
- Users on 0.15.2 should be able to install 0.16.0 directly over the existing app without uninstalling or removing BluBox data.
- The built-in updater continues to verify SHA-256 before handing the APK to Android for installation.

## Install

If you already have 0.15.2-alpha, update normally to 0.16.0-alpha. No uninstall should be required.
