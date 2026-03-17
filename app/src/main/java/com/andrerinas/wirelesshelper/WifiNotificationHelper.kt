package com.andrerinas.wirelesshelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object WifiNotificationHelper {

    /**
     * Checks if Wi-Fi is enabled. If it is, executes the normal connection flow.
     * If not, shows either a UI Dialog or a System Notification.
     * * @param context The application or service context.
     * @param isFromUi Set to true if calling from an Activity to show a Popup Dialog instead of a Notification.
     * @param onConnectReady A lambda function containing the normal connection logic.
     */
    fun checkWifiAndConnect(context: Context, isFromUi: Boolean = false, onConnectReady: () -> Unit) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        // Check if Wi-Fi is currently enabled
        if (wifiManager.isWifiEnabled) {
            // Wi-Fi is on, trigger the callback to proceed
            onConnectReady() 
        } else {
            // Wi-Fi is off, check the context to display the appropriate alert
            if (isFromUi) {
                showWifiDialog(context)
            } else {
                showWifiNotification(context)
            }
        }
    }

    /**
     * Builds and displays an in-app Alert Dialog.
     * Used when the user is actively interacting with the app's UI.
     */
    private fun showWifiDialog(context: Context) {
        val title = context.getString(R.string.notification_wifi_off_title)
        val message = context.getString(R.string.notification_wifi_off_text)
        val actionText = context.getString(R.string.action_turn_on_wifi)

        MaterialAlertDialogBuilder(context, R.style.DarkAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(actionText) { _, _ ->
                // Open the Wi-Fi settings panel
                val intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
                context.startActivity(intentAction)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Builds and displays a high-priority system notification.
     * Used when the app is triggered in the background (Bluetooth, Widget, Tile).
     */
    private fun showWifiNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "wireless_helper_alerts"

        // 1. Create Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.channel_name_alerts)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Create Intent to open Wi-Fi settings
        val intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intentAction,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Build Notification
        val title = context.getString(R.string.notification_wifi_off_title)
        val text = context.getString(R.string.notification_wifi_off_text)
        val actionText = context.getString(R.string.action_turn_on_wifi)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_wireless_helper) 
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_preferences, actionText, pendingIntent)
            .build()

        // 4. Show Notification
        notificationManager.notify(1001, notification)
    }
}
