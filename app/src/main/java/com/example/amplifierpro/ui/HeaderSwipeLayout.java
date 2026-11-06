package com.example.amplifierpro.ui;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.amplifierpro.R;

public class HeaderSwipeLayout extends FrameLayout {

    public interface OnSwipeListener {
        void onSwipeRightToLeft(); // Finger swiped right to left
        void onSwipeLeftToRight(); // Finger swiped left to right
    }

    private OnSwipeListener swipeListener;
    private float startRawX, startRawY;
    private boolean isSwiping = false;
    private boolean isTouchOnSeekBar = false;
    private int touchSlop;
    private float minSwipeDistance;
    private int fixedHeaderHeight = 0;
    private View seekBarView;

    public HeaderSwipeLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    public HeaderSwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public HeaderSwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minSwipeDistance = 22f * context.getResources().getDisplayMetrics().density;
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        this.swipeListener = listener;
    }

    public void setSeekBarView(View view) {
        this.seekBarView = view;
    }

    private boolean isTouchInsideView(View v, float rawX, float rawY) {
        if (v == null || !v.isShown()) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        Rect rect = new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
        return rect.contains((int) rawX, (int) rawY);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startRawX = ev.getRawX();
                startRawY = ev.getRawY();
                isSwiping = false;
                if (seekBarView == null) {
                    seekBarView = findViewById(R.id.seekBarMasterVolume);
                }
                isTouchOnSeekBar = isTouchInsideView(seekBarView, ev.getRawX(), ev.getRawY());
                if (!isTouchOnSeekBar && getParent() != null) {
                    // Disallow parent ViewPager2 from intercepting horizontal touches on the header!
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isTouchOnSeekBar) {
                    return false;
                }
                float dx = ev.getRawX() - startRawX;
                float dy = ev.getRawY() - startRawY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    isSwiping = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isSwiping = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }
        return isSwiping;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startRawX = ev.getRawX();
                startRawY = ev.getRawY();
                if (!isTouchOnSeekBar && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isTouchOnSeekBar && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_UP:
                float totalDx = ev.getRawX() - startRawX;
                float totalDy = ev.getRawY() - startRawY;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (Math.abs(totalDx) >= minSwipeDistance && Math.abs(totalDx) > Math.abs(totalDy)) {
                    if (swipeListener != null) {
                        if (totalDx < 0) {
                            swipeListener.onSwipeRightToLeft();
                        } else {
                            swipeListener.onSwipeLeftToRight();
                        }
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                isSwiping = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View normalView = findViewById(R.id.headerNormalView);
        if (normalView != null) {
            normalView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (normalView.getMeasuredHeight() > 0) {
                fixedHeaderHeight = normalView.getMeasuredHeight();
            }
        }

        View graphView = findViewById(R.id.headerGraphView);
        if (graphView != null && fixedHeaderHeight > 0) {
            ViewGroup.LayoutParams lp = graphView.getLayoutParams();
            int targetHeight = fixedHeaderHeight;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                targetHeight = fixedHeaderHeight - mlp.topMargin - mlp.bottomMargin;
            }
            if (lp.height != targetHeight && targetHeight > 0) {
                lp.height = targetHeight;
                graphView.setLayoutParams(lp);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (fixedHeaderHeight > 0) {
            setMeasuredDimension(getMeasuredWidth(), fixedHeaderHeight);
        }
    }
}
