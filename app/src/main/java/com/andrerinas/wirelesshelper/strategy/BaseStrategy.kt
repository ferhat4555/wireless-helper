package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Parcelable
import android.util.Log
import com.andrerinas.wirelesshelper.connection.AapProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseStrategy(protected val context: Context, private val scope: CoroutineScope) : ConnectionStrategy {

    interface StateListener {
        fun onProxyConnected()
        fun onProxyDisconnected()
        fun onLaunchTimeout()
    }

    protected val TAG = "HUREV_WIFI"
    private var activeProxy: AapProxy? = null
    var stateListener: StateListener? = null
    protected val isLaunching = AtomicBoolean(false)
    protected var connectionEstablished = AtomicBoolean(false)
    protected var strategyJob: Job? = null

    protected fun getStrategyScope(): CoroutineScope {
        val job = Job(scope.coroutineContext[Job])
        strategyJob = job
        return CoroutineScope(scope.coroutineContext + job)
    }

    companion object {
        const val ACTION_TRIGGER_INTENT = "com.andrerinas.wirelesshelper.ACTION_TRIGGER_INTENT"
    }

    private fun createFakeNetwork(netId: Int): Network? {
        val parcel = android.os.Parcel.obtain()
        return try {
            parcel.writeInt(netId)
            parcel.setDataPosition(0)
            Network.CREATOR.createFromParcel(parcel)
        } catch (e: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }

    protected fun launchAndroidAuto(hostIp: String, forceFakeNetwork: Boolean = false) {
        if (isLaunching.get()) return
        if (!isLaunching.compareAndSet(false, true)) return
        
        Log.i(TAG, "Strategy triggering PROXY launch for $hostIp")
        connectionEstablished.set(false)
        
        stop()

        scope.launch {
            try {
                // 1. Start the Proxy Server with a listener
                val proxy = AapProxy(hostIp, listener = object : AapProxy.Listener {
                    override fun onConnected() {
                        Log.i(TAG, "AA is now flowing through proxy")
                        connectionEstablished.set(true)
                        stateListener?.onProxyConnected()
                    }
                    override fun onDisconnected() {
                        Log.i(TAG, "AA proxy connection lost")
                        stateListener?.onProxyDisconnected()
                    }
                })
                
                activeProxy = proxy
                val localPort = proxy.start()

                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                // Determine which network to use. If forced, or if offline, use Network 0.
                val targetNetwork = if (forceFakeNetwork) {
                    createFakeNetwork(0)
                } else {
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) connectivityManager.activeNetwork else null)
                        ?: createFakeNetwork(0)
                }

                val wifiInfo: Parcelable? = try {
                    val clazz = Class.forName("android.net.wifi.WifiInfo")
                    val constructor = clazz.getDeclaredConstructor()
                    constructor.isAccessible = true
                    constructor.newInstance() as Parcelable
                } catch (e: Exception) { null }

                val intent = Intent().apply {
                    setClassName("com.google.android.projection.gearhead", "com.google.android.apps.auto.wireless.setup.service.impl.WirelessStartupActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("PARAM_HOST_ADDRESS", "127.0.0.1")
                    putExtra("PARAM_SERVICE_PORT", localPort)
                    targetNetwork?.let { putExtra("PARAM_SERVICE_WIFI_NETWORK", it) }
                    wifiInfo?.let { putExtra("wifi_info", it) }
                }

                Log.i(TAG, "Firing Intent. Host=127.0.0.1, Port=$localPort, Network=$targetNetwork")
                
                // 1. Try via Broadcast (if TransparentTriggerActivity is active)
                val broadcastIntent = Intent(ACTION_TRIGGER_INTENT).apply {
                    putExtra("intent", intent)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(broadcastIntent)

                // 2. Standard Start (Fallback for Widget/App usage)
                context.startActivity(intent)

                // The lock stays active until proxy confirms connection or timeout
                delay(15000) 
                
                if (!connectionEstablished.get()) {
                    Log.w(TAG, "Launch timed out without connection.")
                    activeProxy?.stop()
                    activeProxy = null
                    stateListener?.onLaunchTimeout()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Proxy Launch failed: ${e.message}")
                activeProxy?.stop()
                activeProxy = null
            } finally {
                isLaunching.set(false)
            }
        }
    }

    override fun stop() {
        strategyJob?.cancel()
        strategyJob = null
    }

    fun cleanup() {
        activeProxy?.stop()
        activeProxy = null
        isLaunching.set(false)
    }
}
