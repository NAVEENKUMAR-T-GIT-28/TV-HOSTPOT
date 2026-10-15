# TV Hotspot Guard

A small native Android app for viewing eligible ARP entries on an Android TV hotspot and manually invoking the TV's existing `XbhBlock` middleware.

## Features
- Connect to a configurable ADB TCP endpoint (defaults to `192.168.43.1:5555`).
- Read `/proc/net/arp` and list reachable `wlan1` clients.
- Manual Block / Unblock buttons with confirmation.
- Hard-coded protected MAC allowlist:
  - `4e:71:01:ea:b5:12` — I2403
  - `e8:b0:c5:24:5d:c9` — LAPTOP-V2CFLSTE
  - `32:b8:42:c0:6d:c9` — critical control phone
- No automatic blocking; protected devices have no action buttons.

## Requirements and limitations
- TV must permit legacy unauthenticated ADB TCP on the selected endpoint. This implementation does not support Android 11+ ADB pairing/authentication.
- The TV must already have `/data/local/tmp/classes.dex` containing the vendor `XbhBlock` class.
- The middleware action convention is based on the provided script: `1 = block`, `0 = unblock`. Verify on your TV before relying on it.
- The app only sees clients represented in the TV's ARP table for `wlan1`; this may be incomplete.
- Android app cannot guarantee the TV's vendor middleware will enforce the same MAC semantics as the old PowerShell script.

## Build APK
Open this folder in Android Studio (JDK 17+, Android SDK 35) and build `app` > `assembleDebug`, or use the included GitHub Actions workflow after pushing this project to a GitHub repository. The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Safety
This is a local network administration tool. Only use it on a TV/hotspot you own or are authorized to manage. Keep ADB off untrusted networks. The app explicitly refuses to show controls for the protected MAC addresses, and rechecks protection before dispatching middleware commands.
