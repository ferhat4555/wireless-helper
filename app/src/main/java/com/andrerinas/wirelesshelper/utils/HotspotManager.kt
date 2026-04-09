package com.andrerinas.wirelesshelper.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.dx.DexMaker
import com.android.dx.TypeId
import java.lang.reflect.Method

/**
 * Manages WiFi Hotspot (tethering) using reflection + dexmaker.
 *
 * Android 8+ changed OnStartTetheringCallback from an interface to an abstract class,
 * so java.lang.reflect.Proxy cannot be used. Instead, we use dexmaker to generate
 * a real subclass at runtime
 */
object HotspotManager {
    private const val TAG = "HUREV_WIFI"
    private const val CALLBACK_CLASS = "android.net.ConnectivityManager\$OnStartTetheringCallback"

    /** AOSP [WifiManager] soft AP states (hidden API); used only via reflection. */
    private const val WIFI_AP_STATE_ENABLING = 12
    private const val WIFI_AP_STATE_ENABLED = 13

    // Cache the generated callback class so we only do bytecode generation once
    private var cachedCallbackClass: Class<*>? = null

    /** Cached [WifiManager.getWifiApState] reflection; avoids repeated [Class.getMethod] lookups. */
    private var getWifiApStateMethod: Method? = null

    /**
     * Whether Wi‑Fi hotspot (soft AP) is on or transitioning on, via [WifiManager.getWifiApState].
     * Returns null if the method is missing or fails — callers should not assume we enabled hotspot.
     */
    fun isWifiHotspotActive(context: Context): Boolean? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = getWifiApStateMethod
                ?: wm.javaClass.getMethod("getWifiApState").also { getWifiApStateMethod = it }
            val state = method.invoke(wm) as Int
            state == WIFI_AP_STATE_ENABLED || state == WIFI_AP_STATE_ENABLING
        } catch (e: Exception) {
            Log.d(TAG, "[HotspotManager] getWifiApState unavailable: ${e.message}")
            null
        }
    }

    fun setHotspotEnabled(context: Context, enabled: Boolean): Boolean {
        Log.i(TAG, "[HotspotManager] Setting hotspot: $enabled (API ${Build.VERSION.SDK_INT})")

        // 1. Try ConnectivityManager.startTethering (API 24+)
        if (tryConnectivityManager(context, enabled)) return true

        // 2. Try TetheringManager (Android 11+ hidden API)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (tryTetheringManager(context, enabled)) return true
        }

        // 3. Try Legacy WifiManager.setWifiApEnabled (Android < 8)
        if (tryLegacyWifiManager(context, enabled)) return true

        Log.w(TAG, "[HotspotManager] All hotspot enable attempts failed.")
        return false
    }

    /**
     * Uses ConnectivityManager.startTethering/stopTethering via reflection.
     * For startTethering, generates a real OnStartTetheringCallback subclass using dexmaker.
     */
    private fun tryConnectivityManager(context: Context, enabled: Boolean): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (!enabled) {
                // stopTethering is simpler — no callback needed
                val stopMethod = cm.javaClass.methods.find { it.name == "stopTethering" }
                if (stopMethod != null) {
                    stopMethod.isAccessible = true
                    stopMethod.invoke(cm, 0)
                    Log.d(TAG, "[HotspotManager] CM.stopTethering invoked")
                    return true
                }
                return false
            }

            // Find startTethering method
            val startMethod = cm.javaClass.methods.find {
                it.name == "startTethering" && it.parameterTypes.size >= 4
            } ?: return false

            startMethod.isAccessible = true

            // Generate a real callback instance using dexmaker
            val callbackInst = createTetheringCallback(context)
            if (callbackInst == null) {
                Log.w(TAG, "[HotspotManager] Failed to create TetheringCallback, trying with null")
            }

            val handler = Handler(Looper.getMainLooper())

            return when (startMethod.parameterTypes.size) {
                4 -> {
                    // startTethering(int type, boolean showUi, callback, handler)
                    startMethod.invoke(cm, 0, false, callbackInst, handler)
                    Log.d(TAG, "[HotspotManager] CM.startTethering(4) invoked successfully")
                    true
                }
                5 -> {
                    // startTethering(int type, boolean showUi, callback, handler, String pkg)
                    startMethod.invoke(cm, 0, false, callbackInst, handler, context.packageName)
                    Log.d(TAG, "[HotspotManager] CM.startTethering(5) invoked successfully")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "[HotspotManager] ConnectivityManager path failed: ${e.message}", e)
            return false
        }
    }

    /**
     * Creates a real subclass of OnStartTetheringCallback using dexmaker.
     *
     * This is the key difference from the old approach:
     * - Old: java.lang.reflect.Proxy (only works for interfaces) or null → fails
     * - New: dexmaker generates a real class extending the abstract callback → works
     *
     */
    @Suppress("UNCHECKED_CAST")
    private fun createTetheringCallback(context: Context): Any? {
        try {
            // Return cached instance if the class was already generated
            cachedCallbackClass?.let { cls ->
                return cls.getDeclaredConstructor().newInstance().also {
                    Log.d(TAG, "[HotspotManager] Using cached TetheringCallback class")
                }
            }

            val parentClass = Class.forName(CALLBACK_CLASS) ?: return null

            // Use DexMaker to generate a subclass
            val dexMaker = DexMaker()

            // Kotlin struggles with Java's static generic TypeId.get<T>(String),
            // so we call it via reflection to bypass type inference issues.
            val getByName: Method = TypeId::class.java.getDeclaredMethod("get", String::class.java)
            val getByClass: Method = TypeId::class.java.getDeclaredMethod("get", Class::class.java)

            @Suppress("UNCHECKED_CAST")
            val generatedType = getByName.invoke(null, "LTetheringCallback;") as TypeId<Any>
            @Suppress("UNCHECKED_CAST")
            val parentType = getByClass.invoke(null, parentClass) as TypeId<Any>

            // Declare the class as public, extending OnStartTetheringCallback
            dexMaker.declare(generatedType, "TetheringCallback.generated", java.lang.reflect.Modifier.PUBLIC, parentType)

            // Generate a no-arg constructor that calls super()
            @Suppress("UNCHECKED_CAST")
            val constructor = generatedType.getConstructor() as com.android.dx.MethodId<Any, Void>
            @Suppress("UNCHECKED_CAST")
            val parentConstructor = parentType.getConstructor() as com.android.dx.MethodId<Any, Void>
            val code = dexMaker.declare(constructor, java.lang.reflect.Modifier.PUBLIC)
            val thisRef = code.getThis(generatedType)
            code.invokeDirect(parentConstructor, null, thisRef)
            code.returnVoid()

            // Load the generated class
            val dexCache = context.codeCacheDir
            val classLoader = dexMaker.generateAndLoad(this.javaClass.classLoader, dexCache)
            val generatedClass = classLoader.loadClass("TetheringCallback")

            // Cache for future use
            cachedCallbackClass = generatedClass

            val instance = generatedClass.getDeclaredConstructor().newInstance()
            Log.d(TAG, "[HotspotManager] Generated TetheringCallback class via dexmaker")
            return instance
        } catch (e: Exception) {
            Log.e(TAG, "[HotspotManager] Dexmaker callback generation failed: ${e.message}", e)
            return null
        }
    }


    /**
     * Android 11+ TetheringManager path.
     */
    private fun tryTetheringManager(context: Context, enabled: Boolean): Boolean {
        try {
            val tm = context.getSystemService("tethering") ?: return false

            if (enabled) {
                val startMethod = tm.javaClass.methods.find {
                    it.name == "startTethering" && it.parameterTypes.size == 3
                } ?: return false

                // startTethering(int type, Executor executor, StartTetheringCallback callback)
                startMethod.invoke(tm, 0, context.mainExecutor, null)
                Log.d(TAG, "[HotspotManager] TM.startTethering invoked")
                return true
            } else {
                val stopMethod = tm.javaClass.methods.find { it.name == "stopTethering" }
                if (stopMethod != null) {
                    stopMethod.invoke(tm, 0)
                    Log.d(TAG, "[HotspotManager] TM.stopTethering invoked")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "[HotspotManager] TetheringManager path failed: ${e.message}")
        }
        return false
    }

    /**
     * Legacy path for Android < 8 using WifiManager.setWifiApEnabled.
     */
    private fun tryLegacyWifiManager(context: Context, enabled: Boolean): Boolean {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wm.javaClass.getMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            val result = method.invoke(wm, null, enabled) as Boolean
            if (result) {
                Log.d(TAG, "[HotspotManager] Legacy setWifiApEnabled successful")
                return true
            }
        } catch (_: Exception) {}
        return false
    }
}
