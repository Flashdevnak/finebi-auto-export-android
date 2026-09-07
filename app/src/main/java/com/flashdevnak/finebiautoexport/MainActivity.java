package com.flashdevnak.finebiautoexport;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout content;
    private LinearLayout statusPage;
    private LinearLayout fineBiPage;
    private LinearLayout historyPage;
    private TextView statusText;
    private TextView sessionText;
    private TextView flashlinkText;
    private TextView templateText;
    private WebView webView;
    private ListView historyList;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStatus();
            ui.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermissionIfNeeded();
        buildUi();
        showPage(statusPage);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(refreshRunnable);
        ui.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("FineBI Auto Export • Android");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), dp(10), dp(16), dp(10));
        title.setBackgroundColor(Color.rgb(28, 39, 54));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));

        Button statusBtn = navButton("สถานะ");
        Button fineBiBtn = navButton("FineBI");
        Button filesBtn = navButton("ไฟล์");

        nav.addView(statusBtn, weight());
        nav.addView(fineBiBtn, weight());
        nav.addView(filesBtn, weight());
        root.addView(nav);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        statusPage = buildStatusPage();
        fineBiPage = buildFineBiPage();
        historyPage = buildHistoryPage();

        content.addView(statusPage);
        content.addView(fineBiPage);
        content.addView(historyPage);

        statusBtn.setOnClickListener(v -> showPage(statusPage));
        fineBiBtn.setOnClickListener(v -> {
            showPage(fineBiPage);
            if (webView.getUrl() == null || "about:blank".equals(webView.getUrl())) {
                webView.loadUrl(FineBiConfig.ENTRY_URL);
            }
        });
        filesBtn.setOnClickListener(v -> {
            refreshHistory();
            showPage(historyPage);
        });

        setContentView(root);
    }

    private LinearLayout buildStatusPage() {
        LinearLayout body = vertical();
        body.setPadding(dp(14), dp(12), dp(14), dp(20));

        TextView headline = heading("HUB Departure Monitor");
        body.addView(headline);

        flashlinkText = statusCard("Flashlink");
        body.addView(flashlinkText);

        sessionText = statusCard("FineBI Session");
        body.addView(sessionText);

        templateText = statusCard("Export Template");
        body.addView(templateText);

        statusText = statusCard("Auto Export");
        body.addView(statusText);

        TextView all = statusCard("HUB Filter");
        all.setText("HUB Filter\n✓ FORCE SELECT ALL ก่อน Export ทุกครั้ง\nถ้าหา filter ไม่เจอ ระบบจะ BLOCK ไฟล์");
        body.addView(all);

        Button start = actionButton("▶ เริ่ม Auto Export");
        start.setOnClickListener(v -> startService());
        body.addView(start);

        Button stop = actionButton("■ หยุด Auto Export");
        stop.setOnClickListener(v -> stopServiceAction());
        body.addView(stop);

        Button flash = actionButton("เปิด Flashlink");
        flash.setOnClickListener(v -> {
            if (!FlashlinkHelper.open(this)) {
                Toast.makeText(this, "เปิด Flashlink ไม่สำเร็จ", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(flash);

        Button finebi = actionButton("เปิด FineBI ในแอป");
        finebi.setOnClickListener(v -> {
            showPage(fineBiPage);
            webView.loadUrl(FineBiConfig.ENTRY_URL);
        });
        body.addView(finebi);

        Button battery = actionButton("ตั้งค่า Battery = Unrestricted");
        battery.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        });
        body.addView(battery);

        TextView note = new TextView(this);
        note.setText(
                "ครั้งแรก: เปิด FineBI ในแอป → Login → กด Export Excel ตามปกติ 1 ครั้ง เพื่อให้แอปเรียนรู้ request\n" +
                "แอปจะลบ sessionId ก่อนเก็บ template และไม่เก็บ Authorization/Cookie ลงไฟล์\n" +
                "จากนั้น Auto Export จะทำงานเอง • ไฟล์: Downloads/FineBI_Auto_Export/YYYY-MM-DD/"
        );
        note.setTextSize(13);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(dp(4), dp(14), dp(4), dp(4));
        body.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        LinearLayout container = vertical();
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        return container;
    }

    private LinearLayout buildFineBiPage() {
        LinearLayout page = vertical();

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(6), dp(6), dp(6), dp(6));

        Button reload = navButton("Reload");
        Button flash = navButton("Flashlink");
        tools.addView(reload, weight());
        tools.addView(flash, weight());
        page.addView(tools);

        webView = WebViewFactory.create(
                this,
                new FineBiWebViewClient.Listener() {
                    @Override public void onPageStarted(String url) {
                        Prefs.setStatus(MainActivity.this, "WEBVIEW", "กำลังเปิด FineBI");
                    }

                    @Override public void onPageFinished(String url) {
                        installExportCaptureHook();
                    }

                    @Override public void onSessionCaptured() {
                        runOnUiThread(() -> {
                            Prefs.setStatus(MainActivity.this, "RUNNING", "FineBI session พร้อม");
                            startService();
                        });
                    }

                    @Override public void onMainFrameError(String description) {
                        runOnUiThread(() ->
                                Toast.makeText(
                                        MainActivity.this,
                                        "FineBI เข้าไม่ได้ — ตรวจ Flashlink",
                                        Toast.LENGTH_SHORT
                                ).show()
                        );
                    }
                }
        );
        webView.addJavascriptInterface(
                new ExportCaptureBridge(this, new ExportCaptureBridge.Listener() {
                    @Override public void onTemplateCaptured() {
                        Prefs.setStatus(MainActivity.this, "RUNNING", "Export template พร้อม");
                        startService();
                        refreshStatus();
                    }

                    @Override public void onTemplateCaptureError(String message) {
                        Toast.makeText(
                                MainActivity.this,
                                "จับ Export template ไม่สำเร็จ: " + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }),
                "FineBIExportCapture"
        );
        page.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        reload.setOnClickListener(v -> webView.loadUrl(FineBiConfig.ENTRY_URL));
        flash.setOnClickListener(v -> FlashlinkHelper.open(this));

        return page;
    }

    private LinearLayout buildHistoryPage() {
        LinearLayout page = vertical();
        page.setPadding(dp(10), dp(8), dp(10), dp(8));

        Button refresh = actionButton("รีเฟรชรายการไฟล์");
        page.addView(refresh);

        historyList = new ListView(this);
        page.addView(historyList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        refresh.setOnClickListener(v -> refreshHistory());
        return page;
    }

    private void refreshStatus() {
        SharedPreferences p = Prefs.get(this);
        boolean enabled = p.getBoolean(Prefs.ENABLED, false);
        String state = p.getString(Prefs.SERVICE_STATE, "STOPPED");
        String message = p.getString(Prefs.MESSAGE, "");
        String backend = p.getString(Prefs.BACKEND_UPDATE, "-");
        String last = p.getString(Prefs.LAST_EXPORTED, "-");
        String file = p.getString(Prefs.LAST_EXPORT_NAME, "-");
        String err = p.getString(Prefs.LAST_ERROR, "");

        flashlinkText.setText(
                "Flashlink\n" +
                (FlashlinkHelper.isInstalled(this)
                        ? "✓ ติดตั้งแล้ว • package com.eagleyun.sase"
                        : "✗ ไม่พบแอป Flashlink")
        );

        long captured = SessionStore.capturedAt();
        sessionText.setText(
                "FineBI Session\n" +
                (SessionStore.isReady()
                        ? "✓ พร้อมใน RAM • " + formatTime(captured)
                        : "ยังไม่จับ session • เปิดแท็บ FineBI และ login 1 ครั้ง")
        );

        templateText.setText(
                "Export Template\n" +
                (TemplateStore.isReady(this)
                        ? "✓ พร้อม • Auto Export ใช้ template ที่เรียนรู้แล้ว"
                        : "ยังไม่มี • ครั้งแรกให้เปิด FineBI แล้วกด Export Excel 1 ครั้ง")
        );

        StringBuilder sb = new StringBuilder();
        sb.append("Auto Export\n");
        sb.append(enabled ? "● ENABLED" : "○ STOPPED");
        sb.append(" • ").append(state).append('\n');
        sb.append(message).append('\n');
        sb.append("Backend: ").append(backend).append('\n');
        sb.append("Last Export: ").append(last).append('\n');
        sb.append("File: ").append(file);
        if (err != null && !err.isEmpty()) {
            sb.append("\nError: ").append(err);
        }
        statusText.setText(sb.toString());
    }

    private void refreshHistory() {
        List<ExportStore.Item> items = ExportStore.list(this, 100);
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (ExportStore.Item item : items) {
            labels.add(item.name + "\n" + formatTime(item.modified));
        }
        if (labels.isEmpty()) labels.add("ยังไม่มีไฟล์ Export");

        historyList.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                labels
        ));

        historyList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= items.size()) return;
            ExportStore.Item item = items.get(position);
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(
                        item.uri,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                );
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "ไม่พบแอปเปิด Excel", Toast.LENGTH_SHORT).show();
            }
        });
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

    private void startService() {
        Prefs.get(this).edit().putBoolean(Prefs.ENABLED, true).apply();
        Intent i = new Intent(this, AutoExportService.class)
                .setAction(AutoExportService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        Toast.makeText(this, "Auto Export เริ่มทำงาน", Toast.LENGTH_SHORT).show();
    }

    private void stopServiceAction() {
        Intent i = new Intent(this, AutoExportService.class)
                .setAction(AutoExportService.ACTION_STOP);
        startService(i);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    1001
            );
        }
    }

    private void showPage(View page) {
        statusPage.setVisibility(page == statusPage ? View.VISIBLE : View.GONE);
        fineBiPage.setVisibility(page == fineBiPage ? View.VISIBLE : View.GONE);
        historyPage.setVisibility(page == historyPage ? View.VISIBLE : View.GONE);
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private TextView heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(22);
        t.setTextColor(Color.rgb(24, 33, 45));
        t.setPadding(dp(4), dp(4), dp(4), dp(12));
        return t;
    }

    private TextView statusCard(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(Color.rgb(30, 42, 58));
        t.setBackgroundColor(Color.WHITE);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(0, 0, 0, dp(9));
        t.setLayoutParams(p);
        return t;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(p);
        return b;
    }

    private Button navButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date(ms));
    }
}
