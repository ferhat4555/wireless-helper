package com.andrerinas.wirelesshelper.strategy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import kotlinx.coroutines.*

class StrategyWifiDirect(context: Context, scope: CoroutineScope) : BaseStrategy(context, scope) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val SERVICE_TYPE = "_aawireless._tcp"

    // WiFi Direct (P2P)
    private val p2pManager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var p2pReceiver: BroadcastReceiver? = null
    private var targetDeviceName: String? = null

    override fun start() {
        val prefs = context.getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        targetDeviceName = prefs.getString("wifi_direct_target_name", null)

        Log.i(TAG, "Strategy: WiFi Direct (Target: $targetDeviceName)")
        
        setupP2p()
        startNsdDiscovery()
    }

    private fun setupP2p() {
        if (p2pManager == null) return
        p2pChannel = p2pManager.initialize(context, context.mainLooper, null)
        
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }

        p2pReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        p2pManager.requestPeers(p2pChannel) { peers ->
                            val target = targetDeviceName ?: return@requestPeers
                            val device = peers.deviceList.find { it.deviceName.contains(target, ignoreCase = true) }
                            if (device != null && device.status == WifiP2pDevice.AVAILABLE) {
                                connectToPeer(device)
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            p2pManager.requestConnectionInfo(p2pChannel) { info ->
                                if (info.groupFormed) {
                                    val host = info.groupOwnerAddress.hostAddress
                                    Log.i(TAG, "WiFi Direct connected. Group Owner: $host")
                                    launchAndroidAuto(host, forceFakeNetwork = false)
                                }
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(p2pReceiver, intentFilter)
        
        // Start discovering peers
        p2pManager.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "P2P Peer Discovery Started") }
            override fun onFailure(reason: Int) { Log.e(TAG, "P2P Peer Discovery Failed: $reason") }
        })
    }

    private fun connectToPeer(device: WifiP2pDevice) {
        val config = android.net.wifi.p2p.WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        
        Log.i(TAG, "Attempting to connect to P2P device: ${device.deviceName}")
        p2pManager?.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "P2P Connect initiated") }
            override fun onFailure(reason: Int) { Log.e(TAG, "P2P Connect failed: $reason") }
        })
    }

    private fun startNsdDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, err: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        si.host.hostAddress?.let { 
                            launchAndroidAuto(it, forceFakeNetwork = false) 
                        }
                    }
                })
            }
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStopped(s: String) {}
            override fun onStartDiscoveryFailed(s: String, e: Int) {}
            override fun onStopDiscoveryFailed(s: String, e: Int) {}
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Start failed", e)
        }
    }

    override fun stop() {
        super.stop()
        try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
        discoveryListener = null
        
        try { context.unregisterReceiver(p2pReceiver) } catch (e: Exception) {}
        p2pReceiver = null
        
        p2pManager?.stopPeerDiscovery(p2pChannel, null)
        p2pChannel = null
    }
}
