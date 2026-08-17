# BluBox 360 0.15.0 public alpha

BluBox 360 0.15.0 introduces built-in update checking and installation for future releases.

## New in this update

- BluBox checks the official GitHub Releases feed after launch.
- When a newer BluBox alpha is available, the app shows an update prompt.
- Update prompts include **Update now**, **What's new**, and **Later** choices.
- **Update now** downloads the ARM64 APK inside BluBox and shows download progress.
- BluBox verifies the downloaded APK against the release SHA-256 file when one is supplied.
- Android's Package Installer handles the final installation confirmation.
- BluBox asks for Android's install-unknown-apps permission only when an update needs to be installed.
- Update checks are rate-limited so repeatedly opening BluBox does not hammer the GitHub API.

## Important signing change

0.15.0 is intended to be the first BluBox build signed with the permanent BluBox release certificate. After that certificate is established, later releases must use the same key so Android can install them as normal updates without uninstalling BluBox.

The release workflow must fail if the permanent signing key is unavailable. It must never generate another temporary signing certificate.

## Install

The move from the temporary 0.14.0 signing certificate to the permanent BluBox certificate requires one final uninstall of the old alpha. Back up BluBox app data first, uninstall 0.14.0-alpha, install 0.15.0-alpha, and restore the backup if needed.

From 0.15.0 onward, future properly signed releases are designed to update over the installed app and preserve BluBox data.
