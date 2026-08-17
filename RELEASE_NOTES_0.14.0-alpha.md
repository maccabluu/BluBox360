# BluBox 360 0.14.0 public alpha

BluBox 360 0.14.0 fixes the startup sound and replaces the avatar creator with profile-photo uploads.

## New in this update

- Reworked the startup chime so it plays through Android media audio as a generated WAV file.
- Increased startup-chime volume and reliability.
- The chime now keeps playing if the boot animation is turned off and BluBox opens the library immediately.
- Removed the skin, hair, outfit, expression, and background avatar creator controls.
- Added Choose profile photo so each profile can select an image from Android storage or the photo library.
- Added Remove photo.
- Profile photos appear in the profile cards, main BluBox account badge, side menu, and Xbox 360 profile image generated for the emulation core.
- BluBox stores a resized local copy of the selected image inside the profile folder.
- Existing gamertags, profiles, saves, achievements, 60 FPS target, Thor telemetry, controller settings, and game library features remain in place.

## Install

This is an ARM64 Android public alpha for Android 10 or newer with Vulkan support.

Back up important BluBox profiles and saves before installing an alpha build.

The automated public-alpha build currently uses a temporary signing certificate when no private release key is configured. Android will reject an update installed over an alpha signed with a different certificate. If Android reports an install conflict, back up BluBox app data first, uninstall the older alpha, install 0.14.0-alpha, then restore the backup.

Games, Xbox firmware, encryption keys, and Microsoft code are not included. Use legal game backups you own.
