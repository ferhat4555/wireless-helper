package com.andrerinas.wirelesshelper

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

class WifiJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // This is the fallback/reboot-survival path
        Log.i(TAG, "JobService triggered by OS.")
        Thread {
            checkWifiAndStart(this)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val TAG = "HUREV_JOB"
        private const val JOB_ID = 1001

        fun checkWifiAndStart(context: Context): Boolean {
            val prefs = context.getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
            val mode = prefs.getInt("auto_start_mode", 0)
            if (mode != 2) return false

            val targetSsid = prefs.getString("auto_start_wifi_ssid", null) ?: return false
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = wifiManager.connectionInfo.ssid?.replace("\"", "") ?: ""
            val currentSsid = if (ssid == "<unknown ssid>") "" else ssid

            Log.d(TAG, "Checking SSID: Current='$currentSsid', Target='$targetSsid'")

            if (currentSsid.equals(targetSsid, ignoreCase = true)) {
                Log.i(TAG, "Match! Starting Service.")
                val startIntent = Intent(context, WirelessHelperService::class.java).apply {
                    action = WirelessHelperService.ACTION_START
                }
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(startIntent)
                    } else {
                        context.startService(startIntent)
                    }
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Start failed: ${e.message}")
                }
            }
            return false
        }

        fun schedule(context: Context) {
            Log.i(TAG, "Registering WiFi Auto-Start...")

            // 1. Modern way: Network Callback with PendingIntent (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                
                val intent = Intent(context, WifiReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                )
                
                try {
                    cm.registerNetworkCallback(request, pendingIntent)
                    Log.i(TAG, "NetworkCallback registered (Immediate trigger)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register NetworkCallback: ${e.message}")
                }
            }

            // 2. Legacy/Survival way: JobScheduler
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val componentName = ComponentName(context, WifiJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setPeriodic(15 * 60 * 1000)
            } else {
                builder.setMinimumLatency(1000)
            }
            jobScheduler.schedule(builder.build())
        }

        fun cancel(context: Context) {
            Log.i(TAG, "Cancelling all WiFi triggers")
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val intent = Intent(context, WifiReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0))
                if (pendingIntent != null) {
                    cm.unregisterNetworkCallback(pendingIntent)
                }
            }
        }
    }
}
