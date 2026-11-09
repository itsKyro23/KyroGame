# NOX - Game Space: Play Integrity & Device Certification Protection Plan

Rencana ini mengamankan seluruh perintah shell pada **Force-Stop Background Apps**, **Lightweight Texture (ANGLE)**, **Play Boost**, dan **Network Optimizer** agar tidak pernah mengganggu **Google Play Services (DroidGuard)**, **Google Play Store Certification**, maupun modul **Root / PlayIntegrityFix / TrickyStore**, serta menyediakan fitur **1-Tap Restore Safe System & Integrity** untuk memulihkan status perangkat dengan cepat.

## User Review & Critical Decisions

> [!IMPORTANT]
> Seluruh fitur optimasi game tetap bekerja maksimal untuk game yang ditargetkan, namun diisolasi secara ketat agar tidak menyentuh komponen keamanan Google Play, layanan sistem, maupun modul Root/Integrity.

- **Confirmed Decision 1**: Mengamankan fitur **Force-Stop Background Apps**, **Lightweight Texture**, **Play Boost**, dan **Network Optimizer** agar 100% aman terhadap *Play Integrity* (`MEETS_DEVICE_INTEGRITY` & `MEETS_STRONG_INTEGRITY`) dan *Play Protect Certification*.
- **Confirmed Decision 2**: Menambahkan kartu tindakan cepat **Restore Safe System & Play Integrity** di halaman **Settings** agar pengguna dapat langsung membersihkan sisa flag `angle_gl_driver_all_angle`, `gpu_debug_app`, `debug.*`, dan me-refresh cache layanan Google Play Store kapan saja dalam satu ketukan.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Perlindungan Proses Root, Integrity & Google Attestation pada Force-Stop Background**: Memperluas daftar proteksi (`protectedSet` dan filter sistem) agar **Force-Stop Background Apps** hanya menutup aplikasi pengguna/hiburan/media latar belakang, dan **tidak pernah** mematikan aplikasi Root Manager (*Magisk, KernelSU, APatch, Shamiko, TrickyStore, PlayIntegrityFix, Zygisk*), layanan Google (*GMS, GSF, Play Store, ExtServices*), maupun layanan keamanan OEM (*Xiaomi FindDevice, MIUI Security, Samsung Knox/KGClient*). Selain itu, tidak lagi memaksa berhenti aplikasi sistem (`FLAG_SYSTEM` / `FLAG_UPDATED_SYSTEM_APP`) kecuali aplikasi hiburan/sosial/musik yang secara eksplisit dikenal (seperti YouTube, Chrome, atau aplikasi musik bawaan).
  2. **Isolasi Per-Game pada Lightweight Texture (Tanpa Flag Global Berbahaya)**: Mengubah `angle_gl_driver_all_angle` agar **selalu `0`** (tidak pernah memaksa seluruh OS/Google Play Services memakai ANGLE), menghapus penggunaan `settings put global gpu_debug_app` dan `setprop debug.hwui.renderer skiavk` global, sehingga driver ANGLE dan filter tekstur hanya diterapkan secara spesifik pada paket game yang dipilih melalui `angle_gl_driver_selection_pkgs` dan `cmd game` / `device_config game_overlay`.
  3. **Keamanan Play Boost & Network Optimizer**: Menghapus perubahan global `block_untrusted_touches 0` dan `cmd netpolicy set restrict-background true` yang dapat menghambat sinkronisasi token sertifikasi Google Play Services di latar belakang.
  4. **Tombol 1-Tap "Restore Safe System & Integrity"**: Menyediakan tombol pemulihan instan di **Settings** untuk mereset seluruh flag sistem (`angle_gl_driver_all_angle 0`, menghapus `gpu_debug_app`, mereset properti `debug.*`, mengembalikan `netpolicy`, dan menyegarkan proses Play Store) agar perangkat langsung kembali bersih tanpa sisa flag debug.
- **Target Audience / Persona**: Pengguna perangkat Android (baik *Non-Root / Shizuku / Wireless ADB* maupun *Root / Magisk / KernelSU*) yang menginginkan performa gaming maksimal tanpa kehilangan status **Play Protect Certified** atau **Play Integrity**.
- **Key Value**: Performa game tetap kencang sementara status `MEETS_BASIC_INTEGRITY`, `MEETS_DEVICE_INTEGRITY`, dan `MEETS_STRONG_INTEGRITY` tetap terjaga aman.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Otomatis Saat Membuka Aplikasi (Auto-Sanitize on Startup)**: Saat aplikasi dibuka, jika terdeteksi flag berbahaya warisan versi sebelumnya (`angle_gl_driver_all_angle == 1` atau `gpu_debug_app` aktif), aplikasi otomatis membersihkannya di belakang layar.
  2. **Saat Menjalankan Play Boost (Force-Stop Background)**: Semua aplikasi latar belakang (Spotify, TikTok, Instagram, WhatsApp, YouTube, Chrome, dll.) tetap ditutup paksa secara menyeluruh untuk melapangkan RAM, namun layanan Google Play Integrity, layanan sistem Android, dan manajer Root/Zygisk tetap terlindungi 100%.
  3. **Saat Menggunakan Lightweight Texture**: Pengguna memilih game atau profil Home Games dan menekan *Save & Apply*; driver ANGLE hanya diaktifkan khusus untuk daftar paket game tersebut tanpa mengubah driver sistem global.
  4. **Saat Menekan "Restore Safe System & Integrity" di Settings**: Pengguna menekan tombol pemulihan di halaman **Settings**, melihat indikator proses pembersihan flag sistem, dan menerima konfirmasi bahwa seluruh pengaturan sistem & Play Integrity guard telah dikembalikan ke kondisi standar yang aman.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Tactical Dark Gaming HUD yang konsisten dengan identitas visual **NOX - Game Space**.
  - *Color Palette & Mood*: Permukaan gelap `#141826` / `#141419` dengan aksen hijau neon (`KyroGreen` `#00E676`) untuk indikator keamanan *Play Integrity Safe* dan aksen sian (`KyroCyan` `#00E5FF`) untuk kontrol tindakan.
  - *Typography & Hierarchy*: Judul kartu menggunakan tipografi tebal berjarak rapi dengan badge status `PLAY INTEGRITY SAFE` bermonospace.
  - *Component Styling & Layout*: Kartu status keamanan dan tombol *1-Tap Restore* ditempatkan secara responsif di halaman **Settings** dengan target sentuh minimal `48.dp`.
- **Interactive Feedback & Motion**: Menampilkan umpan balik *Toast* dan status langsung saat proses pembersihan flag sistem selesai dijalankan.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Isolasi ANGLE Driver Hanya untuk Paket Game (`angle_gl_driver_all_angle = 0`)**
  - *Chosen Approach*: Selalu menjaga `angle_gl_driver_all_angle = 0` dan hanya mendaftarkan paket game ke `angle_gl_driver_selection_pkgs` + `angle_gl_driver_selection_values` serta `device_config put game_overlay <pkg> ... useAngle=true`.
  - *Why*: Menyalakan `angle_gl_driver_all_angle = 1` memaksa proses `com.google.android.gms.unstable` (DroidGuard) dan SystemUI menggunakan ANGLE, yang memicu kegagalan pemeriksaan lingkungan perangkat pada Google Play Integrity.
  - *Alternatives Considered*: Mematikan ANGLE sepenuhnya — ditolak karena fitur Lightweight Texture masih dibutuhkan untuk game 3D.
- **Decision 2: Perlindungan Berlapis pada Force-Stop Background Apps**
  - *Chosen Approach*: Melindungi seluruh paket Google (`com.google.android.gms*`, `com.google.android.gsf*`, `com.android.vending*`, `com.google.android.ext*`), seluruh paket Root/Integrity (`com.topjohnwu.magisk`, `me.weishu.kernelsu`, `me.bmax.apatch`, `io.github.vvb2060.*`, `moe.shizuku.*`), serta tidak melakukan `am force-stop` membabi buta pada aplikasi ber-flag `FLAG_SYSTEM` (kecuali aplikasi media/browser/sosial yang masuk daftar target eksplisit).
  - *Why*: Memastikan aplikasi musik, sosial media, dan aplikasi pengguna tetap tertutup rapat saat *Boost*, tanpa pernah mematikan daemon Root/PlayIntegrityFix atau komponen Play Store.
- **Decision 3: Menghapus Penggunaan `gpu_debug_app` dan `block_untrusted_touches 0`**
  - *Chosen Approach*: Menghapus injeksi `settings put global gpu_debug_app` dan `settings put global block_untrusted_touches 0`, serta membersihkannya secara otomatis.
  - *Why*: Kedua flag global tersebut dipantau oleh sistem keamanan Android dan anti-cheat sebagai tanda lingkungan debug yang tidak aman.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:
```
┌───────────────────────────────────────────────────────────────────────────┐
│                         NOX - Game Space UI Layer                         │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌─────────────────┐  │
│  │   Home / Play Boost  │  │ Lightweight Texture  │  │ Settings Screen │  │
│  │  (Safe Force-Stop)   │  │ (Per-Game ANGLE Only)│  │ (1-Tap Restore) │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └────────┬────────┘  │
└─────────────┼─────────────────────────┼───────────────────────┼───────────┘
              │                         │                       │
              ▼                         ▼                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                 GameSpaceRepository & Command Sanitizer                   │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ 1. Auto-Sanitize Startup Guard:                                     │  │
│  │    Resets angle_gl_driver_all_angle=0 & deletes gpu_debug_app       │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │ 2. Integrity-Safe Force-Stop Filter:                                │  │
│  │    Protects GMS/PlayStore, System Core, & Root/PIF/TrickyStore pkgs │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │ 3. Per-Package ANGLE Command Builder:                               │  │
│  │    Applies angle_gl_driver_selection_pkgs without global debug flag │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │ 4. 1-Tap Restore Safe System & Play Integrity Action:               │  │
│  │    Cleans all global flags, debug props, netpolicy, & Play cache    │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────┬───────────────────────────────────┘
                                        │
                                        ▼
┌───────────────────────────────────────────────────────────────────────────┐
│            Privileged Shell Executor (Shizuku / Wireless ADB / Root)      │
└───────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - Menambahkan fungsi `restoreSafePlayIntegrityAndSystemDefaults()` pada `GameSpaceRepository` yang mengembalikan `Result<String>` berisi ringkasan pemulihan.
  - Menambahkan daftar proteksi paket Root/Integrity & layanan sistem penting pada filter `Force-Stop Background Apps`.
  - Memperbarui generator perintah shell `TextureConfig` dan `PlayBoostConfig` agar bebas dari flag global yang memicu kegagalan *Play Integrity*.
- **Interactive Component & State Mapping**:
  - Saat aplikasi dijalankan (`init`), `GameSpaceRepository` menjalankan pembersihan ringan untuk memastikan `angle_gl_driver_all_angle` bernilai `0` dan `gpu_debug_app` dihapus jika ada sesi lama yang tertinggal.
  - Pada **Settings Screen**, tombol **Restore Safe System & Play Integrity** memanggil `repository.restoreSafePlayIntegrityAndSystemDefaults()`, menampilkan indikator *loading*, lalu memunculkan pesan sukses setelah seluruh pengaturan sistem dan flag debug dibersihkan.
