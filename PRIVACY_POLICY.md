# Privacy Policy — NOX - Game Space

**Application Name:** NOX - Game Space  
**Package Identifier:** `com.nox.gamespace`  
**Version:** 1.0.0  
**Last Updated:** November 2026  
**Privacy Contact:** [kyrostudios23@gmail.com](mailto:kyrostudios23@gmail.com)

---

## 1. Introduction & Overview
**NOX - Game Space** ("the Application", "we", "our") is a dedicated Android gaming performance and hardware optimization hub, offering per-game hardware boosting, safe display resolution downscaling, GPU mipmap/LOD lightweight texture tuning, multi-DNS latency benchmarking, and a live translucent in-game performance overlay (Floating HUD). We are committed to complete transparency and protecting your digital privacy.

## 2. Zero Personal Data Harvesting (100% On-Device Processing)
- **NOX - Game Space DOES NOT collect, transmit, store on external servers, or sell any of your personal identifiable information (PII).**
- We do **not** require account registration, logins, passwords, phone numbers, or email addresses to use the Application.
- All user configurations—including per-game FPS limits, resolution presets, texture detail profiles, NOX Energy cells, and playtime telemetry—are stored **100% locally on your device** inside the Application's private sandbox storage (`Room SQLite` & `SharedPreferences`).

## 3. Android System Permissions & Usage Disclosure
To deliver hardware-level gaming optimizations, the Application requests specific Android permissions. Every permission is utilized strictly for its declared, user-initiated functionality:

| Permission | Purpose & On-Device Scope |
| :--- | :--- |
| **`PACKAGE_USAGE_STATS` (Usage Access)** | Used strictly on-device to detect the active game running in the foreground and calculate local game session playtime. No app usage history is transmitted externally. |
| **`SYSTEM_ALERT_WINDOW` (Display Over Other Apps)** | Used exclusively to render the real-time translucent in-game Floating HUD (FPS, CPU, GPU, and RAM telemetry) when enabled by the user. |
| **`POST_NOTIFICATIONS` (Notifications)** | Used on Android 13+ to display the interactive Wireless ADB pairing notification and active Floating HUD service status. |
| **`INTERNET` & `ACCESS_NETWORK_STATE`** | Used for local Wi-Fi mDNS discovery (`_adb-tls-pairing` / `_adb-tls-connect`), local DNS latency ping benchmarking, and loading Google AdMob advertisements. |
| **Shizuku / Wireless ADB / Root Access** | Used solely to execute user-selected Android gaming optimization commands (`cmd game`, `wm size`, `wm density`, and ANGLE/GPU properties). The Application contains no remote shell or unauthorized background execution. |

## 4. Third-Party Services & Advertising (Google AdMob)
- The Application integrates the official **Google Mobile Ads SDK (AdMob)** to display non-intrusive banner ads, app-open ads, and optional rewarded interstitial ads (to top up **NOX Energy Cells**).
- Google AdMob may use your device's Advertising ID and coarse network metadata in accordance with Google's Privacy Policy to serve advertisements.
- You can review Google's Privacy & Terms here: [https://policies.google.com/technologies/ads](https://policies.google.com/technologies/ads)

## 5. Data Retention & User Control
You retain 100% control over all local data stored by the Application:
- You can clear all saved configurations, databases, and keys at any time via **Android Settings > Apps > NOX - Game Space > Storage > Clear Data**.
- Uninstalling the Application permanently deletes all local databases, preferences, and paired ADB keys from your device.

## 6. Security Integrity & Anti-Tamper
The Application includes local cryptographic checksum validation (`SHA-256`) to protect the integrity of the on-device NOX Energy Vault against unauthorized external file or memory manipulation. All verification runs strictly offline on your device.

## 7. Changes to This Privacy Policy
We may update this Privacy Policy from time to time to reflect new features or regulatory requirements. Any updates will be published in this repository and inside the Application's **Settings > Privacy Policy & Compliance** section.

## 8. Privacy Contact
For any questions, feedback, or privacy inquiries regarding **NOX - Game Space**, please contact us exclusively at:

- **Email:** [kyrostudios23@gmail.com](mailto:kyrostudios23@gmail.com)
