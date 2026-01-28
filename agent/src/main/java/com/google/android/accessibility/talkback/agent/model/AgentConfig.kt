package com.google.android.accessibility.talkback.agent.model

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration for the TalkBack Agent.
 * Stored in SharedPreferences, accessible from settings UI.
 */
class AgentConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the agent is enabled at all. */
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Whether to suppress TTS output (silent capture mode for testing). */
    var isTtsSuppressed: Boolean
        get() = prefs.getBoolean(KEY_TTS_SUPPRESSED, false)
        set(value) = prefs.edit().putBoolean(KEY_TTS_SUPPRESSED, value).apply()

    /** Connection method preference. */
    var connectionMethod: ConnectionMethod
        get() = ConnectionMethod.valueOf(
            prefs.getString(KEY_CONNECTION_METHOD, ConnectionMethod.AUTO.name)!!
        )
        set(value) = prefs.edit().putString(KEY_CONNECTION_METHOD, value.name).apply()

    /** Manual server address (IP:port). */
    var manualServerAddress: String
        get() = prefs.getString(KEY_MANUAL_ADDRESS, "localhost:8080") ?: "localhost:8080"
        set(value) = prefs.edit().putString(KEY_MANUAL_ADDRESS, value).apply()

    /** Analysis trigger mode. */
    var triggerMode: TriggerMode
        get() = TriggerMode.valueOf(
            prefs.getString(KEY_TRIGGER_MODE, TriggerMode.SCREEN_CHANGE.name)!!
        )
        set(value) = prefs.edit().putString(KEY_TRIGGER_MODE, value.name).apply()

    /** Number of utterances to buffer before analysis. */
    var bufferSize: Int
        get() = prefs.getInt(KEY_BUFFER_SIZE, 20)
        set(value) = prefs.edit().putInt(KEY_BUFFER_SIZE, value).apply()

    /** Minimum severity level to display. */
    var severityFilter: IssueSeverity
        get() = IssueSeverity.valueOf(
            prefs.getString(KEY_SEVERITY_FILTER, IssueSeverity.SUGGESTION.name)!!
        )
        set(value) = prefs.edit().putString(KEY_SEVERITY_FILTER, value.name).apply()

    /** Whether to show notifications for issues. */
    var showNotifications: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NOTIFICATIONS, value).apply()

    /** Whether to capture full element metadata (vs minimal). */
    var captureFullMetadata: Boolean
        get() = prefs.getBoolean(KEY_FULL_METADATA, true)
        set(value) = prefs.edit().putBoolean(KEY_FULL_METADATA, value).apply()

    /** Whether debug logging is enabled. */
    var debugLogging: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOGGING, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_LOGGING, value).apply()

    /** Whether gesture injection is enabled. */
    var gestureInjectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_INJECTION, false)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_INJECTION, value).apply()

    /** Server port for ADB reverse / direct connections. */
    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    enum class ConnectionMethod {
        AUTO, ADB_REVERSE, NETWORK, USB_TETHERING, MANUAL
    }

    enum class TriggerMode {
        SCREEN_CHANGE, BUFFER_FULL, MANUAL, CONTINUOUS
    }

    companion object {
        const val PREFS_NAME = "talkback_agent_config"
        const val DEFAULT_PORT = 8080

        private const val KEY_ENABLED = "agent_enabled"
        private const val KEY_TTS_SUPPRESSED = "tts_suppressed"
        private const val KEY_CONNECTION_METHOD = "connection_method"
        private const val KEY_MANUAL_ADDRESS = "manual_address"
        private const val KEY_TRIGGER_MODE = "trigger_mode"
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_SEVERITY_FILTER = "severity_filter"
        private const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
        private const val KEY_FULL_METADATA = "full_metadata"
        private const val KEY_DEBUG_LOGGING = "debug_logging"
        private const val KEY_GESTURE_INJECTION = "gesture_injection"
        private const val KEY_SERVER_PORT = "server_port"

        @Volatile
        private var instance: AgentConfig? = null

        fun getInstance(context: Context): AgentConfig {
            return instance ?: synchronized(this) {
                instance ?: AgentConfig(context.applicationContext).also { instance = it }
            }
        }
    }
}
