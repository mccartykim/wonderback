package com.google.android.accessibility.talkback.agent.connection

import com.google.android.accessibility.talkback.agent.model.AnalysisRequest
import com.google.android.accessibility.talkback.agent.model.AnalysisResponse

/**
 * Interface for connections to the analysis server.
 */
interface ServerConnection {
    /** Human-readable description of this connection (e.g., "ADB reverse (localhost:8080)"). */
    val description: String

    /** Check if the server is reachable. */
    suspend fun ping(): Boolean

    /** Send an analysis request and get the response. */
    suspend fun analyze(request: AnalysisRequest): AnalysisResponse

    /** Send a raw POST command to the server (for agent skill API). */
    suspend fun sendCommand(endpoint: String, payload: String): String

    /** Send a raw GET request to the server. Returns the response body. */
    suspend fun fetchCommand(endpoint: String): String

    /**
     * Fetch device settings from the server.
     * Returns the raw JSON response, or null if no update is available.
     * @param currentRevision Pass the last known revision to get changes only.
     * @param deviceId If provided, server includes auth token for approved devices.
     */
    suspend fun fetchSettings(currentRevision: Int = 0, deviceId: String = ""): String?

    /** Close the connection. */
    fun close()
}
