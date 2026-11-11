package com.example.amplifierpro.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.service.AudioStreamService;

public class StreamingSettingsDialog {

    private static final int[] BITRATE_VALUES = {32000, 48000, 64000, 96000, 128000, 192000};
    private static final int[] FRAME_MS_VALUES = {5, 10, 20, 40};

    public static void show(Context context, Runnable onSaved) {
        EqSettingsManager settings = new EqSettingsManager(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_settings_transmitter, null);

        Button btnSave = view.findViewById(R.id.btnSaveSettings);

        TextView tvReceiverHwTitle = view.findViewById(R.id.tvReceiverHwTitle);
        TextView tvReceiverHwDetails = view.findViewById(R.id.tvReceiverHwDetails);
        Spinner spStreamMode = view.findViewById(R.id.sp_stream_mode);
        Spinner spSampleRate = view.findViewById(R.id.sp_sample_rate);
        View layoutSampleRate = view.findViewById(R.id.layout_sample_rate);
        View layoutOpusSettings = view.findViewById(R.id.layout_opus_settings);

        Spinner spPreset = view.findViewById(R.id.sp_preset);
        Spinner spBitrate = view.findViewById(R.id.sp_bitrate);
        Spinner spFrameMs = view.findViewById(R.id.sp_frame_ms);
        Spinner spAudioProfile = view.findViewById(R.id.sp_audio_profile);
        Spinner spFec = view.findViewById(R.id.sp_fec);
        Spinner spQos = view.findViewById(R.id.sp_qos);

        // Populate Hardware Info Card
        String activeIp = settings.getActiveReceiverIp();
        if (activeIp != null && !activeIp.isEmpty()) {
            String model = settings.getReceiverModel(activeIp);
            String hal = settings.getReceiverHalType(activeIp);
            int nativeSr = settings.getReceiverNativeSampleRate(activeIp);
            String supported = settings.getReceiverSupportedRates(activeIp);
            tvReceiverHwTitle.setText("Receiver: " + model + " (" + hal + ")");
            String supportedFmt = supported.replace("44100", "44.1k")
                    .replace("48000", "48.0k")
                    .replace("88200", "88.2k")
                    .replace("96000", "96.0k")
                    .replace("192000", "192.0k");
            tvReceiverHwDetails.setText("DAC Hardware: " + (nativeSr / 1000.0f) + " kHz Native | Didukung: " + supportedFmt);
        } else {
            tvReceiverHwTitle.setText("Hardware Receiver: Belum Terdeteksi");
            tvReceiverHwDetails.setText("Hubungkan ke receiver untuk membaca DAC & HAL");
        }
        settings.requestReceiverHardwareCaps();

        // Populate Stream Mode Spinner
        String[] streamModes = {
                "PCM Murni (Bit-Perfect, 0% CPU, Anti-Protect)",
                "Opus Terkompresi (Hemat Sinyal Wi-Fi)"
        };
        spStreamMode.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, streamModes));
        spStreamMode.setSelection(settings.getAudioStreamMode());

        // Populate Sample Rate Spinner
        String[] sampleRates = {
                "Otomatis (Sesuai Hardware Receiver)",
                "44.1 kHz (Bit-Perfect Legacy DAC)",
                "48.0 kHz (Bit-Perfect Modern DAC)"
        };
        spSampleRate.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, sampleRates));
        spSampleRate.setSelection(settings.getPcmSampleRateMode());

        // Stream Mode Selection Listener
        spStreamMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                boolean isPcm = (position == 0);
                layoutOpusSettings.setVisibility(isPcm ? View.GONE : View.VISIBLE);
                layoutSampleRate.setVisibility(isPcm ? View.VISIBLE : View.GONE);
                spSampleRate.setVisibility(isPcm ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Populate Spinners
        String[] presets = {
                "Standar (Nonton Video)",
                "Gaming (Low Latency - 150 ms)",
                "Pro Gaming (Super Cepat - 60 ms)",
                "Real-Time Live (Ekstrem - 20 ms)",
                "Hi-Fi Musik",
                "Jarak Jauh / Sinyal Lemah",
                "Kustom..."
        };
        spPreset.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, presets));

        String[] bitrates = {
                "32 kbps (Hemat)",
                "48 kbps",
                "64 kbps (Standar)",
                "96 kbps (Jernih)",
                "128 kbps (Hi-Fi)",
                "192 kbps (Studio HQ)"
        };
        spBitrate.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, bitrates));

        String[] frames = {
                "5 ms (Ultra Low Latency / Ekstrem)",
                "10 ms (Super Cepat)",
                "20 ms (Standar Rekomendasi)",
                "40 ms (Hemat Paket)"
        };
        spFrameMs.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, frames));

        String[] profiles = {
                "Musik / Audio Umum",
                "Low-Delay Khusus",
                "Vokal / Percakapan (VoIP)"
        };
        spAudioProfile.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, profiles));

        String[] fecList = {
                "Nonaktif (OFF)",
                "Aktif (10% Cadangan)",
                "Aktif (20% Cadangan)"
        };
        spFec.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, fecList));

        String[] qosList = {
                "Low Latency (DSCP EF / Prioritas Router)",
                "Standar"
        };
        spQos.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, qosList));

        // Restore saved settings
        spPreset.setSelection(Math.min(settings.getOpusPreset(), presets.length - 1));
        spBitrate.setSelection(getBitrateIndex(settings.getOpusBitrate()));
        spFrameMs.setSelection(getFrameIndex(settings.getOpusFrameMs()));
        spAudioProfile.setSelection(Math.min(settings.getOpusProfile(), profiles.length - 1));
        spFec.setSelection(Math.min(settings.getOpusFecPercent() / 10, fecList.length - 1));
        spQos.setSelection(Math.min(settings.getOpusQosMode(), qosList.length - 1));

        // Preset Change Auto-Configuration (Matching Base Prototype 1:1)
        final boolean[] isUserSelection = {false};
        spPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (!isUserSelection[0]) {
                    isUserSelection[0] = true;
                    return;
                }
                switch (position) {
                    case 0: // Standar Video (64k, 20ms, Musik, FEC Off, Standar)
                        spBitrate.setSelection(2); // 64 kbps
                        spFrameMs.setSelection(2); // 20 ms
                        spAudioProfile.setSelection(0); // Musik
                        spFec.setSelection(0); // OFF
                        spQos.setSelection(1); // Standar
                        break;
                    case 1: // Gaming (64k, 5ms, Low-Delay, FEC Off, Buffer 150ms)
                        spBitrate.setSelection(2); // 64 kbps
                        spFrameMs.setSelection(0); // 5 ms
                        spAudioProfile.setSelection(1); // Low-Delay Khusus
                        spFec.setSelection(0); // OFF
                        spQos.setSelection(0); // Low Latency
                        break;
                    case 2: // Pro Gaming Super Cepat (64k, 5ms, Low-Delay, FEC Off, Buffer 60ms)
                        spBitrate.setSelection(2); // 64 kbps
                        spFrameMs.setSelection(0); // 5 ms
                        spAudioProfile.setSelection(1); // Low-Delay Khusus
                        spFec.setSelection(0); // OFF
                        spQos.setSelection(0); // Low Latency
                        break;
                    case 3: // Real-Time Live Ekstrem (64k, 5ms, Low-Delay, FEC Off, Buffer 20ms)
                        spBitrate.setSelection(2); // 64 kbps
                        spFrameMs.setSelection(0); // 5 ms
                        spAudioProfile.setSelection(1); // Low-Delay Khusus
                        spFec.setSelection(0); // OFF
                        spQos.setSelection(0); // Low Latency
                        break;
                    case 4: // Hi-Fi Musik (128k, 20ms, Musik, FEC Off, Standar, Buffer 1000ms)
                        spBitrate.setSelection(4); // 128 kbps
                        spFrameMs.setSelection(2); // 20 ms
                        spAudioProfile.setSelection(0); // Musik
                        spFec.setSelection(0); // OFF
                        spQos.setSelection(1); // Standar
                        break;
                    case 5: // Jarak Jauh / Sinyal Lemah (48k, 20ms, Musik, FEC Off, Standar, Buffer 2500ms)
                        spBitrate.setSelection(1); // 48 kbps
                        spFrameMs.setSelection(2); // 20 ms
                        spAudioProfile.setSelection(0); // Musik
                        spFec.setSelection(0); // OFF
                        spQos.setSelection(1); // Standar
                        break;
                    case 6: // Kustom... (Bebas)
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Info Dialogs
        setupInfoDialogs(context, view);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        btnSave.setOnClickListener(v -> {
            int port = 5000;
            int streamMode = spStreamMode.getSelectedItemPosition();
            int sampleRateMode = spSampleRate.getSelectedItemPosition();
            int preset = spPreset.getSelectedItemPosition();
            int bitrate = BITRATE_VALUES[Math.min(spBitrate.getSelectedItemPosition(), BITRATE_VALUES.length - 1)];
            int frameMs = FRAME_MS_VALUES[Math.min(spFrameMs.getSelectedItemPosition(), FRAME_MS_VALUES.length - 1)];
            int profile = spAudioProfile.getSelectedItemPosition();
            int fec = spFec.getSelectedItemPosition() * 10;
            int qos = spQos.getSelectedItemPosition();
            boolean isBroadcast = false;

            if (settings.getAudioStreamMode() != streamMode) {
                settings.setAudioStreamMode(streamMode);
            }
            if (settings.getPcmSampleRateMode() != sampleRateMode) {
                settings.setPcmSampleRateMode(sampleRateMode);
            }
            settings.setUdpPort(port);
            settings.setBroadcastEnabled(isBroadcast);
            settings.setOpusPreset(preset);
            settings.setOpusBitrate(bitrate);
            settings.setOpusFrameMs(frameMs);
            settings.setOpusProfile(profile);
            settings.setOpusFecPercent(fec);
            settings.setOpusQosMode(qos);

            if (AudioStreamService.isServiceRunning) {
                AudioStreamService.opusClient.updateSettings(preset, bitrate, frameMs, profile, fec, qos);
            }

            Toast.makeText(context, "Pengaturan streaming disimpan!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onSaved != null) onSaved.run();
        });

        dialog.show();
    }

    private static void setupInfoDialogs(Context context, View view) {
        view.findViewById(R.id.btn_info_stream_mode).setOnClickListener(v -> showInfo(context,
                "Mode Transmisi Audio",
                "• PCM Murni (Bit-Perfect):\n" +
                "Mengirim data audio 16-bit asli tanpa kompresi dan 0% CPU decoding di receiver. Sempurna untuk Samsung Galaxy Star / amplifier sensitif agar tidak terjadi clipping intersample yang memicu protect mode.\n\n" +
                "• Opus Terkompresi:\n" +
                "Mengompresi suara 32-192 kbps. Sangat hemat data dan stabil di sinyal Wi-Fi lemah atau jarak jauh."));

        view.findViewById(R.id.btn_info_sample_rate).setOnClickListener(v -> showInfo(context,
                "Sample Rate Transmisi",
                "• Otomatis: Menyesuaikan otomatis dengan DAC hardware receiver yang terhubung (44.1 kHz untuk Galaxy Star, 48.0 kHz untuk perangkat modern).\n\n" +
                "• 44.1 kHz: Memaksa rate CD / Galaxy Star tanpa software resampling.\n\n" +
                "• 48.0 kHz: Memaksa rate standar audio Android modern."));

        view.findViewById(R.id.btn_info_preset).setOnClickListener(v -> showInfo(context,
                "Preset Mode Cepat",
                "Menyetel kombinasi Bitrate, Frame, dan Buffer secara otomatis sekali klik sesuai kebutuhan:\n\n" +
                "• Standar (Video): 64 kbps, 20 ms. Sangat sinkron dan mulus untuk YouTube/film (Buffer: 500ms).\n" +
                "• Gaming (Low Latency): 64 kbps, 5 ms. Delay rendah dan stabil untuk game (Buffer: 150ms).\n" +
                "• Pro Gaming (Super Cepat): 64 kbps, 5 ms. Respon kompetitif sangat cepat (Buffer: 60ms).\n" +
                "• Real-Time Live (Ekstrem): 64 kbps, 5 ms. Latensi titik terendah untuk monitoring/DJ jarak dekat (Buffer: 20ms).\n" +
                "• Hi-Fi Musik: 128 kbps, 20 ms. Kejernihan akustik maksimal setara CD (Buffer: 1000ms).\n" +
                "• Jarak Jauh / Sinyal Lemah: 48 kbps, 20 ms. Tahan rintangan sinyal dan jarak jauh (Buffer: 2500ms)."));

        view.findViewById(R.id.btn_info_bitrate).setOnClickListener(v -> showInfo(context,
                "Bitrate Audio",
                "Mengatur kecepatan data suara yang dikompresi Opus:\n\n" +
                "• 32 kbps: Sangat hemat data, cocok di jaringan lambat/banyak perangkat.\n" +
                "• 64 kbps (Default): Keseimbangan ideal antara kejernihan dan kelancaran streaming.\n" +
                "• 128 - 192 kbps: Kualitas audio studio resolusi tinggi untuk penggemar musik."));

        view.findViewById(R.id.btn_info_frame).setOnClickListener(v -> showInfo(context,
                "Durasi Frame Audio",
                "Ukuran potongan waktu paket audio dalam milidetik:\n\n" +
                "• 5 ms: Latensi ultra rendah (ekstrem) untuk respon instan tanpa jeda.\n" +
                "• 10 ms: Super cepat untuk live monitoring/gaming.\n" +
                "• 20 ms (Default): Standar emas industri VoIP dan audio streaming.\n" +
                "• 40 ms: Mengurangi beban pengiriman paket di router Wi-Fi yang padat pengguna."));

        view.findViewById(R.id.btn_info_profile).setOnClickListener(v -> showInfo(context,
                "Profil Suara Opus",
                "Mengatur penekanan algoritma kompresi Opus:\n\n" +
                "• Musik / Audio Umum: Mempertahankan respons nada penuh 20 Hz - 20 kHz untuk instrumen dan lagu.\n" +
                "• Low-Delay Khusus: Menonaktifkan prediksi algoritma delay untuk respon lebih cepat.\n" +
                "• Vokal (VoIP): Mengutamakan artikulasi kejernihan suara manusia."));

        view.findViewById(R.id.btn_info_fec).setOnClickListener(v -> showInfo(context,
                "In-Band FEC (Forward Error Correction)",
                "Teknologi proteksi paket hilang:\n\n" +
                "Transmitter menyisipkan data cadangan beresolusi rendah dari frame sebelumnya ke dalam paket saat ini.\n" +
                "Jika ada paket yang hilang di perjalanan, Receiver dapat memulihkannya langsung tanpa jeda."));

        view.findViewById(R.id.btn_info_qos).setOnClickListener(v -> showInfo(context,
                "QoS Prioritas Router (DSCP)",
                "Memberi label prioritas transmisi paket (Traffic Class) pada kartu Wi-Fi dan router:\n\n" +
                "• Low Latency (DSCP EF): Menginstruksikan router memprioritaskan paket audio di atas antrean unduhan atau browsing perangkat lain.\n" +
                "• Standar: Prioritas paket jaringan normal."));
    }

    private static void showInfo(Context context, String title, String msg) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private static int getBitrateIndex(int val) {
        for (int i = 0; i < BITRATE_VALUES.length; i++) {
            if (BITRATE_VALUES[i] == val) return i;
        }
        return 2; // default 64k
    }

    private static int getFrameIndex(int val) {
        for (int i = 0; i < FRAME_MS_VALUES.length; i++) {
            if (FRAME_MS_VALUES[i] == val) return i;
        }
        return 2; // default 20ms
    }
}
