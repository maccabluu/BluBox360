# BluBox 360 0.12.0 public alpha

BluBox 360 0.12.0 adds a 60 FPS target for compatible games and a live AYN
Thor lower-screen performance dashboard.

## New in this update

- 60 FPS is the default emulator ceiling for compatible games.
- A 30 FPS option is available under Settings, Performance, Frame Rate.
- The lower screen shows actual FPS above Achievements during gameplay.
- The lower screen shows device temperature from an exposed CPU, SoC, GPU,
  or skin sensor. Android battery temperature is used as a fallback.
- The lower screen shows battery percentage and charging state.
- Telemetry colours highlight low frame rate, elevated heat, and low battery.
- Android's severe-heat guard still closes emulation before sustained critical heat.

## Compatibility

A 60 FPS ceiling does not remove a game's own internal frame-rate lock. Games
made for 30 FPS stay at their original rate unless a compatible game patch is
installed. Performance also depends on the game, emulator core, Vulkan driver,
and device cooling.

Fable II keeps the safer 24 FPS recovery preset because earlier high-load tests
caused graphical errors, freezes, and excess heat on the AYN Thor.

## Install

Install the ARM64 APK over 0.11.0-alpha. The package name and signing certificate
are unchanged, so Android keeps BluBox profiles, saves, artwork, and settings.

Back up important saves before testing an alpha update.

APK SHA-256:
`e2a819148b7c07e92321cf07d6666f857fd85b923a787e7e34a04cda85b72d68`
