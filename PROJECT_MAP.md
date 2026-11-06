# PROJECT_MAP.md — Amplifier Transmitter

> Cache peta proyek. Baca ini lebih dulu sebelum memindai `build.gradle` / `AndroidManifest.xml`.

## Identitas & SDK
- Root: `C:\Users\Administrator\Links\AndroidStudioProjects\AMPLIFIER TRANSMITER PROJECT\STABLE`
- Module: `app` | namespace & applicationId: `com.example.amplifierpro`
- compileSdk=37, minSdk=29, targetSdk=37, versionName=1.0 (Java 11)
- Build: `.\gradlew.bat assembleDebug` (Gradle 9.4.1) | Release: minify+shrink, signing `app/release-key.jks`
- Bahasa: mayoritas **Java**, sebagian **Kotlin** (Compose). Lib utama: AppCompat, Material, ConstraintLayout, Compose BOM, ZXing.

## Entry Point & Manifest
- LAUNCHER: `.ui.TransmitterActivity` (exported, MAIN) — activity utama.
- Service: `.service.AudioStreamService` (foreground, mediaProjection) — capture & streaming audio.
- Service: `.service.MetaListenerService` (NotificationListenerService) — metadata.
- Activity lain: `.ui.StatusActivity`, `.ui.PortraitCaptureActivity` (+ ZXing CaptureActivity).
- Permission kunci: INTERNET, RECORD_AUDIO, FOREGROUND_SERVICE(_MEDIA_PROJECTION), NEARBY_WIFI_DEVICES, LOCATION, POST_NOTIFICATIONS.

## Struktur Kode (`app/src/main/java/com/example/amplifierpro/`)
- `ui/` — layar & komponen: TransmitterActivity, StatusActivity, EqualizerActivity, MainFragment, EqualizerFragment, AudioEffectsFragment, PeqUiController, LicenseDialog, LatencyChartView, VolumePeakHelper, VuGradientDrawable, MainPagerAdapter; `ui/theme/` (Compose: Theme/Color/Type).
- `service/` — AudioStreamService, MetaListenerService.
- `network/` — AudioClient, DiscoveryClient, CommandServer, MetadataSender (streaming/discovery antar-perangkat).
- `audio/` — EqProcessor, Presets (pemrosesan EQ & preset).
- `manager/` — ThemeManager, ThemePalette, EqSettingsManager (tema & setting EQ).
- `security/` — LicenseManager, DeviceSecurityManager (lisensi & keamanan perangkat).
- Tests: `src/test` (ExampleUnitTest), `src/androidTest` (ExampleInstrumentedTest).

## Resource Penting (`app/src/main/res/`)
- Layout utama: `activity_transmitter.xml`, `activity_status.xml`, `activity_equalizer.xml`, `fragment_*`, `dialog_*`, `layout_peq_controls.xml`.
- Proguard keep: `app/keepRules/rules.keep` + `app/proguard-rules.pro`.

## Arsitektur EQ (GEQ/PEQ)
- 3 target independen: `LINK` (terantai, L=R) / `CH1` / `CH2` — tiap target punya data band, preset, dan jumlah band sendiri.
- Jumlah band per-target disimpan di pref `geq_band_count_<TARGET>` (`EqSettingsManager.KEY_GEQ_BAND_COUNT_PREFIX`).
- DSP (`EqProcessor`) hanya punya SATU grid aktif (`activeFrequencies`) yang mengikuti target terfokus; kanal lain di-remap via `EqProcessor.remapGains(src, tgtCount)`.
- Grid frekuensi: `EqProcessor.FREQS_5/7/10/15/30`; grid 5/7/10/15 adalah SUBSET dari grid 30 → remap round-trip bersifat lossless.
- Preset user dinormalisasi ke 30-band saat save (`EqSettingsManager.normalizeTo30Bands` → `remapGains(bands, 30)`).
- `EqProcessor.processAudio` memproses PCM 16-bit stereo: 4 byte/sampel (L-low, L-high, R-low, R-high), gain raw 0..24 dengan 12 = flat (0 dB).
- PENTING: `EqProcessor.setBandCount()/updateChannelL/R()/loadFullConfig()` memakai `android.util.Log` → JANGAN dipanggil dari JVM unit test.
- Test pengunci logika EQ (murni JVM, tanpa Robolectric): `app/src/test/java/com/example/amplifierpro/EqMappingTest.kt`.

## Protokol Volume Receiver (per-perangkat, satuan STEP)
- Prinsip: **receiver sebagai data acuan**. Transmitter TIDAK menyimpan tabel kemampuan perangkat; kapabilitas dilaporkan receiver saat connect → adaptif otomatis untuk perangkat baru.
- Port: metadata/audio-control **50007** (TX→RX), perintah balik **50008** (RX→TX).
- TX→RX: `VOL_CAPS_REQ` (minta kapabilitas) · `VOL_SET:<index>` (absolut, satuan step) · `VOL_STEP:<seq>:<±1>` (relatif + nomor urut)
- RX→TX: `VOL_CAPS:<max>:<current>` (kapabilitas) · `REC_VOL:<index>:<max>` (posisi aktual)
- Penyimpanan TX: `receiver_volume_<IP>` (index) + `receiver_volmax_<IP>` (max) di pref `eq_settings`; IP aktif di `active_receiver_ip`. Nilai lama `receiver_volume` (persen) hanya sebagai fallback.
- Anti-penekanan-ganda: satu tap = SATU perintah. `VOL_STEP` memakai `seq` monotonik (`EqSettingsManager.volSeq`, **static** + di-seed `currentTimeMillis`) → receiver membuang `seq <= lastVolSeq`. JANGAN kembalikan pola lama (kirim `VOL:` DAN `VOL_UP` bersamaan).
- `MetadataSender.sendMetadata` mengirim tiap paket 2× (redundansi UDP) — karena itu dedup berbasis `seq` WAJIB, bukan berbasis selisih waktu.
- Semua perubahan protokol ini **harus** di-deploy ke DUA app sekaligus (transmitter + receiver).

## Konvensi Kerja
- Multi-device: WAJIB `C:\Android\platform-tools\adb.exe -s <SERIAL>` (lihat rules bagian 7).
- Perbarui file ini hanya bila ada perubahan struktur besar.
