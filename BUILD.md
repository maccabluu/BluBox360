# Building BluBox 360

## Requirements

- Linux or WSL2
- JDK 17 or newer
- Android SDK platform 36 and build-tools 36
- `zip` and the Android SDK command-line tools

## Direct reproducible app build

The release source package includes the audited ARM64 JNI libraries used by
the APK, so the frontend, resources, Java bridge, and package can be rebuilt
without downloading Gradle or recompiling the large native Xenia tree:

```bash
ANDROID_SDK_ROOT=/path/to/android-sdk ./build-direct.sh
```

This writes an aligned unsigned APK under `app/build/outputs/apk/release/`.
To sign it, set `BLUBOX_KEYSTORE`, `BLUBOX_KEYSTORE_PASS`, `BLUBOX_KEY_PASS`,
and optionally `BLUBOX_KEY_ALIAS` before running the same command.

## Full native core rebuild

Recompiling the JNI libraries additionally requires Android NDK
`27.2.12479018`, CMake 3.30.3, Git, Python 3, and the host Vulkan shader tools
`glslangValidator`, `spirv-opt`, and `spirv-dis`.

## Prepare the pinned core

Run:

```bash
./prepare-upstream.sh
```

This downloads the pinned XenDroid source and its submodules, applies the exact
NDK compatibility patch, and stages the pinned game-patch bundle. The download
is large because Xenia has many native third-party dependencies.

Copy `local.properties.example` to `local.properties` and set your Android SDK
path. Make sure the shader tools above are on `PATH` (or set `VULKAN_SDK`),
then build:

```bash
./gradlew :app:assembleRelease \
  -x :emulator-core:extractReleaseAnnotations \
  -x :emulator-core:lintVitalAnalyzeRelease \
  -x :app:lintVitalAnalyzeRelease \
  -x :app:lintVitalReportRelease \
  -x :app:lintVitalRelease
```

The downloadable public alpha uses its existing private alpha certificate so
an earlier BluBox 360 alpha installation updates without replacing app data.
The certificate and passwords are not part of the source repository. Use a
separate private production keystore before publishing through an app store.

The APK is written under `app/build/outputs/apk/release/`.
