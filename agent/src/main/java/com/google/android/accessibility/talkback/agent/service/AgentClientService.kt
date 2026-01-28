package com.google.android.accessibility.talkback.agent.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.accessibility.talkback.agent.connection.ConnectionManager
import com.google.android.accessibility.talkback.agent.connection.ServerSettingsPoller
import com.google.android.accessibility.talkback.agent.model.*
import com.google.android.accessibility.talkback.agent.ui.IssueListActivity
import kotlinx.coroutines.*

/**
 * Foreground service that manages the agent lifecycle:
 * - Connects to the macOS analysis server
 * - Buffers utterances from SpeechCapture
 * - Triggers analysis based on configured mode
 * - Displays results via notifications
 */
class AgentClientService : Service(), SpeechCapture.SpeechCaptureListener,
    ConnectionManager.ConnectionListener {

    private lateinit var config: AgentConfig
    private lateinit var connectionManager: ConnectionManager
    private lateinit var settingsPoller: ServerSettingsPoller
    private val utteranceBuffer = ArrayDeque<UtteranceEvent>()
    private val recentIssues = mutableListOf<Issue>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var analysisJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        config = AgentConfig.getInstance(this)
        connectionManager = ConnectionManager(this, config)
        connectionManager.addListener(this)
        settingsPoller = ServerSettingsPoller(config)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildStatusNotification("Initializing..."))

        // Register for speech capture events
        SpeechCapture.getInstance().addListener(this)

        // Auto-connect
        scope.launch {
            connectionManager.connect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANALYZE_NOW -> {
                scope.launch { requestAnalysis() }
            }
            ACTION_CLEAR_ISSUES -> {
                recentIssues.clear()
                updateNotification()
            }
            ACTION_RECONNECT -> {
                scope.launch { connectionManager.connect() }
            }
            ACTION_DISCONNECT -> {
                connectionManager.disconnect()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        settingsPoller.stop()
        SpeechCapture.getInstance().removeListener(this)
        connectionManager.removeListener(this)
        connectionManager.destroy()
        scope.cancel()
        super.onDestroy()
    }

    // -- SpeechCaptureListener --

    override fun onUtteranceCaptured(event: UtteranceEvent) {
        synchronized(utteranceBuffer) {
            utteranceBuffer.addLast(event)
            // Keep buffer at configured size
            while (utteranceBuffer.size > config.bufferSize) {
                utteranceBuffer.removeFirst()
            }
        }

        if (shouldAnalyze(event)) {
            scope.launch { requestAnalysis() }
        }
    }

    // -- ConnectionListener --

    override fun onConnectionStateChanged(
        state: ConnectionManager.ConnectionState,
        info: String
    ) {
        val statusText = when (state) {
            ConnectionManager.ConnectionState.CONNECTED -> {
                // Start polling for server-controlled settings
                connectionManager.currentConnection?.let { conn ->
                    settingsPoller.start(conn, scope)
                }
                "Connected: $info"
            }
            ConnectionManager.ConnectionState.CONNECTING -> "Connecting..."
            ConnectionManager.ConnectionState.RECONNECTING -> {
                settingsPoller.stop()
                "Reconnecting..."
            }
            ConnectionManager.ConnectionState.DISCONNECTED -> {
                settingsPoller.stop()
                "Disconnected"
            }
        }
        updateNotification(statusText)
    }

    // -- Analysis logic --

    private fun shouldAnalyze(event: UtteranceEvent): Boolean {
        if (!connectionManager.isConnected) return false

        return when (config.triggerMode) {
            AgentConfig.TriggerMode.BUFFER_FULL ->
                utteranceBuffer.size >= config.bufferSize
            AgentConfig.TriggerMode.SCREEN_CHANGE ->
                event.navigation == NavigationType.SCREEN_CHANGE ||
                event.navigation == NavigationType.WINDOW_CHANGE
            AgentConfig.TriggerMode.MANUAL -> false
            AgentConfig.TriggerMode.CONTINUOUS -> true
        }
    }

    private suspend fun requestAnalysis() {
        // Avoid concurrent analysis requests
        if (analysisJob?.isActive == true) return

        analysisJob = scope.launch {
            val utterances: List<UtteranceEvent>
            synchronized(utteranceBuffer) {
                utterances = utteranceBuffer.toList()
            }

            if (utterances.isEmpty()) return@launch

            val request = AnalysisRequest(
                utterances = utterances,
                context = RequestContext(
                    trigger = when (config.triggerMode) {
                        AgentConfig.TriggerMode.SCREEN_CHANGE -> TriggerType.SCREEN_CHANGE
                        AgentConfig.TriggerMode.BUFFER_FULL -> TriggerType.BUFFER_FULL
                        AgentConfig.TriggerMode.MANUAL -> TriggerType.MANUAL
                        AgentConfig.TriggerMode.CONTINUOUS -> TriggerType.CONTINUOUS
                    }
                ),
                previousIssues = recentIssues.takeLast(5)
            )

            try {
                val response = connectionManager.analyze(request)
                processResults(response)
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
            }
        }
    }

    private fun processResults(response: AnalysisResponse) {
        val filteredIssues = response.issues.filter { issue ->
            issue.severity.ordinal <= config.severityFilter.ordinal
        }

        recentIssues.addAll(filteredIssues)
        // Keep max 100 recent issues
        while (recentIssues.size > 100) {
            recentIssues.removeAt(0)
        }

        if (config.showNotifications && filteredIssues.isNotEmpty()) {
            showIssueNotification(filteredIssues)
        }
    }

    // -- Notifications --

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "Agent Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "TalkBack Agent connection status"
            }

            val issueChannel = NotificationChannel(
                ISSUE_CHANNEL_ID,
                "Accessibility Issues",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Accessibility issues found by analysis"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(statusChannel)
            nm.createNotificationChannel(issueChannel)
        }
    }

    private fun buildStatusNotification(status: String = "Idle"): Notification {
        val openIntent = Intent(this, IssueListActivity::class.java).let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val analyzeIntent = Intent(this, AgentClientService::class.java).apply {
            action = ACTION_ANALYZE_NOW
        }.let {
            PendingIntent.getService(
                this, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val issueCount = recentIssues.size
        val subtitle = if (issueCount > 0) {
            "$status | $issueCount issue${if (issueCount != 1) "s" else ""} found"
        } else {
            status
        }

        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("TalkBack Agent")
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_play, "Analyze Now", analyzeIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String? = null) {
        val notification = buildStatusNotification(
            status ?: when {
                connectionManager.isConnected -> "Connected: ${connectionManager.connectionInfo}"
                else -> "Disconnected"
            }
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showIssueNotification(issues: List<Issue>) {
        val openIntent = Intent(this, IssueListActivity::class.java).let {
            PendingIntent.getActivity(
                this, 2, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val clearIntent = Intent(this, AgentClientService::class.java).apply {
            action = ACTION_CLEAR_ISSUES
        }.let {
            PendingIntent.getService(
                this, 3, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle("Found ${issues.size} accessibility issue${if (issues.size != 1) "s" else ""}")

        issues.take(5).forEach { issue ->
            val icon = when (issue.severity) {
                IssueSeverity.ERROR -> "!!"
                IssueSeverity.WARNING -> "!"
                IssueSeverity.SUGGESTION -> "~"
            }
            style.addLine("$icon ${issue.issue}")
        }
        if (issues.size > 5) {
            style.addLine("+ ${issues.size - 5} more...")
        }

        val notification = NotificationCompat.Builder(this, ISSUE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Accessibility Issues Found")
            .setContentText("${issues.size} issue${if (issues.size != 1) "s" else ""}")
            .setStyle(style)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Clear", clearIntent)
            .setAutoCancel(false)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(ISSUE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "AgentClientService"
        private const val STATUS_CHANNEL_ID = "agent_status"
        private const val ISSUE_CHANNEL_ID = "agent_issues"
        private const val NOTIFICATION_ID = 9001
        private const val ISSUE_NOTIFICATION_ID = 9002

        const val ACTION_ANALYZE_NOW = "com.google.android.accessibility.talkback.agent.ANALYZE"
        const val ACTION_CLEAR_ISSUES = "com.google.android.accessibility.talkback.agent.CLEAR"
        const val ACTION_RECONNECT = "com.google.android.accessibility.talkback.agent.RECONNECT"
        const val ACTION_DISCONNECT = "com.google.android.accessibility.talkback.agent.DISCONNECT"

        fun start(context: Context) {
            val intent = Intent(context, AgentClientService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentClientService::class.java))
        }
    }
}
