package com.google.android.accessibility.talkback.agent

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.google.android.accessibility.talkback.agent.gesture.GestureController
import com.google.android.accessibility.talkback.agent.model.AgentConfig
import com.google.android.accessibility.talkback.agent.model.NavigationType
import com.google.android.accessibility.talkback.agent.service.AgentClientService
import com.google.android.accessibility.talkback.agent.service.AgentSpeechBridge
import com.google.android.accessibility.talkback.agent.service.SpeechCapture
import com.google.android.accessibility.talkback.agent.skill.AccessibilityAgent

/**
 * Top-level manager for the TalkBack Agent subsystem.
 *
 * Initialize from TalkBackService.onServiceConnected() and shut down
 * from TalkBackService.onUnbind(). This class owns:
 * - AgentSpeechBridge (intercepts TTS output)
 * - AgentClientService (manages server connection)
 * - AccessibilityAgent (high-level skill API)
 * - GestureController (gesture injection)
 */
class AgentManager(private val service: AccessibilityService) {

    private lateinit var config: AgentConfig
    private lateinit var speechBridge: AgentSpeechBridge
    private var agent: AccessibilityAgent? = null
    private var gestureController: GestureController? = null
    private var isInitialized = false

    /**
     * Initialize the agent subsystem.
     * Call from TalkBackService.onServiceConnected().
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "AgentManager already initialized")
            return
        }

        config = AgentConfig.getInstance(service)

        // Install speech capture bridge
        speechBridge = AgentSpeechBridge(service, config)
        speechBridge.install()

        // Create gesture controller
        gestureController = GestureController(service)

        // Create high-level agent API
        agent = AccessibilityAgent(service)

        // Start the client service if agent is enabled
        if (config.isEnabled) {
            AgentClientService.start(service)
        }

        isInitialized = true
        Log.i(TAG, "AgentManager initialized (enabled: ${config.isEnabled})")
    }

    /**
     * Shut down the agent subsystem.
     * Call from TalkBackService.onUnbind() or onDestroy().
     */
    fun shutdown() {
        if (!isInitialized) return

        speechBridge.uninstall()
        agent?.destroy()
        agent = null
        gestureController = null
        AgentClientService.stop(service)

        isInitialized = false
        Log.i(TAG, "AgentManager shut down")
    }

    /**
     * Notify the agent of a navigation type change.
     * Call from gesture/event processors.
     */
    fun onNavigationEvent(type: NavigationType) {
        SpeechCapture.getInstance().setNavigationType(type)
    }

    /**
     * Notify the agent of a screen/window change.
     */
    fun onScreenChanged(
        packageName: String? = null,
        activityName: String? = null,
        windowTitle: String? = null
    ) {
        SpeechCapture.getInstance().updateScreenContext(
            packageName = packageName,
            activityName = activityName,
            windowTitle = windowTitle
        )
        SpeechCapture.getInstance().setNavigationType(NavigationType.SCREEN_CHANGE)
    }

    /**
     * Get the high-level agent API for building skills.
     */
    fun getAgent(): AccessibilityAgent? = agent

    /**
     * Get the gesture controller for injection.
     */
    fun getGestureController(): GestureController? = gestureController

    /**
     * Get the current configuration.
     */
    fun getConfig(): AgentConfig = config

    /**
     * Check if the agent is enabled and initialized.
     */
    fun isReady(): Boolean = isInitialized && config.isEnabled

    companion object {
        private const val TAG = "AgentManager"

        @Volatile
        private var instance: AgentManager? = null

        /**
         * Get the singleton AgentManager instance.
         * Must call initialize() with the AccessibilityService before use.
         */
        @JvmStatic
        fun getInstance(service: AccessibilityService): AgentManager {
            return instance ?: synchronized(this) {
                instance ?: AgentManager(service).also { instance = it }
            }
        }

        /**
         * Get the existing instance (may be null if not initialized).
         */
        fun getInstanceOrNull(): AgentManager? = instance
    }
}
