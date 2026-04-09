package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope

/**
 * A connection strategy using Google Nearby Connections API.
 * Inspired by WiFi-Launcher 4.0.
 */
class StrategyNearby(context: Context, scope: CoroutineScope) : BaseStrategy(context, scope) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.andrerinas.headunitrevived.NEARBY"

    override fun start() {
        Log.i("NearbyStrategy", "Starting Nearby Connections (Discovery & Advertising)...")
        startDiscovery()
        startAdvertising()
    }

    override fun stop() {
        Log.i("NearbyStrategy", "Stopping Nearby Connections...")
        connectionsClient.stopDiscovery()
        connectionsClient.stopAdvertising()
        cleanup()
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { Log.d("NearbyStrategy", "Discovery started successfully") }
            .addOnFailureListener { e -> Log.e("NearbyStrategy", "Discovery failed: ${e.message}") }
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            android.os.Build.MODEL, // Use phone model as name
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        )
            .addOnSuccessListener { Log.d("NearbyStrategy", "Advertising started successfully") }
            .addOnFailureListener { e -> Log.e("NearbyStrategy", "Advertising failed: ${e.message}") }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i("NearbyStrategy", "Endpoint found: ${info.endpointName} ($endpointId)")
            // If we found the headunit, we try to connect to it
            connectionsClient.requestConnection(android.os.Build.MODEL, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i("NearbyStrategy", "Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i("NearbyStrategy", "Connection initiated with $endpointId. Accepting...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.i("NearbyStrategy", "Connected to $endpointId")
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.w("NearbyStrategy", "Connection rejected by $endpointId")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.e("NearbyStrategy", "Connection error with $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i("NearbyStrategy", "Disconnected from $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val data = payload.asBytes()?.let { String(it) }
                Log.i("NearbyStrategy", "Received data from $endpointId: $data")
                // Future: If data contains IP, call launchAndroidAuto(ip)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Monitor transfer progress
        }
    }
}
