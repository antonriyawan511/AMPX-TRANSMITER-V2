package com.example.amplifierpro.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Custom Drawable rendering the dual-color gradient peak detector
 * matching the exact color ratio and theme accent behavior of the receiver.
 *
 * Ratio:
 *  0.00f - 0.62f: Base color (e.g. vuBaseColor)
 *  0.62f - 0.78f: Smooth transition from vuBaseColor to accentColor
 *  0.78f - 1.00f: Accent color (theme.accentColor)
 */
public class VuGradientDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private int baseColor;
    private int accentColor;
    private float cornerRadius;
    private int lastHeight = -1;
    private int lastTop = -1;

    public VuGradientDrawable(int baseColor, int accentColor, float cornerRadius) {
        this.baseColor = baseColor;
        this.accentColor = accentColor;
        this.cornerRadius = cornerRadius;
        this.paint.setStyle(Paint.Style.FILL);
    }

    public void setColors(int baseColor, int accentColor) {
        if (this.baseColor != baseColor || this.accentColor != accentColor) {
            this.baseColor = baseColor;
            this.accentColor = accentColor;
            updateShader(getBounds());
            invalidateSelf();
        }
    }

    public void setCornerRadius(float cornerRadius) {
        if (this.cornerRadius != cornerRadius) {
            this.cornerRadius = cornerRadius;
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        updateShader(bounds);
    }

    private void updateShader(@NonNull Rect bounds) {
        int height = bounds.height();
        if (height <= 0) return;

        lastHeight = height;
        lastTop = bounds.top;

        // Exactly matching receiver's VuMeterView gradient stops and ratio:
        // colors: baseColor (0.00f), baseColor (0.62f), accentColor (0.78f), accentColor (1.00f)
        // From bottom (bounds.bottom) to top (bounds.top)
        int[] colors = new int[] {
            baseColor,
            baseColor,
            accentColor,
            accentColor
        };
        float[] positions = new float[] {
            0.00f,
            0.62f,
            0.78f,
            1.00f
        };

        LinearGradient shader = new LinearGradient(
            0f, bounds.bottom,
            0f, bounds.top,
            colors, positions,
            Shader.TileMode.CLAMP
        );
        paint.setShader(shader);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;

        if (paint.getShader() == null || bounds.height() != lastHeight || bounds.top != lastTop) {
            updateShader(bounds);
        }

        rectF.set(bounds);
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
        } else {
            canvas.drawRect(rectF, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
