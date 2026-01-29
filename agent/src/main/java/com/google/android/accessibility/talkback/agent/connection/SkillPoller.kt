package com.google.android.accessibility.talkback.agent.connection

import android.util.Log
import com.google.android.accessibility.talkback.agent.skill.AccessibilityAgent
import com.google.android.accessibility.talkback.agent.skill.ActivateElementSkill
import com.google.android.accessibility.talkback.agent.skill.CollectScreenUtterancesSkill
import com.google.android.accessibility.talkback.agent.skill.FillTextFieldSkill
import com.google.android.accessibility.talkback.agent.skill.NavigateToElementSkill
import com.google.android.accessibility.talkback.agent.skill.ScreenSnapshotSkill
import com.google.android.accessibility.talkback.agent.skill.ScrollAndCollectSkill
import com.google.android.accessibility.talkback.agent.skill.SkillResult
import com.google.android.accessibility.talkback.agent.skill.WaitForTextSkill
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.*

/**
 * Polls the server for pending skill commands and executes them on-device.
 *
 * Flow:
 * 1. Poll GET /skill/pending every [pollIntervalMs]
 * 2. For each pending skill, dispatch to the appropriate built-in skill
 * 3. Execute via AccessibilityAgent
 * 4. Report result back via POST /skill/result
 *
 * This completes the server-driven automation loop: the server (or LLM)
 * can queue commands, and the device executes them and reports back.
 */
class SkillPoller(
    private val agent: AccessibilityAgent,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    private val gson = Gson()
    private var connection: ServerConnection? = null
    private var pollingJob: Job? = null

    fun start(conn: ServerConnection, scope: CoroutineScope) {
        stop()
        connection = conn
        pollingJob = scope.launch {
            Log.i(TAG, "Skill poller started (interval: ${pollIntervalMs}ms)")
            while (isActive) {
                try {
                    pollAndExecute()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Skill poll error: ${e.message}")
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        connection = null
        Log.i(TAG, "Skill poller stopped")
    }

    private suspend fun pollAndExecute() {
        val conn = connection ?: return
        val json = try {
            conn.fetchCommand("/skill/pending")
        } catch (e: Exception) {
            Log.d(TAG, "Skill pending fetch failed: ${e.message}")
            return
        }

        val pending: JsonArray
        try {
            pending = gson.fromJson(json, JsonArray::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse pending skills: ${e.message}")
            return
        }

        if (pending.size() == 0) return

        Log.i(TAG, "Got ${pending.size()} pending skill(s)")

        for (element in pending) {
            val cmd = element.asJsonObject
            val requestId = cmd.get("request_id")?.asString ?: continue
            val skillName = cmd.get("skill_name")?.asString ?: continue
            val parameters = cmd.get("parameters")?.asJsonObject ?: JsonObject()

            Log.i(TAG, "Executing skill: $skillName ($requestId)")

            val result = try {
                executeSkill(skillName, parameters)
            } catch (e: Exception) {
                Log.e(TAG, "Skill $skillName failed with exception", e)
                SkillResult.failure("Exception: ${e.message}")
            }

            reportResult(conn, requestId, result)
        }
    }

    private suspend fun executeSkill(skillName: String, params: JsonObject): SkillResult {
        // Direct agent actions — execute immediately and return result
        when (skillName.lowercase()) {
            "navigate_next" -> {
                agent.navigateNext()
                return SkillResult.success("Navigated to next element")
            }
            "navigate_previous" -> {
                agent.navigatePrevious()
                return SkillResult.success("Navigated to previous element")
            }
            "go_back" -> {
                agent.goBack()
                return SkillResult.success("Pressed back")
            }
            "go_home" -> {
                agent.goHome()
                return SkillResult.success("Pressed home")
            }
            "scroll_down" -> {
                agent.scrollDown()
                return SkillResult.success("Scrolled down")
            }
            "scroll_up" -> {
                agent.scrollUp()
                return SkillResult.success("Scrolled up")
            }
            "get_screen_state" -> {
                val state = agent.getScreenState()
                return SkillResult.success(
                    "Screen: ${state.packageName} - ${state.windowTitle} (${state.nodeCount} nodes)",
                    mapOf(
                        "summary" to state.toTextSummary(),
                        "package_name" to state.packageName,
                        "window_title" to state.windowTitle,
                        "node_count" to state.nodeCount
                    )
                )
            }
            // Direct gesture injection commands
            "swipe_right" -> {
                val success = agent.gesture.swipeRight()
                return if (success) {
                    SkillResult.success("Swiped right (TalkBack next)")
                } else {
                    SkillResult.failure("Failed to dispatch swipe_right gesture")
                }
            }
            "swipe_left" -> {
                val success = agent.gesture.swipeLeft()
                return if (success) {
                    SkillResult.success("Swiped left (TalkBack previous)")
                } else {
                    SkillResult.failure("Failed to dispatch swipe_left gesture")
                }
            }
            "swipe_up" -> {
                val success = agent.gesture.swipeUp()
                return if (success) {
                    SkillResult.success("Swiped up")
                } else {
                    SkillResult.failure("Failed to dispatch swipe_up gesture")
                }
            }
            "swipe_down" -> {
                val success = agent.gesture.swipeDown()
                return if (success) {
                    SkillResult.success("Swiped down")
                } else {
                    SkillResult.failure("Failed to dispatch swipe_down gesture")
                }
            }
            "double_tap" -> {
                val success = agent.gesture.doubleTapCenter()
                return if (success) {
                    SkillResult.success("Double tapped (TalkBack activate)")
                } else {
                    SkillResult.failure("Failed to dispatch double_tap gesture")
                }
            }
            "tap" -> {
                val x = params.get("x")?.asFloat ?: return SkillResult.failure("Missing x coordinate")
                val y = params.get("y")?.asFloat ?: return SkillResult.failure("Missing y coordinate")
                agent.gesture.tap(x, y)
                return SkillResult.success("Tapped at ($x, $y)")
            }
            "long_press" -> {
                val x = params.get("x")?.asFloat
                val y = params.get("y")?.asFloat
                if (x != null && y != null) {
                    agent.gesture.longPress(x, y)
                    return SkillResult.success("Long pressed at ($x, $y)")
                } else {
                    agent.longPress()
                    return SkillResult.success("Long pressed current focus")
                }
            }
            "activate" -> {
                agent.activate()
                return SkillResult.success("Activated current focus")
            }
        }

        // Compound skills — resolve to AgentSkill instance, then execute
        val skill = resolveSkill(skillName, params)
            ?: return SkillResult.failure("Unknown skill: $skillName")

        return skill.execute(agent)
    }

    private fun resolveSkill(
        skillName: String,
        params: JsonObject
    ): com.google.android.accessibility.talkback.agent.skill.AgentSkill? {
        return when (skillName.lowercase()) {
            "collectscreenutterances", "collect_screen_utterances" -> {
                val maxSteps = params.get("max_steps")?.asInt ?: 100
                CollectScreenUtterancesSkill(maxSteps)
            }
            "navigatetoelement", "navigate_to_element", "navigate_to" -> {
                val text = params.get("text")?.asString ?: return null
                val maxSteps = params.get("max_steps")?.asInt ?: 50
                NavigateToElementSkill(text, maxSteps)
            }
            "activateelement", "activate_element", "activate" -> {
                val text = params.get("text")?.asString ?: return null
                val maxSteps = params.get("max_steps")?.asInt ?: 50
                ActivateElementSkill(text, maxSteps)
            }
            "filltextfield", "fill_text_field", "fill" -> {
                val field = params.get("field")?.asString ?: return null
                val text = params.get("text")?.asString ?: return null
                FillTextFieldSkill(field, text)
            }
            "scrollandcollect", "scroll_and_collect" -> {
                val maxScrolls = params.get("max_scrolls")?.asInt ?: 10
                ScrollAndCollectSkill(maxScrolls)
            }
            "waitfortext", "wait_for_text" -> {
                val text = params.get("text")?.asString ?: return null
                val timeoutMs = params.get("timeout_ms")?.asLong ?: 10000L
                WaitForTextSkill(text, timeoutMs)
            }
            "screensnapshot", "screen_snapshot", "snapshot" -> {
                ScreenSnapshotSkill()
            }
            else -> null
        }
    }

    private suspend fun reportResult(conn: ServerConnection, requestId: String, result: SkillResult) {
        try {
            val payload = JsonObject().apply {
                addProperty("request_id", requestId)
                addProperty("success", result.success)
                addProperty("message", result.message)
                // Serialize data map
                add("data", gson.toJsonTree(result.data))
            }
            conn.sendCommand("/skill/result", gson.toJson(payload))
            Log.i(TAG, "Reported result for $requestId: success=${result.success}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report result for $requestId: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SkillPoller"
        private const val DEFAULT_POLL_INTERVAL_MS = 2000L
    }
}
