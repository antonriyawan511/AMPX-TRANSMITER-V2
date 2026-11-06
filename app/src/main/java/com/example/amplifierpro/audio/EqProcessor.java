package com.example.amplifierpro.audio;

public class EqProcessor {
    private static final String TAG = "EqProcessor";
    public static final int MAX_BANDS = 30;
    private static final float SAMPLE_RATE = 44100f;

    public static final float[] FREQS_30 = {
            25, 31.5f, 40, 50, 63, 80, 100, 125, 160, 200,
            250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000,
            2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000
    };
    public static final float[] FREQS_15 = {
            25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600,
            2500, 4000, 6300, 10000, 16000
    };
    public static final float[] FREQS_10 = {
            31.5f, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
    };
    public static final float[] FREQS_7 = {
            63, 160, 400, 1000, 2500, 6300, 16000
    };
    public static final float[] FREQS_5 = {
            63, 250, 1000, 4000, 16000
    };

    private static final float Q_30 = 4.318f;
    private static final float Q_15 = 2.145f;
    private static final float Q_10 = 1.414f;
    private static final float Q_7 = 1.000f;
    private static final float Q_5 = 0.667f;

    private volatile int activeBandCount = 30;
    private volatile float[] activeFrequencies = FREQS_30;
    private volatile float activeQ = Q_30;

    private final Biquad[] filtersL = new Biquad[MAX_BANDS];
    private final Biquad[] filtersR = new Biquad[MAX_BANDS];
    private static final int PEQ_BANDS = 4;
    private final Biquad[] peqFiltersL = new Biquad[PEQ_BANDS];
    private final Biquad[] peqFiltersR = new Biquad[PEQ_BANDS];
    private volatile boolean isPeqMode = false;
    
    private volatile float masterVolumeGain = 1.0f;
    private volatile float stereoWidthGain = 1.0f;
    private volatile float ch1GainLin = 1.0f;
    private volatile float ch2GainLin = 1.0f;
    private final float[] currentGainsL = new float[MAX_BANDS];
    private final float[] currentGainsR = new float[MAX_BANDS];
    private volatile boolean needsUpdateL = false;
    private volatile boolean needsUpdateR = false;
    // Anti-klik: saat jumlah band berubah, seluruh state biquad di-reset sehingga muncul
    // transien tajam. Output di-fade-in singkat (~1.5 ms) agar transisi terasa halus.
    private static final int FADE_SAMPLES = 64;
    private volatile int fadeInRemaining = 0;
    private volatile boolean eqEnabled = true;
    private volatile boolean audioEffectsEnabled = true;
    private volatile boolean stereoWidthEnabled = false;
    private volatile boolean virtualBassEnabled = false;
    private volatile float virtualBassAmount = 0.0f;

    private volatile boolean compressorEnabled = false;
    private volatile float compThreshold = -12.0f;
    private volatile float compRatio = 2.0f;
    private volatile float compAttack = 20.0f;
    private volatile float compRelease = 150.0f;
    private volatile float compMakeup = 0.0f;
    private float compEnvelope = 0.0f; // Reduction in dB
    private float compAlphaA = 0.999f;
    private float compAlphaR = 0.999f;

    private volatile boolean limiterEnabled = false;
    private volatile float limiterCeiling = 0.891f; // -1dB
    private volatile float limiterReleaseMs = 200.0f;
    private volatile String limiterReleaseMode = "AUTO";
    private volatile float limiterLookaheadMs = 3.0f;
    private int limiterLookaheadSamples = 132;
    private final float[] lookaheadBufferL = new float[512];
    private final float[] lookaheadBufferR = new float[512];
    private int lookaheadWriteIndex = 0;
    private float limiterGain = 1.0f;
    private float limiterAlphaR = 0.999f;

    private volatile float lastPeakL = 0.0f;
    private volatile float lastPeakR = 0.0f;

    private final Biquad vbFilterMinL = new Biquad();
    private final Biquad vbFilterMinR = new Biquad();
    private final Biquad vbFilterMaxL = new Biquad();
    private final Biquad vbFilterMaxR = new Biquad();
    private final Biquad vbFilterOutL = new Biquad();
    private final Biquad vbFilterOutR = new Biquad();

    public EqProcessor() {
        for (int i = 0; i < MAX_BANDS; i++) {
            filtersL[i] = new Biquad();
            filtersR[i] = new Biquad();
            currentGainsL[i] = 12;
            currentGainsR[i] = 12;
            updateFilter(filtersL[i], 12, i);
            updateFilter(filtersR[i], 12, i);
        }
        for (int i = 0; i < PEQ_BANDS; i++) {
            peqFiltersL[i] = new Biquad();
            peqFiltersR[i] = new Biquad();
        }
        
        setVirtualBassFrequencies(30, 100);
        vbFilterOutL.setLowPass(400, SAMPLE_RATE, 0.707f);
        vbFilterOutR.setLowPass(400, SAMPLE_RATE, 0.707f);
    }

    public void setEqMode(String mode) {
        this.isPeqMode = "PQ".equalsIgnoreCase(mode) || "PEQ".equalsIgnoreCase(mode);
    }

    public synchronized void updatePeqFilterL(int index, float freq, float gainDb, float q) {
        if (index < 0 || index >= PEQ_BANDS) return;
        peqFiltersL[index].setPeakingEQ(freq, SAMPLE_RATE, q, gainDb);
    }

    public synchronized void updatePeqFilterR(int index, float freq, float gainDb, float q) {
        if (index < 0 || index >= PEQ_BANDS) return;
        peqFiltersR[index].setPeakingEQ(freq, SAMPLE_RATE, q, gainDb);
    }

    public void setMasterVolume(int volumePercent) {
        this.masterVolumeGain = volumePercent / 100f;
    }

    public void setEqEnabled(boolean enabled) {
        this.eqEnabled = enabled;
    }

    public void setAudioEffectsEnabled(boolean enabled) {
        this.audioEffectsEnabled = enabled;
    }

    public void setStereoWidthEnabled(boolean enabled) {
        this.stereoWidthEnabled = enabled;
    }

    public void setStereoWidthValue(int widthPercent) {
        this.stereoWidthGain = widthPercent / 100f;
    }

    public void setVirtualBassEnabled(boolean enabled) {
        this.virtualBassEnabled = enabled;
    }

    public void setVirtualBassAmount(int amountPercent) {
        this.virtualBassAmount = amountPercent / 100f;
    }

    public void setVirtualBassFrequencies(int minFreq, int maxFreq) {
        vbFilterMinL.setHighPass(minFreq, SAMPLE_RATE, 0.707f);
        vbFilterMinR.setHighPass(minFreq, SAMPLE_RATE, 0.707f);
        vbFilterMaxL.setLowPass(maxFreq, SAMPLE_RATE, 0.707f);
        vbFilterMaxR.setLowPass(maxFreq, SAMPLE_RATE, 0.707f);
    }

    public void setCompressorEnabled(boolean enabled) {
        this.compressorEnabled = enabled;
        if (!enabled) compEnvelope = 0.0f;
    }

    public void setCompressorParams(float threshold, float ratio, float attackMs, float releaseMs, float makeup) {
        this.compThreshold = threshold;
        this.compRatio = ratio;
        this.compAttack = attackMs;
        this.compRelease = releaseMs;
        this.compMakeup = makeup;
        
        // alpha = exp(-1.0 / (sample_rate * time_seconds))
        this.compAlphaA = (float) Math.exp(-1.0 / (SAMPLE_RATE * (attackMs / 1000.0)));
        this.compAlphaR = (float) Math.exp(-1.0 / (SAMPLE_RATE * (releaseMs / 1000.0)));
    }

    public void setLimiterEnabled(boolean enabled) {
        this.limiterEnabled = enabled;
        if (!enabled) {
            limiterGain = 1.0f;
            java.util.Arrays.fill(lookaheadBufferL, 0);
            java.util.Arrays.fill(lookaheadBufferR, 0);
        }
    }

    public void setLimiterParams(float ceilingDb, String releaseMode, float releaseMs, float lookaheadMs) {
        this.limiterCeiling = (float) Math.pow(10.0, ceilingDb / 20.0);
        this.limiterReleaseMode = releaseMode;
        this.limiterReleaseMs = releaseMs;
        this.limiterLookaheadMs = lookaheadMs;
        this.limiterLookaheadSamples = (int) (lookaheadMs * SAMPLE_RATE / 1000.0f);
        if (this.limiterLookaheadSamples > 511) this.limiterLookaheadSamples = 511;

        float effectiveRelease = releaseMs;
        if ("AUTO".equals(releaseMode)) {
            effectiveRelease = 200.0f; // Base for auto
        }
        this.limiterAlphaR = (float) Math.exp(-1.0 / (SAMPLE_RATE * (effectiveRelease / 1000.0)));
    }

    public void setChannelGains(float ch1Db, float ch2Db) {
        this.ch1GainLin = (float) Math.pow(10.0, ch1Db / 20.0);
        this.ch2GainLin = (float) Math.pow(10.0, ch2Db / 20.0);
    }

    public float getCompressorGainReduction() {
        return audioEffectsEnabled && compressorEnabled ? compEnvelope : 0.0f;
    }

    public float getLimiterGainReduction() {
        if (!audioEffectsEnabled || !limiterEnabled || limiterGain >= 0.999f) return 0.0f;
        return (float) (-20.0 * Math.log10(limiterGain));
    }

    public float getLeftPeak() {
        return lastPeakL;
    }

    public float getRightPeak() {
        return lastPeakR;
    }

    public synchronized void setBandCount(int count) {
        if (count != 5 && count != 7 && count != 10 && count != 15 && count != 30) {
            count = 30;
        }
        if (this.activeBandCount == count) {
            return;
        }
        this.activeBandCount = count;
        switch (count) {
            case 5:
                this.activeFrequencies = FREQS_5;
                this.activeQ = Q_5;
                break;
            case 7:
                this.activeFrequencies = FREQS_7;
                this.activeQ = Q_7;
                break;
            case 10:
                this.activeFrequencies = FREQS_10;
                this.activeQ = Q_10;
                break;
            case 15:
                this.activeFrequencies = FREQS_15;
                this.activeQ = Q_15;
                break;
            case 30:
            default:
                this.activeFrequencies = FREQS_30;
                this.activeQ = Q_30;
                break;
        }
        for (int i = 0; i < MAX_BANDS; i++) {
            filtersL[i].reset();
            filtersR[i].reset();
        }
        needsUpdateL = true;
        needsUpdateR = true;
        fadeInRemaining = FADE_SAMPLES; // cegah klik saat ganti jumlah band
        android.util.Log.d(TAG, "[setBandCount] Active bandCount=" + count + ", Q=" + activeQ + ", freqs=" + java.util.Arrays.toString(activeFrequencies));
    }

    public int getActiveBandCount() {
        return activeBandCount;
    }

    /** Tabel frekuensi untuk jumlah band tertentu (dipakai untuk remap antar kanal). */
    public static float[] getFreqsForBandCount(int count) {
        switch (count) {
            case 15: return FREQS_15;
            case 10: return FREQS_10;
            case 7:  return FREQS_7;
            case 5:  return FREQS_5;
            case 30:
            default: return FREQS_30;
        }
    }

    /**
     * Remap gain dari grid frekuensi panjang array sumber ke grid {@code tgtCount}
     * dengan interpolasi logaritmik. Dipakai agar dua kanal dengan jumlah band berbeda
     * tetap dapat diproses oleh DSP yang hanya memiliki satu grid aktif.
     * Mengembalikan array sumber apa adanya bila jumlah band sudah sama.
     */
    public static int[] remapGains(int[] src, int tgtCount) {
        if (src == null || src.length == 0) return src;
        if (src.length == tgtCount) return src;
        float[] srcF = getFreqsForBandCount(src.length);
        float[] tgtF = getFreqsForBandCount(tgtCount);
        if (srcF == null || tgtF == null) return src;
        int[] out = new int[tgtF.length];
        for (int j = 0; j < tgtF.length; j++) {
            float f = tgtF[j];
            if (f <= srcF[0]) {
                out[j] = src[0];
            } else if (f >= srcF[srcF.length - 1]) {
                out[j] = src[src.length - 1];
            } else {
                int i = 0;
                while (i < srcF.length - 1 && srcF[i + 1] < f) i++;
                double logF0 = Math.log(srcF[i]);
                double logF1 = Math.log(srcF[i + 1]);
                double t = (logF1 > logF0) ? (Math.log(f) - logF0) / (logF1 - logF0) : 0.0;
                out[j] = Math.round((float) (src[i] + t * (src[i + 1] - src[i])));
            }
        }
        return out;
    }

    public float[] getActiveFrequencies() {
        return activeFrequencies;
    }

    public synchronized void updateChannelL(int index, int rawGain) {
        if (index < 0 || index >= activeBandCount) return;
        currentGainsL[index] = rawGain;
        needsUpdateL = true;
        android.util.Log.d(TAG, "[updateChannelL] band=" + index + "/" + activeBandCount + ", freq=" + activeFrequencies[index] + "Hz, gainDb=" + (rawGain - 12) + "dB (raw=" + rawGain + ")");
    }

    public synchronized void updateChannelR(int index, int rawGain) {
        if (index < 0 || index >= activeBandCount) return;
        currentGainsR[index] = rawGain;
        needsUpdateR = true;
        android.util.Log.d(TAG, "[updateChannelR] band=" + index + "/" + activeBandCount + ", freq=" + activeFrequencies[index] + "Hz, gainDb=" + (rawGain - 12) + "dB (raw=" + rawGain + ")");
    }

    public synchronized void loadFullConfig(int[] gainsL, int[] gainsR) {
        int newCount = (gainsL != null && gainsL.length > 0) ? gainsL.length : ((gainsR != null && gainsR.length > 0) ? gainsR.length : activeBandCount);
        if (newCount != activeBandCount) {
            setBandCount(newCount);
        }
        // PENTING: slot yang tidak tercakup oleh array sumber HARUS di-reset ke flat (12).
        // Tanpa ini, sisa nilai dari konfigurasi sebelumnya tetap aktif dan membuat
        // kurva EQ tidak konsisten (mis. saat satu kanal punya jumlah band berbeda).
        if (gainsL != null) {
            for (int i = 0; i < activeBandCount; i++) {
                currentGainsL[i] = (i < gainsL.length) ? gainsL[i] : 12;
            }
        } else {
            for (int i = 0; i < activeBandCount; i++) currentGainsL[i] = 12;
        }
        if (gainsR != null) {
            for (int i = 0; i < activeBandCount; i++) {
                currentGainsR[i] = (i < gainsR.length) ? gainsR[i] : 12;
            }
        } else {
            for (int i = 0; i < activeBandCount; i++) currentGainsR[i] = 12;
        }
        needsUpdateL = true;
        needsUpdateR = true;
        android.util.Log.d(TAG, "[loadFullConfig] bandCount=" + activeBandCount + 
                ", freqs=" + java.util.Arrays.toString(activeFrequencies) + 
                ", gainsL=" + java.util.Arrays.toString(java.util.Arrays.copyOf(currentGainsL, activeBandCount)) + 
                ", gainsR=" + java.util.Arrays.toString(java.util.Arrays.copyOf(currentGainsR, activeBandCount)));
    }

    private void updateFilter(Biquad filter, float rawGain, int bandIndex) {
        float dbGain = rawGain - 12;
        float freq = activeFrequencies[bandIndex];
        filter.setPeakingEQ(freq, SAMPLE_RATE, activeQ, dbGain);
    }

    public void processAudio(byte[] buffer, int length) {
        if (needsUpdateL || needsUpdateR) {
            synchronized (this) {
                int count = activeBandCount;
                if (needsUpdateL) {
                    for (int i = 0; i < count; i++) updateFilter(filtersL[i], currentGainsL[i], i);
                    needsUpdateL = false;
                }
                if (needsUpdateR) {
                    for (int i = 0; i < count; i++) updateFilter(filtersR[i], currentGainsR[i], i);
                    needsUpdateR = false;
                }
            }
        }

        float maxL = 0.0f;
        float maxR = 0.0f;

        for (int i = 0; i < length - 3; i += 4) {
            short lShort = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            short rShort = (short) ((buffer[i + 2] & 0xFF) | (buffer[i + 3] << 8));
            
            float l = lShort;
            float r = rShort;

            if (eqEnabled) {
                if (isPeqMode) {
                    for (int b = 0; b < PEQ_BANDS; b++) {
                        l = peqFiltersL[b].process(l);
                        r = peqFiltersR[b].process(r);
                    }
                } else {
                    int count = activeBandCount;
                    for (int b = 0; b < count; b++) {
                        l = filtersL[b].process(l);
                        r = filtersR[b].process(r);
                    }
                }
            }

            // Apply Channel Gain
            l *= ch1GainLin;
            r *= ch2GainLin;

            if (audioEffectsEnabled && virtualBassEnabled && virtualBassAmount > 0) {
                l = applyVirtualBass(l, vbFilterMinL, vbFilterMaxL, vbFilterOutL);
                r = applyVirtualBass(r, vbFilterMinR, vbFilterMaxR, vbFilterOutR);
            }

            if (audioEffectsEnabled && compressorEnabled) {
                float absL = Math.abs(l);
                float absR = Math.abs(r);
                float maxAbs = Math.max(absL, absR);
                
                // PCM to dB (16-bit)
                float inputDB = (maxAbs < 0.1f) ? -100.0f : (float) (20.0 * Math.log10(maxAbs / 32768.0));
                
                float targetReduction = 0.0f;
                if (inputDB > compThreshold) {
                    targetReduction = (inputDB - compThreshold) * (1.0f - 1.0f / compRatio);
                }
                
                // Smoothing in dB domain
                if (targetReduction > compEnvelope) {
                    compEnvelope = compAlphaA * compEnvelope + (1.0f - compAlphaA) * targetReduction;
                } else {
                    compEnvelope = compAlphaR * compEnvelope + (1.0f - compAlphaR) * targetReduction;
                }
                
                float totalGainDB = compMakeup - compEnvelope;
                float gainLin = (float) Math.pow(10.0, totalGainDB / 20.0);
                
                l *= gainLin;
                r *= gainLin;
            }

            if (audioEffectsEnabled && stereoWidthEnabled) {
                float mid = (l + r) / 2.0f;
                float side = (l - r) / 2.0f;
                side *= stereoWidthGain;
                l = mid + side;
                r = mid - side;
            }

            l *= masterVolumeGain;
            r *= masterVolumeGain;

            if (audioEffectsEnabled && limiterEnabled) {
                lookaheadBufferL[lookaheadWriteIndex] = l;
                lookaheadBufferR[lookaheadWriteIndex] = r;
                
                float absL = Math.abs(l);
                float absR = Math.abs(r);
                float peak = Math.max(absL, absR) / 32768.0f;
                float targetGain = (peak > limiterCeiling) ? (limiterCeiling / peak) : 1.0f;

                if (targetGain < limiterGain) {
                    limiterGain = targetGain;
                } else {
                    float currentAlphaR = limiterAlphaR;
                    if ("AUTO".equals(limiterReleaseMode)) {
                        float gr = 1.0f - limiterGain;
                        currentAlphaR = (float) Math.exp(-1.0 / (SAMPLE_RATE * ((100.0f + gr * 900.0f) / 1000.0)));
                    }
                    limiterGain = currentAlphaR * limiterGain + (1.0f - currentAlphaR) * targetGain;
                }

                int readIndex = (lookaheadWriteIndex - limiterLookaheadSamples + 512) & 511;
                l = lookaheadBufferL[readIndex] * limiterGain;
                r = lookaheadBufferR[readIndex] * limiterGain;
                
                lookaheadWriteIndex = (lookaheadWriteIndex + 1) & 511;
            }

            float absL = Math.abs(l);
            float absR = Math.abs(r);
            if (absL > maxL) maxL = absL;
            if (absR > maxR) maxR = absR;

            // Anti-klik: fade-in singkat setelah perubahan jumlah band
            if (fadeInRemaining > 0) {
                float fade = 1.0f - (float) fadeInRemaining / (float) FADE_SAMPLES;
                l *= fade;
                r *= fade;
                fadeInRemaining--;
            }

            int lOut = Math.round(l);
            if (lOut > 32767) lOut = 32767;
            else if (lOut < -32768) lOut = -32768;
            buffer[i] = (byte) (lOut & 0xFF);
            buffer[i + 1] = (byte) ((lOut >> 8) & 0xFF);

            int rOut = Math.round(r);
            if (rOut > 32767) rOut = 32767;
            else if (rOut < -32768) rOut = -32768;
            buffer[i + 2] = (byte) (rOut & 0xFF);
            buffer[i + 3] = (byte) ((rOut >> 8) & 0xFF);
        }

        // Report raw peak of current buffer to UI. UI thread handles smooth decay at 60fps.
        lastPeakL = maxL / 32768.0f;
        lastPeakR = maxR / 32768.0f;
    }

    private float applyVirtualBass(float input, Biquad minFilter, Biquad maxFilter, Biquad outFilter) {
        float low = minFilter.process(input);
        low = maxFilter.process(low);
        // Nonlinear function: f(x) = x - 0.1 * x^3
        // Scale input to avoid massive values before cubing. 
        // 16-bit PCM is +/- 32768.
        float x = low / 32768.0f;
        float harmonics = (x - 0.1f * x * x * x) * 32768.0f;
        
        // Post-filter to remove high frequency noise from saturation
        harmonics = outFilter.process(harmonics);
        
        return input + (harmonics * virtualBassAmount);
    }

    private static class Biquad {
        private float b0, b1, b2, a1, a2;
        private float x1, x2, y1, y2;
        private boolean active = false;

        public void reset() {
            x1 = x2 = y1 = y2 = 0;
        }

        public void setPeakingEQ(float freq, float sr, float q, float dbGain) {
            if (Math.abs(dbGain) < 0.1f) {
                active = false;
                reset();
                return;
            }
            
            float a = (float) Math.pow(10, dbGain / 40);
            float omega = (float) (2 * Math.PI * freq / sr);
            float sn = (float) Math.sin(omega);
            float cs = (float) Math.cos(omega);
            float alpha = sn / (2 * q);

            float a0 = 1 + alpha / a;
            b0 = (1 + alpha * a) / a0;
            b1 = (-2 * cs) / a0;
            b2 = (1 - alpha * a) / a0;
            a1 = (-2 * cs) / a0;
            a2 = (1 - alpha / a) / a0;
            
            active = true;
        }

        public void setLowPass(float freq, float sr, float q) {
            float omega = (float) (2 * Math.PI * freq / sr);
            float sn = (float) Math.sin(omega);
            float cs = (float) Math.cos(omega);
            float alpha = sn / (2 * q);

            float a0 = 1 + alpha;
            b0 = (1 - cs) / 2 / a0;
            b1 = (1 - cs) / a0;
            b2 = (1 - cs) / 2 / a0;
            a1 = -2 * cs / a0;
            a2 = (1 - alpha) / a0;
            active = true;
        }

        public void setHighPass(float freq, float sr, float q) {
            float omega = (float) (2 * Math.PI * freq / sr);
            float sn = (float) Math.sin(omega);
            float cs = (float) Math.cos(omega);
            float alpha = sn / (2 * q);

            float a0 = 1 + alpha;
            b0 = (1 + cs) / 2 / a0;
            b1 = -(1 + cs) / a0;
            b2 = (1 + cs) / 2 / a0;
            a1 = -2 * cs / a0;
            a2 = (1 - alpha) / a0;
            active = true;
        }

        public float process(float in) {
            if (!active) return in;
            float out = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = in;
            y2 = y1; y1 = out;
            return out;
        }
    }
}
