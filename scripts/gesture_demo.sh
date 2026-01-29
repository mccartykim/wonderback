#!/usr/bin/env bash
set -euo pipefail

# Gesture Demo - Demonstrates TalkBack gesture navigation via server API
# This script sends gesture commands to the Android device through the server

SERVER="${1:-http://localhost:8080}"
DELAY="${2:-1.5}"

echo ""
echo "🚀 TalkBack Gesture Demo"
echo "   Server: $SERVER"
echo "   Delay: ${DELAY}s between gestures"
echo ""

# Get device token
echo "🔑 Getting device auth token..."
TOKEN=$(curl -s "$SERVER/device/all" | python3 -c "import sys, json; d=json.load(sys.stdin)[0]; print(d['auth_token'])")

if [ -z "$TOKEN" ]; then
    echo "❌ No devices registered. Please run the demo setup first."
    exit 1
fi

echo "✅ Connected to device (token: ${TOKEN:0:8}...)"
echo ""

# Execute a gesture
execute_gesture() {
    local gesture=$1
    local description=$2
    
    echo "  $description"
    echo -n "  🎯 $gesture..."
    
    result=$(curl -s -X POST "$SERVER/skill/execute" \
        -H "Content-Type: application/json" \
        -H "X-Agent-Token: $TOKEN" \
        -d "{\"skill_name\":\"$gesture\",\"parameters\":{},\"timeout_ms\":5000}")
    
    success=$(echo "$result" | python3 -c "import sys, json; print(json.load(sys.stdin)['success'])")
    elapsed=$(echo "$result" | python3 -c "import sys, json; print(json.load(sys.stdin)['elapsed_ms'])")
    
    if [ "$success" = "True" ]; then
        echo " ✅ (${elapsed}ms)"
    else
        message=$(echo "$result" | python3 -c "import sys, json; print(json.load(sys.stdin)['message'])")
        echo " ❌ $message"
    fi
    
    sleep "$DELAY"
}

# Sudoku navigation demo
echo "📱 Sudoku Navigation Demo"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

execute_gesture "swipe_right" "Navigate to next element"
execute_gesture "swipe_right" "Continue navigating"
execute_gesture "swipe_right" "Keep exploring"
execute_gesture "swipe_left" "Go back one element"
execute_gesture "swipe_right" "Forward again"
execute_gesture "double_tap" "Activate current element"

echo ""
echo "✨ Demo complete"
echo ""
