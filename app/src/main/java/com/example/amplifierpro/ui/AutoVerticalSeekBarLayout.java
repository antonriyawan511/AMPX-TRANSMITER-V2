package com.example.amplifierpro.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Layout khusus untuk menampung SeekBar vertikal (dengan rotasi -90 derajat)
 * secara adaptif. Class ini secara dinamis menyesuaikan lebar SeekBar horizontal
 * agar tepat sama dengan tinggi vertikal yang tersedia di dalam card equalizer,
 * sehingga teks frekuensi di bagian bawah tidak akan pernah terdorong keluar card.
 */
public class AutoVerticalSeekBarLayout extends FrameLayout {

    public AutoVerticalSeekBarLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public AutoVerticalSeekBarLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AutoVerticalSeekBarLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int availableHeight = getMeasuredHeight();
        int availableWidth = getMeasuredWidth();

        if (availableHeight <= 0 && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            availableHeight = (int) (100 * getResources().getDisplayMetrics().density);
        }

        if (availableHeight > 0 && getChildCount() > 0) {
            View child = getChildAt(0);
            if (child.getLayoutParams() != null) {
                boolean needsRemeasure = false;

                if (child.getLayoutParams().width != availableHeight) {
                    child.getLayoutParams().width = availableHeight;
                    needsRemeasure = true;
                }

                float density = getResources().getDisplayMetrics().density;
                int maxThickness = (int) (36 * density);
                int targetChildHeight = availableWidth > 0 ? Math.min(availableWidth, maxThickness) : maxThickness;
                if (targetChildHeight <= 0) {
                    targetChildHeight = maxThickness;
                }

                if (child.getLayoutParams().height != targetChildHeight) {
                    child.getLayoutParams().height = targetChildHeight;
                    needsRemeasure = true;
                }

                if (needsRemeasure) {
                    int childWidthSpec = MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY);
                    int childHeightSpec = MeasureSpec.makeMeasureSpec(targetChildHeight, MeasureSpec.EXACTLY);
                    child.measure(childWidthSpec, childHeightSpec);
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0 && getChildCount() > 0) {
            View child = getChildAt(0);
            if (child.getLayoutParams() != null) {
                if (child.getLayoutParams().width != h) {
                    child.getLayoutParams().width = h;
                    child.requestLayout();
                }
            }
        }
    }
}
