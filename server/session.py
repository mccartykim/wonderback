"""
Session logging and export.

Records all utterances, analysis results, and skill executions
during a testing session. Supports export to JSON and Markdown
for sharing with teams or filing JIRA tickets.
"""

from __future__ import annotations

import json
import logging
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

from pydantic import BaseModel

from models import AnalysisRequest, AnalysisResponse, Issue

logger = logging.getLogger(__name__)


class SessionEvent(BaseModel):
    """A single event in the session timeline."""
    timestamp: float
    event_type: str  # utterance | analysis | skill | note
    data: dict = {}


class SessionSummary(BaseModel):
    """Summary statistics for a session."""
    session_id: str
    started_at: str
    duration_seconds: int
    total_utterances: int
    total_analyses: int
    total_issues: int
    issues_by_severity: dict[str, int] = {}
    issues_by_category: dict[str, int] = {}
    screens_visited: list[str] = []


class Session:
    """
    Records a testing session for later review and export.
    """

    def __init__(self, session_id: Optional[str] = None):
        self.session_id = session_id or datetime.now().strftime("%Y%m%d_%H%M%S")
        self.started_at = time.time()
        self.events: list[SessionEvent] = []
        self.issues: list[Issue] = []
        self.screens: set[str] = set()

    def record_utterances(self, request: AnalysisRequest):
        """Record incoming utterances."""
        for u in request.utterances:
            self.events.append(SessionEvent(
                timestamp=time.time(),
                event_type="utterance",
                data=u.model_dump(),
            ))
            screen = f"{u.screen.package_name}/{u.screen.activity_name}"
            if screen != "/":
                self.screens.add(screen)

    def record_analysis(self, request: AnalysisRequest, response: AnalysisResponse):
        """Record an analysis result."""
        self.events.append(SessionEvent(
            timestamp=time.time(),
            event_type="analysis",
            data={
                "utterance_count": len(request.utterances),
                "issue_count": len(response.issues),
                "metadata": response.metadata.model_dump() if response.metadata else {},
            },
        ))
        self.issues.extend(response.issues)

    def record_skill(self, skill_name: str, success: bool, message: str = ""):
        """Record a skill execution."""
        self.events.append(SessionEvent(
            timestamp=time.time(),
            event_type="skill",
            data={"skill_name": skill_name, "success": success, "message": message},
        ))

    def add_note(self, note: str):
        """Add a manual note to the session."""
        self.events.append(SessionEvent(
            timestamp=time.time(),
            event_type="note",
            data={"text": note},
        ))

    def get_summary(self) -> SessionSummary:
        """Get summary statistics."""
        by_severity: dict[str, int] = {}
        by_category: dict[str, int] = {}
        for issue in self.issues:
            sev = issue.severity.value if hasattr(issue.severity, 'value') else str(issue.severity)
            cat = issue.category.value if hasattr(issue.category, 'value') else str(issue.category)
            by_severity[sev] = by_severity.get(sev, 0) + 1
            by_category[cat] = by_category.get(cat, 0) + 1

        utterance_count = sum(1 for e in self.events if e.event_type == "utterance")
        analysis_count = sum(1 for e in self.events if e.event_type == "analysis")
        duration = int(time.time() - self.started_at)

        return SessionSummary(
            session_id=self.session_id,
            started_at=datetime.fromtimestamp(self.started_at).isoformat(),
            duration_seconds=duration,
            total_utterances=utterance_count,
            total_analyses=analysis_count,
            total_issues=len(self.issues),
            issues_by_severity=by_severity,
            issues_by_category=by_category,
            screens_visited=sorted(self.screens),
        )

    def export_json(self) -> str:
        """Export session as JSON."""
        return json.dumps({
            "session": self.get_summary().model_dump(),
            "issues": [i.model_dump() for i in self.issues],
            "events": [e.model_dump() for e in self.events],
        }, indent=2, default=str)

    def export_markdown(self) -> str:
        """Export session as Markdown (suitable for JIRA/GitHub issues)."""
        summary = self.get_summary()
        lines = [
            f"# Accessibility Audit: {summary.session_id}",
            "",
            f"**Date:** {summary.started_at}",
            f"**Duration:** {summary.duration_seconds}s",
            f"**Utterances:** {summary.total_utterances}",
            f"**Issues Found:** {summary.total_issues}",
            "",
        ]

        if summary.screens_visited:
            lines.append("## Screens Visited")
            for screen in summary.screens_visited:
                lines.append(f"- `{screen}`")
            lines.append("")

        if summary.issues_by_severity:
            lines.append("## Issue Summary")
            lines.append("")
            lines.append("| Severity | Count |")
            lines.append("|----------|-------|")
            for sev, count in sorted(summary.issues_by_severity.items()):
                lines.append(f"| {sev} | {count} |")
            lines.append("")

        if self.issues:
            lines.append("## Issues")
            lines.append("")
            for i, issue in enumerate(self.issues, 1):
                sev = issue.severity.value if hasattr(issue.severity, 'value') else str(issue.severity)
                cat = issue.category.value if hasattr(issue.category, 'value') else str(issue.category)
                icon = {"ERROR": "🔴", "WARNING": "🟡", "SUGGESTION": "🔵"}.get(sev, "⚪")
                lines.append(f"### {icon} {i}. {issue.issue}")
                lines.append("")
                lines.append(f"**Severity:** {sev} | **Category:** {cat}")
                if issue.utterance:
                    lines.append(f"**Utterance:** `{issue.utterance}`")
                if issue.explanation:
                    lines.append(f"\n{issue.explanation}")
                if issue.suggestion:
                    lines.append(f"\n**Suggestion:** {issue.suggestion}")
                lines.append("")

        return "\n".join(lines)

    def save(self, directory: str = "sessions"):
        """Save session to disk as JSON."""
        path = Path(directory)
        path.mkdir(parents=True, exist_ok=True)
        filepath = path / f"{self.session_id}.json"
        filepath.write_text(self.export_json())
        logger.info(f"Session saved to {filepath}")
        return str(filepath)


class SessionManager:
    """Manages multiple sessions."""

    def __init__(self):
        self._current: Optional[Session] = None
        self._history: list[SessionSummary] = []

    @property
    def current(self) -> Session:
        if self._current is None:
            self._current = Session()
            logger.info(f"Started new session: {self._current.session_id}")
        return self._current

    def start_new(self, session_id: Optional[str] = None) -> Session:
        """Start a new session, closing the current one."""
        if self._current is not None:
            self._history.append(self._current.get_summary())
        self._current = Session(session_id)
        logger.info(f"Started session: {self._current.session_id}")
        return self._current

    def end_current(self) -> Optional[SessionSummary]:
        """End the current session and return its summary."""
        if self._current is None:
            return None
        summary = self._current.get_summary()
        self._history.append(summary)
        self._current = None
        return summary

    def get_history(self) -> list[SessionSummary]:
        return list(self._history)


# Singleton
session_manager = SessionManager()
