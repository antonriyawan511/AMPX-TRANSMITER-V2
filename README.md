# AMPX Transmitter — High-Fidelity Android Audio Streamer & DSP Equalizer

<div align="center">
  <h3>Transmitter Audio Nirkabel Latensi Rendah (MediaProjection & Internal Audio Capture) dengan DSP Graphic/Parametric EQ untuk Android</h3>
  <p><b>Internal AudioPlaybackCapture API • Raw TCP/UDP Streaming • DSP GEQ/PEQ Studio • QR Code Pairing</b></p>
</div>

---

## 📌 Ringkasan Proyek (Overview)

**AMPX Transmitter** adalah pasangan pemancar (*Transmitter*) untuk sistem receiver nirkabel **AMPX Receiver**. Aplikasi ini mampu menangkap sinyal audio internal perangkat Android secara *lossless* menggunakan Android `AudioPlaybackCapture` (MediaProjection API), memprosesnya melalui engine equalizer DSP profesional (*Graphic EQ & Parametric EQ*), lalu memancarkannya secara *real-time* ke perangkat Receiver melalui jaringan Wi-Fi lokal dengan latensi ultra-rendah.

Selain itu, aplikasi ini dilengkapi dengan penangkap metadata lagu otomatis (*NotificationListenerService*), grafik pemantau latensi *real-time*, pengukur level volume (*VU meter*), dan pemindai QR Code untuk pemasangan instan (*instant pairing*).

> **Versi**: **v074 - STABLE**  
> **Pasangan Receiver**: [`ampx-receiver`](https://github.com/antonriyawan511/ampx-receiver)

---

## ✨ Fitur-Fitur Utama

### 1. Internal Audio Capture (MediaProjection API)
- **AudioPlaybackCapture**: Menangkap aliran audio digital internal dari aplikasi pemutar musik (Spotify, YouTube, Apple Music, dll.) tanpa penurunan kualitas mikrofon fisik.
- **Foreground Audio Service**: Berjalan di latar belakang dengan konsumsi baterai efisien dan notifikasi status pemancaran aktif.

### 2. Studio DSP Equalizer (GEQ & PEQ)
- **Graphic Equalizer (GEQ)**: Pengaturan multi-band frekuensi audio responsif.
- **Parametric Equalizer (PEQ)**: Kontrol presisi titik frekuensi, gain (*dB*), dan nilai faktor kualitas (*Q-factor*).
- **Rute Kanal Fleksibel**: 3 mode target independen:
  - `LINK`: Kanal Kiri dan Kanan terikat (*Stereo matched*).
  - `CH1`: Kanal 1 / Kiri mandiri.
  - `CH2`: Kanal 2 / Kanan mandiri.
- **Dynamic Remapping**: Penyesuaian grid frekuensi DSP real-time saat berpindah preset.

### 3. Jaringan Latensi Rendah & Sinkronisasi Cerdas
- **AudioClient (Raw TCP)**: Pengiriman stream PCM 16-bit 44.1kHz Stereo ke Receiver dengan latensi minimal.
- **DiscoveryClient (UDP Broadcast)**: Deteksi otomatis IP Receiver di jaringan LAN Wi-Fi.
- **Metadata Sender**: Mengirimkan judul lagu, artis, dan album secara otomatis ke layar Receiver melalui `MetaListenerService`.
- **QR Code Fast Pairing**: Pindai QR Code pada layar Receiver untuk menghubungkan perangkat seketika tanpa input IP manual.

### 4. Monitoring & Telemetri Real-Time
- **Latency Chart View**: Grafik pemantauan stabilitas jaringan dan jeda transmisi secara visual.
- **Volume Peak & VU Meter**: Indikator visual amplitudo audio input/output dengan gradasi warna modern.

---

## 🛠️ Arsitektur & Teknologi

| Komponen | Spesifikasi |
|---|---|
| **Bahasa Utama** | Java & Kotlin (Hybrid Architecture) |
| **UI Framework** | Android XML Layouts + Jetpack Compose (BOM) |
| **Audio Capture** | Android `AudioPlaybackCaptureConfiguration` (Android 10+ / API 29+) |
| **Audio DSP** | Custom Floating-Point DSP Filter & Equalizer Engine |
| **Networking** | Java Raw Sockets (TCP Port 50005 & UDP Port 50006) |
| **Barcode/QR** | ZXing Barcode Scanning Library |
| **Target SDK** | Android 15 / 16 (API 37) |
| **Minimum SDK** | Android 10 (API 29) |
| **Build System** | Gradle 9.5 dengan Kotlin DSL |

---

## 🚀 Cara Menjalankan Secara Lokal

### Prasyarat
1. **Android Studio** (Koala / Ladybug atau versi terbaru)
2. **JDK 17 atau JDK 21**
3. Perangkat fisik Android minimal **Android 10 (API 29)** untuk mendukung penangkapan audio internal.

### Langkah Build & Run
```bash
# Clone repository
git clone https://github.com/USERNAME/ampx-transmiter.git
cd ampx-transmiter

# Build Debug APK
./gradlew assembleDebug

# Install ke perangkat yang terhubung USB
adb install -r -d -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.amplifierpro/.ui.TransmitterActivity
```

---

## 📄 Lisensi
Didistribusikan untuk pengembangan internal dan cadangan kode pribadi.
