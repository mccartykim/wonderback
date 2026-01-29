# macOS Setup Notes (M1/M2)

Quick notes for running on Apple Silicon Macs.

## What's Different

**M1/M2 Macs use ARM architecture:**
- System image: `system-images;android-34;google_apis;arm64-v8a`
- Not `x86_64` (that's for Intel)

**The flake now includes both:**
```nix
abiVersions = [ "x86_64" "arm64-v8a" ];
```

So it works on Intel Macs, M1/M2 Macs, and Linux!

---

## Creating AVD on M1/M2

When you create the AVD (first time setup):

```bash
nix develop

# Create AVD with ARM image
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
  -n talkback_test \
  -k "system-images;android-34;google_apis;arm64-v8a" \
  -d "pixel_6"
```

**Key difference:** `arm64-v8a` instead of `x86_64`

---

## Everything Else is the Same

```bash
# Works exactly the same on M1/M2
nix run .#start-emulator
nix run .#demo
```

---

## Performance on M1/M2

**Actually better than Intel in many cases:**
- Native ARM execution (no translation)
- Hypervisor.framework is fast
- Metal graphics are smooth
- Lower power consumption

The demo should run beautifully on your M1 Mac! 🚀

---

## Troubleshooting

### "No system images available"
**Problem:** arm64-v8a image not downloaded

**Solution:**
```bash
# List available images
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list | grep arm64

# Install it (nix should have done this)
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "system-images;android-34;google_apis;arm64-v8a"
```

### "Emulator: ERROR: x86_64 emulation not supported"
**Problem:** Trying to use x86_64 image on M1

**Solution:** Recreate AVD with arm64-v8a (see above)

### Audio not working
**Problem:** macOS audio permissions

**Solution:**
- System Settings → Privacy & Security → Microphone
- Allow Terminal (if running from terminal)
- Or allow your IDE (if running from VSCode/etc)

---

## Why This Works

Android emulator on M1/M2:
1. Uses native ARM CPU (same as Android phones!)
2. Hypervisor.framework for virtualization
3. Metal for GPU acceleration
4. No translation needed (unlike x86_64)

**Result:** Fast, efficient, smooth demo experience.

---

**Created for M1/M2 Mac users**
**Works on:** macOS 11+ (Big Sur and later)
