#!/usr/bin/env bash
set -euo pipefail

# TalkBack Agent developer build & test script.
# Wraps the existing build.sh and adds Python server tests.
#
# Usage (inside `nix develop`):
#   ./dev.sh                  Full build (Android + Python tests)
#   ./dev.sh --android-only   Just Gradle assemblePhoneDebug
#   ./dev.sh --server-only    Just Python server tests
#   ./dev.sh --check          Kotlin/Java compile check only (no APK, faster)
#   ./dev.sh --deploy         Build + install on connected device + start server

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

ANDROID=true
SERVER=true
CHECK_ONLY=false
DEPLOY=false

for arg in "$@"; do
  case "$arg" in
    --android-only) SERVER=false ;;
    --server-only)  ANDROID=false ;;
    --check)        CHECK_ONLY=true ;;
    --deploy)       DEPLOY=true ;;
    --help|-h)
      sed -n '3,10p' "$0" | sed 's/^# //' | sed 's/^#//'
      exit 0
      ;;
    *) echo "Unknown arg: $arg"; exit 1 ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

echo -e "${CYAN}TalkBack Agent — Dev Build${NC}"
echo "=========================="

# ── Resolve Gradle ────────────────────────────────────────────────
#
# The original build.sh downloads Gradle 7.6.4. Inside `nix develop`
# we have Gradle on PATH already. We prefer the Nix-provided one
# but fall back to downloading if not available.

find_gradle() {
  if command -v gradle &>/dev/null; then
    echo "gradle"
  elif [ -x "/opt/gradle-7.6.4/gradle-7.6.4/bin/gradle" ]; then
    echo "/opt/gradle-7.6.4/gradle-7.6.4/bin/gradle"
  else
    echo ""
  fi
}

# ── Pre-flight ────────────────────────────────────────────────────

if [ "$ANDROID" = true ]; then
  # Accept either ANDROID_HOME or ANDROID_SDK (the latter is what build.sh uses)
  export ANDROID_SDK="${ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
  if [ -z "$ANDROID_SDK" ]; then
    echo -e "${RED}No Android SDK found. Set ANDROID_HOME or ANDROID_SDK, or run inside 'nix develop'.${NC}"
    exit 1
  fi
  export ANDROID_HOME="$ANDROID_SDK"
  export ANDROID_SDK_ROOT="$ANDROID_SDK"

  if [ -z "${JAVA_HOME:-}" ]; then
    echo -e "${RED}JAVA_HOME not set. Use JDK 17.${NC}"
    exit 1
  fi

  # Ensure local.properties
  echo "sdk.dir=$ANDROID_SDK" > local.properties

  GRADLE_BIN=$(find_gradle)
  if [ -z "$GRADLE_BIN" ]; then
    echo -e "${YELLOW}Gradle not on PATH. Running original build.sh to download it...${NC}"
    bash build.sh
    # After build.sh, the APK is built and Gradle is downloaded.
    # Skip our Gradle steps.
    ANDROID=false
  fi

  echo "Java:   $(java -version 2>&1 | head -1)"
  echo "SDK:    $ANDROID_SDK"
  echo "Gradle: $GRADLE_BIN"
fi

STEP=1
TOTAL=0
[ "$ANDROID" = true ] && TOTAL=$((TOTAL + 1))
[ "$SERVER" = true ] && TOTAL=$((TOTAL + 1))

# ── Android Build ────────────────────────────────────────────────

if [ "$ANDROID" = true ]; then
  echo ""
  echo -e "${YELLOW}[$STEP/$TOTAL] Android Build${NC}"
  echo "-------------------"
  STEP=$((STEP + 1))

  GRADLE_ARGS="--no-daemon --warning-mode all"

  if [ "$CHECK_ONLY" = true ]; then
    echo "Compile check (no APK)..."
    TASK="compilePhoneDebugKotlin compilePhoneDebugJavaWithJavac"
  else
    echo "Full assemblePhoneDebug..."
    TASK="assemblePhoneDebug"
  fi

  set +e
  $GRADLE_BIN $TASK $GRADLE_ARGS 2>&1 | tee /tmp/talkback-build.log
  BUILD_RC=${PIPESTATUS[0]}
  set -e

  if [ "$BUILD_RC" -eq 0 ]; then
    echo -e "${GREEN}Android build succeeded.${NC}"
    if [ "$CHECK_ONLY" = false ]; then
      APK=$(find . -name "*phone*debug*.apk" -newer /tmp/talkback-build.log 2>/dev/null | head -1)
      [ -n "$APK" ] && echo "APK: $APK ($(du -h "$APK" | cut -f1))"
    fi
  else
    echo -e "${RED}Android build failed.${NC}"
    echo ""
    echo "Errors:"
    grep -E "^e:|error:|FAILURE" /tmp/talkback-build.log | head -30
    echo ""
    echo "Full log: /tmp/talkback-build.log"
    exit 1
  fi
fi

# ── Python Server Tests ──────────────────────────────────────────

if [ "$SERVER" = true ]; then
  echo ""
  echo -e "${YELLOW}[$STEP/$TOTAL] Python Server Tests${NC}"
  echo "-------------------------"
  STEP=$((STEP + 1))

  cd "$REPO_ROOT/server"

  # Install pip-only deps if needed (ollama, zeroconf aren't in nixpkgs)
  if ! python -c "import ollama" 2>/dev/null; then
    echo "Installing ollama + zeroconf via pip..."
    pip install --quiet ollama zeroconf 2>/dev/null || echo "(pip install failed — tests may skip ollama-dependent tests)"
  fi

  set +e
  python -m pytest test_server.py -v 2>&1
  TEST_RC=$?
  set -e

  cd "$REPO_ROOT"

  if [ "$TEST_RC" -eq 0 ]; then
    echo -e "${GREEN}All server tests passed.${NC}"
  else
    echo -e "${RED}Server tests failed.${NC}"
    exit 1
  fi
fi

# ── Deploy (optional) ────────────────────────────────────────────

if [ "$DEPLOY" = true ] && [ "$CHECK_ONLY" = false ]; then
  echo ""
  echo -e "${YELLOW}Deploying...${NC}"

  APK=$(find . -name "*phone*debug*.apk" 2>/dev/null | sort -t/ -k2 | tail -1)
  if [ -z "$APK" ]; then
    echo -e "${RED}No APK found. Build first.${NC}"
    exit 1
  fi

  echo "Installing $APK..."
  adb install -r "$APK"

  echo "Setting up ADB port forwarding..."
  adb reverse tcp:8080 tcp:8080

  echo ""
  echo -e "${GREEN}Deployed.${NC}"
  echo "Starting server... (Ctrl+C to stop)"
  cd "$REPO_ROOT/server"
  exec python main.py
fi

# ── Summary ──────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}All done.${NC}"
