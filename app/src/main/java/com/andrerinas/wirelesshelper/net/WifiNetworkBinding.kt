package com.andrerinas.wirelesshelper.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlin.jvm.Synchronized

object WifiNetworkBinding {

    private const val TAG = "HUREV_WIFI_BIND"

    @Volatile
    var currentNetwork: Network? = null
        private set

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun start(context: Context) {
        if (networkCallback != null) return

        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Wi-Fi (no internet) network available: $network")
                currentNetwork = network
                bindProcessToNetwork(cm, network)
            }

            override fun onLost(network: Network) {
                if (currentNetwork == network) {
                    Log.i(TAG, "Wi-Fi binding lost for $network")
                    currentNetwork = null
                    bindProcessToNetwork(cm, null)
                }
            }
        }
        networkCallback = callback
        try {
            cm.requestNetwork(request, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "requestNetwork failed", e)
            networkCallback = null
            connectivityManager = null
        }
    }

    @Synchronized
    fun stop(context: Context) {
        val cm = connectivityManager
            ?: context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cb = networkCallback
        if (cb != null && cm != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterNetworkCallback", e)
            }
        }
        networkCallback = null
        if (cm != null) {
            bindProcessToNetwork(cm, null)
        }
        currentNetwork = null
        connectivityManager = null
    }

    @Suppress("DEPRECATION")
    private fun bindProcessToNetwork(cm: ConnectivityManager, network: Network?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.bindProcessToNetwork(network)
            } else {
                ConnectivityManager.setProcessDefaultNetwork(network)
            }
        } catch (e: Exception) {
            Log.w(TAG, "bindProcessToNetwork", e)
        }
    }
}
