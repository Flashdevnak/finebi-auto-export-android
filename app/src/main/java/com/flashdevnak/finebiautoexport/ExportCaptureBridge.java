package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public final class ExportCaptureBridge {
    public interface Listener {
        void onTemplateCaptured();
        void onTemplateCaptureError(String message);
    }

    private final Activity activity;
    private final Listener listener;

    public ExportCaptureBridge(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    @JavascriptInterface
    public void captureExport(String url, String body) {
        try {
            TemplateStore.saveCaptured(activity, url, body);
            activity.runOnUiThread(() -> {
                Toast.makeText(
                        activity,
                        "เรียนรู้ Export template สำเร็จ • Auto Export พร้อม",
                        Toast.LENGTH_LONG
                ).show();
                if (listener != null) listener.onTemplateCaptured();
            });
        } catch (Exception e) {
            activity.runOnUiThread(() -> {
                if (listener != null) {
                    listener.onTemplateCaptureError(e.getMessage());
                }
            });
        }
    }
}
