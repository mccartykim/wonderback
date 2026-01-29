"""
Server-controlled device settings.

Allows the server (or dashboard user) to remotely toggle device behaviors
like TTS suppression, gesture injection, trigger mode, etc.

The Android device polls GET /settings to pick up changes.
The server or dashboard can PATCH /settings to update values.
"""

from __future__ import annotations

import logging
import time
from typing import Optional

from pydantic import BaseModel

logger = logging.getLogger(__name__)


class DeviceSettings(BaseModel):
    """
    Settings that the server can push to the device.

    The device polls these periodically and applies changes locally.
    This allows the server/dashboard to control device behavior without
    needing a push channel.
    """

    # TTS suppression: when True, device captures utterances but does not speak them
    tts_suppressed: bool = False

    # Gesture injection: when True, device allows programmatic gesture dispatch
    gesture_injection_enabled: bool = False

    # Analysis trigger mode
    trigger_mode: str = "SCREEN_CHANGE"  # SCREEN_CHANGE | BUFFER_FULL | MANUAL | CONTINUOUS

    # Utterance buffer size before auto-analysis
    buffer_size: int = 20

    # Minimum severity filter for notifications
    severity_filter: str = "SUGGESTION"  # SUGGESTION | WARNING | ERROR

    # Whether to show on-device notifications for issues
    show_notifications: bool = True

    # Whether to capture full element metadata
    capture_full_metadata: bool = True

    # Debug logging on device
    debug_logging: bool = False

    # Revision counter — incremented on every change so device knows to re-fetch
    revision: int = 0

    # Last modified timestamp
    last_modified: float = 0.0


class DeviceSettingsManager:
    """Manages server-side device settings with change tracking."""

    def __init__(self):
        self._settings = DeviceSettings()

    @property
    def current(self) -> DeviceSettings:
        return self._settings

    def update(self, **kwargs) -> DeviceSettings:
        """Update one or more settings. Bumps revision automatically."""
        changed = False
        for key, value in kwargs.items():
            if hasattr(self._settings, key) and key not in ("revision", "last_modified"):
                current_val = getattr(self._settings, key)
                if current_val != value:
                    setattr(self._settings, key, value)
                    changed = True
                    logger.info(f"Setting changed: {key} = {value} (was {current_val})")

        if changed:
            self._settings.revision += 1
            self._settings.last_modified = time.time()

        return self._settings

    def get_if_newer(self, client_revision: int) -> Optional[DeviceSettings]:
        """Return settings only if they've changed since client_revision."""
        if self._settings.revision > client_revision:
            return self._settings
        return None


# Singleton
device_settings = DeviceSettingsManager()
