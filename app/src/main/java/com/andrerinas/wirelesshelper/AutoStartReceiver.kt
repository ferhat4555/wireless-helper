package com.andrerinas.wirelesshelper

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class AutoStartReceiver : BroadcastReceiver() {
    private val TAG = "HUREV_AUTOSTART"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Broadcast received: $action")

        val prefs = context.getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        val autoStartMode = prefs.getInt("auto_start_mode", 0) // 0=Off, 1=BT
        val connectionMode = prefs.getInt("connection_mode", 0)

        if (autoStartMode == 0) return

        val targetMacs = prefs.getStringSet("auto_start_bt_macs", emptySet()) ?: emptySet()
        val legacyTargetMac = prefs.getString("auto_start_bt_mac", null)

        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val deviceAddress = device?.address ?: return
        val isTarget = targetMacs.contains(deviceAddress) || deviceAddress == legacyTargetMac

        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            Log.i(TAG, "BT Device connected: ${device.name} ($deviceAddress)")

            if (isTarget) {
                // Precise check if the device is actually fully connected (not just ACL)
                // Using reflection
                val isFullyConnected = try {
                    val isConnectedMethod = device.javaClass.getMethod("isConnected")
                    isConnectedMethod.invoke(device) as? Boolean ?: true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check isConnected via reflection, falling back to true.", e)
                    true // Fallback to true if reflection fails
                }

                if (!isFullyConnected) {
                    Log.w(TAG, "Device ACL is connected but isConnected() is false. Skipping start.")
                    return
                }

                Log.i(TAG, "MATCH! Checking Wi-Fi state before starting service...")

                // Wrap the service start logic to ensure Wi-Fi is enabled
                WifiNotificationHelper.checkWifiAndConnect(context, connectionMode = connectionMode) {
                    startService(context)
                }
            }
        } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            Log.i(TAG, "BT Device disconnected: ${device.name} ($deviceAddress)")

            if (isTarget) {
                val stopOnDisconnect = prefs.getBoolean("bt_disconnect_stop", false)
                if (stopOnDisconnect) {
                    Log.i(TAG, "MATCH! Stopping service as requested by settings...")
                    stopService(context)
                }
            }
        }
    }

    private fun startService(context: Context) {
        val serviceIntent = Intent(context, WirelessHelperService::class.java).apply {
            action = WirelessHelperService.ACTION_START
        }
        try {
            Log.i(TAG, "Firing Foreground Service Intent...")
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-start service: ${e.message}", e)
        }
    }

    private fun stopService(context: Context) {
        val serviceIntent = Intent(context, WirelessHelperService::class.java).apply {
            action = WirelessHelperService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }
}
