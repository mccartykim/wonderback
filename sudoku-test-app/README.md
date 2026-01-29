# Sudoku Test App - TalkBack Accessibility Testing

This is a minimal Sudoku application designed specifically for testing TalkBack accessibility features. The app prioritizes accessibility over game logic and serves as a testing ground for the TalkBack agent.

## Purpose

This app is part of the Model Gym MVP (wonderback-30) for testing TalkBack agents. It provides a realistic UI testing scenario with:

- Complex grid navigation
- Interactive elements requiring precise input
- State management (editable vs given cells)
- Modal dialogs (number picker)
- Result feedback (solve validation)

## Features

### UI Components
- **Header**: "You have to solve this sudoku"
- **User Prompt**: Brief note indicating blind/low-vision user context
- **9x9 Sudoku Grid**: Partially filled puzzle with editable cells
- **Solve Button**: Validates the completed puzzle
- **Number Picker Dialog**: Accessible number selection (1-9 + Clear)

### Accessibility Features

The app is built with **accessibility-first** design:

1. **Comprehensive Content Descriptions**
   - Each cell announces: position (row/column), value, and status (given/editable)
   - Example: "Row 1, column 3, empty, editable"

2. **Logical Focus Order**
   - Grid navigates row-by-row, left-to-right
   - Natural reading order for screen reader users

3. **Role Semantics**
   - Buttons marked with proper roles
   - Headings identified for screen readers
   - Disabled state for non-editable (given) cells

4. **State Announcements**
   - Cell state changes announced via stateDescription
   - Live regions for solve result feedback (assertive mode)

5. **On-Click Labels**
   - Descriptive click actions: "Enter number for Row 2, column 5"

6. **Dialog Accessibility**
   - Number picker announces all 9 number buttons
   - Clear button with descriptive label
   - Cancel button for dismissal

## Technical Details

### Stack
- **Kotlin** for Android development
- **Jetpack Compose** for modern declarative UI
- **Material Design** components
- **Android SDK 26+** (Android 8.0 Oreo and above)

### Build Configuration
- Gradle 8.14.4
- Android Gradle Plugin 8.3.0
- Kotlin 1.8.0
- Compose 1.4.3

## Building the App

### Prerequisites
- Nix environment (preferred) OR
- Android SDK with API 34
- JDK 17

### Build with Nix

From the wonderback project root:

```bash
# Build all modules including sudoku-test-app
nix run .#build

# The APK will be at:
# sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk
```

### Build with Gradle (alternative)

```bash
cd /home/kimb/projects/wonderback

# Build the Sudoku test app
./gradlew :sudoku-test-app:assembleDebug

# APK location:
# sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk
```

## Installing the App

### Using ADB

```bash
# Install to connected device/emulator
adb install sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk

# Or reinstall if already present
adb install -r sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk
```

### Verify Installation

```bash
# Check if app is installed
adb shell pm list packages | grep wonderback.sudoku

# Launch the app
adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity
```

## Testing with TalkBack

### Enable TalkBack

1. On the Android device/emulator:
   ```bash
   adb shell settings put secure enabled_accessibility_services com.android.talkback/com.google.android.marvin.talkback.TalkBackService
   adb shell settings put secure accessibility_enabled 1
   ```

2. Or manually: Settings → Accessibility → TalkBack → Turn on

### Testing Scenarios

#### Scenario 1: Grid Navigation
1. Launch the Sudoku app
2. Use swipe gestures to navigate through cells
3. Verify each cell announces position, value, and editability
4. Check that focus order follows left-to-right, top-to-bottom

#### Scenario 2: Number Input
1. Navigate to an empty, editable cell
2. Double-tap to activate
3. Verify number picker dialog opens
4. Navigate through numbers 1-9
5. Select a number and verify it's announced
6. Verify cell updates and announces new value

#### Scenario 3: Solve Validation
1. Fill in some cells (puzzle doesn't need to be correct)
2. Navigate to "Solve Puzzle" button
3. Double-tap to activate
4. Verify result message is announced via live region

## Puzzle Details

The app uses a hardcoded Sudoku puzzle for consistency:

**Initial State** (0 = empty):
```
5 3 . | . 7 . | . . .
6 . . | 1 9 5 | . . .
. 9 8 | . . . | . 6 .
------+-------+------
8 . . | . 6 . | . . 3
4 . . | 8 . 3 | . . 1
7 . . | . 2 . | . . 6
------+-------+------
. 6 . | . . . | 2 8 .
. . . | 4 1 9 | . . 5
. . . | . 8 . | . 7 9
```

**Solution**:
```
5 3 4 | 6 7 8 | 9 1 2
6 7 2 | 1 9 5 | 3 4 8
1 9 8 | 3 4 2 | 5 6 7
------+-------+------
8 5 9 | 7 6 1 | 4 2 3
4 2 6 | 8 5 3 | 7 9 1
7 1 3 | 9 2 4 | 8 5 6
------+-------+------
9 6 1 | 5 3 7 | 2 8 4
2 8 7 | 4 1 9 | 6 3 5
3 4 5 | 2 8 6 | 1 7 9
```

## Agent Testing Goals

This app is designed to test if an AI agent can:

1. **Navigate** a complex grid using only TalkBack utterances
2. **Understand** spatial relationships (rows, columns, 3x3 boxes)
3. **Select** specific cells by position
4. **Input** numbers through accessible dialogs
5. **Complete** a multi-step task (solving the puzzle)
6. **Validate** results using the solve button

## Known Limitations

- No automatic solving algorithm (by design - for testing only)
- Single hardcoded puzzle
- No difficulty levels or puzzle generation
- No undo/redo functionality
- Minimal error handling

These limitations are intentional to keep the app simple and focused on accessibility testing.

## Future Improvements

Potential enhancements for agent testing:

- [ ] Add timer for completion tracking
- [ ] Include hint system
- [ ] Add difficulty levels
- [ ] Multiple puzzle variants
- [ ] Mistake highlighting
- [ ] Progress saving

## Related Issues

- wonderback-30: Model Gym MVP - TalkBack Agent Sudoku Test
- wonderback-33: Create minimal Sudoku test app with Kotlin Compose
- wonderback-35: Run TalkBack agent test on Sudoku app

## License

This is a test application for the wonderback project. See the main project LICENSE file for details.
