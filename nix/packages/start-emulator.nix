# Start emulator with GUI for demo viewing
{ pkgs, androidSdk }:

pkgs.writeShellApplication {
  name = "talkback-start-emulator-gui";
  runtimeInputs = [ androidSdk pkgs.coreutils ];
  text = ''
    REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
    cd "$REPO"

    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_AVD_HOME="$HOME/.wonderback/avd"
    mkdir -p "$ANDROID_AVD_HOME"

    echo "╔═══════════════════════════════════════════════════════╗"
    echo "║   STARTING ANDROID EMULATOR WITH GUI                 ║"
    echo "╚═══════════════════════════════════════════════════════╝"
    echo ""

    AVD_NAME="talkback_test"
    if ! "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null | grep -q "^$AVD_NAME$"; then
      echo "❌ AVD '$AVD_NAME' not found!"
      echo ""
      echo "Available AVDs:"
      "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null || echo "  (none)"
      echo ""
      echo "To create the AVD: nix run .#create-avd"
      exit 1
    fi

    if adb devices | grep -q "emulator"; then
      echo "⚠️  Emulator already running!"
      echo ""
      adb devices
      echo ""
      echo "To stop it first: adb emu kill"
      exit 0
    fi

    echo "Starting emulator '$AVD_NAME' with GUI and audio..."
    echo ""
    echo "Features enabled:"
    echo "  ✓ GUI window (not headless)"
    echo "  ✓ Audio output (hear TalkBack speaking!)"
    echo "  ✓ Root access (-writable-system)"
    echo "  ✓ Permissive SELinux (for accessibility)"
    echo ""

    "$ANDROID_HOME/emulator/emulator" \
      -avd "$AVD_NAME" \
      -writable-system \
      -selinux permissive \
      -no-boot-anim \
      -gpu swiftshader_indirect \
      &

    echo "Waiting for emulator to boot..."
    timeout=60
    count=0
    until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
      if [ $count -ge $timeout ]; then
        echo "❌ Timeout waiting for emulator"
        exit 1
      fi
      echo -n "."
      sleep 1
      count=$((count + 1))
    done
    echo ""

    echo "✅ Emulator is ready!"
    adb root
    sleep 2
    echo ""
    echo "Next: nix run .#demo"
  '';
}
