# Build Issues and Fixes

## Current Status

**All gesture fixes are committed to master:**
- ✅ `a4ee0aa7` - Main thread dispatch fix (THE KEY FIX)
- ✅ `02b65e0a` - Gesture result checking
- ✅ `0cbc9111` - Demo ANR fix
- ✅ `54968eb5` - Gradle 8 compatibility (partial)

**Build system issue:**
The gradle build fails due to Android Gradle Plugin compatibility with Gradle 8.14.3:
```
Cannot use @TaskAction annotation on method IncrementalTask.taskAction$gradle_core() 
because interface org.gradle.api.tasks.incremental.IncrementalTaskInputs is not a 
valid parameter to an action method.
```

## The Key Fix

**Root cause:** `AccessibilityService.dispatchGesture()` was being called from an IO coroutine thread, but Android requires it on the main thread.

**Fix:** Wrapped `dispatchGesture()` in `withContext(Dispatchers.Main)` at:
`agent/src/main/java/com/google/android/accessibility/talkback/agent/gesture/GestureController.kt:270`

## To Test the Fix

### Option 1: Quick Test with Existing APK (RECOMMENDED)
The existing APK at `build/outputs/apk/phone/debug/wonderback-phone-debug.apk` (from 16:49) doesn't have the main thread fix, but you can manually patch and test:

**Use adb to manually trigger gestures on main thread:**
```bash
# The device is already running TalkBack with the old APK
# The server and polling infrastructure works
# Just need to test if main thread dispatch works

# You can verify the fix by checking if the Android logs show
# gesture dispatch succeeding instead of failing
```

### Option 2: Fix Build System (IN PROGRESS)
Updated AGP to 8.2.0 but now all submodules need namespace declarations added to their build.gradle files. This is a larger migration.

### Option 3: Use Android Studio
1. Open project in Android Studio
2. Let it sync and fix gradle issues automatically
3. Build > Build Bundle(s) / APK(s) > Build APK(s)
4. Install: `adb install -r build/outputs/apk/phone/debug/wonderback-phone-debug.apk`

### Option 3: Use Older Gradle
```bash
# If you have gradle wrapper
./gradlew assemblePhoneDebug
```

## Expected Result

Once rebuilt with the main thread fix:
- Gestures should actually dispatch (no more "Failed to dispatch gesture" errors)
- Green focus rectangle should move on screen
- TalkBack should speak element descriptions
- Demo should show visible navigation

## Stale APK Issue

The demo script at `nix/packages/demo.nix` checks if APK exists and skips rebuild:
```bash
if [ ! -f "$REPO/build/outputs/apk/phone/debug/wonderback-phone-debug.apk" ]; then
  # Build...
else
  echo "✓ TalkBack APK exists"  # Uses stale APK!
fi
```

To force rebuild: `rm build/outputs/apk/phone/debug/wonderback-phone-debug.apk`
