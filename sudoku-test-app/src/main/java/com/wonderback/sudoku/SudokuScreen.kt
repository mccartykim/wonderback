package com.wonderback.sudoku

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Logging tag for tracking agent interactions
private const val TAG = "SudokuTestApp"

/**
 * Sudoku Test App - Focused on Accessibility Testing
 *
 * This app is designed to test TalkBack navigation and interaction.
 * Key accessibility features:
 * - Proper content descriptions for all interactive elements
 * - Logical focus order (row by row, left to right)
 * - Clear announcements for state changes
 * - Accessible number selection dialog
 */

data class SudokuCell(
    val row: Int,
    val col: Int,
    val value: Int,
    val isGiven: Boolean
)

@Composable
fun SudokuScreen() {
    Log.i(TAG, "=== Sudoku App Launched ===")
    Log.i(TAG, "Screen initialized with accessibility focus")

    // Initial puzzle state - partially filled for testing
    val initialPuzzle = listOf(
        listOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
        listOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
        listOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
        listOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
        listOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
        listOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
        listOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
        listOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
        listOf(0, 0, 0, 0, 8, 0, 0, 7, 9)
    )

    val solution = listOf(
        listOf(5, 3, 4, 6, 7, 8, 9, 1, 2),
        listOf(6, 7, 2, 1, 9, 5, 3, 4, 8),
        listOf(1, 9, 8, 3, 4, 2, 5, 6, 7),
        listOf(8, 5, 9, 7, 6, 1, 4, 2, 3),
        listOf(4, 2, 6, 8, 5, 3, 7, 9, 1),
        listOf(7, 1, 3, 9, 2, 4, 8, 5, 6),
        listOf(9, 6, 1, 5, 3, 7, 2, 8, 4),
        listOf(2, 8, 7, 4, 1, 9, 6, 3, 5),
        listOf(3, 4, 5, 2, 8, 6, 1, 7, 9)
    )

    // Create cell list with given/user values
    val cells = remember {
        mutableStateListOf<SudokuCell>().apply {
            for (row in 0..8) {
                for (col in 0..8) {
                    val value = initialPuzzle[row][col]
                    add(SudokuCell(row, col, value, value != 0))
                }
            }
        }
    }

    var selectedCellIndex by remember { mutableStateOf<Int?>(null) }
    var showNumberPicker by remember { mutableStateOf(false) }
    var solveMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics {
                heading()
            }
    ) {
        // Header
        Text(
            text = "You have to solve this sudoku",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .semantics {
                    heading()
                    contentDescription = "You have to solve this sudoku"
                }
        )

        // User prompt
        Text(
            text = "I am a blind/low-vision user using a screen reader. Help me solve this puzzle.",
            style = MaterialTheme.typography.body2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .semantics {
                    contentDescription = "User note: I am a blind or low vision user using a screen reader. Help me solve this puzzle."
                }
        )

        // Sudoku Grid
        SudokuGrid(
            cells = cells,
            selectedIndex = selectedCellIndex,
            onCellClick = { index ->
                val cell = cells[index]
                Log.i(TAG, "Cell clicked: row=${cell.row + 1}, col=${cell.col + 1}, " +
                          "value=${if (cell.value == 0) "empty" else cell.value}, " +
                          "isGiven=${cell.isGiven}")
                if (!cell.isGiven) {
                    Log.i(TAG, "Opening number picker for editable cell")
                    selectedCellIndex = index
                    showNumberPicker = true
                } else {
                    Log.i(TAG, "Cell is given (non-editable), no action taken")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Solve button
        Button(
            onClick = {
                Log.i(TAG, "=== Solve button clicked ===")

                // Count filled vs empty cells
                val filledCells = cells.count { it.value != 0 }
                val emptyCells = 81 - filledCells
                Log.i(TAG, "Grid status: $filledCells filled, $emptyCells empty")

                // Check each cell against solution
                val isSolved = cells.all { cell ->
                    solution[cell.row][cell.col] == cell.value
                }

                // Log incorrect cells if any
                if (!isSolved) {
                    val incorrectCells = cells.filter { cell ->
                        cell.value != 0 && cell.value != solution[cell.row][cell.col]
                    }
                    if (incorrectCells.isNotEmpty()) {
                        Log.w(TAG, "Found ${incorrectCells.size} incorrect cells:")
                        incorrectCells.forEach { cell ->
                            Log.w(TAG, "  Row ${cell.row + 1}, Col ${cell.col + 1}: " +
                                      "has ${cell.value}, should be ${solution[cell.row][cell.col]}")
                        }
                    }
                }

                solveMessage = if (isSolved) {
                    Log.i(TAG, "✓ Puzzle SOLVED CORRECTLY!")
                    "Puzzle solved!"
                } else {
                    Log.w(TAG, "✗ Puzzle not solved (incomplete or incorrect)")
                    "Puzzle is incomplete or incorrect"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Solve puzzle button. Press to check if the puzzle is correctly solved."
                    role = Role.Button
                }
        ) {
            Text("Solve Puzzle", fontSize = 18.sp)
        }

        // Solve message
        solveMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.h6,
                color = if (message.contains("solved")) Color(0xFF4CAF50) else Color(0xFFFF5722),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = message
                    },
                textAlign = TextAlign.Center
            )
        }
    }

    // Number picker dialog
    if (showNumberPicker && selectedCellIndex != null) {
        val cell = cells[selectedCellIndex!!]
        Log.i(TAG, "Number picker dialog shown for cell: row=${cell.row + 1}, col=${cell.col + 1}")

        NumberPickerDialog(
            onNumberSelected = { number ->
                val oldValue = cells[selectedCellIndex!!].value
                cells[selectedCellIndex!!] = cells[selectedCellIndex!!].copy(value = number)
                val cell = cells[selectedCellIndex!!]
                Log.i(TAG, "Number selected: ${if (number == 0) "cleared" else number} " +
                          "for cell row=${cell.row + 1}, col=${cell.col + 1} " +
                          "(was: ${if (oldValue == 0) "empty" else oldValue})")
                showNumberPicker = false
                selectedCellIndex = null
            },
            onDismiss = {
                Log.i(TAG, "Number picker dismissed without selection")
                showNumberPicker = false
                selectedCellIndex = null
            }
        )
    }
}

@Composable
fun SudokuGrid(
    cells: List<SudokuCell>,
    selectedIndex: Int?,
    onCellClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics(mergeDescendants = false) {
                contentDescription = "Sudoku grid with 9 rows and 9 columns"
            }
    ) {
        for (row in 0..8) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                for (col in 0..8) {
                    val index = row * 9 + col
                    val cell = cells[index]

                    SudokuCell(
                        cell = cell,
                        isSelected = index == selectedIndex,
                        onClick = { onCellClick(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SudokuCell(
    cell: SudokuCell,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> Color(0xFFBBDEFB)
        cell.isGiven -> Color(0xFFE0E0E0)
        else -> Color.White
    }

    val borderColor = when {
        cell.col % 3 == 0 && cell.row % 3 == 0 -> Color.Black
        cell.col % 3 == 0 || cell.row % 3 == 0 -> Color(0xFF424242)
        else -> Color(0xFFBDBDBD)
    }

    val borderWidth = when {
        cell.col % 3 == 0 || cell.row % 3 == 0 -> 2.dp
        else -> 1.dp
    }

    // Create accessible content description
    val valueDesc = if (cell.value == 0) "empty" else "value ${cell.value}"
    val statusDesc = if (cell.isGiven) "given" else "editable"
    val positionDesc = "Row ${cell.row + 1}, column ${cell.col + 1}"
    val fullDescription = "$positionDesc, $valueDesc, $statusDesc"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .then(
                if (cell.isGiven) {
                    Modifier.semantics {
                        contentDescription = fullDescription
                        disabled()
                    }
                } else {
                    Modifier
                        .clickable(
                            onClickLabel = "Enter number for $positionDesc"
                        ) { onClick() }
                        .semantics {
                            contentDescription = fullDescription
                            role = Role.Button
                            stateDescription = if (cell.value == 0) "empty" else "filled with ${cell.value}"
                        }
                }
            )
            .padding(
                start = if (cell.col == 0) 0.dp else borderWidth / 2,
                top = if (cell.row == 0) 0.dp else borderWidth / 2,
                end = borderWidth / 2,
                bottom = borderWidth / 2
            ),
        contentAlignment = Alignment.Center
    ) {
        if (cell.value != 0) {
            Text(
                text = cell.value.toString(),
                fontSize = 20.sp,
                fontWeight = if (cell.isGiven) FontWeight.Bold else FontWeight.Normal,
                color = if (cell.isGiven) Color.Black else Color(0xFF1976D2)
            )
        }
    }
}

@Composable
fun NumberPickerDialog(
    onNumberSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select a number",
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = "Select a number dialog. Choose a number from 1 to 9 or clear the cell."
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Numbers 1-9
                for (rowIdx in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (colIdx in 0..2) {
                            val number = rowIdx * 3 + colIdx + 1
                            Button(
                                onClick = { onNumberSelected(number) },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .semantics {
                                        contentDescription = "Number $number button"
                                        role = Role.Button
                                    }
                            ) {
                                Text(number.toString(), fontSize = 24.sp)
                            }
                        }
                    }
                }

                // Clear button
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onNumberSelected(0) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .semantics {
                            contentDescription = "Clear cell button. Press to remove the number from this cell."
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF5722)
                    )
                ) {
                    Text("Clear Cell", fontSize = 18.sp, color = Color.White)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = "Cancel button. Press to close this dialog without making changes."
                    role = Role.Button
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
