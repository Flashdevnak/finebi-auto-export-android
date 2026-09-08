package com.flashdevnak.finebiautoexport;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

/** Main launcher UI for unattended daily operation. */
public final class DailyMainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout content;
    private TextView connectionBadge;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView sessionValue;
    private TextView templateValue;
    private TextView backendValue;
    private TextView pollValue;
    private TextView batteryValue;
    private TextView dailyValue;
    private TextView updateValue;
    private TextView updateButton;
    private TextView mailValue;
    private TextView mailButton;
    private TextView latestFile;
    private TextView primaryAction;
    private TextView batteryButton;
    private TextView footer;
    private TextView navOverview;
    private TextView navFineBi;
    private TextView navFiles;
    private String lastUpdateMessage = "";

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            try {
                refreshStatus();
            } catch (Throwable ignored) {
            } finally {
                if (!isFinishing()) ui.postDelayed(this, 3000L);
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> ui.post(this::safeRefreshStatus);
    private final SharedPreferences.OnSharedPreferenceChangeListener mailPrefListener =
            (prefs, key) -> ui.post(this::safeRefreshStatus);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(UiKit.NAVY);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        requestNotificationPermissionIfNeeded();
        buildUi();
        showStatusPage();
        if (Prefs.get(this).getBoolean(Prefs.ENABLED, false)) startAutoExport(false);
    }

    @Override protected void onStart() {
        super.onStart();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefListener);
        MailSettings.get(this).registerOnSharedPreferenceChangeListener(mailPrefListener);
    }

    @Override protected void onResume() {
        super.onResume();
        ui.removeCallbacks(refreshRunnable);
        safeRefreshStatus();
        ui.postDelayed(refreshRunnable, 1000L);
        UpdateManager.resumePendingInstall(this, this::onUpdateEvent);
        UpdateManager.checkAsync(this, false, this::onUpdateEvent);
        MailManager.kick(this);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) safeRefreshStatus();
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override protected void onStop() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        MailSettings.get(this).unregisterOnSharedPreferenceChangeListener(mailPrefListener);
        super.onStop();
    }

    private void safeRefreshStatus() {
        try { refreshStatus(); } catch (Throwable ignored) {}
    }

    private void onUpdateEvent(UpdateManager.Info info, String message) {
        runOnUiThread(() -> {
            lastUpdateMessage = message == null ? "" : message;
            safeRefreshStatus();
        });
    }

    private boolean compact() {
        return getResources().getConfiguration().screenWidthDp < 600;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiKit.BG);
        root.addView(buildAppBar());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildBottomNav());
        setContentView(root);
    }

    private View buildAppBar() {
        boolean c = compact();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c ? 12 : 18), dp(c ? 10 : 13), dp(c ? 12 : 18), dp(c ? 10 : 13));
        bar.setBackgroundColor(UiKit.NAVY);

        TextView mark = UiKit.text(this, "F", c ? 16 : 18, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(UiKit.rounded(UiKit.BLUE, 12, this));
        int size = dp(c ? 36 : 42);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(size, size);
        mlp.rightMargin = dp(c ? 10 : 12);
        bar.addView(mark, mlp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(UiKit.text(this, "FineBI Auto Export", c ? 16 : 18, Color.WHITE, true));
        TextView appSub = UiKit.text(this,
                "HUB Departure Monitor",
                c ? 10 : 11,
                Color.rgb(203, 213, 225),
                false);
        appSub.setPadding(0, dp(2), 0, 0);
        labels.addView(appSub);
        bar.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        connectionBadge = UiKit.text(this, "ออนไลน์", c ? 9 : 10, Color.WHITE, true);
        connectionBadge.setGravity(Gravity.CENTER);
        connectionBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        connectionBadge.setBackground(UiKit.rounded(UiKit.GREEN, 20, this));
        bar.addView(connectionBadge);
        return bar;
    }

    private void setPage(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showStatusPage() {
        boolean c = compact();
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(c ? 12 : 18), dp(c ? 12 : 16), dp(c ? 12 : 18), dp(22));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(c ? 16 : 20), dp(c ? 16 : 20), dp(c ? 16 : 20), dp(c ? 16 : 20));
        hero.setBackground(UiKit.gradient(UiKit.NAVY_SOFT, UiKit.BLUE_DARK, 18, this));
        UiKit.elevation(hero, 2);

        TextView heroKicker = UiKit.text(this, "AUTO EXPORT", 10, Color.rgb(191, 219, 254), true);
        hero.addView(heroKicker);
        heroTitle = UiKit.text(this, "กำลังตรวจสอบความพร้อม", c ? 20 : 23, Color.WHITE, true);
        heroTitle.setPadding(0, dp(6), 0, 0);
        hero.addView(heroTitle);
        heroSubtitle = UiKit.text(this,
                "ตรวจการเชื่อมต่อ FineBI และสถานะการทำงานอัตโนมัติ",
                c ? 11 : 13,
                Color.rgb(219, 234, 254),
                false);
        heroSubtitle.setPadding(0, dp(6), 0, 0);
        hero.addView(heroSubtitle);
        body.addView(hero);

        addSection(body,
                "ภาพรวมการทำงาน",
                "สถานะสำคัญสำหรับการส่งออกรายงานโดยอัตโนมัติ",
                c ? 18 : 22);

        LinearLayout dailyCard = metricCard(
                "ระบบอัตโนมัติ",
                "กำลังตรวจ",
                "ทำงานเบื้องหลังและเริ่มใหม่หลังเปิดเครื่อง");
        dailyValue = (TextView) dailyCard.getChildAt(1);
        LinearLayout batteryCard = metricCard(
                "การทำงานเบื้องหลัง",
                "กำลังตรวจ",
                "แนะนำ Unrestricted เพื่อความเสถียรระยะยาว");
        batteryValue = (TextView) batteryCard.getChildAt(1);
        addResponsive(body, dailyCard, batteryCard, 9);

        addSection(body,
                "การเชื่อมต่อและข้อมูล",
                "ตรวจเฉพาะสถานะที่ระบบใช้งานจริง",
                18);

        LinearLayout sessionCard = metricCard(
                "FineBI",
                "รอการเชื่อมต่อ",
                "สถานะเข้าสู่ระบบเก็บเฉพาะระหว่างการใช้งาน");
        sessionValue = (TextView) sessionCard.getChildAt(1);
        LinearLayout templateCard = metricCard(
                "รูปแบบการส่งออก",
                "กำลังตรวจ",
                "ตั้งค่าครั้งแรกจาก Export Excel เพียงครั้งเดียว");
        templateValue = (TextView) templateCard.getChildAt(1);
        addResponsive(body, sessionCard, templateCard, 9);

        LinearLayout backendCard = metricCard(
                "ข้อมูลล่าสุด",
                "-",
                "อ้างอิงเวลาอัปเดตจาก FineBI");
        backendValue = (TextView) backendCard.getChildAt(1);
        LinearLayout pollCard = metricCard(
                "รอบตรวจสอบ",
                "-",
                "ปรับความถี่อัตโนมัติตามช่วงเวลาและเครือข่าย");
        pollValue = (TextView) pollCard.getChildAt(1);
        addResponsive(body, backendCard, pollCard, 9);

        addSection(body,
                "การส่งรายงานและเวอร์ชัน",
                "จัดการอีเมลอัตโนมัติและการอัปเดตแอป",
                18);

        LinearLayout mailCard = metricCard(
                "อีเมลอัตโนมัติ",
                "กำลังตรวจ",
                "ส่งไฟล์ XLSX หลัง Export และตรวจสอบผ่าน");
        mailValue = (TextView) mailCard.getChildAt(1);

        LinearLayout updateCard = metricCard(
                "เวอร์ชันแอป",
                "กำลังตรวจ",
                "ตรวจแพ็กเกจและลายเซ็นก่อนติดตั้งทุกครั้ง");
        updateValue = (TextView) updateCard.getChildAt(1);
        addResponsive(body, mailCard, updateCard, 9);

        mailButton = UiKit.button(this, "ตั้งค่าการส่งอีเมล", false);
        mailButton.setOnClickListener(v -> startActivity(new Intent(this, MailSettingsActivity.class)));

        updateButton = UiKit.button(this, "ตรวจสอบเวอร์ชันล่าสุด", false);
        updateButton.setOnClickListener(v -> {
            UpdateManager.Info info = UpdateManager.cached(this);
            if (info.available) {
                UpdateManager.startUpdate(this, info, this::onUpdateEvent);
            } else {
                lastUpdateMessage = "กำลังตรวจสอบเวอร์ชันล่าสุด...";
                safeRefreshStatus();
                UpdateManager.checkAsync(this, true, this::onUpdateEvent);
            }
        });
        addResponsive(body, mailButton, updateButton, 9);

        addSection(body,
                "รายงานล่าสุด",
                "ไฟล์ที่บันทึกสำเร็จล่าสุดในเครื่อง",
                18);

        LinearLayout fileCard = new LinearLayout(this);
        fileCard.setOrientation(LinearLayout.HORIZONTAL);
        fileCard.setGravity(Gravity.CENTER_VERTICAL);
        fileCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        fileCard.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 16, this));

        TextView fileIcon = UiKit.text(this, "XLSX", 10, UiKit.GREEN, true);
        fileIcon.setGravity(Gravity.CENTER);
        fileIcon.setBackground(UiKit.rounded(UiKit.GREEN_SOFT, 10, this));
        fileCard.addView(fileIcon, new LinearLayout.LayoutParams(dp(52), dp(44)));

        latestFile = UiKit.text(this, "ยังไม่มีรายงานที่บันทึก", c ? 12 : 14, UiKit.TEXT, true);
        latestFile.setSingleLine(true);
        latestFile.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        latestFile.setPadding(dp(12), 0, 0, 0);
        fileCard.addView(latestFile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(fileCard, UiKit.full(this, 9));

        primaryAction = UiKit.button(this, "เริ่มระบบอัตโนมัติ", true);
        primaryAction.setOnClickListener(v -> toggleAutoExport());
        body.addView(primaryAction, UiKit.full(this, 16));

        TextView finebi = UiKit.button(this, "เปิด FineBI", false);
        finebi.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        TextView flash = UiKit.button(this, "เปิด Flashlink", false);
        flash.setOnClickListener(v -> {
            if (!FlashlinkHelper.open(this)) {
                Toast.makeText(this, "ไม่สามารถเปิด Flashlink ได้", Toast.LENGTH_SHORT).show();
            }
        });
        addResponsive(body, finebi, flash, 9);

        batteryButton = UiKit.button(this, "ตั้งค่าการทำงานเบื้องหลัง", false);
        batteryButton.setOnClickListener(v -> BatteryHelper.requestUnrestricted(this));
        body.addView(batteryButton, UiKit.full(this, 9));

        footer = UiKit.text(this, "", c ? 10 : 11, UiKit.MUTED, false);
        footer.setPadding(dp(3), dp(14), dp(3), dp(4));
        body.addView(footer);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        setPage(scroll);
        setNavState(navOverview);
        safeRefreshStatus();
    }

    private void addSection(LinearLayout body, String title, String hint, int topDp) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(UiKit.sectionTitle(this, title));
        TextView h = UiKit.sectionHint(this, hint);
        h.setPadding(0, dp(3), 0, 0);
        section.addView(h);
        body.addView(section, UiKit.full(this, topDp));
    }

    private LinearLayout metricCard(String title, String value, String detail) {
        boolean c = compact();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c ? 13 : 15), dp(c ? 12 : 14), dp(c ? 13 : 15), dp(c ? 12 : 14));
        card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 16, this));

        TextView t = UiKit.text(this, title, c ? 11 : 12, UiKit.MUTED, false);
        card.addView(t);

        TextView v = UiKit.text(this, value, c ? 15 : 16, UiKit.TEXT, true);
        v.setPadding(0, dp(5), 0, 0);
        card.addView(v);

        TextView d = UiKit.text(this, detail, c ? 10 : 11, UiKit.MUTED, false);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d);
        return card;
    }

    private void addResponsive(LinearLayout body, View a, View b, int topDp) {
        if (compact()) {
            body.addView(a, UiKit.full(this, topDp));
            body.addView(b, UiKit.full(this, 8));
        } else {
            body.addView(pair(a, b), UiKit.full(this, topDp));
        }
    }

    private LinearLayout pair(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.rightMargin = dp(6);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.leftMargin = dp(6);
        row.addView(a, p1);
        row.addView(b, p2);
        return row;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setBackgroundColor(Color.WHITE);
        UiKit.elevation(nav, 10);

        navOverview = navButton("ภาพรวม");
        navFineBi = navButton("FineBI");
        navFiles = navButton("ไฟล์");

        navOverview.setOnClickListener(v -> showStatusPage());
        navFineBi.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        navFiles.setOnClickListener(v -> showFilesPage());

        nav.addView(navOverview, navLp());
        nav.addView(navFineBi, navLp());
        nav.addView(navFiles, navLp());
        return nav;
    }

    private TextView navButton(String s) {
        TextView v = UiKit.text(this, s, compact() ? 12 : 13, UiKit.MUTED, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(10), dp(8), dp(10));
        v.setClickable(true);
        return v;
    }

    private LinearLayout.LayoutParams navLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private void setNavState(TextView active) {
        if (navOverview == null) return;
        applyNav(navOverview, active == navOverview);
        applyNav(navFineBi, active == navFineBi);
        applyNav(navFiles, active == navFiles);
    }

    private void applyNav(TextView item, boolean active) {
        item.setTextColor(active ? UiKit.BLUE : UiKit.MUTED);
        item.setBackground(active
                ? UiKit.rounded(UiKit.BLUE_SOFT, 12, this)
                : UiKit.rounded(Color.WHITE, 12, this));
    }

    private void showFilesPage() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(compact() ? 12 : 18), dp(14), dp(compact() ? 12 : 18), dp(12));

        body.addView(UiKit.text(this, "รายงานที่บันทึกไว้", compact() ? 20 : 23, UiKit.TEXT, true));
        TextView sub = UiKit.text(this,
                "เปิดดูไฟล์ XLSX ที่ส่งออกสำเร็จได้จากหน้านี้",
                11,
                UiKit.MUTED,
                false);
        sub.setPadding(0, dp(5), 0, 0);
        body.addView(sub);

        List<ExportStore.Item> items = ExportStore.list(this, 100);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setAdapter(new FileAdapter(items));
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= items.size()) return;
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(items.get(pos).uri,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "ไม่พบแอปที่รองรับไฟล์ Excel", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = dp(14);
        body.addView(list, listLp);
        setPage(body);
        setNavState(navFiles);
    }

    private void refreshStatus() {
        if (heroTitle == null || dailyValue == null) return;

        SharedPreferences p = Prefs.get(this);
        boolean enabled = p.getBoolean(Prefs.ENABLED, false);
        String state = p.getString(Prefs.SERVICE_STATE, "STOPPED");
        String message = p.getString(Prefs.MESSAGE, "");
        String backend = p.getString(Prefs.BACKEND_UPDATE, "-");
        String file = p.getString(Prefs.LAST_EXPORT_NAME, "-");
        String pollMode = p.getString(Prefs.POLL_MODE, "-");
        long nextCheck = p.getLong(Prefs.NEXT_CHECK_AT, 0L);
        long lastPollOk = p.getLong(Prefs.LAST_POLL_OK_AT, 0L);
        long heartbeat = p.getLong(Prefs.LAST_SERVICE_HEARTBEAT_AT, 0L);
        int errors = p.getInt(Prefs.CONSECUTIVE_ERRORS, 0);

        boolean online = NetworkHelper.isOnline(this);
        boolean sessionReady = SessionStore.isReady();
        boolean templateReady = TemplateStore.isReady(this);
        boolean flashInstalled = FlashlinkHelper.isInstalled(this);
        boolean batteryAcknowledged = BatteryHelper.isUnrestricted(this);
        boolean batteryNeedsReview = BatteryHelper.needsReview(this);
        long now = System.currentTimeMillis();
        boolean pollFresh = lastPollOk > 0L && now - lastPollOk <= 12 * 60_000L;
        boolean serviceFresh = heartbeat > 0L && now - heartbeat <= 12 * 60_000L;

        connectionBadge.setText(online ? "ออนไลน์" : "ออฟไลน์");
        connectionBadge.setBackground(UiKit.rounded(online ? UiKit.GREEN : UiKit.AMBER, 20, this));

        sessionValue.setText(sessionReady
                ? "เชื่อมต่อแล้ว"
                : (online ? "รอเข้าสู่ระบบ" : "รอเครือข่าย"));
        sessionValue.setTextColor(sessionReady ? UiKit.GREEN : UiKit.AMBER);

        templateValue.setText(templateReady ? "พร้อมใช้งาน" : "ต้องตั้งค่าครั้งแรก");
        templateValue.setTextColor(templateReady ? UiKit.GREEN : UiKit.AMBER);

        backendValue.setText(backend == null || backend.isEmpty() ? "ยังไม่มีข้อมูล" : backend);
        latestFile.setText(file == null || file.isEmpty() || "-".equals(file)
                ? "ยังไม่มีรายงานที่บันทึก"
                : file);

        pollValue.setText(pollModeLabel(pollMode));
        if ("FAST".equals(pollMode)) pollValue.setTextColor(UiKit.AMBER);
        else if ("ECO".equals(pollMode) || "SYNC".equals(pollMode)) pollValue.setTextColor(UiKit.GREEN);
        else pollValue.setTextColor(UiKit.TEXT);

        if (batteryNeedsReview) {
            batteryValue.setText("ตั้งค่าแล้ว • แนะนำตรวจอีกครั้ง");
            batteryValue.setTextColor(UiKit.AMBER);
            batteryButton.setText("ตรวจการทำงานเบื้องหลัง");
        } else if (batteryAcknowledged) {
            batteryValue.setText("ตั้งค่าแล้ว");
            batteryValue.setTextColor(UiKit.GREEN);
            batteryButton.setText("การตั้งค่าเบื้องหลัง");
        } else {
            batteryValue.setText("แนะนำให้ตั้งค่า");
            batteryValue.setTextColor(UiKit.AMBER);
            batteryButton.setText("ตั้งค่าการทำงานเบื้องหลัง");
        }

        UpdateManager.Info updateInfo = UpdateManager.cached(this);
        if (updateInfo.available) {
            updateValue.setText("มีเวอร์ชัน " + updateInfo.version);
            updateValue.setTextColor(UiKit.BLUE);
            updateButton.setText("อัปเดตเป็น v" + updateInfo.version);
            updateButton.setBackground(UiKit.rounded(UiKit.BLUE, 14, this));
            updateButton.setTextColor(Color.WHITE);
        } else if (updateInfo.checkedAt > 0L) {
            updateValue.setText("เวอร์ชันล่าสุด • v" + BuildConfig.VERSION_NAME);
            updateValue.setTextColor(UiKit.GREEN);
            updateButton.setText("ตรวจสอบเวอร์ชันล่าสุด");
            updateButton.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 14, this));
            updateButton.setTextColor(UiKit.TEXT);
        } else {
            updateValue.setText("ยังไม่ได้ตรวจสอบ");
            updateValue.setTextColor(UiKit.MUTED);
            updateButton.setText("ตรวจสอบเวอร์ชันล่าสุด");
        }

        boolean mailConfigured = MailSettings.configured(this);
        boolean mailEnabled = MailSettings.enabled(this);
        String mailStatus = MailSettings.get(this).getString(MailSettings.STATUS, "");
        String lastSentVersion = MailSettings.get(this).getString(MailSettings.LAST_SENT_VERSION, "");
        if (!mailConfigured) {
            mailValue.setText("ยังไม่ได้ตั้งค่า");
            mailValue.setTextColor(UiKit.AMBER);
            mailButton.setText("ตั้งค่าการส่งอีเมล");
        } else if (!mailEnabled) {
            mailValue.setText("ตั้งค่าแล้ว • ปิดการส่งอัตโนมัติ");
            mailValue.setTextColor(UiKit.MUTED);
            mailButton.setText("แก้ไขการส่งอีเมล");
        } else if (mailStatus != null && mailStatus.startsWith("AUTH_REQUIRED")) {
            mailValue.setText("ต้องเชื่อมต่อบัญชี Google ใหม่");
            mailValue.setTextColor(UiKit.AMBER);
            mailButton.setText("ตรวจการตั้งค่าอีเมล");
        } else if (mailStatus != null && mailStatus.startsWith("RETRY")) {
            mailValue.setText("กำลังรอส่งใหม่");
            mailValue.setTextColor(UiKit.AMBER);
            mailButton.setText("ตรวจการตั้งค่าอีเมล");
        } else if ("SENDING".equals(mailStatus)) {
            mailValue.setText("กำลังส่งรายงาน");
            mailValue.setTextColor(UiKit.AMBER);
            mailButton.setText("การตั้งค่าอีเมล");
        } else {
            mailValue.setText(lastSentVersion == null || lastSentVersion.isEmpty()
                    ? "พร้อมส่งอัตโนมัติ"
                    : "ส่งล่าสุด • " + lastSentVersion);
            mailValue.setTextColor(UiKit.GREEN);
            mailButton.setText("การตั้งค่าอีเมล");
        }

        boolean dailyReady = enabled
                && templateReady
                && flashInstalled
                && (online ? (pollFresh || sessionReady || "STARTING".equals(state) || "SESSION".equals(state)) : true)
                && errors < 3;

        if (dailyReady) {
            dailyValue.setText("ทำงานตามปกติ");
            dailyValue.setTextColor(UiKit.GREEN);
        } else if (!templateReady) {
            dailyValue.setText("ต้องตั้งค่าครั้งแรก");
            dailyValue.setTextColor(UiKit.AMBER);
        } else if (!enabled) {
            dailyValue.setText("ระบบอัตโนมัติหยุดอยู่");
            dailyValue.setTextColor(UiKit.MUTED);
        } else if (!flashInstalled) {
            dailyValue.setText("ไม่พบ Flashlink");
            dailyValue.setTextColor(UiKit.RED);
        } else if (online && !serviceFresh && !pollFresh) {
            dailyValue.setText("กำลังกู้คืนการเชื่อมต่อ");
            dailyValue.setTextColor(UiKit.AMBER);
        } else {
            dailyValue.setText("กำลังเตรียมระบบ");
            dailyValue.setTextColor(UiKit.AMBER);
        }

        if (!templateReady) {
            heroTitle.setText("ตั้งค่าเริ่มต้นอีก 1 ขั้นตอน");
            heroSubtitle.setText("เปิด Flashlink → เข้าสู่ระบบ FineBI → Export Excel 1 ครั้ง เพื่อบันทึกรูปแบบการส่งออก");
        } else if (!enabled) {
            heroTitle.setText("พร้อมเริ่มระบบอัตโนมัติ");
            heroSubtitle.setText("เมื่อเริ่มแล้ว ระบบจะตรวจข้อมูลและส่งออกรายงานตามรอบโดยไม่ต้องเปิดหน้าจอค้างไว้");
        } else if (!online) {
            heroTitle.setText("รอการเชื่อมต่อเครือข่าย");
            heroSubtitle.setText("ระบบพักการตรวจข้อมูลชั่วคราว และจะทำงานต่อเองเมื่อเครือข่ายกลับมา");
        } else if (dailyReady) {
            heroTitle.setText("ระบบอัตโนมัติกำลังทำงาน");
            heroSubtitle.setText("FineBI Auto Export ทำงานเบื้องหลังและตรวจข้อมูลตามรอบที่เหมาะสม");
        } else {
            heroTitle.setText("กำลังเตรียมความพร้อม");
            heroSubtitle.setText(humanStatusMessage(state, message));
        }

        primaryAction.setText(enabled ? "หยุดระบบอัตโนมัติ" : "เริ่มระบบอัตโนมัติ");
        primaryAction.setBackground(UiKit.rounded(enabled ? UiKit.RED : UiKit.BLUE, 14, this));

        StringBuilder f = new StringBuilder();
        f.append("FineBI Auto Export v").append(BuildConfig.VERSION_NAME);
        f.append(" • เริ่มทำงานอัตโนมัติหลังเปิดเครื่องและหลังอัปเดตแอป");
        if (nextCheck > 0L) f.append("\nตรวจข้อมูลครั้งถัดไป: ").append(formatTime(nextCheck));
        if (lastPollOk > 0L) f.append("\nFineBI ตอบกลับล่าสุด: ").append(formatTime(lastPollOk));
        if (mailEnabled) f.append("\nอีเมลอัตโนมัติ: เปิดใช้งาน");
        if (errors > 0) f.append("\nระบบกำลังลองใหม่หลังพบข้อผิดพลาด: ").append(errors).append(" ครั้ง");
        if (!lastUpdateMessage.isEmpty()) f.append("\nอัปเดตแอป: ").append(lastUpdateMessage);
        footer.setText(f.toString());
    }

    private String pollModeLabel(String mode) {
        if (mode == null || mode.isEmpty() || "-".equals(mode)) return "รอเริ่มตรวจข้อมูล";
        if ("FAST".equals(mode)) return "ตรวจถี่ • ใกล้รอบอัปเดต";
        if ("ECO".equals(mode)) return "ประหยัดพลังงาน";
        if ("OFFLINE".equals(mode)) return "พักชั่วคราว • ออฟไลน์";
        if ("SYNC".equals(mode)) return "กำลังตรวจข้อมูล";
        if ("STOPPED".equals(mode)) return "หยุดอยู่";
        return mode;
    }

    private String humanStatusMessage(String state, String message) {
        if (message != null && !message.trim().isEmpty()) return message;
        if ("SESSION".equals(state)) return "กำลังเตรียมการเชื่อมต่อ FineBI";
        if ("STARTING".equals(state)) return "กำลังเริ่มบริการเบื้องหลัง";
        if ("RECOVERING".equals(state)) return "กำลังกู้คืนระบบอัตโนมัติ";
        if ("LOGIN_OR_VPN".equals(state)) return "รอเข้าสู่ระบบ FineBI หรือการเชื่อมต่อ Flashlink";
        return "ระบบกำลังตรวจสอบสถานะล่าสุด";
    }

    private void toggleAutoExport() {
        if (Prefs.get(this).getBoolean(Prefs.ENABLED, false)) stopAutoExport();
        else startAutoExport(true);
    }

    private void startAutoExport(boolean toast) {
        Prefs.get(this).edit()
                .putBoolean(Prefs.ENABLED, true)
                .putBoolean(Prefs.SMART_BATTERY, true)
                .apply();
        Intent i = new Intent(this, AutoExportService.class).setAction(AutoExportService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        if (toast) {
            Toast.makeText(this,
                    NetworkHelper.isOnline(this)
                            ? "เริ่มระบบอัตโนมัติแล้ว"
                            : "เปิดระบบอัตโนมัติแล้ว • ระบบจะเริ่มเมื่อเครือข่ายพร้อม",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAutoExport() {
        startService(new Intent(this, AutoExportService.class).setAction(AutoExportService.ACTION_STOP));
        Toast.makeText(this, "หยุดระบบอัตโนมัติแล้ว", Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int v) { return UiKit.dp(this, v); }

    private static String formatTime(long ms) {
        if (ms <= 0L) return "-";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    private final class FileAdapter extends BaseAdapter {
        private final List<ExportStore.Item> items;
        FileAdapter(List<ExportStore.Item> items) { this.items = items; }
        @Override public int getCount() { return items.isEmpty() ? 1 : items.size(); }
        @Override public Object getItem(int p) { return items.isEmpty() ? null : items.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View cv, ViewGroup parent) {
            if (items.isEmpty()) {
                LinearLayout emptyCard = new LinearLayout(DailyMainActivity.this);
                emptyCard.setOrientation(LinearLayout.VERTICAL);
                emptyCard.setGravity(Gravity.CENTER);
                emptyCard.setPadding(dp(20), dp(34), dp(20), dp(34));
                emptyCard.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 16, DailyMainActivity.this));
                TextView title = UiKit.text(DailyMainActivity.this,
                        "ยังไม่มีรายงานที่บันทึก",
                        14,
                        UiKit.TEXT,
                        true);
                title.setGravity(Gravity.CENTER);
                TextView hint = UiKit.text(DailyMainActivity.this,
                        "เมื่อ Export สำเร็จ ไฟล์จะปรากฏในหน้านี้",
                        11,
                        UiKit.MUTED,
                        false);
                hint.setGravity(Gravity.CENTER);
                hint.setPadding(0, dp(5), 0, 0);
                emptyCard.addView(title);
                emptyCard.addView(hint);
                return emptyCard;
            }

            ExportStore.Item item = items.get(p);
            LinearLayout card = new LinearLayout(DailyMainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(13), dp(14), dp(13));
            card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 16, DailyMainActivity.this));

            TextView icon = UiKit.text(DailyMainActivity.this, "XLSX", 10, UiKit.GREEN, true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(UiKit.rounded(UiKit.GREEN_SOFT, 10, DailyMainActivity.this));
            card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(44)));

            LinearLayout text = new LinearLayout(DailyMainActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(12), 0, 0, 0);
            TextView name = UiKit.text(DailyMainActivity.this, item.name, compact() ? 12 : 13, UiKit.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            TextView date = UiKit.text(DailyMainActivity.this,
                    "บันทึกเมื่อ " + formatTime(item.modified),
                    10,
                    UiKit.MUTED,
                    false);
            date.setPadding(0, dp(4), 0, 0);
            text.addView(name);
            text.addView(date);
            card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout wrap = new LinearLayout(DailyMainActivity.this);
            wrap.setPadding(0, 0, 0, dp(9));
            wrap.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return wrap;
        }
    }
}
