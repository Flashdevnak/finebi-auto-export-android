package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Dedicated FineBI browser.
 *
 * Important: this Activity intentionally contains NO Status/Files navigation.
 * DailyMainActivity is the single owner of the app dashboard. This prevents
 * Android task/back-stack returns from ever revealing the retired legacy UI.
 */
public final class MainActivity extends Activity {
    private WebView webView;
    private TextView sessionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(UiKit.NAVY);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        buildBrowserUi();
        webView.loadUrl(FineBiConfig.ENTRY_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSessionStatus();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("FineBIExportCapture");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void buildBrowserUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        root.addView(buildAppBar());
        root.addView(buildToolbar());

        View divider = new View(this);
        divider.setBackgroundColor(UiKit.BORDER);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        webView = WebViewFactory.create(
                this,
                new FineBiWebViewClient.Listener() {
                    @Override
                    public void onPageStarted(String url) {
                        Prefs.setStatus(MainActivity.this, "WEBVIEW", "กำลังเปิด FineBI");
                        runOnUiThread(() -> {
                            sessionStatus.setText("กำลังโหลด...");
                            sessionStatus.setTextColor(UiKit.MUTED);
                        });
                    }

                    @Override
                    public void onPageFinished(String url) {
                        runOnUiThread(() -> {
                            refreshSessionStatus();
                            installExportCaptureHook();
                        });
                    }

                    @Override
                    public void onSessionCaptured() {
                        runOnUiThread(() -> {
                            sessionStatus.setText("Session พร้อม");
                            sessionStatus.setTextColor(UiKit.GREEN);
                            Prefs.setStatus(MainActivity.this, "RUNNING", "FineBI session พร้อม");
                            if (Prefs.get(MainActivity.this).getBoolean(Prefs.ENABLED, false)) {
                                startAutoExportService();
                            }
                        });
                    }

                    @Override
                    public void onMainFrameError(String description) {
                        runOnUiThread(() -> {
                            sessionStatus.setText("เข้า FineBI ไม่ได้");
                            sessionStatus.setTextColor(UiKit.RED);
                            Toast.makeText(
                                    MainActivity.this,
                                    "FineBI เข้าไม่ได้ — ตรวจ Flashlink",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
        );

        webView.addJavascriptInterface(
                new ExportCaptureBridge(this, new ExportCaptureBridge.Listener() {
                    @Override
                    public void onTemplateCaptured() {
                        Prefs.setStatus(MainActivity.this, "RUNNING", "Export template พร้อม");
                        runOnUiThread(() -> {
                            refreshSessionStatus();
                            Toast.makeText(
                                    MainActivity.this,
                                    "บันทึก Export Template แล้ว",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                        if (Prefs.get(MainActivity.this).getBoolean(Prefs.ENABLED, false)) {
                            startAutoExportService();
                        }
                    }

                    @Override
                    public void onTemplateCaptureError(String message) {
                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                "จับ Export template ไม่สำเร็จ: " + message,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }),
                "FineBIExportCapture"
        );

        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private View buildAppBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.setBackgroundColor(UiKit.NAVY);

        TextView back = UiKit.text(this, "‹  สถานะ", 13, Color.WHITE, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(10), dp(8), dp(10), dp(8));
        back.setBackground(UiKit.rounded(Color.argb(45, 255, 255, 255), 10, this));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(UiKit.text(this, "FineBI", 16, Color.WHITE, true));
        labels.addView(UiKit.text(
                this,
                "HUB Departure Monitor",
                10,
                Color.rgb(191, 201, 216),
                false
        ));
        bar.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView badge = UiKit.text(this, "SELECT ALL", 9, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(5), dp(8), dp(5));
        badge.setBackground(UiKit.rounded(UiKit.BLUE, 20, this));
        bar.addView(badge);
        return bar;
    }

    private View buildToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(8), dp(12), dp(8));
        toolbar.setBackgroundColor(Color.WHITE);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.addView(UiKit.text(this, "FineBI Browser", 13, UiKit.TEXT, true));
        sessionStatus = UiKit.text(this, "รอ Session", 10, UiKit.MUTED, false);
        sessionStatus.setPadding(0, dp(2), 0, 0);
        status.addView(sessionStatus);
        toolbar.addView(status, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView reload = compactButton("Reload");
        reload.setOnClickListener(v -> {
            if (webView != null) webView.loadUrl(FineBiConfig.ENTRY_URL);
        });
        toolbar.addView(reload);

        TextView flashlink = compactButton("Flashlink");
        flashlink.setOnClickListener(v -> {
            if (!FlashlinkHelper.open(this)) {
                Toast.makeText(this, "เปิด Flashlink ไม่สำเร็จ", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fp.leftMargin = dp(7);
        toolbar.addView(flashlink, fp);
        return toolbar;
    }

    private TextView compactButton(String label) {
        TextView b = UiKit.text(this, label, 11, UiKit.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(7), dp(10), dp(7));
        b.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 9, this));
        b.setClickable(true);
        return b;
    }

    private void refreshSessionStatus() {
        if (sessionStatus == null) return;
        if (SessionStore.isReady()) {
            sessionStatus.setText(TemplateStore.isReady(this)
                    ? "Session + Template พร้อม"
                    : "Session พร้อม • รอ Export Template");
            sessionStatus.setTextColor(UiKit.GREEN);
        } else {
            sessionStatus.setText("รอ Login / Session");
            sessionStatus.setTextColor(UiKit.AMBER);
        }
    }

    private void installExportCaptureHook() {
        if (webView == null) return;
        String js = "(function(){" +
                "if(window.__finebiExportCaptureInstalled)return;" +
                "window.__finebiExportCaptureInstalled=true;" +
                "function cap(u,b){try{" +
                "u=new URL(u,location.href).href;" +
                "if(u.indexOf('/export/excel')>=0&&typeof b==='string'&&b.length>1000){" +
                "FineBIExportCapture.captureExport(u,b);}}catch(e){}}" +
                "var of=window.fetch;if(of){window.fetch=function(i,n){" +
                "try{var u=(typeof i==='string')?i:(i&&i.url)||'';cap(u,n&&n.body);}catch(e){}" +
                "return of.apply(this,arguments);};}" +
                "var oo=XMLHttpRequest.prototype.open;" +
                "var os=XMLHttpRequest.prototype.send;" +
                "XMLHttpRequest.prototype.open=function(m,u){this.__finebiU=u;return oo.apply(this,arguments);};" +
                "XMLHttpRequest.prototype.send=function(b){try{cap(this.__finebiU||'',b);}catch(e){}return os.apply(this,arguments);};" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void startAutoExportService() {
        Intent i = new Intent(this, AutoExportService.class)
                .setAction(AutoExportService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private int dp(int value) {
        return UiKit.dp(this, value);
    }
}
