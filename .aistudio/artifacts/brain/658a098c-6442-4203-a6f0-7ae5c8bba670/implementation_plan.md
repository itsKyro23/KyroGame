# Lightweight Texture & GPU Texture Filter Tuner

Delivers a dedicated **Lightweight Texture Tuner** (inspired by the reference screenshot) that adjusts **GPU Texture Sampling, Mipmap LOD Bias, and Anisotropic/Bilinear Filtering**—independent of display resolution scaling (`wm size`) or `cmd game` downscaling—complete with a real **Device & GPU Hardware Compatibility Scanner**, an interactive **Before / After Split-Screen Preview**, and **per-game or global** profile targeting.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and design choices have been confirmed from your responses and incorporated into this plan:

- **Confirmed Entry Points**: Accessible **both** from the **Tools Hub** (as a 3rd tactical card alongside Resolution Changer and Network Optimizer) **and** directly from each game's **Customize Game Profile** dialog (with a quick preset selector and a shortcut to launch the full Texture Tuner pre-targeted to that game).
- **Confirmed Interactive Preview**: Includes an interactive **Before / After Split-Screen Preview Canvas** with a draggable vertical divider handle (`↔`) and a press-and-hold **"Hold: Before"** comparison button that renders brick wall and ground terrain textures in real time with the exact pixelation, blockiness, bilinear blur, or nearest-neighbor filtering selected.
- **Confirmed Target Scope**: Supports **Per-Game Selection** (e.g., *Free Fire MAX*, *Call of Duty Mobile*, *Mobile Legends*, or any installed game) as well as a **Global GPU Profile** option.

---

## 1. Overview & Core Concept

- **What It Does**: Modifies how 3D game textures are sampled and filtered by the GPU without shrinking the UI or screen resolution. Users can make 3D surface textures ultra-smooth (high-LOD blurred mipmaps), retro/pixelated (`GL_NEAREST` sharp point sampling), or total blocky (high mipmap LOD bias + nearest-neighbor cutoff) to drastically reduce GPU texture memory bandwidth and frame-time spikes on heavy 3D games.
- **Target Audience / Persona**: Mobile gamers playing 3D titles (such as *Free Fire MAX*, *PUBG Mobile*, *CODM*, *Genshin Impact*, or *Roblox*) who want smoother framerates, lower GPU heat, or a high-visibility "Minecraft/Potato Graphics" competitive look while keeping their HUD, crosshair, and text razor-sharp.
- **Key Value**:
  - **Independent of Resolution Scaling**: Keeps 2D HUD elements and touch coordinates native while reducing 3D texture fill-rate load.
  - **Real Device & GPU Compatibility Verification**: Inspects the phone's actual OpenGL ES / Vulkan GPU hardware (`Adreno`, `Mali`, `PowerVR`, `Xclipse`), driver extensions (`GL_EXT_texture_filter_anisotropic`, `GL_KHR_debug`, ANGLE / GPU debug layer support), and active execution engine (`Shizuku`, `Wireless ADB Daemon`, or `Root`) to report supported texture tuning capabilities.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Checking Device & GPU Compatibility**:
     - At the top of the Lightweight Texture screen, a **GPU & Texture Compatibility Banner** displays the detected GPU Renderer (e.g., *Qualcomm Adreno 740* or *ARM Mali-G610*), OpenGL ES / Vulkan version, driver hook support, and active execution privilege status (`Supported — Hardware + System GPU Driver Tuning` or `Root Enhanced`).
  2. **Interactive Before / After Split Preview**:
     - The preview card renders a 3D-perspective brick wall and grassy ground scene split vertically by a draggable divider (`↔`):
       - **Left Side (`BEFORE`)**: Native original game texture detail.
       - **Right Side (`AFTER`)**: Live-rendered result reflecting the chosen **Texture Detail** step, **Sharp Pixels** toggle, and **Hard Distance Cutoff** toggle.
       - **Hold: Before Button**: Pressing and holding temporarily reveals the full `BEFORE` view across the entire card for instant visual comparison.
  3. **Quick Presets & Fine-Grained Controls**:
     - **Quick Presets Bar**:
       - `Smooth` (*Halus*): Bilinear/smooth mipmap filtering with reduced texture detail for clean, low-noise surfaces.
       - `Retro`: Balanced mipmap reduction with **Sharp Pixels** enabled for crisp retro pixel art edges.
       - `Total Blocky` (*Kotak Total*): Maximum mipmap LOD reduction + **Sharp Pixels** + **Hard Distance Cutoff** for ultra-lightweight blocky competitive graphics.
       - `Custom` (*Kustom*): Automatically activates whenever the user manually adjusts the slider or toggles.
     - **Texture Detail Slider (5 Steps)**:
       - `Original` → `Light` → `Retro` → `Blocky` → `Extreme` (controls Mipmap LOD Bias from `0.0` to `+3.5`).
     - **Two Independent Filter Toggles**:
       - **Sharp Pixels (`GL_NEAREST`)**: Sharp, blocky pixel edges when enabled; smooth bilinear blur (`GL_LINEAR`) when disabled.
       - **Hard Distance Cutoff (Mipmap Snap)**: Snaps near-to-far texture LOD transitions sharply (`GL_NEAREST_MIPMAP_NEAREST`) instead of trilinear blending.
  4. **Target Selector & Apply Action**:
     - Bottom bar lets the user pick a **Target Game** (with app icon and package name, or *Global System Default*) and tap **Apply & Save** (plus a **Reset to Original** action).
     - Informative tips remind the user to restart the game if it is currently running and note that the effect is most pronounced when in-game graphics are set to Low/Smooth.

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Tactical OLED Dark gaming dashboard matching Kyro Game Space's signature aesthetic, enhanced with cyan/sky-blue accent highlights for active texture presets and emerald green badges for hardware compatibility.
  - **Color Palette & Mood**: Pure OLED black background (`#07070B`), elevated carbon cards (`#13131C`), electric cyan/blue interactive controls (`#38BDF8` / `#6C5CE7`), and semantic status badges (`#00E676` Compatible, `#FFAB00` Partial/Restart Required).
  - **Typography & Hierarchy**: Bold uppercase section headers with wide tracking, monospace technical readouts for GPU renderer and LOD bias values, and concise English descriptions throughout.
  - **Adaptive Layout**: Supports both portrait single-column scrolling and landscape/tablet side-by-side split layout (Preview & Target Game on the left, Presets & Sliders on the right, matching the reference screenshot layout).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Multi-Layer GPU Texture Application (Non-Root Shizuku/ADB + Root Support)**
  - *Chosen Approach*: Apply texture tuning through a layered command pipeline that combines:
    1. Per-app Android GPU Driver & ANGLE/Game Driver settings (`settings put global gpu_debug_app`, `updatable_driver_production_opt_in_apps`, `angle_gl_driver_selection_pkgs` / `values`).
    2. System/GPU HWUI & OpenGL ES texture filtering/LOD properties (`setprop debug.hwui.texture_lod_bias`, `debug.egl.force_msaa`, `persist.sys.gpu.texture_filter`, vendor Adreno/Mali LOD properties).
    3. Per-game Unity/Unreal engine quality/mipmap preference overrides when accessible via `Shizuku` or `Root`.
  - *Why*: Works safely on non-rooted devices via Shizuku or Wireless ADB Daemon while automatically unlocking deeper vendor driver & engine config overrides when Root (`su`) is enabled.
  - *Alternatives Considered*: Modifying APK assets or injecting `.so` hooks into game processes—rejected because modifying game files triggers anti-cheat bans in online games like *Free Fire MAX* and *CODM*.

- **Decision 2: Real Hardware OpenGL ES / EGL Context Inspection for Device Compatibility**
  - *Chosen Approach*: Query the device's real GPU hardware using an offscreen `EGL14` / `GLES20` context (`GL_VENDOR`, `GL_RENDERER`, `GL_VERSION`, `GL_EXTENSIONS`) combined with system property inspection (`ro.hardware.egl`, `ro.board.platform`, Vulkan feature flags).
  - *Why*: Gives the user accurate, real hardware telemetry about their exact GPU chip and driver capabilities rather than static text.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                UI Layer (Compose)                            │
│                                                                              │
│  ┌─────────────────────────┐          ┌───────────────────────────────────┐  │
│  │       ToolsScreen       │          │        GameCustomizeDialog        │  │
│  │  (3rd Card: Lightweight │          │  (Quick Texture Preset Chip +     │  │
│  │     Texture Tuner)      │          │   "Open Texture Tuner" Shortcut)  │  │
│  └────────────┬────────────┘          └─────────────────┬─────────────────┘  │
│               │                                         │                    │
│               └────────────────────┬────────────────────┘                    │
│                                    ▼                                         │
│               ┌─────────────────────────────────────────┐                    │
│               │          TextureTunerScreen             │                    │
│               │  • GPU Compatibility Banner & Details   │                    │
│               │  • Split Before/After Canvas + Hold Btn │                    │
│               │  • Quick Presets (Smooth/Retro/Blocky)  │                    │
│               │  • 5-Step Texture Detail Slider         │                    │
│               │  • Sharp Pixels & Hard Distance Toggles │                    │
│               │  • Game Target Picker + Apply / Reset   │                    │
│               └────────────────────┬────────────────────┘                    │
└────────────────────────────────────┼─────────────────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                       Repository & GPU Hardware Layer                        │
│                                                                              │
│  ┌─────────────────────────────────┐  ┌───────────────────────────────────┐  │
│  │   GpuTextureCompatibilityProbe  │  │        GameSpaceRepository        │  │
│  │  • Offscreen EGL14/GLES20 query │  │  • textureTunerState: StateFlow   │  │
│  │  • Detects Adreno/Mali/PowerVR  │  │  • Per-game & global persistence  │  │
│  │  • Checks GL extensions & ANGLE │  │  • Auto-applies on launchGame()   │  │
│  └─────────────────────────────────┘  └─────────────────┬─────────────────┘  │
│                                                         ▼                    │
│                                       ┌───────────────────────────────────┐  │
│                                       │         ExecutorFactory           │  │
│                                       │ (Shizuku / Wireless ADB / Root)   │  │
│                                       └───────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **`TextureQuickPreset`**: `SMOOTH`, `RETRO`, `TOTAL_BLOCKY`, `CUSTOM`.
- **`TextureDetailLevel`** (5 steps):
  - `ORIGINAL` (Step 0 — LOD Bias `0.0`, Native Mipmaps)
  - `LIGHT` (Step 1 — LOD Bias `+0.75`, Mild Mipmap Reduction)
  - `RETRO` (Step 2 — LOD Bias `+1.5`, 1/4 Texture Mipmap Resolution)
  - `BLOCKY` (Step 3 — LOD Bias `+2.5`, 1/16 Blocky Mipmap Level)
  - `EXTREME` (Step 4 — LOD Bias `+3.5`, Ultra-Low 4x4/8x8 Mipmap Level)
- **`TextureConfig`**:
  - `preset: TextureQuickPreset`
  - `detailLevel: TextureDetailLevel`
  - `sharpPixels: Boolean` (`true` = Nearest-Neighbor `GL_NEAREST`, `false` = Smooth Bilinear `GL_LINEAR`)
  - `hardDistanceCutoff: Boolean` (`true` = `GL_NEAREST_MIPMAP_NEAREST`, `false` = Smooth Mipmap Transition)
  - `targetPackage: String` (`""` for Global or specific game package name)
- **`DeviceTextureCompatibility`**:
  - `isSupported: Boolean`
  - `gpuVendor: String`, `gpuRenderer: String`, `glVersion: String`
  - `supportsLodBias: Boolean`, `supportsAnisotropicFilter: Boolean`, `supportsGameDriverHook: Boolean`
  - `compatibilityLevel: String` (e.g., `"Full Support (Adreno GPU + Driver LOD)"`)
  - `summaryNote: String`

### Interactive Component & State Mapping
- **Preset Selection**: Clicking `Smooth`, `Retro`, or `Total Blocky` immediately updates `detailLevel`, `sharpPixels`, and `hardDistanceCutoff` to the preset's signature values and updates the live `AFTER` preview canvas.
- **Manual Slider / Toggle Change**: Moving the 5-step **Texture Detail** slider or toggling **Sharp Pixels** / **Hard Distance Cutoff** automatically switches the active preset pill to `Custom` if the combination does not match a standard preset.
- **Interactive Split Slider & Hold-to-Compare**: Dragging the horizontal split handle adjusts `splitFraction` (`0.0f..1.0f`) on the procedural brick/ground canvas; holding the **Hold: Before** button temporarily sets the preview to 100% `BEFORE` until released.
- **Game Launch Integration**: Launching a game from Home or Apps automatically applies its saved `TextureConfig` alongside its `GameCustomConfig`.
