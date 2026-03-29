package com.andrerinas.wirelesshelper

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.andrerinas.wirelesshelper.strategy.BaseStrategy
import com.andrerinas.wirelesshelper.strategy.ConnectionStrategy
import com.andrerinas.wirelesshelper.strategy.StrategyHotspotPhone
import com.andrerinas.wirelesshelper.strategy.StrategyHotspotTablet
import com.andrerinas.wirelesshelper.strategy.StrategySharedNetwork
import com.andrerinas.wirelesshelper.strategy.StrategyWifiDirect
import com.andrerinas.wirelesshelper.net.WifiNetworkBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class WirelessHelperService : Service(), BaseStrategy.StateListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentStrategy: ConnectionStrategy? = null

    companion object {
        private const val TAG = "HUREV_WIFI"
        private const val CHANNEL_ID = "WirelessHelperChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        var isRunning = false
            internal set
        var isConnected = false
            internal set
    }

    override fun onCreate() {
        super.onCreate()
        setupLocks()
        createNotificationChannel()
        startForeground(1, createNotification(getString(R.string.notif_searching)))
    }

    private fun setupLocks() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("WirelessHelperLock")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WirelessHelper:WakeLock")
            
            Log.i(TAG, "Locks initialized")
        } catch (e: Exception) { Log.e(TAG, "Failed to initialize locks: ${e.message}") }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max at a time
                Log.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private fun startSelectedStrategy() {
        val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("connection_mode", 0)
        
        acquireWakeLock()
        currentStrategy?.stop()

        if (mode == 2) {
            WifiNetworkBinding.start(this)
        } else {
            WifiNetworkBinding.stop(this)
        }

        currentStrategy = when (mode) {
            0 -> StrategySharedNetwork(this, serviceScope)
            1 -> StrategyHotspotPhone(this, serviceScope)
            2 -> StrategyHotspotTablet(this, serviceScope)
            3 -> StrategyWifiDirect(this, serviceScope)
            else -> StrategySharedNetwork(this, serviceScope)
        }
        
        if (currentStrategy is BaseStrategy) {
            (currentStrategy as BaseStrategy).stateListener = this
        }
        
        Log.i(TAG, "Starting strategy for mode $mode")
        currentStrategy?.start()
    }

    override fun onProxyConnected() {
        isConnected = true
        acquireWakeLock()
        updateNotification(getString(R.string.notif_connected))
        updateAllUIs()
    }

    override fun onProxyDisconnected() {
        isConnected = false
        Log.i(TAG, "AA proxy connection lost.")
        updateAllUIs()

        val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        val autoReconnect = prefs.getBoolean("bt_auto_reconnect", false)
        val targetMacs = prefs.getStringSet("auto_start_bt_macs", emptySet()) ?: emptySet()
        val legacyTargetMac = prefs.getString("auto_start_bt_mac", null)

        val anyTargetConnected = targetMacs.any { isBluetoothDeviceConnected(it) } || 
                                (legacyTargetMac != null && isBluetoothDeviceConnected(legacyTargetMac))

        if (autoReconnect && anyTargetConnected) {
            Log.i(TAG, "Target Bluetooth still connected and auto-reconnect enabled. Restarting strategy...")
            acquireWakeLock()
            updateNotification(getString(R.string.notif_searching))
            serviceScope.launch {
                delay(3000) // Buffer vor Neustart
                startSelectedStrategy()
            }
        } else {
            Log.i(TAG, "Stopping service.")
            releaseWakeLock()
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothDeviceConnected(mac: String): Boolean {
        try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val adapter = bm.adapter ?: return false
            
            val device = adapter.getRemoteDevice(mac) ?: return false
            
            // Use reflection to access hidden 'isConnected' method for precise per-device status.
            // Suggested by Gemini Code Assist to fix incorrect profile-based detection.
            return try {
                val isConnectedMethod = device.javaClass.getMethod("isConnected")
                isConnectedMethod.invoke(device) as? Boolean ?: false
            } catch (e: Exception) {
                // Fallback: check profile connection if reflection fails
                val a2dp = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)
                val hfp = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET)
                a2dp == android.bluetooth.BluetoothProfile.STATE_CONNECTED || 
                hfp == android.bluetooth.BluetoothProfile.STATE_CONNECTED
            }
        } catch (e: Exception) {
            return false
        }
    }

    override fun onLaunchTimeout() {
        Log.i(TAG, "Launch timeout. Resuming discovery.")
        updateNotification(getString(R.string.notif_searching))
        updateAllUIs()
        
        // Restart the strategy to resume searching
        currentStrategy?.stop()
        startSelectedStrategy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Service was restarted by the system
            if (isRunning) {
                Log.i(TAG, "Service restarted by system. Resuming strategy.")
                startSelectedStrategy()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_STOP -> {
                isRunning = false
                releaseWakeLock()
                updateAllUIs()
                stopSelf()
            }
            ACTION_START -> {
                isRunning = true
                isConnected = false
                startSelectedStrategy()
                updateAllUIs()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isConnected = false
        WifiNetworkBinding.stop(this)
        currentStrategy?.stop()
        if (currentStrategy is BaseStrategy) {
            (currentStrategy as BaseStrategy).cleanup()
        }
        try { 
            multicastLock?.release()
            releaseWakeLock()
        } catch (e: Exception) { }
        serviceJob.cancel()
        updateAllUIs()
        super.onDestroy()
    }

    private fun updateAllUIs() {
        WirelessHelperWidget.triggerUpdate(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            WirelessHelperTileService.triggerUpdate(this)
        }
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(content))
    }

    private fun createNotification(content: String): Notification {
        val stopIntent = Intent(this, WirelessHelperService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name)).setContentText(content)
            .setSmallIcon(R.drawable.ic_wireless_helper).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_stop), pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onBind(intent: Intent?) = null
}