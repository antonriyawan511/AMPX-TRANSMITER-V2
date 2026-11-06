package com.example.amplifierpro.manager;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ThemeManager {
    public static final String ACTION_THEME_CHANGED = "com.example.amplifierpro.ACTION_THEME_CHANGED";
    public static final String EXTRA_THEME_INDEX = "extra_theme_index";

    private static final String PREF_NAME = "theme_preferences";
    private static final String KEY_THEME_INDEX = "current_theme_index";

    private static ThemeManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnThemeChangeListener {
        void onThemeChanged(ThemePalette theme);
    }

    private final List<OnThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    public static final String ACTION_CYCLE_THEME = "com.example.amplifierpro.CYCLE_THEME";
    public static final String ACTION_SET_THEME = "com.example.amplifierpro.SET_THEME";

    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CYCLE_THEME);
        filter.addAction(ACTION_SET_THEME);
        android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null) return;
                if (ACTION_CYCLE_THEME.equals(intent.getAction())) {
                    cycleTheme();
                } else if (ACTION_SET_THEME.equals(intent.getAction())) {
                    int idx = intent.getIntExtra("index", 0);
                    setThemeIndex(idx);
                }
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            this.context.registerReceiver(receiver, filter);
        }
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    public int getThemeIndex() {
        int idx = prefs.getInt(KEY_THEME_INDEX, 0);
        if (idx < 0 || idx >= ThemePalette.ALL_THEMES.length) {
            idx = 0;
        }
        return idx;
    }

    public ThemePalette getCurrentTheme() {
        return ThemePalette.ALL_THEMES[getThemeIndex()];
    }

    public void setThemeIndex(int index) {
        if (index < 0 || index >= ThemePalette.ALL_THEMES.length) {
            index = 0;
        }
        prefs.edit().putInt(KEY_THEME_INDEX, index).apply();

        ThemePalette theme = ThemePalette.ALL_THEMES[index];
        notifyListeners(theme);

        Intent intent = new Intent(ACTION_THEME_CHANGED);
        intent.putExtra(EXTRA_THEME_INDEX, index);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public ThemePalette cycleTheme() {
        int next = (getThemeIndex() + 1) % ThemePalette.ALL_THEMES.length;
        setThemeIndex(next);
        return ThemePalette.ALL_THEMES[next];
    }

    public void registerListener(OnThemeChangeListener listener) {
        if (listener != null) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
            listener.onThemeChanged(getCurrentTheme());
        }
    }

    public void unregisterListener(OnThemeChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(ThemePalette theme) {
        mainHandler.post(() -> {
            for (OnThemeChangeListener listener : listeners) {
                if (listener != null) {
                    try {
                        listener.onThemeChanged(theme);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
