#!/usr/bin/env bash
set -euo pipefail

APIS=(23 24)
AVD_PREFIX="emu-api"
SDCARD_SIZE="1024M"

SDK_ROOT="/Users/aleksandr.gringauz/Library/Android/sdk"
SDKMGR="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
EMU="$SDK_ROOT/emulator/emulator"

# Use Google APIs ARM64 images; note slashes in the on-disk path
TAG_ID="google_apis"
ABI_DIR="arm64-v8a"
SDK_PKG_FLAVOR="google_apis;arm64-v8a"

[[ -n "$SDK_ROOT" ]] || { echo "❌ ANDROID_SDK_ROOT/ANDROID_HOME not set"; exit 1; }
[[ -x "$SDKMGR" ]]   || { echo "❌ sdkmanager not found at $SDKMGR"; exit 1; }
[[ -x "$EMU"    ]]   || { echo "❌ emulator not found at $EMU"; exit 1; }

yes | "$SDKMGR" --licenses >/dev/null || true
"$SDKMGR" --install "platform-tools" "emulator" >/dev/null

mkdir -p "$HOME/.android/avd"

for api in "${APIS[@]}"; do
  pkg="system-images;android-${api};${SDK_PKG_FLAVOR}"
  avd_name="${AVD_PREFIX}${api}"
  avd_dir="$HOME/.android/avd/${avd_name}.avd"
  ini_file="$HOME/.android/avd/${avd_name}.ini"

  echo "==> Installing system image: ${pkg}"
  "$SDKMGR" --install "${pkg}"

  img_dir="$SDK_ROOT/system-images/android-${api}/${TAG_ID}/${ABI_DIR}"
  [[ -d "$img_dir" ]] || { echo "❌ Missing image dir: $img_dir"; exit 1; }

  echo "==> Creating AVD files for ${avd_name}"
  rm -rf "$avd_dir" "$ini_file"
  mkdir -p "$avd_dir"

  cat > "$avd_dir/config.ini" <<EOF
AvdId=${avd_name}
PlayStore.enabled=false
abi.type=arm64-v8a
avd.ini.displayname=${avd_name}
hw.cpu.arch=arm64
hw.cpu.ncore=2
hw.ramSize=2048
hw.sdCard=yes
sdcard.size=${SDCARD_SIZE}
# No explicit skin: rely on LCD size
hw.lcd.density=420
hw.lcd.height=1920
hw.lcd.width=1080
# Correct system image path (uses slashes)
image.sysdir.1=system-images/android-${api}/${TAG_ID}/${ABI_DIR}/
tag.id=${TAG_ID}
tag.display=Google APIs
target=android-${api}
hw.gpu.enabled=yes
EOF

  cat > "$ini_file" <<EOF
avd.ini.encoding=UTF-8
path=${avd_dir}
path.rel=avd/${avd_name}.avd
target=android-${api}
EOF

  echo "✓ Created ${avd_name}"
done

echo
"$EMU" -list-avds
echo "Launch with:"
echo "  $EMU -avd ${AVD_PREFIX}23"
