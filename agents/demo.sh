#!/usr/bin/env bash
# Demo script for TalkBack Tester Agent
# Shows the agent in action with GUI and TalkBack announcements visible

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Default delay (2 seconds gives good viewing time)
DELAY="${1:-2.0}"

echo "╔═══════════════════════════════════════════════════════╗"
echo "║    TALKBACK TESTER AGENT - DEMO MODE                 ║"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""
echo "This will:"
echo "  1. Check if emulator is running (with GUI)"
echo "  2. Launch Sudoku app"
echo "  3. Run tester agent with ${DELAY}s delays for viewing"
echo ""
echo "Watch the GUI window to see:"
echo "  - Agent tapping cells"
echo "  - Number picker dialogs opening"
echo "  - TalkBack announcements (bottom of screen)"
echo "  - Numbers being entered"
echo ""
echo "Press Ctrl+C to stop at any time"
echo ""

# Check if we're in nix environment
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME not set. Running via nix develop..."
    echo ""
    cd "$PROJECT_ROOT"
    exec nix develop --command "$0" "$@"
fi

# Check if emulator is running
if ! adb devices | grep -q "emulator"; then
    echo "❌ No emulator detected!"
    echo ""
    echo "Please start the emulator with GUI first:"
    echo ""
    echo "  # Option 1: Start emulator with GUI from nix develop"
    echo "  nix develop"
    echo "  \$ANDROID_HOME/emulator/emulator -avd talkback_test -writable-system -selinux permissive &"
    echo ""
    echo "  # Option 2: Use existing emulator if you have one running"
    echo ""
    exit 1
fi

echo "✓ Emulator detected"
echo ""

# Check if TalkBack is running
if adb shell ps -A | grep -q talkback; then
    echo "✓ TalkBack is running"
else
    echo "⚠️  TalkBack not detected - agent will still work but no announcements visible"
fi
echo ""

# Force stop and restart Sudoku app for clean state
echo "🎮 Launching Sudoku app (clean state)..."
adb shell am force-stop com.wonderback.sudoku.debug 2>/dev/null || true
sleep 1
adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity
sleep 2
echo ""

echo "🤖 Starting Tester Agent with ${DELAY}s demo delays..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Run the agent with debug delay
cd "$SCRIPT_DIR"
python3 tester_agent.py --debug-delay "$DELAY"

EXIT_CODE=$?
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ Demo complete! Agent successfully solved the puzzle!"
else
    echo "ℹ️  Demo complete! Agent attempted to solve (expected behavior for MVP)"
fi

echo ""
echo "To run again: $0 [delay-seconds]"
echo "Examples:"
echo "  $0 2.0    # 2 second delays (default)"
echo "  $0 3.0    # 3 second delays (slower, better for presenting)"
echo "  $0 1.0    # 1 second delays (faster demo)"
