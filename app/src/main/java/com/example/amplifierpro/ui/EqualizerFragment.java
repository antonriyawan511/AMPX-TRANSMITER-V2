package com.example.amplifierpro.ui;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.amplifierpro.audio.Presets;
import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.service.AudioStreamService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.animation.ValueAnimator;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.graphics.Color;
import android.net.Uri;
import android.widget.ImageView;
import com.example.amplifierpro.security.LicenseManager;

public class EqualizerFragment extends Fragment implements ThemeManager.OnThemeChangeListener {

    private EqSettingsManager settingsManager;
    private VolumePeakHelper volumePeakHelper;
    private String currentTarget = "CH1";
    private final List<SeekBar> bandSeekBars = new ArrayList<>();
    private final List<TextView> bandGainTexts = new ArrayList<>();
    private TextView tvEqPopup;
    private ImageButton btnAlwaysOn;
    private ImageButton btnSavePreset;
    private ImageButton btnGeqBands;
    private ImageButton btnPowerEqualizer;
    private Spinner spinnerGeqBands;
    private int currentGeqBandMode = 30;
    private int currentBandSelectionIndex = 0;
    private boolean isInitialSpinnerLoad = true;
    private float[] currentBandGains;

    public static final float[] FREQS_30 = {
        25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f,
        250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f, 2000f,
        2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
    };

    public static final float[] FREQS_15 = {
        25f, 40f, 63f, 100f, 160f,
        250f, 400f, 630f, 1000f, 1600f,
        2500f, 4000f, 6300f, 10000f, 16000f
    };

    public static final float[] FREQS_10 = {
        31.5f, 63f, 125f,
        250f, 500f, 1000f, 2000f,
        4000f, 8000f, 16000f
    };

    public static final float[] FREQS_7 = {
        63f, 160f,
        400f, 1000f, 2500f,
        6300f, 16000f
    };

    public static final float[] FREQS_5 = {
        63f, 250f,
        1000f,
        4000f, 16000f
    };

    private float[] getCurrentFreqs() {
        return getFreqsForBandMode(currentGeqBandMode);
    }

    private float[] getFreqsForBandMode(int mode) {
        switch (mode) {
            case 15: return FREQS_15;
            case 10: return FREQS_10;
            case 7: return FREQS_7;
            case 5: return FREQS_5;
            case 30:
            default: return FREQS_30;
        }
    }

    private float[] toFloatArray(int[] arr) {
        if (arr == null) return new float[0];
        float[] f = new float[arr.length];
        for (int i = 0; i < arr.length; i++) f[i] = arr[i];
        return f;
    }

    private int[] toIntArray(float[] arr) {
        if (arr == null) return new int[0];
        int[] res = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            res[i] = Math.max(0, Math.min(24, Math.round(arr[i])));
        }
        return res;
    }

    private float[] getBandsFloatFromSettings() {
        int[] b = settingsManager != null ? settingsManager.getBands(currentTarget) : new int[currentGeqBandMode];
        return toFloatArray(b);
    }

    public static float[] interpolateGains(float[] srcFreqs, float[] srcGains, float[] tgtFreqs) {
        float[] tgtGains = new float[tgtFreqs.length];
        int nSrc = srcFreqs.length;
        if (nSrc == 0) return tgtGains;
        if (nSrc == 1 || srcGains.length == 1) {
            java.util.Arrays.fill(tgtGains, srcGains[0]);
            return tgtGains;
        }

        for (int j = 0; j < tgtFreqs.length; j++) {
            float f = tgtFreqs[j];
            if (f <= srcFreqs[0]) {
                tgtGains[j] = srcGains[0];
            } else if (f >= srcFreqs[nSrc - 1]) {
                tgtGains[j] = srcGains[nSrc - 1];
            } else {
                int i = 0;
                while (i < nSrc - 1 && srcFreqs[i + 1] < f) {
                    i++;
                }
                double logF0 = Math.log(srcFreqs[i]);
                double logF1 = Math.log(srcFreqs[i + 1]);
                double logF = Math.log(f);
                double t = (logF1 > logF0) ? (logF - logF0) / (logF1 - logF0) : 0.0;
                tgtGains[j] = (float) (srcGains[i] + t * (srcGains[i + 1] - srcGains[i]));
            }
        }
        return tgtGains;
    }

    private HeaderSwipeLayout headerSwipeLayout;
    private View headerNormalView;
    private View headerGraphView;
    private LatencyChartView chartEqGraph;
    private boolean isShowingGraph = false;
    private boolean isHeaderAnimating = false;

    private View btnSwitchEqMode;
    private TextView tvSegmentEq;
    private TextView tvSegmentPeq;
    private PeqUiController peqUiController;
    private boolean isPeqMode = false;

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
    
    private String getSelectedPresetName(String target) {
        if (settingsManager == null) return "Flat";
        return settingsManager.getSelectedPreset(target);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_equalizer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        settingsManager = new EqSettingsManager(requireContext());
        currentTarget = settingsManager.getActiveTarget();
        // Jumlah band diambil PER-TARGET (bukan global) agar layout slider selalu cocok
        // dengan panjang data target yang sedang aktif.
        currentGeqBandMode = settingsManager.getGeqBandCount(currentTarget);
        switch (currentGeqBandMode) {
            case 15: currentBandSelectionIndex = 1; break;
            case 10: currentBandSelectionIndex = 2; break;
            case 7: currentBandSelectionIndex = 3; break;
            case 5: currentBandSelectionIndex = 4; break;
            case 30:
            default:
                currentGeqBandMode = 30;
                currentBandSelectionIndex = 0;
                break;
        }
        volumePeakHelper = new VolumePeakHelper(this);
        tvEqPopup = view.findViewById(R.id.tvEqValuePopup);

        setupEqModeSwitch(view);
        setupGeqBandsSelector(view);
        setupSavePresetButton(view);
        setupAlwaysOnButton(view);
        setupEqualizerToggle(view);
        setupChannelControls(view);
        setupHeaderSwipe(view);
        peqUiController = new PeqUiController(this, settingsManager, chartEqGraph);
        volumePeakHelper.setup(view);
        String currentMode = settingsManager != null ? settingsManager.getEqMode() : "EQ";
        boolean isPq = "PQ".equalsIgnoreCase(currentMode) || "PEQ".equalsIgnoreCase(currentMode);
        if (isPq) {
            switchToPeqMode();
        } else {
            setupBands(view);
            loadTargetData();
        }
        updateProLockState();
        alignPeakMetersWithTopCard(view);
        ThemeManager.getInstance(requireContext()).registerListener(this);
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ThemeManager.getInstance(requireContext()).unregisterListener(this);
        if (volumePeakHelper != null) volumePeakHelper.onDestroyView();
        tvEqPopup = null;
        btnAlwaysOn = null;
        btnSavePreset = null;
        btnGeqBands = null;
        spinnerGeqBands = null;
        headerSwipeLayout = null;
        headerNormalView = null;
        headerGraphView = null;
        chartEqGraph = null;
        btnSwitchEqMode = null;
        tvSegmentEq = null;
        tvSegmentPeq = null;
        if (peqUiController != null) {
            peqUiController.onDestroyView();
            peqUiController = null;
        }
    }

    private void setupChannelControls(View root) {
        View btnCH1 = root.findViewById(R.id.btnCH1);
        View btnCH2 = root.findViewById(R.id.btnCH2);
        View btnLink = root.findViewById(R.id.btnLink);

        if (btnCH1 != null && btnCH2 != null && btnLink != null) {
            updateTargetSelectionUI(root);

            btnCH1.setOnClickListener(v -> switchTarget(root, "CH1"));
            btnCH2.setOnClickListener(v -> switchTarget(root, "CH2"));
            btnLink.setOnClickListener(v -> switchTarget(root, "LINK"));

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
            View btnPlus = root.findViewById(R.id.btnGainPlus);
            if (btnPlus != null) {
                btnPlus.setOnClickListener(v -> adjustAllGains(1));
            }
            View btnMinus = root.findViewById(R.id.btnGainMinus);
            if (btnMinus != null) {
                btnMinus.setOnClickListener(v -> adjustAllGains(-1));
            }
            
            // Channel Gain Controls
            View btnChanPlus = root.findViewById(R.id.btnChanGainPlus);
            View btnChanMinus = root.findViewById(R.id.btnChanGainMinus);
            if (btnChanPlus != null) {
                btnChanPlus.setOnClickListener(v -> adjustChannelGain(1.0f));
            }
            if (btnChanMinus != null) {
                btnChanMinus.setOnClickListener(v -> adjustChannelGain(-1.0f));
            }
        }
    }

    private void adjustChannelGain(float delta) {
        float current = settingsManager.getChannelGain(currentTarget);
        float newVal = current + delta;
        if (newVal < -12.0f) newVal = -12.0f;
        if (newVal > 12.0f) newVal = 12.0f;
        
        settingsManager.setChannelGain(currentTarget, newVal);
        updateChannelTexts(getView());
    }

    private void updateChannelTexts(View root) {
        if (root == null) return;
        TextView btnCH1 = root.findViewById(R.id.btnCH1);
        TextView btnCH2 = root.findViewById(R.id.btnCH2);
        
        float g1 = settingsManager.getChannelGain("CH1");
        float g2 = settingsManager.getChannelGain("CH2");
        
        btnCH1.setText(String.format(java.util.Locale.US, "CH1\n%.0f dB", g1));
        btnCH2.setText(String.format(java.util.Locale.US, "CH2\n%.0f dB", g2));
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
                "USER", "REFERENCE", "TONAL", "BASS", "MID", "TREBLE", "LISTENING", "MUSIC"
        ));

        // Category USER at the very top (only if user presets exist)
        List<String> userPresets = settingsManager.getUserPresetNames();
        if (!userPresets.isEmpty()) {
            items.add("USER");
            items.addAll(userPresets);
        }

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
        MaxHeightRecyclerView rvPresets = dialogView.findViewById(R.id.rvPresets);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);

        if (tvTitle != null) tvTitle.setText(target + " PRESETS");

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxHeight = (int) (dm.heightPixels * 0.62f);
        if (rvPresets != null) {
            rvPresets.setMaxHeight(maxHeight);
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setView(dialogView)
                .create();

        GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), 4);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return headers.contains(items.get(position)) ? 4 : 1;
            }
        });
        if (rvPresets != null) {
            rvPresets.setLayoutManager(gridLayoutManager);
        }

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
                    int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
                    tv.setTextColor(accentColor);
                    tv.setTextSize(9.5f);
                    tv.setAlpha(0.85f);
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                    float density = requireContext().getResources().getDisplayMetrics().density;
                    int topPad = (position == 0) ? (int)(2 * density) : (int)(8 * density);
                    int bottomPad = (int)(3 * density);
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

                    int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;

                    // Apply programmatic background with theme accent color for focus ring
                    float density = requireContext().getResources().getDisplayMetrics().density;
                    int cornerRadius = (int) (10 * density);
                    int strokeWidth = (int) (1.2f * density);
                    GradientDrawable gd = new GradientDrawable();
                    gd.setCornerRadius(cornerRadius);
                    if (isSel) {
                        int fillColor = (0x33 << 24) | (accentColor & 0x00FFFFFF);
                        gd.setColor(fillColor);
                        gd.setStroke(strokeWidth, accentColor);
                        if (tvSelected.getBackground() != null) {
                            GradientDrawable dotGd = new GradientDrawable();
                            dotGd.setShape(GradientDrawable.OVAL);
                            dotGd.setColor(accentColor);
                            tvSelected.setBackground(dotGd);
                        }
                    } else {
                        gd.setColor(0x0DFFFFFF);
                        gd.setStroke((int)(1 * density), 0x1AFFFFFF);
                    }
                    container.setBackground(gd);

                    tvName.setTextColor(android.graphics.Color.WHITE);
                    if (isSel) {
                        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                    } else {
                        tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
                    }

                    holder.itemView.setOnClickListener(v -> {
                        settingsManager.saveSelectedPreset(target, name);
                        int[] presetValues = settingsManager.getPresetBands(name);
                        if (presetValues != null) {
                            int[] valuesToApply;
                            if (presetValues.length != currentGeqBandMode) {
                                float[] srcF = getFreqsForBandMode(presetValues.length);
                                float[] srcG = toFloatArray(presetValues);
                                float[] tgtG = interpolateGains(srcF, srcG, getCurrentFreqs());
                                valuesToApply = toIntArray(tgtG);
                            } else {
                                valuesToApply = presetValues;
                            }
                            settingsManager.updateAllBands(target, valuesToApply);
                            loadTargetData();
                        }
                        dialog.dismiss();
                    });

                    if (settingsManager.isUserPreset(name)) {
                        holder.itemView.setOnLongClickListener(v -> {
                            showEditUserPresetDialog(name, target, dialog);
                            return true;
                        });
                    } else {
                        holder.itemView.setOnLongClickListener(null);
                    }
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

    private void showEditUserPresetDialog(String presetName, String target, androidx.appcompat.app.AlertDialog presetsDialog) {
        if (getContext() == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_receiver, null);
        TextView tvTitle = dialogView.findViewById(R.id.tvEditDialogTitle);
        if (tvTitle != null) tvTitle.setText("EDIT PRESET");

        TextView tvLabel = dialogView.findViewById(R.id.tvDisplayNameLabel);
        if (tvLabel != null) tvLabel.setText("PRESET NAME");

        EditText etName = dialogView.findViewById(R.id.etReceiverName);
        if (etName != null) {
            etName.setText(presetName);
            etName.setSelection(presetName.length());
        }

        Button btnDelete = dialogView.findViewById(R.id.btnDeleteReceiver);
        if (btnDelete != null) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setText("DELETE");
        }

        Button btnCancel = dialogView.findViewById(R.id.btnCancelDialog);
        Button btnSave = dialogView.findViewById(R.id.btnSaveDialog);
        if (btnSave != null) {
            btnSave.setText("SAVE NAME");
        }

        int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
        if (tvLabel != null) tvLabel.setTextColor(accentColor);
        if (btnCancel != null) btnCancel.setTextColor(accentColor);
        if (btnSave != null) btnSave.setTextColor(accentColor);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                dialog.dismiss();
                settingsManager.deleteUserPreset(presetName);
                if (presetName.equals(settingsManager.getSelectedPreset(target))) {
                    settingsManager.saveSelectedPreset(target, "Flat");
                    int[] flatValues = new int[currentGeqBandMode];
                    java.util.Arrays.fill(flatValues, 12);
                    settingsManager.updateAllBands(target, flatValues);
                    loadTargetData();
                }
                Toast.makeText(requireContext(), "Preset '" + presetName + "' dihapus", Toast.LENGTH_SHORT).show();
                if (presetsDialog != null && presetsDialog.isShowing()) {
                    presetsDialog.dismiss();
                    showPresetSelector(target);
                }
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String newName = etName != null ? etName.getText().toString().trim() : "";
                if (newName.isEmpty()) {
                    newName = presetName;
                }
                if (!newName.equals(presetName)) {
                    settingsManager.renameUserPreset(presetName, newName);
                    if (presetName.equals(settingsManager.getSelectedPreset(target))) {
                        settingsManager.saveSelectedPreset(target, newName);
                    }
                    Toast.makeText(requireContext(), "Nama preset disimpan", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
                if (presetsDialog != null && presetsDialog.isShowing()) {
                    presetsDialog.dismiss();
                    showPresetSelector(target);
                }
            });
        }

        dialog.show();
    }

    private void showSavePresetDialog() {
        if (getContext() == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_receiver, null);
        TextView tvTitle = dialogView.findViewById(R.id.tvEditDialogTitle);
        if (tvTitle != null) tvTitle.setText("SAVE PRESET");

        TextView tvLabel = dialogView.findViewById(R.id.tvDisplayNameLabel);
        if (tvLabel != null) tvLabel.setText("PRESET NAME");

        int nextIndex = settingsManager.getNextUserPresetIndex();
        String fallbackName = "User " + nextIndex;

        EditText etName = dialogView.findViewById(R.id.etReceiverName);
        if (etName != null) {
            etName.setText("");
            etName.setHint(fallbackName);
        }

        View divider = dialogView.findViewById(R.id.dividerDelete);
        if (divider != null) divider.setVisibility(View.GONE);

        Button btnDelete = dialogView.findViewById(R.id.btnDeleteReceiver);
        if (btnDelete != null) btnDelete.setVisibility(View.GONE);

        Button btnCancel = dialogView.findViewById(R.id.btnCancelDialog);
        Button btnSave = dialogView.findViewById(R.id.btnSaveDialog);
        if (btnSave != null) {
            btnSave.setText("SAVE PRESET");
        }

        int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
        if (tvLabel != null) tvLabel.setTextColor(accentColor);
        if (btnCancel != null) btnCancel.setTextColor(accentColor);
        if (btnSave != null) btnSave.setTextColor(accentColor);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String inputName = etName != null ? etName.getText().toString().trim() : "";
                String finalName = inputName.isEmpty() ? fallbackName : inputName;

                int[] currentBands = settingsManager.getBands(currentTarget);
                settingsManager.saveUserPreset(finalName, currentBands);
                settingsManager.saveSelectedPreset(currentTarget, finalName);
                updateEqGraphData();

                Toast.makeText(requireContext(), "Preset '" + finalName + "' disimpan", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void setupGeqBandsSelector(View view) {
        btnGeqBands = view.findViewById(R.id.btnGeqBands);
        spinnerGeqBands = view.findViewById(R.id.spinnerGeqBands);
        if (spinnerGeqBands == null) return;

        final String[] bandOptions = {"30 Band", "15 Band", "10 Band", "7 Band", "5 Band"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), R.layout.item_spinner_selected, bandOptions) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                v.setVisibility(View.GONE);
                return v;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View dropDownView = LayoutInflater.from(getContext()).inflate(R.layout.item_spinner_dropdown, parent, false);
                TextView tv = dropDownView.findViewById(android.R.id.text1);
                tv.setText(bandOptions[position]);
                if (getContext() != null) {
                    ThemePalette theme = ThemeManager.getInstance(getContext()).getCurrentTheme();
                    if (position == currentBandSelectionIndex) {
                        tv.setTextColor(theme.accentColor);
                    } else {
                        tv.setTextColor(Color.WHITE);
                    }
                }
                return dropDownView;
            }
        };

        spinnerGeqBands.setAdapter(adapter);
        spinnerGeqBands.setSelection(currentBandSelectionIndex);

        if (btnGeqBands != null) {
            btnGeqBands.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                spinnerGeqBands.performClick();
            });
        }

        spinnerGeqBands.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (isInitialSpinnerLoad) {
                    isInitialSpinnerLoad = false;
                    return;
                }
                currentBandSelectionIndex = position;
                applyGeqBandCount(position);
                Toast.makeText(requireContext(), "GEQ Mode: " + bandOptions[position], Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Menyimpan hasil remap HANYA untuk target yang sedang aktif.
     *
     * LINK kini punya data & preset sendiri (mode "terantai": saat aktif, DSP memakai
     * L = R). CH1 dan CH2 berdiri sendiri (mode "tidak terantai"). Mengedit salah satu
     * target TIDAK boleh mengubah target lain, agar preset tiap target tetap terjaga
     * ketika jumlah band diubah.
     */
    private void saveCurrentTargetBands(int[] tgtGainsInt) {
        if (settingsManager == null) return;
        settingsManager.updateAllBands(currentTarget, tgtGainsInt);
    }

    private void applyGeqBandCount(int position) {
        int targetBandMode;
        switch (position) {
            case 1: targetBandMode = 15; break;
            case 2: targetBandMode = 10; break;
            case 3: targetBandMode = 7; break;
            case 4: targetBandMode = 5; break;
            case 0:
            default: targetBandMode = 30; break;
        }

        if (targetBandMode == currentGeqBandMode && currentBandGains != null) return;

        float[] srcFreqs = getCurrentFreqs();
        float[] srcGains = currentBandGains != null ? currentBandGains : getBandsFloatFromSettings();

        float[] tgtFreqs = getFreqsForBandMode(targetBandMode);
        float[] tgtGains = interpolateGains(srcFreqs, srcGains, tgtFreqs);

        currentGeqBandMode = targetBandMode;
        currentBandGains = tgtGains;

        int[] tgtGainsInt = toIntArray(tgtGains);
        android.util.Log.d("EqualizerFragment", "[applyGeqBandCount] targetBandMode=" + targetBandMode + 
                ", tgtGains=" + java.util.Arrays.toString(tgtGainsInt) + 
                ", freqs=" + java.util.Arrays.toString(tgtFreqs));

        saveCurrentTargetBands(tgtGainsInt);

        if (isPeqMode) {
            setEqModeDirect("EQ");
        } else {
            View root = getView();
            if (root != null) {
                setupBands(root);
                for (int i = 0; i < bandSeekBars.size() && i < tgtGains.length; i++) {
                    int p = Math.max(0, Math.min(24, Math.round(tgtGains[i])));
                    bandSeekBars.get(i).setProgress(p);
                    updateGainText(bandGainTexts.get(i), p);
                }
                updateEqGraphData();
            }
        }
    }

    private void onBandGainChanged(int sliderIndex, int progress) {
        if (currentBandGains != null && sliderIndex >= 0 && sliderIndex < currentBandGains.length) {
            currentBandGains[sliderIndex] = progress;
        }
        float freq = (getCurrentFreqs() != null && sliderIndex < getCurrentFreqs().length) ? getCurrentFreqs()[sliderIndex] : 0f;
        android.util.Log.d("EqualizerFragment", "[onBandGainChanged] target=" + currentTarget + 
                ", bandCount=" + currentGeqBandMode + 
                ", sliderIndex=" + sliderIndex + 
                ", freq=" + freq + "Hz, progress=" + progress + " (" + (progress - 12) + "dB)");

        settingsManager.updateBand(currentTarget, sliderIndex, progress);
        if (chartEqGraph != null) {
            chartEqGraph.updateBand(sliderIndex, progress);
        }
    }

    private void setupSavePresetButton(View root) {
        btnSavePreset = root.findViewById(R.id.btnSavePresetEqualizer);
        if (btnSavePreset != null) {
            btnSavePreset.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                showSavePresetDialog();
            });
        }
    }

    private void setupEqModeSwitch(View view) {
        btnSwitchEqMode = view.findViewById(R.id.btnSwitchEqMode);
        tvSegmentEq = view.findViewById(R.id.tvSegmentEq);
        tvSegmentPeq = view.findViewById(R.id.tvSegmentPeq);

        String currentMode = settingsManager != null ? settingsManager.getEqMode() : "EQ";
        updateEqModeUI(currentMode);

        if (btnSwitchEqMode != null) {
            btnSwitchEqMode.setOnClickListener(v -> toggleEqMode());
        }
        if (tvSegmentEq != null) {
            tvSegmentEq.setOnClickListener(v -> setEqModeDirect("EQ"));
        }
        if (tvSegmentPeq != null) {
            tvSegmentPeq.setOnClickListener(v -> setEqModeDirect("PQ"));
        }
    }

    private void setEqModeDirect(String mode) {
        if (settingsManager != null) {
            settingsManager.setEqMode(mode);
        }
        updateEqModeUI(mode);
        if (getView() != null) {
            getView().performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        }
        boolean isPq = "PQ".equalsIgnoreCase(mode) || "PEQ".equalsIgnoreCase(mode);
        if (isPq) {
            switchToPeqMode();
        } else {
            switchToGeqMode();
        }
        Toast.makeText(requireContext(), "Mode: " + (isPq ? "PQ Mode" : "GEQ 30-Band"), Toast.LENGTH_SHORT).show();
    }

    private void switchToPeqMode() {
        isPeqMode = true;
        View root = getView();
        if (root == null || peqUiController == null) return;
        ViewGroup c1 = root.findViewById(R.id.containerRow1);
        ViewGroup c2 = root.findViewById(R.id.containerRow2);
        ViewGroup c3 = root.findViewById(R.id.containerRow3);
        TextView t1 = root.findViewById(R.id.tvRow1Title);
        TextView t2 = root.findViewById(R.id.tvRow2Title);
        TextView t3 = root.findViewById(R.id.tvRow3Title);

        peqUiController.setupPeqViews(c1, c2, c3, t1, t2, t3, currentTarget);
        alignPeakMetersWithTopCard(root);
    }

    private void switchToGeqMode() {
        isPeqMode = false;
        View root = getView();
        if (root == null) return;
        TextView t1 = root.findViewById(R.id.tvRow1Title);
        TextView t2 = root.findViewById(R.id.tvRow2Title);
        TextView t3 = root.findViewById(R.id.tvRow3Title);
        if (t1 != null) t1.setText(R.string.row_1);
        if (t2 != null) t2.setText(R.string.row_2);
        if (t3 != null) t3.setText(R.string.row_3);

        View c1 = root.findViewById(R.id.containerRow1);
        View c2 = root.findViewById(R.id.containerRow2);
        View c3 = root.findViewById(R.id.containerRow3);
        float density = getResources().getDisplayMetrics().density;
        int p4 = (int) (4 * density);
        if (c1 != null) {
            c1.setBackgroundResource(R.drawable.section_background);
            c1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x0DFFFFFF));
            c1.setPadding(p4, p4, p4, p4);
        }
        if (c2 != null) {
            c2.setBackgroundResource(R.drawable.section_background);
            c2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x0DFFFFFF));
            c2.setPadding(p4, p4, p4, p4);
        }
        if (c3 != null) {
            c3.setBackgroundResource(R.drawable.section_background);
            c3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x0DFFFFFF));
            c3.setPadding(p4, p4, p4, p4);
        }

        setupBands(root);
        loadTargetData();
        alignPeakMetersWithTopCard(root);
    }

    private void alignPeakMetersWithTopCard(View root) {
        if (root == null) return;
        View row1 = root.findViewById(R.id.containerRow1);
        View meterL = root.findViewById(R.id.peakMeterLeft);
        View meterR = root.findViewById(R.id.peakMeterRight);
        if (row1 == null || meterL == null) return;

        View parentL = (View) meterL.getParent();
        View parentR = meterR != null ? (View) meterR.getParent() : null;

        row1.post(() -> {
            int top = row1.getTop();
            if (top > 0) {
                if (parentL != null && parentL.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) parentL.getLayoutParams();
                    if (lp.topMargin != top) {
                        lp.topMargin = top;
                        parentL.setLayoutParams(lp);
                    }
                }
                if (parentR != null && parentR.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) parentR.getLayoutParams();
                    if (lp.topMargin != top) {
                        lp.topMargin = top;
                        parentR.setLayoutParams(lp);
                    }
                }
            }
        });
    }

    private void toggleEqMode() {
        String current = settingsManager != null ? settingsManager.getEqMode() : "EQ";
        String nextMode = ("PQ".equalsIgnoreCase(current) || "PEQ".equalsIgnoreCase(current)) ? "EQ" : "PQ";
        setEqModeDirect(nextMode);
    }

    private void updateEqModeUI(String mode) {
        boolean isPq = "PQ".equalsIgnoreCase(mode) || "PEQ".equalsIgnoreCase(mode);
        if (getContext() == null) return;
        ThemePalette theme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
        float density = getResources().getDisplayMetrics().density;

        GradientDrawable activeGd = new GradientDrawable();
        activeGd.setCornerRadius(17f * density);
        activeGd.setColor(theme.accentColor);

        if (tvSegmentEq != null && tvSegmentPeq != null) {
            if (isPq) {
                tvSegmentPeq.setBackground(activeGd);
                tvSegmentPeq.setTextColor(Color.WHITE);
                tvSegmentEq.setBackground(null);
                tvSegmentEq.setTextColor(Color.parseColor("#80FFFFFF"));
            } else {
                tvSegmentEq.setBackground(activeGd);
                tvSegmentEq.setTextColor(Color.WHITE);
                tvSegmentPeq.setBackground(null);
                tvSegmentPeq.setTextColor(Color.parseColor("#80FFFFFF"));
            }
        }
    }

    private void setupHeaderSwipe(View view) {
        headerSwipeLayout = view.findViewById(R.id.headerSwipeLayout);
        headerNormalView = view.findViewById(R.id.headerNormalView);
        headerGraphView = view.findViewById(R.id.headerGraphView);
        chartEqGraph = view.findViewById(R.id.chartEqGraph);

        View btnToggleGraph = view.findViewById(R.id.btnToggleEqGraph);
        if (btnToggleGraph != null) {
            btnToggleGraph.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                toggleEqView();
            });
        }

        View headerEqualizer = view.findViewById(R.id.headerEqualizer);
        if (headerEqualizer != null) {
            setupTopBarSwipe(headerEqualizer);
        }

        if (chartEqGraph != null && getContext() != null) {
            ThemePalette curTheme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            chartEqGraph.setAccentColor(curTheme.accentColor);
        }

        if (headerSwipeLayout != null) {
            headerSwipeLayout.setOnSwipeListener(new HeaderSwipeLayout.OnSwipeListener() {
                @Override
                public void onSwipeRightToLeft() {
                    // Jari swipe dari kanan ke kiri (geser ke kiri)
                    if (!isShowingGraph) {
                        // Tampilkan grafik EQ: header bergeser ke kiri, grafik masuk dari kanan
                        showEqGraph(true, true);
                    } else {
                        showNormalHeader(true, false);
                    }
                }

                @Override
                public void onSwipeLeftToRight() {
                    // Jari swipe dari kiri ke kanan (geser ke kanan)
                    if (isShowingGraph) {
                        // Kembali ke header normal: grafik bergeser ke kanan, header masuk dari kiri
                        showNormalHeader(true, true);
                    } else {
                        showEqGraph(true, false);
                    }
                }
            });
        }

        if (headerGraphView != null) {
            final View rootView = view;
            headerGraphView.setOnClickListener(v -> {
                // Tap pada grafik = tukar target CH1 <-> CH2 (A/B compare).
                // Diabaikan saat target LINK: hanya CH1 <-> CH2 yang bisa ditukar.
                String next = nextTargetForGraphTap(currentTarget);
                if (next == null) return;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                switchTarget(rootView, next);
            });
        }

        updateEqGraphData();
    }

    private void setupTopBarSwipe(View topBar) {
        final float[] startRawX = new float[1];
        final float[] startRawY = new float[1];
        final float minDistance = 22f * getResources().getDisplayMetrics().density;
        final float touchSlop = android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop();

        topBar.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startRawX[0] = ev.getRawX();
                    startRawY[0] = ev.getRawY();
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getRawX() - startRawX[0];
                    float dy = ev.getRawY() - startRawY[0];
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    float totalDx = ev.getRawX() - startRawX[0];
                    float totalDy = ev.getRawY() - startRawY[0];
                    if (Math.abs(totalDx) >= minDistance && Math.abs(totalDx) > Math.abs(totalDy)) {
                        if (totalDx < 0) {
                            if (!isShowingGraph) showEqGraph(true, true);
                            else showNormalHeader(true, false);
                        } else {
                            if (isShowingGraph) showNormalHeader(true, true);
                            else showEqGraph(true, false);
                        }
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    break;
            }
            return false;
        });
    }

    private void toggleEqView() {
        if (isShowingGraph) {
            showNormalHeader(true, true);
        } else {
            showEqGraph(true, true);
        }
    }

    private void showEqGraph(boolean animate, boolean toLeft) {
        if (isShowingGraph || isHeaderAnimating || headerSwipeLayout == null || headerNormalView == null || headerGraphView == null) return;
        isShowingGraph = true;
        updateEqGraphData();

        int width = headerSwipeLayout.getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;

        if (!animate) {
            headerNormalView.setVisibility(View.GONE);
            headerGraphView.setVisibility(View.VISIBLE);
            headerGraphView.setTranslationX(0);
            headerGraphView.setAlpha(1f);
            return;
        }

        isHeaderAnimating = true;
        headerGraphView.setVisibility(View.VISIBLE);
        // Jari geser ke kiri: konten lama ke kiri (-width), konten baru dari kanan (+width)
        float startGraphX = toLeft ? width : -width;
        float exitNormalX = toLeft ? -width : width;

        headerGraphView.setTranslationX(startGraphX);
        headerGraphView.setAlpha(0.2f);

        headerNormalView.animate()
                .translationX(exitNormalX)
                .alpha(0f)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    if (headerNormalView != null) {
                        headerNormalView.setVisibility(View.GONE);
                        headerNormalView.setTranslationX(0);
                        headerNormalView.setAlpha(1f);
                    }
                    isHeaderAnimating = false;
                })
                .start();

        headerGraphView.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void showNormalHeader(boolean animate, boolean toRight) {
        if (!isShowingGraph || isHeaderAnimating || headerSwipeLayout == null || headerNormalView == null || headerGraphView == null) return;
        isShowingGraph = false;

        int width = headerSwipeLayout.getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;

        if (!animate) {
            headerGraphView.setVisibility(View.GONE);
            headerNormalView.setVisibility(View.VISIBLE);
            headerNormalView.setTranslationX(0);
            headerNormalView.setAlpha(1f);
            return;
        }

        isHeaderAnimating = true;
        headerNormalView.setVisibility(View.VISIBLE);
        // Jari geser ke kanan: grafik lama ke kanan (+width), header normal dari kiri (-width)
        float startNormalX = toRight ? -width : width;
        float exitGraphX = toRight ? width : -width;

        headerNormalView.setTranslationX(startNormalX);
        headerNormalView.setAlpha(0.2f);

        headerGraphView.animate()
                .translationX(exitGraphX)
                .alpha(0f)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    if (headerGraphView != null) {
                        headerGraphView.setVisibility(View.GONE);
                        headerGraphView.setTranslationX(0);
                        headerGraphView.setAlpha(1f);
                    }
                    isHeaderAnimating = false;
                })
                .start();

        headerNormalView.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void updateEqGraphData() {
        if (chartEqGraph != null && settingsManager != null) {
            if (isPeqMode && peqUiController != null) {
                peqUiController.loadTargetData(currentTarget);
            } else {
                int[] bands = settingsManager.getBands(currentTarget);
                String preset = getSelectedPresetName(currentTarget);
                chartEqGraph.setEqBands(bands, currentTarget, preset);
            }
        }
    }

    private void switchTarget(View root, String target) {
        currentTarget = target;
        settingsManager.setActiveTarget(target);

        // Setiap target punya jumlah band sendiri -> sinkronkan mode band saat berpindah target.
        int targetBandMode = settingsManager.getGeqBandCount(target);
        boolean bandModeChanged = false;
        if (targetBandMode != currentGeqBandMode) {
            currentGeqBandMode = targetBandMode;
            currentBandSelectionIndex = getBandSelectionIndexForMode(targetBandMode);
            if (spinnerGeqBands != null) {
                isInitialSpinnerLoad = true; // cegah callback memicu remap lagi
                spinnerGeqBands.setSelection(currentBandSelectionIndex);
            }
            bandModeChanged = true;
        }

        updateTargetSelectionUI(root);
        if (isPeqMode && peqUiController != null) {
            peqUiController.setTarget(target);
        } else {
            if (bandModeChanged && root != null) {
                setupBands(root);
            }
            loadTargetData();
        }
    }

    /**
     * Target berikutnya saat grafik di-tap (A/B compare CH1 <-> CH2).
     * Mengembalikan {@code null} bila tidak boleh berpindah (saat target LINK).
     */
    private String nextTargetForGraphTap(String current) {
        if ("LINK".equals(current)) return null;   // LINK diabaikan
        return "CH1".equals(current) ? "CH2" : "CH1";
    }

    /** Indeks spinner ("30/15/10/7/5 Band") untuk sebuah jumlah band. */
    private int getBandSelectionIndexForMode(int bandCount) {
        switch (bandCount) {
            case 15: return 1;
            case 10: return 2;
            case 7:  return 3;
            case 5:  return 4;
            case 30:
            default: return 0;
        }
    }

    private void updateTargetSelectionUI(View root) {
        if (root == null) return;
        View btnCH1 = root.findViewById(R.id.btnCH1);
        View btnCH2 = root.findViewById(R.id.btnCH2);
        View btnLink = root.findViewById(R.id.btnLink);

        boolean isCH1 = "CH1".equals(currentTarget);
        boolean isCH2 = "CH2".equals(currentTarget);
        boolean isLink = "LINK".equals(currentTarget);

        if (btnCH1 != null) btnCH1.setSelected(isCH1);
        if (btnCH2 != null) btnCH2.setSelected(isCH2);
        if (btnLink != null) btnLink.setSelected(isLink);

        if (getContext() != null) {
            ThemePalette theme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            updateChannelPill(btnCH1, isCH1, theme.accentColor);
            updateChannelPill(btnCH2, isCH2, theme.accentColor);
            updateChannelPill(btnLink, isLink, theme.accentColor);
        }
        
        updateChannelTexts(root);
    }

    private void updateChannelPill(View view, boolean isSelected, int accentColor) {
        if (view == null || getContext() == null) return;
        float density = view.getContext().getResources().getDisplayMetrics().density;
        int cornerRadius = (int) (20 * density);
        int strokeWidth = (int) (1 * density);

        if (isSelected) {
            GradientDrawable outer = new GradientDrawable();
            outer.setShape(GradientDrawable.RECTANGLE);
            outer.setCornerRadius(cornerRadius);
            outer.setColor(0x1AFFFFFF);
            outer.setStroke(strokeWidth, 0x33FFFFFF);

            GradientDrawable inner = new GradientDrawable();
            inner.setShape(GradientDrawable.RECTANGLE);
            inner.setCornerRadius((int) (17 * density));
            inner.setColor(accentColor);

            int inset = (int) (3 * density);
            LayerDrawable layer = new LayerDrawable(new Drawable[]{outer, inner});
            layer.setLayerInset(1, inset, inset, inset, inset);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                layer.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
                layer.setPadding(0, 0, 0, 0);
            }
            view.setBackground(layer);
        } else {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(cornerRadius);
            gd.setColor(0x0DFFFFFF);
            gd.setStroke(strokeWidth, 0x26FFFFFF);
            view.setBackground(gd);
        }
    }

    private void loadTargetData() {
        int[] bands = settingsManager.getBands(currentTarget);
        float[] bandsFloat = toFloatArray(bands);

        if (bands.length == currentGeqBandMode) {
            currentBandGains = bandsFloat;
        } else {
            currentBandGains = interpolateGains(getFreqsForBandMode(bands.length), bandsFloat, getCurrentFreqs());
        }

        for (int i = 0; i < bandSeekBars.size() && i < currentBandGains.length; i++) {
            int p = Math.max(0, Math.min(24, Math.round(currentBandGains[i])));
            bandSeekBars.get(i).setProgress(p);
            updateGainText(bandGainTexts.get(i), p);
        }
        updateEqGraphData();
    }

    private void updateGainText(TextView tv, int progress) {
        int gain = progress - 12;
        String gainText = (gain > 0 ? "+" : "") + gain + "dB";
        tv.setText(gainText);
    }

    @Override
    public void onThemeChanged(ThemePalette theme) {
        applyTheme(theme);
    }

    public void applyTheme(ThemePalette theme) {
        if (theme == null || getView() == null || getContext() == null) return;
        View root = getView();
        root.setBackgroundColor(theme.backgroundColor);

        View btnTheme = root.findViewById(R.id.btnThemeEqualizer);
        if (btnTheme instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) btnTheme).setColorFilter(theme.accentColor);
        }

        View btnSave = root.findViewById(R.id.btnSavePresetEqualizer);
        if (btnSave instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) btnSave).setColorFilter(Color.WHITE);
        }

        View btnBands = root.findViewById(R.id.btnGeqBands);
        if (btnBands instanceof ImageButton) {
            ((ImageButton) btnBands).setColorFilter(Color.WHITE);
        }

        if (btnPowerEqualizer != null && settingsManager != null) {
            updatePowerButtonUI(btnPowerEqualizer, settingsManager.isEqEnabled(), theme.accentColor);
        }

        ColorStateList accentCsl = ColorStateList.valueOf(theme.accentColor);
        for (SeekBar sb : bandSeekBars) {
            sb.setProgressTintList(accentCsl);
            sb.setThumbTintList(ColorStateList.valueOf(android.graphics.Color.WHITE));
        }
        for (TextView tv : bandGainTexts) {
            tv.setTextColor(theme.accentColor);
        }

        if (chartEqGraph != null) {
            chartEqGraph.setAccentColor(theme.accentColor);
        }

        updateTargetSelectionUI(root);

        TextView tvRow1 = root.findViewById(R.id.tvRow1Title);
        TextView tvRow2 = root.findViewById(R.id.tvRow2Title);
        TextView tvRow3 = root.findViewById(R.id.tvRow3Title);
        if (tvRow1 != null) tvRow1.setTextColor(theme.accentColor);
        if (tvRow2 != null) tvRow2.setTextColor(theme.accentColor);
        if (tvRow3 != null) tvRow3.setTextColor(theme.accentColor);

        if (volumePeakHelper != null) {
            volumePeakHelper.applyTheme(theme);
        }

        if (isPeqMode && peqUiController != null) {
            peqUiController.applyTheme(theme);
        }

        if (tvEqPopup != null && getContext() != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(root.getContext().getResources().getDisplayMetrics().density * 12);
            gd.setColor(theme.accentColor);
            tvEqPopup.setBackground(gd);
        }

        if (btnAlwaysOn != null && settingsManager != null) {
            updateAlwaysOnUI(settingsManager.isAlwaysOnScreen(), theme.accentColor);
        }

        updateEqModeUI(settingsManager != null ? settingsManager.getEqMode() : "EQ");
        updateProLockState();
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
        overlay.setOnTouchListener((v, event) -> true); // Blokir seluruh sentuhan ke seekbar di bawah

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

    private void setupAlwaysOnButton(View root) {
        btnAlwaysOn = root.findViewById(R.id.btnAlwaysOnEqualizer);
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

    private void setupEqualizerToggle(View root) {
        View btnTheme = root.findViewById(R.id.btnThemeEqualizer);
        if (btnTheme instanceof android.widget.ImageButton) {
            android.widget.ImageButton ibTheme = (android.widget.ImageButton) btnTheme;
            ThemePalette cur = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            ibTheme.setColorFilter(cur.accentColor);
            ibTheme.setOnClickListener(v -> {
                ThemePalette newTheme = ThemeManager.getInstance(requireContext()).cycleTheme();
                android.widget.Toast.makeText(requireContext(), newTheme.name, android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        btnPowerEqualizer = root.findViewById(R.id.btnPowerEqualizer);
        if (btnPowerEqualizer != null) {
            boolean isEnabled = settingsManager.isEqEnabled();
            ThemePalette cur = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            updatePowerButtonUI(btnPowerEqualizer, isEnabled, cur.accentColor);
            updateEqVisualState(root, isEnabled);

            btnPowerEqualizer.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                boolean next = !settingsManager.isEqEnabled();
                settingsManager.setEqEnabled(next);
                ThemePalette theme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
                updatePowerButtonUI(btnPowerEqualizer, next, theme.accentColor);
                updateEqVisualState(root, next);
            });
        }
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

    private void updateEqVisualState(View root, boolean isEnabled) {
        float alpha = isEnabled ? 1.0f : 0.5f;
        View row1 = root.findViewById(R.id.containerRow1);
        View row2 = root.findViewById(R.id.containerRow2);
        View row3 = root.findViewById(R.id.containerRow3);
        if (row1 != null) row1.setAlpha(alpha);
        if (row2 != null) row2.setAlpha(alpha);
        if (row3 != null) row3.setAlpha(alpha);
    }

    private void setupBands(View root) {
        bandSeekBars.clear();
        bandGainTexts.clear();

        String[] row1Freqs;
        String[] row2Freqs;
        String[] row3Freqs;
        int startIndexRow1 = 0;
        int startIndexRow2;
        int startIndexRow3;

        switch (currentGeqBandMode) {
            case 15:
                row1Freqs = new String[]{"25", "40", "63", "100", "160"};
                row2Freqs = new String[]{"250", "400", "630", "1k", "1.6k"};
                row3Freqs = new String[]{"2.5k", "4k", "6.3k", "10k", "16k"};
                startIndexRow2 = row1Freqs.length;
                startIndexRow3 = startIndexRow2 + row2Freqs.length;
                break;
            case 10:
                row1Freqs = new String[]{"31.5", "63", "125"};
                row2Freqs = new String[]{"250", "500", "1k", "2k"};
                row3Freqs = new String[]{"4k", "8k", "16k"};
                startIndexRow2 = row1Freqs.length;
                startIndexRow3 = startIndexRow2 + row2Freqs.length;
                break;
            case 7:
                row1Freqs = new String[]{"63", "160"};
                row2Freqs = new String[]{"400", "1k", "2.5k"};
                row3Freqs = new String[]{"6.3k", "16k"};
                startIndexRow2 = row1Freqs.length;
                startIndexRow3 = startIndexRow2 + row2Freqs.length;
                break;
            case 5:
                row1Freqs = new String[]{"63", "250"};
                row2Freqs = new String[]{"1k"};
                row3Freqs = new String[]{"4k", "16k"};
                startIndexRow2 = row1Freqs.length;
                startIndexRow3 = startIndexRow2 + row2Freqs.length;
                break;
            case 30:
            default:
                row1Freqs = new String[]{"25", "31.5", "40", "50", "63", "80", "100", "125", "160", "200"};
                row2Freqs = new String[]{"250", "315", "400", "500", "630", "800", "1k", "1.25k", "1.6k", "2k"};
                row3Freqs = new String[]{"2.5k", "3.15k", "4k", "5k", "6.3k", "8k", "10k", "12.5k", "16k", "20k"};
                startIndexRow2 = 10;
                startIndexRow3 = 20;
                break;
        }

        populateContainer(root.findViewById(R.id.containerRow1), row1Freqs, startIndexRow1);
        populateContainer(root.findViewById(R.id.containerRow2), row2Freqs, startIndexRow2);
        populateContainer(root.findViewById(R.id.containerRow3), row3Freqs, startIndexRow3);
    }

    private void populateContainer(LinearLayout container, String[] freqs, int startIndex) {
        if (container == null) return;
        container.removeAllViews();
        for (int i = 0; i < freqs.length; i++) {
            final int sliderIndex = startIndex + i;
            View bandView = getLayoutInflater().inflate(R.layout.item_equalizer_band, container, false);
            TextView tvFreq = bandView.findViewById(R.id.tvFrequency);
            TextView tvGain = bandView.findViewById(R.id.tvGainValue);
            SeekBar seekBar = bandView.findViewById(R.id.seekBarBand);

            ThemePalette curTheme = ThemeManager.getInstance(requireContext()).getCurrentTheme();
            tvGain.setTextColor(curTheme.accentColor);
            seekBar.setProgressTintList(ColorStateList.valueOf(curTheme.accentColor));

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
            tvGain.setOnClickListener(v -> triggerGainTapFeedback(tvGain, seekBar, sliderIndex));

            tvFreq.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    setPressedVisual(tvFreq);
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    resetValueVisual(tvFreq, false);
                }
                return false;
            });
            tvFreq.setOnClickListener(v -> triggerFreqTapFeedback(tvFreq, seekBar, sliderIndex));

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int lastProgress = -1;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateGainText(tvGain, progress);
                    if (tvEqPopup != null) {
                        updateGainText(tvEqPopup, progress);
                    }
                    if (fromUser) {
                        if (progress != lastProgress) {
                            seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                            lastProgress = progress;
                        }
                        onBandGainChanged(sliderIndex, progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    lastProgress = seekBar.getProgress();
                    if (tvEqPopup != null) {
                        updateGainText(tvEqPopup, seekBar.getProgress());
                        updateEqPopupPosition(tvGain, tvEqPopup);
                        tvEqPopup.animate().alpha(1f).setDuration(120).start();
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (tvEqPopup != null) {
                        tvEqPopup.animate().alpha(0f).setDuration(200).start();
                    }
                }
            });
            container.addView(bandView);
        }
    }

    private void updateEqPopupPosition(View localView, TextView popup) {
        if (popup == null || localView == null) return;
        if (popup.getWidth() == 0 || popup.getHeight() == 0) {
            popup.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
        }

        int popupW = popup.getWidth() > 0 ? popup.getWidth() : popup.getMeasuredWidth();
        int popupH = popup.getHeight() > 0 ? popup.getHeight() : popup.getMeasuredHeight();

        int[] localLoc = new int[2];
        localView.getLocationInWindow(localLoc);

        View root = (View) popup.getParent();
        if (root == null) return;
        int[] rootLoc = new int[2];
        root.getLocationInWindow(rootLoc);

        float targetXInRoot = localLoc[0] - rootLoc[0] + (localView.getWidth() / 2f) - (popupW / 2f);
        float targetYInRoot = localLoc[1] - rootLoc[1] + (localView.getHeight() / 2f) - (popupH / 2f);

        if (root.getWidth() > 0) {
            targetXInRoot = Math.max(8, Math.min(root.getWidth() - popupW - 8, targetXInRoot));
        }
        targetYInRoot = Math.max(8, targetYInRoot);

        popup.setX(targetXInRoot);
        popup.setY(targetYInRoot);
    }

    private void setPressedVisual(TextView tv) {
        if (tv == null || getContext() == null) return;
        int accentColor = ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor;
        Drawable bg = ContextCompat.getDrawable(requireContext(), R.drawable.pill_value_popup);
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
        if (tv == null || getContext() == null) return;
        int pl = tv.getPaddingLeft();
        int pt = tv.getPaddingTop();
        int pr = tv.getPaddingRight();
        int pb = tv.getPaddingBottom();
        tv.setBackground(null);
        tv.setPadding(pl, pt, pr, pb);
        if (isGainValue) {
            tv.setTextColor(ThemeManager.getInstance(requireContext()).getCurrentTheme().accentColor);
        } else {
            tv.setTextColor(Color.WHITE);
            tv.setAlpha(0.8f);
        }
    }

    private void triggerGainTapFeedback(TextView tvGain, SeekBar seekBar, int sliderIndex) {
        if (tvGain == null || seekBar == null || getContext() == null) return;
        int current = seekBar.getProgress();
        if (current < 24) {
            int next = current + 1;
            seekBar.setProgress(next);
            seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            onBandGainChanged(sliderIndex, next);
        }
        applyValueTapVisual(tvGain, true);
    }

    private void triggerFreqTapFeedback(TextView tvFreq, SeekBar seekBar, int sliderIndex) {
        if (tvFreq == null || seekBar == null || getContext() == null) return;
        int current = seekBar.getProgress();
        if (current > 0) {
            int next = current - 1;
            seekBar.setProgress(next);
            seekBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            onBandGainChanged(sliderIndex, next);
        }
        applyValueTapVisual(tvFreq, false);
    }

    private void applyValueTapVisual(TextView tv, boolean isGainValue) {
        if (tv == null || getContext() == null) return;
        setPressedVisual(tv);
        tv.animate().cancel();
        tv.animate().alpha(1.0f).setDuration(120).withEndAction(() -> resetValueVisual(tv, isGainValue)).start();
    }
}
