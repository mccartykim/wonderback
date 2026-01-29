#!/usr/bin/env bash
# Start Android Emulator with GUI for demo viewing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "╔═══════════════════════════════════════════════════════╗"
echo "║   STARTING ANDROID EMULATOR WITH GUI                 ║"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""

# Check if we're in nix environment
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME not set. Running via nix develop..."
    echo ""
    cd "$PROJECT_ROOT"
    exec nix develop --command "$0" "$@"
fi

# Check if AVD exists
AVD_NAME="talkback_test"
if ! "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null | grep -q "^$AVD_NAME$"; then
    echo "❌ AVD '$AVD_NAME' not found!"
    echo ""
    echo "Available AVDs:"
    "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null || echo "  (none)"
    echo ""
    echo "To create the AVD, see SETUP.md"
    exit 1
fi

# Check if emulator is already running
if adb devices | grep -q "emulator"; then
    echo "⚠️  Emulator already running!"
    echo ""
    adb devices
    echo ""
    echo "To stop it first: adb emu kill"
    exit 0
fi

echo "Starting emulator '$AVD_NAME' with GUI..."
echo ""
echo "Emulator window will open shortly."
echo "Features enabled:"
echo "  ✓ GUI window (not headless)"
echo "  ✓ Root access (-writable-system)"
echo "  ✓ Permissive SELinux (for accessibility)"
echo ""
echo "This will run in the background."
echo "To stop: adb emu kill"
echo ""

# Start emulator with GUI
# -no-window is removed to show GUI
# -writable-system and -selinux permissive for root access
"$ANDROID_HOME/emulator/emulator" \
    -avd "$AVD_NAME" \
    -writable-system \
    -selinux permissive \
    -no-audio \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    &

EMULATOR_PID=$!
echo "Emulator starting (PID: $EMULATOR_PID)..."
echo ""
echo "Waiting for emulator to boot (this may take 30-60 seconds)..."

# Wait for device to be ready
timeout=60
count=0
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
    if [ $count -ge $timeout ]; then
        echo "❌ Timeout waiting for emulator to boot"
        exit 1
    fi
    echo -n "."
    sleep 1
    count=$((count + 1))
done
echo ""
echo ""

echo "✅ Emulator is ready!"
echo ""

# Enable root
echo "Enabling root access..."
adb root
sleep 2

echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. The emulator GUI window should be visible"
echo "  2. Run the demo: ./agents/demo.sh"
echo ""
echo "Or run tester agent manually:"
echo "  python3 agents/tester_agent.py --debug-delay 2.0"
