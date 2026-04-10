package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.util.Log
import com.andrerinas.wirelesshelper.connection.NearbySocket
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A connection strategy using Google Nearby Connections API.
 * The Phone (WirelessHelper) acts as an ADVERTISER only.
 * Uses Stream Tunneling (like Emil's implementation) for robust connections.
 */
class StrategyNearby(context: Context, scope: CoroutineScope) : BaseStrategy(context, scope) {

    override val TAG = "HUREV_NEARBY"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.andrerinas.headunitrevived.NEARBY"
    private var activeNearbySocket: NearbySocket? = null

    override fun start() {
        Log.i(TAG, "NearbyStrategy: Starting Nearby Connections (Advertiser only)...")
        startAdvertising()
    }

    override fun stop() {
        Log.i(TAG, "NearbyStrategy: Stopping Nearby Connections...")
        connectionsClient.stopAdvertising()
        connectionsClient.stopAllEndpoints()
        cleanup()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
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

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i(TAG, "NearbyStrategy: Connection initiated with $endpointId. Accepting and stopping advertising...")
            connectionsClient.stopAdvertising() // Stop immediately to free up radio bandwidth
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.i(TAG, "NearbyStrategy: Connected to $endpointId. Waiting for Tablet to initiate stream tunnel...")
                    // We wait for the Tablet to send its stream first in onPayloadReceived.
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.w(TAG, "NearbyStrategy: Connection rejected by $endpointId")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.e(TAG, "NearbyStrategy: Connection error with $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "NearbyStrategy: Disconnected from $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.i(TAG, "NearbyStrategy: Payload RECEIVED from $endpointId. Type: ${payload.type}")
            if (payload.type == Payload.Type.STREAM) {
                Log.i(TAG, "NearbyStrategy: Received STREAM payload. Completing bidirectional tunnel.")
                
                val socket = NearbySocket()
                activeNearbySocket = socket
                
                // 1. Map incoming stream from Tablet to socket's input
                socket.inputStreamWrapper = payload.asStream()?.asInputStream()
                
                // 2. Create outgoing pipe (Phone -> Tablet) and send it back
                val pipes = android.os.ParcelFileDescriptor.createPipe()
                socket.outputStreamWrapper = android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])
                val phoneToTabletPayload = Payload.fromStream(android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]))
                
                Log.d(TAG, "NearbyStrategy: Sending Phone->Tablet stream payload back...")
                connectionsClient.sendPayload(endpointId, phoneToTabletPayload)
                
                // 3. Launch Android Auto with this tunnel socket
                launchAndroidAuto("127.0.0.1", preConnectedSocket = socket)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "NearbyStrategy: Payload transfer SUCCESS")
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.e(TAG, "NearbyStrategy: Payload transfer FAILURE")
            }
        }
    }
}
