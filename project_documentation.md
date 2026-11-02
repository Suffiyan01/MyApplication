# RiderVoice Cockpit — Technical Documentation & Architecture

Welcome to the **RiderVoice Cockpit** Android application documentation. RiderVoice is a high-performance, real-time motorcycle HUD, squad intercom, and voice AI copilot engineered with Jetpack Compose, CameraX, ML Kit, and Google Nearby Connections P2P mesh.

---

## 🏗️ Architectural Overview

```
com.ridervoice.cockpit/
├── domain/                  # Hardware Managers, Bluetooth GATT, P2P Mesh, GPS & Sensors
├── model/                   # Data Classes, Enums, UI State (`RiderVoiceUiState`)
├── service/                 # Foreground Location & Tracking Services
├── ui/                      # Jetpack Compose UI Screens & Design System
│   ├── components/          # Persistent Status Bar, Bottom Navigation Bar, AI Sheet
│   ├── screens/             # HUD, Squad, Live Map, AI & Places, Audio DSP & Settings
│   └── theme/               # Light/Dark Color Schemes & Typography (`Theme.kt`)
└── viewmodel/               # Central `RiderVoiceViewModel` state container
```

---

## ✨ Core Features & Implementation Details

### 1. **Real Hardware Telemetry & Stability**
- **GPS Speedometer & Heading:** Driven by real device `Location` updates (`LocationRepository`), filtered with an Exponential Moving Average (EMA) low-pass filter to prevent rapid speed jitter.
- **Lean Horizon Gyroscope:** Real-time artificial horizon powered by sensor fusion from the device accelerometer and gyroscope sensors (`RealRideSensorManager`).
- **Bluetooth Headset Telemetry:** Monitors real Bluetooth headset battery percentage, talk time, and codec status via BLE GATT (`BluetoothHelmetMonitor`).

### 2. **Squad Mesh & QR Code Pairing**
- **ISO 18004 Standard QR Code Generation:** Generates valid high-contrast black-and-white ISO QR code matrices on canvas (`SquadQrCodeView` / `QrCodeGenerator`) for squad join codes (e.g., `RV-8492`).
- **CameraX + ML Kit QR Scanner:** Integrated live camera scanner modal (`QrCodeCameraScannerModal`) using CameraX `ImageAnalysis` and Google ML Kit Barcode Scanning to instantly scan squad QR codes (<50ms).
- **Auto-Connect Main Channel:** Scanning a squad QR code automatically connects the rider and switches their active intercom channel to **`CH-01 • Squad Open Mesh (FULL PACK)`**.

### 3. **Lead vs Member Squad Authority**
- **Squad Creator = LEAD (Road Captain):** Has full administrative privileges:
  - Add or remove co-riders from the mesh roster.
  - Transfer leadership (**`MAKE LEAD`**) to any co-rider.
  - Lock and sync shared navigation destinations for the pack.
  - Deactivate the squad group session.
- **Joined Riders = MEMBER:** View-access to squad radar and PTT audio intercom, with permission protection on lead-only actions.

### 4. **Dynamic Light & Dark Appearance Modes**
- **Dual Palette Engine ([Theme.kt](file:///C:/Users/web/AndroidStudioProjects/MyApplication/app/src/main/java/com/ridervoice/cockpit/ui/theme/Theme.kt)):**
  - **Tactical Dark (Night):** Carbon surface palette (`#14171F`, `#0A0C10`) optimized for night rides and OLED efficiency.
  - **Daylight High-Contrast (Sunlight):** Clean daylight grey/white surface palette (`#FFFFFF`, `#F8FAFC`, `#0F172A`) for high sunlight glare readability.
- **Universal Adaptation:** All screens, surface cards, text, bottom navigation bars, and dialogs dynamically adapt using Material3 `MaterialTheme.colorScheme`.
- **Quick Theme Toggles:** Switch instantly via the Sun/Moon icon on the Top Status Bar or inside the **Helmet & SOS** settings tab.

---

## 🛠️ Tech Stack
- **UI Toolkit:** Jetpack Compose & Material 3
- **Camera & Scanning:** CameraX 1.4 + Google ML Kit Barcode Scanning 17.3
- **Mesh Intercom:** Google Nearby Connections P2P + LiveKit Cloud WebRTC
- **Mapping:** Google Maps Compose + osmdroid OpenStreetMap Engine
- **State Management:** Kotlin Coroutines & StateFlow (`ViewModel`)
