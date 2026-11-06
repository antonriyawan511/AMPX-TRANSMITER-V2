package com.example.amplifierpro.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LicenseManager {

    private static final String TAG = "LicenseManager";
    private static final String PREF_NAME = "dsp_pro_license_pref";
    private static final String KEY_LICENSE_TOKEN = "key_license_token";
    private static final String KEY_CUSTOMER_NAME = "key_customer_name";
    private static final String KEY_FIRST_INSTALL_TIME = "key_first_install_time";
    private static final String KEY_ACTIVE_TIER = "key_active_tier";
    private static final String KEY_SERVER_TIME_AT_SYNC = "key_server_time_at_sync";
    private static final String KEY_ELAPSED_AT_SYNC = "key_elapsed_at_sync";
    private static final String KEY_TRIAL_START_TIME = "key_trial_start_time";
    private static final String KEY_STATUS_TIMESTAMP = "key_status_timestamp";
    private static final String KEY_LAST_KNOWN_SERVER_TIME = "key_last_known_server_time";
    private static final String KEY_LAST_SAVED_WALL_TIME = "key_last_saved_wall_time";
    private static final String KEY_LAST_ELAPSED_REALTIME = "key_last_elapsed_realtime";
    private static final String KEY_PRO_PRICE = "key_pro_price";
    private static final String KEY_FORMATTED_PRICE = "key_formatted_price";
    private static final String KEY_NORMAL_PRICE = "key_normal_price";
    private static final String KEY_FORMATTED_NORMAL_PRICE = "key_formatted_normal_price";
    private static final long DEFAULT_PRICE = 35000L;
    private static final String DEFAULT_FORMATTED_PRICE = "Rp 35.000";
    private static final long DEFAULT_NORMAL_PRICE = 50000L;
    private static final String DEFAULT_FORMATTED_NORMAL_PRICE = "Rp 50.000";

    // Server Endpoints
    private static final String SERVER_CHECK_URL = "https://antonimous.dsp-pro-license.workers.dev/api/check";
    private static final String SERVER_ORDER_URL = "https://antonimous.dsp-pro-license.workers.dev/api/order";

    // Public Key RSA-2048 (Aman berada di APK, hanya untuk verifikasi token)
    private static final String PUBLIC_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA08ZMblZV+HxINPzksWIqe4Je+X9NMAPvU0Vy" +
            "LgtfCCVPZ7ciXAktvD5FROVQqKo3Z7jPGLOVpTeZLtoTIeG24Tie9rpOL9/F/88DvYZdIbNQlLDSB7y8" +
            "OwRkoWQWMea9Enf2jF41gBKtC1tNvQi1bRb5KC8nM6rgJ5oy3jzFfFMX0B1G7s4Y+IBp74IRqBfqQns9" +
            "q4XxCZltmP9+TDJdqCzQKHF7B/e6wIR77fIqZHf7GbN/sxXsVVdEOqJkUPZYr8QUvTth5VEMgODbWt+r" +
            "PCTI7DqCpUEHs+lbqVzvUYfki0LgVKhO/6Kboow89KzEw7KPJRyGzbQTGH5+052ENQIDAQAB";

    // Masa Trial Normal: 3 Hari (Dapat di-reset atau di-expired-kan langsung secara real-time lewat server)
    public static final long TRIAL_DURATION_MS = 3 * 24 * 60 * 60 * 1000L;
    public static final long TRIAL_SESSION_DURATION_MS = 30 * 1000L; // Legacy fallback

    private static LicenseManager instance;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isPro = false;
    private String activeTier = "UNINITIALIZED"; // UNINITIALIZED, TRIAL, PENDING, PRO, BLOCKED, EXPIRED
    private long licenseExpiry = 0;
    private long sessionStartTime = 0;
    private boolean isStreamingActive = false;

    // Timekeeping (NTP Server Time + Monotonic Clock Pasca-Reboot)
    private long serverTimeAtSync = 0;
    private long elapsedRealtimeAtSync = 0;
    private long trialStartTime = 0;
    private long statusTimestamp = 0;
    private long sessionTimeAnchor = 0;
    private long sessionElapsedAnchor = 0;
    private boolean isClockSuspended = false;

    public enum Status {
        SUCCESS,
        INVALID_FORMAT,
        INVALID_SIGNATURE,
        DEVICE_MISMATCH,
        WRONG_APP_TYPE,
        EXPIRED
    }

    public interface LicenseCheckListener {
        void onCheckComplete(String status, boolean isPro, String message);
    }

    public interface OrderSubmitListener {
        void onSubmitComplete(boolean success, String message);
    }

    private LicenseManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = this.appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        this.serverTimeAtSync = prefs.getLong(KEY_SERVER_TIME_AT_SYNC, 0);
        this.elapsedRealtimeAtSync = prefs.getLong(KEY_ELAPSED_AT_SYNC, 0);
        this.trialStartTime = prefs.getLong(KEY_TRIAL_START_TIME, 0);
        this.statusTimestamp = prefs.getLong(KEY_STATUS_TIMESTAMP, 0);

        long lastSavedWall = prefs.getLong(KEY_LAST_SAVED_WALL_TIME, 0);
        long lastKnownServer = prefs.getLong(KEY_LAST_KNOWN_SERVER_TIME, 0);
        long wallNow = System.currentTimeMillis();
        long currentElapsed = SystemClock.elapsedRealtime();

        // Deteksi Rollback Jam HP (Hanya jika pernah menyimpan wall time / server time)
        if (lastSavedWall > 0 && (wallNow < lastSavedWall - 30000L || (lastKnownServer > 0 && wallNow < lastKnownServer - 30000L))) {
            this.isClockSuspended = true;
            Log.w(TAG, "PERINGATAN: Jam sistem HP terdeteksi dimundurkan (Time Rollback)!");
        } else {
            this.isClockSuspended = false;
        }

        // Simpan wall time & elapsed terbaru
        prefs.edit()
                .putLong(KEY_LAST_SAVED_WALL_TIME, Math.max(lastSavedWall, wallNow))
                .putLong(KEY_LAST_ELAPSED_REALTIME, currentElapsed)
                .apply();

        // Inisialisasi Anchor Sesi Monotonic Pasca-Reboot
        if (lastKnownServer > 0) {
            this.sessionTimeAnchor = lastKnownServer;
            this.sessionElapsedAnchor = currentElapsed;
        } else {
            this.sessionTimeAnchor = 0;
            this.sessionElapsedAnchor = currentElapsed;
        }

        loadAndVerifyStoredLicense();
    }

    public static synchronized LicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new LicenseManager(context);
        }
        return instance;
    }

    private void loadAndVerifyStoredLicense() {
        String token = prefs.getString(KEY_LICENSE_TOKEN, null);
        if (token != null && !token.isEmpty()) {
            Status status = verifyToken(token, false);
            if (status == Status.SUCCESS) {
                isPro = true;
                activeTier = "PRO";
                return;
            }
        }
        isPro = false;
        activeTier = prefs.getString(KEY_ACTIVE_TIER, "UNINITIALIZED");
        licenseExpiry = 0;
    }

    public synchronized Status activateLicense(String token) {
        Status status = verifyToken(token, true);
        if (status == Status.SUCCESS) {
            prefs.edit().putString(KEY_LICENSE_TOKEN, token.trim()).putString(KEY_ACTIVE_TIER, "PRO").apply();
            this.isPro = true;
            this.activeTier = "PRO";
        }
        return status;
    }

    public synchronized void revokeLicenseLocally(String reason) {
        String tier = reason != null ? reason : "UNINITIALIZED";
        prefs.edit().remove(KEY_LICENSE_TOKEN).putString(KEY_ACTIVE_TIER, tier).apply();
        this.isPro = false;
        this.activeTier = tier;
        this.licenseExpiry = 0;
    }

    private Status verifyToken(String token, boolean updateState) {
        if (token == null) return Status.INVALID_FORMAT;
        token = token.trim();
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Status.INVALID_FORMAT;
        }

        try {
            byte[] payloadBytes = Base64.decode(parts[0], Base64.DEFAULT);
            byte[] signatureBytes = Base64.decode(parts[1], Base64.DEFAULT);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);

            // Verifikasi Tanda Tangan Digital RSA-2048
            byte[] pubKeyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(pubKeyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(payload.getBytes(StandardCharsets.UTF_8));

            if (!sig.verify(signatureBytes)) {
                return Status.INVALID_SIGNATURE;
            }

            // Parse Payload: HWID|APP_TYPE|TIER|EXPIRY
            String[] fields = payload.split("\\|");
            if (fields.length < 4) {
                return Status.INVALID_FORMAT;
            }

            String targetHwid = fields[0];
            String appType = fields[1];
            String tier = fields[2];
            long expiry = Long.parseLong(fields[3]);

            String localHwid = DeviceSecurityManager.getDeviceId(appContext).replace("-", "");
            String cleanedTarget = targetHwid.replace("-", "");

            if (!cleanedTarget.equalsIgnoreCase(localHwid)) {
                return Status.DEVICE_MISMATCH;
            }

            if (!"ALL".equalsIgnoreCase(appType) && !"TX".equalsIgnoreCase(appType) && !"Transmitter".equalsIgnoreCase(appType)) {
                return Status.WRONG_APP_TYPE;
            }

            if (expiry > 0 && System.currentTimeMillis() > expiry) {
                return Status.EXPIRED;
            }

            if (updateState) {
                this.isPro = true;
                this.activeTier = tier;
                this.licenseExpiry = expiry;
            }
            return Status.SUCCESS;

        } catch (Exception e) {
            Log.e(TAG, "Error verifying token", e);
            return Status.INVALID_FORMAT;
        }
    }

    public synchronized boolean isProLicensed() {
        return isPro;
    }

    public synchronized boolean isPro() {
        return isPro;
    }

    public synchronized String getActiveTier() {
        return activeTier;
    }

    public synchronized boolean isBlocked() {
        return "BLOCKED".equalsIgnoreCase(activeTier) || "BLOCK".equalsIgnoreCase(activeTier);
    }

    public synchronized boolean isClockSuspended() {
        return isClockSuspended;
    }

    public synchronized void updateServerTimeSync(long serverTime) {
        if (serverTime > 0) {
            this.serverTimeAtSync = serverTime;
            this.elapsedRealtimeAtSync = SystemClock.elapsedRealtime();
            this.sessionTimeAnchor = serverTime;
            this.sessionElapsedAnchor = this.elapsedRealtimeAtSync;
            this.isClockSuspended = false; // Reset penangguhan saat server time valid diterima
            prefs.edit()
                    .putLong(KEY_SERVER_TIME_AT_SYNC, this.serverTimeAtSync)
                    .putLong(KEY_ELAPSED_AT_SYNC, this.elapsedRealtimeAtSync)
                    .putLong(KEY_LAST_KNOWN_SERVER_TIME, serverTime)
                    .putLong(KEY_LAST_SAVED_WALL_TIME, Math.max(prefs.getLong(KEY_LAST_SAVED_WALL_TIME, 0), System.currentTimeMillis()))
                    .putLong(KEY_LAST_ELAPSED_REALTIME, this.elapsedRealtimeAtSync)
                    .apply();
        }
    }

    public synchronized long getSyncedCurrentTime() {
        if (sessionTimeAnchor > 0) {
            long currentElapsed = SystemClock.elapsedRealtime();
            long elapsedDelta = currentElapsed - sessionElapsedAnchor;
            if (elapsedDelta < 0) elapsedDelta = 0;
            return sessionTimeAnchor + elapsedDelta;
        }
        return System.currentTimeMillis();
    }

    public synchronized void setTrialStartTime(long time) {
        this.trialStartTime = time;
        prefs.edit().putLong(KEY_TRIAL_START_TIME, time).apply();
    }

    public synchronized long getTrialStartTime() {
        if (trialStartTime <= 0) {
            trialStartTime = prefs.getLong(KEY_TRIAL_START_TIME, 0);
        }
        return trialStartTime;
    }

    public synchronized void setStatusTimestamp(long time) {
        this.statusTimestamp = time;
        prefs.edit().putLong(KEY_STATUS_TIMESTAMP, time).apply();
    }

    public synchronized long getStatusTimestamp() {
        if (statusTimestamp <= 0) {
            statusTimestamp = prefs.getLong(KEY_STATUS_TIMESTAMP, getSyncedCurrentTime());
        }
        return statusTimestamp;
    }

    public synchronized boolean isTrialActive() {
        if (isPro || isBlocked() || isClockSuspended) return false;
        if ("EXPIRED".equalsIgnoreCase(activeTier) || "FREE".equalsIgnoreCase(activeTier) || "UNINITIALIZED".equalsIgnoreCase(activeTier)) return false;
        long start = getTrialStartTime();
        if (start <= 0) return false;
        long now = getSyncedCurrentTime();
        return (now - start) < TRIAL_DURATION_MS;
    }

    public synchronized boolean isTrialExpired() {
        if (isPro || isBlocked()) return false;
        if ("EXPIRED".equalsIgnoreCase(activeTier) || "FREE".equalsIgnoreCase(activeTier)) return true;
        if ("UNINITIALIZED".equalsIgnoreCase(activeTier) || trialStartTime <= 0) return false;
        long start = getTrialStartTime();
        if (start <= 0) return false;
        long now = getSyncedCurrentTime();
        return (now - start) >= TRIAL_DURATION_MS;
    }

    public synchronized boolean isDspAllowed() {
        if (isBlocked() || isClockSuspended) return false;
        if (isPro) return true;
        return isTrialActive();
    }

    public synchronized void resetTrialTime() {
        long now = getSyncedCurrentTime();
        setTrialStartTime(now);
        setStatusTimestamp(now);
    }

    public synchronized long getTrialRemainingMillis() {
        if (isPro || isBlocked() || "EXPIRED".equalsIgnoreCase(activeTier) || "FREE".equalsIgnoreCase(activeTier)) return 0;
        long start = getTrialStartTime();
        long now = getSyncedCurrentTime();
        long remaining = (start + TRIAL_DURATION_MS) - now;
        return Math.max(0, remaining);
    }

    public synchronized long getStatusElapsedMillis() {
        long start = getStatusTimestamp();
        long now = getSyncedCurrentTime();
        long elapsed = now - start;
        return Math.max(0, elapsed);
    }

    public synchronized long getTrialRemainingSeconds() {
        long remainingMs = getTrialRemainingMillis();
        if (remainingMs <= 0) return 0;
        return Math.max(1, (remainingMs + 999) / 1000L);
    }

    public synchronized long getTrialRemainingDays() {
        long remainingSec = getTrialRemainingSeconds();
        if (remainingSec <= 0) return 0;
        return (remainingSec / 86400L) + 1;
    }

    public static String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0) {
            return String.format(java.util.Locale.US, "%dh %02dj %02dm %02ds", days, hours, minutes, seconds);
        } else {
            return String.format(java.util.Locale.US, "%02dj %02dm %02ds", hours, minutes, seconds);
        }
    }

    public void setCustomerName(String name) {
        if (name != null) {
            prefs.edit().putString(KEY_CUSTOMER_NAME, name.trim()).apply();
        }
    }

    public String getCustomerName() {
        return prefs.getString(KEY_CUSTOMER_NAME, "");
    }

    // Pengecekan Ketersediaan Internet (Wajib Online saat Trial)
    public boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network activeNet = cm.getActiveNetwork();
            if (activeNet == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            return false;
        }
    }

    // Hubungi API /api/check di Cloudflare Worker
    public void checkOnlineStatus(LicenseCheckListener listener) {
        executor.execute(() -> {
            String hwid = DeviceSecurityManager.getDeviceId(appContext);
            String serverStatus = "UNKNOWN";
            String serverMsg = "";
            boolean serverAllowed = false;

            try {
                URL url = new URL(SERVER_CHECK_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setDoOutput(true);

                JSONObject req = new JSONObject();
                req.put("hwid", hwid);
                req.put("app", "Transmitter");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(req.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    serverStatus = res.optString("status", "UNINITIALIZED");
                    serverAllowed = res.optBoolean("allowed", false);
                    serverMsg = res.optString("message", "");

                    long serverTime = res.optLong("server_time", 0);
                    if (serverTime > 0) {
                        updateServerTimeSync(serverTime);
                    }
                    long serverPrice = res.optLong("promo_price", res.optLong("price", DEFAULT_PRICE));
                    String formattedPrice = res.optString("formatted_promo_price", res.optString("formatted_price", DEFAULT_FORMATTED_PRICE));
                    long normalPrice = res.optLong("normal_price", DEFAULT_NORMAL_PRICE);
                    String formattedNormalPrice = res.optString("formatted_normal_price", DEFAULT_FORMATTED_NORMAL_PRICE);
                    prefs.edit()
                            .putLong(KEY_PRO_PRICE, serverPrice)
                            .putString(KEY_FORMATTED_PRICE, formattedPrice)
                            .putLong(KEY_NORMAL_PRICE, normalPrice)
                            .putString(KEY_FORMATTED_NORMAL_PRICE, formattedNormalPrice)
                            .apply();

                    long trialStartServer = res.optLong("trial_start_at", 0);
                    long activatedServer = res.optLong("activated_at", 0);
                    long createdServer = res.optLong("created_at", 0);
                    long expiredServer = res.optLong("expired_at", 0);
                    long blockedServer = res.optLong("blocked_at", 0);
                    String subStatus = res.optString("sub_status", "");

                    if ("PRO".equalsIgnoreCase(serverStatus)) {
                        String token = res.optString("token", null);
                        Status actStatus = Status.INVALID_FORMAT;
                        if (token != null && !token.isEmpty()) {
                            actStatus = activateLicense(token);
                        }
                        if (actStatus == Status.SUCCESS) {
                            Log.d(TAG, "Lisensi PRO berhasil diverifikasi secara kriptografis");
                            setStatusTimestamp(activatedServer > 0 ? activatedServer : getSyncedCurrentTime());
                        } else {
                            Log.e(TAG, "Status PRO ditolak: Token RSA dari server kosong atau tidak valid kriptografis (" + actStatus + ")");
                            revokeLicenseLocally("EXPIRED");
                            setStatusTimestamp(getSyncedCurrentTime());
                        }
                    } else if ("BLOCKED".equalsIgnoreCase(serverStatus)) {
                        revokeLicenseLocally("BLOCKED");
                        setStatusTimestamp(blockedServer > 0 ? blockedServer : getSyncedCurrentTime());
                    } else if ("PENDING".equalsIgnoreCase(serverStatus)) {
                        revokeLicenseLocally("PENDING");
                        setStatusTimestamp(createdServer > 0 ? createdServer : getSyncedCurrentTime());
                    } else if ("EXPIRED".equalsIgnoreCase(serverStatus) || "FREE".equalsIgnoreCase(serverStatus)) {
                        revokeLicenseLocally("EXPIRED");
                        setStatusTimestamp(expiredServer > 0 ? expiredServer : getSyncedCurrentTime());
                        setTrialStartTime(0);
                    } else if ("TRIAL".equalsIgnoreCase(serverStatus)) {
                        revokeLicenseLocally("TRIAL");
                        if ("TRIAL_RESET".equalsIgnoreCase(subStatus) || trialStartServer <= 0) {
                            long nowSync = getSyncedCurrentTime();
                            setTrialStartTime(nowSync);
                            setStatusTimestamp(nowSync);
                        } else {
                            // TRIAL_RESUME
                            setTrialStartTime(trialStartServer);
                            setStatusTimestamp(trialStartServer);
                        }
                    } else {
                        revokeLicenseLocally("UNINITIALIZED");
                        setStatusTimestamp(getSyncedCurrentTime());
                    }
                } else {
                    serverMsg = "Server merespons kode: " + responseCode;
                }
            } catch (Exception e) {
                Log.w(TAG, "Gagal menghubungi server lisensi: " + e.getMessage());
                serverMsg = "Tidak dapat tersambung ke server";
            }

            final String finalStatus = serverStatus;
            final String finalMsg = serverMsg;
            final boolean finalPro = isPro;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onCheckComplete(finalStatus, finalPro, finalMsg);
                }
            });
        });
    }

    // Kirim pesanan ke /api/order saat pembeli klik beli
    public void submitOrder(String customerName, OrderSubmitListener listener) {
        executor.execute(() -> {
            String hwid = DeviceSecurityManager.getDeviceId(appContext);
            boolean success = false;
            String message = "";

            try {
                URL url = new URL(SERVER_ORDER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setDoOutput(true);

                JSONObject req = new JSONObject();
                req.put("hwid", hwid);
                req.put("name", customerName);
                req.put("app", "Transmitter");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(req.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                success = (code == 200);
                if (success) {
                    synchronized (this) {
                        if (!isPro) this.activeTier = "PENDING";
                    }
                    message = "Pesanan tercatat di server (PENDING)";
                } else {
                    message = "Server mengembalikan kode: " + code;
                }
            } catch (Exception e) {
                Log.w(TAG, "Gagal mengirim pesanan: " + e.getMessage());
                message = "Gagal koneksi server: " + e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalMsg = message;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onSubmitComplete(finalSuccess, finalMsg);
                }
            });
        });
    }

    // Manajemen Sesi Streaming
    public synchronized void startSession() {
        sessionStartTime = SystemClock.elapsedRealtime();
        isStreamingActive = true;
    }

    public synchronized void stopSession() {
        isStreamingActive = false;
        sessionStartTime = 0;
    }

    // Cek apakah audio diizinkan mengalir
    public synchronized boolean isPlaybackAllowed() {
        // Hukuman Maksimal: Jika diblokir oleh administrator, streaming ditolak total!
        if (isBlocked()) {
            return false;
        }
        // Freemium: Audio streaming lossless mengalir bebas sepuasnya (Flat sound bypass DSP)
        return true;
    }

    public synchronized long getSessionRemainingMs() {
        if (isBlocked()) {
            return 0;
        }
        return -1; // Unlimited streaming
    }

    public synchronized String getFormattedPrice() {
        return prefs.getString(KEY_FORMATTED_PRICE, DEFAULT_FORMATTED_PRICE);
    }

    public synchronized String getFormattedNormalPrice() {
        return prefs.getString(KEY_FORMATTED_NORMAL_PRICE, DEFAULT_FORMATTED_NORMAL_PRICE);
    }

    public synchronized long getProPrice() {
        return prefs.getLong(KEY_PRO_PRICE, DEFAULT_PRICE);
    }

    public synchronized long getNormalPrice() {
        return prefs.getLong(KEY_NORMAL_PRICE, DEFAULT_NORMAL_PRICE);
    }
}
