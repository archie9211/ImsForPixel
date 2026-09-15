# IMS for Pixel

> Enable VoLTE, VoNR (5G Calling), and Wi-Fi Calling on Google Pixel devices — **no root required**.

[![Platform](https://img.shields.io/badge/platform-Android%2010--17-brightgreen)](https://developer.android.com)
[![API](https://img.shields.io/badge/minSdk-28-blue)](https://developer.android.com/about/versions/10)
[![License](https://img.shields.io/badge/license-MIT-orange)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.1.0-informational)](app/build.gradle)
[![Tested](https://img.shields.io/badge/tested-Android%2017-success)](https://developer.android.com)

---

## Overview

**IMS for Pixel** is a rootless carrier configuration override tool for Google Pixel (and AOSP-compatible) devices. It enables VoLTE, VoNR (5G Calling), and Wi-Fi Calling on carriers that ship incomplete or locked carrier bundles — and works even on **Android 17**, which further restricts privileged telephony APIs for third-party apps.

### Inspired by Shizuku & IMS

This project draws on the ideas of two great open-source projects:

- **[Shizuku](https://github.com/RikkaApps/Shizuku)** pioneered the pattern of using Android's built-in Wireless Debugging to give user apps a temporary shell permission identity — without root. *IMS for Pixel* takes that same concept and applies it in a fully self-contained way: no Shizuku app needs to be installed. The pairing, connection, and `am instrument` invocation are handled entirely within this app using the [Kadb](https://github.com/flyfishxu/Kadb) library.

- **[IMS Patch / carrier config tools](https://github.com/imsforpixel)** demonstrated which `CarrierConfigManager` keys unlock VoLTE/VoNR/VoWiFi on Pixel hardware. *IMS for Pixel* combines those config keys with the shell-permission approach above to produce a single, simple, self-contained solution.

By merging these two ideas, **IMS for Pixel** is simpler and more convenient than either approach alone:

| | Shizuku-based tools | IMS for Pixel |
|---|---|---|
| Requires Shizuku app | ✅ Yes | ❌ No |
| Requires root | ❌ No | ❌ No |
| Requires PC / ADB cable | ❌ No | ❌ No |
| Works on Android 15 / 16 / 17 | ⚠️ Varies | ✅ Yes |
| Self-contained single APK | ❌ No | ✅ Yes |
| Shows real IMS registration result | ⚠️ Varies | ✅ Yes (notification) |

---

## Features

- ✅ **VoLTE** — Enable HD voice calls over LTE
- ✅ **VoNR** — Enable 5G calling (Voice over New Radio)
- ✅ **Video Calling (VT / ViLTE / ViNR)** — Carrier video calling over LTE and 5G
- ✅ **Wi-Fi Calling (VoWiFi)** — Make calls over Wi-Fi
- ✅ **Wi-Fi Calling Roaming** — Keep Wi-Fi calling active while abroad
- ✅ **Multi-Language Support** — English, Simplified Chinese, and in-app language switcher
- ✅ **Supplementary Services (UT)** — Call forwarding, call waiting, etc.
- ✅ **Dual-SIM support** — Independent per-slot configuration
- ✅ **One-tap restore** — Clear all overrides and return to carrier defaults
- ✅ **Live IMS status** — Background polling every 3 s
- ✅ **Notification result** — Real per-slot IMS registration status shown in notification bar after activation
- ✅ **Tested on Android 10 – 17**
- 🚫 **No root** required
- 🚫 **No Shizuku** or any other privilege manager required
- 🚫 **No internet** — all ADB traffic is local loopback only

---

## How It Works

```
┌─────────────────────┐      loopback ADB       ┌──────────────────────────┐
│   IMS for Pixel UI  │ ───────────────────────► │  BrokerInstrumentation   │
│  (Jetpack Compose)  │   am instrument -w       │  (Instrumentation runner)│
└─────────────────────┘                          └──────────┬───────────────┘
                                                            │ adoptShellPermissionIdentity()
                                                            ▼
                                                  CarrierConfigManager
                                                  .overrideConfig(subId, bundle)
                                                            │
                                                            ▼
                                                  TelephonyManager.resetIms()
                                                            │
                                                            ▼
                                                  Poll IMS registration (≤ 30 s)
                                                            │
                                                            ▼
                                                  Post result notification
```

1. The app self-connects to the device's **Wireless Debugging** port via loopback (`127.0.0.1`).
2. It launches `BrokerInstrumentation` via `am instrument`, which runs under the `shell` permission identity — the same mechanism Shizuku uses, but self-contained.
3. `BrokerInstrumentation` calls `CarrierConfigManager.overrideConfig` with your chosen settings, resets IMS, and polls registration for up to 30 seconds.
4. On completion, a **notification bar** message reports the real per-slot IMS registration result.
5. A background `ImsQueryTool` process refreshes the in-app IMS status badges every 3 seconds.

---

## Requirements

| Requirement | Details |
|---|---|
| Android version | Android 10 (API 28) or higher |
| Tested up to | **Android 17** ✅ |
| Wi-Fi | Must be connected to a Wi-Fi network |
| Wireless Debugging | Must be enabled in Developer Options |
| Root / Shizuku | ❌ Not required |

---

## Quick Start

### 1. Enable Developer Options & Wireless Debugging

1. Go to **Settings → About phone** and tap **Build number** 7 times.
2. Go to **Settings → System → Developer options**.
3. Enable **Wireless debugging**.

### 2. Install the App

Download the latest APK from [Releases](../../releases) and install it, or build from source (see [Building](#building)).

### 3. Pair & Activate

1. Open **IMS for Pixel**.
2. The app auto-discovers the Wireless Debugging port via mDNS. When the pairing notification appears, enter the **6-digit pairing code** shown in the Wireless Debugging settings.
3. Configure per-slot toggles as desired (VoLTE, VoNR, VoWiFi, etc.).
4. Tap **一键激活** (One-tap Activate).
5. Wait up to ~30 seconds. A system notification confirms success with per-slot IMS registration status.

---

## Building

### Prerequisites

- Android Studio (Ladybug or later recommended)
- JDK 17 (bundled with Android Studio)
- Android SDK API 36

### Build Debug

```bash
git clone https://github.com/svenuks/ImsForPixel.git
cd ImsForPixel
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Build Release

```bash
./gradlew assembleRelease
```

> **macOS note:** If you don't have a system JDK, use Android Studio's bundled JRE:
> ```bash
> JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
> ```

---

## Project Structure

```
app/src/main/java/com/svenuks/imsforpixel/
├── MainActivity.kt          # Jetpack Compose UI + mDNS discovery + background IMS polling
├── BrokerInstrumentation.kt # Shell-identity runner: overrides carrier config, polls IMS, posts notification
└── ImsQueryTool.kt          # Lightweight app_process entry point for background IMS status queries
```

| File | Role |
|---|---|
| `MainActivity.kt` | Full UI (Material 3 / Compose), mDNS service discovery, Kadb ADB client, background IMS refresh |
| `BrokerInstrumentation.kt` | Privileged patch runner — overrides carrier config, resets IMS, polls registration, posts notification |
| `ImsQueryTool.kt` | Minimal `app_process` entry point — queries IMS state without UiAutomation overhead |

---

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Required by Kadb for local loopback ADB socket |
| `ACCESS_NETWORK_STATE` | Check Wi-Fi connectivity before ADB pairing |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS (NSD) discovery of the Wireless Debugging port |
| `POST_NOTIFICATIONS` | Show activation result and pairing code in notification bar |

> No data is ever sent to any external server. All network traffic is loopback `127.0.0.1` only.

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| [androidx.core:core-ktx](https://developer.android.com/jetpack/androidx/releases/core) | 1.13.1 | Kotlin extensions |
| [androidx.activity:activity-compose](https://developer.android.com/jetpack/androidx/releases/activity) | 1.9.1 | Compose activity |
| [androidx.compose BOM](https://developer.android.com/jetpack/compose/bom) | 2024.06.00 | Compose UI, Material 3 |
| [hiddenapibypass](https://github.com/LSPosed/HiddenApiBypass) | 4.3 | Restricted telephony API access on Android 9+ |
| [kadb](https://github.com/flyfishxu/Kadb) | 2.1.1 | Pure-Kotlin ADB over Wi-Fi |
| [adblib](https://github.com/tananaev/adblib) | 1.3 | ADB protocol support |

---

## FAQ

**Q: Does this survive a reboot?**  
A: No. `CarrierConfigManager.overrideConfig` overrides are in-memory and reset on reboot. Tap **一键激活** again after each reboot.

**Q: Will this break anything?**  
A: Tap **一键恢复** (One-tap Restore) at any time to clear all overrides and return to carrier defaults.

**Q: My carrier requires provisioning — will this still work?**  
A: The app sets `carrier_volte_provisioning_required_bool = false`. This works on most carriers. Carriers with server-side IMS provisioning checks may still fail independently of this app.

**Q: Dual SIM support?**  
A: Yes. Each active SIM slot is configured independently, with its own IMS badge in the UI.

**Q: Why not just use Shizuku?**  
A: Shizuku is great, but requires a separate app to be installed and running. *IMS for Pixel* is entirely self-contained — one APK, no dependencies on other apps. It uses the same underlying mechanism (Wireless Debugging + shell permission identity) but handles everything internally.

**Q: Is the Wireless Debugging port exposed to the network?**  
A: No. The ADB connection is made over `127.0.0.1` (loopback) only. Android binds the Wireless Debugging port to `localhost` and it is not accessible from the local network.

---

## Security & Auditability

- **No external network requests.** No telemetry, analytics, or data collection of any kind.
- **Loopback-only ADB.** Every socket connection goes to `127.0.0.1` (the device itself).
- **Three source files.** The entire app is ~1200 lines of Kotlin across three files — easy to audit end-to-end.
- **Standard APIs only.** Hidden APIs accessed via the established `HiddenApiBypass` library and Java reflection — no native code or binary blobs.

---

## Contributing

Pull requests and issues are welcome. Please open an issue before submitting large changes.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m 'Add my feature'`
4. Push: `git push origin feature/my-feature`
5. Open a Pull Request

---

## License

```
MIT License

Copyright (c) 2025 Sven

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Acknowledgements

Special thanks to the following projects for the ideas and inspiration that made *IMS for Pixel* possible:

- **[Shizuku](https://github.com/RikkaApps/Shizuku)** by RikkaW — for pioneering the "Wireless Debugging as a privilege bridge" pattern. *IMS for Pixel* borrows this core idea and packages it in a fully self-contained way.
- **[LSPosed/HiddenApiBypass](https://github.com/LSPosed/HiddenApiBypass)** — for making restricted telephony API access practical on modern Android.
- **[flyfishxu/Kadb](https://github.com/flyfishxu/Kadb)** — for the pure-Kotlin ADB over Wi-Fi implementation.
- The various IMS/carrier-config research threads in the Android modding community for documenting which `CarrierConfigManager` keys actually matter.
