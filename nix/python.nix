# Python environment for the analysis server
{ pkgs }:

pkgs.python312.withPackages (ps: with ps; [
  fastapi
  uvicorn
  pydantic
  pyyaml
  pytest
  httpx
  websockets
  anyio
  pytest-asyncio
  requests  # For gesture_demo.py and other HTTP clients
])
