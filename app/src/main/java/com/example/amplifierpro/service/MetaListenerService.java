package com.example.amplifierpro.service;

import com.example.amplifierpro.network.CommandServer;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import androidx.annotation.Nullable;

import java.util.List;

public class MetaListenerService extends NotificationListenerService {
    private static final String TAG = "MetaListenerService";
    public static final String ACTION_CONTROLLER_STATUS = "com.example.amplifierpro.CONTROLLER_STATUS";
    public static final String EXTRA_HAS_CONTROLLER = "has_controller";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_IS_PLAYING = "is_playing";
    public static final String ACTION_REQUEST_REFRESH = "com.example.amplifierpro.REQUEST_REFRESH";

    private MediaSessionManager mediaSessionManager;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private MediaController activeController = null;
    private CommandServer commandServer = null;
    private boolean isRunning = true;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionListener = 
            controllers -> findActiveController();

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_REQUEST_REFRESH.equals(intent.getAction())) {
                findActiveController();
                broadcastCurrentInfo(true);
            }
        }
    };

    // Command deduplication to handle UDP bursts correctly
    private String lastCommand = "";
    private long lastCommandTime = 0;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            broadcastCurrentInfo(true);
        }
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            broadcastCurrentInfo(true);
        }
    };

    private String lastTitle = "";
    private String lastArtist = "";
    private int lastState = -1;
    private long lastDuration = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        startCommandListener();
        
        IntentFilter filter = new IntentFilter(ACTION_REQUEST_REFRESH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshReceiver, filter);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            ComponentName cn = new ComponentName(this, MetaListenerService.class);
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, cn);
            findActiveController();
        } catch (SecurityException e) {
            Log.e(TAG, "Permission not granted for session listener");
            broadcastStatus(false, null, false);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener);
        }
        if (activeController != null) activeController.unregisterCallback(mediaCallback);
        if (commandServer != null) commandServer.stop();
        try {
            unregisterReceiver(refreshReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    public static final String ACTION_RTT_STATS_UPDATED = "com.example.amplifierpro.RTT_STATS_UPDATED";
    public static final String EXTRA_RTT_MIN = "rtt_min";
    public static final String EXTRA_RTT_AVG = "rtt_avg";
    public static final String EXTRA_RTT_MAX = "rtt_max";
    public static final String EXTRA_RTT_SAMPLES = "rtt_samples";

    private void startCommandListener() {
        commandServer = new CommandServer(50008, new CommandServer.CommandListener() {
            @Override
            public void onCommandReceived(String command) {}

            @Override
            public void onCommandReceived(String command, String senderIp) {
                Log.d("DEBUG_RECV", "RAW UDP Received from " + senderIp + ": '" + command + "'");
                if (command.startsWith("REC_VOL:")) {
                    handleReceiverVolumeFeedback(command, senderIp);
                } else if (command.startsWith("VOL_CAPS:")) {
                    handleReceiverVolumeCaps(command, senderIp);
                } else if (command.startsWith("RTT_STATS:")) {
                    handleRttStatsFeedback(command);
                } else {
                    handleRemoteCommand(command);
                }
            }
        });
        commandServer.start();
    }

    private void handleRttStatsFeedback(String command) {
        try {
            String[] parts = command.substring(10).split(":");
            if (parts.length >= 1) {
                String[] stats = parts[0].split(",");
                long min = Long.parseLong(stats[0]);
                long avg = Long.parseLong(stats[1]);
                long max = Long.parseLong(stats[2]);

                Intent intent = new Intent(ACTION_RTT_STATS_UPDATED);
                intent.putExtra(EXTRA_RTT_MIN, min);
                intent.putExtra(EXTRA_RTT_AVG, avg);
                intent.putExtra(EXTRA_RTT_MAX, max);

                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    String[] sampleStrings = parts[1].split(",");
                    long[] samples = new long[sampleStrings.length];
                    for (int i = 0; i < sampleStrings.length; i++) {
                        samples[i] = Long.parseLong(sampleStrings[i]);
                    }
                    intent.putExtra(EXTRA_RTT_SAMPLES, samples);
                }
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleReceiverVolumeFeedback(String command, String senderIp) {
        try {
            // Format baru: REC_VOL:<index>:<max>  (satuan STEP perangkat, lossless)
            // Format lama: REC_VOL:<persen>       (tetap didukung untuk kompatibilitas)
            String payload = command.substring(8);
            com.example.amplifierpro.manager.EqSettingsManager manager =
                    new com.example.amplifierpro.manager.EqSettingsManager(this);
            String ip = com.example.amplifierpro.service.AudioStreamService.currentReceiverIp;
            if (ip == null || ip.isEmpty()) ip = senderIp;
            if (ip == null || ip.isEmpty()) ip = manager.getActiveReceiverIp();
            if (ip != null && !ip.isEmpty() && manager.getActiveReceiverIp().isEmpty()) {
                manager.setActiveReceiverIp(ip);
            }

            String[] parts = payload.split(":");
            int index = Integer.parseInt(parts[0]);
            int max = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
            // Simpan sebagai laporan (fromReceiver=true) -> tidak dikirim balik, anti-loop.
            manager.applyReceiverVolumeReport(ip, index, max);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** VOL_CAPS:<max>:<current> — kapabilitas volume perangkat receiver. */
    private void handleReceiverVolumeCaps(String command, String senderIp) {
        try {
            String payload = command.substring(9);
            String[] parts = payload.split(":");
            int max = Integer.parseInt(parts[0]);
            int current = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
            com.example.amplifierpro.manager.EqSettingsManager manager =
                    new com.example.amplifierpro.manager.EqSettingsManager(this);
            String ip = com.example.amplifierpro.service.AudioStreamService.currentReceiverIp;
            if (ip == null || ip.isEmpty()) ip = senderIp;
            if (ip == null || ip.isEmpty()) ip = manager.getActiveReceiverIp();
            if (ip != null && !ip.isEmpty() && manager.getActiveReceiverIp().isEmpty()) {
                manager.setActiveReceiverIp(ip);
            }
            manager.setReceiverVolumeCaps(ip, max, current);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleRemoteCommand(String command) {
        Log.d("DEBUG_RECV", "Processing command: " + command);
        long now = System.currentTimeMillis();
        // Ignore duplicate destructive commands received within 300ms (to handle bursts)
        if (!command.equals("GET_META") && command.equals(lastCommand) && (now - lastCommandTime < 300)) {
            Log.d("DEBUG_RECV", "Command " + command + " ignored (deduplication)");
            return;
        }
        lastCommand = command;
        lastCommandTime = now;

        updateHandler.post(() -> attemptExecuteCommand(command, true));
    }

    private void sendMediaKeyEvent(MediaController controller, int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        if (controller != null) {
            try {
                controller.dispatchMediaButtonEvent(down);
                controller.dispatchMediaButtonEvent(up);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void executeFallbackMediaKey(String command) {
        int keyCode = 0;
        switch (command) {
            case "TOGGLE":
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                break;
            case "NEXT":
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
                break;
            case "PREV":
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                break;
        }
        if (keyCode != 0) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long now = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
                KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
                try {
                    am.dispatchMediaKeyEvent(down);
                    am.dispatchMediaKeyEvent(up);
                    Log.i("DEBUG_RECV", "Fallback AudioManager media key dispatched: " + keyCode);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void attemptExecuteCommand(String command, boolean allowRetry) {
        findActiveController();
        if (activeController == null) {
            if (allowRetry) {
                Log.w("DEBUG_RECV", "RETRY: activeController NULL untuk " + command + ", coba lagi dalam 200ms");
                updateHandler.postDelayed(() -> attemptExecuteCommand(command, false), 200);
            } else {
                Log.e("DEBUG_RECV", "GAGAL: activeController tetap NULL, executing AudioManager fallback untuk: " + command);
                executeFallbackMediaKey(command);
            }
            return;
        }

        MediaController.TransportControls controls = activeController.getTransportControls();
        Log.i("DEBUG_RECV", "SUCCESS: Executing " + command + " on " + activeController.getPackageName());
        switch (command) {
            case "GET_META":
                broadcastCurrentInfo(true);
                break;
            case "TOGGLE":
                PlaybackState state = activeController.getPlaybackState();
                long actions = (state != null) ? state.getActions() : 0L;
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    if ((actions & PlaybackState.ACTION_PAUSE) != 0) {
                        try { controls.pause(); } catch (Exception ignored) {}
                    } else {
                        sendMediaKeyEvent(activeController, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    }
                } else if (state != null && (state.getState() == PlaybackState.STATE_PAUSED || state.getState() == PlaybackState.STATE_STOPPED)) {
                    if ((actions & PlaybackState.ACTION_PLAY) != 0) {
                        try { controls.play(); } catch (Exception ignored) {}
                    } else {
                        sendMediaKeyEvent(activeController, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    }
                } else {
                    sendMediaKeyEvent(activeController, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                }
                break;
            case "NEXT":
                PlaybackState nextState = activeController.getPlaybackState();
                long nextActions = (nextState != null) ? nextState.getActions() : 0L;
                if ((nextActions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
                    try { controls.skipToNext(); } catch (Exception ignored) {}
                } else {
                    sendMediaKeyEvent(activeController, KeyEvent.KEYCODE_MEDIA_NEXT);
                }
                break;
            case "PREV":
                PlaybackState prevState = activeController.getPlaybackState();
                long prevActions = (prevState != null) ? prevState.getActions() : 0L;
                if ((prevActions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
                    try { controls.skipToPrevious(); } catch (Exception ignored) {}
                } else {
                    sendMediaKeyEvent(activeController, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                }
                break;
        }
    }

    private void findActiveController() {
        try {
            ComponentName cn = new ComponentName(this, MetaListenerService.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
            
            MediaController bestController = null;
            if (controllers != null && !controllers.isEmpty()) {
                // Priority 1: Currently Playing
                for (MediaController controller : controllers) {
                    PlaybackState state = controller.getPlaybackState();
                    if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                        bestController = controller;
                        break;
                    }
                }
                
                // Priority 2: Buffering
                if (bestController == null) {
                    for (MediaController controller : controllers) {
                        PlaybackState state = controller.getPlaybackState();
                        if (state != null && state.getState() == PlaybackState.STATE_BUFFERING) {
                            bestController = controller;
                            break;
                        }
                    }
                }
                
                // Priority 3: First available
                if (bestController == null) {
                    bestController = controllers.get(0);
                }
            }

            if (bestController != activeController) {
                if (activeController != null) activeController.unregisterCallback(mediaCallback);
                activeController = bestController;
                if (activeController != null) activeController.registerCallback(mediaCallback);
            }

            if (activeController != null) {
                PlaybackState ps = activeController.getPlaybackState();
                boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                broadcastStatus(true, activeController.getPackageName(), playing);
            } else {
                broadcastStatus(false, null, false);
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Permission error");
            broadcastStatus(false, "PERMISSION_DENIED", false);
        }
    }

    private void broadcastStatus(boolean found, String pkg, boolean isPlaying) {
        Intent statusIntent = new Intent(ACTION_CONTROLLER_STATUS);
        statusIntent.putExtra(EXTRA_HAS_CONTROLLER, found);
        statusIntent.putExtra(EXTRA_PACKAGE_NAME, pkg);
        statusIntent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        statusIntent.setPackage(getPackageName());
        sendBroadcast(statusIntent);
    }

    private void broadcastCurrentInfo(boolean force) {
        if (activeController == null) {
            if (force) {
                // If forced but no controller, send a "No Media" signal
                Intent intent = new Intent("com.example.amplifierpro.METADATA_UPDATED");
                intent.putExtra("info", "WIFI PLAYING||0|0|1");
                sendBroadcast(intent);
            }
            return;
        }

        MediaMetadata metadata = activeController.getMetadata();
        PlaybackState state = activeController.getPlaybackState();

        String title = "UNKNOWN";
        String artist = "";
        long duration = 0;
        long position = 0;
        int isPlaying = 0;

        if (metadata != null) {
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title == null || title.isEmpty()) {
                title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            }

            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null || artist.trim().isEmpty()) {
                artist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            }
            if (artist == null || artist.trim().isEmpty()) {
                artist = metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR);
            }
            if (artist == null || artist.trim().isEmpty()) {
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }

            duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }

        if (state != null) {
            position = state.getPosition();
            int currentState = state.getState();
            isPlaying = (currentState == PlaybackState.STATE_PLAYING || currentState == PlaybackState.STATE_BUFFERING) ? 1 : 0;
        }

        if (title == null || title.isEmpty() || title.equals("UNKNOWN")) {
            title = "WIFI STREAMING";
        }
        if (artist == null) {
            artist = "";
        }
        artist = artist.trim();

        // Fallback: If artist is empty but title contains " - ", extract artist and title
        if (artist.isEmpty() && title.contains(" - ")) {
            String[] parts = title.split(" - ", 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                artist = parts[0].trim();
                title = parts[1].trim();
            }
        }

        // Sanitize delimiter character '|' to prevent UDP packet corruption
        title = title.replace("|", " ").trim();
        artist = artist.replace("|", " ").trim();

        // Deteksi Perubahan
        boolean changed = !title.equals(lastTitle) || !artist.equals(lastArtist) || isPlaying != lastState || Math.abs(duration - lastDuration) > 2000;

        if (changed || force) {
            lastTitle = title;
            lastArtist = artist;
            lastState = isPlaying;
            lastDuration = duration;

            String payload = title.toUpperCase() + "|" + artist.toUpperCase() + "|" + position + "|" + duration + "|" + isPlaying;
            Intent intent = new Intent("com.example.amplifierpro.METADATA_UPDATED");
            intent.putExtra("info", payload);
            sendBroadcast(intent);
        }
    }

    @Override
    public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        // Any music-related notification can trigger a check, 
        // but we no longer rely on a hardcoded package filter.
        findActiveController();
        broadcastCurrentInfo(true);
    }
}
