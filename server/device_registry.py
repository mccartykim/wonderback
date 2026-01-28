"""
Device registration and approval for shared-secret auth.

Flow:
1. Device connects and calls POST /device/register with its identity
2. Server creates a pending device entry, visible on the dashboard
3. Dashboard user clicks "Approve" → server generates a token
4. Device picks up the token via its existing /settings poll
5. Device attaches the token as X-Agent-Token on all subsequent requests
6. Auth middleware validates the token on mutation endpoints

Tokens and device state are in-memory (reset on server restart).
This is acceptable for a dev/testing tool.
"""

from __future__ import annotations

import logging
import os
import secrets
import time
from enum import Enum
from typing import Optional

from pydantic import BaseModel

logger = logging.getLogger(__name__)


class DeviceStatus(str, Enum):
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"


class DeviceInfo(BaseModel):
    """A registered device."""
    device_id: str
    device_name: str = ""
    device_serial: str = ""
    status: DeviceStatus = DeviceStatus.PENDING
    auth_token: Optional[str] = None
    registered_at: float = 0.0
    approved_at: Optional[float] = None


class DeviceRegistry:
    """
    In-memory registry of connected devices and their auth tokens.

    Thread-safe for single-process async use (FastAPI default).
    """

    def __init__(self):
        self._devices: dict[str, DeviceInfo] = {}
        self._tokens: dict[str, str] = {}  # token -> device_id (reverse lookup)
        # Optional: env var or config override for a pre-shared token
        self._static_token: Optional[str] = os.environ.get("AGENT_AUTH_TOKEN")
        if self._static_token:
            logger.info("Using static auth token from AGENT_AUTH_TOKEN env var")

    @property
    def auth_enabled(self) -> bool:
        """Auth is enabled if there's a static token or any approved device."""
        return self._static_token is not None or any(
            d.status == DeviceStatus.APPROVED for d in self._devices.values()
        )

    def register(self, device_name: str, device_serial: str = "") -> DeviceInfo:
        """
        Register a new device or return existing one.

        If the device_serial matches an existing device, return that instead
        of creating a duplicate.
        """
        # Check for existing device by serial
        if device_serial:
            for device in self._devices.values():
                if device.device_serial == device_serial:
                    logger.info(f"Device re-registered: {device.device_id} ({device_name})")
                    return device

        device_id = secrets.token_hex(4)  # 8-char hex ID
        device = DeviceInfo(
            device_id=device_id,
            device_name=device_name,
            device_serial=device_serial,
            status=DeviceStatus.PENDING,
            registered_at=time.time(),
        )
        self._devices[device_id] = device
        logger.info(f"Device registered: {device_id} ({device_name})")
        return device

    def approve(self, device_id: str) -> Optional[DeviceInfo]:
        """Approve a device and generate its auth token."""
        device = self._devices.get(device_id)
        if device is None:
            return None

        if device.status == DeviceStatus.APPROVED and device.auth_token:
            return device  # Already approved

        token = secrets.token_hex(16)  # 32-char hex token
        device.status = DeviceStatus.APPROVED
        device.auth_token = token
        device.approved_at = time.time()
        self._tokens[token] = device_id
        logger.info(f"Device approved: {device_id} ({device.device_name})")
        return device

    def reject(self, device_id: str) -> Optional[DeviceInfo]:
        """Reject a device registration."""
        device = self._devices.get(device_id)
        if device is None:
            return None

        # Revoke token if previously approved
        if device.auth_token and device.auth_token in self._tokens:
            del self._tokens[device.auth_token]

        device.status = DeviceStatus.REJECTED
        device.auth_token = None
        logger.info(f"Device rejected: {device_id} ({device.device_name})")
        return device

    def validate_token(self, token: str) -> bool:
        """Check if a token is valid (from an approved device or static env)."""
        if self._static_token and token == self._static_token:
            return True
        return token in self._tokens

    def get_device(self, device_id: str) -> Optional[DeviceInfo]:
        """Get device by ID."""
        return self._devices.get(device_id)

    def get_device_by_token(self, token: str) -> Optional[DeviceInfo]:
        """Get device by its auth token."""
        device_id = self._tokens.get(token)
        if device_id:
            return self._devices.get(device_id)
        return None

    def get_token_for_device(self, device_id: str) -> Optional[str]:
        """Get the auth token for a device, if approved."""
        device = self._devices.get(device_id)
        if device and device.status == DeviceStatus.APPROVED:
            return device.auth_token
        return None

    def get_pending(self) -> list[DeviceInfo]:
        """Get all pending devices."""
        return [d for d in self._devices.values() if d.status == DeviceStatus.PENDING]

    def get_approved(self) -> list[DeviceInfo]:
        """Get all approved devices."""
        return [d for d in self._devices.values() if d.status == DeviceStatus.APPROVED]

    def get_all(self) -> list[DeviceInfo]:
        """Get all registered devices."""
        return list(self._devices.values())

    def clear(self):
        """Clear all devices and tokens."""
        self._devices.clear()
        self._tokens.clear()


# Singleton
device_registry = DeviceRegistry()
