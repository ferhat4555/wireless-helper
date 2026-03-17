package com.andrerinas.wirelesshelper

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.wirelesshelper.strategy.BaseStrategy

/**
 * A transparent activity that surfaces the app to the foreground.
 * This is required to bypass "Background Activity Launch" (BAL) restrictions
 * introduced in modern Android versions (14+ / SDK 36).
 */
class TransparentTriggerActivity : AppCompatActivity() {

    private val TAG = "HUREV_TRIGGER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make activity invisible
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("intent", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("intent")
        }

        if (targetIntent != null) {
            Log.i(TAG, "TransparentTriggerActivity in foreground. Launching AA intent...")
            try {
                startActivity(targetIntent)
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w(TAG, "Legacy activity not found. Trying minimal broadcast fallback for AA 16.4+.")
                
                // Read params from the failed intent to build the broadcast
                val port = targetIntent.getIntExtra("PARAM_SERVICE_PORT", 5288)
                
                val receiverIntent = Intent().apply {
                    setClassName("com.google.android.projection.gearhead", "com.google.android.apps.auto.wireless.setup.receiver.WirelessStartupReceiver")
                    action = "com.google.android.apps.auto.wireless.setup.receiver.wirelessstartup.START"
                    putExtra("ip_address", "127.0.0.1")
                    putExtra("projection_port", port)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                sendBroadcast(receiverIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch AA from foreground: ${e.message}")
            }
        } else {
            Log.w(TAG, "No target intent provided to TransparentTriggerActivity.")
        }

        // Close the activity immediately after firing the trigger
        finish()
    }
}
