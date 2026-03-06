package com.andrerinas.wirelesshelper.connection

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class AapProxy(private val remoteIp: String, private val remotePort: Int = 5288, private val listener: Listener? = null) {
    
    interface Listener {
        fun onConnected()
        fun onDisconnected()
    }

    private val TAG = "HUREV_PROXY"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeBridges = AtomicInteger(0)

    fun start(): Int {
        serverSocket = ServerSocket(0)
        val localPort = serverSocket!!.localPort
        isRunning = true
        
        Log.i(TAG, "Proxy started on localhost:$localPort, forwarding to $remoteIp:$remotePort")
        
        scope.launch {
            try {
                while (isRunning) {
                    val aaSocket = serverSocket?.accept() ?: break
                    Log.i(TAG, "Android Auto connected to proxy")
                    launchBridge(aaSocket)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Proxy server stopped: ${e.message}")
            }
        }
        
        return localPort
    }

    private fun launchBridge(aaSocket: Socket) {
        scope.launch {
            var tabletSocket: Socket? = null
            try {
                activeBridges.incrementAndGet()
                listener?.onConnected()

                tabletSocket = Socket(remoteIp, remotePort)
                Log.i(TAG, "Bridge established: AA <-> Tablet ($remoteIp)")

                val aaIn = aaSocket.getInputStream()
                val aaOut = aaSocket.getOutputStream()
                val tabletIn = tabletSocket.getInputStream()
                val tabletOut = tabletSocket.getOutputStream()

                // Two-way pump
                val job1 = launch { pump(aaIn, tabletOut, "AA -> Tablet") }
                val job2 = launch { pump(tabletIn, aaOut, "Tablet -> AA") }

                joinAll(job1, job2)
            } catch (e: Exception) {
                Log.e(TAG, "Bridge error: ${e.message}")
            } finally {
                Log.i(TAG, "Bridge closed")
                try { aaSocket.close() } catch (e: Exception) {}
                try { tabletSocket?.close() } catch (e: Exception) {}
                
                if (activeBridges.decrementAndGet() <= 0) {
                    listener?.onDisconnected()
                }
            }
        }
    }

    private suspend fun pump(input: InputStream, output: OutputStream, name: String) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(16384)
        try {
            while (isRunning) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "$name error: ${e.message}")
        } finally {
            if (isRunning) stop()
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
        scope.cancel()
    }
}
