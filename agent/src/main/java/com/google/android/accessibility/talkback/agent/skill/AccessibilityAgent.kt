package com.google.android.accessibility.talkback.agent.skill

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.google.android.accessibility.talkback.agent.gesture.GestureController
import com.google.android.accessibility.talkback.agent.model.*
import com.google.android.accessibility.talkback.agent.service.SpeechCapture
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * High-level agent API for building Claude-style skills around TalkBack's accessibility APIs.
 *
 * This class provides a clean, composable interface for:
 * - Reading screen state (accessibility tree, current focus, utterances)
 * - Performing actions (gestures, navigation, activation)
 * - Waiting for state changes (screen transitions, focus changes)
 * - Building higher-level "skills" (navigate to X, find element, etc.)
 *
 * Usage:
 * ```kotlin
 * val agent = AccessibilityAgent(talkBackService)
 *
 * // Read current screen
 * val screenState = agent.getScreenState()
 *
 * // Navigate to next element and wait for utterance
 * val utterance = agent.navigateNext()
 *
 * // Find and activate a button
 * agent.findAndActivate("Submit")
 *
 * // Build a skill
 * agent.executeSkill(NavigateToSettings())
 * ```
 */
class AccessibilityAgent(
    private val service: AccessibilityService
) : SpeechCapture.SpeechCaptureListener {

    val gesture = GestureController(service)
    private val speechCapture = SpeechCapture.getInstance()
    private val utteranceChannel = Channel<UtteranceEvent>(Channel.BUFFERED)
    private val utteranceHistory = ArrayDeque<UtteranceEvent>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        speechCapture.addListener(this)
    }

    override fun onUtteranceCaptured(event: UtteranceEvent) {
        utteranceHistory.addLast(event)
        while (utteranceHistory.size > MAX_HISTORY) {
            utteranceHistory.removeFirst()
        }
        utteranceChannel.trySend(event)
    }

    // ========== Screen State Reading ==========

    /**
     * Get the complete current screen state including accessibility tree,
     * focused element, and recent utterances.
     */
    fun getScreenState(): ScreenState {
        val rootNode = service.rootInActiveWindow
        val focusedNode = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        return ScreenState(
            root = rootNode,
            focusedNode = focusedNode,
            windowTitle = service.windows?.firstOrNull()?.title?.toString(),
            packageName = rootNode?.packageName?.toString() ?: "",
            recentUtterances = utteranceHistory.toList(),
            allNodes = if (rootNode != null) flattenTree(rootNode) else emptyList()
        )
    }

    /**
     * Get the currently focused accessibility node.
     */
    fun getFocusedNode(): AccessibilityNodeInfo? {
        return service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    /**
     * Get the root node of the active window.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return service.rootInActiveWindow
    }

    /**
     * Get all nodes in the accessibility tree as a flat list.
     */
    fun getAllNodes(): List<AccessibilityNodeInfo> {
        val root = service.rootInActiveWindow ?: return emptyList()
        return flattenTree(root)
    }

    /**
     * Find nodes matching a text predicate.
     */
    fun findNodes(predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        return getAllNodes().filter(predicate)
    }

    /**
     * Find nodes by text content (contentDescription or text).
     */
    fun findNodesByText(text: String, ignoreCase: Boolean = true): List<AccessibilityNodeInfo> {
        return findNodes { node ->
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            if (ignoreCase) {
                nodeText.contains(text, ignoreCase = true) ||
                nodeDesc.contains(text, ignoreCase = true)
            } else {
                nodeText.contains(text) || nodeDesc.contains(text)
            }
        }
    }

    /**
     * Find nodes by class name.
     */
    fun findNodesByClass(className: String): List<AccessibilityNodeInfo> {
        return findNodes { node ->
            node.className?.toString()?.contains(className) == true
        }
    }

    /**
     * Find nodes by view ID resource name.
     */
    fun findNodesById(viewId: String): List<AccessibilityNodeInfo> {
        val root = getRootNode() ?: return emptyList()
        return root.findAccessibilityNodeInfosByViewId(viewId)?.toList() ?: emptyList()
    }

    /**
     * Get recent utterances.
     */
    fun getRecentUtterances(count: Int = 10): List<UtteranceEvent> {
        return utteranceHistory.takeLast(count)
    }

    // ========== Navigation Actions ==========

    /**
     * Navigate to the next element and return the resulting utterance.
     */
    suspend fun navigateNext(): UtteranceEvent? {
        speechCapture.setNavigationType(NavigationType.SWIPE_RIGHT)
        gesture.swipeRight()
        return waitForUtterance()
    }

    /**
     * Navigate to the previous element and return the resulting utterance.
     */
    suspend fun navigatePrevious(): UtteranceEvent? {
        speechCapture.setNavigationType(NavigationType.SWIPE_LEFT)
        gesture.swipeLeft()
        return waitForUtterance()
    }

    /**
     * Activate the currently focused element (double-tap).
     */
    suspend fun activate(): Boolean {
        speechCapture.setNavigationType(NavigationType.DOUBLE_TAP)
        return gesture.doubleTapCenter()
    }

    /**
     * Long press the currently focused element.
     */
    suspend fun longPress(): Boolean {
        val node = getFocusedNode() ?: return false
        speechCapture.setNavigationType(NavigationType.LONG_PRESS)
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return gesture.longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /**
     * Scroll down in the current view.
     */
    suspend fun scrollDown(): UtteranceEvent? {
        speechCapture.setNavigationType(NavigationType.SCROLL)
        gesture.scrollDown()
        return waitForUtterance()
    }

    /**
     * Scroll up in the current view.
     */
    suspend fun scrollUp(): UtteranceEvent? {
        speechCapture.setNavigationType(NavigationType.SCROLL)
        gesture.scrollUp()
        return waitForUtterance()
    }

    /**
     * Perform a back gesture.
     */
    suspend fun goBack(): Boolean {
        return gesture.back()
    }

    /**
     * Navigate to home screen.
     */
    fun goHome(): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * Open recent apps.
     */
    fun openRecents(): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    /**
     * Open notifications shade.
     */
    fun openNotifications(): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * Open quick settings.
     */
    fun openQuickSettings(): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    // ========== High-Level Skills ==========

    /**
     * Find an element by text and navigate to it.
     * Returns true if found and focused.
     */
    suspend fun navigateTo(text: String, maxSteps: Int = 50): Boolean {
        for (i in 0 until maxSteps) {
            val utterance = navigateNext() ?: continue
            if (utterance.text.contains(text, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Find an element by text and activate it.
     * Returns true if found and activated.
     */
    suspend fun findAndActivate(text: String, maxSteps: Int = 50): Boolean {
        if (!navigateTo(text, maxSteps)) return false
        return activate()
    }

    /**
     * Collect all utterances on the current screen by navigating through all elements.
     * Stops when we loop back to the first element or reach maxSteps.
     */
    suspend fun collectScreenUtterances(maxSteps: Int = 100): List<UtteranceEvent> {
        val collected = mutableListOf<UtteranceEvent>()
        var firstText: String? = null

        for (i in 0 until maxSteps) {
            val utterance = navigateNext() ?: break

            if (firstText == null) {
                firstText = utterance.text
            } else if (utterance.text == firstText && i > 1) {
                // We've looped back to the start
                break
            }

            collected.add(utterance)
        }

        return collected
    }

    /**
     * Wait for a screen change (new activity/window).
     */
    suspend fun waitForScreenChange(timeoutMs: Long = 10000): UtteranceEvent? {
        return withTimeoutOrNull(timeoutMs) {
            for (event in utteranceChannel) {
                if (event.navigation == NavigationType.SCREEN_CHANGE ||
                    event.navigation == NavigationType.WINDOW_CHANGE) {
                    return@withTimeoutOrNull event
                }
            }
            null
        }
    }

    /**
     * Perform an action on a specific node by its AccessibilityNodeInfo action.
     */
    fun performNodeAction(
        node: AccessibilityNodeInfo,
        action: Int,
        arguments: android.os.Bundle? = null
    ): Boolean {
        return node.performAction(action, arguments)
    }

    /**
     * Click a node directly using the accessibility API (no gesture needed).
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Set text on an editable node.
     */
    fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Execute a custom skill.
     */
    suspend fun executeSkill(skill: AgentSkill): SkillResult {
        return skill.execute(this)
    }

    // ========== Utility ==========

    /**
     * Wait for the next utterance with a timeout.
     */
    suspend fun waitForUtterance(timeoutMs: Long = UTTERANCE_WAIT_TIMEOUT_MS): UtteranceEvent? {
        return withTimeoutOrNull(timeoutMs) {
            utteranceChannel.receive()
        }
    }

    /**
     * Flatten the accessibility tree into a list.
     */
    private fun flattenTree(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            nodes.add(node)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return nodes
    }

    fun destroy() {
        speechCapture.removeListener(this)
        utteranceChannel.close()
        scope.cancel()
    }

    companion object {
        private const val TAG = "AccessibilityAgent"
        private const val MAX_HISTORY = 200
        private const val UTTERANCE_WAIT_TIMEOUT_MS = 3000L
    }
}

/**
 * Current state of the screen as seen through accessibility APIs.
 */
data class ScreenState(
    val root: AccessibilityNodeInfo?,
    val focusedNode: AccessibilityNodeInfo?,
    val windowTitle: String?,
    val packageName: String,
    val recentUtterances: List<UtteranceEvent>,
    val allNodes: List<AccessibilityNodeInfo>
) {
    val focusedText: String?
        get() = focusedNode?.text?.toString() ?: focusedNode?.contentDescription?.toString()

    val nodeCount: Int
        get() = allNodes.size

    /**
     * Get a text summary of the current screen state, suitable for LLM context.
     */
    fun toTextSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Screen State ===")
        sb.appendLine("Package: $packageName")
        sb.appendLine("Window: ${windowTitle ?: "Unknown"}")
        sb.appendLine("Focused: ${focusedText ?: "None"}")
        sb.appendLine("Nodes: $nodeCount")
        sb.appendLine()
        sb.appendLine("Recent utterances:")
        recentUtterances.takeLast(10).forEachIndexed { i, u ->
            sb.appendLine("  $i. \"${u.text}\" [${u.element.className}]")
        }
        return sb.toString()
    }
}
