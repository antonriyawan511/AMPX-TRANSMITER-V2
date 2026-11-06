# Implementasi Panel Status Diagnostik (Transmitter)

Implementasi halaman baru untuk memantau status permission dan kondisi sistem secara real-time guna mengatasi masalah *silent failure* pada komunikasi Transmitter↔Receiver.

## User Review Required

> [!IMPORTANT]
> Navigasi halaman ini akan ditambahkan sebagai gerakan **Swipe ke Kiri** (dari kanan ke kiri) pada layar utama, sebagai pasangan dari Equalizer yang menggunakan Swipe ke Kanan. Mohon konfirmasi jika Anda lebih menyukai tombol fisik di UI.

## Proposed Changes

### [Component] UI & Navigation

#### [NEW] [StatusActivity.java](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER%20TRANSMITER%20PROJECT/AMPLIFIERPRO/app/src/main/java/com/example/amplifierpro/StatusActivity.java)
Akan menangani logika pengecekan 7 item status:
1. **Notification Listener:** Menggunakan `Settings.Secure`.
2. **Media Session:** Mendengarkan broadcast status `activeController` dari `MetaListenerService`.
3. **Battery Optimization:** Menggunakan `PowerManager.isIgnoringBatteryOptimizations()`.
4. **RECORD_AUDIO:** Cek runtime permission standar.
5. **Location/Nearby:** Cek status GPS dan permission.
6. **TCP/IP Connection:** Mengambil data dari `AudioStreamService`.
7. **Metadata Status:** Menampilkan judul lagu terakhir yang berhasil di-parsing.

#### [NEW] [activity_status.xml](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER%20TRANSMITER%20PROJECT/AMPLIFIERPRO/app/src/main/res/layout/activity_status.xml)
Desain UI menggunakan `CardView` atau `LinearLayout` vertikal dengan indikator warna (Hijau/Merah) untuk tiap item. Sertakan tombol "FIX" di samping item yang bermasalah untuk membuka Settings terkait.

#### [MODIFY] [TransmitterActivity.java](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER%20TRANSMITER%20PROJECT/AMPLIFIERPRO/app/src/main/java/com/example/amplifierpro/TransmitterActivity.java)
Menambahkan deteksi swipe ke kiri pada `GestureDetector` untuk membuka `StatusActivity`.

---

### [Component] Service Logic Improvements

#### [MODIFY] [MetaListenerService.java](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER%20TRANSMITER%20PROJECT/AMPLIFIERPRO/app/src/main/java/com/example/amplifierpro/MetaListenerService.java)
Menambahkan mekanisme broadcast tambahan setiap kali `activeController` berubah (menjadi null atau terisi) agar UI Status bisa menampilkan indikator "Media Session Detected" secara akurat.

#### [MODIFY] [AudioStreamService.java](file:///C:/Users/Administrator/Links/AndroidStudioProjects/AMPLIFIER%20TRANSMITER%20PROJECT/AMPLIFIERPRO/app/src/main/java/com/example/amplifierpro/AudioStreamService.java)
Menyediakan informasi IP Receiver yang sedang aktif melalui intent broadcast yang lebih detail.

## Verification Plan

### Manual Verification
1. Jalankan aplikasi, swipe ke kiri untuk masuk ke Panel Status.
2. Verifikasi semua indikator berwarna Hijau saat streaming berjalan normal.
3. Coba matikan "Notification Access" di Settings HP, lalu cek apakah indikator di app berubah menjadi Merah secara real-time.
4. Coba hapus permission Microphone (Audio), dan pastikan panel mendeteksinya.
5. Verifikasi tombol "FIX" pada bagian Battery Optimization membuka dialog sistem "Ignore Battery Optimizations".
