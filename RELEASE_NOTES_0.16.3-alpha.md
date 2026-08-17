# BluBox 360 0.16.3 public alpha

BluBox 360 0.16.3 focuses on easier updating and safer sustained performance on Android handhelds such as the AYN Thor.

## Check for updates

- Added a **Check for updates** button under **Settings > App**.
- Automatic update checks keep the existing 15-minute cooldown.
- A manual check runs immediately and ignores the automatic cooldown.
- Manual checks now confirm when BluBox is already up to date.
- Manual checks show an error message if GitHub cannot be reached instead of silently closing.

## Smart Heat Guard

- Low Heat Mode remains available and still avoids maximum-clock requests.
- BluBox now checks Android thermal status and thermal headroom when a game launches.
- When Android reports thermal pressure, BluBox uses the low-heat launch preset even if Low Heat Mode was switched off.
- A 60 FPS target can be reduced to a 30 FPS safety ceiling when the device is already approaching thermal throttling.
- Shader creation is reduced to one worker under thermal pressure to lower sustained CPU load.
- Fable II keeps its existing 24 FPS safety ceiling.
- The existing severe-heat protection remains enabled during gameplay.

Smart Heat Guard is designed to reduce heat and throttling. Xbox 360 emulation is still a demanding workload, so no software setting can guarantee that a handheld stays completely cool in every game.

## Xbox services note

BluBox continues to use its local Xbox 360 profile and achievement tracking in this release. Real Xbox network sign-in and official achievement syncing are not enabled in 0.16.3 because Microsoft Xbox services require an approved Partner Center title configuration and service identifiers before a title can authenticate users or update Xbox achievements.

## Existing features

- Flat-cover library layout from 0.16.2.
- HD+ rendering mode.
- Retail cover import workflow.
- Profiles, local achievements and save-game progress.
- AYN Thor lower-screen FPS, temperature, battery and achievement display.
- Permanent BluBox signing and SHA-256 verification.

Version: 0.16.3-alpha
Platform: ARM64 Android
