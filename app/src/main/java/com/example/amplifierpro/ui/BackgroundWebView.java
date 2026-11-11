package com.example.amplifierpro.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BackgroundWebView extends WebView {

    public BackgroundWebView(@NonNull Context context) {
        super(context);
    }

    public BackgroundWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BackgroundWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        // Teruskan View.VISIBLE agar Chromium rendering engine tidak men-suspend audio
        // atau mematikan decoder media HTML5 saat aplikasi di-minimize/background.
        super.onWindowVisibilityChanged(View.VISIBLE);
    }

    @Override
    public void dispatchWindowVisibilityChanged(int visibility) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, View.VISIBLE);
    }
}
