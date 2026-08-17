#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")" && pwd)
dist="$root/dist"
app="$dist/BluBox 360.app"
contents="$app/Contents"
macos="$contents/MacOS"
resources="$contents/Resources"
triple=${BLUBOX_MAC_TRIPLE:-arm64-apple-macosx13.0}
version=${BLUBOX_MAC_VERSION:-0.2.0-preview}

rm -rf "$dist"
mkdir -p "$macos" "$resources"

swift build --package-path "$root" -c release --triple "$triple"

binary=$(find "$root/.build" -type f -name BluBoxMac -path '*/release/*' | head -n 1)
if [[ -z "${binary:-}" || ! -f "$binary" ]]; then
  echo "BluBoxMac release binary was not produced." >&2
  exit 1
fi

cp "$binary" "$macos/BluBox360"
chmod +x "$macos/BluBox360"

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
    <string>${version%-preview}</string>
    <key>CFBundleVersion</key>
    <string>2</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.games</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
PLIST

# Ad-hoc signing is enough for private preview testing. A public Mac release will
# use Developer ID signing and notarization once the native emulator core is ready.
codesign --force --deep --sign - "$app"

zip_path="$dist/BluBox-360-Mac-${version}-Apple-Silicon.zip"
dmg_path="$dist/BluBox-360-Mac-${version}-Apple-Silicon.dmg"

ditto -c -k --sequesterRsrc --keepParent "$app" "$zip_path"
hdiutil create \
  -volname "BluBox 360" \
  -srcfolder "$app" \
  -ov \
  -format UDZO \
  "$dmg_path"

shasum -a 256 "$zip_path" "$dmg_path" > "$dist/SHA256SUMS.txt"

echo "Built BluBox 360 macOS preview:"
echo "  $zip_path"
echo "  $dmg_path"
