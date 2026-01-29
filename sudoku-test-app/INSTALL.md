# Sudoku Test App - Installation Guide

## Quick Start

The APK is pre-built and ready to install. Location:
```
/home/kimb/projects/wonderback/sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk
```

## Installation Methods

### Method 1: Direct ADB Install (Recommended)

```bash
# From the project root
cd /home/kimb/projects/wonderback

# Install to connected Android device/emulator
adb install sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk

# If the app is already installed, use -r to reinstall
adb install -r sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk
```

### Method 2: Install with Debugging

```bash
# Install with verbose logging
adb install -r -d sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk
```

## Verification

### Check if installed
```bash
adb shell pm list packages | grep wonderback.sudoku
```

Expected output:
```
package:com.wonderback.sudoku.debug
```

### Launch the app
```bash
adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity
```

## Rebuilding from Source

If you need to rebuild:

```bash
cd /home/kimb/projects/wonderback

# Using Nix (recommended)
nix develop --command gradle :sudoku-test-app:assembleDebug

# Or if already in nix shell
gradle :sudoku-test-app:assembleDebug
```

## Package Information

- **Package ID**: `com.wonderback.sudoku.debug`
- **Main Activity**: `com.wonderback.sudoku.MainActivity`
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)
- **APK Size**: ~7 MB
- **Build Time**: ~17 seconds (incremental)

## Uninstalling

```bash
adb uninstall com.wonderback.sudoku.debug
```

## Testing with TalkBack

### Enable TalkBack via ADB

```bash
# Enable TalkBack
adb shell settings put secure enabled_accessibility_services com.android.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1

# Verify TalkBack is running
adb shell dumpsys accessibility | grep -A 5 TalkBack
```

### Disable TalkBack

```bash
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0
```

### View Logs

```bash
# View app logs
adb logcat | grep -i sudoku

# View TalkBack logs
adb logcat | grep -i talkback

# View accessibility events
adb logcat | grep -i accessibility
```

## Troubleshooting

### ADB Device Not Found

```bash
# List connected devices
adb devices

# If no devices, check USB connection or emulator status
```

### Installation Failed

```bash
# If installation fails due to signature conflict
adb uninstall com.wonderback.sudoku.debug

# Then try installing again
adb install sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk
```

### App Crashes on Launch

```bash
# Check crash logs
adb logcat -s AndroidRuntime:E

# Check for missing permissions
adb shell dumpsys package com.wonderback.sudoku.debug | grep permission
```

## Next Steps

After installation:
1. Enable TalkBack on the device
2. Launch the Sudoku app
3. Test navigation using swipe gestures
4. Verify accessibility announcements
5. Test number input via the dialog
6. Test solve validation

See [README.md](README.md) for detailed testing scenarios.
