package com.example.amplifierpro.ui;

import com.example.amplifierpro.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.example.amplifierpro.manager.EqSettingsManager;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.manager.ThemePalette;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import com.example.amplifierpro.security.LicenseManager;

public class TransmitterActivity extends AppCompatActivity implements ThemeManager.OnThemeChangeListener {

    private ViewPager2 viewPager;
    private GestureDetector gestureDetector;
    private MainPagerAdapter adapter;
    private EqSettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        EdgeToEdge.enable(this, 
            SystemBarStyle.dark(Color.TRANSPARENT), 
            SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        settingsManager = new EqSettingsManager(this);
        applyAlwaysOnScreen(settingsManager.isAlwaysOnScreen());
        setContentView(R.layout.activity_transmitter);

        viewPager = findViewById(R.id.viewPagerMain);
        adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);

        // Auto-popup dialog lisensi HANYA jika masa trial sudah habis (expired)
        // Tidak muncul jika diblokir atau jika trial masih berjalan
        LicenseManager lm = LicenseManager.getInstance(this);
        if (lm.isTrialExpired()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    LicenseDialog.show(this, null);
                }
            }, 800);
        }

        // Apply global padding for status bar and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(viewPager, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Restore tab terakhir yang dibuka (default 2 jika baru pertama kali install)
        android.content.SharedPreferences appPrefs = getSharedPreferences("AMPLIFIER_SETTINGS", MODE_PRIVATE);
        int savedPage = appPrefs.getInt("last_viewpager_page", 2);
        if (savedInstanceState != null) {
            savedPage = savedInstanceState.getInt("saved_page", savedPage);
        }
        final int initialPage = savedPage;
        viewPager.post(() -> viewPager.setCurrentItem(initialPage, false));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // Simpan tab terakhir ke SharedPreferences
                getSharedPreferences("AMPLIFIER_SETTINGS", MODE_PRIVATE)
                        .edit().putInt("last_viewpager_page", position).apply();

                // Kunci swipe pager di halaman YouTube (index 0): scroll bebas
                // tanpa berebut gesture. Keluar via fling tegas dari host.
                viewPager.setUserInputEnabled(position != 0);
                // Pause/Resume animations in MainFragment (index 2)
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + 2);
                if (fragment instanceof MainFragment) {
                    if (position == 2) {
                        ((MainFragment) fragment).startAnimations();
                    } else {
                        ((MainFragment) fragment).stopAnimations();
                    }
                }
            }
        });

        setupStatusSwipeGesture();
        ThemeManager.getInstance(this).registerListener(this);

        IntentFilter closeFilter = new IntentFilter("com.example.amplifierpro.ACTION_CLOSE_APP");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeAppReceiver, closeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(closeAppReceiver, closeFilter);
        }
    }

    private final BroadcastReceiver closeAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finishAffinity();
            System.exit(0);
        }
    };

    private boolean isTouchInSavedReceivers = false;

    private void setupStatusSwipeGesture() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 150;
            private static final int SWIPE_VELOCITY_THRESHOLD = 150;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                
                // Active only when on MainFragment (position 2)
                if (viewPager.getCurrentItem() != 2) return false;

                // Jangan buka Status/Diagnostic jika swipe berada di area Saved Receivers
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + 2);
                if (fragment instanceof MainFragment) {
                    MainFragment mf = (MainFragment) fragment;
                    if (mf.isTouchInSavedReceivers(e1.getRawX(), e1.getRawY()) ||
                        mf.isTouchInSavedReceivers(e2.getRawX(), e2.getRawY())) {
                        return false;
                    }
                }

                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                // Detect Swipe Down
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (diffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        openStatusActivity();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void openStatusActivity() {
        // Stop animations before opening status activity (MainFragment is at index 1)
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + 2);
        if (fragment instanceof MainFragment) {
            ((MainFragment) fragment).stopAnimations();
        }
        
        Intent intent = new Intent(this, StatusActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_bottom);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (viewPager != null && viewPager.getCurrentItem() == 2 && gestureDetector != null) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + 2);
                if (fragment instanceof MainFragment) {
                    isTouchInSavedReceivers = ((MainFragment) fragment).isTouchInSavedReceivers(ev.getRawX(), ev.getRawY());
                } else {
                    isTouchInSavedReceivers = false;
                }
            }

            if (!isTouchInSavedReceivers) {
                gestureDetector.onTouchEvent(ev);
            }

            if (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                isTouchInSavedReceivers = false;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            VolumePeakHelper.handleVolumeKey(this, +1);
            return true;
        } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            VolumePeakHelper.handleVolumeKey(this, -1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewPager != null) {
            outState.putInt("saved_page", viewPager.getCurrentItem());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsManager != null) {
            applyAlwaysOnScreen(settingsManager.isAlwaysOnScreen());
        }
        // Ensure animations are correct on resume (MainFragment at index 2)
        if (viewPager.getCurrentItem() == 2) {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + 2);
            if (fragment instanceof MainFragment) {
                ((MainFragment) fragment).startAnimations();
            }
        }
    }

    public void applyAlwaysOnScreen(boolean enabled) {
        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(closeAppReceiver);
        } catch (Exception ignored) {}
        ThemeManager.getInstance(this).unregisterListener(this);
    }

    @Override
    public void onThemeChanged(ThemePalette theme) {
        if (theme == null) return;
        if (viewPager != null) {
            viewPager.setBackgroundColor(theme.backgroundColor);
        }
        getWindow().getDecorView().setBackgroundColor(theme.backgroundColor);
    }
}
