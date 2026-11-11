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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;

public class DummyFragment extends Fragment implements ThemeManager.OnThemeChangeListener {

    private static final String YOUTUBE_URL = "https://m.youtube.com";
    private static final String KEY_WEB_HEIGHT = "dummy_web_height";

    public static final String ACTION_WEB_MEDIA_COMMAND = "com.example.amplifierpro.ACTION_WEB_MEDIA_COMMAND";
    public static final String EXTRA_COMMAND = "command";
    private static volatile DummyFragment activeInstance = null;

    private final BroadcastReceiver webCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_WEB_MEDIA_COMMAND.equals(intent.getAction())) {
                String cmd = intent.getStringExtra(EXTRA_COMMAND);
                if (cmd != null) {
                    handleRemoteCommandInternal(cmd);
                }
            }
        }
    };

    private static final String SCRIPT_BACKGROUND_PLAYBACK =
            "(function() {" +
            "  try {" +
            "    if (!window._bgPlaybackPatched) {" +
            "      window._bgPlaybackPatched = true;" +
            "      Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });" +
            "      Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });" +
            "      Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });" +
            "      var block = function(e) { if (e) { e.stopImmediatePropagation(); e.stopPropagation(); } };" +
            "      window.addEventListener('visibilitychange', block, true);" +
            "      document.addEventListener('visibilitychange', block, true);" +
            "      window.addEventListener('webkitvisibilitychange', block, true);" +
            "      document.addEventListener('webkitvisibilitychange', block, true);" +
            "      window.addEventListener('blur', block, true);" +
            "      document.addEventListener('blur', block, true);" +
            "    }" +
            "    var v = document.querySelector('video');" +
            "    if (v && !v._bgTracked) {" +
            "      v._bgTracked = true;" +
            "      v.addEventListener('play', function() { window._userWantsPlay = true; });" +
            "      v.addEventListener('ended', function() { window._userWantsPlay = false; });" +
            "    }" +
            "  } catch (e) {}" +
            "})();";

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
    private static volatile long lastWebPlayedTimestamp = 0L;
    private static volatile boolean isFragmentVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dummy, container, false);
        activeInstance = this;
        try {
            IntentFilter filter = new IntentFilter(ACTION_WEB_MEDIA_COMMAND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(webCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(webCommandReceiver, filter);
            }
        } catch (Exception ignored) {}
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
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectBackgroundPlaybackScript(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectBackgroundPlaybackScript(view);
            }

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
            try {
                android.content.SharedPreferences prefs = requireContext().getSharedPreferences("AMPLIFIER_SETTINGS", android.content.Context.MODE_PRIVATE);
                String lastUrl = prefs.getString("yt_last_url", YOUTUBE_URL);
                long lastPosMs = prefs.getLong("yt_last_pos", 0);
                if (lastUrl != null && !lastUrl.isEmpty() && !lastUrl.equals("about:blank") && !lastUrl.equals(YOUTUBE_URL)) {
                    if (lastPosMs > 5000 && (lastUrl.contains("watch?v=") || lastUrl.contains("youtu.be/"))) {
                        long sec = lastPosMs / 1000;
                        if (!lastUrl.contains("t=")) {
                            lastUrl += (lastUrl.contains("?") ? "&t=" : "?t=") + sec + "s";
                        }
                    }
                    webView.loadUrl(lastUrl);
                } else {
                    webView.loadUrl(YOUTUBE_URL);
                }
            } catch (Exception e) {
                webView.loadUrl(YOUTUBE_URL);
            }
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

    private void injectBackgroundPlaybackScript(WebView view) {
        if (view == null) return;
        try {
            view.evaluateJavascript(SCRIPT_BACKGROUND_PLAYBACK, null);
        } catch (Exception ignored) {}
    }

    private void pollYtMeta() {
        if (webView == null) return;
        try {
            String js = SCRIPT_BACKGROUND_PLAYBACK
                    + ";(function(){"
                    + "  function getM() {"
                    + "    var vs = document.querySelectorAll('video');"
                    + "    if (vs.length === 1) return vs[0];"
                    + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0 && !vs[i].paused) return vs[i]; }"
                    + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0) return vs[i]; }"
                    + "    return document.querySelector('video') || document.querySelector('audio');"
                    + "  }"
                    + "  var t = ''; try { t = document.title || ''; } catch(e) {}"
                    + "  var u = ''; try { u = window.location.href || ''; } catch(e) {}"
                    + "  var v = null; try { v = getM(); } catch(e) {}"
                    + "  var p = 0; var c = 0; var d = 0;"
                    + "  try {"
                    + "    if (v) {"
                    + "      if (isFinite(v.currentTime) && v.currentTime >= 0) { c = Math.floor(v.currentTime * 1000); }"
                    + "      if (isFinite(v.duration) && v.duration > 0) { d = Math.floor(v.duration * 1000); }"
                    + "      var isWatchOrShorts = (u.indexOf('watch') !== -1 || u.indexOf('shorts/') !== -1 || u.indexOf('youtu.be/') !== -1 || d > 0);"
                    + "      p = (!v.paused && !v.ended && v.readyState >= 2 && isWatchOrShorts) ? 1 : 0;"
                    + "    }"
                    + "  } catch(e) {}"
                    + "  return JSON.stringify({t: t, p: p, c: c, d: d});"
                    + "})();";
            webView.evaluateJavascript(js, value -> {
                try {
                    onYtMetaResult(value);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private void onYtTitle(String title) {
        if (title == null) return;
        onYtMeta(title, metaPlaying < 0 ? 0 : metaPlaying, metaPos, metaDur);
        pollYtMeta();
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
            int p = obj.optInt("p", 0);
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
        if (clean.isEmpty()) {
            metaPlaying = 0;
            return;
        }
        String title = clean;
        String artist = "YOUTUBE";
        posMs = Math.max(0, posMs);
        durMs = Math.max(0, durMs);
        if (playing == 1) {
            lastWebPlayedTimestamp = System.currentTimeMillis();
        }
        boolean changed = !title.equals(metaTitle) || !artist.equals(metaArtist)
                || playing != metaPlaying || Math.abs(durMs - metaDur) > 2000
                || Math.abs(posMs - metaPos) > 3000;
        metaRawTitle = rawTitle;
        metaPlaying = playing;
        metaPos = posMs;
        metaDur = durMs;
        // Jangan timpa metadata aplikasi eksternal jika Web UI sedang tidak memutar video
        if (changed && (playing == 1 || isFragmentVisible || !com.example.amplifierpro.service.MetaListenerService.isExternalAppPlaying())) {
            broadcastYtMeta(title, artist, playing, posMs, durMs);
        }
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
        isFragmentVisible = true;
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

    private void saveYtPlaybackState() {
        if (webView == null || getContext() == null) return;
        try {
            String currentUrl = webView.getUrl();
            if (currentUrl != null && !currentUrl.isEmpty() && !currentUrl.equals("about:blank")) {
                android.content.SharedPreferences prefs = requireContext().getSharedPreferences("AMPLIFIER_SETTINGS", android.content.Context.MODE_PRIVATE);
                prefs.edit()
                        .putString("yt_last_url", currentUrl)
                        .putLong("yt_last_pos", metaPos)
                        .apply();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onPause() {
        isFragmentVisible = false;
        saveYtPlaybackState();
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
        saveYtPlaybackState();
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

    // ==========================================
    // REMOTE WEB PLAYBACK CONTROL BRIDGE
    // ==========================================

    public static long getLastWebPlayedTimestamp() {
        return lastWebPlayedTimestamp;
    }

    public static boolean isWebTabVisible() {
        return isFragmentVisible;
    }

    public static boolean isWebPlaybackActive() {
        DummyFragment inst = activeInstance;
        return inst != null && inst.isWebPlaybackActiveInternal();
    }

    public boolean isWebPlaybackActiveInternal() {
        if (webView == null) return false;
        if (metaPlaying == 1) return true;
        String url = webView.getUrl();
        boolean isVideoUrl = url != null && (url.contains("watch?v=") || url.contains("youtu.be/") || url.contains("/shorts/"));
        if (metaPlaying == 0 && metaDur > 0 && isVideoUrl) return true;
        return isVideoUrl;
    }

    public static boolean isWebCurrentlyPlaying() {
        DummyFragment inst = activeInstance;
        return inst != null && inst.metaPlaying == 1;
    }

    public static void requestMetaRefresh() {
        DummyFragment inst = activeInstance;
        if (inst != null) {
            inst.metaHandler.post(inst::pollYtMeta);
        }
    }

    public static void executeRemoteCommand(Context context, String command) {
        DummyFragment inst = activeInstance;
        if (inst != null) {
            inst.metaHandler.post(() -> inst.handleRemoteCommandInternal(command));
        } else if (context != null) {
            try {
                Intent intent = new Intent(ACTION_WEB_MEDIA_COMMAND);
                intent.putExtra(EXTRA_COMMAND, command);
                intent.setPackage(context.getPackageName());
                context.sendBroadcast(intent);
            } catch (Exception ignored) {}
        }
    }

    public void handleRemoteCommandInternal(String command) {
        if (webView == null || command == null) return;
        Log.i("DummyFragment", "handleRemoteCommandInternal: " + command);
        lastWebPlayedTimestamp = System.currentTimeMillis();
        String js;
        switch (command) {
            case "TOGGLE":
            case "PLAY_PAUSE":
                js = "(function() {"
                        + "  function getM() {"
                        + "    var vs = document.querySelectorAll('video');"
                        + "    if (vs.length === 1) return vs[0];"
                        + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0 && !vs[i].paused) return vs[i]; }"
                        + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0) return vs[i]; }"
                        + "    return document.querySelector('video') || document.querySelector('audio');"
                        + "  }"
                        + "  try {"
                        + "    var m = getM();"
                        + "    var mp = document.getElementById('movie_player');"
                        + "    if (m) {"
                        + "      if (m.paused || m.ended) {"
                        + "        window._userWantsPlay = true;"
                        + "        if (mp && typeof mp.playVideo === 'function') { try { mp.playVideo(); } catch(e){} }"
                        + "        m.play();"
                        + "        return 'PLAYING';"
                        + "      } else {"
                        + "        window._userWantsPlay = false;"
                        + "        if (mp && typeof mp.pauseVideo === 'function') { try { mp.pauseVideo(); } catch(e){} }"
                        + "        m.pause();"
                        + "        return 'PAUSED';"
                        + "      }"
                        + "    }"
                        + "    var b = document.querySelector('.ytp-play-button') ||"
                        + "            document.querySelector('button.play-button') ||"
                        + "            document.querySelector('button[aria-label*=Play]') ||"
                        + "            document.querySelector('button[aria-label*=Pause]') ||"
                        + "            document.querySelector('button[aria-label*=Putar]') ||"
                        + "            document.querySelector('button[aria-label*=Jeda]');"
                        + "    if (b) { b.click(); return 'BTN_CLICKED'; }"
                        + "    var firstVid = document.querySelector('a[href*=\"/watch?v=\"]');"
                        + "    if (firstVid) { firstVid.click(); return 'OPEN_FIRST_VID'; }"
                        + "  } catch (e) { return 'ERR:' + e; }"
                        + "  return 'NO_ELEM';"
                        + "})();";
                if (metaPlaying >= 0 && !metaTitle.isEmpty()) {
                    metaPlaying = (metaPlaying == 1 ? 0 : 1);
                    broadcastYtMeta(metaTitle, metaArtist, metaPlaying, metaPos, metaDur);
                }
                break;

            case "PLAY":
                js = "(function() {"
                        + "  function getM() {"
                        + "    var vs = document.querySelectorAll('video');"
                        + "    if (vs.length === 1) return vs[0];"
                        + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0 && !vs[i].paused) return vs[i]; }"
                        + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0) return vs[i]; }"
                        + "    return document.querySelector('video') || document.querySelector('audio');"
                        + "  }"
                        + "  try {"
                        + "    window._userWantsPlay = true;"
                        + "    var mp = document.getElementById('movie_player');"
                        + "    if (mp && typeof mp.playVideo === 'function') { try { mp.playVideo(); } catch(e){} }"
                        + "    var m = getM();"
                        + "    if (m && m.paused) { m.play(); return 'PLAY'; }"
                        + "    var b = document.querySelector('button[aria-label*=Play]') || document.querySelector('button[aria-label*=Putar]');"
                        + "    if (b) { b.click(); return 'BTN_PLAY'; }"
                        + "  } catch (e) { return 'ERR:' + e; }"
                        + "  return 'NO_ELEM';"
                        + "})();";
                if (!metaTitle.isEmpty()) {
                    metaPlaying = 1;
                    broadcastYtMeta(metaTitle, metaArtist, 1, metaPos, metaDur);
                }
                break;

            case "PAUSE":
                js = "(function() {"
                        + "  function getM() {"
                        + "    var vs = document.querySelectorAll('video');"
                        + "    if (vs.length === 1) return vs[0];"
                        + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0 && !vs[i].paused) return vs[i]; }"
                        + "    for (var i = 0; i < vs.length; i++) { if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0) return vs[i]; }"
                        + "    return document.querySelector('video') || document.querySelector('audio');"
                        + "  }"
                        + "  try {"
                        + "    window._userWantsPlay = false;"
                        + "    var mp = document.getElementById('movie_player');"
                        + "    if (mp && typeof mp.pauseVideo === 'function') { try { mp.pauseVideo(); } catch(e){} }"
                        + "    var m = getM();"
                        + "    if (m && !m.paused) { m.pause(); return 'PAUSE'; }"
                        + "    var b = document.querySelector('button[aria-label*=Pause]') || document.querySelector('button[aria-label*=Jeda]');"
                        + "    if (b) { b.click(); return 'BTN_PAUSE'; }"
                        + "  } catch (e) { return 'ERR:' + e; }"
                        + "  return 'NO_ELEM';"
                        + "})();";
                if (!metaTitle.isEmpty()) {
                    metaPlaying = 0;
                    broadcastYtMeta(metaTitle, metaArtist, 0, metaPos, metaDur);
                }
                break;

            case "NEXT":
                js = "(function() {"
                        + "  try {"
                        + "    window._userWantsPlay = true;"
                        + "    var curUrl = window.location.href;"
                        + "    var curVid = '';"
                        + "    var mVid = curUrl.match(/[?&]v=([^&#]+)/);"
                        + "    if (mVid) curVid = mVid[1];"
                        + "    var btn = document.querySelector('button.ytp-next-button') ||"
                        + "              document.querySelector('a.ytp-next-button') ||"
                        + "              document.querySelector('button.next-button') ||"
                        + "              document.querySelector('.player-controls-middle-core-buttons button[aria-label*=Next]') ||"
                        + "              document.querySelector('.player-controls-middle-core-buttons button[aria-label*=Berikutnya]') ||"
                        + "              document.querySelector('button[aria-label=\"Next video\"]') ||"
                        + "              document.querySelector('button[aria-label=\"Video berikutnya\"]') ||"
                        + "              document.querySelector('button[aria-label*=Next]') ||"
                        + "              document.querySelector('button[aria-label*=Berikutnya]');"
                        + "    if (btn && !btn.disabled && btn.getAttribute('aria-disabled') !== 'true') {"
                        + "      btn.click();"
                        + "      return 'CLICK_NEXT_BTN';"
                        + "    }"
                        + "    var mp = document.getElementById('movie_player');"
                        + "    if (mp && typeof mp.nextVideo === 'function') {"
                        + "      try {"
                        + "        mp.nextVideo();"
                        + "        if (window.location.href !== curUrl) return 'MP_NEXT_VIDEO';"
                        + "      } catch(e) {}"
                        + "    }"
                        + "    var links = document.querySelectorAll('ytm-item-section-renderer a[href*=\"/watch?v=\"], ytm-compact-video-renderer a[href*=\"/watch?v=\"], ytm-video-with-context-renderer a[href*=\"/watch?v=\"], ytm-media-item a[href*=\"/watch?v=\"], a.media-item-thumbnail-container[href*=\"/watch?v=\"], a[href*=\"/watch?v=\"]');"
                        + "    for (var i = 0; i < links.length; i++) {"
                        + "      var a = links[i];"
                        + "      var href = a.getAttribute('href') || a.href || '';"
                        + "      if (href.indexOf('watch?v=') !== -1 && (curVid === '' || href.indexOf(curVid) === -1)) {"
                        + "        a.click();"
                        + "        setTimeout(function() {"
                        + "          if (window.location.href === curUrl && a.href) { window.location.href = a.href; }"
                        + "        }, 180);"
                        + "        return 'CLICK_NEXT_LINK:' + href;"
                        + "      }"
                        + "    }"
                        + "    var vs = document.querySelectorAll('video');"
                        + "    for (var j = 0; j < vs.length; j++) {"
                        + "      var v = vs[j];"
                        + "      if (v && isFinite(v.duration) && v.duration > 0) {"
                        + "        v.currentTime = Math.max(0, v.duration - 0.1);"
                        + "        v.play();"
                        + "        return 'SEEK_AUTOPLAY';"
                        + "      }"
                        + "    }"
                        + "  } catch (e) { return 'ERR:' + e; }"
                        + "  return 'NO_NEXT';"
                        + "})();";
                break;

            case "PREV":
                js = "(function() {"
                        + "  try {"
                        + "    window._userWantsPlay = true;"
                        + "    var vs = document.querySelectorAll('video');"
                        + "    var m = vs.length > 0 ? vs[0] : null;"
                        + "    for (var i = 0; i < vs.length; i++) {"
                        + "      if (vs[i] && isFinite(vs[i].duration) && vs[i].duration > 0) { m = vs[i]; break; }"
                        + "    }"
                        + "    if (m && isFinite(m.currentTime) && m.currentTime > 4.0) {"
                        + "      m.currentTime = 0;"
                        + "      m.play();"
                        + "      return 'RESTART';"
                        + "    }"
                        + "    var btn = document.querySelector('button.ytp-prev-button') ||"
                        + "              document.querySelector('a.ytp-prev-button') ||"
                        + "              document.querySelector('button.prev-button') ||"
                        + "              document.querySelector('button[aria-label=\"Previous video\"]') ||"
                        + "              document.querySelector('button[aria-label=\"Video sebelumnya\"]') ||"
                        + "              document.querySelector('button[aria-label*=Previous]') ||"
                        + "              document.querySelector('button[aria-label*=Sebelumnya]');"
                        + "    if (btn && !btn.disabled && btn.getAttribute('aria-disabled') !== 'true') {"
                        + "      btn.click();"
                        + "      return 'CLICK_PREV_BTN';"
                        + "    }"
                        + "    var mp = document.getElementById('movie_player');"
                        + "    if (mp && typeof mp.previousVideo === 'function') {"
                        + "      try { mp.previousVideo(); } catch(e) {}"
                        + "    }"
                        + "    if (window.history.length > 1) {"
                        + "      window.history.back();"
                        + "      return 'HISTORY_BACK';"
                        + "    }"
                        + "    if (m) { m.currentTime = 0; m.play(); return 'RESTART_FALLBACK'; }"
                        + "  } catch (e) { return 'ERR:' + e; }"
                        + "  return 'NO_PREV';"
                        + "})();";
                break;

            default:
                return;
        }

        try {
            webView.evaluateJavascript(js, result -> {
                Log.d("DummyFragment", "Command '" + command + "' evaluated with result: " + result);
            });
        } catch (Exception e) {
            Log.e("DummyFragment", "Error evaluating JS command", e);
        }

        metaHandler.postDelayed(this::pollYtMeta, 150);
        metaHandler.postDelayed(this::pollYtMeta, 500);
        metaHandler.postDelayed(this::pollYtMeta, 1200);
        metaHandler.postDelayed(this::pollYtMeta, 2500);
    }
}
