#!/usr/bin/env bash
set -euo pipefail

# TalkBack Agent developer build & test script.
#
# With Nix:     nix run .#build            (preferred)
# Without Nix:  ./dev.sh                   (falls back to build.sh + local tools)
#
# Modes:
#   ./dev.sh                  Full build (Android + Python tests)
#   ./dev.sh --android-only   Just Gradle build
#   ./dev.sh --server-only    Just Python tests
#   ./dev.sh --check          Compile check only (no APK)

# If Nix is available, delegate to the flake packages
if command -v nix &>/dev/null && [ -f flake.nix ]; then
  case "${1:---full}" in
    --server-only)  exec nix run .#test-server ;;
    --check)        exec nix run .#build -- --check ;;
    --android-only) exec nix run .#build ;;
    *)              exec nix run .#dev ;;
  esac
fi

# ── Non-Nix fallback ─────────────────────────────────────────────
# Uses whatever JDK/SDK/Gradle are available locally, or runs the
# original build.sh which downloads Gradle from the internet.

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

ANDROID=true
SERVER=true
CHECK_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --android-only) SERVER=false ;;
    --server-only)  ANDROID=false ;;
    --check)        CHECK_ONLY=true ;;
    --help|-h)
      sed -n '3,14p' "$0" | sed 's/^# //' | sed 's/^#//'
      exit 0
      ;;
  esac
done

if [ "$ANDROID" = true ]; then
  if command -v gradle &>/dev/null; then
    echo "Using system gradle: $(gradle --version | grep '^Gradle')"
    echo "sdk.dir=${ANDROID_HOME:-${ANDROID_SDK:-${ANDROID_SDK_ROOT:-}}}" > local.properties
    if [ "$CHECK_ONLY" = true ]; then
      gradle compilePhoneDebugKotlin compilePhoneDebugJavaWithJavac --no-daemon --warning-mode all
    else
      gradle assemblePhoneDebug --no-daemon --warning-mode all
    fi
  else
    echo "No gradle on PATH. Running original build.sh (downloads Gradle 7.6.4)..."
    bash build.sh
  fi
fi

if [ "$SERVER" = true ]; then
  echo ""
  echo "=== Python Server Tests ==="
  cd "$REPO_ROOT/server"
  python -m pytest test_server.py -v
fi

echo ""
echo "Done."
