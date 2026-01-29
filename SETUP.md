# TalkBack Agent Testing Environment - Complete Setup Guide

**Purpose**: Reproducible setup for headless Android emulator with TalkBack accessibility testing for Model Gym MVP.

**Status**: ✅ Fully working as of 2026-01-29

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Detailed Setup](#detailed-setup)
4. [Verification](#verification)
5. [Troubleshooting](#troubleshooting)
6. [Architecture](#architecture)

---

## Prerequisites

### Required Software
- **Nix package manager** with flakes enabled
- **Git** for repository access
- **KVM** (for hardware acceleration on Linux)
- **~10GB disk space** for Android SDK and system images

### System Requirements
- x86_64 Linux (tested on NixOS)
- 8GB+ RAM recommended
- Internet connection for initial setup

---

## Quick Start

```bash
# 1. Clone and enter repository
cd /home/kimb/projects/wonderback

# 2. Enter Nix development environment (auto-installs everything)
nix develop

# 3. Build APKs
gradle assemblePhoneDebug --no-daemon
gradle :sudoku-test-app:assembleDebug --no-daemon

# 4. Create Android Virtual Device
echo 'no' | avdmanager create avd -n talkback_test \
  -k 'system-images;android-34;google_apis;x86_64' -d pixel_5 --force

# 5. Start emulator with ROOT-ENABLING FLAGS (critical!)
emulator @talkback_test \
  -no-window \
  -no-audio \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  -writable-system \
  -selinux permissive &

# 6. Wait for device
adb wait-for-device

# 7. Enable root and install APKs
adb root
adb install -r build/outputs/apk/phone/debug/wonderback-phone-debug.apk
adb install -r sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk

# 8. Enable TalkBack (with root)
adb shell settings put secure enabled_accessibility_services \
  com.android.talkback/com.google.android.accessibility.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure touch_exploration_enabled 1

# 9. Launch Sudoku app
adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity

# 10. Verify everything works
adb shell uiautomator dump
adb shell cat /sdcard/window_dump.xml | grep -o 'text="[^"]*"' | head -10
```

**You're ready!** The emulator is running with TalkBack enabled and Sudoku app installed.

---

## Detailed Setup

### 1. Nix Environment Configuration

The `flake.nix` defines our reproducible development environment:

```nix
# Key configuration in flake.nix
androidComposition = pkgs.androidenv.composeAndroidPackages {
  buildToolsVersions = [ "34.0.0" ];
  platformVersions = [ "34" ];
  includeNDK = true;
  ndkVersions = [ "21.4.7075529" ];
  includeEmulator = true;  # For headless testing
  includeSystemImages = true;  # For AVD
  systemImageTypes = [ "google_apis" "google_apis_playstore" ];
  abiVersions = [ "x86_64" ];  # KVM acceleration
  includeSources = false;
};
```

**Why this configuration:**
- `includeEmulator = true`: Enables headless Android Emulator
- `systemImageTypes = [ "google_apis" ... ]`: Includes both production images
- `abiVersions = [ "x86_64" ]`: Uses KVM for performance

### 2. Building the APKs

#### TalkBack Agent APK (32MB)

```bash
# Full build with APK generation
nix run .#build

# Or just compile check (faster, no APK)
nix run .#build -- --check
```

**Output**: `build/outputs/apk/phone/debug/wonderback-phone-debug.apk`

**What it includes:**
- TalkBack accessibility service
- Custom agent module for LLM integration
- All accessibility utilities
- Braille modules disabled (wonderback-49)

#### Sudoku Test App (6.8MB)

```bash
gradle :sudoku-test-app:assembleDebug --no-daemon
```

**Output**: `sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk`

**Features:**
- 9x9 Sudoku grid with accessibility content descriptions
- Number picker for cell input
- Solve button with validation
- **Comprehensive logging** for agent tracking (see below)

### 3. Android Virtual Device (AVD) Setup

#### Create AVD

```bash
avdmanager create avd \
  -n talkback_test \
  -k 'system-images;android-34;google_apis;x86_64' \
  -d pixel_5 \
  --force
```

**Configuration:**
- **Name**: `talkback_test`
- **System Image**: `android-34;google_apis;x86_64`
- **Device**: Pixel 5 (1080x2340)
- **Location**: `~/.android/avd/talkback_test.avd/`

#### Optional: Custom config.ini settings

```bash
# Enable hardware keyboard
echo 'hw.keyboard=yes' >> ~/.android/avd/talkback_test.avd/config.ini

# Disable audio input (headless)
echo 'hw.audioInput=no' >> ~/.android/avd/talkback_test.avd/config.ini
```

### 4. Starting the Emulator

#### 🔑 **CRITICAL: Root-Enabling Flags**

The key breakthrough was discovering emulator flags that enable root access on production images:

```bash
emulator @talkback_test \
  -no-window \
  -no-audio \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  -writable-system \          # ← Enables root after adb remount
  -selinux permissive \       # ← Sets SELinux to permissive mode
  > /tmp/emulator.log 2>&1 &
```

**Why these flags:**
- `-no-window`: Headless mode (no GUI)
- `-no-audio`: Disable audio (not needed for testing)
- `-gpu swiftshader_indirect`: Software GPU rendering
- `-no-boot-anim`: Skip boot animation (faster startup)
- **`-writable-system`**: Makes system partition writable (enables root operations)
- **`-selinux permissive`**: Disables SELinux enforcement (allows root shell)

**Boot time**: ~30-60 seconds

#### Verify Boot Completion

```bash
# Wait for device
adb wait-for-device

# Check boot status
adb shell getprop sys.boot_completed  # Should output: 1

# Verify root access works
adb root
adb shell whoami  # Should output: root
```

### 5. Installing APKs

```bash
# Install TalkBack
adb install -r build/outputs/apk/phone/debug/wonderback-phone-debug.apk

# Install Sudoku
adb install -r sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk

# Verify installations
adb shell pm list packages | grep -E 'talkback|sudoku'
# Expected output:
# package:com.android.talkback
# package:com.wonderback.sudoku.debug
```

### 6. Enabling TalkBack

**With root access** (enabled by emulator flags), we can programmatically enable TalkBack:

```bash
# Enable accessibility service
adb shell settings put secure enabled_accessibility_services \
  com.android.talkback/com.google.android.accessibility.talkback.TalkBackService

# Enable accessibility framework
adb shell settings put secure accessibility_enabled 1

# Enable touch exploration
adb shell settings put secure touch_exploration_enabled 1

# Verify TalkBack is running
adb shell ps -A | grep talkback
# Should show: com.google.android.marvin.talkback
```

**Why this works:**
- Official SDK system images are production builds (no `adb root` normally)
- The `-writable-system -selinux permissive` flags enable root access
- With root, we can modify secure settings without GUI interaction
- TalkBack process starts automatically when configured

---

## Verification

### Check Accessibility Tree

```bash
# Dump UI hierarchy
adb shell uiautomator dump

# View accessible elements
adb shell cat /sdcard/window_dump.xml | grep -o 'content-desc="[^"]*"' | head -20
```

**Expected output** (when Sudoku is open):
```
content-desc="You have to solve this sudoku"
content-desc="I am a blind/low-vision user using a screen reader. Help me solve this puzzle."
content-desc="Row 1, column 1, 5, given"
content-desc="Row 1, column 2, 3, given"
content-desc="Row 1, column 3, empty, editable"
...
```

### Test Touch Interaction

```bash
# Simulate tap on center of screen
adb shell input tap 540 1000

# Check Sudoku logs for interaction
adb logcat -d -s SudokuTestApp:I | tail -10
```

**Expected log output:**
```
Cell clicked: row=2, col=2, value=empty, isGiven=false
Opening number picker for editable cell
Number picker dialog shown for cell: row=2, col=2
```

### Check TalkBack Logs

```bash
adb logcat -d -s TalkBack:I TalkBackService:I | tail -20
```

### Known Quirk: Accessibility Settings Show 0

When checking accessibility settings in headless emulator:
```bash
adb shell settings get secure accessibility_enabled
# Returns: 0 (despite TalkBack running!)

adb shell settings get secure touch_exploration_enabled
# Returns: 0 (despite touch exploration working!)
```

**This is expected behavior** and does not affect functionality:
- ✅ TalkBack process runs correctly
- ✅ Accessibility tree works perfectly
- ✅ Agent testing fully functional
- ✅ Touch events processed
- ❌ Settings flags show 0 (cosmetic only)

**Why it happens**: Root-enabled activation bypasses AccessibilityManagerService flag update logic. The service binds directly without triggering the state machine that updates these informational flags.

**Solution**: Ignore the flag values. Use these commands to verify functionality instead:
```bash
# Check if TalkBack process is running
adb shell ps -A | grep talkback

# Check if accessibility tree works
adb shell uiautomator dump
adb shell ls -lh /sdcard/window_dump.xml

# Check accessibility service status
adb shell dumpsys accessibility | grep -A 10 TalkBack
```

**See also**: `ACCESSIBILITY_SETTINGS_INVESTIGATION.md` for detailed analysis.

---

## Troubleshooting

### Emulator Won't Start

**Symptom**: Emulator command hangs or crashes

**Solutions**:
1. Check KVM is available: `ls /dev/kvm`
2. Try software rendering: Add `-gpu swiftshader_indirect`
3. Check logs: `tail -f /tmp/emulator.log`
4. Kill existing emulators: `pkill -9 emulator`

### No Root Access

**Symptom**: `adb root` returns "adbd cannot run as root in production builds"

**Solution**: You forgot the critical flags! Restart emulator with:
```bash
emulator @talkback_test -writable-system -selinux permissive ...
```

### TalkBack Not Running

**Symptom**: No TalkBack process when checking `ps -A | grep talkback`

**Solutions**:
1. Verify it's installed: `adb shell pm list packages | grep talkback`
2. Check settings: `adb shell settings get secure enabled_accessibility_services`
3. Force-stop and restart: `adb shell am force-stop com.android.talkback`
4. Reboot emulator: `adb reboot`

### APK Installation Fails

**Symptom**: `adb install` returns error

**Solutions**:
1. Check device is connected: `adb devices`
2. Verify APK exists: `ls -lh build/outputs/apk/phone/debug/*.apk`
3. Try reinstall: `adb install -r <apk>` (the `-r` flag replaces existing)
4. Clear app data first: `adb shell pm clear <package>`

### UI Hierarchy Empty

**Symptom**: `uiautomator dump` returns empty XML

**Solutions**:
1. Ensure app is in foreground: `adb shell dumpsys window windows | grep mCurrentFocus`
2. Launch Sudoku: `adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity`
3. Wait a few seconds for UI to render

### Accessibility Settings Show 0

**Symptom**: `settings get secure accessibility_enabled` returns 0, but TalkBack works fine

**This is NOT a problem!** See "Known Quirk: Accessibility Settings Show 0" section above. This is expected behavior for headless emulators with root-enabled accessibility.

**Verification** (to confirm everything works):
```bash
# 1. Check TalkBack is running
adb shell ps -A | grep talkback
# Should show: com.google.android.marvin.talkback

# 2. Verify accessibility tree works
adb shell uiautomator dump && adb shell wc -l /sdcard/window_dump.xml
# Should show: hundreds of lines

# 3. Check for content descriptions
adb shell cat /sdcard/window_dump.xml | grep -c 'content-desc='
# Should show: multiple matches

# 4. Run investigation script
python3 investigate_accessibility.py
```

If all verification steps pass, you're good! The 0 values are cosmetic only.

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────┐
│                     Nix Environment                      │
│  (Reproducible: Android SDK, Emulator, Gradle, Python)  │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│              Headless Android Emulator                   │
│  (Android 14, API 34, x86_64, root via flags)           │
│                                                          │
│  ┌────────────────────┐  ┌────────────────────┐        │
│  │  TalkBack Service  │  │   Sudoku Test App  │        │
│  │  (Accessibility)   │  │  (Target for       │        │
│  │                    │  │   agent testing)   │        │
│  └────────────────────┘  └────────────────────┘        │
│            │                       │                     │
│            └───────┬───────────────┘                     │
│                    ▼                                     │
│         Accessibility Framework                          │
│         (UI Tree, Events, etc.)                          │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼ (via ADB)
┌─────────────────────────────────────────────────────────┐
│                    Agent Testing                         │
│  - Read UI hierarchy (uiautomator dump)                 │
│  - Simulate touch events (adb shell input tap)          │
│  - Check logs (adb logcat)                              │
│  - Tester Agent: Navigate & solve Sudoku                │
│  - Developer Agent: Improve accessibility                │
└─────────────────────────────────────────────────────────┘
```

### Logging System

The Sudoku app has comprehensive logging for tracking agent behavior:

**Lifecycle Events:**
```
╔═══════════════════════════════════════════════════════╗
║   SUDOKU TEST APP - TALKBACK AGENT TESTING           ║
╚═══════════════════════════════════════════════════════╝
MainActivity.onCreate() called
MainActivity.onStart() - App visible to user
MainActivity.onResume() - App ready for interaction
```

**User Interactions:**
```
Cell clicked: row=2, col=3, value=empty, isGiven=false
Opening number picker for editable cell
Number selected: 7 for cell row=2, col=3 (was: empty)
```

**Solve Button:**
```
=== Solve button clicked ===
Grid status: 45 filled, 36 empty
✗ Puzzle not solved (incomplete or incorrect)
Found 3 incorrect cells:
  Row 5, Col 4: has 3, should be 8
```

**Access logs:**
```bash
# Real-time logs
adb logcat -s SudokuTestApp:I

# Recent logs
adb logcat -d -s SudokuTestApp:I | tail -50
```

### Key Files

**Nix Configuration:**
- `flake.nix` - Development environment definition
- `flake.lock` - Locked dependency versions

**Build System:**
- `build.gradle` - Root build configuration (AGP 8.3.0)
- `shared.gradle` - Shared Android module settings
- `settings.gradle` - Module inclusion

**TalkBack Module:**
- `talkback/build.gradle` - TalkBack module configuration
- `talkback/src/main/java/...` - TalkBack source code
- `build/outputs/apk/phone/debug/` - Built APK location

**Sudoku Test App:**
- `sudoku-test-app/build.gradle` - App module configuration
- `sudoku-test-app/src/main/java/com/wonderback/sudoku/` - App source
- `sudoku-test-app/src/main/res/` - UI resources

**Documentation:**
- `SETUP.md` - This file
- `README.md` - Project overview
- `.beads/` - Issue tracking database

---

## Next Steps

### For Developers

1. **Build Tester Agent** (wonderback-69):
   - Read UI hierarchy via `uiautomator dump`
   - Parse accessible elements (content descriptions)
   - Navigate Sudoku grid using touch simulation
   - Attempt to solve puzzle using only TalkBack context

2. **Build Developer Agent** (wonderback-70):
   - Receive failure reports from Tester Agent
   - Analyze accessibility issues (missing descriptions, poor focus order, etc.)
   - Modify Sudoku app code to improve accessibility
   - Rebuild and reinstall APK
   - Trigger Tester Agent to retry

3. **Iterate Feedback Loop**:
   - Tester tries → fails → Developer improves → Tester retries
   - Continue until Tester successfully navigates and solves puzzle
   - Document accessibility improvements

### For Testing

**Verify Manual Navigation:**
```bash
# 1. Get list of empty cells
adb shell uiautomator dump
adb shell cat /sdcard/window_dump.xml | grep 'empty.*editable'

# 2. Find clickable element bounds
# Parse XML to get coordinates

# 3. Tap cell
adb shell input tap X Y

# 4. Check if picker opened
adb logcat -d -s SudokuTestApp:I | grep "Number picker"

# 5. Select number (tap button in picker)
adb shell input tap X Y

# 6. Verify number was entered
adb shell uiautomator dump
# Check XML for updated cell value
```

---

## Credits

**Built with insights from:**
- nixpkgs androidenv source code
- Android Emulator documentation
- TalkBack accessibility best practices

**Key Breakthrough**: Emulator flags `-writable-system -selinux permissive` enable root access on production system images, eliminating the need for custom AOSP builds.

---

**Last Updated**: 2026-01-29
**Status**: ✅ Production Ready
**Beads**: wonderback-75 (testing), wonderback-76 (documentation)
