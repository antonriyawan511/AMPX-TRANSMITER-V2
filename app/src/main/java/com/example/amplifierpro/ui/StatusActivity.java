package com.example.amplifierpro.ui;

import com.example.amplifierpro.service.AudioStreamService;
import com.example.amplifierpro.service.MetaListenerService;
import com.example.amplifierpro.network.MetadataSender;
import com.example.amplifierpro.R;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.graphics.Color;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StatusActivity extends AppCompatActivity implements ThemeManager.OnThemeChangeListener {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable statusChecker;
    private boolean isControllerDetected = false;
    private String activePackage = null;
    private boolean isPlaying = false;
    private String currentReceiverIp = "Not connected";
    private GestureDetector gestureDetector;
    private TextView tvRadioReceiverTarget;
    private Button btnSendRadioToReceiver;
    private TextView tvTxBufferMsInfo;
    private TextView tvRttLatencyStats;
    private LatencyChartView latencyChartView;
    private final MetadataSender radioMetadataSender = new MetadataSender();

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MetaListenerService.ACTION_CONTROLLER_STATUS.equals(action)) {
                isControllerDetected = intent.getBooleanExtra(MetaListenerService.EXTRA_HAS_CONTROLLER, false);
                activePackage = intent.getStringExtra(MetaListenerService.EXTRA_PACKAGE_NAME);
                isPlaying = intent.getBooleanExtra(MetaListenerService.EXTRA_IS_PLAYING, false);
                updateDiagnosticUI();
            } else if (AudioStreamService.ACTION_STREAM_STATE_CHANGED.equals(action)) {
                currentReceiverIp = intent.getStringExtra("receiver_ip");
                if (currentReceiverIp == null) currentReceiverIp = "Not connected";
                updateDiagnosticUI();
            } else if ("com.example.amplifierpro.METADATA_UPDATED".equals(action)) {
                updateDiagnosticUI();
            } else if (MetaListenerService.ACTION_RTT_STATS_UPDATED.equals(action)) {
                long min = intent.getLongExtra(MetaListenerService.EXTRA_RTT_MIN, 0);
                long avg = intent.getLongExtra(MetaListenerService.EXTRA_RTT_AVG, 0);
                long max = intent.getLongExtra(MetaListenerService.EXTRA_RTT_MAX, 0);
                long[] samples = intent.getLongArrayExtra(MetaListenerService.EXTRA_RTT_SAMPLES);

                if (tvRttLatencyStats != null) {
                    tvRttLatencyStats.setText(String.format(java.util.Locale.US, "Min: %dms | Avg: %dms | Max: %dms", min, avg, max));
                }
                if (latencyChartView != null && samples != null && samples.length > 0) {
                    latencyChartView.setSamples(samples, samples.length);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        EdgeToEdge.enable(this,
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        View rootView = findViewById(R.id.rootStatusLayout);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupSwipeUpToClose();

        initDiagnosticItems();
        tvRadioReceiverTarget = findViewById(R.id.tvRadioReceiverTarget);
        btnSendRadioToReceiver = findViewById(R.id.btnSendRadioToReceiver);
        tvTxBufferMsInfo = findViewById(R.id.tvTxBufferMsInfo);
        tvRttLatencyStats = findViewById(R.id.tvRttLatencyStats);
        latencyChartView = findViewById(R.id.latencyChartView);

        if (btnSendRadioToReceiver != null) {
            btnSendRadioToReceiver.setOnClickListener(v -> showSendRadioDialog());
        }
        setupLatencyPresetUI();
        radioMetadataSender.open();

        requestServiceStatus();
        startStatusChecker();

        ThemeManager.getInstance(this).registerListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MetaListenerService.ACTION_CONTROLLER_STATUS);
        filter.addAction(AudioStreamService.ACTION_STREAM_STATE_CHANGED);
        filter.addAction("com.example.amplifierpro.METADATA_UPDATED");
        filter.addAction(MetaListenerService.ACTION_RTT_STATS_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    private void setupSwipeUpToClose() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 150;
            private static final int SWIPE_VELOCITY_THRESHOLD = 150;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                // Detect Swipe Up (Gerakan ke atas)
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (diffY < -SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        finishWithAnimation();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void initDiagnosticItems() {
        setupItem(R.id.statusNotifListener, "Notification Listener", "Access to media titles");
        setupItem(R.id.statusMediaSession, "Media Session", "Active player detection");
        setupItem(R.id.statusBatteryOpt, "Battery Optimization", "Background stability");
        setupItem(R.id.statusAudioPermission, "Audio Permission", "Internal sound capture");
        setupItem(R.id.statusLocation, "Location / Nearby", "WiFi signal monitoring");
        setupItem(R.id.statusTcpConnection, "TCP Connection", "Audio stream status");
        setupItem(R.id.statusMetadata, "Current Metadata", "Title parsing status");
    }

    private void setupItem(int id, String label, String desc) {
        View view = findViewById(id);
        ((TextView) view.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) view.findViewById(R.id.tvDescription)).setText(desc);
    }

    private void startStatusChecker() {
        statusChecker = new Runnable() {
            @Override
            public void run() {
                updateDiagnosticUI();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(statusChecker);
    }

    private void requestServiceStatus() {
        sendBroadcast(new Intent(MetaListenerService.ACTION_REQUEST_REFRESH).setPackage(getPackageName()));
    }

    private void updateDiagnosticUI() {
        // 1. Notification Listener
        boolean notifActive = isNotificationServiceEnabled();
        updateItemUI(R.id.statusNotifListener, notifActive,
                notifActive ? "Active" : "Disabled",
                notifActive ? null : () -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));

        // 2. Media Session
        String sessionStatus = "No active session";
        boolean sessionOk = isControllerDetected;
        if ("PERMISSION_DENIED".equals(activePackage)) {
            sessionStatus = "Permission Denied (System)";
            sessionOk = false;
        } else if (isControllerDetected && activePackage != null) {
            sessionStatus = (isPlaying ? "▶ Playing: " : "⏸ Paused: ") + activePackage;
        }
        updateItemUI(R.id.statusMediaSession, sessionOk, sessionStatus, null);

        // 3. Battery Optimization
        boolean batteryIgnored = isBatteryOptimizationsIgnored();
        updateItemUI(R.id.statusBatteryOpt, batteryIgnored,
                batteryIgnored ? "Whitelisted" : "Optimized (Risky)",
                batteryIgnored ? null : () -> requestIgnoreBatteryOptimizations());

        // 4. Audio Permission
        boolean audioPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        updateItemUI(R.id.statusAudioPermission, audioPerm,
                audioPerm ? "Granted" : "Denied", null);

        // 5. Location/Nearby
        boolean locPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        updateItemUI(R.id.statusLocation, locPerm,
                locPerm ? "Active" : "Location Off/Denied", null);

        // 6. TCP Connection
        boolean tcpActive = AudioStreamService.isServiceRunning && "STREAMING AKTIF".equals(AudioStreamService.lastStatus);
        updateItemUI(R.id.statusTcpConnection, tcpActive,
                tcpActive ? "Connected to " + currentReceiverIp : "Disconnected", null);

        // 7. Metadata
        // Attempt to get last metadata if available (cached in AudioStreamService or broadcast)
        updateItemUI(R.id.statusMetadata, tcpActive, tcpActive ? "Receiving Data" : "No Data", null);

        // 8. Receiver Radio URL
        String targetIp = getEffectiveReceiverIp();
        if (tvRadioReceiverTarget != null) {
            tvRadioReceiverTarget.setText("Target: " + (targetIp != null ? targetIp : "Belum terhubung"));
        }

        // 9. Realtime Network & Buffer Monitor Info
        EqSettingsManager eqManager = new EqSettingsManager(this);
        int bufferBytes = eqManager.getStreamBufferSize();
        double bufferMs = (bufferBytes / 176.4);
        if (tvTxBufferMsInfo != null) {
            tvTxBufferMsInfo.setText(String.format(java.util.Locale.US, "%.1f ms (%d Byte)", bufferMs, bufferBytes));
        }
    }

    private String getEffectiveReceiverIp() {
        if (AudioStreamService.currentReceiverIp != null && !AudioStreamService.currentReceiverIp.isEmpty()) {
            return AudioStreamService.currentReceiverIp;
        }
        if (currentReceiverIp != null && !currentReceiverIp.isEmpty() && !"Not connected".equals(currentReceiverIp)) {
            return currentReceiverIp;
        }
        SharedPreferences prefs = getSharedPreferences("AMPLIFIER_SETTINGS", Context.MODE_PRIVATE);
        String manualIp = prefs.getString("manual_ip", "");
        if (manualIp != null && !manualIp.isEmpty()) {
            return manualIp;
        }
        return null;
    }

    private void showSendRadioDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_send_radio, null);
        EditText etTargetIp = dialogView.findViewById(R.id.etTargetReceiverIp);
        EditText etStations = dialogView.findViewById(R.id.etSendRadioStations);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelSendRadio);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirmSendRadio);

        String defaultIp = getEffectiveReceiverIp();
        if (defaultIp != null) {
            etTargetIp.setText(defaultIp);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String ip = etTargetIp.getText().toString().trim();
            String stations = etStations.getText().toString().trim();

            if (ip.isEmpty()) {
                Toast.makeText(this, "Masukkan IP Receiver terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }
            if (stations.isEmpty()) {
                Toast.makeText(this, "Masukkan daftar stasiun radio", Toast.LENGTH_SHORT).show();
                return;
            }

            // Ensure sender socket is open
            radioMetadataSender.open();
            // Send UDP packet on port 50007
            radioMetadataSender.sendMetadata(ip, 50007, "ADD_STATIONS:" + stations);

            // Save IP to preferences for convenience
            getSharedPreferences("AMPLIFIER_SETTINGS", Context.MODE_PRIVATE)
                    .edit()
                    .putString("manual_ip", ip)
                    .apply();

            Toast.makeText(this, "Stasiun radio berhasil dikirim ke " + ip, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            updateDiagnosticUI();
        });

        dialog.show();
    }

    private void updateItemUI(int id, boolean active, String status, Runnable fixAction) {
        View view = findViewById(id);
        View indicator = view.findViewById(R.id.viewIndicator);
        TextView tvDesc = view.findViewById(R.id.tvDescription);
        Button btnFix = view.findViewById(R.id.btnFix);

        indicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active ? Color.parseColor("#00C853") : Color.parseColor("#D50000")));
        tvDesc.setText(status);

        if (fixAction != null && !active) {
            btnFix.setVisibility(View.VISIBLE);
            btnFix.setOnClickListener(v -> fixAction.run());
        } else {
            btnFix.setVisibility(View.GONE);
        }
    }

    private boolean isNotificationServiceEnabled() {
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return listeners != null && listeners.contains(getPackageName());
    }

    private boolean isBatteryOptimizationsIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestIgnoreBatteryOptimizations() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void finishWithAnimation() {
        finish();
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
    }

    @Override
    public void onBackPressed() {
        finishWithAnimation();
    }

    private void setupLatencyPresetUI() {
        RadioGroup rgLatency = findViewById(R.id.rgLatencyPreset);
        RadioButton rbUltraLow = findViewById(R.id.rbLatencyUltraLow);
        RadioButton rbBalanced = findViewById(R.id.rbLatencyBalanced);
        RadioButton rbHigh = findViewById(R.id.rbLatencyHigh);

        if (rgLatency == null) return;

        EqSettingsManager settingsManager = new EqSettingsManager(this);
        int currentBuffer = settingsManager.getStreamBufferSize();

        if (currentBuffer <= 2048) {
            if (rbUltraLow != null) rbUltraLow.setChecked(true);
        } else if (currentBuffer >= 8192) {
            if (rbHigh != null) rbHigh.setChecked(true);
        } else {
            if (rbBalanced != null) rbBalanced.setChecked(true);
        }

        rgLatency.setOnCheckedChangeListener((group, checkedId) -> {
            int newSize;
            String message;
            if (checkedId == R.id.rbLatencyUltraLow) {
                newSize = 2048;
                message = "🚀 Latensi Ultra-Low (~11ms) Diaktifkan";
            } else if (checkedId == R.id.rbLatencyHigh) {
                newSize = 8192;
                message = "🛡️ High Buffer (~46ms) Diaktifkan";
            } else {
                newSize = 4096;
                message = "⚡ Balanced Latency (~23ms) Diaktifkan";
            }
            settingsManager.setStreamBufferSize(newSize);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onThemeChanged(ThemePalette theme) {
        if (theme == null) return;
        int accent = theme.accentColor;
        ColorStateList csl = ColorStateList.valueOf(accent);

        TextView tv1 = findViewById(R.id.tvHeaderRealtimeMonitor);
        TextView tv2 = findViewById(R.id.tvHeaderSystemDiagnostics);
        TextView tv3 = findViewById(R.id.tvHeaderLatencyPreset);
        TextView tv4 = findViewById(R.id.tvHeaderRadioPush);

        if (tv1 != null) tv1.setTextColor(accent);
        if (tv2 != null) tv2.setTextColor(accent);
        if (tv3 != null) tv3.setTextColor(accent);
        if (tv4 != null) tv4.setTextColor(accent);

        if (tvRttLatencyStats != null) tvRttLatencyStats.setTextColor(accent);

        TextView tvWatermark = findViewById(R.id.tvWatermarkDiag);
        if (tvWatermark != null) tvWatermark.setTextColor(accent);

        RadioButton rb1 = findViewById(R.id.rbLatencyUltraLow);
        RadioButton rb2 = findViewById(R.id.rbLatencyBalanced);
        RadioButton rb3 = findViewById(R.id.rbLatencyHigh);

        if (rb1 != null) rb1.setButtonTintList(csl);
        if (rb2 != null) rb2.setButtonTintList(csl);
        if (rb3 != null) rb3.setButtonTintList(csl);

        if (btnSendRadioToReceiver != null) {
            btnSendRadioToReceiver.setBackgroundTintList(csl);
            double luminance = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255.0;
            btnSendRadioToReceiver.setTextColor(luminance > 0.5 ? Color.parseColor("#0F172A") : Color.WHITE);
        }

        if (latencyChartView != null) {
            latencyChartView.setAccentColor(accent);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(statusChecker);
        unregisterReceiver(statusReceiver);
        radioMetadataSender.close();
        ThemeManager.getInstance(this).unregisterListener(this);
        super.onDestroy();
    }
}
