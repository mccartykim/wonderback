package com.google.android.accessibility.talkback.agent.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.talkback.agent.model.AgentConfig
import com.google.android.accessibility.utils.output.SpeechControllerImpl

/**
 * Bridge between the SpeechControllerImpl capture hook and the agent's SpeechCapture.
 *
 * This class registers as a SpeechInterceptListener on SpeechControllerImpl
 * and forwards captured utterances to SpeechCapture with the current
 * accessibility focus node's metadata.
 *
 * In TTS-suppressed mode, onSpeechIntercepted() returns true to prevent
 * actual TTS output while still capturing the text — useful for automated testing.
 */
class AgentSpeechBridge(
    private val service: AccessibilityService,
    private val config: AgentConfig
) : SpeechControllerImpl.SpeechInterceptListener {

    private val speechCapture = SpeechCapture.getInstance()

    /**
     * Install this bridge as the speech intercept listener.
     * Call during agent initialization.
     */
    fun install() {
        SpeechControllerImpl.setSpeechInterceptListener(this)
        Log.i(TAG, "Agent speech bridge installed (TTS suppressed: ${config.isTtsSuppressed})")
    }

    /**
     * Uninstall this bridge.
     * Call during agent shutdown.
     */
    fun uninstall() {
        SpeechControllerImpl.setSpeechInterceptListener(null)
        Log.i(TAG, "Agent speech bridge uninstalled")
    }

    override fun onSpeechIntercepted(text: CharSequence, queueMode: Int, flags: Int): Boolean {
        if (!config.isEnabled) return false

        // Get the currently focused accessibility node for metadata extraction
        val focusedNode = try {
            service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get focused node: ${e.message}")
            null
        }

        // Forward to SpeechCapture for buffering and event dispatch
        speechCapture.captureUtterance(text, focusedNode, queueMode, flags)

        // Return true to suppress TTS if configured for silent capture
        return config.isTtsSuppressed
    }

    companion object {
        private const val TAG = "AgentSpeechBridge"
    }
}
