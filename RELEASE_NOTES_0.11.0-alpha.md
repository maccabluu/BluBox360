# BluBox 360 0.11.0 public alpha

This is the first BluBox 360 package prepared for public testing by Macca and
the BluBox team.

## Highlights

- ARM64 Xenia-derived Xbox 360 core with Vulkan rendering
- Single-game import, recursive folder scanning, and library refresh
- Controller profiles, macros, rumble, trigger tuning, and detailed stick tuning
- Local profiles, saves, achievements, mods, custom covers, and backups
- AYN Thor lower-screen achievements, Low Heat Mode, and ClusterTune guidance
- Conservative Fable II launch preset and Android severe-heat guard
- Public bug, compatibility, and feature-request forms

## Install

Download `BluBox-360-0.11.0-alpha-arm64.apk`. Android 10 or newer, ARM64, and
Vulkan are required. The package uses the same app identity and alpha signing
certificate as earlier BluBox 360 builds, so an in-place update keeps app data.

Back up profiles and saves before updating. Games, firmware, and keys are not
included.

## Known limits

This alpha does not promise full game compatibility or full speed. Some titles
fail to boot, render incorrectly, run slowly, or increase device temperature.
Fable II problems are still under active testing. Player 1 is the active native
controller slot.

Read [PUBLIC_ALPHA_TESTING.md](PUBLIC_ALPHA_TESTING.md) before filing a report.

## Package verification

APK SHA-256:
`f80df8c2d7478efcd79c8e4131900652be837cfa43893e45cd17c7e11567f74e`

Signing certificate SHA-256:
`e52e0c52d9157a2998ba68f296f5ea8bcd8cb3405cb4983ed495793a03d7fc39`
