# BluBox 360 0.15.2 public alpha

BluBox 360 0.15.2 fixes the version shown inside Settings and strengthens the updater version check.

## Fixed

- The Settings > App card now shows the version actually being built instead of the old hard-coded `0.12.0` label.
- Release builds now inject their exact version into the Settings screen automatically.
- The updater build-version floor is updated automatically for each release build.
- The update popup continues to appear only when the published GitHub release version is newer than the installed BluBox version.
- Old releases are ignored.
- SHA-256 verification remains enabled before installation.

## Signing

0.15.2 uses the same permanent BluBox release certificate as 0.15.0 and 0.15.1. Android should install 0.15.2 directly over 0.15.1 without uninstalling BluBox or removing app data.

## Install

If you already have 0.15.1-alpha, install 0.15.2-alpha normally over it. After installation, Settings > App should show `BluBox 360 0.15.2 public alpha`.

When 0.15.2 is the newest published version, the automatic update popup should stay hidden. It should appear again only after a newer release is published.
