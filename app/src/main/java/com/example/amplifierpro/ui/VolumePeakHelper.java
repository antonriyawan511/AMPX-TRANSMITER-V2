package com.example.amplifierpro.ui;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;
import com.example.amplifierpro.service.AudioStreamService;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class VolumePeakHelper {

    // Registry of all active VolumePeakHelper instances for instant in-memory sync
    private static final Set<VolumePeakHelper> activeHelpers = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean isReceiverMode = false;
    private static long lastGlobalKeyTime = 0;

    public static boolean isReceiverModeActive() {
        return isReceiverMode;
    }

    public static void handleVolumeKey(Context context, int delta) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastGlobalKeyTime < 100) return;
        lastGlobalKeyTime = now;

        for (VolumePeakHelper helper : activeHelpers) {
            if (helper != null) {
                if (isReceiverMode) {
                    helper.stepReceiverVolume(delta);
                } else {
                    helper.stepMasterVolume(delta > 0 ? 5 : -5);
                }
                return;
            }
        }

        // Fallback jika tidak ada UI helper aktif di memory
        EqSettingsManager manager = new EqSettingsManager(context);
        if (isReceiverMode) {
            String ip = manager.getActiveReceiverIp();
            int max = manager.getReceiverVolMax(ip);
            if (max > 0) {
                int current = manager.getReceiverVolumeIndex(ip);
                int next = Math.max(0, Math.min(current + delta, max));
                manager.setReceiverVolumeIndex(ip, next);
                syncReceiverVolumeToOtherHelpers(null, next, max);
            } else {
                int currentPct = manager.getReceiverVolumePercent(ip);
                int nextPct = Math.max(0, Math.min(100, currentPct + (delta > 0 ? 7 : -7)));
                manager.applyReceiverVolumePercentReport(ip, nextPct);
                syncReceiverVolumeToOtherHelpers(null, nextPct);
                manager.requestReceiverVolumeCaps();
            }
            manager.sendReceiverVolStep(delta);
        } else {
            int current = manager.getMasterVolume();
            int next = Math.max(0, Math.min(100, current + (delta > 0 ? 5 : -5)));
            manager.updateMasterVolume(next);
            syncMasterVolumeToOtherHelpers(null, next);
        }
    }

    private final Fragment fragment;
    private final EqSettingsManager settingsManager;
    private final Handler meterHandler = new Handler(Looper.getMainLooper());

    private SeekBar seekBarMaster;
    private TextView tvMasterVolumeValue;
    private TextView tvReceiverVolumeValue;
    private TextView tvMasterVolumeLabel;
    private TextView tvReceiverVolumeLabel;
    private View btnReceiverPlus;
    private View btnReceiverMinus;

    private boolean isUpdatingMasterVolume = false;
    private ValueAnimator receiverVolAnimator;
    private boolean isReceiverRegistered = false;

    private View peakMeterLeft, peakMeterRight, peakHoldLeft, peakHoldRight;
    private View peakParentL, peakParentR;
    private VuGradientDrawable vuDrawableL, vuDrawableR;
    private float visualPeakL = 0.0f, visualPeakR = 0.0f;
    private float holdPeakL = 0.0f, holdPeakR = 0.0f;
    private int holdTimerL = 0, holdTimerR = 0;

    private final Runnable meterRunnable = new Runnable() {
        @Override
        public void run() {
            updatePeakMeters();
            meterHandler.postDelayed(this, 16);
        }
    };

    private final BroadcastReceiver volumeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (EqSettingsManager.ACTION_VOLUME_UPDATED.equals(action)) {
                int volume = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 100);
                applyMasterVolume(volume);
            } else if (EqSettingsManager.ACTION_RECEIVER_VOLUME_UPDATED.equals(action)) {
                int value = intent.getIntExtra(EqSettingsManager.EXTRA_VALUE, 0);
                int max = intent.getIntExtra(EqSettingsManager.EXTRA_RECEIVER_VOL_MAX, 0);
                if (max > 0) {
                    updateReceiverVolumeUI(value, max);
                } else {
                    // Update langsung dari persentase (REC_VOL_PCT atau format lama)
                    updateReceiverVolumeUI(value);
                }
            }
        }
    };

    public VolumePeakHelper(Fragment fragment) {
        this.fragment = fragment;
        this.settingsManager = new EqSettingsManager(fragment.requireContext());
    }

    public void setup(View root) {
        activeHelpers.add(this);
        setupVolumeControls(root);
        setupPeakMeters(root);
        registerReceiver();
        applyTheme(ThemeManager.getInstance(fragment.requireContext()).getCurrentTheme());
    }

    public void applyTheme(ThemePalette theme) {
        if (theme == null) return;
        updateVolumeModeUI();
        if (seekBarMaster != null) {
            seekBarMaster.setProgressTintList(ColorStateList.valueOf(theme.accentColor));
            seekBarMaster.setThumbTintList(ColorStateList.valueOf(android.graphics.Color.WHITE));
        }
        if (vuDrawableL != null) {
            vuDrawableL.setColors(theme.vuBaseColor, theme.accentColor);
        }
        if (vuDrawableR != null) {
            vuDrawableR.setColors(theme.vuBaseColor, theme.accentColor);
        }
        if (peakParentL != null && peakParentL.getBackground() != null) {
            Drawable bg = peakParentL.getBackground().mutate();
            if (bg instanceof GradientDrawable) {
                ((GradientDrawable) bg).setColor(theme.vuInactiveColor);
            }
        }
        if (peakParentR != null && peakParentR.getBackground() != null) {
            Drawable bg = peakParentR.getBackground().mutate();
            if (bg instanceof GradientDrawable) {
                ((GradientDrawable) bg).setColor(theme.vuInactiveColor);
            }
        }
        if (peakHoldLeft != null) {
            peakHoldLeft.setBackgroundColor(theme.accentColor);
        }
        if (peakHoldRight != null) {
            peakHoldRight.setBackgroundColor(theme.accentColor);
        }
    }

    public static void applyThemeToAll(ThemePalette theme) {
        for (VolumePeakHelper helper : activeHelpers) {
            if (helper != null) {
                helper.applyTheme(theme);
            }
        }
    }

    private void setupVolumeControls(View root) {
        seekBarMaster = root.findViewById(R.id.seekBarMasterVolume);
        tvMasterVolumeValue = root.findViewById(R.id.tvMasterVolumeValue);
        tvMasterVolumeLabel = root.findViewById(R.id.tvMasterVolumeLabel);
        tvReceiverVolumeLabel = root.findViewById(R.id.tvReceiverVolumeLabel);
        tvReceiverVolumeValue = root.findViewById(R.id.tvReceiverVolumeValue);
        btnReceiverPlus = root.findViewById(R.id.btnReceiverPlus);
        btnReceiverMinus = root.findViewById(R.id.btnReceiverMinus);

        if (seekBarMaster != null) {
            // Slider tidak bisa dikontrol manual (drag/geser ditiadakan).
            // Mengetuk slider akan berpindah mode antara Master Volume dan Receiver Volume.
            seekBarMaster.setOnTouchListener(new View.OnTouchListener() {
                private float downX, downY;
                private long downTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getX();
                            downY = event.getY();
                            downTime = SystemClock.uptimeMillis();
                            return true; // Consume touch agar SeekBar tidak drag manual

                        case MotionEvent.ACTION_UP:
                            long duration = SystemClock.uptimeMillis() - downTime;
                            float dx = Math.abs(event.getX() - downX);
                            float dy = Math.abs(event.getY() - downY);
                            float density = v.getResources().getDisplayMetrics().density;
                            if (duration < 500 && dx < 20 * density && dy < 20 * density) {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                toggleVolumeMode();
                            }
                            return true;

                        case MotionEvent.ACTION_MOVE:
                        case MotionEvent.ACTION_CANCEL:
                            return true; // Ignore drag
                    }
                    return true;
                }
            });
        }

        View.OnClickListener toggleClick = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            toggleVolumeMode();
        };
        if (tvMasterVolumeLabel != null) tvMasterVolumeLabel.setOnClickListener(toggleClick);
        if (tvMasterVolumeValue != null) tvMasterVolumeValue.setOnClickListener(toggleClick);
        if (tvReceiverVolumeLabel != null) tvReceiverVolumeLabel.setOnClickListener(toggleClick);
        if (tvReceiverVolumeValue != null) tvReceiverVolumeValue.setOnClickListener(toggleClick);

        if (btnReceiverPlus != null) {
            btnReceiverPlus.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (isReceiverMode) {
                    stepReceiverVolume(+1);
                } else {
                    stepMasterVolume(+5);
                }
            });
        }
        if (btnReceiverMinus != null) {
            btnReceiverMinus.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (isReceiverMode) {
                    stepReceiverVolume(-1);
                } else {
                    stepMasterVolume(-5);
                }
            });
        }

        updateVolumeModeUI();
    }

    private void stepMasterVolume(int delta) {
        int current = settingsManager.getMasterVolume();
        int next = Math.max(0, Math.min(100, current + delta));
        if (next == current) return;
        settingsManager.updateMasterVolume(next);
        applyMasterVolume(next);
        syncMasterVolumeToOtherHelpers(this, next);
    }

    public void toggleVolumeMode() {
        isReceiverMode = !isReceiverMode;
        if (fragment.getContext() != null) {
            Toast.makeText(fragment.requireContext(),
                    isReceiverMode ? "Mode: Receiver Volume" : "Mode: Master Volume",
                    Toast.LENGTH_SHORT).show();
        }
        for (VolumePeakHelper helper : activeHelpers) {
            if (helper != null) {
                helper.updateVolumeModeUI();
            }
        }
    }

    public void updateVolumeModeUI() {
        if (fragment.getContext() == null) return;
        ThemePalette theme = ThemeManager.getInstance(fragment.requireContext()).getCurrentTheme();
        int accentColor = theme != null ? theme.accentColor : Color.parseColor("#38BDF8");
        int dimmedColor = Color.parseColor("#80FFFFFF");

        String ip = settingsManager.getActiveReceiverIp();
        int recPct = settingsManager.getReceiverVolumePercent(ip);
        int masterVol = settingsManager.getMasterVolume();

        if (isReceiverMode) {
            if (tvMasterVolumeLabel != null) {
                tvMasterVolumeLabel.setText("RECEIVER VOLUME");
                tvMasterVolumeLabel.setTextColor(accentColor);
            }
            if (tvMasterVolumeValue != null) {
                tvMasterVolumeValue.setText(recPct + "%");
                tvMasterVolumeValue.setTextColor(accentColor);
            }
            if (seekBarMaster != null) {
                isUpdatingMasterVolume = true;
                seekBarMaster.setProgress(recPct);
                isUpdatingMasterVolume = false;
            }
            if (tvReceiverVolumeLabel != null) {
                tvReceiverVolumeLabel.setVisibility(View.VISIBLE);
                tvReceiverVolumeLabel.setText("Master Vol: " + masterVol + "%");
                tvReceiverVolumeLabel.setTextColor(dimmedColor);
            }
        } else {
            if (tvMasterVolumeLabel != null) {
                tvMasterVolumeLabel.setText(R.string.master_volume);
                tvMasterVolumeLabel.setTextColor(accentColor);
            }
            if (tvMasterVolumeValue != null) {
                tvMasterVolumeValue.setText(masterVol + "%");
                tvMasterVolumeValue.setTextColor(accentColor);
            }
            if (seekBarMaster != null) {
                isUpdatingMasterVolume = true;
                seekBarMaster.setProgress(masterVol);
                isUpdatingMasterVolume = false;
            }
            if (tvReceiverVolumeLabel != null) {
                tvReceiverVolumeLabel.setVisibility(View.VISIBLE);
                tvReceiverVolumeLabel.setText("Receiver Vol: " + recPct + "%");
                tvReceiverVolumeLabel.setTextColor(dimmedColor);
            }
        }
        if (tvReceiverVolumeValue != null) {
            tvReceiverVolumeValue.setVisibility(View.GONE);
        }
    }

    /**
     * Satu penekanan = SATU step volume perangkat.
     *
     * Sebelumnya satu tap mengirim DUA perintah (VOL:<persen> + VOL_UP) sehingga volume
     * receiver naik 2-3x per penekanan. Sekarang hanya satu perintah relatif yang
     * bertanda nomor urut (VOL_STEP:<seq>:<delta>) sehingga paket duplikat UDP dibuang
     * oleh receiver.
     *
     * Nilai ditampilkan dari UMPAN BALIK receiver (index/max), bukan ditebak dari +5%.
     */
    private long lastStepClickTime = 0;

    private void stepReceiverVolume(int delta) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastStepClickTime < 120) {
            // Anti-bounce bila tombol fisik/layar tertekan jitter sangat cepat (<120ms)
            return;
        }
        lastStepClickTime = now;

        String ip = settingsManager.getActiveReceiverIp();
        int max = settingsManager.getReceiverVolMax(ip);

        if (max <= 0) {
            // Optimistic UI dengan perkiraan step Android (max 15)
            int currentPct = settingsManager.getReceiverVolumePercent(ip);
            int nextPct = Math.max(0, Math.min(100, currentPct + (delta > 0 ? 7 : -7)));
            
            // Simpan estimasi secara lokal sebagai persentase agar UI responsif
            settingsManager.applyReceiverVolumePercentReport(ip, nextPct);
            updateReceiverVolumeUI(nextPct);
            syncReceiverVolumeToOtherHelpers(this, nextPct);
            
            settingsManager.requestReceiverVolumeCaps();
            settingsManager.sendReceiverVolStep(delta);
            return;
        }

        int current = settingsManager.getReceiverVolumeIndex(ip);
        int next = Math.max(0, Math.min(current + delta, max));
        if (next == current) return; // sudah di ujung batas minimum atau maksimum

        // Optimistic lokal (satuan step) langsung update UI seketika tanpa delay
        settingsManager.setReceiverVolumeIndex(ip, next);
        updateReceiverVolumeUI(next, max);
        syncReceiverVolumeToOtherHelpers(this, next, max);
        settingsManager.sendReceiverVolStep(delta);
    }

    private void setupPeakMeters(View root) {
        peakMeterLeft = root.findViewById(R.id.peakMeterLeft);
        peakMeterRight = root.findViewById(R.id.peakMeterRight);
        peakHoldLeft = root.findViewById(R.id.peakHoldLeft);
        peakHoldRight = root.findViewById(R.id.peakHoldRight);
        if (peakMeterLeft != null) peakParentL = (View) peakMeterLeft.getParent();
        if (peakMeterRight != null) peakParentR = (View) peakMeterRight.getParent();

        ThemePalette currentTheme = ThemeManager.getInstance(root.getContext()).getCurrentTheme();
        int baseColor = currentTheme != null ? currentTheme.vuBaseColor : Color.WHITE;
        int accentColor = currentTheme != null ? currentTheme.accentColor : Color.parseColor("#FF6600");
        float density = root.getResources().getDisplayMetrics().density;
        float radius = 3f * density;

        vuDrawableL = new VuGradientDrawable(baseColor, accentColor, radius);
        vuDrawableR = new VuGradientDrawable(baseColor, accentColor, radius);

        if (peakMeterLeft != null) {
            ClipDrawable clipL = new ClipDrawable(vuDrawableL, Gravity.BOTTOM, ClipDrawable.VERTICAL);
            peakMeterLeft.setBackground(clipL);
        }
        if (peakMeterRight != null) {
            ClipDrawable clipR = new ClipDrawable(vuDrawableR, Gravity.BOTTOM, ClipDrawable.VERTICAL);
            peakMeterRight.setBackground(clipR);
        }
    }

    private static void syncMasterVolumeToOtherHelpers(VolumePeakHelper source, int progress) {
        for (VolumePeakHelper helper : activeHelpers) {
            if (helper != null && helper != source) {
                helper.applyMasterVolume(progress);
            }
        }
    }

    private static void syncReceiverVolumeToOtherHelpers(VolumePeakHelper source, int index, int max) {
        for (VolumePeakHelper helper : activeHelpers) {
            if (helper != null && helper != source) {
                helper.updateReceiverVolumeUI(index, max);
            }
        }
    }

    private static void syncReceiverVolumeToOtherHelpers(VolumePeakHelper source, int percent) {
        for (VolumePeakHelper helper : activeHelpers) {
            if (helper != null && helper != source) {
                helper.updateReceiverVolumeUI(percent);
            }
        }
    }

    public void applyMasterVolume(int progress) {
        if (!isReceiverMode) {
            if (seekBarMaster != null && seekBarMaster.getProgress() != progress) {
                isUpdatingMasterVolume = true;
                seekBarMaster.setProgress(progress);
                isUpdatingMasterVolume = false;
            }
            if (tvMasterVolumeValue != null) {
                tvMasterVolumeValue.setText(progress + "%");
            }
        } else {
            if (tvReceiverVolumeValue != null) {
                tvReceiverVolumeValue.setText(progress + "%");
            }
        }
    }

    public void syncFromSettings() {
        updateVolumeModeUI();
    }

    private int currentDisplayedReceiverVol = -1;

    public void updateReceiverVolumeUI(int volume) {
        if (isReceiverMode) {
            if (seekBarMaster != null && seekBarMaster.getProgress() != volume) {
                isUpdatingMasterVolume = true;
                seekBarMaster.setProgress(volume);
                isUpdatingMasterVolume = false;
            }
            if (tvMasterVolumeValue != null) {
                animateVolumeText(tvMasterVolumeValue, volume);
            }
        } else {
            if (tvReceiverVolumeValue != null) {
                animateVolumeText(tvReceiverVolumeValue, volume);
            }
        }
    }

    /** Tampilkan volume dari index/max perangkat (satuan step) sebagai persen. */
    public void updateReceiverVolumeUI(int index, int max) {
        int percent = (max > 0) ? (int) Math.round(index * 100.0 / max) : index;
        updateReceiverVolumeUI(percent);
    }

    private void animateVolumeText(TextView textView, int targetValue) {
        if (textView == null) return;
        targetValue = Math.max(0, Math.min(100, targetValue));
        currentDisplayedReceiverVol = targetValue;
        textView.setText(targetValue + "%");
    }

    private void registerReceiver() {
        if (!isReceiverRegistered && fragment.getContext() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(EqSettingsManager.ACTION_VOLUME_UPDATED);
            filter.addAction(EqSettingsManager.ACTION_RECEIVER_VOLUME_UPDATED);
            ContextCompat.registerReceiver(
                fragment.requireContext(),
                volumeBroadcastReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            );
            isReceiverRegistered = true;
        }
    }

    private void unregisterReceiver() {
        if (isReceiverRegistered && fragment.getContext() != null) {
            try {
                fragment.requireContext().unregisterReceiver(volumeBroadcastReceiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
    }

    private void updatePeakMeters() {
        if (peakMeterLeft == null || peakMeterRight == null) return;
        float targetPeakL = AudioStreamService.getLeftPeak();
        float targetPeakR = AudioStreamService.getRightPeak();
        float attackCoeff = 0.5f, releaseCoeff = 0.965f;
        if (targetPeakL > visualPeakL) visualPeakL += (targetPeakL - visualPeakL) * attackCoeff;
        else visualPeakL *= releaseCoeff;
        if (targetPeakR > visualPeakR) visualPeakR += (targetPeakR - visualPeakR) * attackCoeff;
        else visualPeakR *= releaseCoeff;

        if (targetPeakL >= holdPeakL) { holdPeakL = targetPeakL; holdTimerL = 60; }
        else { if (holdTimerL > 0) holdTimerL--; else holdPeakL *= 0.96f; }
        if (targetPeakR >= holdPeakR) { holdPeakR = targetPeakR; holdTimerR = 60; }
        else { if (holdTimerR > 0) holdTimerR--; else holdPeakR *= 0.96f; }

        if (peakMeterLeft.getBackground() != null) peakMeterLeft.getBackground().setLevel((int) (visualPeakL * 10000));
        if (peakMeterRight.getBackground() != null) peakMeterRight.getBackground().setLevel((int) (visualPeakR * 10000));
        int totalHeight = peakMeterLeft.getHeight();
        if (totalHeight > 0 && peakHoldLeft != null && peakHoldRight != null) {
            peakHoldLeft.setTranslationY(-holdPeakL * totalHeight);
            peakHoldRight.setTranslationY(-holdPeakR * totalHeight);
            peakHoldLeft.setAlpha(holdPeakL > 0.01f ? 0.8f : 0.0f);
            peakHoldRight.setAlpha(holdPeakR > 0.01f ? 0.8f : 0.0f);
        }
        float currentAlpha = visualPeakL > 0.001f ? 1.0f : 0.4f;
        if (peakParentL != null && Math.abs(peakParentL.getAlpha() - currentAlpha) > 0.05f) {
            peakParentL.setAlpha(currentAlpha);
            if (peakParentR != null) peakParentR.setAlpha(currentAlpha);
        }
    }

    public void onResume() {
        registerReceiver();
        syncFromSettings();
        String ip = settingsManager.getActiveReceiverIp();
        if (settingsManager.getReceiverVolMax(ip) <= 0) {
            settingsManager.requestReceiverVolumeCaps();
        }
        meterHandler.post(meterRunnable);
        if (fragment.getContext() != null) {
            applyTheme(ThemeManager.getInstance(fragment.requireContext()).getCurrentTheme());
        }
    }

    public void onPause() {
        meterHandler.removeCallbacks(meterRunnable);
    }

    public void onDestroyView() {
        unregisterReceiver();
        activeHelpers.remove(this);
        meterHandler.removeCallbacks(meterRunnable);
        if (receiverVolAnimator != null) {
            receiverVolAnimator.cancel();
            receiverVolAnimator = null;
        }
        currentDisplayedReceiverVol = -1;
        seekBarMaster = null;
        tvMasterVolumeValue = null;
        tvReceiverVolumeValue = null;
        tvMasterVolumeLabel = null;
        tvReceiverVolumeLabel = null;
        btnReceiverPlus = null;
        btnReceiverMinus = null;
        peakMeterLeft = null;
        peakMeterRight = null;
        peakHoldLeft = null;
        peakHoldRight = null;
        peakParentL = null;
        peakParentR = null;
        vuDrawableL = null;
        vuDrawableR = null;
    }
}
