package com.example.amplifierpro.ui;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.amplifierpro.R;
import com.example.amplifierpro.manager.ThemeManager;
import com.example.amplifierpro.security.DeviceSecurityManager;
import com.example.amplifierpro.security.LicenseManager;

public class LicenseDialog {

    private static final String SELLER_WHATSAPP_NUMBER = "6285167326085";

    public interface OnLicenseActivatedListener {
        void onActivated();
    }

    public static void show(Context context, OnLicenseActivatedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_license, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvBadge = view.findViewById(R.id.tvLicenseStatusBadge);
        TextView tvSub = view.findViewById(R.id.tvLicenseStatusSub);
        TextView tvDeviceId = view.findViewById(R.id.tvDeviceId);
        TextView tvLabelDeviceId = view.findViewById(R.id.tvLabelDeviceId);
        TextView tvLabelCustomerName = view.findViewById(R.id.tvLabelCustomerName);
        Button btnCopy = view.findViewById(R.id.btnCopyDeviceId);
        EditText etCustomerName = view.findViewById(R.id.etCustomerName);
        Button btnBuy = view.findViewById(R.id.btnBuyLicense);
        Button btnCheckStatus = view.findViewById(R.id.btnCheckStatus);
        EditText etLicenseKey = view.findViewById(R.id.etLicenseKey);
        Button btnActivate = view.findViewById(R.id.btnActivateLicense);
        View btnClose = view.findViewById(R.id.btnCloseLicenseDialog);
        View layoutPurchase = view.findViewById(R.id.layoutPurchaseSection);
        View layoutOffline = view.findViewById(R.id.layoutOfflineSection);

        // Apply theme accent color dynamically
        int accentColor = ThemeManager.getInstance(context).getCurrentTheme().accentColor;
        ColorStateList accentCsl = ColorStateList.valueOf(accentColor);
        if (tvLabelDeviceId != null) tvLabelDeviceId.setTextColor(accentColor);
        if (tvLabelCustomerName != null) tvLabelCustomerName.setTextColor(accentColor);
        double luminance = (0.299 * Color.red(accentColor) + 0.587 * Color.green(accentColor) + 0.114 * Color.blue(accentColor)) / 255.0;
        int contrastTextColor = luminance > 0.5 ? Color.parseColor("#0F172A") : Color.WHITE;
        if (btnBuy != null) {
            btnBuy.setBackgroundTintList(accentCsl);
            btnBuy.setTextColor(contrastTextColor);
        }
        TextView tvBadgeLifetime = view.findViewById(R.id.tvBadgeLifetime);
        if (tvBadgeLifetime != null) {
            tvBadgeLifetime.setTextColor(accentColor);
        }

        LicenseManager licenseManager = LicenseManager.getInstance(context);

        TextView tvOriginalPrice = view.findViewById(R.id.tvLicenseOriginalPrice);
        if (tvOriginalPrice != null) {
            tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tvOriginalPrice.setText(licenseManager.getFormattedNormalPrice());
        }

        TextView tvTableHeaderPro = view.findViewById(R.id.tvTableHeaderPro);
        if (tvTableHeaderPro != null) {
            tvTableHeaderPro.setTextColor(Color.WHITE);
        }

        TextView tvLicensePrice = view.findViewById(R.id.tvLicensePrice);
        if (tvLicensePrice != null) {
            tvLicensePrice.setText(licenseManager.getFormattedPrice());
        }

        String hwid = DeviceSecurityManager.getDeviceId(context);
        tvDeviceId.setText(hwid);

        // Muat nama tersimpan sebelumnya
        String savedName = licenseManager.getCustomerName();
        if (savedName != null && !savedName.isEmpty()) {
            etCustomerName.setText(savedName);
        }

        Handler tickerHandler = new Handler(Looper.getMainLooper());
        Runnable updateUiState = new Runnable() {
            @Override
            public void run() {
                tickerHandler.removeCallbacks(this);
                if (licenseManager.isProLicensed()) {
                    long elapsed = licenseManager.getStatusElapsedMillis();
                    tvBadge.setText("STATUS: PRO");
                    tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#22C55E")));
                    tvBadge.setTextColor(Color.parseColor("#0F172A"));
                    if (tvSub != null) tvSub.setText("AKTIF: " + LicenseManager.formatDuration(elapsed));
                    if (layoutPurchase != null) layoutPurchase.setVisibility(View.GONE);
                    if (layoutOffline != null) layoutOffline.setVisibility(View.GONE);
                } else {
                    if (layoutPurchase != null) layoutPurchase.setVisibility(View.VISIBLE);
                    if (layoutOffline != null) layoutOffline.setVisibility(View.VISIBLE);

                    String tier = licenseManager.getActiveTier();
                    if (licenseManager.isClockSuspended()) {
                        tvBadge.setText("STATUS: SUSPENDED");
                        tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                        tvBadge.setTextColor(Color.WHITE);
                        if (tvSub != null) tvSub.setText("JAM DIMUNDURKAN: Butuh Sync");
                    } else if ("UNINITIALIZED".equalsIgnoreCase(tier)) {
                        tvBadge.setText("STATUS: BELUM SYNC");
                        tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#64748B")));
                        tvBadge.setTextColor(Color.WHITE);
                        if (tvSub != null) tvSub.setText("Menunggu Koneksi Server");
                    } else if ("PENDING".equalsIgnoreCase(tier)) {
                        long elapsed = licenseManager.getStatusElapsedMillis();
                        tvBadge.setText("STATUS: PENDING");
                        tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B")));
                        tvBadge.setTextColor(Color.parseColor("#0F172A"));
                        if (tvSub != null) tvSub.setText("MENUNGGU: " + LicenseManager.formatDuration(elapsed));
                    } else if (licenseManager.isBlocked()) {
                        long elapsed = licenseManager.getStatusElapsedMillis();
                        tvBadge.setText("STATUS: BLOCKED");
                        tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                        tvBadge.setTextColor(Color.WHITE);
                        if (tvSub != null) tvSub.setText("DIBLOKIR: " + LicenseManager.formatDuration(elapsed));
                    } else if (licenseManager.isTrialActive()) {
                        long remainingMs = licenseManager.getTrialRemainingMillis();
                        tvBadge.setText("STATUS: TRIAL");
                        tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#38BDF8")));
                        tvBadge.setTextColor(Color.parseColor("#0F172A"));
                        if (tvSub != null) tvSub.setText("SISA: " + LicenseManager.formatDuration(remainingMs));
                    } else {
                        long elapsed = licenseManager.getStatusElapsedMillis();
                        tvBadge.setText("STATUS: EXPIRED");
                        tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#64748B")));
                        tvBadge.setTextColor(Color.WHITE);
                        if (tvSub != null) tvSub.setText("KEDALUWARSA: " + LicenseManager.formatDuration(elapsed));
                    }
                }

                tickerHandler.postDelayed(this, 1000);
            }
        };

        // Jalankan ticker awal dan pasang pembatalan saat dialog ditutup
        tickerHandler.post(updateUiState);
        dialog.setOnDismissListener(d -> tickerHandler.removeCallbacksAndMessages(null));

        // 1. Salin HWID
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Device ID", hwid);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "ID Perangkat berhasil disalin!", Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Tombol Pesan via WhatsApp
        btnBuy.setOnClickListener(v -> {
            String inputName = etCustomerName.getText().toString().trim();
            if (inputName.isEmpty()) {
                Toast.makeText(context, "Nama pembeli wajib diisi!", Toast.LENGTH_SHORT).show();
                etCustomerName.requestFocus();
                return;
            }

            licenseManager.setCustomerName(inputName);

            // Kirim ke server /api/order di background
            licenseManager.submitOrder(inputName, (success, msg) -> {
                updateUiState.run();
            });

            // Buka WhatsApp dengan template profesional
            try {
                String message = "Halo Mas Anton, saya ingin memesan Lisensi PRO AMPX PRO.\n\n"
                        + "• Nama: " + inputName + "\n"
                        + "• ID Perangkat: " + hwid + "\n"
                        + "• Paket: Lisensi PRO (" + licenseManager.getFormattedPrice() + ")\n\n"
                        + "Mohon info nomor rekening / QRIS untuk pembayaran. Terima kasih!";
                String url = "https://api.whatsapp.com/send?phone=" + SELLER_WHATSAPP_NUMBER + "&text=" + Uri.encode(message);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "Tidak dapat membuka WhatsApp", Toast.LENGTH_SHORT).show();
            }
        });

        // 3. Tombol Cek Status Lisensi (Debounced untuk mencegah request berulang)
        final long[] lastCheckClickTime = {0};
        Runnable performCheckStatus = () -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastCheckClickTime[0] < 2000) {
                return; // Debounce 2 detik
            }
            lastCheckClickTime[0] = now;

            if (btnCheckStatus != null) btnCheckStatus.setEnabled(false);

            Toast.makeText(context, "Memeriksa status ke server...", Toast.LENGTH_SHORT).show();
            licenseManager.checkOnlineStatus((status, isPro, msg) -> {
                if (btnCheckStatus != null) btnCheckStatus.setEnabled(true);

                if (tvLicensePrice != null) {
                    tvLicensePrice.setText(licenseManager.getFormattedPrice());
                }
                if (tvOriginalPrice != null) {
                    tvOriginalPrice.setText(licenseManager.getFormattedNormalPrice());
                }
                updateUiState.run();
                if (listener != null) listener.onActivated();
                if (isPro) {
                    Toast.makeText(context, "🎉 Selamat! Lisensi PRO Sudah Aktif!", Toast.LENGTH_LONG).show();
                    view.postDelayed(dialog::dismiss, 1200);
                } else if ("PENDING".equalsIgnoreCase(status)) {
                    Toast.makeText(context, "⏳ Pesanan Masih PENDING. Silakan transfer via WA.", Toast.LENGTH_LONG).show();
                } else if ("BLOCKED".equalsIgnoreCase(status)) {
                    Toast.makeText(context, "❌ Perangkat ini diblokir admin.", Toast.LENGTH_LONG).show();
                } else if ("EXPIRED".equalsIgnoreCase(status)) {
                    Toast.makeText(context, "Masa Trial Telah Habis (Free Tier)", Toast.LENGTH_LONG).show();
                } else if ("TRIAL".equalsIgnoreCase(status)) {
                    Toast.makeText(context, "⏱️ Status Server: Masa TRIAL Aktif", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Status: Free Tier (Streaming Gratis)", Toast.LENGTH_SHORT).show();
                }
            });
        };

        if (btnCheckStatus != null) {
            btnCheckStatus.setOnClickListener(v -> performCheckStatus.run());
        }

        // 4. Fallback Kode Manual Offline
        btnActivate.setOnClickListener(v -> {
            String inputKey = etLicenseKey.getText().toString().trim();
            if (inputKey.isEmpty()) {
                Toast.makeText(context, "Masukkan kode lisensi offline terlebih dahulu!", Toast.LENGTH_SHORT).show();
                return;
            }

            LicenseManager.Status status = licenseManager.activateLicense(inputKey);
            switch (status) {
                case SUCCESS:
                    Toast.makeText(context, "🎉 Selamat! Lisensi PRO Berhasil Diaktifkan!", Toast.LENGTH_LONG).show();
                    updateUiState.run();
                    if (listener != null) listener.onActivated();
                    view.postDelayed(dialog::dismiss, 1500);
                    break;
                case DEVICE_MISMATCH:
                    Toast.makeText(context, "❌ Kode lisensi tidak cocok dengan ID Perangkat ini!", Toast.LENGTH_LONG).show();
                    break;
                case EXPIRED:
                    Toast.makeText(context, "❌ Kode lisensi sudah kedaluwarsa!", Toast.LENGTH_LONG).show();
                    break;
                case WRONG_APP_TYPE:
                    Toast.makeText(context, "❌ Kode lisensi bukan untuk AMPX PRO!", Toast.LENGTH_LONG).show();
                    break;
                default:
                    Toast.makeText(context, "❌ Kode lisensi tidak valid atau rusak!", Toast.LENGTH_LONG).show();
                    break;
            }
        });


        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}