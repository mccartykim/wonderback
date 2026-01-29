# TalkBack Testing Agents - Model Gym MVP

**Purpose**: Automated feedback loop for improving app accessibility using TalkBack agent testing.

---

## Agents

### 1. Tester Agent (`tester_agent.py`)

**Bead**: wonderback-69

**What it does:**
- Connects to emulator via ADB
- Reads UI accessibility tree
- Attempts to navigate and solve Sudoku using only TalkBack context
- Logs all interactions and failures
- Generates report of accessibility issues

**Usage:**
```bash
cd /home/kimb/projects/wonderback
python3 agents/tester_agent.py
```

**Output:**
- Console logs showing testing progress
- Exit code: 0 if solved, 1 if failed
- Failures logged to help Developer Agent

### 2. Developer Agent (`developer_agent.py`)

**Bead**: wonderback-70

**What it does:**
- Analyzes Tester Agent failure reports
- Identifies accessibility issues
- Suggests code improvements
- (MVP: Manual fixes) Rebuilds and retests

**Usage:**
```bash
# Analyze tester report
python3 agents/developer_agent.py <report.json>

# After manual fixes, rebuild and retest
python3 agents/developer_agent.py --rebuild-and-retest
```

---

## The Feedback Loop

```
┌──────────────────────────────────────────────────────┐
│  1. TESTER AGENT                                     │
│     - Attempts to solve Sudoku                       │
│     - Uses only accessibility tree                   │
│     - Logs failures                                  │
└──────────────────┬───────────────────────────────────┘
                   │ (failures)
                   ▼
┌──────────────────────────────────────────────────────┐
│  2. DEVELOPER AGENT                                  │
│     - Analyzes failures                              │
│     - Identifies accessibility issues                │
│     - Suggests improvements                          │
└──────────────────┬───────────────────────────────────┘
                   │ (manual fixes in MVP)
                   ▼
┌──────────────────────────────────────────────────────┐
│  3. HUMAN (MVP) / LLM (Future)                       │
│     - Reviews suggestions                            │
│     - Applies code changes                           │
│     - Improves accessibility                         │
└──────────────────┬───────────────────────────────────┘
                   │ (improved app)
                   ▼
┌──────────────────────────────────────────────────────┐
│  4. REBUILD & RETEST                                 │
│     - developer_agent --rebuild-and-retest           │
│     - Loops back to step 1                           │
└──────────────────────────────────────────────────────┘
```

**Goal**: Iterate until Tester Agent successfully solves the puzzle!

---

## Quick Start

### Prerequisites
1. Emulator running with root access
2. TalkBack enabled
3. Sudoku app installed

**Verify:**
```bash
adb devices  # Should show emulator
adb shell ps -A | grep talkback  # TalkBack process
adb shell ps -A | grep sudoku  # Sudoku if running
```

### Run First Test

```bash
# Make scripts executable
chmod +x agents/*.py

# Run Tester Agent
python3 agents/tester_agent.py

# Expected output:
# ╔═══════════════════════════════════════════════════════╗
# ║        TALKBACK TESTER AGENT - wonderback-69         ║
# ╚═══════════════════════════════════════════════════════╝
# [HH:MM:SS] [INFO] Launching Sudoku app...
# [HH:MM:SS] [INFO] Reading Sudoku grid state...
# [HH:MM:SS] [INFO] Grid: 30 filled, 51 empty, 51 editable
# ...
```

### Iterate on Failures

If the test fails (exit code 1):

```bash
# Agent will print failures like:
# ✗ Test FAILED: Agent could not solve Sudoku
# Failures: 3
#   - {'cell': 'R1C3: empty (editable)', 'reason': 'Could not tap cell'}
#   - {'cell': 'R2C5: empty (editable)', 'number': 4, 'reason': 'Could not select number'}
```

Then:

1. **Analyze** (Developer Agent does this):
   ```bash
   # Save tester output to file for analysis
   python3 agents/tester_agent.py > tester_report.txt 2>&1
   ```

2. **Fix** accessibility issues manually in `sudoku-test-app/src/main/java/com/wonderback/sudoku/`

3. **Rebuild and Retest**:
   ```bash
   python3 agents/developer_agent.py --rebuild-and-retest
   ```

4. **Repeat** until test passes!

---

## Features

### Tester Agent Features

- ✅ **UI Tree Parsing**: Extracts all Sudoku cells from accessibility hierarchy
- ✅ **Content Description Parsing**: Understands "Row X, column Y, value, status"
- ✅ **Touch Simulation**: Taps cells and buttons using calculated coordinates
- ✅ **Dialog Interaction**: Opens number picker and selects values
- ✅ **Logging**: Comprehensive logs for debugging
- ✅ **Verification**: Uses logcat to check solve result

### Developer Agent Features

- ✅ **Failure Analysis**: Identifies patterns in tester failures
- ✅ **Issue Classification**: Groups by type (interaction, dialog, tree)
- ✅ **Severity Ranking**: Critical vs high priority issues
- ✅ **Code Suggestions**: Provides specific Compose semantic fixes
- ✅ **Rebuild Workflow**: Automates rebuild and reinstall
- ✅ **Retest Trigger**: Automatically reruns Tester Agent

---

## Current Limitations (MVP)

### Tester Agent
- **Simplified solving**: Uses basic logic, not true Sudoku constraints
- **Limited retries**: Only attempts first 5 empty cells
- **No backtracking**: Doesn't undo incorrect moves

### Developer Agent
- **Manual fixes**: Doesn't auto-generate code changes (needs LLM integration)
- **Limited analysis**: Basic pattern matching on failure reasons
- **No code modification**: Provides suggestions, doesn't edit files

---

## Future Enhancements

### Tester Agent
- [ ] Full Sudoku solving algorithm
- [ ] Constraint validation before entering numbers
- [ ] Retry logic with backtracking
- [ ] Multiple solving strategies
- [ ] Performance metrics (time to solve, moves made)

### Developer Agent
- [ ] LLM integration for auto-code generation
- [ ] Direct code modification via Edit tool
- [ ] AST parsing for targeted fixes
- [ ] Git integration (branch, commit fixes)
- [ ] A/B testing of improvements
- [ ] Regression detection

### Integration
- [ ] Continuous loop (fully automated)
- [ ] Multi-app testing (beyond Sudoku)
- [ ] Parallel agent testing (multiple devices)
- [ ] Accessibility score tracking
- [ ] Report generation (HTML/PDF)

---

## Troubleshooting

### "Could not read grid state"
- **Check**: Is Sudoku app running in foreground?
- **Fix**: `adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity`

### "Could not tap cell"
- **Check**: Are bounds correct in UI dump?
- **Fix**: Verify cell has `.clickable { }` modifier in Compose

### "Could not select number"
- **Check**: Is number picker dialog open?
- **Debug**: `adb shell uiautomator dump && adb pull /sdcard/window_dump.xml /tmp/`
- **Fix**: Add `contentDescription` to all number buttons

### Emulator disconnected
```bash
adb devices
# If no devices, restart emulator or run:
adb kill-server && adb start-server
```

---

## Logging

Both agents log to stdout with timestamps:

```
[HH:MM:SS] [INFO] Message
[HH:MM:SS] [WARN] Warning message
[HH:MM:SS] [ERROR] Error message
[HH:MM:SS] [SUCCESS] Success message
```

**Pipe to file for analysis:**
```bash
python3 agents/tester_agent.py 2>&1 | tee test_run.log
```

**Check Sudoku app logs:**
```bash
adb logcat -s SudokuTestApp:I
```

---

## Architecture

```
agents/
├── tester_agent.py      # Navigates Sudoku via accessibility
├── developer_agent.py   # Analyzes failures and suggests fixes
└── README.md            # This file

Key Classes:
- TesterAgent: Main testing logic, UI parsing, interaction
  - SudokuCell: Data class for cell properties
- DeveloperAgent: Failure analysis, code suggestions, rebuild workflow

External Dependencies:
- ADB (Android Debug Bridge)
- UI Automator (for accessibility tree dumps)
- Sudoku App (target for testing)
- Python 3.x (subprocess, xml.etree, dataclasses)
```

---

**Beads**: wonderback-69 (Tester), wonderback-70 (Developer), wonderback-77 (Both)
**Last Updated**: 2026-01-29
**Status**: ✅ MVP Ready for Testing
