# NOX - Game Space (`com.nox.gamespace`)

**NOX - Game Space** is a modern Material 3 Android Gaming Performance Hub engineered for competitive mobile gamers. Supporting **Android 9+ (API 24+)**, it provides per-game hardware boosting, safe display resolution scaling with auto-revert protection, GPU mipmap/LOD lightweight texture tuning, multi-DNS latency benchmarking, and a live translucent in-game FPS, CPU, and GPU telemetry overlay.

- **Package Name:** `com.nox.gamespace`
- **Privacy Policy Document:** [PRIVACY_POLICY.md](./PRIVACY_POLICY.md)
- **Privacy Contact:** [kyrostudios23@gmail.com](mailto:kyrostudios23@gmail.com)

---

## Key Features
- **Multi-Engine Execution Backend:** Supports **Shizuku IPC**, **Native Wireless ADB (`libadb.so` + Persistent Daemon on port 38555)** with fixed `NOX` device identity, and **Root (SU)**.
- **Game Tune & Hardware Booster:** Per-game FPS target selection, Android `cmd game` downscaling, and memory optimization.
- **Resolution Changer:** Safe display resolution downscaling and automatic DPI synchronization with a 10-second auto-revert watchdog.
- **Lightweight Texture Tuner:** GPU mipmap/LOD adjustment and ANGLE Vulkan driver configuration (Single Game or All Added Games).
- **Network Optimizer:** Multi-DNS real-time latency benchmark and Ping monitor.
- **In-Game Floating HUD:** Translucent overlay displaying live FPS, CPU temperature, and RAM usage.
- **NOX Energy Cell System:** Cryptographically verified on-device energy vault (`SHA-256` anti-tamper protection).

---

## Privacy Policy & Compliance Disclosure

**Application Name:** NOX - Game Space  
**Package Identifier:** `com.nox.gamespace`  
**Last Updated:** November 2026  
**Privacy Contact:** `kyrostudios23@gmail.com`

### 1. Zero Personal Data Harvesting (100% On-Device Processing)
- **NOX - Game Space DOES NOT collect, transmit, store on external servers, or sell any of your personal identifiable information (PII).**
- No account registration, login, phone number, or email address is required to use the Application.
- All configurations—including per-game FPS limits, resolution presets, texture detail profiles, NOX Energy cells, and playtime telemetry—are stored **100% locally on your device** inside the Application's private sandbox storage (`Room SQLite` & `SharedPreferences`).

### 2. Android System Permissions & Usage Disclosure
Every permission requested by the Application is utilized strictly for its declared, user-initiated functionality:
- **`PACKAGE_USAGE_STATS` (Usage Access):** Used strictly on-device to detect the active foreground game and calculate local game session playtime. No usage logs are uploaded or shared.
- **`SYSTEM_ALERT_WINDOW` (Display Over Other Apps):** Used exclusively to render the real-time translucent in-game Floating HUD (FPS, CPU, GPU, and RAM telemetry).
- **`POST_NOTIFICATIONS` (Notifications):** Used on Android 13+ to display interactive Wireless ADB pairing notifications and active Floating HUD service status.
- **`INTERNET` & `ACCESS_NETWORK_STATE`:** Used for local Wi-Fi mDNS discovery (`_adb-tls-pairing` / `_adb-tls-connect`), local DNS latency ping benchmarking, and loading Google AdMob advertisements.
- **Shizuku / Wireless ADB / Root Access:** Used solely to execute user-selected Android gaming optimization commands (`cmd game`, `wm size`, `wm density`, and ANGLE/GPU properties).

### 3. Third-Party Services & Advertising (Google AdMob)
- The Application integrates the official **Google Mobile Ads SDK (AdMob)** to display banner ads, app-open ads, and optional rewarded interstitial ads (to top up **NOX Energy Cells**).
- Google AdMob may collect anonymous device identifiers (such as Android Advertising ID) in accordance with Google's Privacy Policy: [https://policies.google.com/technologies/ads](https://policies.google.com/technologies/ads)

### 4. Data Retention & User Control
- You can reset all saved configurations and local databases at any time via **Android Settings > Apps > NOX - Game Space > Storage > Clear Data**.
- Uninstalling the Application permanently deletes 100% of its local databases, preferences, and paired keys from your device.

### 5. Privacy Contact
For any questions, feedback, or privacy inquiries regarding **NOX - Game Space**, please contact us exclusively at:
- **Email:** [kyrostudios23@gmail.com](mailto:kyrostudios23@gmail.com)
