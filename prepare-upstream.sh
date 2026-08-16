#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/blubox360-upstream.XXXXXX")"
xendroid_dir="$work_dir/XenDroid"
patches_dir="$work_dir/game-patches"

if [ -e "$project_dir/emulator-core" ]; then
  echo "emulator-core already exists; remove or move it before preparing again." >&2
  exit 1
fi

git clone https://github.com/rfandango/XenDroid "$xendroid_dir"
git -C "$xendroid_dir" checkout 0b1120187340be3644e28ceb7a04be04cd0f2e08
git -C "$xendroid_dir" submodule update --init --recursive
cp -a "$xendroid_dir/emulator-core" "$project_dir/emulator-core"

git -C "$project_dir" apply patches/xendroid-core-android-r27.patch

git clone https://github.com/xenia-canary/game-patches "$patches_dir"
git -C "$patches_dir" checkout 3553a5aea5ad64c5ec0169d8b8d5792e2ab45e44
mkdir -p "$project_dir/emulator-core/src/main/cpp/xenia/build/data_repos/game-patches"
cp -a "$patches_dir/patches" \
  "$project_dir/emulator-core/src/main/cpp/xenia/build/data_repos/game-patches/"

echo "Pinned Xbox 360 core prepared in $project_dir/emulator-core"
