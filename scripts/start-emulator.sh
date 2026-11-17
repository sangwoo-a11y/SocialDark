#!/usr/bin/env bash
set -euo pipefail
tools_dir="${SOCIALDARK_TOOLCHAINS:-/workspace/toolchains}"
export ANDROID_HOME="$tools_dir/android-sdk"
export ANDROID_USER_HOME="$tools_dir/android-user"
export ANDROID_EMULATOR_HOME="$tools_dir/android-user"
export ANDROID_AVD_HOME="$tools_dir/avd"
export XDG_RUNTIME_DIR="$tools_dir/runtime"
mkdir -p "$ANDROID_AVD_HOME" "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
if ! [ -f "$ANDROID_AVD_HOME/socialdark-test.avd/config.ini" ]; then
    printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd --name socialdark-test --package 'system-images;android-35;default;x86_64' --device pixel_6
fi
exec "$ANDROID_HOME/emulator/emulator" -avd socialdark-test -no-window -no-audio -no-boot-anim -no-snapshot -accel off -gpu swiftshader_indirect -cores 2 -memory 2048
