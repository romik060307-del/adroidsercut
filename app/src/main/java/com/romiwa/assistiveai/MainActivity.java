package com.romiwa.assistiveai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 91;
    private EditText endpointInput;
    private EditText tokenInput;
    private TextView statusText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(FloatingAiService.PREFS, MODE_PRIVATE);
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setBackgroundColor(Color.rgb(9, 17, 31));
        scroll.addView(root);

        TextView logo = new TextView(this);
        logo.setText("✦");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(32);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF13C9EE, 0xFF0873D6});
        logoBg.setShape(GradientDrawable.OVAL);
        logo.setBackground(logoBg);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.bottomMargin = dp(14);
        root.addView(logo, logoParams);

        TextView title = text("Assistive AI", 27, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());
        TextView subtitle = text("Asisten AI melayang untuk semua aplikasi", 14, 0xFF9BAAC0, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.bottomMargin = dp(24);
        root.addView(subtitle, subParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(18), dp(17), dp(18));
        card.setBackground(round(0xFF121F31, 20));
        root.addView(card, matchWrap());

        TextView section = text("Koneksi AI", 18, Color.WHITE, true);
        card.addView(section, matchWrap());
        TextView help = text("Masukkan alamat Cloudflare Worker milikmu. API key Gemini dan DeepSeek disimpan di server, bukan di APK.", 13, 0xFF9BAAC0, false);
        LinearLayout.LayoutParams helpP = matchWrap(); helpP.topMargin = dp(7); helpP.bottomMargin = dp(16);
        card.addView(help, helpP);

        card.addView(fieldLabel("URL Worker"), matchWrap());
        endpointInput = input("https://nama-worker.workers.dev", false);
        card.addView(endpointInput, fieldParams());
        card.addView(fieldLabel("Token akses Worker"), matchWrap());
        tokenInput = input("Token akses yang kamu buat sendiri", true);
        card.addView(tokenInput, fieldParams());

        String savedEndpoint = prefs.getString(FloatingAiService.KEY_ENDPOINT, "");
        String savedToken = prefs.getString(FloatingAiService.KEY_TOKEN, "");
        endpointInput.setText(savedEndpoint);
        tokenInput.setText(savedToken);

        Button save = button("Simpan koneksi", 0xFF1E344C);
        LinearLayout.LayoutParams saveP = matchWrap(); saveP.topMargin = dp(4);
        card.addView(save, saveP);
        save.setOnClickListener(v -> saveSettings());

        statusText = text("Status: tombol pintas belum aktif", 13, 0xFF9BAAC0, false);
        LinearLayout.LayoutParams statusP = matchWrap(); statusP.topMargin = dp(19); statusP.bottomMargin = dp(10);
        root.addView(statusText, statusP);

        Button enable = button("AKTIFKAN ASSISTIVE TOUCH", 0xFF0AA6CE);
        LinearLayout.LayoutParams enableP = matchWrap(); enableP.height = dp(54); enableP.bottomMargin = dp(10);
        root.addView(enable, enableP);
        enable.setOnClickListener(v -> enableBubble());

        Button disable = button("Matikan tombol melayang", 0xFF243347);
        LinearLayout.LayoutParams disableP = matchWrap(); disableP.height = dp(48);
        root.addView(disable, disableP);
        disable.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingAiService.class).setAction(FloatingAiService.ACTION_STOP));
            statusText.setText("Status: tombol pintas dimatikan");
        });

        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(15), dp(14), dp(15), dp(14));
        note.setBackground(round(0xFF101B2B, 16));
        LinearLayout.LayoutParams noteP = matchWrap(); noteP.topMargin = dp(20);
        root.addView(note, noteP);
        note.addView(text("Cara menggunakan", 15, Color.WHITE, true));
        note.addView(text("1. Deploy Worker dan isi URL serta tokennya.\n2. Simpan koneksi, lalu aktifkan tombol melayang.\n3. Beri izin tampil di atas aplikasi lain.\n4. Buka aplikasi apa pun, tekan bola AI, lalu kirim pertanyaan.\n5. Untuk analisis layar, tekan Tangkap layar dan setujui dialog Android.", 13, 0xFFB8C5D6, false), matchWrap());

        TextView privacy = text("Privasi: tangkapan layar hanya dibuat setelah kamu menekan tombol dan menyetujui izin Android. Jangan kirim layar yang berisi password, OTP, atau data pribadi.", 12, 0xFF8393A8, false);
        LinearLayout.LayoutParams privacyP = matchWrap(); privacyP.topMargin = dp(15);
        root.addView(privacy, privacyP);
        setContentView(scroll);
        updateStatus();
    }

    private void enableBubble() {
        saveSettings();
        if (prefs.getString(FloatingAiService.KEY_ENDPOINT, "").isEmpty()) {
            Toast.makeText(this, "Isi URL Worker terlebih dahulu", Toast.LENGTH_LONG).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Izinkan Assistive AI tampil di atas aplikasi lain, lalu tekan Aktifkan lagi", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        Intent service = new Intent(this, FloatingAiService.class).setAction(FloatingAiService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        statusText.setText("Status: Assistive AI sedang aktif");
        Toast.makeText(this, "Tombol AI sudah diaktifkan", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            if (Settings.canDrawOverlays(this)) {
                Intent service = new Intent(this, FloatingAiService.class).setAction(FloatingAiService.ACTION_START);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
                statusText.setText("Status: Assistive AI sedang aktif");
            } else {
                Toast.makeText(this, "Izin tampil di atas aplikasi lain belum diberikan", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveSettings() {
        if (endpointInput == null || tokenInput == null) return;
        String endpoint = endpointInput.getText().toString().trim();
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        if (!endpoint.isEmpty() && !endpoint.startsWith("https://")) {
            Toast.makeText(this, "URL Worker harus menggunakan https://", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString(FloatingAiService.KEY_ENDPOINT, endpoint)
                .putString(FloatingAiService.KEY_TOKEN, tokenInput.getText().toString().trim()).apply();
        Toast.makeText(this, "Pengaturan koneksi disimpan", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (statusText != null && Settings.canDrawOverlays(this)) {
            statusText.setText("Status: izin tombol melayang tersedia");
        }
    }

    @Override protected void onResume() { super.onResume(); updateStatus(); }

    private TextView fieldLabel(String value) {
        TextView t = text(value, 13, 0xFFDCE7F4, true);
        LinearLayout.LayoutParams p = matchWrap(); p.bottomMargin = dp(7);
        t.setLayoutParams(p); return t;
    }
    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setTextSize(14); e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF7E8DA3);
        e.setHint(hint); e.setPadding(dp(13), 0, dp(13), 0);
        e.setBackground(round(0xFF0B1523, 12));
        if (password) e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        return e;
    }
    private Button button(String label, int color) {
        Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(color, 14)); return b;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams fieldParams() { LinearLayout.LayoutParams p = matchWrap(); p.height = dp(48); p.bottomMargin = dp(14); return p; }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
