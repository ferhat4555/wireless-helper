package com.andrerinas.wirelesshelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WifiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("HUREV_WIFI", "WifiReceiver: Received intent ${intent.action ?: "PendingIntent Callback"}")
        
        // ConnectivityManager PendingIntents might not have a specific action
        checkWifiAndStart(context)
    }

    private fun checkWifiAndStart(context: Context) {
        WifiJobService.checkWifiAndStart(context)
    }
}