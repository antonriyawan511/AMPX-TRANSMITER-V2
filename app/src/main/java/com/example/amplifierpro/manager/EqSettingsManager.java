package com.example.amplifierpro.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.amplifierpro.audio.EqProcessor;
import com.example.amplifierpro.audio.Presets;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EqSettingsManager {
    private static final String PREFS_NAME = "eq_settings";
    private static final String KEY_MASTER_VOLUME = "master_volume";
    private static final String KEY_ACTIVE_TARGET = "active_target"; // Renamed from profile to target
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_AUDIO_EFFECTS_ENABLED = "audio_effects_enabled";
    private static final String KEY_BAND_PREFIX = "eq_band_";
    private static final String KEY_PRESET_PREFIX = "eq_preset_";
    private static final String KEY_STEREO_WIDTH_ENABLED = "stereo_width_enabled";
    private static final String KEY_STEREO_WIDTH_VALUE = "stereo_width_value";
    private static final String KEY_VIRTUAL_BASS_ENABLED = "virtual_bass_enabled";
    private static final String KEY_VIRTUAL_BASS_AMOUNT = "virtual_bass_amount";
    private static final String KEY_VIRTUAL_BASS_MIN_FREQ = "virtual_bass_min_freq";
    private static final String KEY_VIRTUAL_BASS_MAX_FREQ = "virtual_bass_max_freq";
    private static final String KEY_COMPRESSOR_ENABLED = "compressor_enabled";
    private static final String KEY_COMPRESSOR_THRESHOLD = "compressor_threshold";
    private static final String KEY_COMPRESSOR_RATIO = "compressor_ratio";
    private static final String KEY_COMPRESSOR_ATTACK = "compressor_attack";
    private static final String KEY_COMPRESSOR_RELEASE = "compressor_release";
    private static final String KEY_COMPRESSOR_MAKEUP = "compressor_makeup";
    private static final String KEY_COMPRESSOR_PRESET = "compressor_preset";
    private static final String KEY_LIMITER_ENABLED = "limiter_enabled";
    private static final String KEY_LIMITER_CEILING = "limiter_ceiling";
    private static final String KEY_LIMITER_RELEASE_MODE = "limiter_release_mode";
    private static final String KEY_LIMITER_RELEASE_MANUAL = "limiter_release_manual";
    private static final String KEY_LIMITER_LOOKAHEAD = "limiter_lookahead";
    private static final String KEY_LIMITER_PRESET = "limiter_preset";
    private static final String KEY_CH1_GAIN = "ch1_gain";
    private static final String KEY_CH2_GAIN = "ch2_gain";
    private static final String KEY_RECEIVER_VOLUME = "receiver_volume";
    // Volume & kapabilitas disimpan PER-IP receiver agar tidak saling menimpa saat
    // user berpindah perangkat (mis. Redmi -> samsung punya jumlah step berbeda).
    private static final String KEY_RECEIVER_VOL_PREFIX = "receiver_volume_";
    private static final String KEY_RECEIVER_VOLMAX_PREFIX = "receiver_volmax_";
    private static final String KEY_ALWAYS_ON_SCREEN = "always_on_screen";
    private static final String KEY_STREAM_BUFFER_SIZE = "stream_buffer_size";
    private static final String KEY_USER_PRESETS_JSON = "user_presets_json";
    private static final String KEY_EQ_MODE = "eq_mode";
    public static final String KEY_GEQ_BAND_COUNT = "geq_band_count";
    private static final String KEY_GEQ_BAND_COUNT_PREFIX = "geq_band_count_";
    private static final int MAX_GEQ_BANDS = 30;

    public static final String ACTION_TARGET_UPDATED = "com.example.amplifierpro.TARGET_UPDATED";
    public static final String ACTION_EQ_VALUE_CHANGED = "com.example.amplifierpro.EQ_VALUE_CHANGED";
    public static final String ACTION_VOLUME_UPDATED = "com.example.amplifierpro.VOLUME_UPDATED";
    public static final String ACTION_BYPASS_UPDATED = "com.example.amplifierpro.BYPASS_UPDATED"; // For EQ
    public static final String ACTION_EFFECTS_MASTER_UPDATED = "com.example.amplifierpro.EFFECTS_MASTER_UPDATED";
    public static final String ACTION_STEREO_WIDTH_UPDATED = "com.example.amplifierpro.STEREO_WIDTH_UPDATED";
    public static final String ACTION_VIRTUAL_BASS_UPDATED = "com.example.amplifierpro.VIRTUAL_BASS_UPDATED";
    public static final String ACTION_COMPRESSOR_UPDATED = "com.example.amplifierpro.COMPRESSOR_UPDATED";
    public static final String ACTION_LIMITER_UPDATED = "com.example.amplifierpro.LIMITER_UPDATED";
    public static final String ACTION_CHANNEL_GAIN_UPDATED = "com.example.amplifierpro.CHANNEL_GAIN_UPDATED";
    public static final String ACTION_RECEIVER_VOLUME_UPDATED = "com.example.amplifierpro.RECEIVER_VOLUME_UPDATED";
    public static final String ACTION_STREAM_BUFFER_UPDATED = "com.example.amplifierpro.STREAM_BUFFER_UPDATED";
    public static final String ACTION_RECEIVER_VOL_UP = "com.example.amplifierpro.RECEIVER_VOL_UP";
    public static final String ACTION_RECEIVER_VOL_DOWN = "com.example.amplifierpro.RECEIVER_VOL_DOWN";
    public static final String ACTION_RECEIVER_VOL_STEP = "com.example.amplifierpro.RECEIVER_VOL_STEP";
    public static final String ACTION_RECEIVER_VOL_SET = "com.example.amplifierpro.RECEIVER_VOL_SET";
    public static final String ACTION_RECEIVER_VOL_CAPS_REQ = "com.example.amplifierpro.RECEIVER_VOL_CAPS_REQ";
    public static final String EXTRA_VOL_SEQ = "vol_seq";
    public static final String EXTRA_VOL_DELTA = "vol_delta";
    public static final String ACTION_ALWAYS_ON_SCREEN_UPDATED = "com.example.amplifierpro.ALWAYS_ON_SCREEN_UPDATED";
    public static final String ACTION_EQ_MODE_CHANGED = "com.example.amplifierpro.EQ_MODE_CHANGED";
    public static final String ACTION_PEQ_VALUE_CHANGED = "com.example.amplifierpro.PEQ_VALUE_CHANGED";
    
    public static final String EXTRA_FROM_RECEIVER = "from_receiver";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PEQ_FREQ = "peq_freq";
    public static final String EXTRA_PEQ_GAIN = "peq_gain";
    public static final String EXTRA_PEQ_Q = "peq_q";

    private static final String KEY_PEQ_FREQ_PREFIX = "peq_freq_";
    private static final String KEY_PEQ_GAIN_PREFIX = "peq_gain_";
    private static final String KEY_PEQ_Q_PREFIX = "peq_q_";

    public static final float[] DEFAULT_PEQ_FREQS = {50f, 200f, 1000f, 4000f};
    public static final float[] DEFAULT_PEQ_GAINS = {0f, 0f, 0f, 0f};
    public static final float[] DEFAULT_PEQ_QS = {1.0f, 1.0f, 1.0f, 1.0f};

    public static final float[] PEQ_MIN_FREQS = {20f, 80f, 400f, 1500f};
    public static final float[] PEQ_MAX_FREQS = {200f, 1000f, 6000f, 20000f};
    
    public static final String EXTRA_TARGET = "target";
    public static final String EXTRA_BAND_INDEX = "band_index";
    public static final String EXTRA_VALUE = "value";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_MIN_FREQ = "min_freq";
    public static final String EXTRA_MAX_FREQ = "max_freq";
    public static final String EXTRA_THRESHOLD = "threshold";
    public static final String EXTRA_RATIO = "ratio";
    public static final String EXTRA_ATTACK = "attack";
    public static final String EXTRA_RELEASE = "release";
    public static final String EXTRA_MAKEUP = "makeup";
    public static final String EXTRA_PRESET = "preset";
    public static final String EXTRA_CEILING = "ceiling";
    public static final String EXTRA_LOOKAHEAD = "lookahead";
    public static final String EXTRA_RELEASE_MODE = "release_mode";
    public static final String EXTRA_CH1_GAIN = "ch1_gain";
    public static final String EXTRA_CH2_GAIN = "ch2_gain";
    public static final String EXTRA_RECEIVER_VOL_MAX = "receiver_vol_max";
    public static final String EXTRA_RECEIVER_IP = "receiver_ip_extra";
    public static final String EXTRA_BAND_COUNT = "band_count";

    private final SharedPreferences prefs;
    private final Context context;
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;
    private SharedPreferences.Editor pendingEditor;
    // Cache DIBAGI antar instance (UI & Service berada di proses yang sama dan memakai
    // SharedPreferences yang sama). Tanpa ini, Service bisa membaca cache basi sehingga
    // perubahan preset/band tidak langsung terdengar (harus geser slider dulu).
    private static final java.util.Map<String, int[]> bandsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Integer cachedMasterVolume = null;

    private static final String KEY_DUMMY_CLEARED = "eq_dummy_presets_cleared_v1";

    public EqSettingsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cleanupDummyPresetsIfNeeded();
        loadSavedUserPresetsIntoMap();
    }

    public int getMasterVolume() {
        if (cachedMasterVolume != null) {
            return cachedMasterVolume;
        }
        int val = prefs.getInt(KEY_MASTER_VOLUME, 100);
        cachedMasterVolume = val;
        return val;
    }

    public String getActiveTarget() {
        return prefs.getString(KEY_ACTIVE_TARGET, "CH1");
    }

    public boolean isEqEnabled() {
        return prefs.getBoolean(KEY_EQ_ENABLED, true);
    }

    public void setEqEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_BYPASS_UPDATED);
        intent.putExtra(EXTRA_VALUE, enabled);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public boolean isAudioEffectsEnabled() {
        return prefs.getBoolean(KEY_AUDIO_EFFECTS_ENABLED, true);
    }

    public void setAudioEffectsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUDIO_EFFECTS_ENABLED, enabled).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_EFFECTS_MASTER_UPDATED);
        intent.putExtra(EXTRA_VALUE, enabled);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public void setActiveTarget(String target) {
        prefs.edit().putString(KEY_ACTIVE_TARGET, target).apply();
        
        // Notify Service about target change
        android.content.Intent intent = new android.content.Intent(ACTION_TARGET_UPDATED);
        intent.putExtra(EXTRA_TARGET, target);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public boolean isAlwaysOnScreen() {
        return prefs.getBoolean(KEY_ALWAYS_ON_SCREEN, false);
    }

    public void setAlwaysOnScreen(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALWAYS_ON_SCREEN, enabled).apply();
        android.content.Intent intent = new android.content.Intent(ACTION_ALWAYS_ON_SCREEN_UPDATED);
        intent.putExtra(EXTRA_VALUE, enabled);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public String getSelectedPreset(String target) {
        return prefs.getString(KEY_PRESET_PREFIX + target, "Flat");
    }

    public void saveSelectedPreset(String target, String presetName) {
        prefs.edit().putString(KEY_PRESET_PREFIX + target, presetName).apply();
    }

    public String getEqMode() {
        return prefs.getString(KEY_EQ_MODE, "EQ");
    }

    public void setEqMode(String mode) {
        prefs.edit().putString(KEY_EQ_MODE, mode).apply();
        android.content.Intent intent = new android.content.Intent(ACTION_EQ_MODE_CHANGED);
        intent.putExtra(EXTRA_MODE, mode);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /** Jumlah band global (legacy). Dipertahankan untuk kompatibilitas. */
    public int getGeqBandCount() {
        return prefs.getInt(KEY_GEQ_BAND_COUNT, 30);
    }

    /** Jumlah band global (legacy). Dipertahankan untuk kompatibilitas. */
    public void setGeqBandCount(int count) {
        prefs.edit().putInt(KEY_GEQ_BAND_COUNT, count).apply();
    }

    /**
     * Jumlah band PER-TARGET. Mencegah satu target mengubah panjang data target lain
     * (akar bug: CH2 dibaca dengan panjang baru padahal datanya masih panjang lama).
     */
    public int getGeqBandCount(String target) {
        if (target == null) return getGeqBandCount();
        if (prefs.contains(KEY_GEQ_BAND_COUNT_PREFIX + target)) {
            return prefs.getInt(KEY_GEQ_BAND_COUNT_PREFIX + target, 30);
        }
        return getGeqBandCount(); // migrasi dari nilai global lama
    }

    public void setGeqBandCount(String target, int count) {
        if (target == null) {
            setGeqBandCount(count);
            return;
        }
        prefs.edit().putInt(KEY_GEQ_BAND_COUNT_PREFIX + target, count).apply();
    }

    public float getPeqFreq(String target, int bandIndex) {
        if (bandIndex < 0 || bandIndex >= 4) return 1000f;
        return prefs.getFloat(KEY_PEQ_FREQ_PREFIX + target + "_" + bandIndex, DEFAULT_PEQ_FREQS[bandIndex]);
    }

    public float getPeqGain(String target, int bandIndex) {
        if (bandIndex < 0 || bandIndex >= 4) return 0f;
        return prefs.getFloat(KEY_PEQ_GAIN_PREFIX + target + "_" + bandIndex, DEFAULT_PEQ_GAINS[bandIndex]);
    }

    public float getPeqQ(String target, int bandIndex) {
        if (bandIndex < 0 || bandIndex >= 4) return 1.0f;
        return prefs.getFloat(KEY_PEQ_Q_PREFIX + target + "_" + bandIndex, DEFAULT_PEQ_QS[bandIndex]);
    }

    public void updatePeqBand(String target, int bandIndex, float freq, float gain, float q) {
        if (bandIndex < 0 || bandIndex >= 4) return;
        SharedPreferences.Editor editor = prefs.edit();
        if ("LINK".equals(target)) {
            editor.putFloat(KEY_PEQ_FREQ_PREFIX + "LINK_" + bandIndex, freq);
            editor.putFloat(KEY_PEQ_GAIN_PREFIX + "LINK_" + bandIndex, gain);
            editor.putFloat(KEY_PEQ_Q_PREFIX + "LINK_" + bandIndex, q);
            editor.putFloat(KEY_PEQ_FREQ_PREFIX + "CH1_" + bandIndex, freq);
            editor.putFloat(KEY_PEQ_GAIN_PREFIX + "CH1_" + bandIndex, gain);
            editor.putFloat(KEY_PEQ_Q_PREFIX + "CH1_" + bandIndex, q);
            editor.putFloat(KEY_PEQ_FREQ_PREFIX + "CH2_" + bandIndex, freq);
            editor.putFloat(KEY_PEQ_GAIN_PREFIX + "CH2_" + bandIndex, gain);
            editor.putFloat(KEY_PEQ_Q_PREFIX + "CH2_" + bandIndex, q);
        } else {
            editor.putFloat(KEY_PEQ_FREQ_PREFIX + target + "_" + bandIndex, freq);
            editor.putFloat(KEY_PEQ_GAIN_PREFIX + target + "_" + bandIndex, gain);
            editor.putFloat(KEY_PEQ_Q_PREFIX + target + "_" + bandIndex, q);
        }
        editor.apply();

        android.content.Intent intent = new android.content.Intent(ACTION_PEQ_VALUE_CHANGED);
        intent.putExtra(EXTRA_TARGET, target);
        intent.putExtra(EXTRA_BAND_INDEX, bandIndex);
        intent.putExtra(EXTRA_PEQ_FREQ, freq);
        intent.putExtra(EXTRA_PEQ_GAIN, gain);
        intent.putExtra(EXTRA_PEQ_Q, q);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public synchronized List<String> getUserPresetNames() {
        List<String> names = new ArrayList<>();
        try {
            String jsonStr = prefs.getString(KEY_USER_PRESETS_JSON, null);
            if (jsonStr != null) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    names.add(obj.getString("name"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return names;
    }

    public synchronized int[] getUserPresetBands(String name) {
        try {
            String jsonStr = prefs.getString(KEY_USER_PRESETS_JSON, null);
            if (jsonStr != null) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (obj.getString("name").equals(name)) {
                        JSONArray bArr = obj.getJSONArray("bands");
                        // Slot kosong = flat (12 = 0 dB), BUKAN 0 (yang berarti full cut).
                        int[] bands = new int[30];
                        Arrays.fill(bands, 12);
                        for (int b = 0; b < 30 && b < bArr.length(); b++) {
                            bands[b] = bArr.getInt(b);
                        }
                        return bands;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        int[] flat = new int[30];
        Arrays.fill(flat, 12);
        return flat;
    }

    public synchronized int[] getPresetBands(String presetName) {
        if (isUserPreset(presetName)) {
            return getUserPresetBands(presetName);
        }
        return Presets.ALL_PRESETS.get(presetName);
    }

    public synchronized boolean isUserPreset(String name) {
        List<String> userPresets = getUserPresetNames();
        return userPresets.contains(name);
    }

    public synchronized int getNextUserPresetIndex() {
        List<String> userPresets = getUserPresetNames();
        int maxNum = 0;
        for (String name : userPresets) {
            if (name.startsWith("User ")) {
                try {
                    int num = Integer.parseInt(name.substring(5).trim());
                    if (num > maxNum) maxNum = num;
                } catch (Exception ignored) {}
            }
        }
        return maxNum + 1;
    }

    public synchronized void saveUserPreset(String name, int[] bands) {
        try {
            // Normalisasi ke grid 30-band agar preset seragam dengan preset bawaan dan
            // dapat dimuat pada jumlah band berapa pun secara lossless (semua grid band
            // 5/7/10/15 adalah subset persis dari grid 30-band).
            int[] normalized = normalizeTo30Bands(bands);

            String jsonStr = prefs.getString(KEY_USER_PRESETS_JSON, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            boolean found = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.getString("name").equals(name)) {
                    JSONArray bArr = new JSONArray();
                    for (int b : normalized) bArr.put(b);
                    obj.put("bands", bArr);
                    found = true;
                    break;
                }
            }
            if (!found) {
                JSONObject newObj = new JSONObject();
                newObj.put("name", name);
                JSONArray bArr = new JSONArray();
                for (int b : normalized) bArr.put(b);
                newObj.put("bands", bArr);
                arr.put(newObj);
            }
            prefs.edit().putString(KEY_USER_PRESETS_JSON, arr.toString()).apply();
            Presets.ALL_PRESETS.put(name, normalized);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Normalisasi array band ke grid 30-band.
     * Slot yang tidak terpakai diisi flat (12 = 0 dB).
     * Bila sumber bukan 30 band, gain di-remap logaritmik ke grid 30-band.
     */
    private static int[] normalizeTo30Bands(int[] bands) {
        int[] out = new int[30];
        Arrays.fill(out, 12);
        if (bands == null || bands.length == 0) return out;
        if (bands.length == 30) {
            System.arraycopy(bands, 0, out, 0, 30);
            return out;
        }
        int[] remapped = EqProcessor.remapGains(bands, 30);
        if (remapped != null && remapped.length == 30) {
            System.arraycopy(remapped, 0, out, 0, 30);
        }
        return out;
    }

    public synchronized boolean deleteUserPreset(String name) {
        try {
            String jsonStr = prefs.getString(KEY_USER_PRESETS_JSON, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            JSONArray newArr = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.getString("name").equals(name)) {
                    removed = true;
                } else {
                    newArr.put(obj);
                }
            }
            if (removed) {
                prefs.edit().putString(KEY_USER_PRESETS_JSON, newArr.toString()).apply();
                Presets.ALL_PRESETS.remove(name);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public synchronized boolean renameUserPreset(String oldName, String newName) {
        try {
            String jsonStr = prefs.getString(KEY_USER_PRESETS_JSON, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.getString("name").equals(oldName)) {
                    obj.put("name", newName);
                    prefs.edit().putString(KEY_USER_PRESETS_JSON, arr.toString()).apply();
                    int[] bands = Presets.ALL_PRESETS.remove(oldName);
                    if (bands != null) {
                        Presets.ALL_PRESETS.put(newName, bands);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private synchronized void cleanupDummyPresetsIfNeeded() {
        if (!prefs.getBoolean(KEY_DUMMY_CLEARED, false)) {
            try {
                String jsonStr = prefs.getString(KEY_USER_PRESETS_JSON, null);
                if (jsonStr != null) {
                    JSONArray arr = new JSONArray(jsonStr);
                    JSONArray newArr = new JSONArray();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String name = obj.optString("name", "");
                        if (name.equals("User 1") || name.equals("User 2") || name.equals("User 3") || name.equals("User 4")) {
                            Presets.ALL_PRESETS.remove(name);
                            for (String t : new String[]{"LINK", "CH1", "CH2"}) {
                                if (name.equals(prefs.getString(KEY_PRESET_PREFIX + t, null))) {
                                    saveSelectedPreset(t, "Flat");
                                }
                            }
                        } else {
                            newArr.put(obj);
                        }
                    }
                    prefs.edit().putString(KEY_USER_PRESETS_JSON, newArr.toString()).apply();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            prefs.edit().putBoolean(KEY_DUMMY_CLEARED, true).apply();
        }
    }

    private synchronized void loadSavedUserPresetsIntoMap() {
        try {
            String jsonStr = prefs.getString(KEY_USER_PRESETS_JSON, null);
            if (jsonStr != null) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.getString("name");
                    JSONArray bArr = obj.getJSONArray("bands");
                    // Slot kosong = flat (12 = 0 dB), bukan 0 (full cut).
                    int[] bands = new int[30];
                    Arrays.fill(bands, 12);
                    for (int b = 0; b < 30 && b < bArr.length(); b++) {
                        bands[b] = bArr.getInt(b);
                    }
                    Presets.ALL_PRESETS.put(name, bands);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized int[] getBands(String target) {
        int count = getGeqBandCount(target);
        int[] cached = bandsCache.get(target);
        if (cached != null && cached.length == count) {
            return Arrays.copyOf(cached, cached.length);
        }
        int[] bands = new int[count];
        for (int i = 0; i < count; i++) {
            bands[i] = prefs.getInt(KEY_BAND_PREFIX + target + "_" + i, 12);
        }
        bandsCache.put(target, Arrays.copyOf(bands, bands.length));
        return bands;
    }

    /**
     * Menulis seluruh band untuk SATU target.
     *
     * Perbaikan penting:
     * 1. Jumlah band disimpan PER-TARGET, bukan global — target lain tidak lagi ikut
     *    berubah panjangnya (dulu ini menyebabkan data CH2 dibaca dengan panjang baru
     *    padahal array lamanya masih panjang lama -> suara tidak sesuai mapping).
     * 2. Slot di atas jumlah band baru di-reset ke 0 (flat) supaya tidak ada data basi
     *    yang muncul lagi saat kembali ke jumlah band besar.
     * 3. Cache dibersihkan agar instance lain (Service) langsung membaca data terbaru.
     */
    public synchronized void updateAllBands(String target, int[] bands) {
        updateAllBands(target, bands, true);
    }

    /**
     * Versi batch: {@code sendBroadcast=false} dipakai saat menulis beberapa target sekaligus
     * (mis. remap jumlah band untuk CH1/CH2/LINK). Broadcast dikirim sekali di akhir agar
     * EqProcessor tidak di-reset berulang kali (menghindari klik/pop dan audio tertunda).
     */
    public synchronized void updateAllBands(String target, int[] bands, boolean sendBroadcast) {
        if (target == null || bands == null) return;

        setGeqBandCount(target, bands.length);

        // Nilai flat = 12 (rentang slider 0..24; gain = progress - 12, jadi 12 = 0 dB).
        // Slot kosong harus flat, bukan menyisakan data band lama.
        int[] full = new int[MAX_GEQ_BANDS];
        java.util.Arrays.fill(full, 12);
        for (int i = 0; i < bands.length && i < MAX_GEQ_BANDS; i++) {
            full[i] = bands[i];
        }

        bandsCache.put(target, Arrays.copyOf(bands, bands.length));

        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < MAX_GEQ_BANDS; i++) {
            editor.putInt(KEY_BAND_PREFIX + target + "_" + i, full[i]);
        }
        editor.apply(); // Langsung terapkan (Instantly apply for presets)

        // Batalkan pending save manual jika ada agar tidak menimpa data preset
        if (saveRunnable != null) {
            saveHandler.removeCallbacks(saveRunnable);
            pendingEditor = null;
        }

        // Kirim SATU broadcast per target (bukan tiga) agar processor tidak di-reset berulang.
        if (sendBroadcast) {
            android.content.Intent intent = new android.content.Intent(ACTION_TARGET_UPDATED);
            intent.putExtra(EXTRA_TARGET, target);
            intent.putExtra(EXTRA_BAND_COUNT, bands.length);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        }
    }

    /**
     * Menulis beberapa target sekaligus lalu mengirim SATU broadcast di akhir.
     * Dipakai untuk remap jumlah band agar transisi audio langsung terdengar tanpa klik.
     */
    public synchronized void updateAllBandsBatch(java.util.Map<String, int[]> perTarget) {
        if (perTarget == null || perTarget.isEmpty()) return;
        for (java.util.Map.Entry<String, int[]> e : perTarget.entrySet()) {
            updateAllBands(e.getKey(), e.getValue(), false);
        }
        android.content.Intent intent = new android.content.Intent(ACTION_TARGET_UPDATED);
        intent.putExtra(EXTRA_TARGET, getActiveTarget());
        intent.putExtra(EXTRA_BAND_COUNT, getGeqBandCount(getActiveTarget()));
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public synchronized void updateBand(String target, int bandIndex, int value) {
        int[] cached = bandsCache.get(target);
        // Validasi panjang: cache bisa berasal dari konfigurasi jumlah band yang lama.
        if (cached == null || cached.length != getGeqBandCount(target)) {
            cached = getBands(target);
        }
        if (bandIndex >= 0 && bandIndex < cached.length) {
            cached[bandIndex] = value;
            bandsCache.put(target, cached);
        }

        if (pendingEditor == null) {
            pendingEditor = prefs.edit();
        }
        pendingEditor.putInt(KEY_BAND_PREFIX + target + "_" + bandIndex, value);
        triggerDebouncedSave();
        
        // Notify Service about the update
        android.content.Intent intent = new android.content.Intent(ACTION_EQ_VALUE_CHANGED);
        intent.putExtra(EXTRA_TARGET, target);
        intent.putExtra(EXTRA_BAND_INDEX, bandIndex);
        intent.putExtra(EXTRA_VALUE, value);
        intent.putExtra(EXTRA_BAND_COUNT, cached != null ? cached.length : getGeqBandCount(target));
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public synchronized void updateMasterVolume(int value) {
        cachedMasterVolume = value;
        if (pendingEditor == null) {
            pendingEditor = prefs.edit();
        }
        pendingEditor.putInt(KEY_MASTER_VOLUME, value);
        triggerDebouncedSave();

        android.content.Intent intent = new android.content.Intent(ACTION_VOLUME_UPDATED);
        intent.putExtra(EXTRA_VALUE, value);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public boolean isStereoWidthEnabled() {
        return prefs.getBoolean(KEY_STEREO_WIDTH_ENABLED, false);
    }

    public void setStereoWidthEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_STEREO_WIDTH_ENABLED, enabled).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_STEREO_WIDTH_UPDATED);
        intent.putExtra(EXTRA_ENABLED, enabled);
        intent.putExtra(EXTRA_VALUE, getStereoWidthValue());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public int getStereoWidthValue() {
        return prefs.getInt(KEY_STEREO_WIDTH_VALUE, 100);
    }

    public void setStereoWidthValue(int value) {
        prefs.edit().putInt(KEY_STEREO_WIDTH_VALUE, value).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_STEREO_WIDTH_UPDATED);
        intent.putExtra(EXTRA_ENABLED, isStereoWidthEnabled());
        intent.putExtra(EXTRA_VALUE, value);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public boolean isVirtualBassEnabled() {
        return prefs.getBoolean(KEY_VIRTUAL_BASS_ENABLED, false);
    }

    public void setVirtualBassEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIRTUAL_BASS_ENABLED, enabled).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_VIRTUAL_BASS_UPDATED);
        intent.putExtra(EXTRA_ENABLED, enabled);
        intent.putExtra(EXTRA_VALUE, getVirtualBassAmount());
        intent.putExtra(EXTRA_MIN_FREQ, getVirtualBassMinFreq());
        intent.putExtra(EXTRA_MAX_FREQ, getVirtualBassMaxFreq());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public int getVirtualBassAmount() {
        return prefs.getInt(KEY_VIRTUAL_BASS_AMOUNT, 0);
    }

    public void setVirtualBassAmount(int value) {
        prefs.edit().putInt(KEY_VIRTUAL_BASS_AMOUNT, value).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_VIRTUAL_BASS_UPDATED);
        intent.putExtra(EXTRA_ENABLED, isVirtualBassEnabled());
        intent.putExtra(EXTRA_VALUE, value);
        intent.putExtra(EXTRA_MIN_FREQ, getVirtualBassMinFreq());
        intent.putExtra(EXTRA_MAX_FREQ, getVirtualBassMaxFreq());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public int getVirtualBassMinFreq() {
        return prefs.getInt(KEY_VIRTUAL_BASS_MIN_FREQ, 30);
    }

    public void setVirtualBassMinFreq(int value) {
        prefs.edit().putInt(KEY_VIRTUAL_BASS_MIN_FREQ, value).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_VIRTUAL_BASS_UPDATED);
        intent.putExtra(EXTRA_ENABLED, isVirtualBassEnabled());
        intent.putExtra(EXTRA_VALUE, getVirtualBassAmount());
        intent.putExtra(EXTRA_MIN_FREQ, value);
        intent.putExtra(EXTRA_MAX_FREQ, getVirtualBassMaxFreq());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public int getVirtualBassMaxFreq() {
        return prefs.getInt(KEY_VIRTUAL_BASS_MAX_FREQ, 100);
    }

    public void setVirtualBassMaxFreq(int value) {
        prefs.edit().putInt(KEY_VIRTUAL_BASS_MAX_FREQ, value).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_VIRTUAL_BASS_UPDATED);
        intent.putExtra(EXTRA_ENABLED, isVirtualBassEnabled());
        intent.putExtra(EXTRA_VALUE, getVirtualBassAmount());
        intent.putExtra(EXTRA_MIN_FREQ, getVirtualBassMinFreq());
        intent.putExtra(EXTRA_MAX_FREQ, value);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public boolean isCompressorEnabled() {
        return prefs.getBoolean(KEY_COMPRESSOR_ENABLED, false);
    }

    public float getCompressorThreshold() {
        return prefs.getFloat(KEY_COMPRESSOR_THRESHOLD, -12.0f);
    }

    public float getCompressorRatio() {
        return prefs.getFloat(KEY_COMPRESSOR_RATIO, 2.0f);
    }

    public float getCompressorAttack() {
        return prefs.getFloat(KEY_COMPRESSOR_ATTACK, 20.0f);
    }

    public float getCompressorRelease() {
        return prefs.getFloat(KEY_COMPRESSOR_RELEASE, 150.0f);
    }

    public float getCompressorMakeup() {
        return prefs.getFloat(KEY_COMPRESSOR_MAKEUP, 0.0f);
    }

    public String getCompressorPreset() {
        return prefs.getString(KEY_COMPRESSOR_PRESET, "Soft");
    }

    public void setCompressorEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_COMPRESSOR_ENABLED, enabled).apply();
        notifyCompressorUpdated();
    }

    public void setCompressorParams(float threshold, float ratio, float attack, float release, float makeup, String preset) {
        prefs.edit()
                .putFloat(KEY_COMPRESSOR_THRESHOLD, threshold)
                .putFloat(KEY_COMPRESSOR_RATIO, ratio)
                .putFloat(KEY_COMPRESSOR_ATTACK, attack)
                .putFloat(KEY_COMPRESSOR_RELEASE, release)
                .putFloat(KEY_COMPRESSOR_MAKEUP, makeup)
                .putString(KEY_COMPRESSOR_PRESET, preset)
                .apply();
        notifyCompressorUpdated();
    }

    private void notifyCompressorUpdated() {
        android.content.Intent intent = new android.content.Intent(ACTION_COMPRESSOR_UPDATED);
        intent.putExtra(EXTRA_ENABLED, isCompressorEnabled());
        intent.putExtra(EXTRA_THRESHOLD, getCompressorThreshold());
        intent.putExtra(EXTRA_RATIO, getCompressorRatio());
        intent.putExtra(EXTRA_ATTACK, getCompressorAttack());
        intent.putExtra(EXTRA_RELEASE, getCompressorRelease());
        intent.putExtra(EXTRA_MAKEUP, getCompressorMakeup());
        intent.putExtra(EXTRA_PRESET, getCompressorPreset());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public boolean isLimiterEnabled() {
        return prefs.getBoolean(KEY_LIMITER_ENABLED, false);
    }

    public float getLimiterCeiling() {
        return prefs.getFloat(KEY_LIMITER_CEILING, -1.0f);
    }

    public String getLimiterReleaseMode() {
        return prefs.getString(KEY_LIMITER_RELEASE_MODE, "AUTO");
    }

    public float getLimiterReleaseManual() {
        return prefs.getFloat(KEY_LIMITER_RELEASE_MANUAL, 200.0f);
    }

    public float getLimiterLookahead() {
        return prefs.getFloat(KEY_LIMITER_LOOKAHEAD, 3.0f);
    }

    public String getLimiterPreset() {
        return prefs.getString(KEY_LIMITER_PRESET, "Balanced");
    }

    public void setLimiterEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LIMITER_ENABLED, enabled).apply();
        notifyLimiterUpdated();
    }

    public void setLimiterParams(float ceiling, String releaseMode, float releaseManual, float lookahead, String preset) {
        prefs.edit()
                .putFloat(KEY_LIMITER_CEILING, ceiling)
                .putString(KEY_LIMITER_RELEASE_MODE, releaseMode)
                .putFloat(KEY_LIMITER_RELEASE_MANUAL, releaseManual)
                .putFloat(KEY_LIMITER_LOOKAHEAD, lookahead)
                .putString(KEY_LIMITER_PRESET, preset)
                .apply();
        notifyLimiterUpdated();
    }

    private void notifyLimiterUpdated() {
        android.content.Intent intent = new android.content.Intent(ACTION_LIMITER_UPDATED);
        intent.putExtra(EXTRA_ENABLED, isLimiterEnabled());
        intent.putExtra(EXTRA_CEILING, getLimiterCeiling());
        intent.putExtra(EXTRA_RELEASE_MODE, getLimiterReleaseMode());
        intent.putExtra(EXTRA_RELEASE, getLimiterReleaseManual());
        intent.putExtra(EXTRA_LOOKAHEAD, getLimiterLookahead());
        intent.putExtra(EXTRA_PRESET, getLimiterPreset());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public float getChannelGain(String target) {
        if ("CH2".equals(target)) return prefs.getFloat(KEY_CH2_GAIN, 0.0f);
        return prefs.getFloat(KEY_CH1_GAIN, 0.0f);
    }

    public void setChannelGain(String target, float gainDb) {
        if ("LINK".equals(target)) {
            prefs.edit().putFloat(KEY_CH1_GAIN, gainDb).putFloat(KEY_CH2_GAIN, gainDb).apply();
        } else if ("CH2".equals(target)) {
            prefs.edit().putFloat(KEY_CH2_GAIN, gainDb).apply();
        } else {
            prefs.edit().putFloat(KEY_CH1_GAIN, gainDb).apply();
        }
        notifyChannelGainUpdated();
    }

    private void notifyChannelGainUpdated() {
        android.content.Intent intent = new android.content.Intent(ACTION_CHANNEL_GAIN_UPDATED);
        intent.putExtra(EXTRA_CH1_GAIN, getChannelGain("CH1"));
        intent.putExtra(EXTRA_CH2_GAIN, getChannelGain("CH2"));
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    // ---------------- Volume Receiver (per-perangkat, satuan STEP) ----------------
    // Nilai disimpan sebagai INDEX step perangkat (0..max), bukan persen, agar
    // tidak ada pembulatan: apa yang tampil di Transmitter = apa yang ada di perangkat.

    private static final String KEY_ACTIVE_RECEIVER_IP = "active_receiver_ip";

    /** IP receiver yang sedang terhubung. Dipakai sebagai kunci penyimpanan volume. */
    public String getActiveReceiverIp() {
        return prefs.getString(KEY_ACTIVE_RECEIVER_IP, "");
    }

    public void setActiveReceiverIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) return;
        prefs.edit().putString(KEY_ACTIVE_RECEIVER_IP, ip.trim()).apply();
    }

    /** Jumlah step volume maksimum perangkat receiver. 0 = belum dilaporkan. */
    public int getReceiverVolMax(String ip) {
        if (ip == null || ip.isEmpty()) return 0;
        return prefs.getInt(KEY_RECEIVER_VOLMAX_PREFIX + ip, 0);
    }

    /** Dipanggil saat receiver melaporkan kapabilitasnya (VOL_CAPS). */
    public void setReceiverVolumeCaps(String ip, int max, int current) {
        if (ip == null || ip.isEmpty() || max <= 0) return;
        int clamped = Math.max(0, Math.min(current, max));
        prefs.edit()
                .putInt(KEY_RECEIVER_VOLMAX_PREFIX + ip, max)
                .putInt(KEY_RECEIVER_VOL_PREFIX + ip, clamped)
                .putInt(KEY_RECEIVER_VOLUME, (int) Math.round(clamped * 100.0 / max))
                .apply();
        notifyReceiverVolumeUpdated(clamped, max, ip);
    }

    /** Index step volume tersimpan untuk receiver tsb (fallback: warisan nilai lama). */
    public int getReceiverVolumeIndex(String ip) {
        if (ip == null || ip.isEmpty()) return prefs.getInt(KEY_RECEIVER_VOLUME, 0);
        if (prefs.contains(KEY_RECEIVER_VOL_PREFIX + ip)) {
            return prefs.getInt(KEY_RECEIVER_VOL_PREFIX + ip, 0);
        }
        return prefs.getInt(KEY_RECEIVER_VOLUME, 0);
    }

    /** Simpan volume (index step) untuk receiver tsb, tanpa mengirim perintah. */
    public void setReceiverVolumeIndex(String ip, int index) {
        if (ip == null || ip.isEmpty()) return;
        int max = getReceiverVolMax(ip);
        int clamped = max > 0 ? Math.max(0, Math.min(index, max)) : Math.max(0, index);
        int percent = max > 0 ? (int) Math.round(clamped * 100.0 / max) : Math.max(0, Math.min(100, index));
        prefs.edit()
                .putInt(KEY_RECEIVER_VOL_PREFIX + ip, clamped)
                .putInt(KEY_RECEIVER_VOLUME, percent)
                .apply();
    }

    /**
     * Volume receiver dalam persen (untuk tampilan).
     * Dihitung dari index/max yang sebenarnya, sehingga tidak ada selisih dengan perangkat.
     */
    public int getReceiverVolumePercent(String ip) {
        int max = getReceiverVolMax(ip);
        if (max <= 0) {
            if (ip != null && !ip.isEmpty() && prefs.contains(KEY_RECEIVER_VOL_PREFIX + ip)) {
                return prefs.getInt(KEY_RECEIVER_VOL_PREFIX + ip, getReceiverVolume());
            }
            return getReceiverVolume();
        }
        int idx = getReceiverVolumeIndex(ip);
        return (int) Math.round(idx * 100.0 / max);
    }

    /** Diterima dari receiver (REC_VOL:<index>:<max> atau REC_VOL:<persen>). Tidak dikirim balik (anti-loop). */
    public void applyReceiverVolumeReport(String ip, int index, int max) {
        if (ip == null || ip.isEmpty()) return;
        if (max > 0) {
            int clamped = Math.max(0, Math.min(index, max));
            int percent = (int) Math.round(clamped * 100.0 / max);
            prefs.edit()
                    .putInt(KEY_RECEIVER_VOLMAX_PREFIX + ip, max)
                    .putInt(KEY_RECEIVER_VOL_PREFIX + ip, clamped)
                    .putInt(KEY_RECEIVER_VOLUME, percent)
                    .apply();
        } else {
            // Format lama: index adalah persen (0..100)
            int existingMax = getReceiverVolMax(ip);
            if (existingMax > 0) {
                int stepIndex = (int) Math.round(index * existingMax / 100.0);
                stepIndex = Math.max(0, Math.min(stepIndex, existingMax));
                prefs.edit()
                        .putInt(KEY_RECEIVER_VOL_PREFIX + ip, stepIndex)
                        .putInt(KEY_RECEIVER_VOLUME, Math.max(0, Math.min(100, index)))
                        .apply();
                index = stepIndex;
                max = existingMax;
            } else {
                prefs.edit()
                        .putInt(KEY_RECEIVER_VOL_PREFIX + ip, Math.max(0, Math.min(100, index)))
                        .putInt(KEY_RECEIVER_VOLUME, Math.max(0, Math.min(100, index)))
                        .apply();
            }
        }
        notifyReceiverVolumeUpdated(index, max, ip);
    }

    private void notifyReceiverVolumeUpdated(int index, int max, String ip) {
        android.content.Intent intent = new android.content.Intent(ACTION_RECEIVER_VOLUME_UPDATED);
        intent.putExtra(EXTRA_VALUE, index);
        intent.putExtra(EXTRA_RECEIVER_VOL_MAX, max);
        intent.putExtra(EXTRA_RECEIVER_IP, ip);
        intent.putExtra(EXTRA_FROM_RECEIVER, true);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public int getReceiverVolume() {
        return prefs.getInt(KEY_RECEIVER_VOLUME, 100);
    }

    public void setReceiverVolume(int value) {
        setReceiverVolume(value, false);
    }

    public void setReceiverVolume(int value, boolean fromReceiver) {
        prefs.edit().putInt(KEY_RECEIVER_VOLUME, value).apply();
        
        android.content.Intent intent = new android.content.Intent(ACTION_RECEIVER_VOLUME_UPDATED);
        intent.putExtra(EXTRA_VALUE, value);
        intent.putExtra(EXTRA_FROM_RECEIVER, fromReceiver);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /**
     * Nomor urut perintah volume relatif. Monotonik naik di setiap penekanan sehingga
     * receiver dapat membuang paket duplikat UDP secara pasti (seq <= terakhir = duplikat).
     *
     * WAJIB static: EqSettingsManager dibuat berkali-kali (UI helper & Service). Bila
     * instance field, seq akan selalu mulai dari 1 lagi sehingga receiver menolaknya
     * sebagai duplikat dan volume berhenti merespons setelah penekanan pertama.
     * Di-seed dari currentTimeMillis agar tetap monotonik walau aplikasi di-restart.
     */
    private static final java.util.concurrent.atomic.AtomicLong volSeq =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    public long nextReceiverVolSeq() {
        return volSeq.incrementAndGet();
    }

    /** Perintah relatif: VOL_STEP:<seq>:<+1|-1> */
    public void sendReceiverVolStep(int delta) {
        long seq = nextReceiverVolSeq();
        android.content.Intent intent = new android.content.Intent(ACTION_RECEIVER_VOL_STEP);
        intent.putExtra(EXTRA_VOL_SEQ, seq);
        intent.putExtra(EXTRA_VOL_DELTA, delta);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /** Perintah absolut (dalam satuan step perangkat): VOL_SET:<index> */
    public void sendReceiverVolSet(int index) {
        android.content.Intent intent = new android.content.Intent(ACTION_RECEIVER_VOL_SET);
        intent.putExtra(EXTRA_VALUE, index);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public void sendReceiverVolUp() {
        sendReceiverVolStep(1);
    }

    public void sendReceiverVolDown() {
        sendReceiverVolStep(-1);
    }

    /** Minta receiver melaporkan kapabilitas volumenya (VOL_CAPS). */
    public void requestReceiverVolumeCaps() {
        android.content.Intent intent = new android.content.Intent(ACTION_RECEIVER_VOL_CAPS_REQ);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public int getStreamBufferSize() {
        return prefs.getInt(KEY_STREAM_BUFFER_SIZE, 4096);
    }

    public void setStreamBufferSize(int sizeInBytes) {
        prefs.edit().putInt(KEY_STREAM_BUFFER_SIZE, sizeInBytes).apply();
        android.content.Intent intent = new android.content.Intent(ACTION_STREAM_BUFFER_UPDATED);
        intent.putExtra(EXTRA_VALUE, sizeInBytes);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void triggerDebouncedSave() {
        if (saveRunnable != null) {
            saveHandler.removeCallbacks(saveRunnable);
        }
        saveRunnable = () -> {
            synchronized (EqSettingsManager.this) {
                if (pendingEditor != null) {
                    pendingEditor.apply();
                    pendingEditor = null;
                }
            }
        };
        saveHandler.postDelayed(saveRunnable, 500);
    }
}
