package com.example.amplifierpro.service;

import com.example.amplifierpro.audio.EqProcessor;
import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.network.AudioClient;
import com.example.amplifierpro.network.DiscoveryClient;
import com.example.amplifierpro.network.MetadataSender;
import com.example.amplifierpro.security.LicenseManager;
import com.example.amplifierpro.ui.TransmitterActivity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioStreamService extends Service {
    private static final String TAG = "AudioStreamService";
    public static final String ACTION_STREAM_STATE_CHANGED = "com.example.amplifierpro.STREAM_STATE_CHANGED";
    public static final String ACTION_RECEIVER_NAME_DISCOVERED = "com.example.amplifierpro.RECEIVER_NAME_DISCOVERED";
    public static final String ACTION_IP_DISCOVERED = "com.example.amplifierpro.IP_DISCOVERED";
    public static final String ACTION_TRIAL_EXPIRED = "com.example.amplifierpro.TRIAL_EXPIRED";
    public static final String ACTION_TRIAL_OFFLINE_ERROR = "com.example.amplifierpro.TRIAL_OFFLINE_ERROR";
    public static final String ACTION_EXIT_APP = "com.example.amplifierpro.ACTION_EXIT_APP";
    public static final String ACTION_CLOSE_APP = "com.example.amplifierpro.ACTION_CLOSE_APP";
    public static final String EXTRA_STATUS_TEXT = "status_text";
    public static final String EXTRA_MANUAL_IP = "EXTRA_MANUAL_IP";
    public static final String EXTRA_RECEIVER_IP = "receiver_ip";
    public static final String EXTRA_RECEIVER_NAME = "receiver_name";
    
    private static final String CHANNEL_ID = "AudioStreamChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int SAMPLE_RATE = 44100;
    private static final int PORT_TCP = 50005;
    private static final int PORT_DISCOVERY = 50006;
    private static final int PORT_METADATA = 50007;

    public static volatile boolean isServiceRunning = false;
    private static volatile boolean isStreamingThreadActive = false;
    public static volatile String lastStatus = "Siap untuk streaming";
    private static AudioStreamService instance;

    public static float getCompressorGR() {
        if (instance != null && instance.eqProcessor != null) {
            return instance.eqProcessor.getCompressorGainReduction();
        }
        return 0.0f;
    }

    public static float getLimiterGR() {
        if (instance != null && instance.eqProcessor != null) {
            return instance.eqProcessor.getLimiterGainReduction();
        }
        return 0.0f;
    }

    public static float getLeftPeak() {
        if (instance != null && instance.eqProcessor != null) {
            return instance.eqProcessor.getLeftPeak();
        }
        return 0.0f;
    }

    public static float getRightPeak() {
        if (instance != null && instance.eqProcessor != null) {
            return instance.eqProcessor.getRightPeak();
        }
        return 0.0f;
    }

    private volatile MediaProjection mediaProjection;
    private volatile AudioRecord audioRecord;
    private ExecutorService executorService;
    private Handler mainHandler;
    private volatile String manualIp = null;
    private final AudioClient audioClient = new AudioClient();
    private final DiscoveryClient discoveryClient = new DiscoveryClient(PORT_DISCOVERY);
    private final MetadataSender metadataSender = new MetadataSender();
    private final EqProcessor eqProcessor = new EqProcessor();
    private EqSettingsManager eqSettingsManager;

    private final BroadcastReceiver eqUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (EqSettingsManager.ACTION_TARGET_UPDATED.equals(action)) {
                syncProcessorWithTargets();
            } else if (EqSettingsManager.ACTION_EQ_VALUE_CHANGED.equals(action)) {
                String target = intent.getStringExtra(EqSettingsManager.EXTRA_TARGET);
                int index = intent.getIntExtra(EqSettingsManager.EXTRA_BAND_INDEX, -1);
                int value = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 12);
                updateProcessorBand(target, index, value);
            } else if (EqSettingsManager.ACTION_EQ_MODE_CHANGED.equals(action)) {
                String mode = intent.getStringExtra(EqSettingsManager.EXTRA_MODE);
                if (mode != null) {
                    eqProcessor.setEqMode(mode);
                    syncPeqWithTargets();
                }
            } else if (EqSettingsManager.ACTION_PEQ_VALUE_CHANGED.equals(action)) {
                String target = intent.getStringExtra(EqSettingsManager.EXTRA_TARGET);
                int index = intent.getIntExtra(EqSettingsManager.EXTRA_BAND_INDEX, -1);
                float freq = intent.getFloatExtra(EqSettingsManager.EXTRA_PEQ_FREQ, 1000f);
                float gain = intent.getFloatExtra(EqSettingsManager.EXTRA_PEQ_GAIN, 0f);
                float q = intent.getFloatExtra(EqSettingsManager.EXTRA_PEQ_Q, 1.0f);
                updateProcessorPeqBand(target, index, freq, gain, q);
            }
 else if (EqSettingsManager.ACTION_VOLUME_UPDATED.equals(action)) {
                int value = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 100);
                eqProcessor.setMasterVolume(value);
            } else if (EqSettingsManager.ACTION_BYPASS_UPDATED.equals(action)) {
                boolean enabled = intent.getBooleanExtra(EqSettingsManager.EXTRA_VALUE, true);
                boolean dspAllowed = LicenseManager.getInstance(AudioStreamService.this).isDspAllowed();
                eqProcessor.setEqEnabled(dspAllowed && enabled);
            } else if (EqSettingsManager.ACTION_EFFECTS_MASTER_UPDATED.equals(action)) {
                boolean enabled = intent.getBooleanExtra(EqSettingsManager.EXTRA_VALUE, true);
                boolean dspAllowed = LicenseManager.getInstance(AudioStreamService.this).isDspAllowed();
                eqProcessor.setAudioEffectsEnabled(dspAllowed && enabled);
            } else if (EqSettingsManager.ACTION_STEREO_WIDTH_UPDATED.equals(action)) {
                boolean enabled = intent.getBooleanExtra(EqSettingsManager.EXTRA_ENABLED, false);
                int value = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 100);
                eqProcessor.setStereoWidthEnabled(enabled);
                eqProcessor.setStereoWidthValue(value);
            } else if (EqSettingsManager.ACTION_VIRTUAL_BASS_UPDATED.equals(action)) {
                boolean enabled = intent.getBooleanExtra(EqSettingsManager.EXTRA_ENABLED, false);
                int value = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 0);
                int minFreq = intent.getIntExtra(EqSettingsManager.EXTRA_MIN_FREQ, 30);
                int maxFreq = intent.getIntExtra(EqSettingsManager.EXTRA_MAX_FREQ, 100);
                eqProcessor.setVirtualBassEnabled(enabled);
                eqProcessor.setVirtualBassAmount(value);
                eqProcessor.setVirtualBassFrequencies(minFreq, maxFreq);
            } else if (EqSettingsManager.ACTION_COMPRESSOR_UPDATED.equals(action)) {
                boolean enabled = intent.getBooleanExtra(EqSettingsManager.EXTRA_ENABLED, false);
                float threshold = intent.getFloatExtra(EqSettingsManager.EXTRA_THRESHOLD, -12.0f);
                float ratio = intent.getFloatExtra(EqSettingsManager.EXTRA_RATIO, 2.0f);
                float attack = intent.getFloatExtra(EqSettingsManager.EXTRA_ATTACK, 20.0f);
                float release = intent.getFloatExtra(EqSettingsManager.EXTRA_RELEASE, 150.0f);
                float makeup = intent.getFloatExtra(EqSettingsManager.EXTRA_MAKEUP, 0.0f);
                eqProcessor.setCompressorEnabled(enabled);
                eqProcessor.setCompressorParams(threshold, ratio, attack, release, makeup);
            } else if (EqSettingsManager.ACTION_LIMITER_UPDATED.equals(action)) {
                boolean enabled = intent.getBooleanExtra(EqSettingsManager.EXTRA_ENABLED, false);
                float ceiling = intent.getFloatExtra(EqSettingsManager.EXTRA_CEILING, -1.0f);
                String releaseMode = intent.getStringExtra(EqSettingsManager.EXTRA_RELEASE_MODE);
                float release = intent.getFloatExtra(EqSettingsManager.EXTRA_RELEASE, 200.0f);
                float lookahead = intent.getFloatExtra(EqSettingsManager.EXTRA_LOOKAHEAD, 3.0f);
                eqProcessor.setLimiterEnabled(enabled);
                eqProcessor.setLimiterParams(ceiling, releaseMode, release, lookahead);
            } else if (EqSettingsManager.ACTION_CHANNEL_GAIN_UPDATED.equals(action)) {
                float ch1 = intent.getFloatExtra(EqSettingsManager.EXTRA_CH1_GAIN, 0.0f);
                float ch2 = intent.getFloatExtra(EqSettingsManager.EXTRA_CH2_GAIN, 0.0f);
                eqProcessor.setChannelGains(ch1, ch2);
            } else if (EqSettingsManager.ACTION_RECEIVER_VOLUME_UPDATED.equals(action)) {
                // HANYA jalur absolut yang boleh mengirim. Sebelumnya satu penekanan tombol
                // mengirim DUA perintah (VOL:<pct> DAN VOL_UP) sehingga volume receiver naik
                // 2-3x per tap. Kini relatif memakai ACTION_RECEIVER_VOL_STEP.
                boolean fromReceiver = intent.getBooleanExtra(EqSettingsManager.EXTRA_FROM_RECEIVER, false);
                if (!fromReceiver) {
                    int index = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 0);
                    sendMetadataToReceiver("VOL_SET:" + index);
                }
            } else if (EqSettingsManager.ACTION_RECEIVER_VOL_STEP.equals(action)) {
                long seq = intent.getLongExtra(EqSettingsManager.EXTRA_VOL_SEQ, 0);
                int delta = intent.getIntExtra(EqSettingsManager.EXTRA_VOL_DELTA, 0);
                if (delta != 0) {
                    sendMetadataToReceiver("VOL_STEP:" + seq + ":" + (delta > 0 ? 1 : -1));
                }
            } else if (EqSettingsManager.ACTION_RECEIVER_VOL_SET.equals(action)) {
                int index = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 0);
                sendMetadataToReceiver("VOL_SET:" + index);
            } else if (EqSettingsManager.ACTION_RECEIVER_VOL_CAPS_REQ.equals(action)) {
                sendMetadataToReceiver("VOL_CAPS_REQ");
            } else if (EqSettingsManager.ACTION_RECEIVER_VOL_UP.equals(action)) {
                sendMetadataToReceiver("VOL_UP");
            } else if (EqSettingsManager.ACTION_RECEIVER_VOL_DOWN.equals(action)) {
                sendMetadataToReceiver("VOL_DOWN");
            }
        }
    };

    private void syncProcessorWithTargets() {
        if (eqSettingsManager == null) return;

        String activeTarget = eqSettingsManager.getActiveTarget();

        // Grid DSP HARUS mengikuti jumlah band target yang sedang aktif. Dengan begitu
        // index slider di UI selalu sama dengan index band di EqProcessor (tidak bergeser).
        int grid = eqSettingsManager.getGeqBandCount(activeTarget);
        if (grid != 5 && grid != 7 && grid != 10 && grid != 15 && grid != 30) {
            grid = 30;
        }

        if ("LINK".equals(activeTarget)) {
            int[] linkBands = EqProcessor.remapGains(eqSettingsManager.getBands("LINK"), grid);
            Log.d(TAG, "[syncProcessorWithTargets] LINK active -> loadFullConfig(grid=" + grid + ")");
            eqProcessor.loadFullConfig(linkBands, linkBands);
        } else {
            int[] ch1Bands = eqSettingsManager.getBands("CH1");
            int[] ch2Bands = eqSettingsManager.getBands("CH2");

            // Kanal dengan jumlah band berbeda di-remap ke grid aktif agar keduanya
            // diproses pada frekuensi yang konsisten (tanpa sisa data lama).
            if (ch1Bands != null) ch1Bands = EqProcessor.remapGains(ch1Bands, grid);
            if (ch2Bands != null) ch2Bands = EqProcessor.remapGains(ch2Bands, grid);

            Log.d(TAG, "[syncProcessorWithTargets] CH1/CH2 active -> loadFullConfig(grid=" + grid + ")");
            eqProcessor.loadFullConfig(ch1Bands, ch2Bands);
        }
        syncPeqWithTargets();
    }

    private void syncPeqWithTargets() {
        if (eqSettingsManager == null) return;
        String activeTarget = eqSettingsManager.getActiveTarget();
        for (int i = 0; i < 4; i++) {
            if ("LINK".equals(activeTarget)) {
                float freq = eqSettingsManager.getPeqFreq("LINK", i);
                float gain = eqSettingsManager.getPeqGain("LINK", i);
                float q = eqSettingsManager.getPeqQ("LINK", i);
                eqProcessor.updatePeqFilterL(i, freq, gain, q);
                eqProcessor.updatePeqFilterR(i, freq, gain, q);
            } else {
                float f1 = eqSettingsManager.getPeqFreq("CH1", i);
                float g1 = eqSettingsManager.getPeqGain("CH1", i);
                float q1 = eqSettingsManager.getPeqQ("CH1", i);
                eqProcessor.updatePeqFilterL(i, f1, g1, q1);

                float f2 = eqSettingsManager.getPeqFreq("CH2", i);
                float g2 = eqSettingsManager.getPeqGain("CH2", i);
                float q2 = eqSettingsManager.getPeqQ("CH2", i);
                eqProcessor.updatePeqFilterR(i, f2, g2, q2);
            }
        }
    }

    private void updateProcessorPeqBand(String target, int index, float freq, float gain, float q) {
        if (eqSettingsManager == null || index < 0 || index >= 4) return;
        String currentMode = eqSettingsManager.getActiveTarget();
        if ("LINK".equals(currentMode)) {
            eqProcessor.updatePeqFilterL(index, freq, gain, q);
            eqProcessor.updatePeqFilterR(index, freq, gain, q);
        } else {
            if ("CH1".equals(target)) {
                eqProcessor.updatePeqFilterL(index, freq, gain, q);
            } else if ("CH2".equals(target)) {
                eqProcessor.updatePeqFilterR(index, freq, gain, q);
            }
        }
    }

    private void updateProcessorBand(String target, int index, int value) {
        if (eqSettingsManager == null) return;
        
        // Trust the target provided in the broadcast to avoid stale manager state
        // but still use manager's mode (LINK/NORMAL) to decide routing
        String currentMode = eqSettingsManager.getActiveTarget();
        
        if ("LINK".equals(currentMode)) {
            // In LINK mode, any band change applies to both channels
            Log.d(TAG, "[updateProcessorBand] LINK mode -> invoking EqProcessor.updateChannelL & R(index=" + index + ", rawGain=" + value + ")");
            eqProcessor.updateChannelL(index, value);
            eqProcessor.updateChannelR(index, value);
        } else {
            // Route based on the specific target intent
            if ("CH1".equals(target)) {
                Log.d(TAG, "[updateProcessorBand] CH1 target -> invoking EqProcessor.updateChannelL(index=" + index + ", rawGain=" + value + ")");
                eqProcessor.updateChannelL(index, value);
            } else if ("CH2".equals(target)) {
                Log.d(TAG, "[updateProcessorBand] CH2 target -> invoking EqProcessor.updateChannelR(index=" + index + ", rawGain=" + value + ")");
                eqProcessor.updateChannelR(index, value);
            }
        }
    }

    public static volatile String currentReceiverIp = null;
    private volatile String lastMetadataInfo = null;
    
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.w(TAG, "MediaProjection stopped by system/user");
            cleanup();
        }
    };

    private final BroadcastReceiver metadataInternalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.amplifierpro.METADATA_UPDATED".equals(intent.getAction())) {
                String info = intent.getStringExtra("info");
                lastMetadataInfo = info; 
                sendMetadataToReceiver(info);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        isServiceRunning = true;
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        executorService = Executors.newSingleThreadExecutor();
        
        eqSettingsManager = new EqSettingsManager(this);
        eqProcessor.setEqMode(eqSettingsManager.getEqMode());
        syncProcessorWithTargets();
        boolean dspAllowed = LicenseManager.getInstance(this).isDspAllowed();
        eqProcessor.setMasterVolume(eqSettingsManager.getMasterVolume());
        eqProcessor.setEqEnabled(dspAllowed && eqSettingsManager.isEqEnabled());
        eqProcessor.setAudioEffectsEnabled(dspAllowed && eqSettingsManager.isAudioEffectsEnabled());
        eqProcessor.setStereoWidthEnabled(eqSettingsManager.isStereoWidthEnabled());
        eqProcessor.setStereoWidthValue(eqSettingsManager.getStereoWidthValue());
        eqProcessor.setVirtualBassEnabled(eqSettingsManager.isVirtualBassEnabled());
        eqProcessor.setVirtualBassAmount(eqSettingsManager.getVirtualBassAmount());
        eqProcessor.setVirtualBassFrequencies(eqSettingsManager.getVirtualBassMinFreq(), eqSettingsManager.getVirtualBassMaxFreq());

        eqProcessor.setCompressorEnabled(eqSettingsManager.isCompressorEnabled());
        eqProcessor.setCompressorParams(
                eqSettingsManager.getCompressorThreshold(),
                eqSettingsManager.getCompressorRatio(),
                eqSettingsManager.getCompressorAttack(),
                eqSettingsManager.getCompressorRelease(),
                eqSettingsManager.getCompressorMakeup()
        );

        eqProcessor.setLimiterEnabled(eqSettingsManager.isLimiterEnabled());
        eqProcessor.setLimiterParams(
                eqSettingsManager.getLimiterCeiling(),
                eqSettingsManager.getLimiterReleaseMode(),
                eqSettingsManager.getLimiterReleaseManual(),
                eqSettingsManager.getLimiterLookahead()
        );

        eqProcessor.setChannelGains(
                eqSettingsManager.getChannelGain("CH1"),
                eqSettingsManager.getChannelGain("CH2")
        );

        IntentFilter eqFilter = new IntentFilter();
        eqFilter.addAction(EqSettingsManager.ACTION_TARGET_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_EQ_VALUE_CHANGED);
        eqFilter.addAction(EqSettingsManager.ACTION_VOLUME_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_BYPASS_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_EFFECTS_MASTER_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_STEREO_WIDTH_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_VIRTUAL_BASS_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_COMPRESSOR_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_LIMITER_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_CHANNEL_GAIN_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_RECEIVER_VOLUME_UPDATED);
        eqFilter.addAction(EqSettingsManager.ACTION_RECEIVER_VOL_UP);
        eqFilter.addAction(EqSettingsManager.ACTION_RECEIVER_VOL_DOWN);
        eqFilter.addAction(EqSettingsManager.ACTION_RECEIVER_VOL_STEP);
        eqFilter.addAction(EqSettingsManager.ACTION_RECEIVER_VOL_SET);
        eqFilter.addAction(EqSettingsManager.ACTION_RECEIVER_VOL_CAPS_REQ);
        eqFilter.addAction(EqSettingsManager.ACTION_EQ_MODE_CHANGED);
        eqFilter.addAction(EqSettingsManager.ACTION_PEQ_VALUE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eqUpdateReceiver, eqFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eqUpdateReceiver, eqFilter);
        }

        setupMediaListener();
        metadataSender.open();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AmplifierPro:WakeLock");
            wakeLock.acquire();
        }

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AmplifierPro:WifiLock");
            wifiLock.acquire();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(lastStatus), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, createNotification(lastStatus));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_EXIT_APP.equals(intent.getAction())) {
                cleanup();
                stopForeground(true);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(NOTIFICATION_ID);
                Intent closeIntent = new Intent(ACTION_CLOSE_APP);
                closeIntent.setPackage(getPackageName());
                sendBroadcast(closeIntent);
                stopSelf();
                System.exit(0);
                return START_NOT_STICKY;
            }

            String newIp = intent.getStringExtra(EXTRA_MANUAL_IP);
            if (newIp != null && !newIp.equals(manualIp)) {
                manualIp = newIp;
                if (eqSettingsManager != null) eqSettingsManager.setActiveReceiverIp(newIp);
                new Thread(audioClient::close).start();
            }

            Intent resultData = intent.getParcelableExtra("EXTRA_RESULT_DATA");
            int resultCode = intent.getIntExtra("EXTRA_RESULT_CODE", -1);
            
            if (resultData != null && !isStreamingThreadActive) {
                startStreamingProcess(resultCode, resultData);
            }
        }
        return START_STICKY;
    }

    private void startStreamingProcess(int resultCode, Intent resultData) {
        isStreamingThreadActive = true;
        executorService.execute(() -> {
            try {
                Thread.sleep(500);
                updateStatus("Mendapatkan Izin Audio...");
                
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                try {
                    mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException: ", e);
                    updateStatus("Gagal: Izin ditolak sistem");
                    cleanup();
                    return;
                }

                if (mediaProjection == null) {
                    updateStatus("Gagal: Izin Ditolak");
                    cleanup();
                    return;
                }

                mediaProjection.registerCallback(projectionCallback, mainHandler);

                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build();

                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build();

                int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 4;
                
                audioRecord = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSize)
                        .build();

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    updateStatus("Gagal Inisialisasi AudioRecord");
                    cleanup();
                    return;
                }

                // Proteksi Lisensi & Blokir
                LicenseManager licenseManager = LicenseManager.getInstance(this);
                if (licenseManager.isBlocked()) {
                    updateStatus("Perangkat Diblokir");
                    cleanup();
                    stopSelf();
                    return;
                }
                licenseManager.startSession();

                audioRecord.startRecording();
                int initialBufferSize = eqSettingsManager != null ? eqSettingsManager.getStreamBufferSize() : 4096;
                if (initialBufferSize <= 0) initialBufferSize = 4096;
                byte[] buffer = new byte[initialBufferSize];

                while (isServiceRunning) {
                    int desiredBufferSize = eqSettingsManager != null ? eqSettingsManager.getStreamBufferSize() : 4096;
                    if (desiredBufferSize <= 0) desiredBufferSize = 4096;
                    if (buffer.length != desiredBufferSize) {
                        buffer = new byte[desiredBufferSize];
                    }

                    String receiverIp;
                    if (manualIp != null && !manualIp.isEmpty()) {
                        receiverIp = manualIp;
                    } else {
                        updateStatus("Mencari Receiver...");
                        receiverIp = discoveryClient.performDiscovery();
                    }

                    if (receiverIp == null) {
                        currentReceiverIp = null;
                        long startWait = System.currentTimeMillis();
                        while (isServiceRunning && (System.currentTimeMillis() - startWait < 3000)) {
                            if (audioRecord != null) {
                                int read = audioRecord.read(buffer, 0, buffer.length);
                                if (read > 0) {
                                    boolean dspAllowed = licenseManager.isDspAllowed();
                                    eqProcessor.setEqEnabled(dspAllowed && eqSettingsManager.isEqEnabled());
                                    eqProcessor.setAudioEffectsEnabled(dspAllowed && eqSettingsManager.isAudioEffectsEnabled());
                                    eqProcessor.processAudio(buffer, read);
                                }
                            }
                            Thread.sleep(100); 
                        }
                        continue; 
                    }

                    // Auto-discovery success, broadcast the IP
                    if (manualIp == null || manualIp.isEmpty()) {
                        Intent discoveryIntent = new Intent(ACTION_IP_DISCOVERED);
                        discoveryIntent.putExtra(EXTRA_RECEIVER_IP, receiverIp);
                        discoveryIntent.setPackage(getPackageName());
                        sendBroadcast(discoveryIntent);
                    }

                    currentReceiverIp = receiverIp;
                    if (eqSettingsManager != null) eqSettingsManager.setActiveReceiverIp(receiverIp);
                    updateStatus("Menyambung ke " + receiverIp);
                    if (audioClient.connect(receiverIp, PORT_TCP)) {
                        String discoveredName = audioClient.performHandshake();
                        if (discoveredName != null) {
                            Intent nameIntent = new Intent(ACTION_RECEIVER_NAME_DISCOVERED);
                            nameIntent.putExtra(EXTRA_RECEIVER_IP, receiverIp);
                            nameIntent.putExtra(EXTRA_RECEIVER_NAME, discoveredName);
                            nameIntent.setPackage(getPackageName());
                            sendBroadcast(nameIntent);
                        }

                        // Minta kapabilitas & volume aktual receiver seketika setelah terkoneksi
                        sendMetadataToReceiver("VOL_CAPS_REQ");
                        broadcastLicenseStatusToReceiver();

                        boolean isFirstDataSent = false;
                        long lastProStatusSentTime = 0;
                        while (isServiceRunning && audioRecord != null && audioClient.isConnected()) {
                            int desiredSize = eqSettingsManager != null ? eqSettingsManager.getStreamBufferSize() : 4096;
                            if (desiredSize <= 0) desiredSize = 4096;
                            if (buffer.length != desiredSize) {
                                buffer = new byte[desiredSize];
                            }
                            int read = audioRecord.read(buffer, 0, buffer.length);
                            if (read > 0) {
                                // Hukuman Maksimal: Jika diblokir oleh admin, hentikan streaming seketika
                                if (licenseManager.isBlocked()) {
                                    updateStatus("Perangkat Diblokir");
                                    isServiceRunning = false;
                                    break;
                                }

                                // Freemium: Jika trial habis / bukan PRO, bypass DSP (suara flat murni)
                                boolean dspAllowed = licenseManager.isDspAllowed();
                                eqProcessor.setEqEnabled(dspAllowed && eqSettingsManager.isEqEnabled());
                                eqProcessor.setAudioEffectsEnabled(dspAllowed && eqSettingsManager.isAudioEffectsEnabled());

                                eqProcessor.processAudio(buffer, read);
                                if (!audioClient.sendAudio(buffer, 0, read)) break;
                                
                                long now = System.currentTimeMillis();
                                if (now - lastProStatusSentTime >= 2000) {
                                    lastProStatusSentTime = now;
                                    broadcastLicenseStatusToReceiver();
                                }

                                if (!isFirstDataSent) {
                                    isFirstDataSent = true;
                                    updateStatus("STREAMING AKTIF");
                                    broadcastLicenseStatusToReceiver();
                                    // Minta receiver melaporkan kapabilitas volumenya.
                                    // Ini yang membuat sistem ADAPTIF: perangkat baru tidak
                                    // perlu didaftarkan manual, kemampuannya datang sendiri.
                                    sendMetadataToReceiver("VOL_CAPS_REQ");
                                    if (lastMetadataInfo != null) {
                                        new Thread(() -> {
                                            try {
                                                for (int i = 0; i < 3; i++) {
                                                    sendMetadataToReceiver(lastMetadataInfo);
                                                    Thread.sleep(500);
                                                }
                                            } catch (Exception ignored) {}
                                        }).start();
                                    }
                                }
                            } else if (read < 0) break;
                        }
                    } else {
                        if (isServiceRunning) {
                            updateStatus("Koneksi Terputus...");
                            Thread.sleep(2000);
                        }
                    }
                    audioClient.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Global Error: ", e);
                updateStatus("Error: " + e.getLocalizedMessage());
            } finally {
                cleanup();
            }
        });
    }

    private void updateStatus(String text) {
        if (text == null) text = "Error tidak diketahui";
        lastStatus = text;
        updateNotification(text);
        Intent intent = new Intent(ACTION_STREAM_STATE_CHANGED);
        intent.putExtra(EXTRA_STATUS_TEXT, text);
        intent.putExtra("receiver_ip", currentReceiverIp); 
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "AMPX PRO", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, TransmitterActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent exitIntent = new Intent(this, AudioStreamService.class);
        exitIntent.setAction(ACTION_EXIT_APP);
        PendingIntent pExitIntent = PendingIntent.getService(
                this, 1, exitIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AMPX PRO")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "✕ Tutup", pExitIntent)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, createNotification(text));
    }

    private void setupMediaListener() {
        IntentFilter filter = new IntentFilter("com.example.amplifierpro.METADATA_UPDATED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(metadataInternalReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(metadataInternalReceiver, filter);
        }
    }

    private void sendMetadataToReceiver(String info) {
        String targetIp = currentReceiverIp;
        if (targetIp == null || targetIp.isEmpty()) {
            targetIp = manualIp;
        }
        if (targetIp != null && !targetIp.isEmpty()) {
            metadataSender.sendMetadata(targetIp, PORT_METADATA, info);
        }
    }

    private String buildLicenseStatusPayload() {
        LicenseManager lm = LicenseManager.getInstance(this);
        if (lm.isProLicensed()) {
            return "LICENSE_STATUS:PRO";
        } else if (lm.isBlocked()) {
            return "LICENSE_STATUS:BLOCKED";
        } else if (lm.isTrialActive()) {
            long remainingMs = lm.getTrialRemainingMillis();
            if (remainingMs > 0) {
                return "LICENSE_STATUS:TRIAL:" + remainingMs;
            } else {
                return "LICENSE_STATUS:EXPIRED";
            }
        } else if ("PENDING".equalsIgnoreCase(lm.getActiveTier())) {
            return "LICENSE_STATUS:PENDING";
        } else {
            return "LICENSE_STATUS:EXPIRED";
        }
    }

    private void broadcastLicenseStatusToReceiver() {
        String payload = buildLicenseStatusPayload();
        sendMetadataToReceiver(payload);
        LicenseManager lm = LicenseManager.getInstance(this);
        boolean isUnlocked = lm.isProLicensed() || (lm.isTrialActive() && lm.getTrialRemainingMillis() > 0);
        sendMetadataToReceiver("PRO_STATUS:" + (isUnlocked ? "1" : "0"));
    }

    private synchronized void cleanup() {
        if (!isServiceRunning && !isStreamingThreadActive) return;
        instance = null;
        isServiceRunning = false;
        isStreamingThreadActive = false;
        
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception ignored) {}
            audioRecord.release();
            audioRecord = null;
        }
        
        if (mediaProjection != null) {
            try {
                mediaProjection.unregisterCallback(projectionCallback);
                mediaProjection.stop();
            } catch (Exception ignored) {}
            mediaProjection = null;
        }
        
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        
        LicenseManager lm = LicenseManager.getInstance(this);
        boolean isUnlocked = lm.isProLicensed() || (lm.isTrialActive() && lm.getTrialRemainingMillis() > 0);
        if (!isUnlocked) {
            try {
                sendMetadataToReceiver("LICENSE_STATUS:EXPIRED");
                sendMetadataToReceiver("PRO_STATUS:0");
                Thread.sleep(50);
            } catch (Exception ignored) {}
        }

        currentReceiverIp = null;
        metadataSender.close();
        audioClient.close();

        LicenseManager.getInstance(this).stopSession();

        try { unregisterReceiver(metadataInternalReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(eqUpdateReceiver); } catch (Exception ignored) {}

        updateStatus("Streaming dihentikan");
        stopSelf();
    }

    @Override
    public void onDestroy() {
        cleanup();
        if (executorService != null) executorService.shutdownNow();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
