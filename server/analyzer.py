"""LLM-based accessibility analyzer using Ollama."""

from __future__ import annotations

import json
import logging
import re
import time
from typing import AsyncIterator

from models import AnalysisRequest, AnalysisResponse, Issue, ResponseMetadata
from prompt import ACCESSIBILITY_AGENT_PROMPT, build_analysis_prompt

logger = logging.getLogger(__name__)


class AccessibilityAnalyzer:
    """Analyzes TalkBack utterances for accessibility issues using a local LLM via Ollama."""

    def __init__(self, model: str = "phi4:14b-q4_K_M", ollama_host: str = "http://localhost:11434"):
        self.model = model
        self.ollama_host = ollama_host
        self.system_prompt = ACCESSIBILITY_AGENT_PROMPT
        self._ollama = None

    async def _get_ollama(self):
        """Lazy-init ollama client."""
        if self._ollama is None:
            try:
                import ollama
                self._ollama = ollama
            except ImportError:
                logger.warning("ollama package not installed. Analysis will return empty results.")
                return None
        return self._ollama

    async def analyze(self, request: AnalysisRequest) -> AnalysisResponse:
        """Batch analysis of utterances."""
        start_time = time.time()

        ollama = await self._get_ollama()
        if ollama is None:
            return self._empty_response(request, start_time)

        utterance_dicts = [u.model_dump() for u in request.utterances]
        context_dict = request.context.model_dump() if request.context else {}
        prompt = build_analysis_prompt(utterance_dicts, context_dict)

        try:
            response = ollama.chat(
                model=self.model,
                messages=[
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": prompt},
                ],
                format="json",
                options={
                    "temperature": 0.3,
                    "num_predict": 2048,
                },
            )

            content = response["message"]["content"]
            issues = self._parse_issues(content)
            elapsed_ms = int((time.time() - start_time) * 1000)

            return AnalysisResponse(
                issues=issues,
                metadata=ResponseMetadata(
                    model=self.model,
                    inference_time_ms=elapsed_ms,
                    total_utterances=len(request.utterances),
                    issues_found=len(issues),
                ),
            )

        except Exception as e:
            logger.error(f"Analysis failed: {e}")
            return self._empty_response(request, start_time)

    def _parse_issues(self, response_text: str) -> list[Issue]:
        """Parse LLM JSON output into Issue objects."""
        try:
            data = json.loads(response_text)
            return self._issues_from_dicts(data.get("issues", []))
        except json.JSONDecodeError:
            # Fallback: extract JSON from markdown code blocks
            match = re.search(r"```json\n(.*?)\n```", response_text, re.DOTALL)
            if match:
                try:
                    data = json.loads(match.group(1))
                    return self._issues_from_dicts(data.get("issues", []))
                except (json.JSONDecodeError, Exception) as inner:
                    logger.warning(f"Failed to parse extracted JSON: {inner}")
            logger.warning(f"Failed to parse response as JSON: {response_text[:200]}")
            return []

    @staticmethod
    def _normalize_issue(raw: dict) -> dict:
        """Normalize LLM output to match our enum values (case-insensitive)."""
        normalized = dict(raw)
        if "severity" in normalized:
            normalized["severity"] = str(normalized["severity"]).upper()
        if "category" in normalized:
            normalized["category"] = str(normalized["category"]).upper()
        return normalized

    def _issues_from_dicts(self, raw_issues: list[dict]) -> list[Issue]:
        """Convert raw dicts to Issue objects, skipping any that fail validation."""
        issues = []
        for raw in raw_issues:
            try:
                issues.append(Issue(**self._normalize_issue(raw)))
            except Exception as e:
                logger.warning(f"Skipping malformed issue: {e} (data: {raw})")
        return issues

    def _empty_response(self, request: AnalysisRequest, start_time: float) -> AnalysisResponse:
        elapsed_ms = int((time.time() - start_time) * 1000)
        return AnalysisResponse(
            issues=[],
            metadata=ResponseMetadata(
                model=self.model,
                inference_time_ms=elapsed_ms,
                total_utterances=len(request.utterances),
                issues_found=0,
            ),
        )
