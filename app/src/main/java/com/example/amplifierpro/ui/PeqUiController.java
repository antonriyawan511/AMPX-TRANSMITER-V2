package com.example.amplifierpro.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;

import java.util.Locale;

/**
 * Controller specifically coordinating the 4-band Parametric EQ (PEQ) UI.
 * Handles focus-based band selection, card data displays, 3 large horizontal control sliders,
 * and real-time graph synchronization.
 */
public class PeqUiController {

    public static final int PEQ_BANDS_COUNT = 4;

    private static final String[] BAND_TITLES = {
            "SUB",
            "LOW",
            "MID",
            "HIGH"
    };

    private final Fragment fragment;
    private final EqSettingsManager settingsManager;
    private final LatencyChartView chartView;

    private String currentTarget = "CH1";
    private int focusedBand = 0;
    private int currentAccentColor = Color.parseColor("#38BDF8");

    // UI elements
    private TextView tvRow1Title;
    private TextView tvRow2Title;
    private TextView tvRow3Title;

    private final View[] cardRoots = new View[PEQ_BANDS_COUNT];
    private final TextView[] cardTitleTexts = new TextView[PEQ_BANDS_COUNT];
    private final TextView[] cardFreqTexts = new TextView[PEQ_BANDS_COUNT];
    private final TextView[] cardGainTexts = new TextView[PEQ_BANDS_COUNT];
    private final TextView[] cardQTexts = new TextView[PEQ_BANDS_COUNT];
    private final View[] cardFocusDots = new View[PEQ_BANDS_COUNT];

    private TextView tvControlFreqValue;
    private TextView tvControlGainValue;
    private TextView tvControlQValue;
    private SeekBar seekBarFreq;
    private SeekBar seekBarGain;
    private SeekBar seekBarQ;

    private boolean isUpdatingSliders = false;

    // Local cached values
    private final float[] freqs = new float[PEQ_BANDS_COUNT];
    private final float[] gains = new float[PEQ_BANDS_COUNT];
    private final float[] qs = new float[PEQ_BANDS_COUNT];

    public PeqUiController(Fragment fragment, EqSettingsManager settingsManager, LatencyChartView chartView) {
        this.fragment = fragment;
        this.settingsManager = settingsManager;
        this.chartView = chartView;
    }

    public void setupPeqViews(ViewGroup container1, ViewGroup container2, ViewGroup container3,
                              TextView title1, TextView title2, TextView title3,
                              String target) {
        if (fragment.getContext() == null || container1 == null || container2 == null || container3 == null) {
            return;
        }

        this.currentTarget = target != null ? target : "CH1";
        this.tvRow1Title = title1;
        this.tvRow2Title = title2;
        this.tvRow3Title = title3;

        ThemePalette curTheme = ThemeManager.getInstance(fragment.requireContext()).getCurrentTheme();
        this.currentAccentColor = curTheme.accentColor;

        if (title1 != null) title1.setText("SUB & LOW");
        if (title2 != null) title2.setText("MID & HIGH");
        if (title3 != null) title3.setText("CONTROLS • " + BAND_TITLES[focusedBand]);

        LayoutInflater inflater = LayoutInflater.from(fragment.requireContext());

        // Setup Card Low (Row 1): PEQ 1 & PEQ 2
        container1.removeAllViews();
        if (container1 instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) container1).setOrientation(android.widget.LinearLayout.HORIZONTAL);
        }
        container1.setBackground(null);
        container1.setPadding(0, 0, 0, 0);
        setupCardItem(inflater, container1, 0);
        setupCardItem(inflater, container1, 1);

        // Setup Card Mid (Row 2): PEQ 3 & PEQ 4
        container2.removeAllViews();
        if (container2 instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) container2).setOrientation(android.widget.LinearLayout.HORIZONTAL);
        }
        container2.setBackground(null);
        container2.setPadding(0, 0, 0, 0);
        setupCardItem(inflater, container2, 2);
        setupCardItem(inflater, container2, 3);

        // Setup Card High (Row 3): 3 Large Horizontal Sliders
        container3.removeAllViews();
        container3.setBackgroundResource(R.drawable.section_background);
        container3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x0DFFFFFF));
        View controlsView = inflater.inflate(R.layout.layout_peq_controls, container3, false);
        container3.addView(controlsView);

        tvControlFreqValue = controlsView.findViewById(R.id.tvControlFreqValue);
        tvControlGainValue = controlsView.findViewById(R.id.tvControlGainValue);
        tvControlQValue = controlsView.findViewById(R.id.tvControlQValue);

        seekBarFreq = controlsView.findViewById(R.id.seekBarPeqFreq);
        seekBarGain = controlsView.findViewById(R.id.seekBarPeqGain);
        seekBarQ = controlsView.findViewById(R.id.seekBarPeqQ);

        setupSlidersListeners();
        applyTheme(curTheme);

        loadTargetData(this.currentTarget);
        selectBand(focusedBand);
    }

    private void setupCardItem(LayoutInflater inflater, ViewGroup container, final int bandIndex) {
        View cardView = inflater.inflate(R.layout.item_peq_card, container, false);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cardView.getLayoutParams();
        if (lp == null) {
            lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        }
        float density = fragment.requireContext().getResources().getDisplayMetrics().density;
        int gap = (int) (3 * density);
        if (bandIndex % 2 == 0) {
            lp.setMargins(0, 0, gap, 0);
        } else {
            lp.setMargins(gap, 0, 0, 0);
        }
        cardView.setLayoutParams(lp);
        container.addView(cardView);

        cardRoots[bandIndex] = cardView.findViewById(R.id.peqCardRoot);
        cardTitleTexts[bandIndex] = cardView.findViewById(R.id.tvPeqBandTitle);
        cardFreqTexts[bandIndex] = cardView.findViewById(R.id.tvPeqFreqValue);
        cardGainTexts[bandIndex] = cardView.findViewById(R.id.tvPeqGainValue);
        cardQTexts[bandIndex] = cardView.findViewById(R.id.tvPeqQValue);
        cardFocusDots[bandIndex] = cardView.findViewById(R.id.viewPeqFocusDot);

        if (cardTitleTexts[bandIndex] != null) {
            cardTitleTexts[bandIndex].setText(BAND_TITLES[bandIndex]);
        }

        cardView.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            selectBand(bandIndex);
        });
    }

    private void setupSlidersListeners() {
        if (seekBarFreq != null) {
            seekBarFreq.setMax(1000);
            seekBarFreq.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || isUpdatingSliders) return;
                    float minF = EqSettingsManager.PEQ_MIN_FREQS[focusedBand];
                    float maxF = EqSettingsManager.PEQ_MAX_FREQS[focusedBand];
                    float frac = progress / 1000.0f;
                    float f = (float) Math.exp(Math.log(minF) + frac * (Math.log(maxF) - Math.log(minF)));
                    f = roundFrequency(f);

                    freqs[focusedBand] = f;
                    updateFreqDisplay(focusedBand, f);

                    settingsManager.updatePeqBand(currentTarget, focusedBand, f, gains[focusedBand], qs[focusedBand]);
                    if (chartView != null) {
                        chartView.updatePeqBand(focusedBand, f, gains[focusedBand], qs[focusedBand]);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (seekBarGain != null) {
            seekBarGain.setMax(240); // -12.0 dB to +12.0 dB with 0.1 dB step
            seekBarGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || isUpdatingSliders) return;
                    float g = (progress - 120) / 10.0f;
                    gains[focusedBand] = g;
                    updateGainDisplay(focusedBand, g);

                    settingsManager.updatePeqBand(currentTarget, focusedBand, freqs[focusedBand], g, qs[focusedBand]);
                    if (chartView != null) {
                        chartView.updatePeqBand(focusedBand, freqs[focusedBand], g, qs[focusedBand]);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (seekBarQ != null) {
            seekBarQ.setMax(97); // 0.30 to 10.00 with 0.10 step (97 steps)
            seekBarQ.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || isUpdatingSliders) return;
                    float q = 0.30f + progress * 0.10f;
                    qs[focusedBand] = q;
                    updateQDisplay(focusedBand, q);

                    settingsManager.updatePeqBand(currentTarget, focusedBand, freqs[focusedBand], gains[focusedBand], q);
                    if (chartView != null) {
                        chartView.updatePeqBand(focusedBand, freqs[focusedBand], gains[focusedBand], q);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    public void selectBand(int bandIndex) {
        if (bandIndex < 0 || bandIndex >= PEQ_BANDS_COUNT) return;
        this.focusedBand = bandIndex;

        // Update focus rings on cards
        updateFocusRings();

        // Update Row 3 title
        if (tvRow3Title != null) {
            tvRow3Title.setText("CONTROLS • " + BAND_TITLES[focusedBand]);
        }

        // Update sliders to reflect newly focused band values
        syncSlidersToFocusedBand();

        // Highlight focused band on chart
        if (chartView != null) {
            chartView.setPeqFocus(focusedBand);
        }
    }

    private void updateFocusRings() {
        if (fragment.getContext() == null) return;
        float density = fragment.requireContext().getResources().getDisplayMetrics().density;
        int cornerRadius = (int) (16 * density);
        int normalStrokeWidth = (int) Math.max(1, 1.2f * density);
        int focusedStrokeWidth = (int) Math.max(2, 1.8f * density);
        int normalStrokeColor = 0x33FFFFFF; // Stroke card standar
        int cardSolidColor = 0xFF1A1A1A;    // Background card

        for (int i = 0; i < PEQ_BANDS_COUNT; i++) {
            View root = cardRoots[i];
            if (root == null) continue;

            boolean isFocused = (i == focusedBand);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(cornerRadius);
            gd.setColor(cardSolidColor);

            if (isFocused) {
                // Kembalikan stroke ring focus (aksen aktif)
                gd.setStroke(focusedStrokeWidth, currentAccentColor);
                if (cardTitleTexts[i] != null) cardTitleTexts[i].setTextColor(currentAccentColor);
                if (cardFocusDots[i] != null) {
                    cardFocusDots[i].setVisibility(View.VISIBLE);
                    GradientDrawable dotGd = new GradientDrawable();
                    dotGd.setShape(GradientDrawable.OVAL);
                    dotGd.setColor(currentAccentColor);
                    cardFocusDots[i].setBackground(dotGd);
                }
            } else {
                // Kembalikan stroke card standar
                gd.setStroke(normalStrokeWidth, normalStrokeColor);
                if (cardTitleTexts[i] != null) cardTitleTexts[i].setTextColor(Color.WHITE);
                if (cardFocusDots[i] != null) {
                    cardFocusDots[i].setVisibility(View.GONE);
                }
            }
            root.setBackground(gd);
        }
    }

    private void syncSlidersToFocusedBand() {
        isUpdatingSliders = true;
        try {
            float f = freqs[focusedBand];
            float g = gains[focusedBand];
            float q = qs[focusedBand];

            // 1. Frequency
            float minF = EqSettingsManager.PEQ_MIN_FREQS[focusedBand];
            float maxF = EqSettingsManager.PEQ_MAX_FREQS[focusedBand];
            float clampedF = Math.max(minF, Math.min(maxF, f));
            float fracF = (float) ((Math.log(clampedF) - Math.log(minF)) / (Math.log(maxF) - Math.log(minF)));
            int progF = Math.max(0, Math.min(1000, Math.round(fracF * 1000f)));
            if (seekBarFreq != null) seekBarFreq.setProgress(progF);
            if (tvControlFreqValue != null) tvControlFreqValue.setText(formatFreq(f));

            // 2. Gain
            int progG = Math.max(0, Math.min(240, Math.round(g * 10f) + 120));
            if (seekBarGain != null) seekBarGain.setProgress(progG);
            if (tvControlGainValue != null) tvControlGainValue.setText(formatGain(g));

            // 3. Q
            int progQ = Math.max(0, Math.min(97, Math.round((q - 0.30f) / 0.10f)));
            if (seekBarQ != null) seekBarQ.setProgress(progQ);
            if (tvControlQValue != null) tvControlQValue.setText(formatQ(q));
        } finally {
            isUpdatingSliders = false;
        }
    }

    private void updateFreqDisplay(int band, float freq) {
        if (cardFreqTexts[band] != null) {
            cardFreqTexts[band].setText(formatFreq(freq));
        }
        if (band == focusedBand && tvControlFreqValue != null) {
            tvControlFreqValue.setText(formatFreq(freq));
        }
    }

    private void updateGainDisplay(int band, float gain) {
        if (cardGainTexts[band] != null) {
            cardGainTexts[band].setText(formatGain(gain));
            cardGainTexts[band].setTextColor(gain != 0 ? currentAccentColor : Color.WHITE);
        }
        if (band == focusedBand && tvControlGainValue != null) {
            tvControlGainValue.setText(formatGain(gain));
        }
    }

    private void updateQDisplay(int band, float q) {
        if (cardQTexts[band] != null) {
            cardQTexts[band].setText(formatQ(q));
        }
        if (band == focusedBand && tvControlQValue != null) {
            tvControlQValue.setText(formatQ(q));
        }
    }

    public void setTarget(String target) {
        this.currentTarget = target != null ? target : "CH1";
        loadTargetData(this.currentTarget);
        selectBand(focusedBand);
    }

    public void loadTargetData(String target) {
        if (settingsManager == null) return;
        this.currentTarget = target;
        for (int i = 0; i < PEQ_BANDS_COUNT; i++) {
            freqs[i] = settingsManager.getPeqFreq(target, i);
            gains[i] = settingsManager.getPeqGain(target, i);
            qs[i] = settingsManager.getPeqQ(target, i);

            updateFreqDisplay(i, freqs[i]);
            updateGainDisplay(i, gains[i]);
            updateQDisplay(i, qs[i]);
        }

        if (chartView != null) {
            chartView.setPeqBands(freqs, gains, qs, focusedBand, currentTarget);
        }
    }

    public void applyTheme(ThemePalette theme) {
        if (theme == null) return;
        this.currentAccentColor = theme.accentColor;

        if (tvRow1Title != null) tvRow1Title.setTextColor(theme.accentColor);
        if (tvRow2Title != null) tvRow2Title.setTextColor(theme.accentColor);
        if (tvRow3Title != null) tvRow3Title.setTextColor(theme.accentColor);

        ColorStateList csl = ColorStateList.valueOf(theme.accentColor);
        if (seekBarFreq != null) seekBarFreq.setProgressTintList(csl);
        if (seekBarGain != null) seekBarGain.setProgressTintList(csl);
        if (seekBarQ != null) seekBarQ.setProgressTintList(csl);

        if (tvControlFreqValue != null) tvControlFreqValue.setTextColor(theme.accentColor);
        if (tvControlGainValue != null) tvControlGainValue.setTextColor(theme.accentColor);
        if (tvControlQValue != null) tvControlQValue.setTextColor(theme.accentColor);

        for (int i = 0; i < PEQ_BANDS_COUNT; i++) {
            if (cardGainTexts[i] != null && gains[i] != 0) {
                cardGainTexts[i].setTextColor(theme.accentColor);
            }
        }

        updateFocusRings();
    }

    private static String formatFreq(float freq) {
        if (freq >= 1000f) {
            float khz = freq / 1000f;
            if (khz == (int) khz) {
                return String.format(Locale.US, "%.0f kHz", khz);
            } else {
                return String.format(Locale.US, "%.1f kHz", khz);
            }
        } else {
            return String.format(Locale.US, "%.0f Hz", freq);
        }
    }

    private static String formatGain(float gain) {
        if (Math.abs(gain) < 0.05f) {
            return "0.0 dB";
        }
        return String.format(Locale.US, "%+.1f dB", gain);
    }

    private static String formatQ(float q) {
        return String.format(Locale.US, "%.2f", q);
    }

    private static float roundFrequency(float f) {
        if (f < 100f) {
            return Math.round(f);
        } else if (f < 1000f) {
            return Math.round(f / 5f) * 5f;
        } else if (f < 10000f) {
            return Math.round(f / 25f) * 25f;
        } else {
            return Math.round(f / 50f) * 50f;
        }
    }

    public void onDestroyView() {
        for (int i = 0; i < PEQ_BANDS_COUNT; i++) {
            cardRoots[i] = null;
            cardTitleTexts[i] = null;
            cardFreqTexts[i] = null;
            cardGainTexts[i] = null;
            cardQTexts[i] = null;
            cardFocusDots[i] = null;
        }
        tvControlFreqValue = null;
        tvControlGainValue = null;
        tvControlQValue = null;
        seekBarFreq = null;
        seekBarGain = null;
        seekBarQ = null;
        tvRow1Title = null;
        tvRow2Title = null;
        tvRow3Title = null;
    }
}
