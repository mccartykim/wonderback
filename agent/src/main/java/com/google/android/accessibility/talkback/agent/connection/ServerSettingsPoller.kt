package com.google.android.accessibility.talkback.agent.connection

import android.util.Log
import com.google.android.accessibility.talkback.agent.model.AgentConfig
import com.google.android.accessibility.talkback.agent.model.IssueSeverity
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*

/**
 * Periodically polls the server for settings changes and applies them to AgentConfig.
 *
 * Uses revision-based change detection so the server only sends data when
 * settings have actually changed. This is lightweight enough to poll every few seconds.
 */
class ServerSettingsPoller(
    private val config: AgentConfig,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    private val gson = Gson()
    private var connection: ServerConnection? = null
    private var pollingJob: Job? = null
    private var lastRevision: Int = 0

    /**
     * Start polling with the given connection.
     * Call this after the connection is established.
     */
    fun start(conn: ServerConnection, scope: CoroutineScope) {
        stop()
        connection = conn
        pollingJob = scope.launch {
            Log.i(TAG, "Settings poller started (interval: ${pollIntervalMs}ms)")
            while (isActive) {
                try {
                    pollOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Settings poll error: ${e.message}")
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        connection = null
        Log.i(TAG, "Settings poller stopped")
    }

    private suspend fun pollOnce() {
        val conn = connection ?: return
        val json = conn.fetchSettings(lastRevision) ?: return

        try {
            val settings = gson.fromJson(json, JsonObject::class.java)
            val revision = settings.get("revision")?.asInt ?: return

            if (revision <= lastRevision) return
            lastRevision = revision

            Log.i(TAG, "Applying server settings (rev $revision)")
            applySettings(settings)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse settings: ${e.message}")
        }
    }

    private fun applySettings(settings: JsonObject) {
        settings.get("tts_suppressed")?.asBoolean?.let { value ->
            if (config.isTtsSuppressed != value) {
                config.isTtsSuppressed = value
                Log.i(TAG, "TTS suppressed -> $value")
            }
        }

        settings.get("gesture_injection_enabled")?.asBoolean?.let { value ->
            if (config.gestureInjectionEnabled != value) {
                config.gestureInjectionEnabled = value
                Log.i(TAG, "Gesture injection -> $value")
            }
        }

        settings.get("trigger_mode")?.asString?.let { value ->
            try {
                val mode = AgentConfig.TriggerMode.valueOf(value)
                if (config.triggerMode != mode) {
                    config.triggerMode = mode
                    Log.i(TAG, "Trigger mode -> $value")
                }
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Unknown trigger mode: $value")
            }
        }

        settings.get("buffer_size")?.asInt?.let { value ->
            if (config.bufferSize != value && value in 1..100) {
                config.bufferSize = value
                Log.i(TAG, "Buffer size -> $value")
            }
        }

        settings.get("severity_filter")?.asString?.let { value ->
            try {
                val severity = IssueSeverity.valueOf(value)
                if (config.severityFilter != severity) {
                    config.severityFilter = severity
                    Log.i(TAG, "Severity filter -> $value")
                }
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Unknown severity: $value")
            }
        }

        settings.get("show_notifications")?.asBoolean?.let { value ->
            if (config.showNotifications != value) {
                config.showNotifications = value
                Log.i(TAG, "Show notifications -> $value")
            }
        }

        settings.get("capture_full_metadata")?.asBoolean?.let { value ->
            if (config.captureFullMetadata != value) {
                config.captureFullMetadata = value
                Log.i(TAG, "Full metadata capture -> $value")
            }
        }

        settings.get("debug_logging")?.asBoolean?.let { value ->
            if (config.debugLogging != value) {
                config.debugLogging = value
                Log.i(TAG, "Debug logging -> $value")
            }
        }
    }

    companion object {
        private const val TAG = "ServerSettingsPoller"
        private const val DEFAULT_POLL_INTERVAL_MS = 5000L
    }
}
