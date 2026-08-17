# BluBox 360 0.16.1 public alpha

BluBox 360 0.16.1 is an updater hotfix for the incorrect old-release popup.

## Fixed updater

- Fixed a legacy GitHub release tag being misread as version 360.0.0.
- Old non-standard tags such as `BluBox360-0.12.0` are now ignored by the in-app updater.
- The updater only accepts the current canonical tag format, for example `v0.16.1-alpha`.
- Version comparison now reads the real `major.minor.patch` number instead of collecting unrelated digits from a tag name.
- Update prompts are checked again before the dialog is shown and before a download starts.
- The update dialog now displays a clean version such as `0.16.1 public alpha`.

When 0.16.1 is installed and 0.16.1 is the newest published release, no update popup should appear.

## Existing 0.16 features

- HD+ graphics mode remains included.
- Retail cover workflow through The Cover Project remains included.
- Permanent BluBox signing and SHA-256 verification remain enabled.

## Install

Because 0.16.0 contains the updater bug, it may keep offering the old 0.12 release even after 0.16.1 is published. Install 0.16.1 manually once from the GitHub release page. It uses the same permanent BluBox certificate, so Android should install it directly over 0.16.0 without removing app data.
