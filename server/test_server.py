"""Tests for the TalkBack Agent analysis server."""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi.testclient import TestClient

import main as srv
from analyzer import AccessibilityAnalyzer
from device_settings import DeviceSettings, DeviceSettingsManager, device_settings
from main import app
from models import (
    AnalysisRequest,
    AnalysisResponse,
    Issue,
    IssueCategory,
    IssueSeverity,
    RequestContext,
    ResponseMetadata,
    TriggerType,
    UtteranceEvent,
)
from prompt import ACCESSIBILITY_AGENT_PROMPT, build_analysis_prompt
from session import Session, SessionManager, session_manager
from skills import SkillExecRequest, SkillQueue, skill_queue


# ── Fixtures ──────────────────────────────────────────────────────


@pytest.fixture(autouse=True)
def init_server():
    """Initialize server globals for each test."""
    srv.analyzer = AccessibilityAnalyzer(model="test-model")
    srv.start_time = __import__("time").time()
    yield
    srv.analyzer = None


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def sample_utterances():
    return [
        {
            "text": "Search. Button. Double tap to activate",
            "timestamp": 1706559123456,
            "element": {
                "class_name": "android.widget.ImageButton",
                "content_description": "Search",
                "is_clickable": True,
                "is_focusable": True,
            },
            "navigation": "SWIPE_RIGHT",
            "screen": {
                "package_name": "com.example.app",
                "activity_name": "MainActivity",
            },
        },
        {
            "text": "Image",
            "timestamp": 1706559123556,
            "element": {
                "class_name": "android.widget.ImageView",
                "is_clickable": True,
            },
            "navigation": "SWIPE_RIGHT",
            "screen": {
                "package_name": "com.example.app",
                "activity_name": "MainActivity",
            },
        },
        {
            "text": "Button",
            "timestamp": 1706559123656,
            "element": {
                "class_name": "android.widget.Button",
                "is_clickable": True,
            },
            "navigation": "SWIPE_RIGHT",
            "screen": {
                "package_name": "com.example.app",
                "activity_name": "MainActivity",
            },
        },
    ]


MOCK_LLM_RESPONSE_JSON = json.dumps(
    {
        "issues": [
            {
                "severity": "warning",
                "category": "label_quality",
                "element_index": 1,
                "utterance": "Image",
                "issue": "Generic image label",
                "explanation": "The image has no meaningful content description.",
                "suggestion": "Add a contentDescription that describes the image purpose.",
            },
            {
                "severity": "error",
                "category": "label_quality",
                "element_index": 2,
                "utterance": "Button",
                "issue": "Generic button label",
                "explanation": "Button has no text or content description.",
                "suggestion": "Add android:text or android:contentDescription.",
            },
        ]
    }
)

MOCK_LLM_RESPONSE_MARKDOWN = """Here are the issues I found:

```json
{
  "issues": [
    {
      "severity": "suggestion",
      "category": "navigation",
      "element_index": 0,
      "utterance": "Search. Button",
      "issue": "Missing heading structure",
      "explanation": "No headings to organize content.",
      "suggestion": "Add heading roles to section titles."
    }
  ]
}
```

These are the main accessibility concerns."""


# ── Model Tests ───────────────────────────────────────────────────


class TestModels:
    def test_utterance_event_defaults(self):
        event = UtteranceEvent(text="Hello", timestamp=123)
        assert event.text == "Hello"
        assert event.navigation == "UNKNOWN"
        assert event.element.class_name == ""
        assert event.screen.package_name == ""

    def test_utterance_event_full(self):
        event = UtteranceEvent(
            text="Submit. Button",
            timestamp=999,
            element={"class_name": "android.widget.Button", "is_clickable": True},
            navigation="TAP",
            screen={"package_name": "com.test", "activity_name": "Main"},
        )
        assert event.element.is_clickable is True
        assert event.screen.package_name == "com.test"

    def test_issue_creation(self):
        issue = Issue(
            severity=IssueSeverity.ERROR,
            category=IssueCategory.LABEL_QUALITY,
            issue="Missing label",
        )
        assert issue.severity == IssueSeverity.ERROR
        assert issue.element_index == -1

    def test_analysis_request_serialization(self):
        req = AnalysisRequest(
            utterances=[UtteranceEvent(text="Test", timestamp=1)],
            context=RequestContext(trigger=TriggerType.SCREEN_CHANGE),
        )
        data = req.model_dump()
        assert len(data["utterances"]) == 1
        assert data["context"]["trigger"] == "SCREEN_CHANGE"

    def test_analysis_response_serialization(self):
        resp = AnalysisResponse(
            issues=[
                Issue(
                    severity=IssueSeverity.WARNING,
                    category=IssueCategory.STRUCTURE,
                    issue="Test issue",
                )
            ],
            metadata=ResponseMetadata(model="phi4", inference_time_ms=500),
        )
        data = resp.model_dump()
        assert data["issues"][0]["severity"] == "WARNING"
        assert data["metadata"]["model"] == "phi4"


# ── Prompt Tests ──────────────────────────────────────────────────


class TestPrompt:
    def test_system_prompt_not_empty(self):
        assert len(ACCESSIBILITY_AGENT_PROMPT) > 100

    def test_system_prompt_has_categories(self):
        for cat in ["label_quality", "structure", "navigation", "context"]:
            assert cat in ACCESSIBILITY_AGENT_PROMPT

    def test_build_prompt_includes_context(self):
        prompt = build_analysis_prompt(
            [{"text": "Hello", "element": {"class_name": "Button"}, "navigation": "TAP"}],
            {"package_name": "com.foo", "activity_name": "BarActivity"},
        )
        assert "com.foo" in prompt
        assert "BarActivity" in prompt
        assert "Hello" in prompt
        assert "Button" in prompt

    def test_build_prompt_handles_empty_context(self):
        prompt = build_analysis_prompt(
            [{"text": "Test", "element": {}, "navigation": "UNKNOWN"}],
            None,
        )
        assert "Test" in prompt
        assert "Unknown" in prompt

    def test_build_prompt_multiple_utterances(self):
        utterances = [
            {"text": f"Item {i}", "element": {"class_name": "View"}, "navigation": "SWIPE_RIGHT"}
            for i in range(5)
        ]
        prompt = build_analysis_prompt(utterances, {})
        for i in range(5):
            assert f"Item {i}" in prompt
            assert f"{i}." in prompt


# ── Analyzer Tests ────────────────────────────────────────────────


class TestAnalyzer:
    def test_parse_clean_json(self):
        analyzer = AccessibilityAnalyzer(model="test")
        issues = analyzer._parse_issues(MOCK_LLM_RESPONSE_JSON)
        assert len(issues) == 2
        assert issues[0].severity == IssueSeverity.WARNING
        assert issues[0].category == IssueCategory.LABEL_QUALITY
        assert issues[0].issue == "Generic image label"
        assert issues[1].severity == IssueSeverity.ERROR

    def test_parse_markdown_wrapped_json(self):
        analyzer = AccessibilityAnalyzer(model="test")
        issues = analyzer._parse_issues(MOCK_LLM_RESPONSE_MARKDOWN)
        assert len(issues) == 1
        assert issues[0].severity == IssueSeverity.SUGGESTION
        assert issues[0].category == IssueCategory.NAVIGATION

    def test_parse_invalid_json(self):
        analyzer = AccessibilityAnalyzer(model="test")
        issues = analyzer._parse_issues("this is not json at all")
        assert issues == []

    def test_parse_empty_issues(self):
        analyzer = AccessibilityAnalyzer(model="test")
        issues = analyzer._parse_issues('{"issues": []}')
        assert issues == []

    def test_parse_missing_issues_key(self):
        analyzer = AccessibilityAnalyzer(model="test")
        issues = analyzer._parse_issues('{"results": []}')
        assert issues == []

    @pytest.mark.asyncio
    async def test_analyze_with_mock_ollama(self):
        analyzer = AccessibilityAnalyzer(model="test-model")

        mock_ollama = MagicMock()
        mock_ollama.chat = MagicMock(
            return_value={"message": {"content": MOCK_LLM_RESPONSE_JSON}}
        )
        analyzer._ollama = mock_ollama

        request = AnalysisRequest(
            utterances=[
                UtteranceEvent(text="Image", timestamp=1),
                UtteranceEvent(text="Button", timestamp=2),
            ],
            context=RequestContext(trigger=TriggerType.SCREEN_CHANGE),
        )

        response = await analyzer.analyze(request)

        assert len(response.issues) == 2
        assert response.metadata is not None
        assert response.metadata.model == "test-model"
        assert response.metadata.total_utterances == 2
        assert response.metadata.issues_found == 2
        assert response.metadata.inference_time_ms >= 0

        # Verify ollama was called correctly
        mock_ollama.chat.assert_called_once()
        call_args = mock_ollama.chat.call_args
        assert call_args.kwargs["model"] == "test-model"
        messages = call_args.kwargs["messages"]
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        assert "Image" in messages[1]["content"]

    @pytest.mark.asyncio
    async def test_analyze_ollama_not_installed(self):
        analyzer = AccessibilityAnalyzer(model="test")
        # _ollama stays None -> graceful degradation
        analyzer._ollama = None

        # Mock the import to fail
        async def mock_get_ollama():
            return None

        analyzer._get_ollama = mock_get_ollama

        request = AnalysisRequest(
            utterances=[UtteranceEvent(text="Test", timestamp=1)],
        )
        response = await analyzer.analyze(request)
        assert response.issues == []
        assert response.metadata.total_utterances == 1

    @pytest.mark.asyncio
    async def test_analyze_ollama_error(self):
        analyzer = AccessibilityAnalyzer(model="test")

        mock_ollama = MagicMock()
        mock_ollama.chat = MagicMock(side_effect=Exception("Connection refused"))
        analyzer._ollama = mock_ollama

        request = AnalysisRequest(
            utterances=[UtteranceEvent(text="Test", timestamp=1)],
        )
        response = await analyzer.analyze(request)
        assert response.issues == []


# ── Server Endpoint Tests ─────────────────────────────────────────


class TestEndpoints:
    def test_health(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "ok"
        assert data["model"] == "test-model"

    def test_analyze_basic(self, client, sample_utterances):
        resp = client.post(
            "/analyze",
            json={
                "utterances": sample_utterances,
                "context": {"trigger": "SCREEN_CHANGE", "timestamp": 123},
            },
        )
        assert resp.status_code == 200
        data = resp.json()
        assert "issues" in data
        assert "metadata" in data
        assert data["metadata"]["total_utterances"] == 3

    def test_analyze_empty_utterances(self, client):
        resp = client.post(
            "/analyze",
            json={"utterances": [], "context": {"trigger": "MANUAL"}},
        )
        assert resp.status_code == 200

    def test_analyze_with_previous_issues(self, client, sample_utterances):
        resp = client.post(
            "/analyze",
            json={
                "utterances": sample_utterances,
                "context": {"trigger": "BUFFER_FULL"},
                "previous_issues": [
                    {
                        "severity": "WARNING",
                        "category": "LABEL_QUALITY",
                        "issue": "Previous issue",
                    }
                ],
            },
        )
        assert resp.status_code == 200

    def test_analyze_invalid_payload(self, client):
        resp = client.post("/analyze", json={"utterances": "not a list"})
        assert resp.status_code == 422

    def test_analyze_missing_field(self, client):
        resp = client.post("/analyze", json={})
        assert resp.status_code == 422

    def test_command_endpoint(self, client):
        resp = client.post(
            "/command",
            json={"skill_name": "NavigateToElement", "parameters": {"text": "Submit"}},
        )
        assert resp.status_code == 200
        assert resp.json()["success"] is False  # Not implemented yet

    def test_analyzer_not_initialized(self, client):
        srv.analyzer = None
        resp = client.post(
            "/analyze",
            json={"utterances": [{"text": "Test", "timestamp": 1}]},
        )
        assert resp.status_code == 503

    def test_analyze_all_trigger_types(self, client, sample_utterances):
        for trigger in ["SCREEN_CHANGE", "BUFFER_FULL", "MANUAL", "CONTINUOUS", "SKILL_REQUEST"]:
            resp = client.post(
                "/analyze",
                json={
                    "utterances": sample_utterances[:1],
                    "context": {"trigger": trigger},
                },
            )
            assert resp.status_code == 200, f"Failed for trigger: {trigger}"

    def test_analyze_all_navigation_types(self, client):
        nav_types = [
            "SWIPE_RIGHT", "SWIPE_LEFT", "SWIPE_UP", "SWIPE_DOWN",
            "TAP", "DOUBLE_TAP", "LONG_PRESS", "SCREEN_CHANGE",
            "SCROLL", "FOCUS_CHANGE", "WINDOW_CHANGE", "ANNOUNCEMENT",
            "KEY_EVENT", "UNKNOWN",
        ]
        for nav in nav_types:
            resp = client.post(
                "/analyze",
                json={
                    "utterances": [{"text": "Test", "timestamp": 1, "navigation": nav}],
                    "context": {"trigger": "MANUAL"},
                },
            )
            assert resp.status_code == 200, f"Failed for navigation: {nav}"

    def test_analyze_rich_element_info(self, client):
        resp = client.post(
            "/analyze",
            json={
                "utterances": [
                    {
                        "text": "Username. Edit text. Double tap to edit",
                        "timestamp": 1,
                        "element": {
                            "class_name": "android.widget.EditText",
                            "text": "Username",
                            "view_id_resource_name": "com.example:id/username",
                            "is_clickable": True,
                            "is_focusable": True,
                            "is_editable": True,
                            "is_enabled": True,
                            "state_description": "empty",
                            "role_description": "edit text",
                            "child_count": 0,
                        },
                        "navigation": "SWIPE_RIGHT",
                        "screen": {
                            "package_name": "com.example",
                            "activity_name": "LoginActivity",
                            "window_title": "Sign In",
                            "window_id": 42,
                            "is_scrollable": False,
                        },
                    }
                ],
                "context": {"trigger": "MANUAL"},
            },
        )
        assert resp.status_code == 200


# ── Device Settings Tests ────────────────────────────────────────


class TestDeviceSettings:
    def test_default_settings(self):
        s = DeviceSettings()
        assert s.tts_suppressed is False
        assert s.gesture_injection_enabled is False
        assert s.trigger_mode == "SCREEN_CHANGE"
        assert s.buffer_size == 20
        assert s.revision == 0

    def test_settings_manager_update(self):
        mgr = DeviceSettingsManager()
        result = mgr.update(tts_suppressed=True)
        assert result.tts_suppressed is True
        assert result.revision == 1

    def test_settings_manager_no_change(self):
        mgr = DeviceSettingsManager()
        mgr.update(tts_suppressed=False)  # same as default
        assert mgr.current.revision == 0  # no change, no bump

    def test_settings_manager_multiple_updates(self):
        mgr = DeviceSettingsManager()
        mgr.update(tts_suppressed=True, buffer_size=10)
        assert mgr.current.revision == 1
        assert mgr.current.tts_suppressed is True
        assert mgr.current.buffer_size == 10

    def test_settings_manager_get_if_newer(self):
        mgr = DeviceSettingsManager()
        assert mgr.get_if_newer(0) is None  # no changes yet
        mgr.update(tts_suppressed=True)
        assert mgr.get_if_newer(0) is not None
        assert mgr.get_if_newer(1) is None  # already up to date

    def test_settings_ignores_unknown_keys(self):
        mgr = DeviceSettingsManager()
        mgr.update(nonexistent_key="value")
        assert mgr.current.revision == 0  # nothing changed

    def test_settings_ignores_revision_override(self):
        mgr = DeviceSettingsManager()
        mgr.update(revision=999, tts_suppressed=True)
        assert mgr.current.revision == 1  # not 999


class TestSettingsEndpoints:
    def test_get_settings(self, client):
        resp = client.get("/settings")
        assert resp.status_code == 200
        data = resp.json()
        assert "tts_suppressed" in data
        assert "revision" in data

    def test_patch_settings(self, client):
        resp = client.patch("/settings", json={"tts_suppressed": True})
        assert resp.status_code == 200
        data = resp.json()
        assert data["tts_suppressed"] is True
        assert data["revision"] >= 1

    def test_tts_toggle_suppress(self, client):
        resp = client.post("/settings/tts?suppress=true")
        assert resp.status_code == 200
        data = resp.json()
        assert data["tts_suppressed"] is True

    def test_tts_toggle_enable(self, client):
        client.post("/settings/tts?suppress=true")
        resp = client.post("/settings/tts?suppress=false")
        assert resp.status_code == 200
        data = resp.json()
        assert data["tts_suppressed"] is False

    def test_health_includes_tts_status(self, client):
        resp = client.get("/health")
        data = resp.json()
        assert "tts_suppressed" in data
        assert "settings_revision" in data


# ── Session Endpoint Tests ───────────────────────────────────────


class TestSessionEndpoints:
    def test_session_auto_created(self, client):
        # Start a fresh session so we don't pick up state from prior tests
        client.post("/session/start?session_id=auto_test")
        resp = client.get("/session")
        assert resp.status_code == 200
        data = resp.json()
        assert data["session_id"] == "auto_test"
        assert data["total_utterances"] == 0

    def test_session_start(self, client):
        resp = client.post("/session/start")
        assert resp.status_code == 200
        data = resp.json()
        assert "session_id" in data

    def test_session_start_with_id(self, client):
        resp = client.post("/session/start?session_id=test123")
        assert resp.status_code == 200
        assert resp.json()["session_id"] == "test123"

    def test_session_note(self, client):
        resp = client.post("/session/note?note=testing+note")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"

    def test_session_export_json(self, client):
        resp = client.get("/session/export?format=json")
        assert resp.status_code == 200
        data = resp.json()
        assert "session" in data
        assert "issues" in data
        assert "events" in data

    def test_session_export_markdown(self, client):
        resp = client.get("/session/export?format=markdown")
        assert resp.status_code == 200
        assert "Accessibility Audit" in resp.text

    def test_session_end(self, client):
        client.post("/session/start?session_id=end_test")
        resp = client.post("/session/end?save=false")
        assert resp.status_code == 200

    def test_session_history(self, client):
        resp = client.get("/session/history")
        assert resp.status_code == 200
        assert isinstance(resp.json(), list)


# ── Skill Endpoint Tests ────────────────────────────────────────


class TestSkillEndpoints:
    def test_skill_pending_empty(self, client):
        resp = client.get("/skill/pending")
        assert resp.status_code == 200
        assert resp.json() == []

    def test_skill_result_not_found(self, client):
        resp = client.post(
            "/skill/result",
            json={"request_id": "nonexistent", "success": True, "message": "done"},
        )
        assert resp.status_code == 404

    def test_skill_history_empty(self, client):
        resp = client.get("/skill/history")
        assert resp.status_code == 200
        assert resp.json() == []

    def test_skill_history_with_limit(self, client):
        resp = client.get("/skill/history?limit=10")
        assert resp.status_code == 200


# ── Session Model Tests ─────────────────────────────────────────


class TestSessionModel:
    def test_session_creation(self):
        session = Session("test_session")
        assert session.session_id == "test_session"
        assert session.events == []
        assert session.issues == []

    def test_session_add_note(self):
        session = Session("test")
        session.add_note("This is a test")
        assert len(session.events) == 1
        assert session.events[0].event_type == "note"
        assert session.events[0].data["text"] == "This is a test"

    def test_session_record_skill(self):
        session = Session("test")
        session.record_skill("NavigateTo", True, "Found element")
        assert len(session.events) == 1
        assert session.events[0].event_type == "skill"
        assert session.events[0].data["success"] is True

    def test_session_summary(self):
        session = Session("test")
        summary = session.get_summary()
        assert summary.session_id == "test"
        assert summary.total_utterances == 0
        assert summary.total_issues == 0

    def test_session_export_json(self):
        session = Session("test")
        session.add_note("note 1")
        result = json.loads(session.export_json())
        assert "session" in result
        assert "events" in result
        assert len(result["events"]) == 1

    def test_session_export_markdown(self):
        session = Session("test")
        md = session.export_markdown()
        assert "# Accessibility Audit: test" in md

    def test_session_manager_lifecycle(self):
        mgr = SessionManager()
        s1 = mgr.current
        assert s1 is not None

        s2 = mgr.start_new("session2")
        assert s2.session_id == "session2"
        assert mgr.current is s2

        summary = mgr.end_current()
        assert summary is not None
        assert summary.session_id == "session2"
        assert len(mgr.get_history()) >= 1


# ── Skill Queue Tests ───────────────────────────────────────────


class TestSkillQueue:
    def test_empty_pending(self):
        q = SkillQueue()
        assert q.get_pending() == []

    def test_empty_history(self):
        q = SkillQueue()
        assert q.get_history() == []

    def test_clear(self):
        q = SkillQueue()
        q.clear()  # should not raise
        assert q.get_pending() == []
