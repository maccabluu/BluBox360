#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "$0")" && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$sdk_root" ]]; then
  echo "Set ANDROID_SDK_ROOT to an Android SDK containing platform 36 and build-tools 36.0.0." >&2
  exit 1
fi

version_code=27
version_name=0.16.0-alpha
version_label=${version_name%-alpha}

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
  "$work_dir/assets" "$work_dir/package/lib/arm64-v8a" "$work_dir/app-java"

cp -a "$project_root/emulator-core/src/main/assets/." "$work_dir/assets/"
cp -a "$project_root/app/src/main/assets/." "$work_dir/assets/"
cp -a "$project_root/emulator-core/src/main/jniLibs/arm64-v8a/." \
  "$work_dir/package/lib/arm64-v8a/"
cp -a "$project_root/app/src/main/java/." "$work_dir/app-java/"

# Release builds compile from a temporary source copy. Replace stale UI/build-floor
# version text there so the APK always reports the exact package version being built.
main_activity="$work_dir/app-java/uk/co/blustudio/blubox360/MainActivity.java"
update_activity="$work_dir/app-java/uk/co/blustudio/blubox360/UpdateActivity.java"
if [[ -f "$main_activity" ]]; then
  sed -E -i "s/BluBox 360 [0-9]+\.[0-9]+\.[0-9]+ public alpha/BluBox 360 ${version_label} public alpha/g" \
    "$main_activity"
fi
if [[ -f "$update_activity" ]]; then
  sed -E -i "s/private static final String BUILD_VERSION_FLOOR = \"[^\"]+\";/private static final String BUILD_VERSION_FLOOR = \"${version_name}\";/" \
    "$update_activity"
fi

# v0.16 release enhancements are applied to the temporary Java source copy so the
# published APK gets HD+ rendering and The Cover Project workflow without touching
# game files or bundling third-party cover scans.
python3 - "$work_dir/app-java" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1]) / "uk/co/blustudio/blubox360"
core_path = root / "CoreConfig.java"
main_path = root / "MainActivity.java"


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"v0.16 source patch failed: {label}")
    return text.replace(old, new, 1)

core = core_path.read_text(encoding="utf-8")
core = replace_once(core,
'''    static final String GRAPHICS_HD = "hd";
    static final String GRAPHICS_CUSTOM = "custom";''',
'''    static final String GRAPHICS_HD = "hd";
    static final String GRAPHICS_HD_PLUS = "hd_plus";
    static final String GRAPHICS_CUSTOM = "custom";''',
"graphics constant")

core = replace_once(core,
'''        } else if (GRAPHICS_BALANCED.equals(mode)) {''',
'''        } else if (GRAPHICS_HD_PLUS.equals(mode)) {
            editor.putInt(PREF_RENDER_SCALE, 2)
                    .putString(PREF_UPSCALER, "fsr")
                    .putString(PREF_ANTIALIASING, "fxaa_extreme")
                    .putInt(PREF_ANISOTROPIC, 5)
                    .putString(PREF_READBACK, "fast")
                    .putBoolean(PREF_ASYNC_SHADERS, true)
                    .putBoolean(PREF_SKIP_DRAWS, true)
                    .putInt(PREF_PIPELINE_THREADS, 4)
                    .putBoolean(PREF_PIPELINE_PRELOAD, true)
                    .putBoolean(PREF_COOL_MODE, false);
        } else if (GRAPHICS_BALANCED.equals(mode)) {''',
"HD+ preset")

core = replace_once(core,
'''        int fallback = GRAPHICS_HD.equals(graphicsMode(context)) ? 2 : 1;''',
'''        String mode = graphicsMode(context);
        int fallback = (GRAPHICS_HD.equals(mode) || GRAPHICS_HD_PLUS.equals(mode)) ? 2 : 1;''',
"HD+ render scale")

core = replace_once(core,
'''            config.save_config_entry("Display|postprocess_ffx_cas_additional_sharpness",
                    "none".equals(upscaler(context)) ? "0.0" : "0.25");''',
'''            String extraSharpness = "none".equals(upscaler(context)) ? "0.0"
                    : GRAPHICS_HD_PLUS.equals(graphicsMode(context)) ? "0.40" : "0.25";
            config.save_config_entry("Display|postprocess_ffx_cas_additional_sharpness",
                    extraSharpness);''',
"HD+ sharpening")
core_path.write_text(core, encoding="utf-8")

main = main_path.read_text(encoding="utf-8")
main = replace_once(main,
'''        RadioButton performance = radio("Performance • native resolution, fastest", CoreConfig.GRAPHICS_PERFORMANCE);
        RadioButton balanced = radio("Balanced • native rendering + FSR sharpening", CoreConfig.GRAPHICS_BALANCED);
        RadioButton hd = radio("HD • 2x internal resolution + FSR", CoreConfig.GRAPHICS_HD);
        RadioButton custom = radio("Custom • Renderer tab settings", CoreConfig.GRAPHICS_CUSTOM);
        group.addView(performance);
        group.addView(balanced);
        group.addView(hd);
        group.addView(custom);
        if (CoreConfig.GRAPHICS_HD.equals(mode)) hd.setChecked(true);
        else if (CoreConfig.GRAPHICS_PERFORMANCE.equals(mode)) performance.setChecked(true);
        else if (CoreConfig.GRAPHICS_CUSTOM.equals(mode)) custom.setChecked(true);
        else balanced.setChecked(true);''',
'''        RadioButton performance = radio("Performance • native resolution, fastest", CoreConfig.GRAPHICS_PERFORMANCE);
        RadioButton balanced = radio("Balanced • native rendering + FSR sharpening", CoreConfig.GRAPHICS_BALANCED);
        RadioButton hd = radio("HD • 2x internal resolution + FSR", CoreConfig.GRAPHICS_HD);
        RadioButton hdPlus = radio("HD+ • 2x + sharper FSR + 16x texture filtering", CoreConfig.GRAPHICS_HD_PLUS);
        RadioButton custom = radio("Custom • Renderer tab settings", CoreConfig.GRAPHICS_CUSTOM);
        group.addView(performance);
        group.addView(balanced);
        group.addView(hd);
        group.addView(hdPlus);
        group.addView(custom);
        if (CoreConfig.GRAPHICS_HD_PLUS.equals(mode)) hdPlus.setChecked(true);
        else if (CoreConfig.GRAPHICS_HD.equals(mode)) hd.setChecked(true);
        else if (CoreConfig.GRAPHICS_PERFORMANCE.equals(mode)) performance.setChecked(true);
        else if (CoreConfig.GRAPHICS_CUSTOM.equals(mode)) custom.setChecked(true);
        else balanced.setChecked(true);''',
"HD+ settings UI")

main = replace_once(main,
'''        new AlertDialog.Builder(this)
                .setTitle(game.name)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        openCoverPicker(game);
                    } else if (customCover && which == 1) {
                        CoverArtStore.remove(this, game.id);
                        coverCache.remove(game.id);
                        showPage(currentPage);
                        Toast.makeText(this, "Automatic cover restored.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        confirmRemove(game);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();''',
'''        new AlertDialog.Builder(this)
                .setTitle(game.name)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        openRetailCoverSite(game);
                    } else if (which == 1) {
                        openCoverPicker(game);
                    } else if (customCover && which == 2) {
                        CoverArtStore.remove(this, game.id);
                        coverCache.remove(game.id);
                        showPage(currentPage);
                        Toast.makeText(this, "Automatic cover restored.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        confirmRemove(game);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();''',
"retail cover menu logic")

main = replace_once(main,
'''        String[] choices = customCover
                ? new String[]{"Choose full cover image", "Reset automatic cover",
                "Remove from library"}
                : new String[]{"Choose full cover image", "Remove from library"};''',
'''        String[] choices = customCover
                ? new String[]{"Find retail cover online", "Choose cover from device",
                "Reset automatic cover", "Remove from library"}
                : new String[]{"Find retail cover online", "Choose cover from device",
                "Remove from library"};''',
"retail cover menu choices")

main = replace_once(main,
'''    private void confirmRemove(GameStore.Game game) {''',
'''    private void openRetailCoverSite(GameStore.Game game) {
        Toast.makeText(this,
                "Find " + game.name + " on The Cover Project, download the Xbox 360 cover, then return and choose Cover from device.",
                Toast.LENGTH_LONG).show();
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.thecoverproject.net/view.php?cat_id=10"));
            startActivity(browser);
        } catch (Throwable t) {
            Toast.makeText(this, "The cover website could not be opened.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmRemove(GameStore.Game game) {''',
"retail cover website action")

main = replace_once(main,
'''        card.addView(label("Choose smooth performance or higher-resolution rendering. Changes apply on the next launch.",
                11, Color.rgb(220, 241, 255), false));''',
'''        card.addView(label("Choose smooth performance or higher-resolution rendering. HD+ improves texture clarity with stronger filtering and sharpening, and turns Low Heat Mode off. Changes apply on the next launch.",
                11, Color.rgb(220, 241, 255), false));''',
"HD+ explanation")

main_path.write_text(main, encoding="utf-8")
PY

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
  --version-code "$version_code" \
  --version-name "$version_name" \
  --replace-version \
  --java "$work_dir/gen" \
  --extra-packages xendroid.compose.core \
  -A "$work_dir/assets" \
  "$work_dir/app-res.zip" "$work_dir/core-res.zip"

mapfile -t java_sources < <(find \
  "$work_dir/app-java" \
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
  output_apk="$output_dir/BluBox-360-${version_name}-arm64.apk"
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
  output_apk="$output_dir/BluBox-360-${version_name}-arm64-unsigned.apk"
  cp "$work_dir/aligned.apk" "$output_apk"
fi

"$tools_dir/zipalign" -c -p 4 "$output_apk"
echo "$output_apk"
