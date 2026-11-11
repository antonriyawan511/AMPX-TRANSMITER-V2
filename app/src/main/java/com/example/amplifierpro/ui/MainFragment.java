package com.example.amplifierpro.ui;

import com.example.amplifierpro.service.AudioStreamService;
import com.example.amplifierpro.security.LicenseManager;
import com.example.amplifierpro.R;
import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.provider.Settings;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.util.Enumeration;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import android.content.res.ColorStateList;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainFragment extends Fragment implements ThemeManager.OnThemeChangeListener {

    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    private static final String PREFS_NAME = "AMPLIFIER_SETTINGS";
    private static final String KEY_MANUAL_IP = "manual_ip";
    private static final String KEY_SAVED_IPS = "saved_ips_list";
    private static final String KEY_IP_NAME_PREFIX = "ip_name_";
    private static final String KEY_IP_RENAMED_FLAG_PREFIX = "ip_renamed_";
    private static final String KEY_IGNORED_UPDATE_VERSION = "ignored_update_version";

    private static class UpdateInfo {
        boolean active = false;
        String version = "";
        String changelog = "";
        String urlRx = "";
        String urlTx = "";
    }

    private UpdateInfo currentUpdateInfo = null;

    private Button btnToggleStream;
    private TextView tvStatus;
    private TextView tvWifiSignal;
    private EditText etManualIpMain;
    private Button btnOkIp, btnScanQrMain, btnHotspotMain, btnAutoDiscoverMain;
    private LinearLayout layoutSavedIps;
    private androidx.core.widget.NestedScrollView mainScrollView;
    private TextView tvSavedReceiversLabel;
    private TextView badgeHiFi;
    private TextView btnUpdateAvailable;
    private TextView tvAudioFormatBadge;
    private TextView tvReceiverHwBadge;

    private View viewGlow;
    private View viewVisualizer1;
    private ObjectAnimator pulseAnimator;
    private ObjectAnimator vizAnimator1;
    private ObjectAnimator buttonPulseAnimator;
    private ObjectAnimator updatePulseAnimator;

    private MediaProjectionManager projectionManager;
    private WifiManager wifiManager;
    private String currentSelectedIp = "";

    // Shared with Activity or kept static as before if needed
    public static Intent pendingProjectionData = null;
    public static int pendingResultCode = 0;

    private ScheduledExecutorService wifiExecutor;
    private final Handler handlerMain = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver streamStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (AudioStreamService.ACTION_STREAM_STATE_CHANGED.equals(action)) {
                updateUIState();
            } else if (AudioStreamService.ACTION_RECEIVER_NAME_DISCOVERED.equals(action)) {
                String ip = intent.getStringExtra("receiver_ip");
                String name = intent.getStringExtra("receiver_name");
                if (ip != null && name != null) {
                    handleDiscoveredName(ip, name);
                }
            } else if (AudioStreamService.ACTION_IP_DISCOVERED.equals(action)) {
                String ip = intent.getStringExtra(AudioStreamService.EXTRA_RECEIVER_IP);
                String name = intent.getStringExtra(AudioStreamService.EXTRA_RECEIVER_NAME);
                if (ip != null) {
                    currentSelectedIp = ip;
                    if (etManualIpMain != null) {
                        etManualIpMain.setText(ip);
                    }
                    saveIpToList(ip);
                    if (name != null) {
                        handleDiscoveredName(ip, name);
                    }
                }
            } else if (AudioStreamService.ACTION_TRIAL_EXPIRED.equals(action)) {
                updateUIState();
                Toast.makeText(context, "⏳ Masa Uji Coba (30 Detik) Selesai. Silakan pesan Lisensi PRO!", Toast.LENGTH_LONG).show();
                LicenseDialog.show(context, MainFragment.this::updateBadgeHiFi);
            } else if (AudioStreamService.ACTION_TRIAL_OFFLINE_ERROR.equals(action)) {
                updateUIState();
                Toast.makeText(context, "⚠️ Mode Uji Coba membutuhkan koneksi internet!", Toast.LENGTH_LONG).show();
                LicenseDialog.show(context, MainFragment.this::updateBadgeHiFi);
            } else if (com.example.amplifierpro.manager.EqSettingsManager.ACTION_RECEIVER_HW_CHANGED.equals(action) ||
                       com.example.amplifierpro.manager.EqSettingsManager.ACTION_AUDIO_STREAM_MODE_CHANGED.equals(action)) {
                updateAudioBadges();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadSettings();
        setupAnimations();
        refreshSavedIpsUI();
        ThemeManager.getInstance(requireContext()).registerListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ThemeManager.getInstance(requireContext()).unregisterListener(this);
    }

    @Override
    public void onThemeChanged(ThemePalette theme) {
        applyTheme(theme);
    }

    public void applyTheme(ThemePalette theme) {
        if (theme == null || getView() == null || getContext() == null) return;
        View root = getView();
        root.setBackgroundColor(theme.backgroundColor);

        ColorStateList accentCsl = ColorStateList.valueOf(theme.accentColor);

        if (tvWifiSignal != null) {
            tvWifiSignal.setTextColor(theme.accentColor);
        }
        if (badgeHiFi != null) {
            updateBadgeHiFi();
        }
        if (viewVisualizer1 != null) {
            viewVisualizer1.setBackgroundTintList(accentCsl);
        }
        if (btnOkIp != null) {
            btnOkIp.setBackgroundTintList(accentCsl);
        }
        if (tvSavedReceiversLabel != null) {
            tvSavedReceiversLabel.setTextColor(theme.accentColor);
        }
        View btnExit = root.findViewById(R.id.btnExitApp);
        if (btnExit instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) btnExit).setColorFilter(theme.accentColor);
        }
        View btnSettings = root.findViewById(R.id.btnStreamingSettings);
        if (btnSettings instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) btnSettings).setColorFilter(theme.accentColor);
        }
        if (btnUpdateAvailable != null) {
            btnUpdateAvailable.setTextColor(theme.accentColor);
            int alphaAccent = Color.argb(32, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor));
            btnUpdateAvailable.setBackgroundTintList(ColorStateList.valueOf(alphaAccent));
        }

        updateUIState();
        refreshSavedIpsUI();
    }

    private void handleDiscoveredName(String ip, String name) {
        if (getContext() == null || ip == null || name == null) return;
        String cleanName = name.trim();
        if (cleanName.isEmpty() || cleanName.equalsIgnoreCase("Receiver") || cleanName.equalsIgnoreCase(ip)) {
            return;
        }
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRenamed = prefs.getBoolean(KEY_IP_RENAMED_FLAG_PREFIX + ip, false);

        if (!isRenamed) {
            String current = prefs.getString(KEY_IP_NAME_PREFIX + ip, "");
            if (!cleanName.equals(current)) {
                prefs.edit().putString(KEY_IP_NAME_PREFIX + ip, cleanName).apply();
                refreshSavedIpsUI();
            }
        }
    }

    private void initViews(View view) {
        btnToggleStream = view.findViewById(R.id.btnToggleStream);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvWifiSignal = view.findViewById(R.id.tvWifiSignal);
        tvAudioFormatBadge = view.findViewById(R.id.tvAudioFormatBadge);
        tvReceiverHwBadge = view.findViewById(R.id.tvReceiverHwBadge);
        etManualIpMain = view.findViewById(R.id.etManualIpMain);
        btnOkIp = view.findViewById(R.id.btnOkIp);
        btnScanQrMain = view.findViewById(R.id.btnScanQrMain);
        btnHotspotMain = view.findViewById(R.id.btnHotspotMain);
        btnAutoDiscoverMain = view.findViewById(R.id.btnAutoDiscoverMain);
        layoutSavedIps = view.findViewById(R.id.layoutSavedIps);
        mainScrollView = view.findViewById(R.id.mainScrollView);
        btnUpdateAvailable = view.findViewById(R.id.btnUpdateAvailable);
        View btnStreamingSettings = view.findViewById(R.id.btnStreamingSettings);
        if (btnStreamingSettings != null) {
            btnStreamingSettings.setOnClickListener(v -> StreamingSettingsDialog.show(requireContext(), this::updateAudioBadges));
        }
        updateAudioBadges();
        if (btnUpdateAvailable != null) {
            btnUpdateAvailable.setVisibility(View.GONE);
            btnUpdateAvailable.setOnClickListener(v -> showUpdateDialog());
        }
        if (mainScrollView != null) {
            final int touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
            mainScrollView.setOnTouchListener(new View.OnTouchListener() {
                private float startX = 0f;
                private float startY = 0f;
                private boolean isDetermined = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getX();
                            startY = event.getY();
                            isDetermined = false;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            if (!isDetermined) {
                                float dx = Math.abs(event.getX() - startX);
                                float dy = Math.abs(event.getY() - startY);
                                if (dx > touchSlop || dy > touchSlop) {
                                    isDetermined = true;
                                    if (dx > dy) {
                                        // Swipe horizontal -> Berikan ke ViewPager2 untuk geser halaman
                                        v.getParent().requestDisallowInterceptTouchEvent(false);
                                        return false;
                                    } else {
                                        // Scroll vertikal -> Kunci touch untuk scroll saved receivers
                                        v.getParent().requestDisallowInterceptTouchEvent(true);
                                    }
                                }
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            isDetermined = false;
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                    }
                    return false;
                }
            });
        }
        tvSavedReceiversLabel = view.findViewById(R.id.tvSavedReceiversLabel);
        badgeHiFi = view.findViewById(R.id.badgeHiFi);
        if (badgeHiFi != null) {
            badgeHiFi.setOnClickListener(v -> LicenseDialog.show(getContext(), this::updateBadgeHiFi));
        }
        updateBadgeHiFi();

        viewGlow = view.findViewById(R.id.viewGlow);
        viewVisualizer1 = view.findViewById(R.id.viewVisualizer1);

        projectionManager = (MediaProjectionManager) requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        btnToggleStream.setOnClickListener(v -> toggleStreaming());
        btnOkIp.setOnClickListener(v -> {
            String ip = etManualIpMain.getText().toString().trim();
            if (!ip.isEmpty()) selectIpAndConnect(ip);
        });
        btnScanQrMain.setOnClickListener(v -> startQrScan());
        if (btnHotspotMain != null) {
            btnHotspotMain.setOnClickListener(v -> openHotspotSettings());
        }
        btnAutoDiscoverMain.setOnClickListener(v -> {
            if (getContext() != null && LicenseManager.getInstance(requireContext()).isBlocked()) {
                Toast.makeText(getContext(), "❌ Perangkat ini telah diblokir oleh administrator", Toast.LENGTH_LONG).show();
                LicenseDialog.show(getContext(), this::updateBadgeHiFi);
                return;
            }
            etManualIpMain.setText("");
            currentSelectedIp = "";
            if (!AudioStreamService.isServiceRunning) {
                checkPermissionsAndStart();
            } else {
                Intent discoverIntent = new Intent(getContext(), AudioStreamService.class);
                discoverIntent.setAction(AudioStreamService.ACTION_TRIGGER_DISCOVERY);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(discoverIntent);
                } else {
                    requireContext().startService(discoverIntent);
                }
                Toast.makeText(getContext(), "Mencari Receiver...", Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.btnExitApp).setOnClickListener(v -> {
            stopStreaming();
            requireActivity().finishAffinity();
            System.exit(0);
        });
    }

    private void loadSettings() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentSelectedIp = prefs.getString(KEY_MANUAL_IP, "");
        etManualIpMain.setText(currentSelectedIp);
    }

    private void toggleStreaming() {
        if (!AudioStreamService.isServiceRunning) {
            if (getContext() != null && LicenseManager.getInstance(requireContext()).isBlocked()) {
                Toast.makeText(getContext(), "❌ Perangkat ini telah diblokir oleh administrator", Toast.LENGTH_LONG).show();
                LicenseDialog.show(getContext(), this::updateBadgeHiFi);
                return;
            }
            checkPermissionsAndStart();
        } else {
            stopStreaming();
        }
    }

    private void selectIpAndConnect(String ip) {
        if (ip == null || ip.trim().isEmpty()) return;
        if (getContext() != null && LicenseManager.getInstance(requireContext()).isBlocked()) {
            Toast.makeText(getContext(), "❌ Perangkat ini telah diblokir oleh administrator", Toast.LENGTH_LONG).show();
            LicenseDialog.show(getContext(), this::updateBadgeHiFi);
            return;
        }
        currentSelectedIp = ip;
        saveIpToList(ip);
        if (etManualIpMain != null) etManualIpMain.setText(ip);

        if (AudioStreamService.isServiceRunning) {
            Intent intent = new Intent(getContext(), AudioStreamService.class);
            intent.putExtra(AudioStreamService.EXTRA_MANUAL_IP, ip);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent);
            } else {
                requireContext().startService(intent);
            }
        } else {
            checkPermissionsAndStart();
        }
    }

    private void saveIpToList(String ip) {
        if (ip == null || ip.trim().isEmpty() || getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedStr = prefs.getString(KEY_SAVED_IPS, "");
        List<String> ips = new ArrayList<>();
        if (!savedStr.isEmpty()) {
            ips.addAll(Arrays.asList(savedStr.split(",")));
        }

        ips.remove("");
        ips.remove(ip);
        ips.add(0, ip);

        if (ips.size() > 20) {
            ips = new ArrayList<>(ips.subList(0, 20));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ips.size(); i++) {
            sb.append(ips.get(i));
            if (i < ips.size() - 1) sb.append(",");
        }
        prefs.edit().putString(KEY_SAVED_IPS, sb.toString()).putString(KEY_MANUAL_IP, ip).apply();
        refreshSavedIpsUI();
    }

    private void refreshSavedIpsUI() {
        if (layoutSavedIps == null || getContext() == null) return;
        layoutSavedIps.removeAllViews();
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedStr = prefs.getString(KEY_SAVED_IPS, "");
        if (savedStr.isEmpty()) return;

        String[] ips = savedStr.split(",");
        for (String ip : ips) {
            if (ip.trim().isEmpty()) continue;

            String displayName = prefs.getString(KEY_IP_NAME_PREFIX + ip, "");

            View itemView = getLayoutInflater().inflate(R.layout.item_saved_receiver, layoutSavedIps, false);
            TextView tvName = itemView.findViewById(R.id.tvReceiverName);
            TextView tvIp = itemView.findViewById(R.id.tvReceiverIp);

            if (displayName.isEmpty() || displayName.equals(ip) || displayName.equalsIgnoreCase("Receiver")) {
                tvName.setText(ip);
                tvIp.setVisibility(View.GONE);
            } else {
                tvName.setText(displayName);
                tvIp.setText(ip);
                tvIp.setVisibility(View.VISIBLE);
            }

            int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
            tvIp.setTextColor(accentColor);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 12);
            itemView.setLayoutParams(lp);

            itemView.setOnClickListener(v -> selectIpAndConnect(ip));
            itemView.findViewById(R.id.btnEditItem).setOnClickListener(v -> showEditReceiverDialog(ip));

            layoutSavedIps.addView(itemView);
        }
    }

    private void showEditReceiverDialog(String ip) {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentName = prefs.getString(KEY_IP_NAME_PREFIX + ip, "");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_receiver, null);
        EditText etName = dialogView.findViewById(R.id.etReceiverName);
        TextView tvLabel = dialogView.findViewById(R.id.tvDisplayNameLabel);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelDialog);
        Button btnSave = dialogView.findViewById(R.id.btnSaveDialog);

        int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
        if (tvLabel != null) tvLabel.setTextColor(accentColor);
        if (btnCancel != null) btnCancel.setTextColor(accentColor);
        if (btnSave != null) btnSave.setTextColor(accentColor);

        if (!currentName.isEmpty()) {
            etName.setText(currentName);
        } else {
            etName.setHint(ip);
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btnDeleteReceiver).setOnClickListener(v -> {
            dialog.dismiss();
            removeIpFromList(ip);
        });

        dialogView.findViewById(R.id.btnCancelDialog).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnSaveDialog).setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) {
                prefs.edit().remove(KEY_IP_NAME_PREFIX + ip).putBoolean(KEY_IP_RENAMED_FLAG_PREFIX + ip, false).apply();
            } else {
                prefs.edit().putString(KEY_IP_NAME_PREFIX + ip, newName).putBoolean(KEY_IP_RENAMED_FLAG_PREFIX + ip, true).apply();
            }
            refreshSavedIpsUI();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void removeIpFromList(String ip) {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedStr = prefs.getString(KEY_SAVED_IPS, "");
        List<String> ips = new ArrayList<>(Arrays.asList(savedStr.split(",")));
        ips.remove(ip);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ips.size(); i++) {
            String s = ips.get(i).trim();
            if (s.isEmpty()) continue;
            sb.append(s);
            if (i < ips.size() - 1) sb.append(",");
        }

        String result = sb.toString();
        if (result.endsWith(",")) result = result.substring(0, result.length() - 1);

        prefs.edit().putString(KEY_SAVED_IPS, result).remove(KEY_IP_NAME_PREFIX + ip).remove(KEY_IP_RENAMED_FLAG_PREFIX + ip).apply();
        refreshSavedIpsUI();
        Toast.makeText(getContext(), "IP dihapus", Toast.LENGTH_SHORT).show();
    }

    private void startQrScan() {
        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
        integrator.setCaptureActivity(PortraitCaptureActivity.class);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan QR di HP Musik");
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    private void setupAnimations() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(viewGlow,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f),
                PropertyValuesHolder.ofFloat("alpha", 0.08f, 0.18f));
        pulseAnimator.setDuration(3000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        buttonPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(btnToggleStream,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.02f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.02f));
        buttonPulseAnimator.setDuration(3000);
        buttonPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        buttonPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        buttonPulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        vizAnimator1 = ObjectAnimator.ofPropertyValuesHolder(viewVisualizer1,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.12f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.12f),
                PropertyValuesHolder.ofFloat("alpha", 0.2f, 0.0f));
        vizAnimator1.setDuration(2200);
        vizAnimator1.setRepeatCount(ValueAnimator.INFINITE);
        vizAnimator1.setInterpolator(new AccelerateDecelerateInterpolator());

        if (btnUpdateAvailable != null) {
            updatePulseAnimator = ObjectAnimator.ofPropertyValuesHolder(btnUpdateAvailable,
                    PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f),
                    PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f),
                    PropertyValuesHolder.ofFloat("alpha", 0.6f, 1.0f));
            updatePulseAnimator.setDuration(1200);
            updatePulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            updatePulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            updatePulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        }
    }

    private void checkForAppUpdate() {
        if (getContext() == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.net.URL url = new java.net.URL("https://antonimous.dsp-pro-license.workers.dev/api/update");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    boolean active = json.optBoolean("active", false);
                    String version = json.optString("version", "");
                    String changelog = json.optString("changelog", "");
                    String urlRx = json.optString("url_rx", "");
                    String urlTx = json.optString("url_tx", "");

                    if (getContext() == null) return;
                    SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String ignoredVersion = prefs.getString(KEY_IGNORED_UPDATE_VERSION, "");

                    handlerMain.post(() -> {
                        if (isAdded() && active && !version.isEmpty() && !version.equals(ignoredVersion)) {
                            currentUpdateInfo = new UpdateInfo();
                            currentUpdateInfo.active = active;
                            currentUpdateInfo.version = version;
                            currentUpdateInfo.changelog = changelog;
                            currentUpdateInfo.urlRx = urlRx;
                            currentUpdateInfo.urlTx = urlTx;

                            if (btnUpdateAvailable != null) {
                                btnUpdateAvailable.setText("🚀 UPDATE " + version);
                                btnUpdateAvailable.setVisibility(View.VISIBLE);
                                if (updatePulseAnimator != null && !updatePulseAnimator.isRunning()) {
                                    updatePulseAnimator.start();
                                }
                            }
                        } else {
                            if (btnUpdateAvailable != null) {
                                btnUpdateAvailable.setVisibility(View.GONE);
                                if (updatePulseAnimator != null) {
                                    updatePulseAnimator.cancel();
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("MainFragment", "Error checking update", e);
            }
        });
    }

    private void showUpdateDialog() {
        if (getContext() == null || currentUpdateInfo == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_update, null);

        TextView tvVersion = dialogView.findViewById(R.id.tvUpdateVersion);
        TextView tvChangelog = dialogView.findViewById(R.id.tvChangelogContent);
        Button btnUpdateRx = dialogView.findViewById(R.id.btnUpdateReceiver);
        Button btnUpdateTx = dialogView.findViewById(R.id.btnUpdateTransmitter);
        Button btnLater = dialogView.findViewById(R.id.btnUpdateLater);
        Button btnDontRemind = dialogView.findViewById(R.id.btnUpdateDontRemind);

        int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
        ColorStateList accentCsl = ColorStateList.valueOf(accentColor);

        View tvUpdateIconBg = dialogView.findViewById(R.id.tvUpdateIconBg);
        if (tvUpdateIconBg != null) {
            int alphaAccent = Color.argb(38, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
            tvUpdateIconBg.setBackgroundTintList(ColorStateList.valueOf(alphaAccent));
        }

        if (tvVersion != null) {
            tvVersion.setText(currentUpdateInfo.version.isEmpty() ? "Versi Terbaru" : "Versi " + currentUpdateInfo.version);
            tvVersion.setTextColor(accentColor);
        }
        if (tvChangelog != null && !currentUpdateInfo.changelog.isEmpty()) {
            tvChangelog.setText(currentUpdateInfo.changelog);
        }

        if (btnUpdateRx != null) {
            btnUpdateRx.setBackgroundTintList(accentCsl);
            btnUpdateRx.setOnClickListener(v -> {
                if (!currentUpdateInfo.urlRx.isEmpty()) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentUpdateInfo.urlRx)));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Gagal membuka link Receiver", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Link Receiver belum tersedia", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnUpdateTx != null) {
            btnUpdateTx.setTextColor(accentColor);
            if (btnUpdateTx instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) btnUpdateTx).setStrokeColor(accentCsl);
            }
            btnUpdateTx.setOnClickListener(v -> {
                if (!currentUpdateInfo.urlTx.isEmpty()) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentUpdateInfo.urlTx)));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Gagal membuka link Transmitter", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Link Transmitter belum tersedia", Toast.LENGTH_SHORT).show();
                }
            });
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        if (btnLater != null) {
            btnLater.setTextColor(accentColor);
            btnLater.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnDontRemind != null) {
            btnDontRemind.setOnClickListener(v -> {
                if (getContext() != null) {
                    SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putString(KEY_IGNORED_UPDATE_VERSION, currentUpdateInfo.version).apply();
                }
                if (btnUpdateAvailable != null) {
                    btnUpdateAvailable.setVisibility(View.GONE);
                }
                dialog.dismiss();
                Toast.makeText(getContext(), "Pembaruan versi " + currentUpdateInfo.version + " tidak akan diingatkan lagi.", Toast.LENGTH_SHORT).show();
            });
        }

        dialog.show();
    }

    private void startWifiMonitor() {
        if (wifiExecutor != null) wifiExecutor.shutdownNow();
        wifiExecutor = Executors.newSingleThreadScheduledExecutor();
        wifiExecutor.scheduleWithFixedDelay(() -> {
            if (wifiManager != null && getContext() != null) {
                try {
                    boolean hasPermission = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        hasPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED ||
                                       ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        hasPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    }

                    if (hasPermission) {
                        android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
                        int rssi = info.getRssi();
                        handlerMain.post(() -> updateWifiUI(rssi));
                    } else {
                        handlerMain.post(() -> tvWifiSignal.setText("Wi-Fi: PERM"));
                    }
                } catch (Exception e) {
                    handlerMain.post(() -> tvWifiSignal.setText("Wi-Fi: ERR"));
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private void updateWifiUI(int rssi) {
        if (tvWifiSignal == null) return;
        if (rssi == -127) {
            android.location.LocationManager lm = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnabled = false;
            try { gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            tvWifiSignal.setText(gpsEnabled ? "Wi-Fi: --" : "Wi-Fi: LOC OFF");
        } else if (rssi != 0) {
            int level = WifiManager.calculateSignalLevel(rssi, 100);
            tvWifiSignal.setText("Wi-Fi: " + level + "%");
        } else {
            tvWifiSignal.setText("Wi-Fi: DC");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            applyTheme(ThemeManager.getInstance(requireContext()).getCurrentTheme());
        }
        updateUIState();
        startWifiMonitor();
        checkForAppUpdate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioStreamService.ACTION_STREAM_STATE_CHANGED);
        filter.addAction(AudioStreamService.ACTION_RECEIVER_NAME_DISCOVERED);
        filter.addAction(AudioStreamService.ACTION_IP_DISCOVERED);
        filter.addAction(AudioStreamService.ACTION_TRIAL_EXPIRED);
        filter.addAction(AudioStreamService.ACTION_TRIAL_OFFLINE_ERROR);
        filter.addAction(com.example.amplifierpro.manager.EqSettingsManager.ACTION_RECEIVER_HW_CHANGED);
        filter.addAction(com.example.amplifierpro.manager.EqSettingsManager.ACTION_AUDIO_STREAM_MODE_CHANGED);

        ContextCompat.registerReceiver(requireContext(), streamStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        updateBadgeHiFi();
        updateAudioBadges();
        if (getContext() != null) {
            LicenseManager.getInstance(requireContext()).checkOnlineStatus((status, isPro, msg) -> {
                if (isAdded()) updateBadgeHiFi();
            });
        }

        // Logic to resume animations if this fragment is currently visible in ViewPager
        // TransmitterActivity will call a public method when page changes
        if (isResumed() && getUserVisibleHint()) {
            startAnimations();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(streamStateReceiver);
        if (wifiExecutor != null) wifiExecutor.shutdownNow();
        stopAnimations();
    }

    public void startAnimations() {
        if (pulseAnimator != null && !pulseAnimator.isRunning()) pulseAnimator.start();
        if (buttonPulseAnimator != null && !buttonPulseAnimator.isRunning()) buttonPulseAnimator.start();
        if (vizAnimator1 != null && !vizAnimator1.isRunning()) vizAnimator1.start();
        if (updatePulseAnimator != null && !updatePulseAnimator.isRunning()) updatePulseAnimator.start();
    }

    public void stopAnimations() {
        if (pulseAnimator != null) pulseAnimator.cancel();
        if (buttonPulseAnimator != null) buttonPulseAnimator.cancel();
        if (vizAnimator1 != null) vizAnimator1.cancel();
        if (updatePulseAnimator != null) updatePulseAnimator.cancel();
    }

    private void updateUIState() {
        if (tvStatus == null || getContext() == null) return;
        String status = AudioStreamService.lastStatus;
        if (status == null) status = "Siap untuk streaming";
        tvStatus.setText(status);

        ThemePalette theme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
        int colorAccent = theme.accentColor;
        int colorRed = Color.parseColor("#D50000");
        int colorYellow = Color.parseColor("#FFAB00");

        if (AudioStreamService.isServiceRunning) {
            btnToggleStream.setText("STOP");
            btnToggleStream.setBackgroundTintList(android.content.res.ColorStateList.valueOf("STREAMING AKTIF".equals(status) ? colorRed : colorYellow));
            viewGlow.setBackgroundTintList(android.content.res.ColorStateList.valueOf("STREAMING AKTIF".equals(status) ? colorRed : colorYellow));

            startAnimations();
            if ("STREAMING AKTIF".equals(status)) triggerKickEffect();
        } else {
            btnToggleStream.setText("START");
            btnToggleStream.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorAccent));
            viewGlow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorAccent));
            stopAnimations();
            viewGlow.setScaleX(1.0f); viewGlow.setScaleY(1.0f); viewGlow.setAlpha(0.1f);
            btnToggleStream.setScaleX(1.0f); btnToggleStream.setScaleY(1.0f);
        }
    }

    private void triggerKickEffect() {
        if (btnToggleStream == null) return;
        ObjectAnimator.ofPropertyValuesHolder(btnToggleStream,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.03f, 1.0f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.03f, 1.0f))
                .setDuration(400).start();
    }

    private void checkPermissionsAndStart() {
        if (getContext() != null && LicenseManager.getInstance(requireContext()).isBlocked()) {
            Toast.makeText(getContext(), "❌ Perangkat ini telah diblokir oleh administrator", Toast.LENGTH_LONG).show();
            LicenseDialog.show(getContext(), this::updateBadgeHiFi);
            return;
        }

        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        List<String> toRequest = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (!toRequest.isEmpty()) {
            requestPermissions(toRequest.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
        } else {
            startProjection();
        }
    }

    private void startProjection() {
        if (projectionManager != null) {
            if (pendingProjectionData != null) startAudioService(pendingResultCode, pendingProjectionData);
            else startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null && scanResult.getContents() != null) {
            selectIpAndConnect(scanResult.getContents().trim());
            return;
        }
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            pendingProjectionData = data; pendingResultCode = resultCode;
            startAudioService(resultCode, data);
        }
    }

    private void startAudioService(int resultCode, Intent data) {
        Intent intent = new Intent(getContext(), AudioStreamService.class);
        intent.putExtra("EXTRA_RESULT_DATA", data);
        intent.putExtra("EXTRA_RESULT_CODE", resultCode);
        if (!currentSelectedIp.isEmpty()) intent.putExtra(AudioStreamService.EXTRA_MANUAL_IP, currentSelectedIp);
        ContextCompat.startForegroundService(requireContext(), intent);
    }

    private void stopStreaming() {
        pendingProjectionData = null;
        pendingResultCode = -1;
        requireContext().stopService(new Intent(getContext(), AudioStreamService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean audioGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i]) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    audioGranted = true;
                }
            }
            if (audioGranted) startProjection();
            else Toast.makeText(getContext(), "Izin Audio diperlukan", Toast.LENGTH_SHORT).show();
            updateUIState();
        }
    }

    private void updateBadgeHiFi() {
        if (badgeHiFi == null || getContext() == null) return;
        LicenseManager lm = LicenseManager.getInstance(getContext());
        int color = ThemeManager.getInstance(getContext()).getCurrentTheme().accentColor;
        String text;

        if (lm.isProLicensed()) {
            text = "PRO";
        } else {
            String tier = lm.getActiveTier();
            if (lm.isClockSuspended()) {
                text = "SUSPEND";
            } else if ("PENDING".equalsIgnoreCase(tier)) {
                text = "PENDING";
            } else if (lm.isBlocked()) {
                text = "BLOCK";
            } else if (lm.isTrialActive()) {
                text = "TRIAL";
            } else {
                text = "FREE";
            }
        }

        badgeHiFi.setText(text);
        badgeHiFi.setTextColor(color);
        badgeHiFi.setBackgroundTintList(null);

        android.util.TypedValue outValue = new android.util.TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)) {
            badgeHiFi.setBackgroundResource(outValue.resourceId);
        } else {
            badgeHiFi.setBackground(null);
        }
    }

    private void updateAudioBadges() {
        if (tvAudioFormatBadge == null || tvReceiverHwBadge == null || getContext() == null) return;
        com.example.amplifierpro.manager.EqSettingsManager settings =
                new com.example.amplifierpro.manager.EqSettingsManager(getContext());
        int mode = settings.getAudioStreamMode();
        String activeIp = settings.getActiveReceiverIp();

        if (mode == com.example.amplifierpro.manager.EqSettingsManager.STREAM_MODE_PCM) {
            int effectiveSr = settings.getEffectiveSampleRate(activeIp);
            String srLabel = (effectiveSr == 44100) ? "44.1k" : "48.0k";
            tvAudioFormatBadge.setText("PCM " + srLabel + " (Bit-Perfect)");
        } else {
            tvAudioFormatBadge.setText("OPUS 48kHz (Kompresi)");
        }

        if (activeIp != null && !activeIp.isEmpty()) {
            int nativeSr = settings.getReceiverNativeSampleRate(activeIp);
            String nativeLabel = (nativeSr == 44100) ? "44.1k" : ((nativeSr == 48000) ? "48k" : (nativeSr / 1000) + "k");
            String supported = settings.getReceiverSupportedRates(activeIp);
            String supShort = supported.replace("44100", "44k")
                    .replace("48000", "48k")
                    .replace("88200", "88k")
                    .replace("96000", "96k")
                    .replace("192000", "192k");
            tvReceiverHwBadge.setVisibility(View.VISIBLE);
            tvReceiverHwBadge.setText("HW: " + nativeLabel + " Native (Sup: " + supShort + ")");
        } else {
            tvReceiverHwBadge.setVisibility(View.GONE);
        }
    }

    public boolean isTouchInSavedReceivers(float rawX, float rawY) {
        if (mainScrollView == null || !mainScrollView.isAttachedToWindow() || mainScrollView.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        mainScrollView.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int w = mainScrollView.getWidth();
        int h = mainScrollView.getHeight();
        return rawX >= x && rawX <= (x + w) && rawY >= y && rawY <= (y + h);
    }

    // ==========================================
    // HOTSPOT CONTROL & STATUS
    // ==========================================

    private void openHotspotSettings() {
        if (getContext() == null) return;
        try {
            Intent intent = new Intent("android.settings.TETHER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}

        try {
            Intent intent = new Intent("com.android.settings.WIFI_AP_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.TetherSettings"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        try {
            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception ignored) {}
    }


}
