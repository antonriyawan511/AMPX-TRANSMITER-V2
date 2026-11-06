package com.example.amplifierpro.security;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class DeviceSecurityManager {

    private static String cachedHwid = null;

    public static synchronized String getDeviceId(Context context) {
        if (cachedHwid != null) {
            return cachedHwid;
        }

        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            if (androidId == null || androidId.isEmpty()) {
                androidId = "GENERIC_ANDROID_ID";
            }

            String hardwareRaw = androidId + "|" +
                    Build.BOARD + "|" +
                    Build.BRAND + "|" +
                    Build.HARDWARE + "|" +
                    Build.MODEL + "|" +
                    Build.MANUFACTURER;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hardwareRaw.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02X", hash[i]));
            }

            String hex = sb.toString();
            cachedHwid = hex.substring(0, 4) + "-" +
                    hex.substring(4, 8) + "-" +
                    hex.substring(8, 12) + "-" +
                    hex.substring(12, 16);

            return cachedHwid;
        } catch (Exception e) {
            return "4A8F-9C12-E03B-56DA";
        }
    }
}
