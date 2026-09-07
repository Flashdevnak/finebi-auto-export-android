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
import android.widget.BaseAdapter;
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
    private TextView navStatus;
    private TextView navFineBi;
    private TextView navFiles;

    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView heroBadge;
    private TextView flashlinkValue;
    private TextView sessionValue;
    private TextView templateValue;
    private TextView latestValue;
    private TextView latestDetail;
    private TextView primaryAction;
    private TextView statusDetail;
    private TextView fineBiToolbarStatus;

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
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(UiKit.NAVY);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        requestNotificationPermissionIfNeeded();
        buildUi();
        showPage(statusPage, navStatus);
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
        root.setBackgroundColor(UiKit.BG);

        root.addView(buildAppBar());

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

        root.addView(buildBottomNav());
        setContentView(root);
    }

    private View buildAppBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(12), dp(18), dp(12));
        bar.setBackgroundColor(UiKit.NAVY);

        TextView mark = UiKit.text(this, "F", 18, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(UiKit.rounded(UiKit.BLUE, 12, this));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        markLp.rightMargin = dp(12);
        bar.addView(mark, markLp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.text(this, "FineBI Auto Export", 18, Color.WHITE, true);
        TextView sub = UiKit.text(this, "HUB Departure Monitor", 12, Color.rgb(191, 201, 216), false);
        sub.setPadding(0, dp(2), 0, 0);
        labels.addView(title);
        labels.addView(sub);
        bar.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView live = UiKit.text(this, "LIVE", 11, Color.WHITE, true);
        live.setGravity(Gravity.CENTER);
        live.setPadding(dp(10), dp(6), dp(10), dp(6));
        live.setBackground(UiKit.rounded(Color.rgb(34, 197, 94), 20, this));
        bar.addView(live);
        return bar;
    }

    private LinearLayout buildStatusPage() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(24));

        body.addView(buildHeroCard());

        TextView section = UiKit.text(this, "สถานะระบบ", 13, UiKit.MUTED, true);
        LinearLayout.LayoutParams sectionLp = UiKit.full(this, 18);
        sectionLp.bottomMargin = dp(8);
        body.addView(section, sectionLp);

        body.addView(buildStatusGrid());

        TextView recent = UiKit.text(this, "รอบล่าสุด", 13, UiKit.MUTED, true);
        LinearLayout.LayoutParams recentLp = UiKit.full(this, 18);
        recentLp.bottomMargin = dp(8);
        body.addView(recent, recentLp);
        body.addView(buildLatestCard());

        primaryAction = UiKit.button(this, "เริ่ม Auto Export", true);
        primaryAction.setOnClickListener(v -> toggleAutoExport());
        body.addView(primaryAction, UiKit.full(this, 16));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView fineBi = UiKit.button(this, "เปิด FineBI", false);
        TextView flash = UiKit.button(this, "เปิด Flashlink", false);
        fineBi.setOnClickListener(v -> {
            showPage(fineBiPage, navFineBi);
            webView.loadUrl(FineBiConfig.ENTRY_URL);
        });
        flash.setOnClickListener(v -> {
            if (!FlashlinkHelper.open(this)) {
                Toast.makeText(this, "เปิด Flashlink ไม่สำเร็จ", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = dp(5);
        actions.addView(fineBi, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(5);
        actions.addView(flash, half2);
        body.addView(actions, UiKit.full(this, 10));

        TextView battery = UiKit.button(this, "ตั้งค่า Battery = Unrestricted", false);
        battery.setOnClickListener(v -> openBatterySettings());
        body.addView(battery, UiKit.full(this, 10));

        statusDetail = UiKit.text(this, "", 12, UiKit.MUTED, false);
        statusDetail.setPadding(dp(4), dp(14), dp(4), 0);
        body.addView(statusDetail);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        return container;
    }

    private View buildHeroCard() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(UiKit.gradient(Color.rgb(25, 45, 78), Color.rgb(35, 83, 169), 18, this));
        UiKit.elevation(hero, 3);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        heroTitle = UiKit.text(this, "พร้อมเริ่มทำงาน", 22, Color.WHITE, true);
        heroSubtitle = UiKit.text(this, "กำลังตรวจสถานะ...", 13, Color.rgb(219, 234, 254), false);
        heroSubtitle.setPadding(0, dp(5), 0, 0);
        left.addView(heroTitle);
        left.addView(heroSubtitle);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        heroBadge = UiKit.text(this, "HUB • ALL", 11, Color.WHITE, true);
        heroBadge.setGravity(Gravity.CENTER);
        heroBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        heroBadge.setBackground(UiKit.rounded(Color.argb(55, 255, 255, 255), 20, this));
        top.addView(heroBadge);
        hero.addView(top);

        TextView hint = UiKit.text(this, "ตรวจข้อมูลอัตโนมัติ • Export เมื่อ th_update_time เปลี่ยน", 12,
                Color.rgb(219, 234, 254), false);
        hint.setPadding(0, dp(14), 0, 0);
        hero.addView(hint);
        return hero;
    }

    private View buildStatusGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout flashCard = metricCard("Flashlink", "กำลังตรวจ", "Network route");
        flashlinkValue = (TextView) flashCard.getChildAt(1);
        LinearLayout sessionCard = metricCard("FineBI Session", "ยังไม่พร้อม", "เก็บเฉพาะใน RAM");
        sessionValue = (TextView) sessionCard.getChildAt(1);
        addMetricPair(row1, flashCard, sessionCard);
        grid.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout templateCard = metricCard("Export Template", "ยังไม่มี", "เรียนรู้ครั้งเดียว");
        templateValue = (TextView) templateCard.getChildAt(1);
        LinearLayout hubCard = metricCard("HUB Filter", "SELECT ALL", "บังคับก่อน Export");
        TextView hubValue = (TextView) hubCard.getChildAt(1);
        hubValue.setTextColor(UiKit.BLUE);
        addMetricPair(row2, templateCard, hubCard);
        LinearLayout.LayoutParams row2Lp = UiKit.full(this, 10);
        grid.addView(row2, row2Lp);
        return grid;
    }

    private void addMetricPair(LinearLayout row, View a, View b) {
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.rightMargin = dp(5);
        row.addView(a, p1);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.leftMargin = dp(5);
        row.addView(b, p2);
    }

    private LinearLayout metricCard(String title, String value, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 14, this));
        UiKit.elevation(card, 1);

        TextView t = UiKit.text(this, title, 12, UiKit.MUTED, false);
        TextView v = UiKit.text(this, value, 15, UiKit.TEXT, true);
        v.setPadding(0, dp(5), 0, 0);
        TextView d = UiKit.text(this, detail, 11, UiKit.MUTED, false);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(t);
        card.addView(v);
        card.addView(d);
        return card;
    }

    private View buildLatestCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 14, this));

        TextView icon = UiKit.text(this, "XLSX", 11, UiKit.GREEN, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(UiKit.rounded(UiKit.GREEN_SOFT, 10, this));
        card.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(44)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, 0, 0);
        latestValue = UiKit.text(this, "ยังไม่มีไฟล์", 15, UiKit.TEXT, true);
        latestDetail = UiKit.text(this, "รอ Export รอบแรก", 12, UiKit.MUTED, false);
        latestDetail.setPadding(0, dp(3), 0, 0);
        text.addView(latestValue);
        text.addView(latestDetail);
        card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private LinearLayout buildFineBiPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(9), dp(12), dp(9));
        toolbar.setBackgroundColor(Color.WHITE);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView t = UiKit.text(this, "FineBI Browser", 15, UiKit.TEXT, true);
        fineBiToolbarStatus = UiKit.text(this, "รอ Session", 11, UiKit.MUTED, false);
        fineBiToolbarStatus.setPadding(0, dp(2), 0, 0);
        labels.addView(t);
        labels.addView(fineBiToolbarStatus);
        toolbar.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reload = compactButton("Reload");
        TextView flash = compactButton("Flashlink");
        toolbar.addView(reload);
        LinearLayout.LayoutParams flashLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        flashLp.leftMargin = dp(7);
        toolbar.addView(flash, flashLp);
        page.addView(toolbar);

        View divider = new View(this);
        divider.setBackgroundColor(UiKit.BORDER);
        page.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        webView = WebViewFactory.create(
                this,
                new FineBiWebViewClient.Listener() {
                    @Override public void onPageStarted(String url) {
                        Prefs.setStatus(MainActivity.this, "WEBVIEW", "กำลังเปิด FineBI");
                        fineBiToolbarStatus.setText("กำลังโหลด...");
                    }

                    @Override public void onPageFinished(String url) {
                        fineBiToolbarStatus.setText(SessionStore.isReady() ? "Session พร้อม" : "พร้อม Login");
                        installExportCaptureHook();
                    }

                    @Override public void onSessionCaptured() {
                        runOnUiThread(() -> {
                            fineBiToolbarStatus.setText("Session พร้อม");
                            fineBiToolbarStatus.setTextColor(UiKit.GREEN);
                            Prefs.setStatus(MainActivity.this, "RUNNING", "FineBI session พร้อม");
                            startAutoExportService(false);
                        });
                    }

                    @Override public void onMainFrameError(String description) {
                        runOnUiThread(() -> {
                            fineBiToolbarStatus.setText("เข้า FineBI ไม่ได้");
                            fineBiToolbarStatus.setTextColor(UiKit.RED);
                            Toast.makeText(MainActivity.this, "FineBI เข้าไม่ได้ — ตรวจ Flashlink",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
        webView.addJavascriptInterface(
                new ExportCaptureBridge(this, new ExportCaptureBridge.Listener() {
                    @Override public void onTemplateCaptured() {
                        Prefs.setStatus(MainActivity.this, "RUNNING", "Export template พร้อม");
                        startAutoExportService(false);
                        refreshStatus();
                    }

                    @Override public void onTemplateCaptureError(String message) {
                        Toast.makeText(MainActivity.this,
                                "จับ Export template ไม่สำเร็จ: " + message,
                                Toast.LENGTH_LONG).show();
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
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(14), dp(14), dp(14));
        page.setBackgroundColor(UiKit.BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(UiKit.text(this, "ประวัติไฟล์", 20, UiKit.TEXT, true));
        labels.addView(UiKit.text(this, "ไฟล์ XLSX ที่ Export สำเร็จ", 12, UiKit.MUTED, false));
        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView refresh = compactButton("รีเฟรช");
        header.addView(refresh);
        page.addView(header);

        historyList = new ListView(this);
        historyList.setDivider(null);
        historyList.setDividerHeight(0);
        historyList.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        listLp.topMargin = dp(12);
        page.addView(historyList, listLp);
        refresh.setOnClickListener(v -> refreshHistory());
        return page;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(6), dp(8), dp(7));
        nav.setBackgroundColor(Color.WHITE);
        UiKit.elevation(nav, 8);

        navStatus = navItem("สถานะ");
        navFineBi = navItem("FineBI");
        navFiles = navItem("ไฟล์");
        nav.addView(navStatus, navLp());
        nav.addView(navFineBi, navLp());
        nav.addView(navFiles, navLp());

        navStatus.setOnClickListener(v -> showPage(statusPage, navStatus));
        navFineBi.setOnClickListener(v -> {
            showPage(fineBiPage, navFineBi);
            if (webView.getUrl() == null || "about:blank".equals(webView.getUrl())) {
                webView.loadUrl(FineBiConfig.ENTRY_URL);
            }
        });
        navFiles.setOnClickListener(v -> {
            refreshHistory();
            showPage(historyPage, navFiles);
        });
        return nav;
    }

    private TextView navItem(String label) {
        TextView t = UiKit.text(this, label, 13, UiKit.MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        t.setClickable(true);
        return t;
    }

    private LinearLayout.LayoutParams navLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private TextView compactButton(String label) {
        TextView b = UiKit.text(this, label, 12, UiKit.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 10, this));
        b.setClickable(true);
        return b;
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

        boolean flashInstalled = FlashlinkHelper.isInstalled(this);
        boolean sessionReady = SessionStore.isReady();
        boolean templateReady = TemplateStore.isReady(this);
        boolean running = enabled && ("RUNNING".equals(state) || "EXPORTING".equals(state));

        flashlinkValue.setText(flashInstalled ? "พร้อม" : "ไม่พบแอป");
        flashlinkValue.setTextColor(flashInstalled ? UiKit.GREEN : UiKit.RED);
        sessionValue.setText(sessionReady ? "พร้อม" : "รอ Login");
        sessionValue.setTextColor(sessionReady ? UiKit.GREEN : UiKit.AMBER);
        templateValue.setText(templateReady ? "พร้อม" : "ต้องเรียนรู้");
        templateValue.setTextColor(templateReady ? UiKit.GREEN : UiKit.AMBER);

        if (running) {
            heroTitle.setText("Auto Export กำลังทำงาน");
            heroSubtitle.setText("Backend ล่าสุด • " + backend);
            primaryAction.setText("หยุด Auto Export");
            primaryAction.setBackground(UiKit.rounded(UiKit.RED, 12, this));
        } else if (enabled) {
            heroTitle.setText("ระบบกำลังเตรียมพร้อม");
            heroSubtitle.setText(message == null || message.isEmpty() ? "กำลังเชื่อมต่อ FineBI" : message);
            primaryAction.setText("หยุด Auto Export");
            primaryAction.setBackground(UiKit.rounded(UiKit.RED, 12, this));
        } else {
            heroTitle.setText("พร้อมเริ่ม Auto Export");
            heroSubtitle.setText(templateReady ? "ตั้งค่าครบแล้ว • กดเริ่มได้ทันที" : "ครั้งแรกให้ Login และ Export Excel 1 ครั้ง");
            primaryAction.setText("เริ่ม Auto Export");
            primaryAction.setBackground(UiKit.rounded(UiKit.BLUE, 12, this));
        }

        heroBadge.setText("HUB • SELECT ALL");

        if (last != null && !last.isEmpty() && !"-".equals(last)) {
            latestValue.setText(last);
            latestDetail.setText(file == null || file.isEmpty() ? "Export สำเร็จ" : file);
        } else {
            latestValue.setText("ยังไม่มีไฟล์");
            latestDetail.setText("รอ Export รอบแรก");
        }

        StringBuilder detail = new StringBuilder();
        detail.append("สถานะ: ").append(state);
        if (message != null && !message.isEmpty()) detail.append(" • ").append(message);
        detail.append("\nPolling: 10 วินาทีช่วงใกล้รอบข้อมูล • 60 วินาทีช่วงปกติ");
        detail.append("\nไฟล์: Downloads/FineBI_Auto_Export/YYYY-MM-DD/");
        if (err != null && !err.isEmpty()) detail.append("\nล่าสุด: ").append(err);
        statusDetail.setText(detail.toString());
    }

    private void refreshHistory() {
        List<ExportStore.Item> items = ExportStore.list(this, 100);
        historyList.setAdapter(new ExportHistoryAdapter(items));
        historyList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= items.size()) return;
            ExportStore.Item item = items.get(position);
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(item.uri,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
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

    private void toggleAutoExport() {
        boolean enabled = Prefs.get(this).getBoolean(Prefs.ENABLED, false);
        if (enabled) stopServiceAction();
        else startAutoExportService(true);
    }

    private void startAutoExportService(boolean toast) {
        Prefs.get(this).edit().putBoolean(Prefs.ENABLED, true).apply();
        Intent i = new Intent(this, AutoExportService.class)
                .setAction(AutoExportService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        if (toast) Toast.makeText(this, "Auto Export เริ่มทำงาน", Toast.LENGTH_SHORT).show();
    }

    private void stopServiceAction() {
        Intent i = new Intent(this, AutoExportService.class)
                .setAction(AutoExportService.ACTION_STOP);
        startService(i);
        Toast.makeText(this, "หยุด Auto Export แล้ว", Toast.LENGTH_SHORT).show();
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void showPage(View page, TextView activeNav) {
        statusPage.setVisibility(page == statusPage ? View.VISIBLE : View.GONE);
        fineBiPage.setVisibility(page == fineBiPage ? View.VISIBLE : View.GONE);
        historyPage.setVisibility(page == historyPage ? View.VISIBLE : View.GONE);
        setNavState(navStatus, activeNav == navStatus);
        setNavState(navFineBi, activeNav == navFineBi);
        setNavState(navFiles, activeNav == navFiles);
    }

    private void setNavState(TextView item, boolean active) {
        item.setTextColor(active ? UiKit.BLUE : UiKit.MUTED);
        item.setBackground(active
                ? UiKit.rounded(UiKit.BLUE_SOFT, 12, this)
                : UiKit.rounded(Color.WHITE, 12, this));
    }

    private int dp(int value) {
        return UiKit.dp(this, value);
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    private final class ExportHistoryAdapter extends BaseAdapter {
        private final List<ExportStore.Item> items;

        ExportHistoryAdapter(List<ExportStore.Item> items) {
            this.items = items;
        }

        @Override public int getCount() { return items.isEmpty() ? 1 : items.size(); }
        @Override public Object getItem(int position) { return items.isEmpty() ? null : items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (items.isEmpty()) {
                TextView empty = UiKit.text(MainActivity.this, "ยังไม่มีไฟล์ Export", 14, UiKit.MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(20), dp(36), dp(20), dp(36));
                return empty;
            }

            ExportStore.Item item = items.get(position);
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 14, MainActivity.this));

            TextView icon = UiKit.text(MainActivity.this, "XLSX", 10, UiKit.GREEN, true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(UiKit.rounded(UiKit.GREEN_SOFT, 9, MainActivity.this));
            card.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(42)));

            LinearLayout text = new LinearLayout(MainActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(12), 0, 0, 0);
            TextView name = UiKit.text(MainActivity.this, item.name, 13, UiKit.TEXT, true);
            TextView date = UiKit.text(MainActivity.this, formatTime(item.modified), 11, UiKit.MUTED, false);
            date.setPadding(0, dp(3), 0, 0);
            text.addView(name);
            text.addView(date);
            card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout wrap = new LinearLayout(MainActivity.this);
            wrap.setPadding(0, 0, 0, dp(9));
            wrap.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return wrap;
        }
    }
}
