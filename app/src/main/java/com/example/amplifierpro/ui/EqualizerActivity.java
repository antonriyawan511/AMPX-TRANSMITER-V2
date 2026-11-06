package com.example.amplifierpro.ui;

import com.example.amplifierpro.audio.Presets;
import com.example.amplifierpro.R;
import com.example.amplifierpro.audio.Presets;
import com.example.amplifierpro.manager.EqSettingsManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class EqualizerActivity extends AppCompatActivity {

    private EqSettingsManager settingsManager;
    private String currentTarget = "CH1";
    private final List<SeekBar> bandSeekBars = new ArrayList<>();
    private final List<TextView> bandGainTexts = new ArrayList<>();
    
    // Memory state for tracking which preset was chosen for each target
    private String getSelectedPresetName(String target) {
        if (settingsManager == null) return "Flat";
        return settingsManager.getSelectedPreset(target);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this, 
            SystemBarStyle.dark(Color.TRANSPARENT), 
            SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equalizer);

        settingsManager = new EqSettingsManager(this);
        currentTarget = settingsManager.getActiveTarget();

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });

        setupEqualizerToggle();
        setupChannelControls();
        setupMasterVolume();
        setupBands();
        
        loadTargetData();
    }

    private void setupChannelControls() {
        View btnCH1 = findViewById(R.id.btnCH1);
        View btnCH2 = findViewById(R.id.btnCH2);
        View btnLink = findViewById(R.id.btnLink);

        if (btnCH1 != null && btnCH2 != null && btnLink != null) {
            updateTargetSelectionUI();

            btnCH1.setOnClickListener(v -> switchTarget("CH1"));
            btnCH2.setOnClickListener(v -> switchTarget("CH2"));
            btnLink.setOnClickListener(v -> switchTarget("LINK"));

            View.OnLongClickListener longClickListener = v -> {
                String target = "CH1";
                if (v.getId() == R.id.btnCH2) target = "CH2";
                else if (v.getId() == R.id.btnLink) target = "LINK";
                showPresetSelector(target);
                return true;
            };

            btnCH1.setOnLongClickListener(longClickListener);
            btnCH2.setOnLongClickListener(longClickListener);
            btnLink.setOnLongClickListener(longClickListener);

            // Global Gain Controls
            View btnPlus = findViewById(R.id.btnGainPlus);
            View btnMinus = findViewById(R.id.btnGainMinus);
            if (btnPlus != null) {
                btnPlus.setOnClickListener(v -> adjustAllGains(1));
            }
            if (btnMinus != null) {
                btnMinus.setOnClickListener(v -> adjustAllGains(-1));
            }
        }
    }

    private void adjustAllGains(int delta) {
        int[] bands = settingsManager.getBands(currentTarget);
        boolean changed = false;
        for (int i = 0; i < bands.length; i++) {
            int newValue = bands[i] + delta;
            if (newValue >= 0 && newValue <= 24) {
                bands[i] = newValue;
                changed = true;
            }
        }
        if (changed) {
            settingsManager.updateAllBands(currentTarget, bands);
            loadTargetData();
        }
    }

    private void showPresetSelector(String target) {
        final List<String> items = new ArrayList<>();
        final java.util.Set<String> headers = new java.util.HashSet<>(java.util.Arrays.asList(
                "REFERENCE", "TONAL", "BASS", "MID", "TREBLE", "LISTENING", "MUSIC"
        ));

        items.add("REFERENCE");
        items.add("Flat"); items.add("Reference"); items.add("Studio"); items.add("Hi-Fi");
        items.add("TONAL");
        items.add("Warm"); items.add("Neutral"); items.add("Bright"); items.add("Smooth"); items.add("Presence"); items.add("Air");
        items.add("BASS");
        items.add("Bass Boost"); items.add("Deep Bass"); items.add("Bass Punch"); items.add("Bass Extension");
        items.add("MID");
        items.add("Mid Focus"); items.add("Vocal"); items.add("Vocal Presence"); items.add("Clear Mid");
        items.add("TREBLE");
        items.add("Treble Boost"); items.add("High Clarity"); items.add("Bright Detail"); items.add("Smooth High");
        items.add("LISTENING");
        items.add("Loudness"); items.add("Dynamic"); items.add("Night");
        items.add("MUSIC");
        items.add("Rock"); items.add("Pop"); items.add("Electronic"); items.add("Hip-Hop"); items.add("Acoustic"); items.add("Jazz"); items.add("Classical"); items.add("Live");

        final String currentSelected = getSelectedPresetName(target);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_presets_grid, null);
        RecyclerView rvPresets = dialogView.findViewById(R.id.rvPresets);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);

        if (tvTitle != null) tvTitle.setText(target + " PRESETS");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return headers.contains(items.get(position)) ? 3 : 1;
            }
        });
        rvPresets.setLayoutManager(gridLayoutManager);

        rvPresets.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == 0) { // Header
                    View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                } else { // Item
                    View v = getLayoutInflater().inflate(R.layout.item_preset, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                }
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                String name = items.get(position);
                if (getItemViewType(position) == 0) {
                    TextView tv = holder.itemView.findViewById(android.R.id.text1);
                    tv.setText(name);
                    tv.setTextColor(getResources().getColor(R.color.accent_green, null));
                    tv.setTextSize(10);
                    tv.setAlpha(0.85f);
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                    float density = getResources().getDisplayMetrics().density;
                    int topPad = (position == 0) ? (int)(4 * density) : (int)(18 * density);
                    int bottomPad = (int)(8 * density);
                    int startPad = (int)(4 * density);
                    tv.setPadding(startPad, topPad, 0, bottomPad);
                    tv.setAllCaps(true);
                    tv.setLetterSpacing(0.1f);
                } else {
                    TextView tvName = holder.itemView.findViewById(R.id.tvPresetName);
                    TextView tvSelected = holder.itemView.findViewById(R.id.tvSelectedIndicator);
                    tvName.setText(name);
                    
                    boolean isSel = name.equals(currentSelected);
                    tvSelected.setVisibility(isSel ? View.VISIBLE : View.GONE);
                    
                    View container = holder.itemView.findViewById(R.id.btnPresetContainer);
                    container.setSelected(isSel);

                    if (isSel) {
                        tvName.setTextColor(getResources().getColor(R.color.accent_green, null));
                        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                    } else {
                        tvName.setTextColor(android.graphics.Color.WHITE);
                        tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
                    }

                    holder.itemView.setOnClickListener(v -> {
                        settingsManager.saveSelectedPreset(target, name);
                        int[] presetValues = Presets.ALL_PRESETS.get(name);
                        if (presetValues != null) {
                            settingsManager.updateAllBands(target, presetValues);
                            loadTargetData();
                        }
                        dialog.dismiss();
                    });
                }
            }

            @Override public int getItemCount() { return items.size(); }
            @Override public int getItemViewType(int position) {
                return headers.contains(items.get(position)) ? 0 : 1;
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    private void switchTarget(String target) {
        currentTarget = target;
        settingsManager.setActiveTarget(target);
        updateTargetSelectionUI();
        loadTargetData();
    }

    private void updateTargetSelectionUI() {
        View btnCH1 = findViewById(R.id.btnCH1);
        View btnCH2 = findViewById(R.id.btnCH2);
        View btnLink = findViewById(R.id.btnLink);

        if (btnCH1 != null) btnCH1.setSelected("CH1".equals(currentTarget));
        if (btnCH2 != null) btnCH2.setSelected("CH2".equals(currentTarget));
        if (btnLink != null) btnLink.setSelected("LINK".equals(currentTarget));
    }

    private void loadTargetData() {
        int[] bands = settingsManager.getBands(currentTarget);
        for (int b = 0; b < bandSeekBars.size() && b < bands.length; b++) {
            bandSeekBars.get(b).setProgress(bands[b]);
            updateGainText(bandGainTexts.get(b), bands[b]);
        }
    }

    private void updateGainText(TextView tv, int progress) {
        int gain = progress - 12;
        String gainText = (gain > 0 ? "+" : "") + gain + "dB";
        tv.setText(gainText);
    }

    private void setupEqualizerToggle() {
        View toggle = findViewById(R.id.switchEqualizer);
        if (toggle instanceof androidx.appcompat.widget.SwitchCompat) {
            androidx.appcompat.widget.SwitchCompat eqSwitch = (androidx.appcompat.widget.SwitchCompat) toggle;
            boolean isEnabled = settingsManager.isEqEnabled();
            eqSwitch.setChecked(isEnabled);
            updateEqVisualState(isEnabled);

            eqSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setEqEnabled(isChecked);
                updateEqVisualState(isChecked);
            });
        }
    }

    private void updateEqVisualState(boolean isEnabled) {
        float alpha = isEnabled ? 1.0f : 0.5f;
        View row1 = findViewById(R.id.containerRow1);
        View row2 = findViewById(R.id.containerRow2);
        View row3 = findViewById(R.id.containerRow3);
        if (row1 != null) row1.setAlpha(alpha);
        if (row2 != null) row2.setAlpha(alpha);
        if (row3 != null) row3.setAlpha(alpha);
    }

    private void setupMasterVolume() {
        SeekBar seekBarMaster = findViewById(R.id.seekBarMasterVolume);
        TextView tvValue = findViewById(R.id.tvMasterVolumeValue);
        TextView tvPopup = findViewById(R.id.tvMasterVolumePopup);

        if (seekBarMaster != null && tvValue != null && tvPopup != null) {
            int savedVol = settingsManager.getMasterVolume();
            seekBarMaster.setProgress(savedVol);
            tvValue.setText(getString(R.string.volume_percentage, savedVol));

            seekBarMaster.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int lastProgress = -1;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    String valStr = getString(R.string.volume_percentage, progress);
                    tvValue.setText(valStr);
                    tvPopup.setText(valStr);
                    updatePopupPosition(seekBar, tvPopup);
                    
                    if (fromUser) {
                        if (progress != lastProgress) {
                            seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                            lastProgress = progress;
                        }
                        settingsManager.updateMasterVolume(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    tvPopup.animate().alpha(1f).setDuration(150).start();
                    lastProgress = seekBar.getProgress();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    tvPopup.animate().alpha(0f).setDuration(200).start();
                }
            });

            seekBarMaster.post(() -> updatePopupPosition(seekBarMaster, tvPopup));
        }
    }

    private void updatePopupPosition(SeekBar seekBar, View popup) {
        if (popup.getWidth() == 0) {
            popup.post(() -> updatePopupPosition(seekBar, popup));
            return;
        }

        int[] seekBarLoc = new int[2];
        seekBar.getLocationInWindow(seekBarLoc);
        
        int[] rootLoc = new int[2];
        View root = (View) popup.getParent();
        root.getLocationInWindow(rootLoc);
        
        int trackWidth = seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
        float thumbPosInSeekBar = seekBar.getPaddingLeft() + (trackWidth * (float) seekBar.getProgress() / seekBar.getMax());
        
        float thumbXInWindow = seekBarLoc[0] + thumbPosInSeekBar;
        float targetXInRoot = thumbXInWindow - rootLoc[0] - (popup.getWidth() / 2f);
        
        targetXInRoot = Math.max(8, Math.min(root.getWidth() - popup.getWidth() - 8, targetXInRoot));
        popup.setX(targetXInRoot);
        
        float targetYInRoot = seekBarLoc[1] - rootLoc[1] - popup.getHeight() - 32; 
        popup.setY(targetYInRoot);
    }

    private void updateEqPopupPosition(SeekBar seekBar, View localView, View popup) {
        if (popup.getWidth() == 0) {
            popup.post(() -> updateEqPopupPosition(seekBar, localView, popup));
            return;
        }

        int[] localLoc = new int[2];
        localView.getLocationInWindow(localLoc);
        
        int[] rootLoc = new int[2];
        View root = (View) popup.getParent();
        root.getLocationInWindow(rootLoc);
        
        float targetXInRoot = localLoc[0] - rootLoc[0] + (localView.getWidth() / 2f) - (popup.getWidth() / 2f);
        float targetYInRoot = localLoc[1] - rootLoc[1] - (popup.getHeight() / 2f) + (localView.getHeight() / 2f);
        
        targetXInRoot = Math.max(8, Math.min(root.getWidth() - popup.getWidth() - 8, targetXInRoot));
        targetYInRoot = Math.max(8, targetYInRoot);
        
        popup.setX(targetXInRoot);
        popup.setY(targetYInRoot);
    }

    private void setupBands() {
        bandSeekBars.clear();
        bandGainTexts.clear();

        String[] row1Freqs = {"25", "31.5", "40", "50", "63", "80", "100", "125", "160", "200"};
        String[] row2Freqs = {"250", "315", "400", "500", "630", "800", "1k", "1.25k", "1.6k", "2k"};
        String[] row3Freqs = {"2.5k", "3.15k", "4k", "5k", "6.3k", "8k", "10k", "12.5k", "16k", "20k"};

        populateContainer(findViewById(R.id.containerRow1), row1Freqs, 0);
        populateContainer(findViewById(R.id.containerRow2), row2Freqs, 10);
        populateContainer(findViewById(R.id.containerRow3), row3Freqs, 20);
    }

    private void populateContainer(LinearLayout container, String[] freqs, int startIndex) {
        if (container == null) return;
        for (int i = 0; i < freqs.length; i++) {
            final int bandIndex = startIndex + i;
            View bandView = getLayoutInflater().inflate(R.layout.item_equalizer_band, container, false);
            TextView tvFreq = bandView.findViewById(R.id.tvFrequency);
            TextView tvGain = bandView.findViewById(R.id.tvGainValue);
            SeekBar seekBar = bandView.findViewById(R.id.seekBarBand);

            tvFreq.setText(freqs[i]);
            bandSeekBars.add(seekBar);
            bandGainTexts.add(tvGain);

            tvGain.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    setPressedVisual(tvGain);
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    resetValueVisual(tvGain, true);
                }
                return false;
            });
            tvGain.setOnClickListener(v -> triggerGainTapFeedback(tvGain, seekBar, bandIndex));

            tvFreq.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    setPressedVisual(tvFreq);
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    resetValueVisual(tvFreq, false);
                }
                return false;
            });
            tvFreq.setOnClickListener(v -> triggerFreqTapFeedback(tvFreq, seekBar, bandIndex));
            
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int lastProgress = -1;
                private TextView tvPopup;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateGainText(tvGain, progress);
                    if (tvPopup != null) {
                        updateGainText(tvPopup, progress);
                    }

                    if (fromUser) {
                        if (progress != lastProgress) {
                            seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                            lastProgress = progress;
                        }
                        settingsManager.updateBand(currentTarget, bandIndex, progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    lastProgress = seekBar.getProgress();
                    tvPopup = findViewById(R.id.tvEqValuePopup);
                    if (tvPopup != null) {
                        updateGainText(tvPopup, seekBar.getProgress());
                        updateEqPopupPosition(seekBar, tvGain, tvPopup);
                        tvPopup.animate().alpha(1f).setDuration(150).start();
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (tvPopup != null) {
                        tvPopup.animate().alpha(0f).setDuration(200).start();
                    }
                    tvGain.setAlpha(1f);
                }
            });

            container.addView(bandView);
        }
    }

    private void setPressedVisual(TextView tv) {
        if (tv == null) return;
        int accentColor = ContextCompat.getColor(this, R.color.accent_green);
        Drawable bg = ContextCompat.getDrawable(this, R.drawable.pill_value_popup);
        if (bg != null) {
            bg = bg.mutate();
            bg.setTint(accentColor);
            int pl = tv.getPaddingLeft();
            int pt = tv.getPaddingTop();
            int pr = tv.getPaddingRight();
            int pb = tv.getPaddingBottom();
            tv.setBackground(bg);
            tv.setPadding(pl, pt, pr, pb);
        }
        tv.setTextColor(Color.WHITE);
        tv.setAlpha(1.0f);
    }

    private void resetValueVisual(TextView tv, boolean isGainValue) {
        if (tv == null) return;
        int pl = tv.getPaddingLeft();
        int pt = tv.getPaddingTop();
        int pr = tv.getPaddingRight();
        int pb = tv.getPaddingBottom();
        tv.setBackground(null);
        tv.setPadding(pl, pt, pr, pb);
        if (isGainValue) {
            tv.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
        } else {
            tv.setTextColor(Color.WHITE);
            tv.setAlpha(0.8f);
        }
    }

    private void triggerGainTapFeedback(TextView tvGain, SeekBar seekBar, int bandIndex) {
        if (tvGain == null || seekBar == null) return;
        int current = seekBar.getProgress();
        if (current < 24) {
            int next = current + 1;
            seekBar.setProgress(next);
            seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (settingsManager != null) {
                settingsManager.updateBand(currentTarget, bandIndex, next);
            }
        }
        applyValueTapVisual(tvGain, true);
    }

    private void triggerFreqTapFeedback(TextView tvFreq, SeekBar seekBar, int bandIndex) {
        if (tvFreq == null || seekBar == null) return;
        int current = seekBar.getProgress();
        if (current > 0) {
            int next = current - 1;
            seekBar.setProgress(next);
            seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (settingsManager != null) {
                settingsManager.updateBand(currentTarget, bandIndex, next);
            }
        }
        applyValueTapVisual(tvFreq, false);
    }

    private void applyValueTapVisual(TextView tv, boolean isGainValue) {
        if (tv == null) return;
        setPressedVisual(tv);
        tv.animate().cancel();
        tv.animate().alpha(1.0f).setDuration(120).withEndAction(() -> resetValueVisual(tv, isGainValue)).start();
    }
}
