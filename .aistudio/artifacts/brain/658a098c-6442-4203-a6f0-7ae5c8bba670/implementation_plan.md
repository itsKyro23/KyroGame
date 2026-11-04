# Rencana Implementasi: AOT Game DEX Compiler (Tools) & Play Auto-Boost Settings (Home)

Dokumen ini merancang penambahan dua fitur nyata ke dalam **NOX - Game Space**:
1. **AOT Game DEX Compiler** di halaman **Tools** (dengan integrasi biaya **NOX Energy Cell** `15⚡` untuk 1 game / `30⚡` untuk semua game).
2. **Play Boost Settings Button & Dialog** di halaman **Home** (untuk mengatur tier **Auto-Boost** dan *checklist* tahapan optimasi nyata—termasuk penutupan paksa aplikasi latar belakang—yang otomatis dijalankan setiap kali tombol **Play** pada kartu game ditekan).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Biaya Energi AOT Game DEX Compiler**:
>   - **Single Game (`15⚡`)**: Mengompilasi 1 game yang dipilih (`speed-profile` atau `speed`).
>   - **All Added Games (`30⚡`)**: Mengompilasi seluruh game yang ada di daftar Home sekaligus.
>   - **Reset Compile (`0⚡` / Gratis)**: Mengembalikan status kompilasi paket ke standar.
> - **3 Tier Preset Auto-Boost (Saat Klik Play di Game Card)**:
>   1. **`ECO PULSE` (Light / Safe Boost)**: Bersih-bersih cache RAM ringan tanpa menutup aplikasi latar belakang.
>   2. **`VELOCITY CORE` (Balanced / Competitive)**: Optimasi RAM + Touch Sensitivity + SurfaceFlinger Zero-Lag + Anti Pop-Up Notifikasi.
>   3. **`NOX OVERDRIVE` (Extreme / Max Performance)**: Semua fitur di atas + **Force-Stop Aplikasi Latar Belakang (`am force-stop` & `am kill-all`)** + **Fixed Performance Mode (`cmd power`)** + **Phantom Process Bypass**.

---

## 1. Penjelasan Detail Fitur yang Ditambahkan

### A. Fitur 1: **AOT Game DEX Compiler** (Di Menu **Tools**)
- **Apa Fungsinya?**
  Menggunakan mesin kompilator internal Android (`dex2oat` via `cmd package compile`) untuk menerjemahkan kode DEX game menjadi **instruksi mesin ARM64 asli (Ahead-Of-Time / AOT)** sebelum game dibuka. Ini menghilangkan *stutter/patah-patah* saat efek *skill* baru keluar dan mempercepat *loading* masuk ke dalam *match*.
- **Pilihan Mode Kompilasi Nyata:**
  1. **`Speed-Profile (Fast & Recommended)`**: Mengompilasi jalur kode utama game secara cerdas (`cmd package compile -m speed-profile -f <package>`).
  2. **`Full Speed AOT (Maximum Zero-JIT)`**: Mengompilasi 100% seluruh metode di dalam game menjadi kode mesin (`cmd package compile -m speed -f <package>`).
- **Integrasi NOX Energy:**
  - Membutuhkan **`15⚡`** untuk 1 game, atau **`30⚡`** untuk semua game di Home.
  - Juga ditampilkan secara rapi di dalam dialog **NOX Energy Cell (`NoxEnergyTopUpDialog`)**.

### B. Fitur 2: **Play Boost Settings Button & Dialog** (Di Halaman **Home**)
- **Lokasi Tombol:**
  Terletak di baris judul **`MY GAMES`** pada halaman **Home** (di sebelah kanan badge jumlah game), berupa tombol kapsul **`Boost Settings`** yang menampilkan nama Tier aktif saat ini (`Eco Pulse` / `Velocity Core` / `Nox Overdrive`).
- **3 Pilihan Tier Auto-Boost (Otomatis Mengatur Checklist):**

| Nama Tier Boost | Level | Tahapan yang Dijalankan Saat Klik Tombol "Play" |
| :--- | :--- | :--- |
| **`ECO PULSE`** | **Ringan (Low)** | **Tahap 1–2:** Hanya membersihkan memori cache (`am trim-memory RUNNING_MODERATE` & `pm trim-caches`) dan menerapkan profil FPS game. Aplikasi chat/musik di latar belakang **tidak** ditutup. |
| **`VELOCITY CORE`** | **Menengah (Mid)** | **Tahap 1–4:** Mencakup *Eco Pulse* + **Zero-Lag SurfaceFlinger** (`disable_backpressure 1`) + **Ultra Touch Response** (`pointer_speed 7`) + **Anti Pop-Up Notifikasi** (`heads_up_notifications_enabled 0`). |
| **`NOX OVERDRIVE`** | **Ekstrem (High)** | **Tahap 1–6 (Maksimal):** Mencakup *Velocity Core* + **Force-Stop Background Apps** (menghentikan paksa aplikasi pihak ketiga yang berjalan di latar belakang dengan `am force-stop` & `am kill-all`) + **Fixed Performance Mode** (`cmd power set-fixed-performance-mode-enabled true` & ` max_phantom_processes` bypass). |

- **6 Checklist Kustomisasi Nyata (Bisa Dicentang/Diubah Manual):**
  Di dalam dialog, pengguna juga bisa mencentang atau mematikan setiap tahapan secara bebas:
  1. **RAM & Cache Trim** (`am trim-memory system RUNNING_CRITICAL` + `pm trim-caches 4G`)
  2. **Force-Stop Background Apps** (`am kill-all` + iterasi `am force-stop <pkg>` pada aplikasi non-esensial di latar belakang)
  3. **Fixed CPU/GPU Performance Lock** (`cmd power set-fixed-performance-mode-enabled true` + `max_phantom_processes 2147483647`)
  4. **SurfaceFlinger Zero-Lag Compositor** (`setprop debug.sf.disable_backpressure 1` + `setprop debug.sf.latch_unsignaled 1`)
  5. **Ultra Touch Response & Polling** (`settings put system pointer_speed 7` + `settings put global block_untrusted_touches 0`)
  6. **Block In-Game Heads-Up Notifications** (`settings put global heads_up_notifications_enabled 0` saat main, dan tombol *Restore Defaults* untuk mengembalikan semua pengaturan sistem ke standar kapan saja).

---

## 2. Desain UI & Alur Interaksi (Responsive & Tersusun Rapi)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              HOME SCREEN                                │
│  🎮 MY GAMES                               [⚡ Boost: Nox Overdrive ⚙️] │
├─────────────────────────────────────────────────────────────────────────┤
│                     (Klik Tombol Boost Settings)                        │
│                                  ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ ⚡ PLAY AUTO-BOOST SETTINGS                                       │  │
│  │ Pilih preset tier atau centang fitur yang dijalankan saat Play    │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ [ 🟢 Eco Pulse ]   [ 🔵 Velocity Core ]   [ 🔴 Nox Overdrive ]    │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ CHECKLIST TAHAPAN EKSEKUSI NYATA:                                 │  │
│  │ [✓] 1. Deep RAM & Cache Purge (am trim-memory)                    │  │
│  │ [✓] 2. Force-Stop Background Apps (Tutup aplikasi latar belakang) │  │
│  │ [✓] 3. Fixed CPU/GPU Performance Lock (cmd power fixed-perf)      │  │
│  │ [✓] 4. SurfaceFlinger Zero-Lag (Pangkas delay frame buffer)       │  │
│  │ [✓] 5. Ultra Touch Polling & Sensitivity (Licinkan respon layar)  │  │
│  │ [✓] 6. Block Heads-Up Pop-Up Notifications (Anti gangguan war)    │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ [ Restore System Defaults ]                     [ Save Settings ] │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Tata Letak Responsif:**
  - Menggunakan *Scrollable Container* dengan batas tinggi proporsional agar seluruh *chip* Tier dan 6 baris *Checklist* tampil rapi di semua ukuran layar tanpa bertumpuk.
  - Saat pengguna menekan tombol **Play** pada kartu game mana pun, dialog **Pre-Launch Tactical Boost (`GameBoostDialog`)** akan menampilkan progres *real-time* sesuai daftar fitur yang dicentang oleh pengguna (termasuk jumlah aplikasi latar belakang yang di-*force-stop* jika opsi tersebut aktif).

---

## 3. Arsitektur Teknis & Alur Data

```
┌───────────────────────────┐       ┌────────────────────────────────────┐
│   PlayBoostSettingsDialog │──────▶│       GameSpaceRepository          │
│ (Tier + 6 Checklists)     │       │ • Simpan PlayBoostConfig di Prefs  │
└───────────────────────────┘       │ • boostAndLaunchGame() menjalankan │
                                    │   perintah shell nyata per tahap   │
┌───────────────────────────┐       │ • runAotGameCompile() menjalankan  │
│   AotCompilerScreen       │──────▶│   cmd package compile -m speed     │
│ (Tools Card + Full Screen)│       └────────────────────────────────────┘
└───────────────────────────┘
```

- **Penyimpanan Konfigurasi (`PlayBoostConfig`)**:
  - Disimpan secara persisten di `SharedPreferences` (`kyro_game_space_prefs`) sehingga pilihan pengguna tetap tersimpan meskipun aplikasi ditutup.
- **Perlindungan Aplikasi Penting Saat Force-Stop**:
  - Saat tahap **Force-Stop Background Apps** berjalan, sistem otomatis mengecualikan:
    1. Game yang sedang akan diluncurkan,
    2. Aplikasi **NOX - Game Space** sendiri (`com.nox.gamespace`),
    3. Layanan **Shizuku** (`moe.shizuku.privileged.api`), dan
    4. Aplikasi sistem inti Android (`FLAG_SYSTEM`), sehingga koneksi Shizuku/ADB dan sistem HP tetap 100% aman.
