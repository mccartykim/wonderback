# Automate TalkBack consent dialog using UI Automator
# This is needed because Android requires user consent for accessibility services
{ pkgs, androidSdk }:

pkgs.writeShellApplication {
  name = "enable-talkback-ui";
  runtimeInputs = [ androidSdk pkgs.coreutils ];
  text = ''
    echo "Opening TalkBack settings to enable service..."
    echo ""
    echo "Android requires user consent for accessibility services."
    echo "This script will:"
    echo "  1. Open Accessibility Settings"
    echo "  2. Navigate to TalkBack"
    echo "  3. Tap the toggle switch"
    echo "  4. Confirm the dialog"
    echo ""
    
    # Open accessibility settings directly to TalkBack
    adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
    sleep 2
    
    # Try to find and tap TalkBack using UI Automator
    echo "Looking for TalkBack in accessibility settings..."
    
    # Get screen bounds
    WIDTH=$(adb shell wm size | grep -oP '\d+x\d+' | cut -d'x' -f1)
    HEIGHT=$(adb shell wm size | grep -oP '\d+x\d+' | cut -d'x' -f2)
    
    # Scroll down to find TalkBack (it might not be visible initially)
    for i in {1..3}; do
      adb shell input swipe $((WIDTH/2)) $((HEIGHT*3/4)) $((WIDTH/2)) $((HEIGHT/4)) 300
      sleep 0.5
    done
    
    # Use UI Automator to find and click TalkBack
    # This is more reliable than hardcoded coordinates
    TALKBACK_BOUNDS=$(adb shell uiautomator dump /dev/tty 2>/dev/null | \
      grep -oP 'text="TalkBack"[^>]*bounds="\[\d+,\d+\]\[\d+,\d+\]"' | \
      grep -oP '\[\d+,\d+\]\[\d+,\d+\]' | head -1)
    
    if [ -n "$TALKBACK_BOUNDS" ]; then
      # Extract coordinates and calculate center
      X1=$(echo "$TALKBACK_BOUNDS" | grep -oP '^\[\K\d+')
      Y1=$(echo "$TALKBACK_BOUNDS" | grep -oP ',\K\d+(?=\])')
      X2=$(echo "$TALKBACK_BOUNDS" | grep -oP '\]\[\K\d+')
      Y2=$(echo "$TALKBACK_BOUNDS" | grep -oP ',\K\d+(?=\]$)')
      
      TAP_X=$(( (X1 + X2) / 2 ))
      TAP_Y=$(( (Y1 + Y2) / 2 ))
      
      echo "Found TalkBack at ($TAP_X, $TAP_Y)"
      adb shell input tap "$TAP_X" "$TAP_Y"
      sleep 2
      
      # Now we should be on TalkBack settings page
      # Look for the toggle switch (usually at top right)
      echo "Looking for toggle switch..."
      
      SWITCH_BOUNDS=$(adb shell uiautomator dump /dev/tty 2>/dev/null | \
        grep -oP 'class="android.widget.Switch"[^>]*bounds="\[\d+,\d+\]\[\d+,\d+\]"' | \
        grep -oP '\[\d+,\d+\]\[\d+,\d+\]' | head -1)
      
      if [ -n "$SWITCH_BOUNDS" ]; then
        X1=$(echo "$SWITCH_BOUNDS" | grep -oP '^\[\K\d+')
        Y1=$(echo "$SWITCH_BOUNDS" | grep -oP ',\K\d+(?=\])')
        X2=$(echo "$SWITCH_BOUNDS" | grep -oP '\]\[\K\d+')
        Y2=$(echo "$SWITCH_BOUNDS" | grep -oP ',\K\d+(?=\]$)')
        
        TAP_X=$(( (X1 + X2) / 2 ))
        TAP_Y=$(( (Y1 + Y2) / 2 ))
        
        echo "Found toggle at ($TAP_X, $TAP_Y)"
        adb shell input tap "$TAP_X" "$TAP_Y"
        sleep 2
        
        # Confirm dialog if it appears
        echo "Looking for confirmation dialog..."
        
        # Try to find "Allow" or "OK" button
        OK_BOUNDS=$(adb shell uiautomator dump /dev/tty 2>/dev/null | \
          grep -oP '(text="Allow"|text="OK"|resource-id="android:id/button1")[^>]*bounds="\[\d+,\d+\]\[\d+,\d+\]"' | \
          grep -oP '\[\d+,\d+\]\[\d+,\d+\]' | head -1)
        
        if [ -n "$OK_BOUNDS" ]; then
          X1=$(echo "$OK_BOUNDS" | grep -oP '^\[\K\d+')
          Y1=$(echo "$OK_BOUNDS" | grep -oP ',\K\d+(?=\])')
          X2=$(echo "$OK_BOUNDS" | grep -oP '\]\[\K\d+')
          Y2=$(echo "$OK_BOUNDS" | grep -oP ',\K\d+(?=\]$)')
          
          TAP_X=$(( (X1 + X2) / 2 ))
          TAP_Y=$(( (Y1 + Y2) / 2 ))
          
          echo "Found confirmation button at ($TAP_X, $TAP_Y)"
          adb shell input tap "$TAP_X" "$TAP_Y"
          sleep 2
          
          echo "✅ TalkBack should now be enabled!"
        else
          echo "⚠️  No confirmation dialog found - TalkBack might already be enabled"
        fi
      else
        echo "⚠️  Could not find toggle switch"
        echo "Please manually enable TalkBack in the settings screen"
      fi
    else
      echo "⚠️  Could not find TalkBack in accessibility settings"
      echo "Please manually:"
      echo "  1. Find TalkBack in the list"
      echo "  2. Tap it"
      echo "  3. Toggle the switch"
      echo "  4. Confirm the dialog"
    fi
    
    # Go back to home
    adb shell input keyevent KEYCODE_HOME
    
    echo ""
    echo "Verifying TalkBack status..."
    sleep 2
    
    if adb shell dumpsys accessibility | grep -q "Bound services:{.*TalkBack"; then
      echo "✅ TalkBack is now bound and running!"
    else
      echo "⚠️  TalkBack may not be fully enabled yet"
      echo "Check: adb shell dumpsys accessibility | grep 'Bound services'"
    fi
  '';
}
