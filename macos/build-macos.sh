#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")" && pwd)
repo_root=$(cd "$root/.." && pwd)
dist="$root/dist"
app="$dist/BluBox 360.app"
contents="$app/Contents"
macos="$contents/MacOS"
resources="$contents/Resources"
version=${BLUBOX_MAC_VERSION:-3.0.1-preview}
arm_triple=${BLUBOX_MAC_ARM_TRIPLE:-arm64-apple-macosx15.0}
intel_triple=${BLUBOX_MAC_INTEL_TRIPLE:-x86_64-apple-macosx15.0}
xenia_arm_app=${BLUBOX_XENIA_ARM_APP:-}
xenia_x64_app=${BLUBOX_XENIA_X64_APP:-}

rm -rf "$dist"
mkdir -p "$macos" "$resources"

build_binary() {
  local triple="$1"
  swift build --package-path "$root" -c release --triple "$triple" >&2
  local bin_dir
  bin_dir=$(swift build --package-path "$root" -c release --triple "$triple" --show-bin-path)
  if [[ ! -x "$bin_dir/BluBoxMac" ]]; then
    echo "BluBoxMac release binary was not produced for $triple." >&2
    exit 1
  fi
  printf '%s\n' "$bin_dir/BluBoxMac"
}

arm_binary=$(build_binary "$arm_triple")
intel_binary=$(build_binary "$intel_triple")
lipo -create "$arm_binary" "$intel_binary" -output "$macos/BluBox360"
chmod +x "$macos/BluBox360"

# Keep the small diagnostic/JIT bootstrap beside the real experimental engine.
core_arm="$dist/blubox360-core-arm64"
core_intel="$dist/blubox360-core-x86_64"
core_universal="$resources/blubox360-core"
clang -O2 -arch arm64 -mmacosx-version-min=15.0 \
  "$root/NativeCore/blubox360_core.c" -o "$core_arm"
clang -O2 -arch x86_64 -mmacosx-version-min=15.0 \
  "$root/NativeCore/blubox360_core.c" -o "$core_intel"
lipo -create "$core_arm" "$core_intel" -output "$core_universal"
chmod +x "$core_universal"
rm -f "$core_arm" "$core_intel"
codesign --force --sign - "$core_universal"

# Use the same BluBox artwork as the Android application.
android_icon="$repo_root/app/src/main/res/drawable-nodpi/blubox_launcher_icon.png"
android_logo="$repo_root/app/src/main/res/drawable-nodpi/blubox_logo.png"
if [[ ! -f "$android_icon" || ! -f "$android_logo" ]]; then
  echo "BluBox Android logo assets were not found." >&2
  exit 1
fi
cp "$android_logo" "$resources/blubox_logo.png"

iconset="$dist/BluBox.iconset"
mkdir -p "$iconset"
make_icon() {
  local pixels="$1"
  local output="$2"
  sips -s format png -z "$pixels" "$pixels" "$android_icon" --out "$iconset/$output" >/dev/null
}
make_icon 16 icon_16x16.png
make_icon 32 icon_16x16@2x.png
make_icon 32 icon_32x32.png
make_icon 64 icon_32x32@2x.png
make_icon 128 icon_128x128.png
make_icon 256 icon_128x128@2x.png
make_icon 256 icon_256x256.png
make_icon 512 icon_256x256@2x.png
make_icon 512 icon_512x512.png
make_icon 1024 icon_512x512@2x.png
iconutil -c icns "$iconset" -o "$resources/BluBox.icns"
rm -rf "$iconset"

# Carry over the bundled Fable II performance patch and open-source notices.
mkdir -p "$resources/Patches" "$resources/Licenses"
patch_source="$repo_root/app/src/main/assets/patches/4D5307F1 - Fable II (BluBox Performance).patch.toml"
if [[ -f "$patch_source" ]]; then
  cp "$patch_source" "$resources/Patches/"
fi
if [[ -f "$repo_root/licenses/XENIA-BSD-3-Clause.txt" ]]; then
  cp "$repo_root/licenses/XENIA-BSD-3-Clause.txt" "$resources/Licenses/XENIA-BSD-3-Clause.txt"
fi
if [[ -f "$root/XENIA_MAC_NOTICE.md" ]]; then
  cp "$root/XENIA_MAC_NOTICE.md" "$resources/Licenses/XENIA_MAC_NOTICE.md"
fi

# Bundle both Xenia-Edge macOS architectures when supplied by CI.
if [[ -n "$xenia_arm_app" && -d "$xenia_arm_app" ]]; then
  mkdir -p "$resources/Engines/arm64"
  ditto "$xenia_arm_app" "$resources/Engines/arm64/Xenia-Edge.app"
fi
if [[ -n "$xenia_x64_app" && -d "$xenia_x64_app" ]]; then
  mkdir -p "$resources/Engines/x86_64"
  ditto "$xenia_x64_app" "$resources/Engines/x86_64/Xenia-Edge.app"
fi

# BluBox 3.0.1 hotfix: the Swift shell has more settings than the current
# Xenia-Edge command-line parser accepts. Put a tiny architecture-matched
# launcher in front of each engine. It forwards only options verified in the
# current Xenia macOS source and ignores unsupported BluBox-only options.
wrap_xenia_engine() {
  local engine_app="$1"
  local arch="$2"
  [[ -d "$engine_app" ]] || return 0

  local exec_name
  exec_name=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$engine_app/Contents/Info.plist")
  local exec_path="$engine_app/Contents/MacOS/$exec_name"
  local real_path="$engine_app/Contents/MacOS/xenia_edge.real"

  if [[ ! -x "$exec_path" ]]; then
    echo "Xenia executable missing: $exec_path" >&2
    exit 1
  fi

  mv "$exec_path" "$real_path"
  clang -O2 -arch "$arch" -mmacosx-version-min=15.0 \
    "$root/EngineLauncher/blubox_xenia_launcher.c" \
    -o "$exec_path"
  chmod +x "$exec_path" "$real_path"
  codesign --force --sign - "$exec_path"

  # Re-seal the app after adding the wrapper. The moved original Xenia binary
  # keeps its own upstream code signature and JIT entitlements.
  codesign --force --sign - "$engine_app"

  "$exec_path" --blubox-self-test | grep -q 'BluBox Xenia launch sanitizer ready'
}

wrap_xenia_engine "$resources/Engines/arm64/Xenia-Edge.app" arm64
wrap_xenia_engine "$resources/Engines/x86_64/Xenia-Edge.app" x86_64

cat > "$contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>BluBox360</string>
    <key>CFBundleIdentifier</key>
    <string>uk.co.blustudio.blubox360.mac</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>BluBox 360</string>
    <key>CFBundleDisplayName</key>
    <string>BluBox 360</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>3.0.1</string>
    <key>CFBundleVersion</key>
    <string>31</string>
    <key>CFBundleIconFile</key>
    <string>BluBox.icns</string>
    <key>LSMinimumSystemVersion</key>
    <string>15.0</string>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.games</string>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>LSArchitecturePriority</key>
    <array>
        <string>arm64</string>
        <string>x86_64</string>
    </array>
</dict>
</plist>
PLIST

# The BluBox shell and diagnostic helper use ad-hoc signatures for private preview testing.
codesign --force --sign - "$app"

zip_path="$dist/BluBox-360-Mac-${version}-Universal.zip"
dmg_path="$dist/BluBox-360-Mac-${version}-Universal.dmg"
staging="$dist/dmg-root"

rm -rf "$staging"
mkdir -p "$staging"
ditto "$app" "$staging/BluBox 360.app"
ln -s /Applications "$staging/Applications"

ditto -c -k --sequesterRsrc --keepParent "$app" "$zip_path"
hdiutil create \
  -volname "BluBox 360 3.0.1" \
  -srcfolder "$staging" \
  -ov \
  -format UDZO \
  "$dmg_path"

rm -rf "$staging"
shasum -a 256 "$zip_path" "$dmg_path" > "$dist/SHA256SUMS.txt"

echo "Built BluBox 360 macOS 3.0.1 universal preview:"
echo "  $zip_path"
echo "  $dmg_path"
echo "  diagnostic core: $core_universal"
find "$resources/Engines" -maxdepth 3 -name '*.app' -print 2>/dev/null || true
