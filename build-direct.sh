#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "$0")" && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$sdk_root" ]]; then
  echo "Set ANDROID_SDK_ROOT to an Android SDK containing platform 36 and build-tools 36.0.0." >&2
  exit 1
fi

build_tools_version=${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}
tools_dir="$sdk_root/build-tools/$build_tools_version"
android_jar="$sdk_root/platforms/android-36/android.jar"
if [[ ! -f "$android_jar" ]]; then
  android_jar=$(find "$sdk_root/platforms/android-36" -maxdepth 2 -name android.jar -print -quit)
fi
if [[ ! -f "$android_jar" || ! -x "$tools_dir/aapt2" ]]; then
  echo "Android platform 36 or build-tools $build_tools_version is missing." >&2
  exit 1
fi

mkdir -p "$project_root/build"
work_dir=$(mktemp -d "$project_root/build/direct.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/gen" "$work_dir/classes" "$work_dir/dex" \
  "$work_dir/assets" "$work_dir/package/lib/arm64-v8a"

cp -a "$project_root/emulator-core/src/main/assets/." "$work_dir/assets/"
cp -a "$project_root/app/src/main/assets/." "$work_dir/assets/"
cp -a "$project_root/emulator-core/src/main/jniLibs/arm64-v8a/." \
  "$work_dir/package/lib/arm64-v8a/"

"$tools_dir/aapt2" compile --dir "$project_root/app/src/main/res" \
  -o "$work_dir/app-res.zip"
"$tools_dir/aapt2" compile \
  -o "$work_dir/core-res.zip" \
  "$project_root/emulator-core/src/main/res/drawable/app_icon.jpg"
"$tools_dir/aapt2" link \
  -o "$work_dir/base.apk" \
  --manifest "$project_root/app/src/main/AndroidManifest.xml" \
  -I "$android_jar" \
  --min-sdk-version 29 \
  --target-sdk-version 35 \
  --version-code 21 \
  --version-name 0.12.0-alpha \
  --replace-version \
  --java "$work_dir/gen" \
  --extra-packages xendroid.compose.core \
  -A "$work_dir/assets" \
  "$work_dir/app-res.zip" "$work_dir/core-res.zip"

mapfile -t java_sources < <(find \
  "$project_root/app/src/main/java" \
  "$project_root/emulator-core/src/main/java" \
  "$work_dir/gen" \
  -type f -name '*.java' | sort)
java -m jdk.compiler/com.sun.tools.javac.Main \
  -encoding UTF-8 -source 17 -target 17 \
  -classpath "$android_jar" \
  -d "$work_dir/classes" \
  "${java_sources[@]}"

(cd "$work_dir/classes" && zip -q -r "$work_dir/classes.jar" .)
"$tools_dir/d8" --lib "$android_jar" --min-api 29 --release \
  --output "$work_dir/dex" "$work_dir/classes.jar"

cp "$work_dir/base.apk" "$work_dir/unaligned.apk"
zip -q -j "$work_dir/unaligned.apk" "$work_dir/dex/classes.dex"
(cd "$work_dir/package" && zip -q -r "$work_dir/unaligned.apk" lib)
"$tools_dir/zipalign" -p -f 4 "$work_dir/unaligned.apk" "$work_dir/aligned.apk"

output_dir="$project_root/app/build/outputs/apk/release"
mkdir -p "$output_dir"
if [[ -n "${BLUBOX_KEYSTORE:-}" ]]; then
  : "${BLUBOX_KEYSTORE_PASS:?Set BLUBOX_KEYSTORE_PASS when signing}"
  : "${BLUBOX_KEY_PASS:?Set BLUBOX_KEY_PASS when signing}"
  export BLUBOX_KEYSTORE_PASS BLUBOX_KEY_PASS
  output_apk="$output_dir/BluBox-360-0.12.0-alpha-arm64.apk"
  "$tools_dir/apksigner" sign \
    --ks "$BLUBOX_KEYSTORE" \
    --ks-key-alias "${BLUBOX_KEY_ALIAS:-androiddebugkey}" \
    --ks-pass env:BLUBOX_KEYSTORE_PASS \
    --key-pass env:BLUBOX_KEY_PASS \
    --v1-signing-enabled false \
    --v2-signing-enabled true \
    --v3-signing-enabled false \
    --v4-signing-enabled false \
    --out "$output_apk" "$work_dir/aligned.apk"
  "$tools_dir/apksigner" verify --verbose "$output_apk"
else
  output_apk="$output_dir/BluBox-360-0.12.0-alpha-arm64-unsigned.apk"
  cp "$work_dir/aligned.apk" "$output_apk"
fi

"$tools_dir/zipalign" -c -p 4 "$output_apk"
echo "$output_apk"
