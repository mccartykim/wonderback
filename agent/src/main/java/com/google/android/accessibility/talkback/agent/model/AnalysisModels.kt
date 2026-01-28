package com.google.android.accessibility.talkback.agent.model

/**
 * Request sent to the macOS analysis server.
 */
data class AnalysisRequest(
    val utterances: List<UtteranceEvent>,
    val context: RequestContext,
    val previousIssues: List<Issue> = emptyList()
)

/**
 * Context about why analysis was triggered.
 */
data class RequestContext(
    val trigger: TriggerType,
    val previousScreen: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * What triggered the analysis request.
 */
enum class TriggerType {
    SCREEN_CHANGE,
    BUFFER_FULL,
    MANUAL,
    CONTINUOUS,
    SKILL_REQUEST
}

/**
 * Response from the analysis server.
 */
data class AnalysisResponse(
    val issues: List<Issue>,
    val metadata: ResponseMetadata? = null
)

/**
 * An accessibility issue identified by the LLM.
 */
data class Issue(
    val severity: IssueSeverity,
    val category: IssueCategory,
    val elementIndex: Int = -1,
    val utterance: String = "",
    val issue: String,
    val explanation: String = "",
    val suggestion: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class IssueSeverity {
    ERROR, WARNING, SUGGESTION
}

enum class IssueCategory {
    LABEL_QUALITY,
    STRUCTURE,
    CONTEXT,
    NAVIGATION
}

/**
 * Server response metadata.
 */
data class ResponseMetadata(
    val model: String = "",
    val inferenceTimeMs: Long = 0,
    val totalUtterances: Int = 0,
    val issuesFound: Int = 0
)
