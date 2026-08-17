# BluPS2

BluPS2 is an experimental PlayStation 2 emulator frontend for ARM64 Android handhelds, developed beside BluBox 360.

## First target

- Android ARM64
- AYN Thor class Snapdragon handhelds
- Landscape handheld UI
- Local game library
- ISO and supported PS2 disc image importing
- Profiles and separate save data
- Controller support
- FPS and thermal diagnostics
- Smart Heat Guard
- Per-game settings
- Cover art library
- App update checker

## Emulator core direction

BluPS2 will integrate an established open-source PS2 emulation core rather than pretending to implement PS2 emulation from scratch. The current preferred research target is Play!, which has an Android build and an open-source PS2 emulation codebase.

The frontend and BluPS2-specific code will remain separate from third-party emulator code and must preserve upstream licence notices.

## Status

0.1 Preview groundwork. The UI shell and integration layer are being prepared first. PS2 gameplay is not claimed until a real core is compiled, integrated and tested on hardware.
