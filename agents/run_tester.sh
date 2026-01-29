#!/usr/bin/env bash
# Wrapper to run Tester Agent in Nix environment with ADB access

cd /home/kimb/projects/wonderback
nix develop --command python3 agents/tester_agent.py "$@"
