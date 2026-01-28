"""
Model backend abstraction for the gym.

Three backend types:
  - OllamaBackend: local Ollama instance (existing pattern from analyzer.py)
  - ApiBackend: any OpenAI-compatible chat completions endpoint
  - CliBackend: shell out to a CLI tool (claude --print, ollama run, etc.)

Each backend takes a system prompt + user prompt and returns raw text.
The gym runner handles parsing, timing, and comparison.
"""

from __future__ import annotations

import asyncio
import json
import logging
import subprocess
import time
from abc import ABC, abstractmethod
from typing import Optional

from pydantic import BaseModel

logger = logging.getLogger(__name__)


class BackendConfig(BaseModel):
    """Configuration for a model backend."""
    name: str
    backend: str  # "ollama", "api", "cli"
    # Ollama
    model: str = ""
    ollama_host: str = "http://localhost:11434"
    # API (OpenAI-compatible)
    api_url: str = ""  # e.g. https://api.openai.com/v1/chat/completions
    api_headers: dict = {}  # e.g. {"Authorization": "Bearer sk-..."}
    api_model: str = ""  # e.g. gpt-4o, claude-sonnet-4-20250514
    api_extra_body: dict = {}  # extra fields in request body
    # CLI
    command: list[str] = []  # e.g. ["claude", "--print"]
    timeout_s: int = 120


class BackendResult(BaseModel):
    """Result from a single backend invocation."""
    backend_name: str
    raw_output: str
    latency_ms: int
    success: bool
    error: str = ""


class ModelBackend(ABC):
    """Abstract base for LLM backends."""

    def __init__(self, config: BackendConfig):
        self.config = config

    @property
    def name(self) -> str:
        return self.config.name

    @abstractmethod
    async def invoke(self, system_prompt: str, user_prompt: str) -> BackendResult:
        """Send prompts to the backend and return raw text output."""
        ...


class OllamaBackend(ModelBackend):
    """Local Ollama instance."""

    async def invoke(self, system_prompt: str, user_prompt: str) -> BackendResult:
        start = time.time()
        try:
            import ollama
            response = ollama.chat(
                model=self.config.model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                format="json",
                options={"temperature": 0.3, "num_predict": 2048},
            )
            raw = response["message"]["content"]
            return BackendResult(
                backend_name=self.name,
                raw_output=raw,
                latency_ms=int((time.time() - start) * 1000),
                success=True,
            )
        except ImportError:
            return BackendResult(
                backend_name=self.name,
                raw_output="",
                latency_ms=int((time.time() - start) * 1000),
                success=False,
                error="ollama package not installed",
            )
        except Exception as e:
            return BackendResult(
                backend_name=self.name,
                raw_output="",
                latency_ms=int((time.time() - start) * 1000),
                success=False,
                error=str(e),
            )


class ApiBackend(ModelBackend):
    """
    OpenAI-compatible chat completions API.

    Works with:
    - OpenAI: url=https://api.openai.com/v1/chat/completions
    - Anthropic (via OpenAI compat): url=https://api.anthropic.com/v1/messages
    - Any OpenAI-compatible endpoint (LiteLLM, vLLM, Together, etc.)
    """

    async def invoke(self, system_prompt: str, user_prompt: str) -> BackendResult:
        start = time.time()
        try:
            import httpx
        except ImportError:
            return BackendResult(
                backend_name=self.name,
                raw_output="",
                latency_ms=int((time.time() - start) * 1000),
                success=False,
                error="httpx package not installed (pip install httpx)",
            )

        url = self.config.api_url
        headers = {
            "Content-Type": "application/json",
            **self.config.api_headers,
        }

        # Detect Anthropic Messages API vs OpenAI-compatible
        is_anthropic = "anthropic.com" in url and "/v1/messages" in url

        if is_anthropic:
            body = {
                "model": self.config.api_model,
                "max_tokens": 2048,
                "system": system_prompt,
                "messages": [{"role": "user", "content": user_prompt}],
                **self.config.api_extra_body,
            }
        else:
            body = {
                "model": self.config.api_model,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                "temperature": 0.3,
                "max_tokens": 2048,
                **self.config.api_extra_body,
            }

        try:
            async with httpx.AsyncClient(timeout=self.config.timeout_s) as client:
                resp = await client.post(url, headers=headers, json=body)
                resp.raise_for_status()
                data = resp.json()

            # Extract content from response
            if is_anthropic:
                raw = data.get("content", [{}])[0].get("text", "")
            else:
                raw = data.get("choices", [{}])[0].get("message", {}).get("content", "")

            return BackendResult(
                backend_name=self.name,
                raw_output=raw,
                latency_ms=int((time.time() - start) * 1000),
                success=True,
            )
        except Exception as e:
            return BackendResult(
                backend_name=self.name,
                raw_output="",
                latency_ms=int((time.time() - start) * 1000),
                success=False,
                error=str(e),
            )


class CliBackend(ModelBackend):
    """
    Shell out to a CLI tool.

    The combined prompt (system + user) is piped to stdin.
    stdout is captured as the response.

    Examples:
      command: ["claude", "--print"]
      command: ["ollama", "run", "phi4"]
      command: ["cat"]  (echo backend for testing)
    """

    async def invoke(self, system_prompt: str, user_prompt: str) -> BackendResult:
        start = time.time()
        combined = f"{system_prompt}\n\n---\n\n{user_prompt}"

        try:
            proc = await asyncio.create_subprocess_exec(
                *self.config.command,
                stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await asyncio.wait_for(
                proc.communicate(input=combined.encode()),
                timeout=self.config.timeout_s,
            )

            if proc.returncode != 0:
                return BackendResult(
                    backend_name=self.name,
                    raw_output=stdout.decode(errors="replace"),
                    latency_ms=int((time.time() - start) * 1000),
                    success=False,
                    error=f"Exit code {proc.returncode}: {stderr.decode(errors='replace')[:500]}",
                )

            return BackendResult(
                backend_name=self.name,
                raw_output=stdout.decode(errors="replace"),
                latency_ms=int((time.time() - start) * 1000),
                success=True,
            )
        except asyncio.TimeoutError:
            return BackendResult(
                backend_name=self.name,
                raw_output="",
                latency_ms=int((time.time() - start) * 1000),
                success=False,
                error=f"Timeout after {self.config.timeout_s}s",
            )
        except Exception as e:
            return BackendResult(
                backend_name=self.name,
                raw_output="",
                latency_ms=int((time.time() - start) * 1000),
                success=False,
                error=str(e),
            )


def create_backend(config: BackendConfig) -> ModelBackend:
    """Factory: create a backend from config."""
    match config.backend:
        case "ollama":
            return OllamaBackend(config)
        case "api":
            return ApiBackend(config)
        case "cli":
            return CliBackend(config)
        case _:
            raise ValueError(f"Unknown backend type: {config.backend}")
