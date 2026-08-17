# BluBox 360 0.15.1 public alpha

BluBox 360 0.15.1 is a hotfix for the built-in updater.

## Fixed

- BluBox no longer reports old releases as newer updates.
- The installed version is checked again before any update prompt is shown.
- Update checks now bypass stale GitHub cache responses.
- The update popup appears only when the published release version is newer than the installed BluBox version.
- The update popup displays the version from the release tag instead of trusting stale release-title data.
- SHA-256 release verification remains enabled before installation.

## Signing

0.15.1 uses the same permanent BluBox release certificate introduced with 0.15.0. Android should install 0.15.1 directly over 0.15.0 without uninstalling the app or removing BluBox data.

## Install

If you already have 0.15.0-alpha, install 0.15.1-alpha normally over it. No uninstall should be required.

If the buggy 0.15.0 updater still displays an older release before this hotfix is installed, install 0.15.1 once from the official GitHub release. After 0.15.1 is installed, future update prompts should only appear for versions newer than the version already installed.
