package com.example.amplifierpro.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Host halaman YouTube: pager DIKUNCI (setUserInputEnabled(false) di index 0),
 * jadi scroll WebView 100% bebas tanpa berebut gesture.
 * Keluar halaman hanya via EDGE-FLING tegas: sentuhan mulai <=24dp dari tepi
 * layar + fling horizontal cepat (min 1200dp/s) ke arah halaman sebelah.
 * Scroll / swipe / diagonal biasa TIDAK PERNAH pindah halaman.
 */
public class NestedScrollableHost extends FrameLayout {

    private final int touchSlop;
    private final float edgePx;
    private final float flingMinVelocity;
    private float downX;
    private float downY;
    private boolean edgeArmed;
    private VelocityTracker velocityTracker;

    public NestedScrollableHost(@NonNull Context context) {
        this(context, null);
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        float density = context.getResources().getDisplayMetrics().density;
        // Dilonggarkan: setengah layar bisa jadi pemicu (bukan cuma 24dp tepi),
        // kecepatan cukup 600dp/s (dulu 1200dp/s, terlalu berat).
        edgePx = 0.5f;
        flingMinVelocity = 600f * density;
    }

    private ViewPager2 findViewPager2() {
        ViewParent p = getParent();
        while (p != null) {
            if (p instanceof ViewPager2) return (ViewPager2) p;
            if (p instanceof View) p = ((View) p).getParent();
            else break;
        }
        return null;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Jangan pernah intercept: semua sentuhan milik WebView (scroll bebas).
        // Pindah halaman di-handle manual di onTouchEvent via edge-fling.
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return super.onTouchEvent(ev);
    }

    private void trackEdgeFling(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            float rawX = ev.getRawX();
            float screenW = getResources().getDisplayMetrics().widthPixels;
            // edgePx kini fraksi lebar (0.5 = separuh layar): mulai dari mana
            // saja di separuh tepi tetap dihitung sebagai niat pindah halaman.
            float edge = screenW * edgePx;
            edgeArmed = rawX <= edge || rawX >= screenW - edge;
            if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
            else velocityTracker.clear();
            velocityTracker.addMovement(ev);
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (velocityTracker != null) velocityTracker.addMovement(ev);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            try {
                if (edgeArmed && velocityTracker != null && action == MotionEvent.ACTION_UP) {
                    velocityTracker.addMovement(ev);
                    velocityTracker.computeCurrentVelocity(1000);
                    float vx = velocityTracker.getXVelocity();
                    float vy = velocityTracker.getYVelocity();
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    boolean fastFling = Math.abs(vx) >= flingMinVelocity
                            && Math.abs(vx) >= Math.abs(vy) * 1.5f
                            && Math.abs(dx) > touchSlop * 2;
                    // Cadangan: swipe panjang pelan tapi tegas (jarak > 120dp,
                    // dominan horizontal 1.5x) juga pindah walau fling pelan.
                    float density = getResources().getDisplayMetrics().density;
                    boolean longSwipe = Math.abs(dx) > 120f * density
                            && Math.abs(dx) >= Math.abs(dy) * 1.5f;
                    if (fastFling || longSwipe) {
                        ViewPager2 vp = findViewPager2();
                        if (vp != null && vp.getAdapter() != null) {
                            int cur = vp.getCurrentItem();
                            int count = vp.getAdapter().getItemCount();
                            int target = dx < 0 ? cur + 1 : cur - 1;
                            if (target >= 0 && target < count) {
                                boolean wasLocked = !vp.isUserInputEnabled();
                                if (wasLocked) vp.setUserInputEnabled(true);
                                vp.setCurrentItem(target, true);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                edgeArmed = false;
                if (velocityTracker != null) {
                    try { velocityTracker.recycle(); } catch (Exception ignored) {}
                    velocityTracker = null;
                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Intip untuk edge-fling tanpa merebut gesture WebView.
        MotionEvent copy = null;
        try {
            copy = MotionEvent.obtain(ev);
            trackEdgeFling(copy);
        } catch (Exception ignored) {
        } finally {
            if (copy != null) {
                try { copy.recycle(); } catch (Exception ignored) {}
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}

