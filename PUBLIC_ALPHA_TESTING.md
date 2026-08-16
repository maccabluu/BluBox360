# BluBox 360 public alpha testing

Thank you for testing BluBox 360. This guide helps the team compare reports
from different games and Android devices.

## Before testing

1. Install BluBox 360 0.11.0-alpha on an ARM64 Android device with Vulkan.
2. Back up important BluBox profiles and saves from Settings > App.
3. Start with Low Heat Mode enabled and the Android system graphics driver.
4. Use a legal game backup stored on internal storage or microSD.
5. Turn on the FPS and frame-time overlay in Settings > Performance.

If the device becomes uncomfortable to hold, shows a temperature warning, or
starts severe throttling, close the game and let the device cool. BluBox closes
emulation when Android reports severe thermal status.

## Compatibility ratings

| Rating | Meaning |
| --- | --- |
| Playable | Main content works with stable controls and no major blocker |
| In-game | Gameplay starts, but major graphics, speed, audio, or crash problems remain |
| Intro | Logos or intro screens work, but gameplay does not start |
| Menu | The title reaches a menu but does not enter gameplay |
| Boots | A game window starts, with no usable menu or gameplay |
| Broken | The title does not boot or closes during preparation |

## Run a useful test

1. Restart BluBox before testing a different graphics or driver setting.
2. Record the exact BluBox version, device, Android version, chipset, and GPU.
3. Record the game title, region, title ID, and file type.
4. Test Performance or Low Heat Mode first.
5. Play for at least ten minutes when the title reaches gameplay.
6. Record average FPS, visible graphics problems, sound problems, controls, and
   the highest temperature shown by the device tools.
7. Repeat the problem once before submitting a report.

## Submit a report

Choose the GitHub Compatibility Report form for one game or the Bug Report form
for an app-wide problem. Include clear steps, a screenshot, and `xe.log` when
available. Remove personal paths or account names from logs before attaching
them.

Never upload game images, extracted game files, firmware, keys, paid mods, or
copyrighted cover scans. A title name, title ID, settings list, screenshot, and
log are enough for most reports.

## Known alpha limits

- Game compatibility is incomplete.
- Fable II uses a conservative preset, but graphical errors and low performance
  remain possible on some driver and device combinations.
- Player 2 through Player 4 controller profiles are stored for later native
  multiplayer work. Player 1 is the active native slot in this alpha.
- Custom Vulkan drivers are device-specific and might cause a black screen or
  crash. Return to the Android system driver after a driver failure.
- The public APK targets ARM64 Android only and requires Vulkan.
