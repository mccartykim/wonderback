#!/usr/bin/env python3
"""
Developer Agent - wonderback-70

Receives failure reports from Tester Agent and improves app accessibility.
Analyzes issues, modifies Sudoku app code, rebuilds, and triggers retesting.
"""

import json
import subprocess
import time
from typing import List, Dict
from pathlib import Path


class DeveloperAgent:
    """Agent that improves app accessibility based on tester feedback"""

    def __init__(self, project_root: str = "/home/kimb/projects/wonderback"):
        self.project_root = Path(project_root)
        self.sudoku_src = self.project_root / "sudoku-test-app/src/main/java/com/wonderback/sudoku"
        self.improvements_made = []

    def log(self, message: str, level: str = "INFO"):
        """Log with timestamp"""
        timestamp = time.strftime("%H:%M:%S")
        print(f"[{timestamp}] [{level}] {message}")

    def analyze_failures(self, report: Dict) -> List[Dict]:
        """Analyze tester report and identify accessibility issues"""
        self.log("Analyzing tester failures...")

        issues = []
        failures = report.get("failures", [])

        for failure in failures:
            reason = failure.get("reason", "")

            if "Could not tap cell" in reason:
                issues.append({
                    "type": "interaction",
                    "severity": "high",
                    "description": "Cell not tappable or bounds incorrect",
                    "fix": "Check clickable modifier and bounds calculation"
                })

            elif "Could not select number" in reason:
                issues.append({
                    "type": "dialog",
                    "severity": "high",
                    "description": "Number picker buttons not accessible",
                    "fix": "Add content descriptions to number buttons"
                })

            elif "Could not read grid state" in reason:
                issues.append({
                    "type": "accessibility_tree",
                    "severity": "critical",
                    "description": "UI hierarchy not accessible",
                    "fix": "Ensure Compose semantics are properly configured"
                })

        # Check if no cells were found
        if report.get("cells_found", 0) == 0:
            issues.append({
                "type": "accessibility_tree",
                "severity": "critical",
                "description": "No Sudoku cells found in accessibility tree",
                "fix": "Add contentDescription to all cell Composables"
            })

        self.log(f"Found {len(issues)} accessibility issues")
        return issues

    def generate_improvement_plan(self, issues: List[Dict]) -> str:
        """Generate a plan for improving accessibility"""
        plan = "# Accessibility Improvement Plan\n\n"

        # Group by severity
        critical = [i for i in issues if i["severity"] == "critical"]
        high = [i for i in issues if i["severity"] == "high"]

        if critical:
            plan += "## Critical Issues (Must Fix)\n"
            for issue in critical:
                plan += f"\n### {issue['type']}\n"
                plan += f"**Problem**: {issue['description']}\n"
                plan += f"**Fix**: {issue['fix']}\n"

        if high:
            plan += "\n## High Priority Issues\n"
            for issue in high:
                plan += f"\n### {issue['type']}\n"
                plan += f"**Problem**: {issue['description']}\n"
                plan += f"**Fix**: {issue['fix']}\n"

        return plan

    def suggest_code_improvements(self, issues: List[Dict]) -> List[str]:
        """Suggest specific code improvements"""
        suggestions = []

        for issue in issues:
            if issue["type"] == "interaction":
                suggestions.append(
                    "Ensure all SudokuCell Composables have:\n"
                    "  .clickable { onClick() }\n"
                    "  .semantics { contentDescription = \"Row X, column Y, ...\" }"
                )

            elif issue["type"] == "dialog":
                suggestions.append(
                    "Add semantics to NumberPickerDialog buttons:\n"
                    "  Button(..., modifier = Modifier.semantics {\n"
                    "    contentDescription = \"Select number X\"\n"
                    "  })"
                )

            elif issue["type"] == "accessibility_tree":
                suggestions.append(
                    "Verify Compose accessibility:\n"
                    "  - Use semantics { } for all interactive elements\n"
                    "  - Set contentDescription for all cells\n"
                    "  - Ensure mergeDescendants = false on grid"
                )

        return list(set(suggestions))  # Remove duplicates

    def rebuild_app(self) -> bool:
        """Rebuild Sudoku APK"""
        self.log("Rebuilding Sudoku app...")

        try:
            result = subprocess.run(
                "cd /home/kimb/projects/wonderback && "
                "nix develop --command gradle :sudoku-test-app:assembleDebug --no-daemon",
                shell=True,
                capture_output=True,
                text=True,
                timeout=300
            )

            if result.returncode == 0:
                self.log("✓ Build successful", "SUCCESS")
                return True
            else:
                self.log(f"✗ Build failed: {result.stderr}", "ERROR")
                return False

        except Exception as e:
            self.log(f"Build exception: {e}", "ERROR")
            return False

    def reinstall_app(self) -> bool:
        """Reinstall Sudoku APK on emulator"""
        self.log("Reinstalling Sudoku app...")

        apk_path = "/home/kimb/projects/wonderback/sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk"

        try:
            result = subprocess.run(
                f"adb install -r {apk_path}",
                shell=True,
                capture_output=True,
                text=True,
                timeout=60
            )

            if "Success" in result.stdout:
                self.log("✓ Installation successful", "SUCCESS")
                return True
            else:
                self.log(f"✗ Installation failed: {result.stdout}", "ERROR")
                return False

        except Exception as e:
            self.log(f"Installation exception: {e}", "ERROR")
            return False

    def trigger_retest(self) -> int:
        """Trigger Tester Agent to run again"""
        self.log("Triggering Tester Agent for retest...")

        try:
            result = subprocess.run(
                "cd /home/kimb/projects/wonderback && python3 agents/tester_agent.py",
                shell=True,
                timeout=300
            )
            return result.returncode

        except Exception as e:
            self.log(f"Retest exception: {e}", "ERROR")
            return 1

    def run(self, tester_report_path: str):
        """Main entry point"""
        self.log("╔═══════════════════════════════════════════════════════╗")
        self.log("║       DEVELOPER AGENT - wonderback-70                ║")
        self.log("╚═══════════════════════════════════════════════════════╝")

        # Load tester report
        self.log(f"\nLoading tester report from {tester_report_path}")
        try:
            with open(tester_report_path) as f:
                report = json.load(f)
        except Exception as e:
            self.log(f"Could not load report: {e}", "ERROR")
            return 1

        # Analyze failures
        issues = self.analyze_failures(report)

        if not issues:
            self.log("✓ No issues found - app accessibility is good!", "SUCCESS")
            return 0

        # Generate improvement plan
        plan = self.generate_improvement_plan(issues)
        self.log("\n" + "=" * 60)
        self.log("IMPROVEMENT PLAN")
        self.log("=" * 60)
        print(plan)

        # Generate code suggestions
        suggestions = self.suggest_code_improvements(issues)
        self.log("\n" + "=" * 60)
        self.log("CODE SUGGESTIONS")
        self.log("=" * 60)
        for i, suggestion in enumerate(suggestions, 1):
            self.log(f"\n{i}. {suggestion}")

        # For MVP: Print suggestions but don't auto-modify
        # In full implementation, would use LLM to generate and apply code changes
        self.log("\n" + "=" * 60)
        self.log("MVP MODE: Manual fixes required")
        self.log("=" * 60)
        self.log("The Developer Agent has identified issues and provided suggestions.")
        self.log("For full automation, integrate with LLM to generate code changes.")
        self.log("\nNext steps:")
        self.log("1. Review suggestions above")
        self.log("2. Apply fixes to sudoku-test-app/src/main/java/com/wonderback/sudoku/")
        self.log("3. Run: developer_agent.py --rebuild-and-retest")

        return 0

    def rebuild_and_retest_workflow(self):
        """Rebuild app and trigger retest (for manual iteration)"""
        self.log("=" * 60)
        self.log("REBUILD AND RETEST WORKFLOW")
        self.log("=" * 60)

        # Rebuild
        if not self.rebuild_app():
            self.log("Rebuild failed, aborting", "ERROR")
            return 1

        # Reinstall
        if not self.reinstall_app():
            self.log("Reinstall failed, aborting", "ERROR")
            return 1

        # Retest
        self.log("\nStarting retest in 3 seconds...")
        time.sleep(3)
        return self.trigger_retest()


if __name__ == "__main__":
    import sys

    agent = DeveloperAgent()

    if "--rebuild-and-retest" in sys.argv:
        exit(agent.rebuild_and_retest_workflow())
    elif len(sys.argv) > 1:
        exit(agent.run(sys.argv[1]))
    else:
        print("Usage:")
        print("  python3 developer_agent.py <tester_report.json>")
        print("  python3 developer_agent.py --rebuild-and-retest")
        exit(1)
