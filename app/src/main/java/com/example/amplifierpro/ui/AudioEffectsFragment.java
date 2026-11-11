package com.example.amplifierpro.ui;

import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.service.AudioStreamService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.graphics.Color;
import android.net.Uri;
import com.example.amplifierpro.security.LicenseManager;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.WindowManager;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AudioEffectsFragment extends Fragment implements ThemeManager.OnThemeChangeListener {

    private EqSettingsManager settingsManager;
    private VolumePeakHelper volumePeakHelper;
    private ImageButton btnAlwaysOn;

    private final BroadcastReceiver alwaysOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && EqSettingsManager.ACTION_ALWAYS_ON_SCREEN_UPDATED.equals(intent.getAction())) {
                boolean enabled = intent.getBooleanExtra(EqSettingsManager.EXTRA_VALUE, false);
                if (getContext() != null) {
                    ThemePalette theme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
                    updateAlwaysOnUI(enabled, theme.accentColor);
                }
            }
        }
    };
    
    // Master Switch / Power Button
    private ImageButton btnPowerAudioEffects;
    private List<View> effectCards = new ArrayList<>();

    // Stereo Width
    private SwitchCompat switchStereoWidth;
    private SeekBar seekBarStereoWidth;
    private TextView textStereoWidthValue;
    private TextView textStereoWidthLabel;
    private Spinner spinnerStereoCutoff;

    // Virtual Bass
    private SwitchCompat switchVirtualBass;
    private SeekBar seekBarVirtualBass;
    private TextView textVirtualBassValue;
    private TextView textVirtualBassLabel;
    private Spinner spinnerVbMinFreq;
    private Spinner spinnerVbMaxFreq;

    // Compressor
    private SwitchCompat switchCompressor;
    private View layoutCompressorControls;
    private Spinner spinnerCompPreset;
    private SeekBar seekBarCompThreshold, seekBarCompRatio, seekBarCompAttack, seekBarCompRelease, seekBarCompMakeup;
    private TextView textCompThreshold, textCompRatio, textCompAttack, textCompRelease, textCompMakeup;
    private TextView textCompGR;
    private boolean isUpdatingCompFromPreset = false;

    // Limiter
    private SwitchCompat switchLimiter;
    private View layoutLimiterControls;
    private Spinner spinnerLimiterPreset, spinnerLimiterReleaseMode;
    private SeekBar seekBarLimiterCeiling, seekBarLimiterRelease, seekBarLimiterLookahead;
    private TextView textLimiterCeiling, textLimiterRelease, textLimiterLookahead;
    private TextView textLimiterGR;
    private boolean isUpdatingLimiterFromPreset = false;

    private final Handler grHandler = new Handler(Looper.getMainLooper());
    private final Runnable grRunnable = new Runnable() {
        @Override
        public void run() {
            updateGRMeters();
            grHandler.postDelayed(this, 100);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_audio_effects, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        settingsManager = new EqSettingsManager(requireContext());
        volumePeakHelper = new VolumePeakHelper(this);
        
        // Cache Effect Cards for global toggle
        effectCards.clear();
        View cardStereo = view.findViewById(R.id.cardStereo);
        View cardVBass = view.findViewById(R.id.cardVBass);
        View cardCompressor = view.findViewById(R.id.cardCompressor);
        View cardLimiter = view.findViewById(R.id.cardLimiter);
        if (cardStereo != null) effectCards.add(cardStereo);
        if (cardVBass != null) effectCards.add(cardVBass);
        if (cardCompressor != null) effectCards.add(cardCompressor);
        if (cardLimiter != null) effectCards.add(cardLimiter);

        // Master Audio Effects Toggle & Theme Button
        setupAlwaysOnButton(view);
        View btnTheme = view.findViewById(R.id.btnThemeAudioEffects);
        if (btnTheme instanceof android.widget.ImageButton) {
            android.widget.ImageButton ibTheme = (android.widget.ImageButton) btnTheme;
            ThemePalette cur = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            ibTheme.setColorFilter(cur.accentColor);
            ibTheme.setOnClickListener(v -> {
                ThemePalette newTheme = ThemeManager.getInstance(requireContext()).cycleTheme();
                android.widget.Toast.makeText(requireContext(), newTheme.name, android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        btnPowerAudioEffects = view.findViewById(R.id.btnPowerAudioEffects);
        if (btnPowerAudioEffects != null) {
            boolean isEffectsEnabled = settingsManager.isAudioEffectsEnabled();
            ThemePalette cur = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            updatePowerButtonUI(btnPowerAudioEffects, isEffectsEnabled, cur.accentColor);
            updateGlobalEffectsUI(isEffectsEnabled);
            
            btnPowerAudioEffects.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                boolean next = !settingsManager.isAudioEffectsEnabled();
                settingsManager.setAudioEffectsEnabled(next);
                ThemePalette theme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
                updatePowerButtonUI(btnPowerAudioEffects, next, theme.accentColor);
                updateGlobalEffectsUI(next);
            });
        }

        // Initialize UI component groups
        initStereoWidthUI(view);
        initVirtualBassUI(view);
        initCompressorUI(view);
        initLimiterUI(view);

        // Setup Logic
        setupStereoWidthUI();
        setupVirtualBassUI();
        setupCompressorUI();
        setupLimiterUI();
        
        volumePeakHelper.setup(view);
        ThemeManager.getInstance(requireContext()).registerListener(this);
        applyTheme(ThemeManager.getInstance(requireContext()).getCurrentTheme());
        updateProLockState();
    }

    private void updateGlobalEffectsUI(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.4f;
        for (View card : effectCards) {
            card.setAlpha(alpha);
            // Disable all interaction inside cards when global toggle is off
            disableViewAndChildren(card, enabled);
        }
        if (enabled) {
            // Restore proper switch states for child controls so disabled switches stay disabled
            if (switchStereoWidth != null) {
                boolean isStereo = switchStereoWidth.isChecked();
                if (seekBarStereoWidth != null) seekBarStereoWidth.setEnabled(isStereo);
                if (spinnerStereoCutoff != null) spinnerStereoCutoff.setEnabled(isStereo);
            }
            if (switchVirtualBass != null) {
                boolean isVb = switchVirtualBass.isChecked();
                if (seekBarVirtualBass != null) seekBarVirtualBass.setEnabled(isVb);
                if (spinnerVbMinFreq != null) spinnerVbMinFreq.setEnabled(isVb);
                if (spinnerVbMaxFreq != null) spinnerVbMaxFreq.setEnabled(isVb);
            }
            if (switchCompressor != null) {
                enableCompressorControls(switchCompressor.isChecked());
            }
            if (switchLimiter != null) {
                enableLimiterControls(switchLimiter.isChecked());
            }
        }
    }

    private void disableViewAndChildren(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableViewAndChildren(group.getChildAt(i), enabled);
            }
        }
    }

    private void initStereoWidthUI(View view) {
        switchStereoWidth = view.findViewById(R.id.switchStereoWidth);
        seekBarStereoWidth = view.findViewById(R.id.seekBarStereoWidth);
        textStereoWidthValue = view.findViewById(R.id.textStereoWidthValue);
        textStereoWidthLabel = view.findViewById(R.id.textStereoWidthLabel);
        spinnerStereoCutoff = view.findViewById(R.id.spinnerStereoCutoff);
    }

    private void initVirtualBassUI(View view) {
        switchVirtualBass = view.findViewById(R.id.switchVirtualBass);
        seekBarVirtualBass = view.findViewById(R.id.seekBarVirtualBass);
        textVirtualBassValue = view.findViewById(R.id.textVirtualBassValue);
        textVirtualBassLabel = view.findViewById(R.id.textVirtualBassLabel);
        spinnerVbMinFreq = view.findViewById(R.id.spinnerVbMinFreq);
        spinnerVbMaxFreq = view.findViewById(R.id.spinnerVbMaxFreq);
    }

    private void initCompressorUI(View view) {
        switchCompressor = view.findViewById(R.id.switchCompressor);
        layoutCompressorControls = view.findViewById(R.id.layoutCompressorControls);
        spinnerCompPreset = view.findViewById(R.id.spinnerCompressorPreset);
        seekBarCompThreshold = view.findViewById(R.id.seekBarCompThreshold);
        seekBarCompRatio = view.findViewById(R.id.seekBarCompRatio);
        seekBarCompAttack = view.findViewById(R.id.seekBarCompAttack);
        seekBarCompRelease = view.findViewById(R.id.seekBarCompRelease);
        seekBarCompMakeup = view.findViewById(R.id.seekBarCompMakeup);
        textCompThreshold = view.findViewById(R.id.textCompThresholdLabel);
        textCompRatio = view.findViewById(R.id.textCompRatioLabel);
        textCompAttack = view.findViewById(R.id.textCompAttackLabel);
        textCompRelease = view.findViewById(R.id.textCompReleaseLabel);
        textCompMakeup = view.findViewById(R.id.textCompMakeupLabel);
        textCompGR = view.findViewById(R.id.textCompGR);
    }

    private void initLimiterUI(View view) {
        switchLimiter = view.findViewById(R.id.switchLimiter);
        layoutLimiterControls = view.findViewById(R.id.layoutLimiterControls);
        spinnerLimiterPreset = view.findViewById(R.id.spinnerLimiterPreset);
        spinnerLimiterReleaseMode = view.findViewById(R.id.spinnerLimiterReleaseMode);
        seekBarLimiterCeiling = view.findViewById(R.id.seekBarLimiterCeiling);
        seekBarLimiterRelease = view.findViewById(R.id.seekBarLimiterRelease);
        seekBarLimiterLookahead = view.findViewById(R.id.seekBarLimiterLookahead);
        textLimiterCeiling = view.findViewById(R.id.textLimiterCeilingLabel);
        textLimiterRelease = view.findViewById(R.id.textLimiterReleaseLabel);
        textLimiterLookahead = view.findViewById(R.id.textLimiterLookaheadLabel);
        textLimiterGR = view.findViewById(R.id.textLimiterGR);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            ContextCompat.registerReceiver(
                requireContext(),
                alwaysOnReceiver,
                new IntentFilter(EqSettingsManager.ACTION_ALWAYS_ON_SCREEN_UPDATED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            );
            if (btnAlwaysOn != null && settingsManager != null) {
                ThemePalette cur = ThemeManager.getInstance(requireContext()).getCurrentTheme();
                updateAlwaysOnUI(settingsManager.isAlwaysOnScreen(), cur.accentColor);
            }
            applyTheme(ThemeManager.getInstance(requireContext()).getCurrentTheme());
            updateProLockState();
        }
        if (volumePeakHelper != null) volumePeakHelper.onResume();
        grHandler.post(grRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            try {
                requireContext().unregisterReceiver(alwaysOnReceiver);
            } catch (Exception ignored) {}
        }
        if (volumePeakHelper != null) volumePeakHelper.onPause();
        grHandler.removeCallbacks(grRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ThemeManager.getInstance(requireContext()).unregisterListener(this);
        if (volumePeakHelper != null) volumePeakHelper.onDestroyView();
        btnAlwaysOn = null;
    }

    @Override
    public void onThemeChanged(ThemePalette theme) {
        applyTheme(theme);
    }

    public void applyTheme(ThemePalette theme) {
        if (theme == null || getView() == null || getContext() == null) return;
        View root = getView();
        root.setBackgroundColor(theme.backgroundColor);

        View btnTheme = root.findViewById(R.id.btnThemeAudioEffects);
        if (btnTheme instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) btnTheme).setColorFilter(theme.accentColor);
        }

        ColorStateList thumbList = new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{-android.R.attr.state_checked}},
            new int[]{theme.accentColor, 0xFF888888}
        );
        ColorStateList trackList = new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{-android.R.attr.state_checked}},
            new int[]{(theme.accentColor & 0x00FFFFFF) | 0x80000000, 0x33888888}
        );

        SwitchCompat[] switches = new SwitchCompat[]{
            switchStereoWidth, switchVirtualBass, switchCompressor, switchLimiter
        };
        for (SwitchCompat sw : switches) {
            if (sw != null) {
                sw.setThumbTintList(thumbList);
                sw.setTrackTintList(trackList);
            }
        }

        if (btnPowerAudioEffects != null && settingsManager != null) {
            updatePowerButtonUI(btnPowerAudioEffects, settingsManager.isAudioEffectsEnabled(), theme.accentColor);
        }

        ColorStateList accentCsl = ColorStateList.valueOf(theme.accentColor);
        SeekBar[] seekBars = new SeekBar[]{
            seekBarStereoWidth, seekBarVirtualBass,
            seekBarCompThreshold, seekBarCompRatio, seekBarCompAttack, seekBarCompRelease, seekBarCompMakeup,
            seekBarLimiterCeiling, seekBarLimiterRelease, seekBarLimiterLookahead
        };
        for (SeekBar sb : seekBars) {
            if (sb != null) {
                sb.setProgressTintList(accentCsl);
                sb.setThumbTintList(accentCsl);
            }
        }

        if (textStereoWidthLabel != null) {
            textStereoWidthLabel.setTextColor(theme.accentColor);
        }
        if (textVirtualBassLabel != null) {
            textVirtualBassLabel.setTextColor(theme.accentColor);
        }

        if (volumePeakHelper != null) {
            volumePeakHelper.applyTheme(theme);
        }

        if (btnAlwaysOn != null && settingsManager != null) {
            updateAlwaysOnUI(settingsManager.isAlwaysOnScreen(), theme.accentColor);
        }

        Spinner[] spinners = new Spinner[]{
            spinnerStereoCutoff,
            spinnerVbMinFreq, spinnerVbMaxFreq,
            spinnerCompPreset,
            spinnerLimiterPreset, spinnerLimiterReleaseMode
        };
        for (Spinner sp : spinners) {
            tintSpinnerArrow(sp, theme.accentColor);
        }

        updateProLockState();
    }

    private void updatePowerButtonUI(ImageButton btn, boolean isEnabled, int accentColor) {
        if (btn == null || getContext() == null) return;
        float density = requireContext().getResources().getDisplayMetrics().density;
        if (isEnabled) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            int fillColor = (0x33 << 24) | (accentColor & 0x00FFFFFF);
            gd.setColor(fillColor);
            gd.setStroke((int) (1.5f * density), accentColor);
            btn.setBackground(gd);
            btn.setColorFilter(accentColor);
            btn.setAlpha(1.0f);
        } else {
            btn.setBackgroundResource(R.drawable.bg_theme_btn);
            btn.setColorFilter(0x88FFFFFF);
            btn.setAlpha(0.45f);
        }
    }

    private void updateProLockState() {
        View root = getView();
        if (root == null || getContext() == null) return;
        View overlay = root.findViewById(R.id.proLockOverlay);
        if (overlay == null) return;

        LicenseManager lm = LicenseManager.getInstance(requireContext());
        if (lm.isDspAllowed()) {
            overlay.setVisibility(View.GONE);
            return;
        }

        // Tampilkan overlay pelindung etalase
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        overlay.setOnTouchListener((v, event) -> true); // Blokir seluruh sentuhan ke kontrol DSP di bawah

        ImageView ivLockIcon = overlay.findViewById(R.id.ivLockIcon);
        TextView tvLockTitle = overlay.findViewById(R.id.tvLockTitle);
        TextView tvLockSubtitle = overlay.findViewById(R.id.tvLockSubtitle);
        com.google.android.material.button.MaterialButton btnUnlockPro = overlay.findViewById(R.id.btnUnlockPro);
        TextView tvLockFooter = overlay.findViewById(R.id.tvLockFooter);

        if (lm.isBlocked()) {
            if (ivLockIcon != null) ivLockIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
            if (tvLockTitle != null) {
                tvLockTitle.setText("PERANGKAT DIBLOKIR");
                tvLockTitle.setTextColor(Color.parseColor("#EF4444"));
            }
            if (tvLockSubtitle != null) {
                tvLockSubtitle.setText("Akses audio dan fitur DSP pada perangkat ini telah diblokir oleh administrator.");
            }
            if (btnUnlockPro != null) {
                btnUnlockPro.setText("HUBUNGI ADMIN VIA WHATSAPP");
                btnUnlockPro.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                btnUnlockPro.setTextColor(Color.WHITE);
                btnUnlockPro.setOnClickListener(v -> {
                    String hwid = com.example.amplifierpro.security.DeviceSecurityManager.getDeviceId(requireContext());
                    String msg = "Halo Admin, perangkat saya (ID: " + hwid + ") terblokir. Mohon bantuannya.";
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=6285167326085&text=" + Uri.encode(msg)));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                });
            }
            if (tvLockFooter != null) {
                tvLockFooter.setText("Streaming audio dinonaktifkan untuk perangkat yang diblokir.");
                tvLockFooter.setTextColor(Color.parseColor("#EF4444"));
            }
        } else {
            int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
            if (ivLockIcon != null) ivLockIcon.setImageTintList(ColorStateList.valueOf(accentColor));
            if (tvLockTitle != null) {
                tvLockTitle.setText("FITUR DSP PRO TERKUNCI");
                tvLockTitle.setTextColor(Color.WHITE);
            }
            if (tvLockSubtitle != null) {
                tvLockSubtitle.setText("Buka Equalizer 10-Band, Virtual Bass, Limiter, dan Crossover Receiver seumur hidup.");
            }
            if (btnUnlockPro != null) {
                String priceStr = getContext() != null ? LicenseManager.getInstance(getContext()).getFormattedPrice() : "Rp 35.000";
                btnUnlockPro.setText("AKTIFKAN PRO (" + priceStr + ")");
                btnUnlockPro.setBackgroundTintList(ColorStateList.valueOf(accentColor));
                double luminance = (0.299 * Color.red(accentColor) + 0.587 * Color.green(accentColor) + 0.114 * Color.blue(accentColor)) / 255.0;
                btnUnlockPro.setTextColor(luminance > 0.5 ? Color.parseColor("#0F172A") : Color.WHITE);
                btnUnlockPro.setOnClickListener(v -> LicenseDialog.show(getContext(), this::updateProLockState));
            }
            if (tvLockFooter != null) {
                tvLockFooter.setText("Audio streaming Lossless tetap aktif gratis selamanya.");
                tvLockFooter.setTextColor(Color.parseColor("#64748B"));
            }
        }
    }

    private void tintSpinnerArrow(Spinner spinner, int color) {
        if (spinner == null) return;
        Drawable bg = spinner.getBackground();
        if (bg != null) {
            bg = bg.mutate();
            if (bg instanceof LayerDrawable) {
                LayerDrawable ld = (LayerDrawable) bg;
                Drawable arrow = ld.findDrawableByLayerId(R.id.spinnerArrow);
                if (arrow == null && ld.getNumberOfLayers() > 1) {
                    arrow = ld.getDrawable(1);
                }
                if (arrow != null) {
                    arrow = arrow.mutate();
                    arrow.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                    arrow.setTint(color);
                }
            } else {
                bg.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                bg.setTint(color);
            }
            spinner.invalidate();
        }
    }

    private void setupAlwaysOnButton(View root) {
        btnAlwaysOn = root.findViewById(R.id.btnAlwaysOnAudioEffects);
        if (btnAlwaysOn != null) {
            ThemePalette cur = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            boolean isAlwaysOn = settingsManager.isAlwaysOnScreen();
            updateAlwaysOnUI(isAlwaysOn, cur.accentColor);

            btnAlwaysOn.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                boolean current = settingsManager.isAlwaysOnScreen();
                boolean next = !current;
                settingsManager.setAlwaysOnScreen(next);
                if (getActivity() instanceof TransmitterActivity) {
                    ((TransmitterActivity) getActivity()).applyAlwaysOnScreen(next);
                } else if (getActivity() != null) {
                    if (next) {
                        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
                ThemePalette theme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
                updateAlwaysOnUI(next, theme.accentColor);
                android.widget.Toast.makeText(
                    requireContext(),
                    next ? "Layar Selalu Menyala: Aktif" : "Layar Selalu Menyala: Nonaktif",
                    android.widget.Toast.LENGTH_SHORT
                ).show();
            });
        }
    }

    private void updateAlwaysOnUI(boolean isAlwaysOn, int accentColor) {
        if (btnAlwaysOn == null || getContext() == null) return;
        float density = requireContext().getResources().getDisplayMetrics().density;
        if (isAlwaysOn) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            int fillColor = (0x33 << 24) | (accentColor & 0x00FFFFFF);
            gd.setColor(fillColor);
            gd.setStroke((int) (1.5f * density), accentColor);
            btnAlwaysOn.setBackground(gd);
            btnAlwaysOn.setColorFilter(accentColor);
            btnAlwaysOn.setAlpha(1.0f);
        } else {
            btnAlwaysOn.setBackgroundResource(R.drawable.bg_theme_btn);
            btnAlwaysOn.setColorFilter(0x88FFFFFF);
            btnAlwaysOn.setAlpha(0.5f);
        }
    }

    private void updateGRMeters() {
        if (textCompGR != null) {
            float gr = AudioStreamService.getCompressorGR();
            if (gr > 0.05f) {
                textCompGR.setText(String.format(Locale.US, "GR: -%.1f dB", gr));
                textCompGR.setTextColor(getResources().getColor(R.color.accent_yellow, null));
                textCompGR.setAlpha(1.0f);
            } else {
                textCompGR.setText("GR: 0.0 dB");
                textCompGR.setTextColor(getResources().getColor(R.color.white, null));
                textCompGR.setAlpha(0.4f);
            }
        }

        if (textLimiterGR != null) {
            float gr = AudioStreamService.getLimiterGR();
            if (gr > 0.05f) {
                textLimiterGR.setText(String.format(Locale.US, "GR: -%.1f dB", gr));
                textLimiterGR.setTextColor(getResources().getColor(R.color.accent_yellow, null));
                textLimiterGR.setAlpha(1.0f);
            } else {
                textLimiterGR.setText("GR: 0.0 dB");
                textLimiterGR.setTextColor(getResources().getColor(R.color.white, null));
                textLimiterGR.setAlpha(0.4f);
            }
        }
    }

    private void setupStereoWidthUI() {
        if (switchStereoWidth == null) return;
        boolean isEnabled = settingsManager.isStereoWidthEnabled();
        int widthValue = settingsManager.getStereoWidthValue();

        switchStereoWidth.setChecked(isEnabled);
        seekBarStereoWidth.setProgress(widthValue);
        updateStereoWidthTexts(widthValue);
        
        SeekBar parentLayout = seekBarStereoWidth;
        if (parentLayout != null) parentLayout.setEnabled(isEnabled);
        if (spinnerStereoCutoff != null) spinnerStereoCutoff.setEnabled(isEnabled);

        final int[] cutoffValues = {0, 80, 120, 160, 200, 250};
        final String[] cutoffLabels = {
            "Normal (Full)",
            "Mono < 80 Hz",
            "Mono < 120 Hz",
            "Mono < 160 Hz",
            "Mono < 200 Hz",
            "Mono < 250 Hz"
        };

        if (spinnerStereoCutoff != null) {
            ArrayAdapter<String> cutoffAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, cutoffLabels) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextSize(10.5f);
                        ((TextView) v).setTextColor(Color.WHITE);
                        ((TextView) v).setSingleLine(true);
                    }
                    return v;
                }

                @Override
                public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextSize(12.5f);
                        if (getContext() != null) {
                            int saved = settingsManager.getStereoCutoffFreq();
                            if (cutoffValues[position] == saved) {
                                ((TextView) v).setTextColor(ThemeManager.getInstance(getContext()).getCurrentTheme().accentColor);
                            } else {
                                ((TextView) v).setTextColor(Color.WHITE);
                            }
                        }
                    }
                    return v;
                }
            };
            cutoffAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerStereoCutoff.setAdapter(cutoffAdapter);

            int savedCutoff = settingsManager.getStereoCutoffFreq();
            int selectedIndex = 2; // Default Mono < 120 Hz
            for (int i = 0; i < cutoffValues.length; i++) {
                if (cutoffValues[i] == savedCutoff) {
                    selectedIndex = i;
                    break;
                }
            }
            spinnerStereoCutoff.setSelection(selectedIndex);

            spinnerStereoCutoff.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    int chosenCutoff = cutoffValues[position];
                    if (chosenCutoff != settingsManager.getStereoCutoffFreq()) {
                        settingsManager.setStereoCutoffFreq(chosenCutoff);
                        spinnerStereoCutoff.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        switchStereoWidth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setStereoWidthEnabled(isChecked);
            seekBarStereoWidth.setEnabled(isChecked);
            if (spinnerStereoCutoff != null) spinnerStereoCutoff.setEnabled(isChecked);
        });

        seekBarStereoWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int lastSnappedPoint = -1;
            private final int[] snapPoints = {0, 50, 100, 140, 180};
            private static final int SNAP_THRESHOLD = 6;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int targetProgress = progress;
                    boolean snapped = false;
                    for (int point : snapPoints) {
                        if (Math.abs(progress - point) <= SNAP_THRESHOLD) {
                            targetProgress = point;
                            snapped = true;
                            if (lastSnappedPoint != point) {
                                seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                                lastSnappedPoint = point;
                            }
                            break;
                        }
                    }
                    if (!snapped) lastSnappedPoint = -1;
                    if (targetProgress != progress) seekBar.setProgress(targetProgress);
                    settingsManager.setStereoWidthValue(targetProgress);
                    updateStereoWidthTexts(targetProgress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateStereoWidthTexts(int value) {
        if (textStereoWidthValue != null) textStereoWidthValue.setText(String.format(Locale.US, "%d%%", value));
        if (textStereoWidthLabel != null) {
            String label;
            if (value == 0) label = "Mono";
            else if (value < 100) label = "Narrow";
            else if (value == 100) label = "Normal Stereo";
            else if (value < 150) label = "Wide";
            else label = "Very Wide";
            textStereoWidthLabel.setText(label);
        }
    }

    private void setupVirtualBassUI() {
        if (switchVirtualBass == null) return;
        boolean isEnabled = settingsManager.isVirtualBassEnabled();
        int bassAmount = settingsManager.getVirtualBassAmount();
        int minFreq = settingsManager.getVirtualBassMinFreq();
        int maxFreq = settingsManager.getVirtualBassMaxFreq();

        switchVirtualBass.setChecked(isEnabled);
        seekBarVirtualBass.setProgress(bassAmount);
        updateVirtualBassTexts(bassAmount);
        
        seekBarVirtualBass.setEnabled(isEnabled);
        if (spinnerVbMinFreq != null) spinnerVbMinFreq.setEnabled(isEnabled);
        if (spinnerVbMaxFreq != null) spinnerVbMaxFreq.setEnabled(isEnabled);

        List<Integer> minFreqs = new ArrayList<>();
        for (int f = 20; f <= 100; f += 5) minFreqs.add(f);
        ArrayAdapter<Integer> minAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, minFreqs);
        minAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVbMinFreq.setAdapter(minAdapter);
        spinnerVbMinFreq.setSelection(minFreqs.indexOf(minFreq));

        List<Integer> maxFreqs = new ArrayList<>();
        for (int f = 50; f <= 200; f += 5) maxFreqs.add(f);
        ArrayAdapter<Integer> maxAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, maxFreqs);
        maxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVbMaxFreq.setAdapter(maxAdapter);
        spinnerVbMaxFreq.setSelection(maxFreqs.indexOf(maxFreq));

        switchVirtualBass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setVirtualBassEnabled(isChecked);
            seekBarVirtualBass.setEnabled(isChecked);
            if (spinnerVbMinFreq != null) spinnerVbMinFreq.setEnabled(isChecked);
            if (spinnerVbMaxFreq != null) spinnerVbMaxFreq.setEnabled(isChecked);
        });

        seekBarVirtualBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int lastSnappedPoint = -1;
            private final int[] snapPoints = {0, 25, 50, 75, 100};
            private static final int SNAP_THRESHOLD = 5;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int targetProgress = progress;
                    boolean snapped = false;
                    for (int point : snapPoints) {
                        if (Math.abs(progress - point) <= SNAP_THRESHOLD) {
                            targetProgress = point;
                            snapped = true;
                            if (lastSnappedPoint != point) {
                                seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                                lastSnappedPoint = point;
                            }
                            break;
                        }
                    }
                    if (!snapped) lastSnappedPoint = -1;
                    if (targetProgress != progress) seekBar.setProgress(targetProgress);
                    settingsManager.setVirtualBassAmount(targetProgress);
                    updateVirtualBassTexts(targetProgress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        spinnerVbMinFreq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedMin = (int) parent.getItemAtPosition(position);
                int currentMax = (int) spinnerVbMaxFreq.getSelectedItem();
                if (selectedMin >= currentMax) {
                    int newMax = selectedMin + 5;
                    if (newMax > 200) newMax = 200;
                    spinnerVbMaxFreq.setSelection(maxFreqs.indexOf(newMax));
                    settingsManager.setVirtualBassMaxFreq(newMax);
                }
                settingsManager.setVirtualBassMinFreq(selectedMin);
                spinnerVbMinFreq.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerVbMaxFreq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedMax = (int) parent.getItemAtPosition(position);
                int currentMin = (int) spinnerVbMinFreq.getSelectedItem();
                if (selectedMax <= currentMin) {
                    int newMin = selectedMax - 5;
                    if (newMin < 20) newMin = 20;
                    spinnerVbMinFreq.setSelection(minFreqs.indexOf(newMin));
                    settingsManager.setVirtualBassMinFreq(newMin);
                }
                settingsManager.setVirtualBassMaxFreq(selectedMax);
                spinnerVbMaxFreq.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateVirtualBassTexts(int value) {
        if (textVirtualBassValue != null) textVirtualBassValue.setText(String.format(Locale.US, "%d%%", value));
        if (textVirtualBassLabel != null) {
            String label;
            if (value == 0) label = "Off";
            else if (value < 30) label = "Subtle";
            else if (value < 60) label = "Moderate";
            else if (value < 80) label = "Strong";
            else label = "Extreme";
            textVirtualBassLabel.setText(label);
        }
    }

    private void setupCompressorUI() {
        if (switchCompressor == null) return;
        boolean isEnabled = settingsManager.isCompressorEnabled();
        switchCompressor.setChecked(isEnabled);
        enableCompressorControls(isEnabled);

        switchCompressor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setCompressorEnabled(isChecked);
            enableCompressorControls(isChecked);
        });

        String[] presets = {"OFF", "Soft", "Balanced", "Vocal", "Punchy", "Smooth", "Music", "Strong", "Heavy", "Limiting", "Custom"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCompPreset.setAdapter(adapter);

        float threshold = settingsManager.getCompressorThreshold();
        float ratio = settingsManager.getCompressorRatio();
        float attack = settingsManager.getCompressorAttack();
        float release = settingsManager.getCompressorRelease();
        float makeup = settingsManager.getCompressorMakeup();
        String currentPreset = settingsManager.getCompressorPreset();

        isUpdatingCompFromPreset = true;
        updateCompressorSeekBars(threshold, ratio, attack, release, makeup);
        int presetIndex = Arrays.asList(presets).indexOf(currentPreset);
        spinnerCompPreset.setSelection(presetIndex >= 0 ? presetIndex : 10);
        isUpdatingCompFromPreset = false;

        spinnerCompPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingCompFromPreset) return;
                String selected = presets[position];
                if (!selected.equals("Custom")) {
                    isUpdatingCompFromPreset = true;
                    applyCompressorPreset(selected);
                    isUpdatingCompFromPreset = false;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        SeekBar.OnSeekBarChangeListener compListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (!isUpdatingCompFromPreset) spinnerCompPreset.setSelection(10); // Custom
                    saveCompressorParams();
                }
                updateCompressorLabels();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekBarCompThreshold.setOnSeekBarChangeListener(compListener);
        seekBarCompRatio.setOnSeekBarChangeListener(compListener);
        seekBarCompAttack.setOnSeekBarChangeListener(compListener);
        seekBarCompRelease.setOnSeekBarChangeListener(compListener);
        seekBarCompMakeup.setOnSeekBarChangeListener(compListener);
        updateCompressorLabels();
    }

    private void enableCompressorControls(boolean enabled) {
        spinnerCompPreset.setEnabled(enabled);
        seekBarCompThreshold.setEnabled(enabled);
        seekBarCompRatio.setEnabled(enabled);
        seekBarCompAttack.setEnabled(enabled);
        seekBarCompRelease.setEnabled(enabled);
        seekBarCompMakeup.setEnabled(enabled);
    }

    private void applyCompressorPreset(String preset) {
        float t = -12, r = 2, a = 20, rel = 150, m = 0;
        switch (preset) {
            case "OFF": t = 0; r = 1.0f; a = 20; rel = 150; m = 0; break;
            case "Soft": t = -12; r = 2.0f; a = 20; rel = 150; m = 0; break;
            case "Balanced": t = -18; r = 4.0f; a = 10; rel = 200; m = 2.0f; break;
            case "Vocal": t = -20; r = 3.0f; a = 12; rel = 100; m = 1.0f; break;
            case "Punchy": t = -18; r = 4.0f; a = 30; rel = 100; m = 1.0f; break;
            case "Smooth": t = -15; r = 2.5f; a = 25; rel = 250; m = 1.0f; break;
            case "Music": t = -18; r = 3.0f; a = 20; rel = 180; m = 1.0f; break;
            case "Strong": t = -24; r = 8.0f; a = 5; rel = 300; m = 4.0f; break;
            case "Heavy": t = -26; r = 8.0f; a = 5; rel = 150; m = 2.0f; break;
            case "Limiting": t = -6; r = 20.0f; a = 1; rel = 100; m = 0; break;
        }
        updateCompressorSeekBars(t, r, a, rel, m);
        settingsManager.setCompressorParams(t, r, a, rel, m, preset);
    }

    private void updateCompressorSeekBars(float t, float r, float a, float rel, float m) {
        seekBarCompThreshold.setProgress((int) (t + 40));
        seekBarCompRatio.setProgress((int) ((r - 1.0f) * 10));
        seekBarCompAttack.setProgress((int) (a - 1));
        seekBarCompRelease.setProgress((int) (rel - 20));
        seekBarCompMakeup.setProgress((int) (m * 10));
    }

    private void saveCompressorParams() {
        float t = seekBarCompThreshold.getProgress() - 40;
        float r = 1.0f + seekBarCompRatio.getProgress() / 10.0f;
        float a = 1 + seekBarCompAttack.getProgress();
        float rel = 20 + seekBarCompRelease.getProgress();
        float m = seekBarCompMakeup.getProgress() / 10.0f;
        String preset = (String) spinnerCompPreset.getSelectedItem();
        settingsManager.setCompressorParams(t, r, a, rel, m, preset);
    }

    private void updateCompressorLabels() {
        float t = seekBarCompThreshold.getProgress() - 40;
        float r = 1.0f + seekBarCompRatio.getProgress() / 10.0f;
        float a = 1 + seekBarCompAttack.getProgress();
        float rel = 20 + seekBarCompRelease.getProgress();
        float m = seekBarCompMakeup.getProgress() / 10.0f;
        textCompThreshold.setText(String.format(Locale.US, "Thresh: %.0fdB", t));
        textCompRatio.setText(String.format(Locale.US, "Ratio: %.1f", r));
        textCompAttack.setText(String.format(Locale.US, "Atk: %.0fms", a));
        textCompRelease.setText(String.format(Locale.US, "Rel: %.0fms", rel));
        textCompMakeup.setText(String.format(Locale.US, "Makeup: %.0fdB", m));
    }

    private void setupLimiterUI() {
        if (switchLimiter == null) return;
        boolean isEnabled = settingsManager.isLimiterEnabled();
        switchLimiter.setChecked(isEnabled);
        enableLimiterControls(isEnabled);

        switchLimiter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setLimiterEnabled(isChecked);
            enableLimiterControls(isChecked);
        });

        String[] presets = {"Safe", "Balanced", "Loud", "Custom"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLimiterPreset.setAdapter(adapter);

        String[] releaseModes = {"AUTO", "MANUAL"};
        ArrayAdapter<String> rmAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, releaseModes);
        rmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLimiterReleaseMode.setAdapter(rmAdapter);

        float ceiling = settingsManager.getLimiterCeiling();
        String releaseMode = settingsManager.getLimiterReleaseMode();
        float release = settingsManager.getLimiterReleaseManual();
        float lookahead = settingsManager.getLimiterLookahead();
        String currentPreset = settingsManager.getLimiterPreset();

        isUpdatingLimiterFromPreset = true;
        updateLimiterSeekBars(ceiling, releaseMode, release, lookahead);
        int presetIndex = Arrays.asList(presets).indexOf(currentPreset);
        spinnerLimiterPreset.setSelection(presetIndex >= 0 ? presetIndex : 3);
        int rmIndex = Arrays.asList(releaseModes).indexOf(releaseMode);
        spinnerLimiterReleaseMode.setSelection(rmIndex >= 0 ? rmIndex : 0);
        isUpdatingLimiterFromPreset = false;

        spinnerLimiterPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingLimiterFromPreset) return;
                String selected = presets[position];
                if (!selected.equals("Custom")) {
                    isUpdatingLimiterFromPreset = true;
                    applyLimiterPreset(selected);
                    isUpdatingLimiterFromPreset = false;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerLimiterReleaseMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingLimiterFromPreset) return;
                spinnerLimiterPreset.setSelection(3); // Custom
                saveLimiterParams();
                updateLimiterLabels();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        SeekBar.OnSeekBarChangeListener limiterListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (!isUpdatingLimiterFromPreset) spinnerLimiterPreset.setSelection(3); // Custom
                    saveLimiterParams();
                }
                updateLimiterLabels();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekBarLimiterCeiling.setOnSeekBarChangeListener(limiterListener);
        seekBarLimiterRelease.setOnSeekBarChangeListener(limiterListener);
        seekBarLimiterLookahead.setOnSeekBarChangeListener(limiterListener);
        updateLimiterLabels();
    }

    private void enableLimiterControls(boolean enabled) {
        spinnerLimiterPreset.setEnabled(enabled);
        spinnerLimiterReleaseMode.setEnabled(enabled);
        seekBarLimiterCeiling.setEnabled(enabled);
        seekBarLimiterRelease.setEnabled(enabled);
        seekBarLimiterLookahead.setEnabled(enabled);
    }

    private void applyLimiterPreset(String preset) {
        float c = -1.0f, r = 200, l = 3.0f;
        String rm = "AUTO";
        switch (preset) {
            case "Safe": c = -2.0f; rm = "AUTO"; l = 5.0f; break;
            case "Balanced": c = -1.0f; rm = "AUTO"; l = 3.0f; break;
            case "Loud": c = -0.5f; rm = "AUTO"; l = 5.0f; break;
        }
        updateLimiterSeekBars(c, rm, r, l);
        settingsManager.setLimiterParams(c, rm, r, l, preset);
        int rmIndex = rm.equals("AUTO") ? 0 : 1;
        spinnerLimiterReleaseMode.setSelection(rmIndex);
    }

    private void updateLimiterSeekBars(float c, String rm, float r, float l) {
        seekBarLimiterCeiling.setProgress((int) ((c + 6.0f) * 10));
        seekBarLimiterRelease.setProgress((int) (r - 20));
        seekBarLimiterLookahead.setProgress((int) (l * 10));
    }

    private void saveLimiterParams() {
        float c = (seekBarLimiterCeiling.getProgress() / 10.0f) - 6.0f;
        String rm = (String) spinnerLimiterReleaseMode.getSelectedItem();
        float r = 20 + seekBarLimiterRelease.getProgress();
        float l = seekBarLimiterLookahead.getProgress() / 10.0f;
        String preset = (String) spinnerLimiterPreset.getSelectedItem();
        settingsManager.setLimiterParams(c, rm, r, l, preset);
    }

    private void updateLimiterLabels() {
        float c = (seekBarLimiterCeiling.getProgress() / 10.0f) - 6.0f;
        String rm = (String) spinnerLimiterReleaseMode.getSelectedItem();
        float r = 20 + seekBarLimiterRelease.getProgress();
        float l = seekBarLimiterLookahead.getProgress() / 10.0f;
        textLimiterCeiling.setText(String.format(Locale.US, "Ceil: %.1fdB", c));
        textLimiterLookahead.setText(String.format(Locale.US, "Look: %.1fms", l));
        boolean isManual = "MANUAL".equals(rm);
        seekBarLimiterRelease.setEnabled(isManual && switchLimiter.isChecked());
        textLimiterRelease.setText(isManual ? String.format(Locale.US, "Rel: %.0fms", r) : "Rel: AUTO");
    }
}
