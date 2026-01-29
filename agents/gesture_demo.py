#!/usr/bin/env python3
"""
Gesture Demo - Demonstrates TalkBack gesture navigation via server API.

This script sends gesture commands to the Android device through the server,
showing how the agent can navigate using only gestures (no accessibility tree access).
"""

import argparse
import requests
import sys
import time


def get_device_token(server_url: str) -> str:
    """Get the auth token for the first approved device."""
    try:
        resp = requests.get(f"{server_url}/device/all", timeout=5)
        resp.raise_for_status()
        devices = resp.json()
        if not devices:
            print("❌ No devices registered. Please run the demo setup first.")
            sys.exit(1)
        
        device = devices[0]
        if device["status"] != "approved":
            print(f"❌ Device {device['device_id']} is not approved yet.")
            print("   Please approve it in the dashboard at http://localhost:8080")
            sys.exit(1)
        
        return device["auth_token"]
    except Exception as e:
        print(f"❌ Failed to get device token: {e}")
        sys.exit(1)


def execute_gesture(server_url: str, token: str, gesture: str, delay: float = 1.5):
    """Execute a gesture command and wait for completion."""
    print(f"  🎯 {gesture}...", end="", flush=True)
    
    try:
        resp = requests.post(
            f"{server_url}/skill/execute",
            headers={
                "Content-Type": "application/json",
                "X-Agent-Token": token,
            },
            json={
                "skill_name": gesture,
                "parameters": {},
                "timeout_ms": 5000,
            },
            timeout=10,
        )
        resp.raise_for_status()
        result = resp.json()
        
        if result["success"]:
            print(f" ✅ ({result['elapsed_ms']}ms)")
        else:
            print(f" ❌ {result['message']}")
        
        time.sleep(delay)
        return result["success"]
    
    except Exception as e:
        print(f" ❌ Error: {e}")
        return False


def sudoku_demo(server_url: str, token: str, delay: float):
    """Demo navigating the Sudoku app using gestures."""
    print("\n📱 Sudoku Navigation Demo")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    
    gestures = [
        ("swipe_right", "Navigate to next element"),
        ("swipe_right", "Continue navigating"),
        ("swipe_right", "Keep exploring"),
        ("swipe_left", "Go back one element"),
        ("swipe_right", "Forward again"),
        ("double_tap", "Activate current element"),
    ]
    
    success_count = 0
    for gesture, description in gestures:
        print(f"  {description}")
        if execute_gesture(server_url, token, gesture, delay):
            success_count += 1
    
    print(f"\n✨ Demo complete: {success_count}/{len(gestures)} gestures succeeded\n")


def main():
    parser = argparse.ArgumentParser(description="TalkBack Gesture Demo")
    parser.add_argument(
        "--server",
        default="http://localhost:8080",
        help="Server URL (default: http://localhost:8080)",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=1.5,
        help="Delay between gestures in seconds (default: 1.5)",
    )
    parser.add_argument(
        "--mode",
        choices=["sudoku"],
        default="sudoku",
        help="Demo mode (default: sudoku)",
    )
    
    args = parser.parse_args()
    
    print(f"\n🚀 TalkBack Gesture Demo")
    print(f"   Server: {args.server}")
    print(f"   Delay: {args.delay}s between gestures\n")
    
    token = get_device_token(args.server)
    print(f"✅ Connected to device (token: {token[:8]}...)\n")
    
    if args.mode == "sudoku":
        sudoku_demo(args.server, token, args.delay)


if __name__ == "__main__":
    main()
