# Complete one-command demo that orchestrates everything
# This is the big impressive demo for coworkers!
{ pkgs, androidSdk, gradle, pythonEnv }:

pkgs.writeShellApplication {
  name = "talkback-demo";
  runtimeInputs = [ androidSdk pythonEnv pkgs.coreutils pkgs.jdk17 gradle pkgs.procps ];
  text = ''
    REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
    cd "$REPO"

    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_AVD_HOME="$HOME/.wonderback/avd"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export ANDROID_SDK="$ANDROID_HOME"
    export JAVA_HOME="${pkgs.jdk17}"
    mkdir -p "$ANDROID_AVD_HOME"

    DELAY="''${1:-1.5}"

    echo "╔═══════════════════════════════════════════════════════╗"
    echo "║    TALKBACK GESTURE DEMO - WOW YOUR COWORKERS!       ║"
    echo "╚═══════════════════════════════════════════════════════╝"
    echo ""
    echo "This will automatically:"
    echo "  1. Build TalkBack APK with gesture injection"
    echo "  2. Create AVD if needed"
    echo "  3. Start emulator if needed"
    echo "  4. Install APKs"
    echo "  5. Enable TalkBack"
    echo "  6. Start analysis server"
    echo "  7. Run gesture-based demo (''${DELAY}s delays)"
    echo ""
    echo "Watch for:"
    echo "  👁️  Green focus rectangle moving with TalkBack"
    echo "  🔊 TalkBack speaking (if audio enabled)"
    echo "  ✋ Real gesture injection (not adb tap!)"
    echo ""
    echo "Press Ctrl+C to cancel..."
    sleep 2
    echo ""

    # Step 1: Build TalkBack APK if missing
    echo "[1/6] Checking TalkBack APK..."
    if [ ! -f "$REPO/build/outputs/apk/phone/debug/wonderback-phone-debug.apk" ]; then
      echo "Building TalkBack APK..."
      echo "sdk.dir=$ANDROID_HOME" > local.properties
      gradle assemblePhoneDebug --no-daemon --warning-mode all
    else
      echo "✓ TalkBack APK exists"
    fi
    echo ""

    # Step 2: Build Sudoku APK if missing
    echo "[2/6] Checking Sudoku APK..."
    if [ ! -f "$REPO/sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk" ]; then
      echo "Building Sudoku APK..."
      echo "sdk.dir=$ANDROID_HOME" > local.properties
      gradle :sudoku-test-app:assembleDebug --no-daemon --warning-mode all
    else
      echo "✓ Sudoku APK exists"
    fi
    echo ""

    # Step 3: Create AVD if missing
    echo "[3/6] Checking AVD..."
    AVD_NAME="talkback_test"
    if ! "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null | grep -q "^$AVD_NAME$"; then
      echo "Creating AVD..."
      if [[ "$(uname -m)" == "arm64" ]]; then
        SYSTEM_IMAGE="system-images;android-34;google_apis;arm64-v8a"
        DEVICE="pixel_6"
      else
        SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"
        DEVICE="pixel_5"
      fi
      echo "no" | "$ANDROID_HOME/cmdline-tools/19.0/bin/avdmanager" \
        create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d "$DEVICE" --force
      echo "✓ AVD created"
    else
      echo "✓ AVD exists"
    fi
    echo ""

    # Step 4: Start emulator if not running
    echo "[4/6] Checking emulator..."
    if ! adb devices | grep -q "emulator"; then
      echo "Starting emulator..."
      "$ANDROID_HOME/emulator/emulator" \
        -avd "$AVD_NAME" \
        -writable-system \
        -selinux permissive \
        -no-boot-anim \
        -gpu swiftshader_indirect \
        &
      echo "Waiting for emulator to boot..."
      timeout=120
      count=0
      until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
        if [ $count -ge $timeout ]; then
          echo "❌ Timeout waiting for emulator"
          exit 1
        fi
        echo -n "."
        sleep 2
        count=$((count + 2))
      done
      echo ""
      echo "✓ Emulator ready"
      adb root
      sleep 2
    else
      echo "✓ Emulator already running"
      adb root 2>/dev/null || true
      sleep 1
    fi
    echo ""

    # Step 5: Install APKs and enable TalkBack
    echo "[5/6] Setting up device..."
    
    # Install TalkBack if needed
    TALKBACK_INSTALLED=false
    if ! adb shell pm list packages | grep -q "com.wonderback.talkback"; then
      echo "Installing TalkBack APK..."
      adb install -r "$REPO/build/outputs/apk/phone/debug/wonderback-phone-debug.apk"
      TALKBACK_INSTALLED=true
      echo "⚠️  Note: Installing APK disables accessibility service - will re-enable"
    else
      echo "✓ TalkBack installed"
    fi

    # Install Sudoku if needed
    if ! adb shell pm list packages | grep -q "com.wonderback.sudoku.debug"; then
      echo "Installing Sudoku APK..."
      adb install -r "$REPO/sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk"
    else
      echo "✓ Sudoku installed"
    fi

    # CRITICAL: Enable TalkBack AFTER APK install
    # Installing the TalkBack APK resets enabled_accessibility_services!
    echo ""
    echo "Enabling TalkBack accessibility service..."
    
    # Wait a moment if we just installed
    if [ "$TALKBACK_INSTALLED" = true ]; then
      echo "Waiting for APK install to complete..."
      sleep 2
    fi
    
    # Set all required settings (must be done AFTER install)
    # CRITICAL ORDER: accessibility_enabled FIRST, then service, then touch exploration
    # NOTE: Using wonderback namespace to avoid conflict with system TalkBack
    adb shell settings put secure accessibility_enabled 1
    adb shell settings put secure enabled_accessibility_services \
      com.wonderback.talkback/com.google.android.marvin.talkback.TalkBackService
    adb shell settings put secure touch_exploration_enabled 1
    
    # Enable the agent subsystem via SharedPreferences
    # AgentConfig defaults to isEnabled=false, need to set it to true
    echo "Enabling TalkBack agent subsystem..."
    
    # Create XML config file locally
    cat > /tmp/talkback_agent_config.xml << 'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="agent_enabled" value="true" />
</map>
EOF
    
    # Use run-as with debug build (now that we have unique namespace)
    adb push /tmp/talkback_agent_config.xml /data/local/tmp/talkback_agent_config.xml
    adb shell "run-as com.wonderback.talkback mkdir -p /data/data/com.wonderback.talkback/shared_prefs"
    adb shell "run-as com.wonderback.talkback cp /data/local/tmp/talkback_agent_config.xml /data/data/com.wonderback.talkback/shared_prefs/talkback_agent_config.xml"
    adb shell "run-as com.wonderback.talkback chmod 660 /data/data/com.wonderback.talkback/shared_prefs/talkback_agent_config.xml"
    
    # Restart TalkBack to pick up new config
    echo "Restarting TalkBack to apply agent configuration..."
    adb shell settings put secure enabled_accessibility_services null
    sleep 1
    adb shell settings put secure enabled_accessibility_services \
      com.wonderback.talkback/com.google.android.marvin.talkback.TalkBackService
    sleep 2
    
    # Give TalkBack time to start (longer if just installed)
    if [ "$TALKBACK_INSTALLED" = true ]; then
      echo "Waiting for TalkBack to start (first launch)..."
      sleep 5
    else
      sleep 3
    fi
    
    # Check if TalkBack is actually bound (not just enabled)
    if adb shell dumpsys accessibility | grep -q "Bound services:{.*TalkBack"; then
      echo "✓ TalkBack service is bound and running"
    else
      echo "⚠️  TalkBack is enabled but not bound - user consent required"
      echo ""
      echo "╔═══════════════════════════════════════════════════════╗"
      echo "║  MANUAL STEP: Enable TalkBack                        ║"
      echo "╚═══════════════════════════════════════════════════════╝"
      echo ""
      echo "Android requires user consent for accessibility services."
      echo "Please enable TalkBack manually on the emulator:"
      echo ""
      echo "  1. Settings app should be opening now..."
      
      # Open accessibility settings to make it easier
      adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
      sleep 1
      
      echo "  2. Find and tap 'TalkBack' in the list"
      echo "  3. Toggle the switch to ON"
      echo "  4. Tap 'Allow' or 'OK' in the confirmation dialog"
      echo ""
      echo "You should see a green focus rectangle appear!"
      echo ""
      echo -n "Press Enter when TalkBack is enabled..."
      read -r
      
      # Go back to home
      adb shell input keyevent KEYCODE_HOME
      sleep 1
    fi
    
    # Final verification
    echo ""
    echo "Verifying TalkBack status..."
    if adb shell dumpsys accessibility | grep -q "Bound services:{.*TalkBack"; then
      echo "✓ TalkBack service is bound and running"
      
      # Verify settings are correct
      ENABLED=$(adb shell settings get secure accessibility_enabled)
      TOUCH=$(adb shell settings get secure touch_exploration_enabled)
      
      if [ "$ENABLED" = "1" ] && [ "$TOUCH" = "1" ]; then
        echo "✓ TalkBack accessibility settings confirmed"
        echo "  You should see green focus rectangle when swiping on screen"
      else
        echo "⚠️  TalkBack settings may not be fully active"
        echo "  accessibility_enabled=$ENABLED, touch_exploration=$TOUCH"
      fi
    else
      echo "⚠️  TalkBack process not detected - visual feedback may not work"
      echo "  Demo will continue but you won't see green focus rectangle"
    fi
    echo ""

    # Step 6: Start analysis server in background
    echo "[6/8] Starting analysis server..."
    cd "$REPO/server"
    
    # Check if server already running
    if lsof -i :8080 >/dev/null 2>&1; then
      echo "✓ Server already running on port 8080"
    else
      echo "Starting server in background..."
      python3 main.py --port 8080 > /tmp/wonderback-server.log 2>&1 &
      SERVER_PID=$!
      echo "Server PID: $SERVER_PID"
      sleep 3
      
      if ps -p $SERVER_PID > /dev/null; then
        echo "✓ Server started successfully"
      else
        echo "⚠️  Server may have failed to start, check /tmp/wonderback-server.log"
      fi
    fi
    echo ""

    # Step 7: Setup ADB reverse port forwarding
    echo "[7/8] Setting up ADB port forwarding..."
    adb reverse tcp:8080 tcp:8080
    echo "✓ Device can reach server at localhost:8080"
    echo ""

    # Step 8: Launch Sudoku and run gesture demo
    echo "[8/8] Running gesture-based demo..."
    echo ""
    echo "Watch the emulator window to see:"
    echo "  👁️  GREEN FOCUS RECTANGLE moving with TalkBack!"
    echo "  ✋ Real gesture injection (AccessibilityService.dispatchGesture)"
    echo "  🔊 TalkBack speaking element descriptions"
    echo "  🎯 Agent navigating using only gestures (no tree access)"
    echo ""
    sleep 2

    echo "🎮 Launching Sudoku app..."
    adb shell am force-stop com.wonderback.sudoku.debug 2>/dev/null || true
    sleep 1
    adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity
    sleep 3
    echo ""

    echo "🤖 Starting Gesture Demo with ''${DELAY}s delays..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    cd "$REPO/agents"
    python3 gesture_demo.py --delay "$DELAY" --mode sudoku

    EXIT_CODE=$?
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    if [ $EXIT_CODE -eq 0 ]; then
      echo "✅ Gesture demo complete!"
    else
      echo "⚠️  Demo ended with errors (check output above)"
    fi

    echo ""
    echo "What you just saw:"
    echo "  ✓ TalkBack gestures injected via AccessibilityService API"
    echo "  ✓ Green focus rectangle showing real TalkBack navigation"
    echo "  ✓ Server-driven automation (not adb input tap)"
    echo "  ✓ This is how blind users navigate with TalkBack!"
    echo ""
    echo "To run again: nix run .#demo [delay-seconds]"
    echo "Examples:"
    echo "  nix run .#demo 2.5    # Slower (better for presenting)"
    echo "  nix run .#demo 1.0    # Faster"
    echo ""
    echo "To stop emulator: adb emu kill"
    echo "To stop server: pkill -f 'python3 main.py'"
  '';
}
