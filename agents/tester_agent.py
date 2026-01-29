#!/usr/bin/env python3
"""
TalkBack Tester Agent - wonderback-69

Attempts to navigate and solve the Sudoku app using only accessibility information,
simulating how a TalkBack user would interact with the app.
"""

import subprocess
import xml.etree.ElementTree as ET
import re
import time
import json
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass


@dataclass
class SudokuCell:
    """Represents a Sudoku cell with its accessibility properties"""
    row: int
    col: int
    value: Optional[int]  # None if empty
    is_given: bool  # True if pre-filled
    is_editable: bool  # True if can be modified
    content_desc: str  # Full accessibility description
    bounds: Tuple[int, int, int, int]  # (left, top, right, bottom)

    def __str__(self):
        val_str = str(self.value) if self.value else "empty"
        status = "given" if self.is_given else "editable"
        return f"R{self.row}C{self.col}: {val_str} ({status})"


class TesterAgent:
    """Agent that tries to solve Sudoku using only TalkBack/accessibility context"""

    def __init__(self):
        self.grid = [[None for _ in range(9)] for _ in range(9)]
        self.cells: List[SudokuCell] = []
        self.attempts = 0
        self.cells_attempted = 0
        self.cells_filled = 0
        self.failures: List[Dict] = []

    def log(self, message: str, level: str = "INFO"):
        """Log with timestamp"""
        timestamp = time.strftime("%H:%M:%S")
        print(f"[{timestamp}] [{level}] {message}")

    def run_adb_command(self, command: str) -> str:
        """Execute ADB command and return output"""
        try:
            result = subprocess.run(
                f"adb shell {command}",
                shell=True,
                capture_output=True,
                text=True,
                timeout=30
            )
            return result.stdout.strip()
        except Exception as e:
            self.log(f"ADB command failed: {e}", "ERROR")
            return ""

    def dump_ui_hierarchy(self) -> Optional[ET.Element]:
        """Dump UI hierarchy and parse XML"""
        self.log("Dumping UI hierarchy...")
        self.run_adb_command("uiautomator dump")

        # Pull the XML file
        subprocess.run(
            "adb pull /sdcard/window_dump.xml /tmp/ui_dump.xml",
            shell=True,
            capture_output=True
        )

        try:
            tree = ET.parse("/tmp/ui_dump.xml")
            return tree.getroot()
        except Exception as e:
            self.log(f"Failed to parse UI XML: {e}", "ERROR")
            return None

    def parse_cell_description(self, desc: str) -> Optional[Dict]:
        """
        Parse accessibility description like:
        'Row 1, column 3, empty, editable'
        'Row 2, column 5, 7, given'
        """
        # Pattern: Row X, column Y, (value|empty), (editable|given)
        pattern = r"Row (\d+), column (\d+), (empty|\d+), (editable|given)"
        match = re.match(pattern, desc)

        if not match:
            return None

        row, col, value_str, status = match.groups()
        return {
            "row": int(row),
            "col": int(col),
            "value": None if value_str == "empty" else int(value_str),
            "is_given": status == "given",
            "is_editable": status == "editable"
        }

    def parse_bounds(self, bounds_str: str) -> Tuple[int, int, int, int]:
        """Parse bounds string '[left,top][right,bottom]' to tuple"""
        # Extract numbers: [46,566][163,683] -> (46, 566, 163, 683)
        numbers = re.findall(r'\d+', bounds_str)
        if len(numbers) == 4:
            return tuple(map(int, numbers))
        return (0, 0, 0, 0)

    def extract_sudoku_cells(self, root: ET.Element) -> List[SudokuCell]:
        """Extract all Sudoku cells from UI hierarchy"""
        cells = []

        # Find all nodes with content-desc containing "Row"
        for node in root.iter():
            desc = node.get("content-desc", "")
            if not desc.startswith("Row"):
                continue

            cell_info = self.parse_cell_description(desc)
            if not cell_info:
                continue

            bounds_str = node.get("bounds", "[0,0][0,0]")
            bounds = self.parse_bounds(bounds_str)

            cell = SudokuCell(
                row=cell_info["row"],
                col=cell_info["col"],
                value=cell_info["value"],
                is_given=cell_info["is_given"],
                is_editable=cell_info["is_editable"],
                content_desc=desc,
                bounds=bounds
            )
            cells.append(cell)

        return cells

    def tap_cell(self, cell: SudokuCell) -> bool:
        """Tap on a cell using its bounds"""
        left, top, right, bottom = cell.bounds
        center_x = (left + right) // 2
        center_y = (top + bottom) // 2

        self.log(f"Tapping {cell} at ({center_x}, {center_y})")
        self.run_adb_command(f"input tap {center_x} {center_y}")
        time.sleep(0.5)
        return True

    def select_number(self, number: int) -> bool:
        """Select a number from the picker dialog"""
        self.log(f"Attempting to select number {number}")

        # Dump UI to find number buttons
        root = self.dump_ui_hierarchy()
        if root is None:
            return False

        # Find button with text matching the number
        for node in root.iter():
            text = node.get("text", "")
            if text == str(number):
                bounds_str = node.get("bounds", "")
                bounds = self.parse_bounds(bounds_str)
                left, top, right, bottom = bounds
                center_x = (left + right) // 2
                center_y = (top + bottom) // 2

                self.log(f"Found number {number} button at ({center_x}, {center_y})")
                self.run_adb_command(f"input tap {center_x} {center_y}")
                time.sleep(0.5)
                return True

        self.log(f"Could not find button for number {number}", "WARN")
        return False

    def read_grid_state(self) -> bool:
        """Read current state of Sudoku grid"""
        self.log("Reading Sudoku grid state...")

        root = self.dump_ui_hierarchy()
        if root is None:
            return False

        self.cells = self.extract_sudoku_cells(root)

        # Update internal grid representation
        for cell in self.cells:
            self.grid[cell.row - 1][cell.col - 1] = cell.value

        # Count filled vs empty
        filled = sum(1 for cell in self.cells if cell.value is not None)
        empty = 81 - filled
        editable = sum(1 for cell in self.cells if cell.is_editable)

        self.log(f"Grid: {filled} filled, {empty} empty, {editable} editable")
        return True

    def find_empty_cells(self) -> List[SudokuCell]:
        """Find all empty editable cells"""
        return [c for c in self.cells if c.value is None and c.is_editable]

    def attempt_solve(self) -> bool:
        """Attempt to solve the Sudoku puzzle"""
        self.log("=" * 60)
        self.log("STARTING SOLVE ATTEMPT")
        self.log("=" * 60)

        self.attempts += 1

        # Read initial state
        if not self.read_grid_state():
            self.failures.append({"reason": "Could not read grid state"})
            return False

        empty_cells = self.find_empty_cells()
        self.log(f"Found {len(empty_cells)} empty cells to fill")

        if not empty_cells:
            self.log("No empty cells found - puzzle might be complete!", "WARN")
            return self.verify_solution()

        # For MVP: Try filling first few cells with simple logic
        # (Real agent would use sophisticated Sudoku solving)
        for i, cell in enumerate(empty_cells[:5]):  # Try first 5 cells for MVP
            self.log(f"\nAttempt {i+1}/5: Filling {cell}")
            self.cells_attempted += 1

            # Tap cell
            if not self.tap_cell(cell):
                self.failures.append({
                    "cell": str(cell),
                    "reason": "Could not tap cell"
                })
                continue

            # Try to find valid number (simplified logic for MVP)
            # In real implementation, would use Sudoku constraints
            test_number = ((cell.row + cell.col) % 9) + 1

            if not self.select_number(test_number):
                self.failures.append({
                    "cell": str(cell),
                    "number": test_number,
                    "reason": "Could not select number"
                })
                # Try to close dialog
                self.run_adb_command("input keyevent KEYCODE_BACK")
                time.sleep(0.5)
                continue

            self.cells_filled += 1
            self.log(f"✓ Entered {test_number} in {cell}")
            time.sleep(1)

        # Verify solution
        return self.verify_solution()

    def verify_solution(self) -> bool:
        """Tap solve button and check result"""
        self.log("\n" + "=" * 60)
        self.log("VERIFYING SOLUTION")
        self.log("=" * 60)

        # Find and tap solve button
        root = self.dump_ui_hierarchy()
        if root is None:
            return False

        for node in root.iter():
            desc = node.get("content-desc", "")
            if "Solve puzzle" in desc:
                bounds = self.parse_bounds(node.get("bounds", ""))
                left, top, right, bottom = bounds
                center_x = (left + right) // 2
                center_y = (top + bottom) // 2

                self.log(f"Tapping Solve button at ({center_x}, {center_y})")
                self.run_adb_command(f"input tap {center_x} {center_y}")
                time.sleep(2)
                break

        # Check logcat for result
        result = subprocess.run(
            "adb logcat -d -s SudokuTestApp:I | tail -20",
            shell=True,
            capture_output=True,
            text=True
        )

        if "✓ Puzzle SOLVED CORRECTLY!" in result.stdout:
            self.log("🎉 SUCCESS: Puzzle solved!", "SUCCESS")
            return True
        elif "✗ Puzzle not solved" in result.stdout:
            self.log("✗ FAILED: Puzzle not solved correctly", "WARN")
            return False
        else:
            self.log("Could not determine result from logs", "WARN")
            return False

    def generate_report(self) -> Dict:
        """Generate detailed report of testing session"""
        # Calculate success metrics
        total_cells = len(self.cells)
        editable_cells = len([c for c in self.cells if c.is_editable])

        # Build experience summary
        summary = []
        summary.append(f"Found {total_cells} total cells ({editable_cells} editable)")
        summary.append(f"Successfully filled {self.cells_filled}/{self.cells_attempted} cells attempted")

        if self.cells_filled > 0:
            summary.append("✓ Cell tapping and number selection working")
            summary.append("✓ Number picker dialog accessible")
            summary.append("✓ Content descriptions properly formatted")

        if len(self.failures) > 0:
            summary.append(f"✗ {len(self.failures)} interaction failures")
        else:
            summary.append("✓ No interaction failures")

        # Categorize issues
        interaction_issues = [f for f in self.failures if "tap" in str(f).lower()]
        dialog_issues = [f for f in self.failures if "select" in str(f).lower()]

        return {
            "attempts": self.attempts,
            "cells_attempted": self.cells_attempted,
            "cells_filled": self.cells_filled,
            "failures": self.failures,
            "cells_found": total_cells,
            "editable_cells": editable_cells,
            "interaction_issues": len(interaction_issues),
            "dialog_issues": len(dialog_issues),
            "summary": summary,
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
        }

    def run(self):
        """Main entry point"""
        self.log("╔═══════════════════════════════════════════════════════╗")
        self.log("║        TALKBACK TESTER AGENT - wonderback-69         ║")
        self.log("╚═══════════════════════════════════════════════════════╝")

        # Launch Sudoku app
        self.log("\nLaunching Sudoku app...")
        self.run_adb_command(
            "am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity"
        )
        time.sleep(3)

        # Attempt to solve
        success = self.attempt_solve()

        # Generate report
        report = self.generate_report()
        self.log("\n" + "=" * 60)
        self.log("FINAL REPORT")
        self.log("=" * 60)
        self.log(json.dumps(report, indent=2))

        if success:
            self.log("\n✓ Test PASSED: Agent solved Sudoku!", "SUCCESS")
            return 0
        else:
            self.log("\n✗ Test FAILED: Agent could not solve Sudoku", "WARN")
            self.log(f"Failures: {len(self.failures)}")
            for failure in self.failures:
                self.log(f"  - {failure}", "WARN")
            return 1


if __name__ == "__main__":
    agent = TesterAgent()
    exit(agent.run())
