# Upstream source record

This BluBox 360 public alpha was built from these pinned open-source revisions:

- XenDroid: `https://github.com/rfandango/XenDroid`
  - commit `0b1120187340be3644e28ceb7a04be04cd0f2e08`
- Xenia source carried by that XenDroid revision
  - commit `0b1120187340be3644e28ceb7a04be04cd0f2e08`
- Xenia Canary game patches: `https://github.com/xenia-canary/game-patches`
  - commit `3553a5aea5ad64c5ec0169d8b8d5792e2ab45e44`

`patches/xendroid-core-android-r27.patch` contains the build and Android NDK r27
compatibility changes used for the binary. The complete BluBox frontend is in
`app/`.

The BluBox changes do not include proprietary X360 Mobile code or Microsoft
code. The upstream Android core and Xenia are used under their published
open-source licences.
