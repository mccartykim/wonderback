package com.google.android.accessibility.talkback.agent.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.android.accessibility.talkback.agent.model.AgentConfig
import com.google.android.accessibility.talkback.agent.model.AnalysisRequest
import com.google.android.accessibility.talkback.agent.model.AnalysisResponse
import kotlinx.coroutines.*
import java.io.IOException
import java.net.NetworkInterface

/**
 * Manages the connection to the macOS analysis server.
 * Tries connection methods in priority order: ADB reverse → mDNS → USB tethering → manual.
 * Handles reconnection with exponential backoff.
 */
class ConnectionManager(
    private val context: Context,
    private val config: AgentConfig
) {
    private var _currentConnection: ServerConnection? = null

    /** The active server connection, or null if disconnected. */
    val currentConnection: ServerConnection?
        get() = _currentConnection

    private var connectionState = ConnectionState.DISCONNECTED
    private val listeners = mutableListOf<ConnectionListener>()
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isConnected: Boolean
        get() = connectionState == ConnectionState.CONNECTED

    val state: ConnectionState
        get() = connectionState

    val connectionInfo: String
        get() = currentConnection?.description ?: "Not connected"

    /**
     * Attempt to connect using configured method or auto-discovery.
     */
    suspend fun connect(): Boolean {
        if (isConnected) return true

        updateState(ConnectionState.CONNECTING)

        val connection = when (config.connectionMethod) {
            AgentConfig.ConnectionMethod.AUTO -> tryAutoConnect()
            AgentConfig.ConnectionMethod.ADB_REVERSE -> tryAdbReverse()
            AgentConfig.ConnectionMethod.NETWORK -> tryMdnsDiscovery()
            AgentConfig.ConnectionMethod.USB_TETHERING -> tryUsbTethering()
            AgentConfig.ConnectionMethod.MANUAL -> tryManualConfig()
        }

        if (connection != null) {
            _currentConnection = connection
            updateState(ConnectionState.CONNECTED)
            Log.i(TAG, "Connected via: ${connection.description}")
            return true
        }

        updateState(ConnectionState.DISCONNECTED)
        Log.w(TAG, "All connection methods failed")
        return false
    }

    /**
     * Send analysis request to server.
     */
    suspend fun analyze(request: AnalysisRequest): AnalysisResponse {
        val conn = _currentConnection ?: throw IOException("Not connected to server")

        return try {
            conn.analyze(request)
        } catch (e: IOException) {
            Log.w(TAG, "Analysis request failed, attempting reconnect", e)
            updateState(ConnectionState.RECONNECTING)
            scheduleReconnect()
            throw e
        }
    }

    /**
     * Disconnect from server.
     */
    fun disconnect() {
        reconnectJob?.cancel()
        _currentConnection?.close()
        _currentConnection = null
        updateState(ConnectionState.DISCONNECTED)
    }

    fun addListener(listener: ConnectionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    // -- Private connection methods --

    private suspend fun tryAutoConnect(): ServerConnection? {
        return tryAdbReverse()
            ?: tryMdnsDiscovery()
            ?: tryUsbTethering()
            ?: tryManualConfig()
    }

    /**
     * ADB reverse: device connects to localhost which is tunneled to Mac via USB.
     * Most reliable method - works on any network, no IP discovery needed.
     */
    private suspend fun tryAdbReverse(): ServerConnection? {
        val port = config.serverPort
        return tryHttpConnection("http://localhost:$port", "ADB reverse (localhost:$port)")
    }

    /**
     * mDNS/NSD: discover server on local network via Bonjour/Zeroconf.
     * Works wirelessly but requires same network and mDNS support.
     */
    private suspend fun tryMdnsDiscovery(): ServerConnection? {
        return withTimeoutOrNull(MDNS_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

                val discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.d(TAG, "mDNS discovery started for $serviceType")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.serviceType == SERVICE_TYPE) {
                            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                    Log.w(TAG, "mDNS resolve failed: $errorCode")
                                }

                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val host = info.host.hostAddress
                                    val port = info.port
                                    Log.i(TAG, "mDNS resolved: $host:$port")

                                    scope.launch {
                                        val conn = tryHttpConnection(
                                            "http://$host:$port",
                                            "mDNS ($host:$port)"
                                        )
                                        if (continuation.isActive) {
                                            continuation.resumeWith(Result.success(conn))
                                        }
                                    }
                                }
                            })
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "mDNS service lost: ${serviceInfo.serviceName}")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.d(TAG, "mDNS discovery stopped")
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.w(TAG, "mDNS discovery start failed: $errorCode")
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(null))
                        }
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.w(TAG, "mDNS discovery stop failed: $errorCode")
                    }
                }

                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

                continuation.invokeOnCancellation {
                    try {
                        nsdManager.stopServiceDiscovery(discoveryListener)
                    } catch (e: Exception) {
                        // Already stopped
                    }
                }
            }
        }
    }

    /**
     * USB tethering: connect over the USB network interface.
     * Reliable like USB but doesn't need ADB.
     */
    private suspend fun tryUsbTethering(): ServerConnection? {
        val usbInterfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.filter { iface ->
                iface.name.contains("rndis") ||
                iface.name.contains("usb") ||
                iface.name.contains("ncm")
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (usbInterfaces.isEmpty()) return null

        // Try common gateway addresses for USB tethering
        val gatewayAddresses = listOf(
            "192.168.42.129",
            "192.168.42.1",
            "192.168.43.1",
            "192.168.44.1"
        )

        val port = config.serverPort
        for (address in gatewayAddresses) {
            val conn = tryHttpConnection(
                "http://$address:$port",
                "USB tethering ($address:$port)"
            )
            if (conn != null) return conn
        }
        return null
    }

    /**
     * Manual: connect to user-specified address.
     */
    private suspend fun tryManualConfig(): ServerConnection? {
        val address = config.manualServerAddress
        val url = if (address.startsWith("http")) address else "http://$address"
        return tryHttpConnection(url, "Manual ($address)")
    }

    private suspend fun tryHttpConnection(baseUrl: String, description: String): ServerConnection? {
        return try {
            val conn = HttpServerConnection(baseUrl, description)
            if (conn.ping()) conn else null
        } catch (e: Exception) {
            Log.d(TAG, "Connection to $description failed: ${e.message}")
            null
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var delayMs = RECONNECT_BASE_DELAY_MS
            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                delay(delayMs)
                Log.i(TAG, "Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS")
                if (connect()) {
                    return@launch
                }
                delayMs = (delayMs * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)
            }
            updateState(ConnectionState.DISCONNECTED)
            Log.e(TAG, "All reconnect attempts failed")
        }
    }

    private fun updateState(newState: ConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            listeners.forEach { it.onConnectionStateChanged(newState, connectionInfo) }
        }
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
    }

    interface ConnectionListener {
        fun onConnectionStateChanged(state: ConnectionState, info: String)
    }

    companion object {
        private const val TAG = "AgentConnectionManager"
        private const val SERVICE_TYPE = "_talkback-agent._tcp."
        private const val MDNS_TIMEOUT_MS = 5000L
        private const val RECONNECT_BASE_DELAY_MS = 2000L
        private const val RECONNECT_MAX_DELAY_MS = 16000L
        private const val MAX_RECONNECT_ATTEMPTS = 4
    }
}
