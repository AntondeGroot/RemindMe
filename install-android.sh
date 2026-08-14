#!/usr/bin/env bash
# Builds the debug APK and installs it on a connected Android device.
#
# Each step has a way of going wrong quietly, which is what this script is for:
#
#   1. Gradle needs JDK 17. The newest installed JDK is normally the default and
#      fails on class file major version, which reads as a project problem
#      rather than a toolchain one.
#   2. adb is not on the PATH from a normal shell, and a phone can be attached
#      without being usable — unauthorized, or still asleep.
#   3. Installing over an app signed with a different debug key fails with
#      INSTALL_FAILED_UPDATE_INCOMPATIBLE; the fix is to uninstall first, which
#      wipes your reminders, so that is never done without asking.
#
# Usage:
#   ./install-android.sh                  # build, then install
#   ./install-android.sh --skip-build     # reinstall the existing APK
#
# ANDROID_SERIAL picks a phone when more than one is attached.

set -euo pipefail

REQUIRED_JDK=17
APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="nl.local.remindme"

# Run from the repo root whichever directory this was invoked from.
cd "$(dirname "${BASH_SOURCE[0]}")"

fail() {
  printf '\n%s\n' "$1" >&2
  exit 1
}

# ——— adb ———

sdk_root() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s' "$ANDROID_SDK_ROOT"
  elif [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s' "$ANDROID_HOME"
  elif [[ "$(uname)" == "Darwin" ]]; then
    printf '%s' "$HOME/Library/Android/sdk"
  else
    printf '%s' "$HOME/Android/Sdk"
  fi
}

ADB="$(sdk_root)/platform-tools/adb"
[[ -x "$ADB" ]] || fail "no adb at $ADB — set ANDROID_SDK_ROOT to your SDK location"

# ——— The one device to install onto ———
#
# Attached is not the same as usable: a phone that has not had the "Allow USB
# debugging?" prompt accepted shows up as `unauthorized`, and installing would
# fail with something far less obvious than saying so here.

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  SERIAL="$ANDROID_SERIAL"
else
  listed="$("$ADB" devices | tail -n +2)"
  ready="$(printf '%s\n' "$listed" | awk '$2 == "device" { print $1 }')"
  unauthorized="$(printf '%s\n' "$listed" | awk '$2 == "unauthorized" { print $1 }')"

  if [[ -z "$ready" && -n "$unauthorized" ]]; then
    fail "unlock the phone and accept the 'Allow USB debugging?' prompt, then retry"
  fi
  if [[ -z "$ready" ]]; then
    fail "no device connected. Plug the phone in over USB with USB debugging on (Settings → search 'USB debugging')"
  fi
  if [[ "$(printf '%s\n' "$ready" | wc -l | tr -d ' ')" -gt 1 ]]; then
    fail "more than one device attached ($(printf '%s' "$ready" | tr '\n' ' ')) — disconnect the others, or set ANDROID_SERIAL"
  fi
  SERIAL="$ready"
fi

# ——— Build ———

if [[ "${1:-}" == "--skip-build" ]]; then
  [[ -f "$APK" ]] || fail "no APK at $APK — run without --skip-build first"
  echo "→ using the existing APK"
else
  echo "→ building the APK with JDK $REQUIRED_JDK"

  # The wrapper jar is a binary and is not in the repo; regenerate it once.
  if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
    command -v gradle >/dev/null || fail \
      "gradle/wrapper/gradle-wrapper.jar is missing and there is no local gradle to regenerate it (brew install gradle)"
    echo "→ regenerating the Gradle wrapper"
    gradle wrapper --gradle-version 8.9
  fi

  if [[ "$(uname)" == "Darwin" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v "$REQUIRED_JDK" 2>/dev/null)" || fail \
      "no JDK $REQUIRED_JDK found. Gradle cannot read newer class files — install Temurin $REQUIRED_JDK and try again"
    export JAVA_HOME
  elif [[ -z "${JAVA_HOME:-}" ]]; then
    # Elsewhere JAVA_HOME is the convention; trust it rather than guess paths.
    fail "set JAVA_HOME to a JDK $REQUIRED_JDK install before running this"
  fi

  ./gradlew assembleDebug
fi

# ——— Install ———
#
# -r reinstalls in place and keeps app data, so your reminders and the times you
# have edited survive the update.

echo "→ installing on $SERIAL"
if ! output="$("$ADB" -s "$SERIAL" install -r "$APK" 2>&1)"; then
  printf '%s\n' "$output" >&2
  if [[ "$output" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* || "$output" == *INSTALL_FAILED_VERSION_DOWNGRADE* ]]; then
    fail "a different build of $PACKAGE is already installed. Removing it wipes your reminders:
  $ADB -s $SERIAL uninstall $PACKAGE && ./install-android.sh --skip-build"
  fi
  fail "install failed"
fi
printf '%s\n' "$output"

printf '\nInstalled. Open Remind Me on the phone and grant the three permissions it asks for.\n'