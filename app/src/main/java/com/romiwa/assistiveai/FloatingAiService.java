package com.romiwa.assistiveai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public class FloatingAiService extends Service {
    public static final String PREFS = "assistive_ai_settings";
    public static final String KEY_ENDPOINT = "worker_endpoint";
    public static final String KEY_TOKEN = "worker_token";
    public static final String ACTION_START = "com.romiwa.assistiveai.START";
    public static final String ACTION_STOP = "com.romiwa.assistiveai.STOP";
    public static final String ACTION_CAPTURE = "com.romiwa.assistiveai.CAPTURE";
    public static final String ACTION_CAPTURE_CANCEL = "com.romiwa.assistiveai.CAPTURE_CANCEL";
    public static final String EXTRA_RESULT_CODE = "projection_result_code";
    public static final String EXTRA_PROJECTION_DATA = "projection_result_data";
    private static final String CHANNEL_ID = "assistive_ai_service";
    private static final int NOTIFICATION_ID = 7301;

    private WindowManager windowManager;
    private View bubbleView;
    private View panelView;
    private WindowManager.LayoutParams bubbleParams;
    private EditText promptInput;
    private Spinner providerSpinner;
    private TextView answerView;
    private TextView captureStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private boolean captureRequested = false;
    private boolean captureHandled = false;
    private String pendingPrompt = "Analisis layar ini dan jelaskan isi serta hal pentingnya.";
    private String pendingProvider = "gemini";

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        startForegroundSafely(false);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_START.equals(intent.getAction())) {
            startForegroundSafely(false);
            showBubble();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopProjection();
            stopForeground(true);
            stopSelf();
        } else if (ACTION_CAPTURE.equals(intent.getAction())) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data;
            if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent.class);
            else data = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
            startForegroundSafely(true);
            beginScreenCapture(resultCode, data);
        } else if (ACTION_CAPTURE_CANCEL.equals(intent.getAction())) {
            showBubble();
            showPanel();
            updateAnswer("Izin tangkap layar dibatalkan. Tidak ada layar yang dikirim.");
        }
        return START_STICKY;
    }

    private void startForegroundSafely(boolean forProjection) {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                if (forProjection) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                startForeground(NOTIFICATION_ID, notification, type);
            } else if (Build.VERSION.SDK_INT >= 29 && forProjection) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            // The service remains user-controlled; if Android rejects the foreground mode, stop safely.
            if (Build.VERSION.SDK_INT >= 26) {
                try { startForeground(NOTIFICATION_ID, notification); } catch (Exception ignored) { }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Assistive AI", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Menjaga tombol pintas AI tetap aktif saat digunakan");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Assistive AI aktif")
                    .setContentText("Tekan bola AI di layar untuk membuka asisten")
                    .setContentIntent(pending).setOngoing(true).build();
        }
        return new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Assistive AI aktif").setContentText("Tekan bola AI di layar")
                .setContentIntent(pending).setOngoing(true).build();
    }

    private void showBubble() {
        if (bubbleView != null || !android.provider.Settings.canDrawOverlays(this)) return;
        TextView bubble = new TextView(this);
        bubble.setText("AI");
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(17);
        bubble.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1BD4F4, 0xFF0873D6});
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(2), 0xAAFFFFFF);
        bubble.setBackground(bg);
        bubble.setElevation(dp(12));

        bubbleParams = new WindowManager.LayoutParams(dp(58), dp(58),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = prefs.getInt("bubble_x", dp(12));
        bubbleParams.y = prefs.getInt("bubble_y", dp(210));
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;
            private boolean moved;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleParams.x; startY = bubbleParams.y;
                        downX = event.getRawX(); downY = event.getRawY(); moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(event.getRawX() - downX);
                        int dy = (int)(event.getRawY() - downY);
                        if (Math.abs(dx) + Math.abs(dy) > dp(5)) moved = true;
                        bubbleParams.x = Math.max(0, startX + dx);
                        bubbleParams.y = Math.max(0, startY + dy);
                        try { windowManager.updateViewLayout(bubbleView, bubbleParams); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs.edit().putInt("bubble_x", bubbleParams.x).putInt("bubble_y", bubbleParams.y).apply();
                        if (!moved) showPanel();
                        return true;
                }
                return false;
            }
        });
        bubbleView = bubble;
        try { windowManager.addView(bubbleView, bubbleParams); }
        catch (Exception e) { bubbleView = null; }
    }

    private void showPanel() {
        if (panelView != null) return;
        if (bubbleView != null) { try { windowManager.removeView(bubbleView); } catch (Exception ignored) { } bubbleView = null; }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackground(round(0xFF111C2B, 22));
        panel.setElevation(dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label("Assistive AI", 18, 0xFFFFFFFF, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = smallButton("✕", 0xFF29394D);
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(38)));
        close.setOnClickListener(v -> closePanel());
        panel.addView(header, matchWrap());

        TextView caption = label("Gemini • DeepSeek  |  Asisten layar", 11, 0xFF91A5BE, false);
        LinearLayout.LayoutParams capP = matchWrap(); capP.topMargin = dp(3); capP.bottomMargin = dp(10);
        panel.addView(caption, capP);

        providerSpinner = new Spinner(this);
        String[] providers = {"Gemini", "DeepSeek"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providers);
        providerSpinner.setAdapter(adapter);
        providerSpinner.setSelection("deepseek".equals(prefs.getString("provider", "gemini")) ? 1 : 0);
        panel.addView(providerSpinner, new LinearLayout.LayoutParams(-1, dp(42)));

        answerView = label("Halo! Tanyakan sesuatu, atau tangkap layar untuk dianalisis.", 13, 0xFFE0E9F4, false);
        answerView.setPadding(dp(10), dp(10), dp(10), dp(10));
        answerView.setBackground(round(0xFF0A1421, 13));
        answerView.setTextIsSelectable(true);
        ScrollView answerScroll = new ScrollView(this);
        answerScroll.setFillViewport(false);
        answerScroll.addView(answerView);
        LinearLayout.LayoutParams answerP = matchWrap(); answerP.height = dp(150); answerP.topMargin = dp(9);
        panel.addView(answerScroll, answerP);

        promptInput = new EditText(this);
        promptInput.setTextColor(0xFFFFFFFF); promptInput.setHintTextColor(0xFF8293A9);
        promptInput.setHint("Tulis pertanyaanmu di sini..."); promptInput.setTextSize(14);
        promptInput.setGravity(Gravity.TOP | Gravity.START); promptInput.setMinLines(2); promptInput.setMaxLines(4);
        promptInput.setPadding(dp(11), dp(10), dp(11), dp(10));
        promptInput.setBackground(round(0xFF1C2A3D, 12));
        LinearLayout.LayoutParams inputP = matchWrap(); inputP.topMargin = dp(10);
        panel.addView(promptInput, inputP);

        captureStatus = label("Tangkap layar memerlukan izin Android.", 10, 0xFF8FA2B9, false);
        LinearLayout.LayoutParams csP = matchWrap(); csP.topMargin = dp(6);
        panel.addView(captureStatus, csP);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionP = matchWrap(); actionP.topMargin = dp(9);
        panel.addView(actions, actionP);
        Button capture = smallButton("Tangkap layar", 0xFF234461);
        Button send = smallButton("Kirim  ➤", 0xFF079FCB);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(46), 1); left.rightMargin = dp(6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(46), 1); right.leftMargin = dp(6);
        actions.addView(capture, left); actions.addView(send, right);
        capture.setOnClickListener(v -> startCaptureFlow());
        send.setOnClickListener(v -> {
            String prompt = promptInput.getText().toString().trim();
            if (prompt.isEmpty()) { updateAnswer("Tuliskan pertanyaan terlebih dahulu."); return; }
            String provider = providerSpinner.getSelectedItemPosition() == 1 ? "deepseek" : "gemini";
            prefs.edit().putString("provider", provider).apply();
            sendToAi(prompt, provider, null, null);
        });

        TextView foot = label("Bola AI dapat dipindahkan. Tutup panel untuk kembali ke aplikasi sebelumnya.", 10, 0xFF768BA4, false);
        LinearLayout.LayoutParams footP = matchWrap(); footP.topMargin = dp(8);
        panel.addView(foot, footP);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(20), dp(390)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.CENTER;
        p.dimAmount = 0.16f;
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        panelView = panel;
        try { windowManager.addView(panelView, p); }
        catch (Exception e) { panelView = null; showBubble(); }
    }

    private void startCaptureFlow() {
        if (promptInput != null && !promptInput.getText().toString().trim().isEmpty()) pendingPrompt = promptInput.getText().toString().trim();
        else pendingPrompt = "Analisis layar ini. Jelaskan apa yang sedang tampil, ringkas poin penting, dan bantu saya memahami atau menyelesaikannya.";
        pendingProvider = providerSpinner != null && providerSpinner.getSelectedItemPosition() == 1 ? "deepseek" : "gemini";
        prefs.edit().putString("pending_prompt", pendingPrompt).putString("provider", pendingProvider).apply();
        closePanel();
        removeBubble();
        Intent capture = new Intent(this, CapturePermissionActivity.class);
        capture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try { startActivity(capture); }
        catch (Exception e) { showBubble(); showPanel(); updateAnswer("Tidak dapat membuka izin tangkap layar Android."); }
    }

    private void beginScreenCapture(int resultCode, Intent data) {
        if (data == null || resultCode != android.app.Activity.RESULT_OK) {
            showBubble(); showPanel(); updateAnswer("Izin tangkap layar tidak tersedia."); return;
        }
        stopProjection();
        captureHandled = false;
        captureRequested = false;
        pendingPrompt = prefs.getString("pending_prompt", "Analisis layar ini dan jelaskan hal pentingnya.");
        pendingProvider = prefs.getString("provider", "gemini");
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, data);
            if (projection == null) throw new IllegalStateException("MediaProjection tidak tersedia");
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { mainHandler.post(() -> stopProjection()); }
            }, mainHandler);
            virtualDisplay = projection.createVirtualDisplay("AssistiveAI-Screen", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, mainHandler);
            imageReader.setOnImageAvailableListener(reader -> {
                if (captureRequested && !captureHandled) captureImage(reader);
            }, mainHandler);
            // The permission activity finishes immediately before this service starts. Give Android time
            // to return to the app the user was viewing before taking the one requested frame.
            mainHandler.postDelayed(() -> {
                captureRequested = true;
                if (imageReader != null && !captureHandled) {
                    Image latest = imageReader.acquireLatestImage();
                    if (latest != null) processImage(latest);
                }
            }, 1300);
        } catch (Exception e) {
            stopProjection(); showBubble(); showPanel();
            updateAnswer("Gagal mengambil layar: " + safeMessage(e));
        }
    }

    private void captureImage(ImageReader reader) {
        if (captureHandled) return;
        Image image = null;
        try { image = reader.acquireLatestImage(); }
        catch (Exception ignored) { }
        if (image != null) processImage(image);
    }

    private void processImage(Image image) {
        if (captureHandled) { image.close(); return; }
        captureHandled = true; captureRequested = false;
        final Bitmap bitmap;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            buffer.rewind();
            padded.copyPixelsFromBuffer(buffer);
            bitmap = Bitmap.createBitmap(padded, 0, 0, width, height);
            if (padded != bitmap) padded.recycle();
        } catch (Exception e) {
            image.close(); stopProjection(); showBubble(); showPanel();
            updateAnswer("Gagal memproses gambar layar: " + safeMessage(e)); return;
        } finally { image.close(); }

        networkExecutor.execute(() -> {
            String encoded = null;
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output);
                encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                output.close();
            } catch (Exception ignored) { }
            bitmap.recycle();
            final String image64 = encoded;
            mainHandler.post(() -> {
                stopProjection();
                showBubble();
                showPanel();
                if (image64 == null) updateAnswer("Gambar layar gagal disiapkan.");
                else sendToAi(pendingPrompt, pendingProvider, image64, "image/jpeg");
            });
        });
    }

    private void sendToAi(String prompt, String provider, String imageBase64, String mimeType) {
        String endpoint = prefs.getString(KEY_ENDPOINT, "").trim();
        String token = prefs.getString(KEY_TOKEN, "").trim();
        if (endpoint.isEmpty()) { updateAnswer("URL Worker belum diatur. Buka aplikasi Assistive AI dan simpan URL Worker."); return; }
        updateAnswer("Sedang menghubungi " + ("deepseek".equals(provider) ? "DeepSeek" : "Gemini") + "...");
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            String resultText;
            try {
                String url = endpoint.endsWith("/chat") ? endpoint : endpoint + "/chat";
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000); connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
                JSONObject body = new JSONObject();
                body.put("provider", provider); body.put("prompt", prompt);
                if (imageBase64 != null) { body.put("imageBase64", imageBase64); body.put("mimeType", mimeType == null ? "image/jpeg" : mimeType); }
                byte[] bytes = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
                int code = connection.getResponseCode();
                InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String response = readFully(input);
                JSONObject json = new JSONObject(response);
                if (code >= 200 && code < 300) resultText = json.optString("answer", "Server tidak mengirim jawaban.");
                else resultText = "Server error (" + code + "): " + json.optString("error", response);
            } catch (Exception e) {
                resultText = "Koneksi AI gagal: " + safeMessage(e) + "\nPeriksa internet, URL Worker, token akses, dan konfigurasi secret di Cloudflare.";
            } finally { if (connection != null) connection.disconnect(); }
            final String answer = resultText;
            mainHandler.post(() -> updateAnswer(answer));
        });
    }

    private String readFully(InputStream input) throws Exception {
        if (input == null) return "{}";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    private void updateAnswer(String text) {
        if (answerView != null) answerView.setText(text);
        if (captureStatus != null) captureStatus.setText("Siap membantu • hasil AI tampil di sini");
    }

    private void closePanel() {
        if (panelView != null) { try { windowManager.removeView(panelView); } catch (Exception ignored) { } panelView = null; }
        promptInput = null; providerSpinner = null; answerView = null; captureStatus = null;
        showBubble();
    }

    private void removeBubble() {
        if (bubbleView != null) { try { windowManager.removeView(bubbleView); } catch (Exception ignored) { } bubbleView = null; }
    }

    private void stopProjection() {
        try { if (imageReader != null) { imageReader.setOnImageAvailableListener(null, null); imageReader.close(); } } catch (Exception ignored) { }
        imageReader = null;
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) { }
        virtualDisplay = null;
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        projection = null;
        captureRequested = false;
    }

    private Button smallButton(String text, int color) {
        Button b = new Button(this); b.setText(text); b.setTextSize(12); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(0xFFFFFFFF); b.setPadding(dp(5), 0, dp(5), 0);
        b.setBackground(round(color, 12)); return b;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String safeMessage(Exception e) { String m = e.getMessage(); return m == null ? e.getClass().getSimpleName() : m; }

    @Override public void onDestroy() {
        closePanel(); removeBubble(); stopProjection();
        networkExecutor.shutdown();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
