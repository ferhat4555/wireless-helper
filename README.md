<p align="center">
  <img src="app/src/main/res/ic_launcher-playstore.png" width="200" alt="Wireless Helper Logo">
</p>

# Wireless Helper

<a href='https://play.google.com/store/apps/details?id=com.andrerinas.wirelesshelper'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' width="200"/></a>

A lightweight launcher utility for **Headunit Revived**.

This app acts as a trigger to start Android Auto Wireless on your phone. It automatically detects your tablet running Headunit Revived via NSD (mDNS) or a passive TCP trigger, ensuring a seamless wireless connection experience.

## Features
- **Auto-Trigger:** Fires the Android Auto wireless intent as soon as the Headunit is detected.
- **NSD Support:** Finds your tablet in the same WiFi network automatically.
- **Passive Mode:** Waits for a trigger from the Headunit (ideal for Tablet-Hotspot setups).
- **Zero-Config:** No manual IP entry required.

## Auto-Start Setup
To ensure reliable background auto-start (Bluetooth/WiFi) and a stable connection:
1. **Disable Battery Optimization (CRITICAL):** Go to App Info -> Battery -> select **"Unrestricted"**. Since this app acts as a data proxy for Android Auto, it must not be put to sleep while driving.
2. **Permissions:** Grant Bluetooth and Location permissions when prompted.
3. **Paired Devices:** Select your car's Bluetooth device in the settings.

## Automation (Tasker / MacroDroid / ADB)
Wireless Helper supports remote control via Android Intents and App Shortcuts.

### URI Schemes
- **Start Search:** `wirelesshelper://start`
- **Start with Specific Mode:** `wirelesshelper://start?mode=<MODE_ID>`
- **Stop Search:** `wirelesshelper://stop`

### Supported Modes
| Mode ID | Description        |
| :--- |:-------------------|
| `nsd` | Shared Wi-Fi       |
| `phone-hotspot` | Phone Hotspot mode |
| `tablet-hotspot` | Tablet Hotspot     |
| `wifi-direct` | Wi-Fi Direct       |

### ADB Examples
```bash
# Start searching in Phone Hotspot mode
adb shell am start -a android.intent.action.VIEW -d "wirelesshelper://start?mode=phone-hotspot"

# Stop searching
adb shell am start -a android.intent.action.VIEW -d "wirelesshelper://stop"
```

## Changelog
### v.1.3.1
- Added: Spanish translation 🇪🇸 thanks to @tsabaia
- Fixed: Bug with Wi-Fi Popup on Hotspot mode

### v.1.3.0
- Added: Try to auto reconnect when bluetooth device still connected
- Added: Ignore bluetooth disconnection and keep service running
- Added: Byebye intent to Headunit for cleaner closes

### v.1.2.0
- Fixing start of Android Auto with new Version 16.4. The intent is gone and now a broadcast.
- Fixing Fatal Crash on Wifi-Direct closing

### v.1.1.0
- Enhancement for the Wifi-Direct Mode. Note: Since Android 10 this mode is very restrictive and it might now work for every modern phone. Just try it

### v.1.0.1
- Fixing offline mode

### v.1.0.0 - First ready version for Playstore!
- Added Deep Links and App Shortcuts for full automation (Samsung Modes & Routines support).
- Added Arabic translation, thanks to @A5H0
- Improved background activity launch and service lifecycle.

### v.0.5.0
- Added 1x1 Widget for Launcher
- Added Quick-Settings-Tile
- Changed Logo because of the new icons
- Merged new translations

### v.0.4.0
- Added experimental Auto-Start on Wi-Fi logic
- Fixed connection hangs and improved discovery logic.
- Notification now disappears correctly when the session ends or is closed from the headunit.
- Added support for system navigation bar insets (no more cut-off content).
- Added translation support, thanks to @saksonovdev with english, russian and german language

### v.0.3.0
- Complete Rebuild of the functions. Now using a proxy server to establish the connections
- Changed the UI a little bit

### v.0.2.0
- Added Auto Scan on Bluetooth Connection

### v.0.1.0
- First release