#!/usr/bin/env bash
# Installs the toolchain needed to build BBDNS on Debian/Ubuntu:
# JDK, Android SDK (platform 36), NDK 27.0.12077973 and CMake 3.22.1.
# Safe to run more than once.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"

if command -v javac >/dev/null && command -v unzip >/dev/null && command -v wget >/dev/null; then
    echo "==> JDK and utilities already installed"
else
    echo "==> Installing JDK and utilities (sudo required)"
    if apt-cache show openjdk-21-jdk-headless >/dev/null 2>&1; then
        JDK_PKG=openjdk-21-jdk-headless
    else
        JDK_PKG=openjdk-17-jdk-headless
    fi
    sudo apt-get update
    sudo apt-get install -y "$JDK_PKG" unzip wget
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
    echo "==> Downloading Android command-line tools"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    tmp="$(mktemp -d)"
    wget -q --show-progress -O "$tmp/$CMDLINE_TOOLS_ZIP" "$CMDLINE_TOOLS_URL"
    unzip -q "$tmp/$CMDLINE_TOOLS_ZIP" -d "$tmp"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp"
else
    echo "==> Android command-line tools already installed"
fi

echo "==> Accepting SDK licenses"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true

echo "==> Installing SDK packages"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0" \
    "ndk;27.0.12077973" \
    "cmake;3.22.1"

echo "==> Writing local.properties"
echo "sdk.dir=$ANDROID_HOME" > "$REPO_DIR/local.properties"

PROFILE="$HOME/.bashrc"
if ! grep -q "ANDROID_HOME=" "$PROFILE" 2>/dev/null; then
    echo "==> Adding ANDROID_HOME to $PROFILE"
    {
        echo ""
        echo "export ANDROID_HOME=\"$ANDROID_HOME\""
        echo 'export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"'
    } >> "$PROFILE"
fi

echo
echo "Done. Build with:"
echo "  cd \"$REPO_DIR\" && ./gradlew :app:assembleDebug"
