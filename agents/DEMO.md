# TalkBack Tester Agent - Demo Mode 🎬

**Visual demo with GUI window and TalkBack announcements visible!**

This demo shows the agent in action solving Sudoku using only accessibility information, perfect for presentations and "wow factor" demos.

---

## Quick Start (2 Commands)

### Option 1: Using Nix (Recommended)
```bash
# 1. Start emulator with GUI
nix run .#start-emulator

# 2. Run demo (agent will interact with visible GUI)
nix run .#demo
```

### Option 2: Direct Scripts
```bash
# 1. Start emulator with GUI
./agents/start_emulator_gui.sh

# 2. Run demo (agent will interact with visible GUI)
./agents/demo.sh
```

**That's it!** Watch the GUI window to see:
- 🖱️ Agent tapping cells
- 📱 Number picker dialogs opening
- 🗣️ TalkBack announcements (bottom of screen)
- ✅ Numbers being entered
- 🤖 Real-time logs showing what the agent "sees"

---

## What You'll See

### In the Terminal
```
╔═══════════════════════════════════════════════════════════╗
║        TALKBACK TESTER AGENT - wonderback-69             ║
╚═══════════════════════════════════════════════════════════╝

🎬 Demo mode enabled with 2.0s delays

Launching Sudoku app...

============================================================
STARTING SOLVE ATTEMPT
============================================================
Reading Sudoku grid state...
Dumping UI hierarchy...
Grid: 0 filled, 81 empty, 51 editable
Found 51 empty cells to fill

Attempt 1/5: Filling R1C3: empty (editable)
Tapping R1C3: empty (editable) at (310, 476)
[DEMO] Cell tapped, waiting 2.0s for TalkBack...
Attempting to select number 5
Found number 5 button at (540, 1059)
[DEMO] Number 5 selected, waiting 2.0s...
✓ Entered 5 in R1C3: empty (editable)
```

### In the GUI Window
- **Sudoku Grid** - 9x9 grid with cells highlighting on tap
- **Number Picker Dialog** - Appears when agent taps a cell
- **TalkBack Announcements** - Black bar at bottom showing what TalkBack says:
  - "Row 1, column 3, empty, editable"
  - "Number 5 button"
  - "5"
  - etc.

---

## Demo Controls

### Adjust the Speed

**Using Nix:**
```bash
# Slower (3 second delays - better for presentations)
nix run .#demo 3.0

# Default (2 second delays - good balance)
nix run .#demo

# Faster (1 second delays - quick demo)
nix run .#demo 1.0
```

**Using Scripts:**
```bash
# Slower (3 second delays - better for presentations)
./agents/demo.sh 3.0

# Default (2 second delays - good balance)
./agents/demo.sh

# Faster (1 second delays - quick demo)
./agents/demo.sh 1.0

# No delays (full speed, hard to follow)
python3 agents/tester_agent.py
```

### Stop the Demo
Press `Ctrl+C` at any time

### Stop the Emulator
```bash
adb emu kill
```

---

## Setup Instructions

### Prerequisites
1. **Nix environment** - Run `nix develop` from project root
2. **AVD created** - See SETUP.md if you don't have `talkback_test` AVD
3. **TalkBack installed** - See SETUP.md for installation
4. **Sudoku app installed** - See sudoku-test-app/INSTALL.md

### First Time Setup

```bash
# 1. Enter nix environment (if not already in)
nix develop

# 2. Verify AVD exists
$ANDROID_HOME/emulator/emulator -list-avds
# Should show: talkback_test

# 3. Start emulator with GUI
./agents/start_emulator_gui.sh
# Wait ~60 seconds for boot

# 4. Install apps (if not already installed)
adb install talkback/build/outputs/apk/phone/debug/talkback-phone-debug.apk
adb install sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk

# 5. Enable TalkBack (one-time setup)
# See SETUP.md for enabling TalkBack with root access

# 6. Run demo!
./agents/demo.sh
```

---

## Troubleshooting

### "No emulator detected"
**Problem:** Emulator not running or not accessible via ADB

**Solution:**
```bash
# Check if emulator is running
adb devices

# If no devices, start emulator
./agents/start_emulator_gui.sh

# If emulator shows but says "offline", wait a bit longer
# Emulator takes 30-60 seconds to fully boot
```

### "TalkBack not detected"
**Problem:** TalkBack service not running

**Note:** The agent will still work and you'll see it interact with the app, but TalkBack announcements won't be visible.

**Solution:**
```bash
# Check if TalkBack is running
adb shell ps -A | grep talkback

# If not, enable it (see SETUP.md)
# Or continue anyway - you'll still see the visual interaction
```

### "Sudoku app not found"
**Problem:** Sudoku test app not installed

**Solution:**
```bash
# Build the app
nix run .#build

# Install it
cd sudoku-test-app
gradle assembleDebug
adb install build/outputs/apk/debug/sudoku-test-app-debug.apk
```

### Emulator window is black
**Problem:** Display rendering issue

**Solution:**
```bash
# Stop and restart with software rendering
adb emu kill
./agents/start_emulator_gui.sh
```

### Agent is too fast/slow
**Problem:** Default delay doesn't match your preference

**Solution:**
```bash
# Adjust the delay
./agents/demo.sh 3.0    # Slower (3 seconds)
./agents/demo.sh 1.0    # Faster (1 second)
./agents/demo.sh 0.5    # Very fast (0.5 seconds)
```

---

## Demo Script Walkthrough

### 1. start_emulator_gui.sh
```bash
# Checks for nix environment
# Verifies AVD exists
# Starts emulator with GUI (not headless)
# Enables root access
# Waits for boot to complete
```

### 2. demo.sh [delay]
```bash
# Checks emulator is running
# Launches Sudoku app (clean state)
# Runs tester_agent.py with --debug-delay
# Shows real-time progress
```

### 3. tester_agent.py --debug-delay N
```bash
# Reads accessibility tree
# Finds Sudoku cells
# Taps cells (with delay)
# Opens number picker (with delay)
# Selects numbers (with delay)
# Generates detailed report
```

---

## Presentation Tips

### For Maximum Wow Factor

1. **Start with emulator visible**
   - Have the Sudoku app open and visible
   - Position terminal and emulator side-by-side

2. **Explain what you're showing**
   - "This agent uses ONLY accessibility information"
   - "Same info a blind user with TalkBack gets"
   - "No computer vision, no screenshots"

3. **Run with 3 second delays**
   ```bash
   ./agents/demo.sh 3.0
   ```
   - Gives audience time to read announcements
   - Shows clear cause and effect

4. **Point out key moments**
   - When agent "reads" the cell description
   - When TalkBack announces what's focused
   - When number is entered

5. **Show the logs side-by-side**
   - Terminal shows what agent "thinks"
   - GUI shows what's actually happening
   - TalkBack shows what's announced

### Demo Flow

```
Terminal                          GUI Window
--------                          ----------
"Reading Sudoku grid..."   -->    (UI hierarchy dump happening)
"Found 51 empty cells"     -->    (Grid visible)
"Tapping R1C3 at (310,476)" -->   [Cell highlights]
"[DEMO] waiting 2.0s..."    -->   [TalkBack: "Row 1, column 3, empty"]
"Attempting select 5"       -->   [Number picker opens]
"Found number 5 button"     -->   [Button 5 highlights]
"[DEMO] Number 5 selected"  -->   [TalkBack: "Number 5 button"]
"✓ Entered 5 in R1C3"       -->   [Dialog closes, 5 appears in cell]
```

---

## What Makes This Impressive

### For Accessibility Teams
- **Automation-first approach** to accessibility testing
- **Feedback loop** where agents improve their own test environment
- **Real TalkBack usage** not simulated

### For Engineering Teams
- **No computer vision** needed - uses Android's accessibility APIs
- **Reproducible** - runs in nix environment
- **Fast iteration** - agents can test continuously

### For Product Teams
- **Visual proof** that accessibility works
- **Quantifiable metrics** (5/5 cells filled, 0 failures)
- **Demo-able progress** in 2 minutes

---

## Files

```
agents/
├── demo.sh                  # Main demo script (run this!)
├── start_emulator_gui.sh    # Start emulator with GUI
├── tester_agent.py          # The agent itself (with --debug-delay support)
├── run_tester.sh            # Original headless wrapper
├── developer_agent.py       # Analyzes failures (not used in demo)
├── README.md                # Full agent documentation
└── DEMO.md                  # This file
```

---

## Advanced Usage

### Run with custom Python flags
```bash
python3 -u agents/tester_agent.py --debug-delay 2.0
# -u for unbuffered output (real-time logs)
```

### Record the demo
```bash
# Terminal recording with asciinema
asciinema rec demo.cast
./agents/demo.sh 2.0
# Press Ctrl+D when done

# Screen recording with OBS/SimpleScreenRecorder
# Record both terminal and emulator window
```

### Multiple runs
```bash
# Run 3 demos in a row with different delays
for delay in 3.0 2.0 1.0; do
    echo "Demo with ${delay}s delay..."
    ./agents/demo.sh $delay
    echo "Press Enter for next demo..."
    read
done
```

---

## Next Steps After Demo

### For Development
- See `../RETROSPECTIVE.md` for lessons learned
- See `../MODEL_GYM_STATUS.md` for project status
- See `README.md` for full agent documentation

### For Testing
- Test with broken accessibility (remove content descriptions)
- Run developer agent to analyze failures
- Iterate feedback loop

### For Integration
- Add to CI/CD pipeline
- Run on multiple devices
- Track accessibility metrics over time

---

**Created:** 2026-01-29
**Purpose:** Demo Mode for TalkBack Tester Agent
**Wow Factor:** ⭐⭐⭐⭐⭐

Enjoy the demo! 🎬🤖
