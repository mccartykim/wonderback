"""
Model gym: compare LLM backends and prompt variants on utterance batches.

Usage:
  POST /gym/run     — single backend + prompt against utterance batch
  POST /gym/compare — matrix of backends x prompts, side-by-side results
  GET  /gym/history — past gym runs
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
import time
import uuid
from typing import Optional

from pydantic import BaseModel

from gym_backends import BackendConfig, BackendResult, ModelBackend, create_backend
from models import Issue
from prompt import ACCESSIBILITY_AGENT_PROMPT, build_analysis_prompt

logger = logging.getLogger(__name__)


class GymCellResult(BaseModel):
    """Result for one (backend, prompt_variant) cell in the comparison matrix."""
    backend_name: str
    prompt_label: str
    raw_output: str
    latency_ms: int
    success: bool
    error: str = ""
    issues_found: int = 0
    issues: list[dict] = []
    parse_success: bool = False


class GymRunRequest(BaseModel):
    """Request to run a single gym evaluation."""
    backend: BackendConfig
    utterances: list[dict]
    prompt_override: Optional[str] = None
    context: Optional[dict] = None


class GymCompareRequest(BaseModel):
    """Request for a multi-backend, multi-prompt comparison."""
    backends: list[BackendConfig]
    utterances: list[dict]
    prompt_variants: list[Optional[str]] = [None]  # None = default prompt
    context: Optional[dict] = None


class GymRunSummary(BaseModel):
    """Summary of a completed gym run, stored in history."""
    run_id: str
    timestamp: float
    utterance_count: int
    backend_count: int
    prompt_variant_count: int
    total_latency_ms: int
    results: list[GymCellResult]


class GymRunner:
    """Executes gym comparisons and stores history."""

    def __init__(self):
        self._history: list[GymRunSummary] = []

    async def run_single(self, request: GymRunRequest) -> GymCellResult:
        """Run one backend against one utterance batch."""
        backend = create_backend(request.backend)
        system_prompt = request.prompt_override or ACCESSIBILITY_AGENT_PROMPT
        user_prompt = build_analysis_prompt(
            request.utterances,
            request.context,
        )

        result = await backend.invoke(system_prompt, user_prompt)
        cell = self._build_cell(result, _prompt_label(request.prompt_override))

        # Store as a single-cell run
        summary = GymRunSummary(
            run_id=str(uuid.uuid4())[:8],
            timestamp=time.time(),
            utterance_count=len(request.utterances),
            backend_count=1,
            prompt_variant_count=1,
            total_latency_ms=cell.latency_ms,
            results=[cell],
        )
        self._history.append(summary)
        return cell

    async def run_compare(self, request: GymCompareRequest) -> GymRunSummary:
        """Run a matrix of backends x prompt variants."""
        start = time.time()
        tasks = []

        for backend_config in request.backends:
            for prompt_variant in request.prompt_variants:
                tasks.append(
                    self._run_one(
                        backend_config,
                        request.utterances,
                        request.context,
                        prompt_variant,
                    )
                )

        results = await asyncio.gather(*tasks, return_exceptions=True)
        cells = []
        for r in results:
            if isinstance(r, Exception):
                cells.append(GymCellResult(
                    backend_name="error",
                    prompt_label="error",
                    raw_output="",
                    latency_ms=0,
                    success=False,
                    error=str(r),
                ))
            else:
                cells.append(r)

        summary = GymRunSummary(
            run_id=str(uuid.uuid4())[:8],
            timestamp=time.time(),
            utterance_count=len(request.utterances),
            backend_count=len(request.backends),
            prompt_variant_count=len(request.prompt_variants),
            total_latency_ms=int((time.time() - start) * 1000),
            results=cells,
        )
        self._history.append(summary)
        return summary

    async def _run_one(
        self,
        backend_config: BackendConfig,
        utterances: list[dict],
        context: Optional[dict],
        prompt_override: Optional[str],
    ) -> GymCellResult:
        backend = create_backend(backend_config)
        system_prompt = prompt_override or ACCESSIBILITY_AGENT_PROMPT
        user_prompt = build_analysis_prompt(utterances, context)
        result = await backend.invoke(system_prompt, user_prompt)
        return self._build_cell(result, _prompt_label(prompt_override))

    def _build_cell(self, result: BackendResult, prompt_label: str) -> GymCellResult:
        """Parse issues from backend output and build a cell result."""
        issues: list[dict] = []
        parse_success = False

        if result.success and result.raw_output:
            issues, parse_success = _parse_issues_from_raw(result.raw_output)

        return GymCellResult(
            backend_name=result.backend_name,
            prompt_label=prompt_label,
            raw_output=result.raw_output,
            latency_ms=result.latency_ms,
            success=result.success,
            error=result.error,
            issues_found=len(issues),
            issues=issues,
            parse_success=parse_success,
        )

    def get_history(self, limit: int = 50) -> list[GymRunSummary]:
        return self._history[-limit:]

    def get_run(self, run_id: str) -> Optional[GymRunSummary]:
        for run in self._history:
            if run.run_id == run_id:
                return run
        return None

    def clear(self):
        self._history.clear()


def _prompt_label(override: Optional[str]) -> str:
    """Generate a short label for a prompt variant."""
    if override is None:
        return "default"
    # Use first 40 chars as label
    return override[:40].replace("\n", " ").strip() or "custom"


def _parse_issues_from_raw(raw: str) -> tuple[list[dict], bool]:
    """
    Attempt to parse issues from raw LLM output.
    Returns (issues_list, parse_success).
    Same parsing logic as AccessibilityAnalyzer._parse_issues.
    """
    try:
        data = json.loads(raw)
        return data.get("issues", []), True
    except json.JSONDecodeError:
        pass

    # Try extracting from markdown code block
    match = re.search(r"```json\n(.*?)\n```", raw, re.DOTALL)
    if match:
        try:
            data = json.loads(match.group(1))
            return data.get("issues", []), True
        except (json.JSONDecodeError, Exception):
            pass

    return [], False


# Singleton
gym_runner = GymRunner()
