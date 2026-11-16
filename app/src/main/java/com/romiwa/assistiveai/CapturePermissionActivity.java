package com.romiwa.assistiveai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/** Requests Android's own screen-capture consent. The app cannot capture until the user approves. */
public class CapturePermissionActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4402;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "Fitur tangkap layar tidak tersedia di perangkat ini", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka izin tangkap layar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;
        Intent service = new Intent(this, FloatingAiService.class);
        if (resultCode == RESULT_OK && data != null) {
            service.setAction(FloatingAiService.ACTION_CAPTURE);
            service.putExtra(FloatingAiService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(FloatingAiService.EXTRA_PROJECTION_DATA, data);
        } else {
            service.setAction(FloatingAiService.ACTION_CAPTURE_CANCEL);
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        } catch (Exception ignored) { }
        finish();
        overridePendingTransition(0, 0);
    }
}
