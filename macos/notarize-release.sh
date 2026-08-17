#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")" && pwd)
dist="$root/dist"
app="$dist/BluBox 360.app"
identity=${APPLE_SIGNING_IDENTITY:?APPLE_SIGNING_IDENTITY is required}
notary_key=${APPLE_NOTARY_KEY_PATH:?APPLE_NOTARY_KEY_PATH is required}
notary_key_id=${APPLE_NOTARY_KEY_ID:?APPLE_NOTARY_KEY_ID is required}
notary_issuer=${APPLE_NOTARY_ISSUER_ID:?APPLE_NOTARY_ISSUER_ID is required}
release_version=${BLUBOX_MAC_RELEASE_VERSION:-3.0.3}
build_number=${BLUBOX_MAC_BUILD_NUMBER:-33}
release_label=${BLUBOX_MAC_RELEASE_LABEL:-3.0.3-preview}

if [[ ! -d "$app" ]]; then
  echo "BluBox 360.app was not built." >&2
  exit 1
fi

/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $release_version" "$app/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $build_number" "$app/Contents/Info.plist"

sign_runtime() {
  codesign --force --options runtime --timestamp --sign "$identity" "$@"
}

for engine in \
  "$app/Contents/Resources/Engines/arm64/Xenia-Edge.app" \
  "$app/Contents/Resources/Engines/x86_64/Xenia-Edge.app"; do
  if [[ -d "$engine" ]]; then
    exec_name=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$engine/Contents/Info.plist")
    real_exec="$engine/Contents/MacOS/xenia_edge.real"
    launcher_exec="$engine/Contents/MacOS/$exec_name"

    if [[ -x "$real_exec" ]]; then
      codesign --force --options runtime --timestamp --sign "$identity" \
        --entitlements "$root/XeniaEngine.entitlements" \
        "$real_exec"
    fi
    if [[ -x "$launcher_exec" ]]; then
      sign_runtime "$launcher_exec"
    fi
    sign_runtime "$engine"
  fi
done

core="$app/Contents/Resources/blubox360-core"
if [[ -x "$core" ]]; then
  sign_runtime "$core"
fi

sign_runtime "$app"
codesign --verify --deep --strict --verbose=2 "$app"

notary_zip="$dist/BluBox-360-Mac-${release_label}-Notary.zip"
rm -f "$notary_zip"
ditto -c -k --sequesterRsrc --keepParent "$app" "$notary_zip"

xcrun notarytool submit "$notary_zip" \
  --key "$notary_key" \
  --key-id "$notary_key_id" \
  --issuer "$notary_issuer" \
  --wait

xcrun stapler staple "$app"
xcrun stapler validate "$app"
spctl --assess --type execute --verbose=2 "$app"

zip_path="$dist/BluBox-360-Mac-${release_label}-Universal.zip"
dmg_path="$dist/BluBox-360-Mac-${release_label}-Universal.dmg"
staging="$dist/notarized-dmg-root"

rm -f "$zip_path" "$dmg_path"
rm -rf "$staging"
mkdir -p "$staging"
ditto "$app" "$staging/BluBox 360.app"
ln -s /Applications "$staging/Applications"
ditto -c -k --sequesterRsrc --keepParent "$app" "$zip_path"
hdiutil create \
  -volname "BluBox 360 $release_version" \
  -srcfolder "$staging" \
  -ov \
  -format UDZO \
  "$dmg_path"
rm -rf "$staging"

xcrun notarytool submit "$dmg_path" \
  --key "$notary_key" \
  --key-id "$notary_key_id" \
  --issuer "$notary_issuer" \
  --wait
xcrun stapler staple "$dmg_path"
xcrun stapler validate "$dmg_path"
spctl --assess --type open --context context:primary-signature --verbose=2 "$dmg_path"

rm -f "$notary_zip"
shasum -a 256 "$zip_path" "$dmg_path" > "$dist/SHA256SUMS.txt"

echo "Developer ID signed and Apple-notarized BluBox 360 $release_version."
echo "$zip_path"
echo "$dmg_path"
