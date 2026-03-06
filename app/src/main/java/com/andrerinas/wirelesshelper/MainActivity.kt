package com.andrerinas.wirelesshelper

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    companion object {
        const val MODE_NSD = 0
        const val MODE_HOTSPOT_PHONE = 1
        const val MODE_PASSIVE = 2
        const val MODE_WIFI_DIRECT = 3
    }

    private lateinit var btnToggleService: Button
    
    // Connection Mode
    private lateinit var layoutConnectionMode: View
    private lateinit var tvConnectionModeValue: TextView

    // Auto Start
    private lateinit var layoutAutoStart: View
    private lateinit var tvAutoStartValue: TextView

    // Conditional Options
    private lateinit var layoutBluetoothDevice: View
    private lateinit var tvBluetoothDeviceValue: TextView

    private lateinit var layoutWifiNetwork: View
    private lateinit var tvWifiNetworkValue: TextView

    private lateinit var layoutWifiDirectName: View
    private lateinit var tvWifiDirectNameValue: TextView

    private lateinit var layoutLanguage: View
    private lateinit var tvLanguageValue: TextView

    private lateinit var tvVersionValue: TextView
    private lateinit var layoutAbout: View

    private var isServiceRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var pulseAnimator: ObjectAnimator? = null
    
    private val statusPoller = object : Runnable {
        override fun run() {
            val running = WirelessHelperService.isRunning
            val connected = WirelessHelperService.isConnected
            updateButtonState(running, connected)
            handler.postDelayed(this, 1000)
        }
    }

    private fun isMyServiceRunning(): Boolean {
        return WirelessHelperService.isRunning
    }

    private val connectionModes by lazy {
        arrayOf(
            getString(R.string.mode_nsd),
            getString(R.string.mode_hotspot_phone),
            getString(R.string.mode_passive),
            getString(R.string.mode_wifi_direct)
        )
    }

    private val autoStartModes by lazy {
        arrayOf(
            getString(R.string.auto_start_no),
            getString(R.string.auto_start_bt),
            getString(R.string.auto_start_wifi)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_WirelessHelper)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_main)

        initializeViews()
        setupListeners()
        restoreState()
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, systemBars.bottom)
                insets
            }
        }
    }

    private fun initializeViews() {
        btnToggleService = findViewById(R.id.btnToggleService)
        
        layoutConnectionMode = findViewById(R.id.layoutConnectionMode)
        tvConnectionModeValue = findViewById(R.id.tvConnectionModeValue)

        layoutAutoStart = findViewById(R.id.layoutAutoStart)
        tvAutoStartValue = findViewById(R.id.tvAutoStartValue)

        layoutBluetoothDevice = findViewById(R.id.layoutBluetoothDevice)
        tvBluetoothDeviceValue = findViewById(R.id.tvBluetoothDeviceValue)

        layoutWifiNetwork = findViewById(R.id.layoutWifiNetwork)
        tvWifiNetworkValue = findViewById(R.id.tvWifiNetworkValue)

        layoutWifiDirectName = findViewById(R.id.layoutWifiDirectName)
        tvWifiDirectNameValue = findViewById(R.id.tvWifiDirectNameValue)

        layoutLanguage = findViewById(R.id.layoutLanguage)
        tvLanguageValue = findViewById(R.id.tvLanguageValue)

        tvVersionValue = findViewById(R.id.tvVersionValue)
        layoutAbout = findViewById(R.id.layoutAbout)
        
        tvVersionValue.text = BuildConfig.VERSION_NAME
    }

    private fun setupListeners() {
        layoutAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
                .setTitle(R.string.about)
//                .setMessage("Wireless Helper is a trigger app for Headunit Revived.\n\nDeveloped by André Rinas\n© 2026")
                .setMessage(getString(R.string.about_dialog_body))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        layoutLanguage.setOnClickListener {
            showLanguageSelector()
        }

        btnToggleService.setOnClickListener {
            if (isServiceRunning) stopLauncherService() else checkPermissionsAndStart()
        }

        layoutConnectionMode.setOnClickListener {
            val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
            val currentMode = prefs.getInt("connection_mode", 0)
            
            MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
                .setTitle(R.string.connection_mode_label)
                .setSingleChoiceItems(connectionModes, currentMode) { dialog, which ->
                    prefs.edit { putInt("connection_mode", which) }
                    tvConnectionModeValue.text = connectionModes[which]
                    updateModeSpecificUI(which)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        layoutAutoStart.setOnClickListener {
            val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
            val currentMode = prefs.getInt("auto_start_mode", 0)

            MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
                .setTitle(R.string.auto_start_label)
                .setSingleChoiceItems(autoStartModes, currentMode) { dialog, which ->
                    prefs.edit { putInt("auto_start_mode", which) }
                    updateAutoStartUI(which)
                    
                    if (which == 2) {
                        WifiJobService.schedule(this@MainActivity)
                    } else {
                        WifiJobService.cancel(this@MainActivity)
                    }
                    
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        layoutBluetoothDevice.setOnClickListener {
            showBluetoothDeviceSelector()
        }

        layoutWifiNetwork.setOnClickListener {
            showWifiSelector()
        }

        layoutWifiDirectName.setOnClickListener {
            showWifiDirectNameSelector()
        }
    }

    private val languageOptions by lazy {
        arrayOf(
            getString(R.string.language_system_default),
            getString(R.string.language_english),
            getString(R.string.language_german),
            getString(R.string.language_russian),
            getString(R.string.language_portuguese),
            getString(R.string.language_arabic)
        )
    }

    private val languageTags = arrayOf("", "en", "de", "ru", "pt-BR", "ar")

    private fun showLanguageSelector() {
        val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        val currentTag = prefs.getString("app_language", "") ?: ""
        val currentIndex = languageTags.indexOf(currentTag).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.language_label)
            .setSingleChoiceItems(languageOptions, currentIndex) { dialog, which ->
                val tag = languageTags[which]
                prefs.edit { putString("app_language", tag) }
                applyLanguage(tag)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyLanguage(tag: String) {
        val localeList = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun showWifiSelector() {
        val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        val savedSsid = prefs.getString("auto_start_wifi_ssid", "")

        @Suppress("DEPRECATION")
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        val ssid = wifiManager.connectionInfo.ssid
        val currentSsid = ssid?.replace("\"", "") ?: ""
        
        val displaySsid = if (currentSsid.isNotEmpty() && currentSsid != "<unknown ssid>") currentSsid else savedSsid

        val input = android.widget.EditText(this).apply {
            setHint(R.string.wifi_ssid_hint)
            setText(displaySsid)
            setTextColor(ContextCompat.getColor(context, R.color.text_title))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_subtitle))
        }

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(64, 24, 64, 24)
        container.addView(input, params)

        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.wifi_ssid_dialog_title)
            .setMessage(R.string.wifi_ssid_dialog_msg)
            .setView(container)
            .setPositiveButton(R.string.wifi_ssid_save) { _, _ ->
                val newSsid = input.text.toString().trim()
                if (newSsid.isNotEmpty()) {
                    prefs.edit { putString("auto_start_wifi_ssid", newSsid) }
                    tvWifiNetworkValue.text = newSsid
                    WifiJobService.schedule(this@MainActivity)
                    
                    if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        MaterialAlertDialogBuilder(this@MainActivity, R.style.DarkAlertDialog)
                            .setTitle(R.string.wifi_background_location_title)
                            .setMessage(R.string.wifi_background_location_msg)
                            .setPositiveButton(R.string.wifi_background_location_button) { _, _ ->
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:$packageName")
                                }
                                startActivity(intent)
                            }
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.auto_start_linked, newSsid), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton(R.string.wifi_ssid_permissions_needed) { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 102)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWifiDirectNameSelector() {
        val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("wifi_direct_target_name", "")

        val input = android.widget.EditText(this).apply {
            setHint(R.string.wifi_direct_name_hint)
            setText(savedName)
            setTextColor(ContextCompat.getColor(context, R.color.text_title))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_subtitle))
        }

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(64, 24, 64, 24)
        container.addView(input, params)

        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.wifi_direct_name_dialog_title)
            .setMessage(R.string.wifi_direct_name_dialog_msg)
            .setView(container)
            .setPositiveButton(R.string.wifi_ssid_save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit { putString("wifi_direct_target_name", newName) }
                    tvWifiDirectNameValue.text = newName
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBluetoothDeviceSelector() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 101)
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = bluetoothManager.adapter
        val bondedDevices = adapter.bondedDevices.toList()

        if (bondedDevices.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_paired_devices), Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = bondedDevices.map { it.name ?: "Unknown Device" }.toTypedArray()
        
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.select_bt_device)
            .setItems(deviceNames) { _, which ->
                val device = bondedDevices[which]
                val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
                prefs.edit { 
                    putString("auto_start_bt_mac", device.address)
                    putString("auto_start_bt_name", device.name)
                }
                tvBluetoothDeviceValue.text = device.name
                Toast.makeText(this, getString(R.string.auto_start_linked, device.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restoreState() {
        val prefs = getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE)
        
        val connMode = prefs.getInt("connection_mode", 0)
        tvConnectionModeValue.text = connectionModes.getOrElse(connMode) { connectionModes[0] }
        updateModeSpecificUI(connMode)

        val autoMode = prefs.getInt("auto_start_mode", 0)
        updateAutoStartUI(autoMode)
        
        tvBluetoothDeviceValue.text = prefs.getString("auto_start_bt_name", getString(R.string.not_set))
        tvWifiNetworkValue.text = prefs.getString("auto_start_wifi_ssid", getString(R.string.not_set))
        tvWifiDirectNameValue.text = prefs.getString("wifi_direct_target_name", getString(R.string.not_set))

        // Language
        val langTag = prefs.getString("app_language", "") ?: ""
        val langIndex = languageTags.indexOf(langTag).coerceAtLeast(0)
        tvLanguageValue.text = languageOptions[langIndex]

        updateButtonState(WirelessHelperService.isRunning, WirelessHelperService.isConnected)
    }

    private fun updateModeSpecificUI(mode: Int) {
        if (mode == MODE_WIFI_DIRECT) {
            layoutWifiDirectName.visibility = View.VISIBLE
        } else {
            layoutWifiDirectName.visibility = View.GONE
        }
    }

    private fun updateAutoStartUI(mode: Int) {
        tvAutoStartValue.text = autoStartModes.getOrElse(mode) { autoStartModes[0] }
        
        when (mode) {
            0 -> { // No
                layoutBluetoothDevice.visibility = View.GONE
                layoutWifiNetwork.visibility = View.GONE
            }
            1 -> { // Bluetooth
                layoutBluetoothDevice.visibility = View.VISIBLE
                layoutWifiNetwork.visibility = View.GONE
            }
            2 -> { // WiFi
                layoutBluetoothDevice.visibility = View.GONE
                layoutWifiNetwork.visibility = View.VISIBLE
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        // Needed for WiFi SSID access
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        } else {
            startLauncherService()
        }
    }

    private fun startLauncherService() {
        val intent = Intent(this, WirelessHelperService::class.java).apply {
            action = WirelessHelperService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateButtonState(true, false)
    }

    private fun stopLauncherService() {
        val intent = Intent(this, WirelessHelperService::class.java).apply {
            action = WirelessHelperService.ACTION_STOP
        }
        startService(intent)
        updateButtonState(false, false)
    }

    private fun updateButtonState(running: Boolean, connected: Boolean = false) {
        isServiceRunning = running
        
        val colorTeal = ContextCompat.getColor(this, R.color.brand_teal)
        val colorGreen = ContextCompat.getColor(this, android.R.color.holo_green_dark)
        
        if (connected) {
            btnToggleService.text = getString(R.string.connected)
            btnToggleService.background.setTint(colorGreen)
            startPulseAnimation(1500) // Slow pulse
        } else if (running) {
            btnToggleService.text = getString(R.string.stop_service)
            btnToggleService.background.setTint(colorTeal)
            startPulseAnimation(800) // Fast pulse
        } else {
            btnToggleService.text = getString(R.string.start_service)
            btnToggleService.background.setTint(colorTeal)
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation(duration: Long) {
        if (pulseAnimator != null && pulseAnimator!!.isRunning && pulseAnimator!!.duration == duration) {
            return
        }
        stopPulseAnimation()
        
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            btnToggleService,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f, 1.0f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f, 1.0f)
        ).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        btnToggleService.scaleX = 1.0f
        btnToggleService.scaleY = 1.0f
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusPoller)
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
                    .setTitle(R.string.battery_opt_title)
                    .setMessage(R.string.battery_opt_msg)
                    .setPositiveButton(R.string.wifi_background_location_button) { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusPoller)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        
        if (data.scheme == "wirelesshelper") {
            when (data.host) {
                "start" -> {
                    // Handle optional mode parameter
                    val modeParam = data.getQueryParameter("mode")
                    if (!modeParam.isNullOrEmpty()) {
                        val modeIdx = when (modeParam.lowercase()) {
                            "nsd" -> 0
                            "phone-hotspot" -> 1
                            "tablet-hotspot" -> 2
                            "wifi-direct" -> 3
                            else -> -1
                        }
                        if (modeIdx != -1) {
                            getSharedPreferences("WirelessHelperPrefs", Context.MODE_PRIVATE).edit {
                                putInt("connection_mode", modeIdx)
                            }
                            tvConnectionModeValue.text = connectionModes[modeIdx]
                        }
                    }
                    if (!isServiceRunning) checkPermissionsAndStart()
                }
                "stop" -> {
                    if (isServiceRunning) stopLauncherService()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (requestCode == 100) startLauncherService()
            if (requestCode == 101) showBluetoothDeviceSelector()
            if (requestCode == 102) showWifiSelector()
        }
    }
}
