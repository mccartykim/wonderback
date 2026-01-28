package com.google.android.accessibility.talkback.agent.connection

import android.util.Log
import com.google.android.accessibility.talkback.agent.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP-based connection to the analysis server.
 * Used for all connection methods (ADB reverse, mDNS, USB tethering, manual).
 */
class HttpServerConnection(
    private val baseUrl: String,
    override val description: String
) : ServerConnection {

    private val gson: Gson = GsonBuilder()
        .setFieldNamingStrategy { field ->
            // Convert camelCase to snake_case for JSON
            field.name.replace(Regex("([A-Z])")) { "_${it.value.lowercase()}" }
        }
        .create()

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.d(TAG, "Ping failed for $baseUrl: ${e.message}")
            false
        }
    }

    override suspend fun analyze(request: AnalysisRequest): AnalysisResponse =
        withContext(Dispatchers.IO) {
            val json = gson.toJson(request)
            val body = json.toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url("$baseUrl/analyze")
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Analysis request failed: ${response.code} ${response.message}")
                }

                val responseBody = response.body?.string()
                    ?: throw IOException("Empty response from server")

                gson.fromJson(responseBody, AnalysisResponse::class.java)
            }
        }

    override suspend fun sendCommand(endpoint: String, payload: String): String =
        withContext(Dispatchers.IO) {
            val body = payload.toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url("$baseUrl$endpoint")
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Command failed: ${response.code} ${response.message}")
                }

                response.body?.string() ?: ""
            }
        }

    override suspend fun fetchCommand(endpoint: String): String =
        withContext(Dispatchers.IO) {
            val httpRequest = Request.Builder()
                .url("$baseUrl$endpoint")
                .get()
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GET $endpoint failed: ${response.code} ${response.message}")
                }

                response.body?.string() ?: ""
            }
        }

    override suspend fun fetchSettings(currentRevision: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/settings?revision=$currentRevision")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> response.body?.string()
                        304 -> null  // No changes since last revision
                        else -> {
                            Log.w(TAG, "Settings fetch failed: ${response.code}")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Settings fetch failed: ${e.message}")
                null
            }
        }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        private const val TAG = "HttpServerConnection"
        private const val CONNECT_TIMEOUT_S = 5L
        private const val READ_TIMEOUT_S = 30L   // LLM inference can be slow
        private const val WRITE_TIMEOUT_S = 10L
    }
}
