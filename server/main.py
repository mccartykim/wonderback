#!/usr/bin/env python3
"""
TalkBack Agent Analysis Server

A FastAPI server that receives TalkBack utterances from an Android device
and analyzes them for accessibility issues using a local LLM via Ollama.

Usage:
    python main.py
    python main.py --port 8080 --model phi4:14b-q4_K_M
    python main.py --no-mdns  # Disable mDNS/Bonjour discovery

Setup:
    1. Install Ollama: brew install ollama (macOS) or see https://ollama.ai
    2. Pull a model: ollama pull phi4:14b-q4_K_M
    3. pip install -r requirements.txt
    4. python main.py
    5. On device: adb reverse tcp:8080 tcp:8080
"""

from __future__ import annotations

import argparse
import logging
import socket
import sys
import time
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse

from analyzer import AccessibilityAnalyzer
from models import AnalysisRequest, AnalysisResponse, SkillCommand, SkillResult

logger = logging.getLogger("talkback-agent-server")

# Globals
analyzer: AccessibilityAnalyzer | None = None
zeroconf_instance = None
start_time: float = 0


def register_mdns(port: int) -> None:
    """Register mDNS/Bonjour service for device discovery."""
    global zeroconf_instance
    try:
        from zeroconf import ServiceInfo, Zeroconf

        zeroconf_instance = Zeroconf()
        hostname = socket.gethostname()
        ip = socket.gethostbyname(hostname)

        service_info = ServiceInfo(
            "_talkback-agent._tcp.local.",
            f"TalkBack Agent Server._talkback-agent._tcp.local.",
            addresses=[socket.inet_aton(ip)],
            port=port,
            properties={"version": "1.0", "hostname": hostname},
        )

        zeroconf_instance.register_service(service_info)
        logger.info(f"mDNS: Server discoverable as '{hostname}' at {ip}:{port}")
    except ImportError:
        logger.warning("zeroconf not installed, mDNS discovery disabled")
    except Exception as e:
        logger.warning(f"mDNS registration failed: {e}")


def unregister_mdns() -> None:
    """Unregister mDNS service."""
    global zeroconf_instance
    if zeroconf_instance:
        zeroconf_instance.unregister_all_services()
        zeroconf_instance.close()
        zeroconf_instance = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Server startup and shutdown."""
    global analyzer, start_time
    start_time = time.time()

    # Initialize analyzer
    model = app.state.model_name if hasattr(app.state, "model_name") else "phi4:14b-q4_K_M"
    analyzer = AccessibilityAnalyzer(model=model)
    logger.info(f"Analyzer initialized with model: {model}")

    # Register mDNS if enabled
    if getattr(app.state, "mdns_enabled", True):
        port = getattr(app.state, "port", 8080)
        register_mdns(port)

    yield

    # Shutdown
    unregister_mdns()
    logger.info("Server shutting down")


app = FastAPI(
    title="TalkBack Agent Analysis Server",
    description="Analyzes TalkBack screen reader utterances for accessibility issues",
    version="1.0.0",
    lifespan=lifespan,
)


# -- Endpoints --


@app.get("/health")
async def health():
    """Health check endpoint. Used by Android client for connection verification."""
    uptime = int(time.time() - start_time)
    return {
        "status": "ok",
        "model": analyzer.model if analyzer else "not initialized",
        "uptime_seconds": uptime,
    }


@app.post("/analyze", response_model=AnalysisResponse)
async def analyze(request: AnalysisRequest):
    """
    Analyze a batch of TalkBack utterances for accessibility issues.

    The Android client sends buffered utterances along with navigation context.
    The server runs LLM inference and returns structured issue reports.
    """
    if analyzer is None:
        return JSONResponse(
            status_code=503,
            content={"error": "Analyzer not initialized"},
        )

    logger.info(
        f"Analyzing {len(request.utterances)} utterances "
        f"(trigger: {request.context.trigger})"
    )

    response = await analyzer.analyze(request)

    logger.info(
        f"Found {len(response.issues)} issues "
        f"(inference: {response.metadata.inference_time_ms if response.metadata else '?'}ms)"
    )

    return response


@app.websocket("/stream")
async def stream_analysis(websocket: WebSocket):
    """
    WebSocket endpoint for streaming analysis.
    The client sends utterance events as they occur, and receives
    analysis results as they're generated.
    """
    await websocket.accept()
    buffer: list = []

    try:
        while True:
            data = await websocket.receive_json()
            msg_type = data.get("type", "")

            if msg_type == "utterance":
                buffer.append(data.get("event", {}))

                # Analyze when we have enough utterances or on screen change
                event = data.get("event", {})
                nav_type = event.get("navigation", "UNKNOWN")

                if len(buffer) >= 20 or nav_type in ("SCREEN_CHANGE", "WINDOW_CHANGE"):
                    if analyzer and buffer:
                        request = AnalysisRequest(
                            utterances=buffer,  # type: ignore
                            context=data.get("context", {}),  # type: ignore
                        )
                        response = await analyzer.analyze(request)
                        for issue in response.issues:
                            await websocket.send_json(
                                {"type": "issue", "data": issue.model_dump()}
                            )
                        buffer.clear()

            elif msg_type == "ping":
                await websocket.send_json({"type": "pong"})

    except WebSocketDisconnect:
        logger.info("WebSocket client disconnected")


@app.post("/command")
async def execute_command(command: SkillCommand) -> SkillResult:
    """
    Execute a skill command. This is a placeholder for future
    server-initiated actions on the device.
    """
    logger.info(f"Received command: {command.skill_name}")
    return SkillResult(
        success=False,
        message="Server-initiated commands not yet implemented",
    )


def main():
    parser = argparse.ArgumentParser(description="TalkBack Agent Analysis Server")
    parser.add_argument("--host", default="0.0.0.0", help="Bind host (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8080, help="Bind port (default: 8080)")
    parser.add_argument(
        "--model",
        default="phi4:14b-q4_K_M",
        help="Ollama model name (default: phi4:14b-q4_K_M)",
    )
    parser.add_argument("--no-mdns", action="store_true", help="Disable mDNS/Bonjour discovery")
    parser.add_argument("--log-level", default="info", help="Log level (default: info)")

    args = parser.parse_args()

    logging.basicConfig(
        level=getattr(logging, args.log_level.upper()),
        format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
    )

    # Pass config to app state
    app.state.model_name = args.model
    app.state.mdns_enabled = not args.no_mdns
    app.state.port = args.port

    logger.info(f"Starting server on {args.host}:{args.port}")
    logger.info(f"Model: {args.model}")
    logger.info(f"mDNS: {'enabled' if not args.no_mdns else 'disabled'}")
    logger.info("")
    logger.info("Setup instructions:")
    logger.info("  1. Connect Android device via USB")
    logger.info(f"  2. Run: adb reverse tcp:{args.port} tcp:{args.port}")
    logger.info("  3. Enable TalkBack Agent in Accessibility settings")
    logger.info("")

    uvicorn.run(
        app,
        host=args.host,
        port=args.port,
        log_level=args.log_level,
    )


if __name__ == "__main__":
    main()
