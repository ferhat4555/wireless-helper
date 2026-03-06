package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket

class StrategyHotspotPhone(context: Context, private val scope: CoroutineScope) : BaseStrategy(context, scope) {

    private var serverSocket: ServerSocket? = null
    private val PORT_DISCOVERY = 5289

    override fun start() {
        Log.i(TAG, "Strategy: Hotspot Phone (TCP 5289 Trigger Listener)")
        
        getStrategyScope().launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT_DISCOVERY))
                }
                while (isActive) {
                    val client = serverSocket?.accept()
                    val remoteIp = client?.inetAddress?.hostAddress
                    client?.close()
                    
                    if (remoteIp != null) {
                        Log.i(TAG, "TCP Trigger received from Tablet at $remoteIp")
                        // Use forceFakeNetwork = true to bypass cellular routing in hotspot mode
                        launchAndroidAuto(remoteIp, forceFakeNetwork = true)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Hotspot TCP Server stopped or error: ${e.message}")
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping Hotspot TCP Listener")
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }
}
