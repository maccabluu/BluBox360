#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")" && pwd)
dist="$root/dist"
app="$dist/BluBox 360.app"
contents="$app/Contents"
macos="$contents/MacOS"
resources="$contents/Resources"
version=${BLUBOX_MAC_VERSION:-2.3.0-preview}
arm_triple=${BLUBOX_MAC_ARM_TRIPLE:-arm64-apple-macosx13.0}
intel_triple=${BLUBOX_MAC_INTEL_TRIPLE:-x86_64-apple-macosx13.0}

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

core_arm="$dist/blubox360-core-arm64"
core_intel="$dist/blubox360-core-x86_64"
core_universal="$resources/blubox360-core"

clang -O2 -arch arm64 -mmacosx-version-min=13.0 \
  "$root/NativeCore/blubox360_core.c" -o "$core_arm"
clang -O2 -arch x86_64 -mmacosx-version-min=13.0 \
  "$root/NativeCore/blubox360_core.c" -o "$core_intel"
lipo -create "$core_arm" "$core_intel" -output "$core_universal"
chmod +x "$core_universal"
rm -f "$core_arm" "$core_intel"

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
    <string>2.3.0</string>
    <key>CFBundleVersion</key>
    <string>23</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
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

codesign --force --sign - "$core_universal"
codesign --force --deep --sign - "$app"

zip_path="$dist/BluBox-360-Mac-${version}-Universal.zip"
dmg_path="$dist/BluBox-360-Mac-${version}-Universal.dmg"
staging="$dist/dmg-root"

rm -rf "$staging"
mkdir -p "$staging"
cp -R "$app" "$staging/BluBox 360.app"
ln -s /Applications "$staging/Applications"

ditto -c -k --sequesterRsrc --keepParent "$app" "$zip_path"
hdiutil create \
  -volname "BluBox 360 2.3" \
  -srcfolder "$staging" \
  -ov \
  -format UDZO \
  "$dmg_path"

rm -rf "$staging"
shasum -a 256 "$zip_path" "$dmg_path" > "$dist/SHA256SUMS.txt"

echo "Built BluBox 360 macOS 2.3 universal preview:"
echo "  $zip_path"
echo "  $dmg_path"
echo "  bundled core: $core_universal"
