"""
Remote skill execution API.

Allows the macOS server to send commands to the Android device,
enabling server-driven automation (e.g., Claude deciding to navigate
to a specific element, collect utterances, or take a screen snapshot).

The flow:
1. Server queues a skill command via POST /skill/execute
2. Android device polls GET /skill/pending (or receives via WebSocket)
3. Device executes the skill using AccessibilityAgent
4. Device reports result via POST /skill/result
5. Server's original request resolves with the result
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from typing import Optional

from pydantic import BaseModel

logger = logging.getLogger(__name__)


class SkillExecRequest(BaseModel):
    """Request to execute a skill on the device."""
    skill_name: str
    parameters: dict = {}
    timeout_ms: int = 30000


class SkillExecResponse(BaseModel):
    """Response from skill execution."""
    request_id: str
    skill_name: str
    success: bool
    message: str = ""
    data: dict = {}
    elapsed_ms: int = 0


class PendingSkill(BaseModel):
    """A skill command waiting for the device to pick up."""
    request_id: str
    skill_name: str
    parameters: dict = {}
    created_at: float = 0


class SkillResultReport(BaseModel):
    """Result reported back by the device after executing a skill."""
    request_id: str
    success: bool
    message: str = ""
    data: dict = {}


class SkillQueue:
    """
    Manages the queue of skill commands between server and device.

    Server side:
      - execute() queues a command and waits for the result
      - get_pending() returns commands waiting for device pickup

    Device side:
      - get_pending() polls for new commands
      - report_result() sends back execution results
    """

    def __init__(self):
        self._pending: dict[str, PendingSkill] = {}
        self._futures: dict[str, asyncio.Future] = {}
        self._results: dict[str, SkillExecResponse] = {}
        self._history: list[SkillExecResponse] = []

    async def execute(self, request: SkillExecRequest) -> SkillExecResponse:
        """
        Queue a skill for execution and wait for the result.
        Called by server-side code (e.g., from an LLM agent loop).
        """
        request_id = str(uuid.uuid4())[:8]
        start_time = time.time()

        pending = PendingSkill(
            request_id=request_id,
            skill_name=request.skill_name,
            parameters=request.parameters,
            created_at=time.time(),
        )
        self._pending[request_id] = pending

        loop = asyncio.get_event_loop()
        future: asyncio.Future = loop.create_future()
        self._futures[request_id] = future

        logger.info(f"Queued skill '{request.skill_name}' as {request_id}")

        try:
            result = await asyncio.wait_for(
                future, timeout=request.timeout_ms / 1000.0
            )
            elapsed = int((time.time() - start_time) * 1000)
            response = SkillExecResponse(
                request_id=request_id,
                skill_name=request.skill_name,
                success=result.success,
                message=result.message,
                data=result.data,
                elapsed_ms=elapsed,
            )
        except asyncio.TimeoutError:
            self._pending.pop(request_id, None)
            self._futures.pop(request_id, None)
            response = SkillExecResponse(
                request_id=request_id,
                skill_name=request.skill_name,
                success=False,
                message=f"Timeout after {request.timeout_ms}ms",
                elapsed_ms=request.timeout_ms,
            )
            logger.warning(f"Skill {request_id} timed out")

        self._history.append(response)
        return response

    def get_pending(self) -> list[PendingSkill]:
        """
        Get all pending skills (called by device polling).
        Removes them from the queue immediately to prevent re-execution.
        """
        pending = list(self._pending.values())
        # Clear pending queue - device has picked them up
        self._pending.clear()
        return pending

    def claim(self, request_id: str) -> Optional[PendingSkill]:
        """
        Claim a pending skill for execution.
        Removes it from the pending queue.
        """
        return self._pending.pop(request_id, None)

    def report_result(self, result: SkillResultReport) -> bool:
        """
        Report the result of a skill execution (called by device).
        Resolves the waiting future on the server side.
        """
        future = self._futures.pop(result.request_id, None)
        if future is None:
            logger.warning(f"No waiting future for {result.request_id}")
            return False

        if not future.done():
            future.set_result(result)
            logger.info(
                f"Skill {result.request_id} completed: "
                f"success={result.success} message='{result.message}'"
            )
        return True

    def get_history(self, limit: int = 50) -> list[SkillExecResponse]:
        """Get recent skill execution history."""
        return self._history[-limit:]

    def clear(self):
        """Clear all pending skills and cancel futures."""
        for future in self._futures.values():
            if not future.done():
                future.cancel()
        self._pending.clear()
        self._futures.clear()


# Singleton
skill_queue = SkillQueue()
