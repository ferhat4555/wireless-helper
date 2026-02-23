<p align="center">
  <img src="app/src/main/res/ic_launcher-playstore.png" width="200" alt="Wireless Helper Logo">
</p>

# Wireless Helper

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

## Changelog
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