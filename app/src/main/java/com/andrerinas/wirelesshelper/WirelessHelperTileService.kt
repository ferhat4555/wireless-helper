package com.andrerinas.wirelesshelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class WirelessHelperTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = WirelessHelperService.isRunning

        if (isRunning) {
            // If service is running, just stop it normally
            val serviceIntent = Intent(this, WirelessHelperService::class.java).apply {
                action = WirelessHelperService.ACTION_STOP
            }
            startService(serviceIntent)
        } else {
            val prefs = getSharedPreferences("WirelessHelperPrefs", MODE_PRIVATE)
            val currentMode = prefs.getInt("connection_mode", 0)

            // Check if Wi-Fi is enabled before starting the service via Quick Settings Tile
            WifiNotificationHelper.checkWifiAndConnect(this, connectionMode = currentMode) {
                val triggerIntent = Intent(this, TransparentTriggerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val pendingIntent = android.app.PendingIntent.getActivity(
                            this, 0, triggerIntent, 
                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        startActivityAndCollapse(pendingIntent)
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(triggerIntent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HUREV_WIFI", "Failed to launch trigger activity from tile: ${e.message}")
                    // Fallback: Start service directly if activity launch is blocked
                    val serviceIntent = Intent(this, WirelessHelperService::class.java).apply {
                        action = WirelessHelperService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                }
            }
        }
    }

    private fun updateTile() {
        val qsTile = qsTile ?: return
        val running = WirelessHelperService.isRunning
        val connected = WirelessHelperService.isConnected

        qsTile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.subtitle = when {
                connected -> "Connected"
                running -> "Searching..."
                else -> "Off"
            }
        }

        qsTile.updateTile()
    }

    companion object {
        @JvmStatic
        fun triggerUpdate(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(context, ComponentName(context, WirelessHelperTileService::class.java))
            }
        }
    }
}
