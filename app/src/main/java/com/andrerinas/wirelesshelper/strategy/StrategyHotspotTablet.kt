package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket

class StrategyHotspotTablet(context: Context, private val scope: CoroutineScope) : BaseStrategy(context, scope) {

    private var serverSocket: ServerSocket? = null
    private val PORT_DISCOVERY = 5289

    override fun start() {
        Log.i(TAG, "Strategy: Hotspot Tablet (Passive TCP Listener)")
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
                        Log.i(TAG, "Trigger from $remoteIp")
                        // In Tablet Hotspot mode, we use normal network routing (false for FakeNet)
                        launchAndroidAuto(remoteIp, forceFakeNetwork = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP Server error: ${e.message}")
            }
        }
    }

    override fun stop() {
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }
}
