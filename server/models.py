"""Pydantic models matching the Android agent's data classes."""

from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel


# -- Utterance models (from Android) --


class ElementInfo(BaseModel):
    bounds: dict = {}  # {left, top, right, bottom}
    class_name: str = ""
    content_description: Optional[str] = None
    text: Optional[str] = None
    view_id_resource_name: Optional[str] = None
    is_clickable: bool = False
    is_focusable: bool = False
    is_checkable: bool = False
    is_checked: bool = False
    is_editable: bool = False
    is_scrollable: bool = False
    is_enabled: bool = True
    is_selected: bool = False
    state_description: Optional[str] = None
    role_description: Optional[str] = None
    child_count: int = 0
    drawing_order: int = -1


class NavigationType(str, Enum):
    SWIPE_RIGHT = "SWIPE_RIGHT"
    SWIPE_LEFT = "SWIPE_LEFT"
    SWIPE_UP = "SWIPE_UP"
    SWIPE_DOWN = "SWIPE_DOWN"
    TAP = "TAP"
    DOUBLE_TAP = "DOUBLE_TAP"
    LONG_PRESS = "LONG_PRESS"
    SCREEN_CHANGE = "SCREEN_CHANGE"
    SCROLL = "SCROLL"
    FOCUS_CHANGE = "FOCUS_CHANGE"
    WINDOW_CHANGE = "WINDOW_CHANGE"
    ANNOUNCEMENT = "ANNOUNCEMENT"
    KEY_EVENT = "KEY_EVENT"
    UNKNOWN = "UNKNOWN"


class ScreenContext(BaseModel):
    package_name: str = ""
    activity_name: str = ""
    window_title: Optional[str] = None
    window_id: int = -1
    is_scrollable: bool = False


class UtteranceEvent(BaseModel):
    text: str
    timestamp: int
    element: ElementInfo = ElementInfo()
    navigation: NavigationType = NavigationType.UNKNOWN
    screen: ScreenContext = ScreenContext()
    queue_mode: int = 0
    flags: int = 0


# -- Analysis models --


class TriggerType(str, Enum):
    SCREEN_CHANGE = "SCREEN_CHANGE"
    BUFFER_FULL = "BUFFER_FULL"
    MANUAL = "MANUAL"
    CONTINUOUS = "CONTINUOUS"
    SKILL_REQUEST = "SKILL_REQUEST"


class RequestContext(BaseModel):
    trigger: TriggerType = TriggerType.MANUAL
    previous_screen: Optional[str] = None
    timestamp: int = 0


class IssueSeverity(str, Enum):
    ERROR = "ERROR"
    WARNING = "WARNING"
    SUGGESTION = "SUGGESTION"


class IssueCategory(str, Enum):
    LABEL_QUALITY = "LABEL_QUALITY"
    STRUCTURE = "STRUCTURE"
    CONTEXT = "CONTEXT"
    NAVIGATION = "NAVIGATION"


class Issue(BaseModel):
    severity: IssueSeverity
    category: IssueCategory
    element_index: int = -1
    utterance: str = ""
    issue: str
    explanation: str = ""
    suggestion: str = ""
    timestamp: int = 0


class ResponseMetadata(BaseModel):
    model: str = ""
    inference_time_ms: int = 0
    total_utterances: int = 0
    issues_found: int = 0


class AnalysisRequest(BaseModel):
    utterances: list[UtteranceEvent]
    context: RequestContext = RequestContext()
    previous_issues: list[Issue] = []


class AnalysisResponse(BaseModel):
    issues: list[Issue] = []
    metadata: Optional[ResponseMetadata] = None


# -- Skill/command models --


class SkillCommand(BaseModel):
    """Command from the server to execute a skill on device."""
    skill_name: str
    parameters: dict = {}


class SkillResult(BaseModel):
    """Result from skill execution on device."""
    success: bool
    message: str = ""
    data: dict = {}
