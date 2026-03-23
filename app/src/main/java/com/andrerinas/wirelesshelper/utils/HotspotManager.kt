package com.andrerinas.wirelesshelper.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

object HotspotManager {
    private const val TAG = "HotspotManager"

    /**
     * Attempts to enable the WiFi Hotspot.
     * Note: This is highly restricted on modern Android versions (8.0+).
     * It uses reflection to access hidden ConnectivityManager methods.
     */
    fun setHotspotEnabled(context: Context, enabled: Boolean): Boolean {
        Log.i(TAG, "Attempting to set hotspot enabled: $enabled")
        
        // Method 1: Legacy reflection (Android < 8.0)
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wifiManager.javaClass.getMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            return method.invoke(wifiManager, null, enabled) as Boolean
        } catch (e: Exception) {
            Log.d(TAG, "Legacy setWifiApEnabled failed: ${e.message}")
        }

        // Method 2: ConnectivityManager.startTethering (Android 8.0 - 10.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val methods = connectivityManager.javaClass.declaredMethods
                val startTethering = methods.find { it.name == "startTethering" }
                val stopTethering = methods.find { it.name == "stopTethering" }

                if (enabled && startTethering != null) {
                    // startTethering(int type, boolean showProvisioningUi, OnStartTetheringCallback callback)
                    // type 0 is TYPE_WIFI
                    startTethering.invoke(connectivityManager, 0, false, null)
                    return true
                } else if (!enabled && stopTethering != null) {
                    stopTethering.invoke(connectivityManager, 0)
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "ConnectivityManager tethering call failed: ${e.message}")
            }
        }

        Log.w(TAG, "Could not enable hotspot automatically. System restrictions apply.")
        return false
    }
}
