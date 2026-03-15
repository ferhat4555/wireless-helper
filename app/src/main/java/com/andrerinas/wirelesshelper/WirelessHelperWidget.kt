package com.andrerinas.wirelesshelper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class WirelessHelperWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_WIDGET_CLICK) {
            val isRunning = WirelessHelperService.isRunning
            val serviceIntent = Intent(context, WirelessHelperService::class.java)
            
            if (isRunning) {
                // If service is running, just stop it normally
                serviceIntent.action = WirelessHelperService.ACTION_STOP
                context.startService(serviceIntent)
            } else {
                // Check if Wi-Fi is enabled before starting the service via Widget
                WifiNotificationHelper.checkWifiAndConnect(context) {
                    serviceIntent.action = WirelessHelperService.ACTION_START
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
        
        // Update all instances
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WirelessHelperWidget::class.java))
        onUpdate(context, manager, ids)
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.andrerinas.wirelesshelper.ACTION_WIDGET_CLICK"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_launcher)
            
            val isRunning = WirelessHelperService.isRunning
            val isConnected = WirelessHelperService.isConnected
            
            val color = when {
                isConnected -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
                isRunning -> ContextCompat.getColor(context, R.color.brand_teal)
                else -> ContextCompat.getColor(context, android.R.color.darker_gray)
            }
            
            views.setInt(R.id.widget_button, "setColorFilter", color)
            
            val intent = Intent(context, WirelessHelperWidget::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, WirelessHelperWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WirelessHelperWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
