package com.example.amplifierpro.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;

public class DummyFragment extends Fragment implements ThemeManager.OnThemeChangeListener {

    private static final String YOUTUBE_URL = "https://m.youtube.com";
    private static final String KEY_WEB_HEIGHT = "dummy_web_height";

    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // Jembatan metadata YouTube -> receiver (format sama dgn MetaListenerService).
    private final Handler metaHandler = new Handler(Looper.getMainLooper());
    private Runnable metaPoller;
    private String metaTitle = "";
    private String metaArtist = "";
    private int metaPlaying = -1;
    private long metaPos = 0;
    private long metaDur = -1;
    private String metaRawTitle = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dummy, container, false);
        webView = root.findViewById(R.id.webViewDummy);
        setupWebView(savedInstanceState);
        applyTheme(ThemeManager.getInstance(requireContext()).getCurrentTheme());
        ThemeManager.getInstance(requireContext()).registerListener(this);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (customView != null) {
                            hideCustomView();
                        } else if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
        return root;
    }


    private void setupWebView(Bundle savedInstanceState) {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(webView, true);
        }
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // Scroll mulus: biarkan WebView menangani scroll internalnya sendiri,
        // matikan scrollbar/overscroll bawaan agar tidak patah-patah.
        webView.setBackgroundColor(0xFF000000);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setNestedScrollingEnabled(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                if (getActivity() != null) {
                    FrameLayout decor = (FrameLayout) getActivity().getWindow().getDecorView();
                    decor.addView(customView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                }
            }
            @Override
            public void onHideCustomView() {
                hideCustomView();
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                onYtTitle(title);
            }
        });
        startMetaPoller();
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(YOUTUBE_URL);
        }
    }

    private void goToPage(int page) {
        try {
            if (getActivity() == null) return;
            View parent = getView();
            ViewPager2 vp = null;
            if (parent != null) {
                ViewParent p = parent.getParent();
                while (p != null) {
                    if (p instanceof ViewPager2) { vp = (ViewPager2) p; break; }
                    if (p instanceof View) p = ((View) p).getParent();
                    else break;
                }
            }
            if (vp == null && getActivity().findViewById(R.id.viewPagerMain) instanceof ViewPager2) {
                vp = getActivity().findViewById(R.id.viewPagerMain);
            }
            if (vp == null || vp.getAdapter() == null) return;
            int count = vp.getAdapter().getItemCount();
            if (page < 0 || page >= count) return;
            boolean wasLocked = !vp.isUserInputEnabled();
            if (wasLocked) vp.setUserInputEnabled(true);
            vp.setCurrentItem(page, true);
        } catch (Exception ignored) {}
    }


    private void hideCustomView() {
        if (customView == null) return;
        try {
            if (getActivity() != null) {
                FrameLayout decor = (FrameLayout) getActivity().getWindow().getDecorView();
                decor.removeView(customView);
            }
        } catch (Exception ignored) {}
        try {
            if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        } catch (Exception ignored) {}
        customView = null;
        customViewCallback = null;
    }

    private void startMetaPoller() {
        stopMetaPoller();
        metaPoller = new Runnable() {
            @Override
            public void run() {
                pollYtMeta();
                metaHandler.postDelayed(this, 2000);
            }
        };
        metaHandler.postDelayed(metaPoller, 2000);
    }

    private void stopMetaPoller() {
        if (metaPoller != null) {
            try { metaHandler.removeCallbacks(metaPoller); } catch (Exception ignored) {}
            metaPoller = null;
        }
    }

    private void pollYtMeta() {
        if (webView == null) return;
        try {
            String js = "(function(){var t='';try{t=document.title||'';}catch(e){}"
                    + "var v=null;try{v=document.querySelector('video');}catch(e){}"
                    + "var p=1;var c=0;var d=0;"
                    + "try{if(v){p=((v.paused||v.ended)?0:1);"
                    + "if(isFinite(v.currentTime)&&v.currentTime>=0){c=Math.floor(v.currentTime*1000);}"
                    + "if(isFinite(v.duration)&&v.duration>0){d=Math.floor(v.duration*1000);}}}catch(e){}"
                    + "return JSON.stringify({t:t,p:p,c:c,d:d});})();";
            webView.evaluateJavascript(js, value -> {
                try {
                    onYtMetaResult(value);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private void onYtTitle(String title) {
        if (title == null) return;
        onYtMeta(title, metaPlaying < 0 ? 1 : metaPlaying, metaPos, metaDur);
    }

    private void onYtMetaResult(String json) {
        if (json == null || json.isEmpty() || json.equals("null")) return;
        try {
            String unquoted = json.trim();
            if (unquoted.startsWith("\"") && unquoted.endsWith("\"") && unquoted.length() >= 2) {
                unquoted = new org.json.JSONTokener(unquoted).nextValue().toString();
            }
            org.json.JSONObject obj = new org.json.JSONObject(unquoted);
            String t = obj.optString("t", "");
            int p = obj.optInt("p", 1);
            long c = obj.optLong("c", 0);
            long d = obj.optLong("d", 0);
            onYtMeta(t, p, c, d);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onYtMeta(String rawTitle, int playing, long posMs, long durMs) {
        if (rawTitle == null) rawTitle = "";
        String clean = cleanYtTitle(rawTitle);
        if (clean.isEmpty()) return;
        String title = clean;
        String artist = "YOUTUBE";
        posMs = Math.max(0, posMs);
        durMs = Math.max(0, durMs);
        boolean changed = !title.equals(metaTitle) || !artist.equals(metaArtist)
                || playing != metaPlaying || Math.abs(durMs - metaDur) > 2000
                || Math.abs(posMs - metaPos) > 3000;
        metaRawTitle = rawTitle;
        metaPlaying = playing;
        metaPos = posMs;
        metaDur = durMs;
        if (changed) broadcastYtMeta(title, artist, playing, posMs, durMs);
    }

    private String cleanYtTitle(String raw) {
        String t = raw.trim();
        if (t.endsWith(" - YouTube")) t = t.substring(0, t.length() - 10).trim();
        else if (t.equals("YouTube") || t.equals("YouTube Music")) return "";
        return t;
    }

    private void broadcastYtMeta(String title, String artist, int playing, long pos, long dur) {
        metaTitle = title;
        metaArtist = artist;
        try {
            if (getContext() == null) return;
            String safeTitle = title.replace("|", " ").trim();
            String safeArtist = artist.replace("|", " ").trim();
            if (safeTitle.isEmpty()) safeTitle = "YOUTUBE";
            String payload = safeTitle.toUpperCase() + "|" + safeArtist.toUpperCase()
                    + "|" + pos + "|" + dur + "|" + (playing == 0 ? 0 : 1);
            Intent intent = new Intent("com.example.amplifierpro.METADATA_UPDATED");
            intent.putExtra("info", payload);
            intent.setPackage(requireContext().getPackageName());
            requireContext().sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Background support: halaman tetap hidup saat swipe keluar fokus,
        // jadi jangan pernah pause WebView di sini/di onPause.
        if (webView != null) {
            try {
                webView.onResume();
            } catch (Exception ignored) {}
            try {
                webView.resumeTimers();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onPause() {
        // Sengaja TIDAK memanggil webView.onPause()/pauseTimers() agar
        // audio/video YouTube tidak mati saat swipe pindah halaman.
        // Hanya flush cookie agar session login tetap tersimpan.
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {}
        super.onPause();
    }

    @Override
    public void onThemeChanged(ThemePalette theme) {
        applyTheme(theme);
    }

    public void applyTheme(ThemePalette theme) {
        if (theme == null || getView() == null) return;
        View root = getView();
        root.setBackgroundColor(theme.backgroundColor);
    }

    @Override
    public void onDestroyView() {
        stopMetaPoller();
        try {
            ThemeManager.getInstance(requireContext()).unregisterListener(this);
        } catch (Exception ignored) {}
        hideCustomView();
        if (webView != null) {
            try { webView.stopLoading(); } catch (Exception ignored) {}
            try {
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
            } catch (Exception ignored) {}
            try {
                ViewGroup parent = (ViewGroup) webView.getParent();
                if (parent != null) parent.removeView(webView);
            } catch (Exception ignored) {}
            try { webView.destroy(); } catch (Exception ignored) {}
            webView = null;
        }
        super.onDestroyView();
    }
}
