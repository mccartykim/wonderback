package com.google.android.accessibility.talkback.agent.skill

/**
 * Interface for composable agent skills.
 *
 * A skill is a reusable, named action sequence that an agent can perform.
 * Skills can compose other skills and use the AccessibilityAgent API
 * to interact with the screen.
 *
 * Example skill implementations:
 * - NavigateToScreen("Settings")
 * - FillForm(mapOf("username" to "test", "password" to "****"))
 * - AuditCurrentScreen()
 * - ScrollToBottom()
 */
interface AgentSkill {
    /** Human-readable name of this skill. */
    val name: String

    /** Description of what this skill does. */
    val description: String

    /**
     * Execute the skill using the agent's capabilities.
     * Returns a result indicating success/failure and any data.
     */
    suspend fun execute(agent: AccessibilityAgent): SkillResult
}

/**
 * Result of a skill execution.
 */
data class SkillResult(
    val success: Boolean,
    val message: String = "",
    val data: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(message: String = "OK", data: Map<String, Any?> = emptyMap()) =
            SkillResult(true, message, data)

        fun failure(message: String, data: Map<String, Any?> = emptyMap()) =
            SkillResult(false, message, data)
    }
}

// ========== Built-in Skills ==========

/**
 * Navigates forward through elements, collecting all utterances.
 * Useful for auditing an entire screen's accessibility.
 */
class CollectScreenUtterancesSkill(
    private val maxSteps: Int = 100
) : AgentSkill {
    override val name = "CollectScreenUtterances"
    override val description = "Navigate through all elements on screen and collect utterances"

    override suspend fun execute(agent: AccessibilityAgent): SkillResult {
        val utterances = agent.collectScreenUtterances(maxSteps)
        return SkillResult.success(
            "Collected ${utterances.size} utterances",
            mapOf("utterances" to utterances)
        )
    }
}

/**
 * Navigates to a specific element by text.
 */
class NavigateToElementSkill(
    private val text: String,
    private val maxSteps: Int = 50
) : AgentSkill {
    override val name = "NavigateToElement"
    override val description = "Navigate to element containing '$text'"

    override suspend fun execute(agent: AccessibilityAgent): SkillResult {
        val found = agent.navigateTo(text, maxSteps)
        return if (found) {
            SkillResult.success("Found and focused: $text")
        } else {
            SkillResult.failure("Element not found: $text")
        }
    }
}

/**
 * Finds and activates (taps) an element by text.
 */
class ActivateElementSkill(
    private val text: String,
    private val maxSteps: Int = 50
) : AgentSkill {
    override val name = "ActivateElement"
    override val description = "Find and activate element containing '$text'"

    override suspend fun execute(agent: AccessibilityAgent): SkillResult {
        val activated = agent.findAndActivate(text, maxSteps)
        return if (activated) {
            SkillResult.success("Activated: $text")
        } else {
            SkillResult.failure("Could not activate: $text")
        }
    }
}

/**
 * Fills a text field by navigating to it and setting text.
 */
class FillTextFieldSkill(
    private val fieldIdentifier: String,
    private val text: String
) : AgentSkill {
    override val name = "FillTextField"
    override val description = "Fill text field '$fieldIdentifier' with text"

    override suspend fun execute(agent: AccessibilityAgent): SkillResult {
        // Try to find the field by text/description first
        val nodes = agent.findNodesByText(fieldIdentifier)
        val editableNode = nodes.firstOrNull { it.isEditable }
            ?: return SkillResult.failure("No editable field found matching: $fieldIdentifier")

        val success = agent.setNodeText(editableNode, text)
        return if (success) {
            SkillResult.success("Filled '$fieldIdentifier' with text")
        } else {
            SkillResult.failure("Failed to set text on: $fieldIdentifier")
        }
    }
}

/**
 * Scrolls through a scrollable view collecting all visible elements.
 */
class ScrollAndCollectSkill(
    private val maxScrolls: Int = 10
) : AgentSkill {
    override val name = "ScrollAndCollect"
    override val description = "Scroll through view collecting all elements"

    override suspend fun execute(agent: AccessibilityAgent): SkillResult {
        val allUtterances = mutableListOf<com.google.android.accessibility.talkback.agent.model.UtteranceEvent>()
        var lastScreenHash = ""

        for (i in 0 until maxScrolls) {
            // Collect current screen
            val screenUtterances = agent.collectScreenUtterances()
            val screenHash = screenUtterances.joinToString { it.text }

            if (screenHash == lastScreenHash) {
                // No new content, we've reached the end
                break
            }

            allUtterances.addAll(screenUtterances)
            lastScreenHash = screenHash

            // Scroll down
            agent.scrollDown()
            kotlinx.coroutines.delay(500)
        }

        return SkillResult.success(
            "Collected ${allUtterances.size} utterances over scrolling",
            mapOf("utterances" to allUtterances)
        )
    }
}

/**
 * Waits for a specific utterance text to appear.
 * Useful for waiting for loading screens, transitions, etc.
 */
class WaitForTextSkill(
    private val text: String,
    private val timeoutMs: Long = 10000
) : AgentSkill {
    override val name = "WaitForText"
    override val description = "Wait for utterance containing '$text'"

    override suspend fun execute(agent: AccessibilityAgent): SkillResult {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val utterance = agent.waitForUtterance(1000)
            if (utterance != null && utterance.text.contains(text, ignoreCase = true)) {
                return SkillResult.success("Found: ${utterance.text}")
            }
        }
        return SkillResult.failure("Timeout waiting for text: $text")
    }
}

/**
 * Takes a snapshot of the accessibility tree and formats it as text.
 * Useful for sending to an LLM for analysis.
 */
class ScreenSnapshotSkill : AgentSkill {
    override val name = "ScreenSnapshot"
    override val description = "Capture accessibility tree snapshot"

    override suspend fun execute(agent: AccessibilityAgent): SkillResult {
        val state = agent.getScreenState()
        val summary = state.toTextSummary()

        // Also build a tree representation
        val treeText = buildTreeText(state.root, 0)

        return SkillResult.success(
            "Captured screen with ${state.nodeCount} nodes",
            mapOf(
                "summary" to summary,
                "tree" to treeText,
                "packageName" to state.packageName,
                "windowTitle" to state.windowTitle,
                "nodeCount" to state.nodeCount
            )
        )
    }

    private fun buildTreeText(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int): String {
        if (node == null) return ""
        val sb = StringBuilder()
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val cls = node.className?.toString()?.substringAfterLast(".") ?: ""
        val label = when {
            text.isNotEmpty() -> "\"$text\""
            desc.isNotEmpty() -> "[$desc]"
            else -> ""
        }
        val props = mutableListOf<String>()
        if (node.isClickable) props.add("clickable")
        if (node.isCheckable) props.add("checkable")
        if (node.isEditable) props.add("editable")
        if (node.isScrollable) props.add("scrollable")
        if (node.isFocusable) props.add("focusable")

        val propsStr = if (props.isNotEmpty()) " (${props.joinToString(", ")})" else ""
        sb.appendLine("$indent$cls $label$propsStr")

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                sb.append(buildTreeText(it, depth + 1))
            }
        }
        return sb.toString()
    }
}
