package com.example.amplifierpro.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class LatencyChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guideTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint zeroLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();
    private final Path fillPath = new Path();

    private final long[] samples = new long[60];
    private int sampleCount = 0;
    private float graphMaxMs = 150f;

    private boolean isEqMode = false;
    private int[] eqBands = null;
    private float[] eqPx = null;
    private float[] eqPy = null;
    private float[] eqDeltaY = null;
    private float[] eqM = null;
    private String eqTarget = "CH1";
    private String eqPresetName = "Flat";
    private int currentAccentColor = Color.parseColor("#38BDF8");

    private boolean isPeqMode = false;
    private float[] peqFreqs = {50f, 200f, 1000f, 4000f};
    private float[] peqGains = {0f, 0f, 0f, 0f};
    private float[] peqQs = {1.0f, 1.0f, 1.0f, 1.0f};
    private int peqFocusedBand = 0;
    private final Paint focusRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LatencyChartView(Context context) {
        super(context);
        init();
    }

    public LatencyChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LatencyChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setColor(currentAccentColor);

        fillPaint.setStyle(Paint.Style.FILL);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(1.2f);
        guidePaint.setColor(Color.parseColor("#26FFFFFF"));

        zeroLinePaint.setStyle(Paint.Style.STROKE);
        zeroLinePaint.setStrokeWidth(1.5f);
        zeroLinePaint.setColor(Color.parseColor("#4DFFFFFF"));

        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(currentAccentColor);

        guideTextPaint.setColor(Color.parseColor("#88FFFFFF"));
        guideTextPaint.setTextSize(24f);

        badgeTextPaint.setColor(currentAccentColor);
        badgeTextPaint.setTextSize(26f);
        badgeTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        focusRingPaint.setStyle(Paint.Style.STROKE);
        focusRingPaint.setStrokeWidth(2.5f);
        focusRingPaint.setColor(currentAccentColor);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateFillShader(h);
    }

    private void updateFillShader(int h) {
        if (h > 0) {
            int fillStart = (0x3D << 24) | (currentAccentColor & 0x00FFFFFF);
            int fillEnd = (0x02 << 24) | (currentAccentColor & 0x00FFFFFF);
            LinearGradient fillShader = new LinearGradient(
                    0f, 0f, 0f, (float) h,
                    fillStart, fillEnd,
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillShader);
        }
    }

    public void setSamples(long[] values, int count) {
        this.isEqMode = false;
        sampleCount = Math.min(count, samples.length);
        System.arraycopy(values, 0, samples, 0, sampleCount);
        postInvalidate();
    }

    public void setEqBands(int[] bands, String target, String presetName) {
        this.isEqMode = true;
        this.isPeqMode = false;
        if (bands != null) {
            if (this.eqBands == null || this.eqBands.length != bands.length) {
                this.eqBands = new int[bands.length];
            }
            System.arraycopy(bands, 0, this.eqBands, 0, bands.length);
        }
        if (target != null) this.eqTarget = target;
        if (presetName != null) this.eqPresetName = presetName;
        postInvalidate();
    }

    public void setPeqBands(float[] freqs, float[] gains, float[] qs, int focusedBand, String target) {
        this.isEqMode = true;
        this.isPeqMode = true;
        if (freqs != null) this.peqFreqs = freqs.clone();
        if (gains != null) this.peqGains = gains.clone();
        if (qs != null) this.peqQs = qs.clone();
        this.peqFocusedBand = focusedBand;
        if (target != null) this.eqTarget = target;
        postInvalidate();
    }

    public void updatePeqBand(int bandIndex, float freq, float gain, float q) {
        this.isEqMode = true;
        this.isPeqMode = true;
        if (bandIndex >= 0 && bandIndex < 4) {
            if (this.peqFreqs == null) this.peqFreqs = new float[]{50f, 200f, 1000f, 4000f};
            if (this.peqGains == null) this.peqGains = new float[]{0f, 0f, 0f, 0f};
            if (this.peqQs == null) this.peqQs = new float[]{1f, 1f, 1f, 1f};
            this.peqFreqs[bandIndex] = freq;
            this.peqGains[bandIndex] = gain;
            this.peqQs[bandIndex] = q;
            postInvalidate();
        }
    }

    public void setPeqFocus(int focusedBand) {
        this.peqFocusedBand = focusedBand;
        postInvalidate();
    }

    public void updateBand(int bandIndex, int value) {
        this.isEqMode = true;
        this.isPeqMode = false;
        if (this.eqBands == null) {
            this.eqBands = new int[30];
            java.util.Arrays.fill(this.eqBands, 12);
        }
        if (bandIndex >= 0 && bandIndex < this.eqBands.length) {
            this.eqBands[bandIndex] = value;
            postInvalidate();
        }
    }

    public void setAccentColor(int color) {
        this.currentAccentColor = color;
        linePaint.setColor(color);
        pointPaint.setColor(color);
        badgeTextPaint.setColor(color);
        focusRingPaint.setColor(color);
        updateFillShader(getHeight());
        postInvalidate();
    }

    public void setGraphMaxMs(float maxMs) {
        this.graphMaxMs = Math.max(10f, maxMs);
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = (float) getWidth();
        float h = (float) getHeight();

        if (w <= 0f || h <= 0f) return;

        if (isEqMode) {
            if (isPeqMode) {
                drawPeqGraph(canvas, w, h);
            } else {
                drawEqGraph(canvas, w, h);
            }
            return;
        }

        drawGuideLine(canvas, 25f, "25 ms", w, h);
        drawGuideLine(canvas, 100f, "100 ms", w, h);

        if (sampleCount < 2) return;

        buildCurvePath(w, h);

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(path, linePaint);
    }

    private void drawEqGraph(Canvas canvas, float w, float h) {
        float density = getResources().getDisplayMetrics().density;
        float padL = 0f;
        float padR = 0f;
        float padT = 22f * density;
        float padB = 18f * density;

        float plotW = w;
        float plotH = h - padT - padB;
        if (plotW <= 0f || plotH <= 0f) return;

        float yPlus12 = padT;
        float yZero = padT + plotH * 0.5f;
        float yMinus12 = padT + plotH;

        // Draw horizontal grid lines across the full card width
        canvas.drawLine(0f, yPlus12, w, yPlus12, guidePaint);
        canvas.drawLine(0f, yZero, w, yZero, zeroLinePaint);
        canvas.drawLine(0f, yMinus12, w, yMinus12, guidePaint);

        // Frequency markings at bottom
        float yFreq = h - 4f * density;
        guideTextPaint.setColor(Color.parseColor("#99FFFFFF"));
        canvas.drawText("25Hz", 10f * density, yFreq, guideTextPaint);
        canvas.drawText("1kHz", w * 0.5f - 8f * density, yFreq, guideTextPaint);
        canvas.drawText("20kHz", w - 38f * density, yFreq, guideTextPaint);

        // Title and preset name at top-left
        badgeTextPaint.setTextSize(10.5f * density);
        badgeTextPaint.setColor(currentAccentColor);
        String headerTitle = eqTarget + " • " + (eqPresetName != null ? eqPresetName.toUpperCase(java.util.Locale.US) : "FLAT");
        canvas.drawText(headerTitle, 10f * density, padT - 7f * density, badgeTextPaint);

        // Swipe hint at top-right
        guideTextPaint.setTextSize(8.5f * density);
        guideTextPaint.setColor(Color.parseColor("#66FFFFFF"));
        canvas.drawText("SWIPE ❯", w - 44f * density, padT - 7f * density, guideTextPaint);

        if (eqBands == null || eqBands.length == 0) return;

        int count = eqBands.length;
        float dx = plotW / (float) Math.max(1, count - 1);

        path.reset();
        fillPath.reset();

        if (eqPx == null || eqPx.length != count) {
            eqPx = new float[count];
            eqPy = new float[count];
            eqDeltaY = new float[count];
            eqM = new float[count];
        }
        float[] px = eqPx;
        float[] py = eqPy;

        for (int i = 0; i < count; i++) {
            px[i] = padL + i * dx;
            float progress = Math.max(0, Math.min(24, eqBands[i]));
            py[i] = padT + (1f - (progress / 24f)) * plotH;
        }

        // 1. Selisih ketinggian antar titik berurutan
        for (int i = 0; i < count - 1; i++) {
            eqDeltaY[i] = py[i + 1] - py[i];
        }

        // 2. Tangen alami pada setiap titik (mencegah flat horizontal paksaan)
        eqM[0] = eqDeltaY[0];
        eqM[count - 1] = eqDeltaY[count - 2];
        for (int i = 1; i < count - 1; i++) {
            float d0 = eqDeltaY[i - 1];
            float d1 = eqDeltaY[i];
            if ((d0 > 0f && d1 < 0f) || (d0 < 0f && d1 > 0f) || d0 == 0f || d1 == 0f) {
                eqM[i] = 0f;
            } else {
                eqM[i] = 0.5f * (d0 + d1);
            }
        }

        // 3. Fritsch-Carlson monotonicity: menjaga kelengkungan mulus & bebas overshoot
        for (int i = 0; i < count - 1; i++) {
            float delta = eqDeltaY[i];
            if (delta == 0f) {
                eqM[i] = 0f;
                eqM[i + 1] = 0f;
            } else {
                float alpha = eqM[i] / delta;
                float beta = eqM[i + 1] / delta;
                float sumSq = alpha * alpha + beta * beta;
                if (sumSq > 9f) {
                    float tau = 3f / (float) Math.sqrt(sumSq);
                    eqM[i] = tau * alpha * delta;
                    eqM[i + 1] = tau * beta * delta;
                }
            }
        }

        path.moveTo(px[0], py[0]);
        fillPath.moveTo(px[0], yMinus12);
        fillPath.lineTo(px[0], py[0]);

        float thirdDx = dx / 3f;
        for (int i = 0; i < count - 1; i++) {
            float cp1x = px[i] + thirdDx;
            float cp1y = py[i] + eqM[i] / 3f;
            float cp2x = px[i + 1] - thirdDx;
            float cp2y = py[i + 1] - eqM[i + 1] / 3f;

            // Pastikan titik kontrol tidak keluar batas vertikal grid
            cp1y = Math.max(padT, Math.min(yMinus12, cp1y));
            cp2y = Math.max(padT, Math.min(yMinus12, cp2y));

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, px[i + 1], py[i + 1]);
            fillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, px[i + 1], py[i + 1]);
        }

        fillPath.lineTo(px[count - 1], yMinus12);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(path, linePaint);

        // Small nodes for the 30 bands (skip first & last: tepat di tepi, agar tidak terpotong sudut rounded)
        pointPaint.setColor(currentAccentColor);
        for (int i = 1; i < count - 1; i++) {
            canvas.drawCircle(px[i], py[i], 1.8f * density, pointPaint);
        }
    }

    private void drawPeqGraph(Canvas canvas, float w, float h) {
        float density = getResources().getDisplayMetrics().density;
        float padL = 0f;
        float padR = 0f;
        float padT = 22f * density;
        float padB = 18f * density;

        float plotW = w;
        float plotH = h - padT - padB;
        if (plotW <= 0f || plotH <= 0f) return;

        float yPlus12 = padT;
        float yZero = padT + plotH * 0.5f;
        float yMinus12 = padT + plotH;

        // Draw horizontal grid lines across the full card width
        canvas.drawLine(0f, yPlus12, w, yPlus12, guidePaint);
        canvas.drawLine(0f, yZero, w, yZero, zeroLinePaint);
        canvas.drawLine(0f, yMinus12, w, yMinus12, guidePaint);

        // Frequency markings at bottom
        float yFreq = h - 4f * density;
        guideTextPaint.setColor(Color.parseColor("#99FFFFFF"));
        canvas.drawText("20Hz", 10f * density, yFreq, guideTextPaint);
        canvas.drawText("1kHz", w * 0.5f - 8f * density, yFreq, guideTextPaint);
        canvas.drawText("20kHz", w - 38f * density, yFreq, guideTextPaint);

        // Header: Target & PEQ Mode
        badgeTextPaint.setTextSize(10.5f * density);
        badgeTextPaint.setColor(currentAccentColor);
        String headerTitle = eqTarget + " • PARAMETRIC";
        canvas.drawText(headerTitle, 10f * density, padT - 7f * density, badgeTextPaint);

        // Swipe hint at top-right
        guideTextPaint.setTextSize(8.5f * density);
        guideTextPaint.setColor(Color.parseColor("#66FFFFFF"));
        canvas.drawText("SWIPE ❯", w - 44f * density, padT - 7f * density, guideTextPaint);

        if (peqFreqs == null || peqGains == null || peqQs == null) return;

        path.reset();
        fillPath.reset();

        int steps = Math.max(80, (int) (plotW / (2f * density)));
        float minLog = (float) Math.log10(20.0);
        float maxLog = (float) Math.log10(20000.0);
        float logRange = maxLog - minLog;

        boolean isFirst = true;
        for (int i = 0; i <= steps; i++) {
            float frac = (float) i / (float) steps;
            float curX = frac * plotW;
            float f = (float) Math.pow(10.0, minLog + frac * logRange);

            float totalGainDb = 0f;
            for (int b = 0; b < 4 && b < peqFreqs.length; b++) {
                totalGainDb += calculatePeakingGainDb(f, peqFreqs[b], peqGains[b], peqQs[b]);
            }

            float clampedGain = Math.max(-14f, Math.min(14f, totalGainDb));
            float curY = yZero - (clampedGain / 12f) * (plotH * 0.5f);
            curY = Math.max(padT, Math.min(yMinus12, curY));

            if (isFirst) {
                path.moveTo(curX, curY);
                fillPath.moveTo(curX, yMinus12);
                fillPath.lineTo(curX, curY);
                isFirst = false;
            } else {
                path.lineTo(curX, curY);
                fillPath.lineTo(curX, curY);
            }
        }

        fillPath.lineTo(w, yMinus12);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(path, linePaint);

        // Draw 4 PEQ band center nodes with focus highlighting
        String[] bandNames = {"SUB", "LOW", "MID", "HIGH"};
        for (int b = 0; b < 4 && b < peqFreqs.length; b++) {
            float f0 = peqFreqs[b];
            float g0 = peqGains[b];

            float fLog = (float) Math.log10(Math.max(20f, Math.min(20000f, f0)));
            float bx = ((fLog - minLog) / logRange) * plotW;
            float clampedG = Math.max(-12f, Math.min(12f, g0));
            float by = yZero - (clampedG / 12f) * (plotH * 0.5f);
            by = Math.max(padT, Math.min(yMinus12, by));

            if (b == peqFocusedBand) {
                // Outer focus ring
                canvas.drawCircle(bx, by, 7f * density, focusRingPaint);
                // Center solid node
                pointPaint.setColor(currentAccentColor);
                canvas.drawCircle(bx, by, 4f * density, pointPaint);
                // Text label
                guideTextPaint.setColor(Color.WHITE);
                guideTextPaint.setTextSize(9f * density);
                guideTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                float labelY = by > yZero ? by - 10f * density : by + 16f * density;
                canvas.drawText(bandNames[b], bx - 10f * density, labelY, guideTextPaint);
                guideTextPaint.setTypeface(android.graphics.Typeface.DEFAULT);
            } else {
                // Unfocused node
                pointPaint.setColor(0xB3FFFFFF);
                canvas.drawCircle(bx, by, 3f * density, pointPaint);
            }
        }
    }

    private static float calculatePeakingGainDb(float f, float f0, float gainDb, float q) {
        if (Math.abs(gainDb) < 0.05f) return 0f;
        float a = (float) Math.pow(10.0, gainDb / 40.0);
        float r = (f / f0) - (f0 / f);
        float num = r * r + (a / q) * (a / q);
        float den = r * r + (1f / (a * q)) * (1f / (a * q));
        if (den <= 0f) return 0f;
        return (float) (10.0 * Math.log10(num / den));
    }

    private void drawGuideLine(Canvas canvas, float valueMs, String label, float w, float h) {
        if (valueMs > graphMaxMs) return;
        float y = h - (valueMs / graphMaxMs) * h;
        canvas.drawLine(0f, y, w, y, guidePaint);
        canvas.drawText(label, 12f, y - 6f, guideTextPaint);
    }

    private void buildCurvePath(float w, float h) {
        path.reset();
        fillPath.reset();

        float dx = w / (float) (sampleCount - 1);

        path.moveTo(0f, getY(0, h));
        fillPath.moveTo(0f, h);
        fillPath.lineTo(0f, getY(0, h));

        for (int i = 1; i < sampleCount; i++) {
            float x0 = (i - 1) * dx;
            float y0 = getY(i - 1, h);
            float x1 = i * dx;
            float y1 = getY(i, h);

            float midX = (x0 + x1) * 0.5f;
            path.quadTo(midX, y0, x1, y1);
            fillPath.quadTo(midX, y0, x1, y1);
        }

        fillPath.lineTo((sampleCount - 1) * dx, h);
        fillPath.close();
    }

    private float getY(int index, float h) {
        float val = Math.min(graphMaxMs, Math.max(0f, (float) samples[index]));
        return h - (val / graphMaxMs) * h;
    }
}
