#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
tools_dir="${SOCIALDARK_TOOLCHAINS:-/workspace/toolchains}"
mkdir -p "$tools_dir/downloads" "$tools_dir/android-user"

download_verified() {
    local artifact_url="$1" artifact_sha="$2" artifact_file="$3"
    if ! [ -f "$artifact_file" ] || ! printf '%s  %s\n' "$artifact_sha" "$artifact_file" | sha256sum -c - >/dev/null 2>&1; then
        curl -fL --retry 2 "$artifact_url" -o "$artifact_file"
    fi
    printf '%s  %s\n' "$artifact_sha" "$artifact_file" | sha256sum -c -
}
if ! [ -x "$tools_dir/jdk-17/bin/javac" ]; then
    download_verified \
        'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz' \
        '8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711' \
        "$tools_dir/downloads/jdk.tar.gz"
    mkdir -p "$tools_dir/jdk-17"
    tar -xzf "$tools_dir/downloads/jdk.tar.gz" -C "$tools_dir/jdk-17" --strip-components=1
fi
export JAVA_HOME="$tools_dir/jdk-17"
export ANDROID_HOME="$tools_dir/android-sdk"
export ANDROID_USER_HOME="$tools_dir/android-user"
export PATH="$JAVA_HOME/bin:$PATH"
if ! [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    download_verified \
        'https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip' \
        '0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8' \
        "$tools_dir/downloads/android-tools.zip"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unpack_dir="$(mktemp -d "$tools_dir/downloads/cli.XXXXXX")"
    unzip -q "$tools_dir/downloads/android-tools.zip" -d "$unpack_dir"
    mv "$unpack_dir/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rmdir "$unpack_dir"
fi
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses < <(yes)
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools' 'emulator' 'system-images;android-35;default;x86_64'
cd "$repo_dir"
python3 scripts/cloud-build.py assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --no-daemon
npm ci --cache "$tools_dir/npm-cache"
npm test
