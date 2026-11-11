package com.example.amplifierpro.service;

import com.example.amplifierpro.network.CommandServer;
import com.example.amplifierpro.ui.DummyFragment;
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
                broadcastRttStats();
            }
        }
    };

    private static volatile long lastRttMin = 0;
    private static volatile long lastRttAvg = 0;
    private static volatile long lastRttMax = 0;
    private static volatile long[] lastRttSamples = null;

    private void broadcastRttStats() {
        if (lastRttAvg > 0 || (lastRttSamples != null && lastRttSamples.length > 0)) {
            Intent intent = new Intent(ACTION_RTT_STATS_UPDATED);
            intent.putExtra(EXTRA_RTT_MIN, lastRttMin);
            intent.putExtra(EXTRA_RTT_AVG, lastRttAvg);
            intent.putExtra(EXTRA_RTT_MAX, lastRttMax);
            if (lastRttSamples != null) {
                intent.putExtra(EXTRA_RTT_SAMPLES, lastRttSamples);
            }
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }

    // Command deduplication to handle UDP bursts correctly
    private String lastCommand = "";
    private long lastCommandTime = 0;

    private static volatile long lastExternalPlayedTimestamp = 0L;
    private static volatile boolean externalCurrentlyPlaying = false;

    public static boolean isExternalAppPlaying() {
        return externalCurrentlyPlaying;
    }

    public static long getLastExternalPlayedTimestamp() {
        return lastExternalPlayedTimestamp;
    }

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            if (!DummyFragment.isWebCurrentlyPlaying()) {
                broadcastCurrentInfo(true);
            }
        }
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                lastExternalPlayedTimestamp = System.currentTimeMillis();
                externalCurrentlyPlaying = true;
            } else {
                externalCurrentlyPlaying = false;
            }
            if (!DummyFragment.isWebCurrentlyPlaying()) {
                broadcastCurrentInfo(true);
            }
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
                if ("REC_BYE".equals(command) || "REC_STOP".equals(command)) {
                    AudioStreamService.notifyReceiverDisconnected(senderIp);
                    return;
                }

                // Any UDP command from receiver proves that receiver is alive
                AudioStreamService.notifyReceiverAlive(senderIp);

                if (command.startsWith("REC_VOL_PCT:")) {
                    handleReceiverVolumePercentFeedback(command, senderIp);
                } else if (command.startsWith("REC_VOL:")) {
                    handleReceiverVolumeFeedback(command, senderIp);
                } else if (command.startsWith("VOL_CAPS:")) {
                    handleReceiverVolumeCaps(command, senderIp);
                } else if (command.startsWith("REC_HW:")) {
                    handleReceiverHardwareCaps(command, senderIp);
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
                if (stats.length >= 3) {
                    lastRttMin = Long.parseLong(stats[0].trim());
                    lastRttAvg = Long.parseLong(stats[1].trim());
                    lastRttMax = Long.parseLong(stats[2].trim());
                }

                if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                    String[] sampleStrings = parts[1].split(",");
                    java.util.ArrayList<Long> sampleList = new java.util.ArrayList<>();
                    for (String s : sampleStrings) {
                        String t = s.trim();
                        if (!t.isEmpty()) {
                            try {
                                sampleList.add(Long.parseLong(t));
                            } catch (Exception ignored) {}
                        }
                    }
                    if (!sampleList.isEmpty()) {
                        long[] samples = new long[sampleList.size()];
                        for (int i = 0; i < sampleList.size(); i++) {
                            samples[i] = sampleList.get(i);
                        }
                        lastRttSamples = samples;
                    }
                }
                broadcastRttStats();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleReceiverVolumePercentFeedback(String command, String senderIp) {
        try {
            // Format baru: REC_VOL_PCT:<percent>
            String payload = command.substring(12);
            com.example.amplifierpro.manager.EqSettingsManager manager =
                    new com.example.amplifierpro.manager.EqSettingsManager(this);
            String ip = senderIp;
            if (ip == null || ip.trim().isEmpty()) {
                ip = com.example.amplifierpro.service.AudioStreamService.currentReceiverIp;
            }
            if (ip != null && !ip.trim().isEmpty()) {
                manager.setActiveReceiverIp(ip.trim());
            }

            int percent = Integer.parseInt(payload.trim());
            manager.applyReceiverVolumePercentReport(ip, percent);
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
            String ip = senderIp;
            if (ip == null || ip.trim().isEmpty()) {
                ip = com.example.amplifierpro.service.AudioStreamService.currentReceiverIp;
            }
            if (ip != null && !ip.trim().isEmpty()) {
                manager.setActiveReceiverIp(ip.trim());
            }

            String[] parts = payload.split(":");
            int index = Integer.parseInt(parts[0]);
            int max = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
            long seq = 0L;
            if (parts.length >= 3) {
                try { seq = Long.parseLong(parts[2].trim()); } catch (Exception ignored) {}
            }
            // Simpan sebagai laporan (fromReceiver=true) -> tidak dikirim balik, anti-loop.
            // Seq membuang paket duplikat/stale agar UI TX tidak ditimpa nilai lama.
            manager.applyReceiverVolumeReport(ip, index, max, seq);
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
            long seq = 0L;
            if (parts.length >= 3) {
                try { seq = Long.parseLong(parts[2].trim()); } catch (Exception ignored) {}
            }
            com.example.amplifierpro.manager.EqSettingsManager manager =
                    new com.example.amplifierpro.manager.EqSettingsManager(this);
            String ip = senderIp;
            if (ip == null || ip.trim().isEmpty()) {
                ip = com.example.amplifierpro.service.AudioStreamService.currentReceiverIp;
            }
            if (ip != null && !ip.trim().isEmpty()) {
                manager.setActiveReceiverIp(ip.trim());
            }
            manager.setReceiverVolumeCaps(ip, max, current, seq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** REC_HW:<nativeSr>:<supportedCsv>:<model>:<halType> — kapabilitas hardware DAC & HAL receiver */
    private void handleReceiverHardwareCaps(String command, String senderIp) {
        try {
            String payload = command.substring(7); // skip "REC_HW:"
            String[] parts = payload.split(":");
            int nativeSr = Integer.parseInt(parts[0]);
            String supportedCsv = parts.length >= 2 ? parts[1] : "44100,48000";
            String model = parts.length >= 3 ? parts[2] : "Receiver";
            String halType = parts.length >= 4 ? parts[3] : "AudioTrack HAL";

            com.example.amplifierpro.manager.EqSettingsManager manager =
                    new com.example.amplifierpro.manager.EqSettingsManager(this);
            String ip = com.example.amplifierpro.service.AudioStreamService.currentReceiverIp;
            if (ip == null || ip.isEmpty()) ip = senderIp;
            if (ip == null || ip.isEmpty()) ip = manager.getActiveReceiverIp();
            if (ip != null && !ip.isEmpty() && manager.getActiveReceiverIp().isEmpty()) {
                manager.setActiveReceiverIp(ip);
            }
            manager.saveReceiverHwCaps(ip, nativeSr, supportedCsv, model, halType);
            Log.i(TAG, "Receiver HW detected: " + model + " (" + halType + "), Native=" + nativeSr + "Hz, Supported=" + supportedCsv);
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

        boolean webPlaying = DummyFragment.isWebCurrentlyPlaying();
        boolean webAvailable = DummyFragment.isWebPlaybackActive();
        boolean webVisible = DummyFragment.isWebTabVisible();
        boolean extPlaying = false;
        if (activeController != null) {
            PlaybackState ps = activeController.getPlaybackState();
            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                extPlaying = true;
                lastExternalPlayedTimestamp = System.currentTimeMillis();
                externalCurrentlyPlaying = true;
            }
        }

        Log.i("DEBUG_RECV", "attemptExecuteCommand: " + command +
                " | webPlaying=" + webPlaying + ", webAvailable=" + webAvailable + ", webVisible=" + webVisible +
                ", extPlaying=" + extPlaying + ", activeController=" + (activeController != null ? activeController.getPackageName() : "null"));

        if ("GET_META".equals(command)) {
            broadcastCurrentInfo(true);
            return;
        }

        // Priority 1: Web UI is actively playing video/music
        if (webPlaying && !extPlaying) {
            Log.i("DEBUG_RECV", "Routing command '" + command + "' to Web UI (Playing)");
            DummyFragment.executeRemoteCommand(this, command);
            return;
        }

        // Priority 2: External app is actively playing music (e.g. YouTube app, Spotify, YT Music)
        if (extPlaying && activeController != null) {
            Log.i("DEBUG_RECV", "Routing command '" + command + "' to External Controller: " + activeController.getPackageName());
            executeOnController(activeController, command);
            return;
        }

        // If BOTH report playing, route to the most recently started or visible
        if (webPlaying) {
            DummyFragment.executeRemoteCommand(this, command);
            return;
        }

        // Priority 3: Neither is actively playing (both paused/idle).
        // Route based on whether Web UI tab is currently open in foreground OR which source played most recently!
        long webLast = DummyFragment.getLastWebPlayedTimestamp();
        long extLast = lastExternalPlayedTimestamp;

        if (webAvailable && activeController != null) {
            if (webVisible || (webLast > 0 && webLast >= extLast)) {
                Log.i("DEBUG_RECV", "Routing command '" + command + "' to Web UI (Most Recent / Visible)");
                DummyFragment.executeRemoteCommand(this, command);
            } else {
                Log.i("DEBUG_RECV", "Routing command '" + command + "' to External Controller (Most Recent Paused): " + activeController.getPackageName());
                executeOnController(activeController, command);
            }
            return;
        }

        if (activeController != null) {
            Log.i("DEBUG_RECV", "Routing command '" + command + "' to External Controller (Paused/Idle): " + activeController.getPackageName());
            executeOnController(activeController, command);
            return;
        }

        if (webAvailable || webVisible) {
            Log.i("DEBUG_RECV", "Routing command '" + command + "' to Web UI (Loaded/Visible)");
            DummyFragment.executeRemoteCommand(this, command);
            return;
        }

        // Priority 5: Fallback if everything is null/empty
        if (allowRetry) {
            Log.w("DEBUG_RECV", "RETRY: No active controller/web for " + command + ", retrying in 200ms");
            updateHandler.postDelayed(() -> attemptExecuteCommand(command, false), 200);
        } else {
            Log.i("DEBUG_RECV", "Executing fallback to both Web UI and AudioManager for: " + command);
            DummyFragment.executeRemoteCommand(this, command);
            executeFallbackMediaKey(command);
        }
    }

    private void executeOnController(MediaController controller, String command) {
        if (controller == null) return;
        MediaController.TransportControls controls = controller.getTransportControls();
        PlaybackState state = controller.getPlaybackState();
        int currentState = (state != null) ? state.getState() : PlaybackState.STATE_NONE;
        long actions = (state != null) ? state.getActions() : 0L;
        Log.i("DEBUG_RECV", "SUCCESS: Executing " + command + " on " + controller.getPackageName() + " (state=" + currentState + ")");
        lastExternalPlayedTimestamp = System.currentTimeMillis();

        switch (command) {
            case "TOGGLE":
            case "PLAY_PAUSE":
                if (currentState == PlaybackState.STATE_PLAYING || currentState == PlaybackState.STATE_BUFFERING) {
                    try {
                        controls.pause();
                    } catch (Exception ignored) {}
                    if ((actions & PlaybackState.ACTION_PAUSE) == 0) {
                        sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PAUSE);
                    }
                    // Optimistic state broadcast so Receiver button updates immediately
                    externalCurrentlyPlaying = false;
                    broadcastOptimisticExternalState(0);
                } else {
                    try {
                        controls.play();
                    } catch (Exception ignored) {}
                    if ((actions & PlaybackState.ACTION_PLAY) == 0) {
                        sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PLAY);
                    }
                    externalCurrentlyPlaying = true;
                    broadcastOptimisticExternalState(1);
                }
                break;
            case "PLAY":
                try { controls.play(); } catch (Exception ignored) {}
                if ((actions & PlaybackState.ACTION_PLAY) == 0) {
                    sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PLAY);
                }
                externalCurrentlyPlaying = true;
                broadcastOptimisticExternalState(1);
                break;
            case "PAUSE":
                try { controls.pause(); } catch (Exception ignored) {}
                if ((actions & PlaybackState.ACTION_PAUSE) == 0) {
                    sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PAUSE);
                }
                externalCurrentlyPlaying = false;
                broadcastOptimisticExternalState(0);
                break;
            case "NEXT":
                try {
                    controls.skipToNext();
                } catch (Exception ignored) {}
                if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) == 0) {
                    sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_NEXT);
                }
                break;
            case "PREV":
                try {
                    controls.skipToPrevious();
                } catch (Exception ignored) {}
                if ((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) == 0) {
                    sendMediaKeyEvent(controller, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                }
                break;
        }

        // Schedule follow-up metadata broadcasts to confirm state & track change to Receiver
        updateHandler.postDelayed(() -> broadcastCurrentInfo(true), 150);
        updateHandler.postDelayed(() -> broadcastCurrentInfo(true), 500);
        updateHandler.postDelayed(() -> broadcastCurrentInfo(true), 1200);
    }

    private void broadcastOptimisticExternalState(int targetPlaying) {
        try {
            String t = (lastTitle != null && !lastTitle.isEmpty()) ? lastTitle : "WIFI STREAMING";
            String a = (lastArtist != null) ? lastArtist : "";
            long pos = 0;
            if (activeController != null && activeController.getPlaybackState() != null) {
                pos = activeController.getPlaybackState().getPosition();
            }
            long dur = Math.max(0, lastDuration);
            lastState = targetPlaying;
            String payload = t.toUpperCase() + "|" + a.toUpperCase() + "|" + pos + "|" + dur + "|" + targetPlaying;
            Intent intent = new Intent("com.example.amplifierpro.METADATA_UPDATED");
            intent.putExtra("info", payload);
            sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private void findActiveController() {
        try {
            ComponentName cn = new ComponentName(this, MetaListenerService.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
            String ownPkg = getPackageName();
            
            MediaController bestController = null;
            if (controllers != null && !controllers.isEmpty()) {
                // Priority 1: External App Currently Playing
                for (MediaController controller : controllers) {
                    if (ownPkg.equals(controller.getPackageName())) continue;
                    PlaybackState state = controller.getPlaybackState();
                    if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                        bestController = controller;
                        break;
                    }
                }
                
                // Priority 2: External App Buffering
                if (bestController == null) {
                    for (MediaController controller : controllers) {
                        if (ownPkg.equals(controller.getPackageName())) continue;
                        PlaybackState state = controller.getPlaybackState();
                        if (state != null && state.getState() == PlaybackState.STATE_BUFFERING) {
                            bestController = controller;
                            break;
                        }
                    }
                }
                
                // Priority 3: External App Paused with valid metadata
                if (bestController == null) {
                    for (MediaController controller : controllers) {
                        if (ownPkg.equals(controller.getPackageName())) continue;
                        PlaybackState state = controller.getPlaybackState();
                        if (state != null && state.getState() == PlaybackState.STATE_PAUSED && controller.getMetadata() != null) {
                            bestController = controller;
                            break;
                        }
                    }
                }

                // Priority 4: First external controller available
                if (bestController == null) {
                    for (MediaController controller : controllers) {
                        if (!ownPkg.equals(controller.getPackageName())) {
                            bestController = controller;
                            break;
                        }
                    }
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
                externalCurrentlyPlaying = playing;
                if (playing) {
                    lastExternalPlayedTimestamp = System.currentTimeMillis();
                }
                broadcastStatus(true, activeController.getPackageName(), playing);
            } else {
                externalCurrentlyPlaying = false;
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
        if (DummyFragment.isWebCurrentlyPlaying() || (activeController == null && DummyFragment.isWebPlaybackActive())) {
            Log.i(TAG, "Requesting Web UI to refresh and broadcast metadata");
            DummyFragment.requestMetaRefresh();
            return;
        }

        if (activeController == null) {
            if (force) {
                // If forced but no controller, send a "No Media" signal
                Intent intent = new Intent("com.example.amplifierpro.METADATA_UPDATED");
                intent.putExtra("info", "AMPX||0|0|0");
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
