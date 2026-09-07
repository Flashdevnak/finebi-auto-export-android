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
    private TextView liveBadge;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView sessionValue;
    private TextView templateValue;
    private TextView backendValue;
    private TextView pollValue;
    private TextView batteryValue;
    private TextView dailyValue;
    private TextView latestFile;
    private TextView primaryAction;
    private TextView batteryButton;
    private TextView footer;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStatus();
            ui.postDelayed(this, 1500L);
        }
    };

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

    @Override protected void onResume() {
        super.onResume();
        ui.removeCallbacks(refreshRunnable);
        ui.post(refreshRunnable);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refreshRunnable);
        super.onPause();
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
        bar.setPadding(dp(c ? 12 : 18), dp(c ? 8 : 12), dp(c ? 12 : 18), dp(c ? 8 : 12));
        bar.setBackgroundColor(UiKit.NAVY);

        TextView mark = UiKit.text(this, "F", c ? 16 : 18, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(UiKit.rounded(UiKit.BLUE, 11, this));
        int size = dp(c ? 34 : 40);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(size, size);
        mlp.rightMargin = dp(c ? 9 : 12);
        bar.addView(mark, mlp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(UiKit.text(this, "FineBI Auto Export", c ? 16 : 18, Color.WHITE, true));
        labels.addView(UiKit.text(this, "Daily Mode • HUB Departure Monitor", c ? 10 : 12,
                Color.rgb(191, 201, 216), false));
        bar.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        liveBadge = UiKit.text(this, "LIVE", c ? 9 : 11, Color.WHITE, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(dp(8), dp(5), dp(8), dp(5));
        liveBadge.setBackground(UiKit.rounded(UiKit.GREEN, 20, this));
        bar.addView(liveBadge);
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
        body.setPadding(dp(c ? 11 : 16), dp(c ? 10 : 14), dp(c ? 11 : 16), dp(18));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(c ? 14 : 18), dp(c ? 13 : 18), dp(c ? 14 : 18), dp(c ? 13 : 18));
        hero.setBackground(UiKit.gradient(Color.rgb(25,45,78), Color.rgb(35,83,169), 16, this));
        heroTitle = UiKit.text(this, "กำลังตรวจความพร้อม", c ? 19 : 22, Color.WHITE, true);
        heroSubtitle = UiKit.text(this, "Daily Mode", c ? 11 : 13, Color.rgb(219,234,254), false);
        heroSubtitle.setPadding(0, dp(4), 0, 0);
        hero.addView(heroTitle);
        hero.addView(heroSubtitle);
        body.addView(hero);

        body.addView(UiKit.text(this, "พร้อมใช้ประจำวัน", 12, UiKit.MUTED, true), UiKit.full(this, c ? 12 : 18));
        LinearLayout dailyCard = metricCard("Daily Readiness", "กำลังตรวจ", "Auto start • Smart Battery • Self recovery");
        dailyValue = (TextView) dailyCard.getChildAt(1);
        LinearLayout batteryCard = metricCard("Battery", "กำลังตรวจ", "แนะนำ Unrestricted สำหรับงานเบื้องหลัง");
        batteryValue = (TextView) batteryCard.getChildAt(1);
        addResponsive(body, dailyCard, batteryCard, 8);

        LinearLayout sessionCard = metricCard("FineBI Session", "รอ Session", "Session เก็บเฉพาะใน RAM");
        sessionValue = (TextView) sessionCard.getChildAt(1);
        LinearLayout templateCard = metricCard("Export Template", "กำลังตรวจ", "ตั้งค่าครั้งแรกครั้งเดียว");
        templateValue = (TextView) templateCard.getChildAt(1);
        addResponsive(body, sessionCard, templateCard, 7);

        LinearLayout backendCard = metricCard("Backend", "-", "th_update_time ล่าสุด");
        backendValue = (TextView) backendCard.getChildAt(1);
        LinearLayout pollCard = metricCard("Smart Battery", "-", "ECO / FAST / OFFLINE");
        pollValue = (TextView) pollCard.getChildAt(1);
        addResponsive(body, backendCard, pollCard, 7);

        body.addView(UiKit.text(this, "ไฟล์ล่าสุด", 12, UiKit.MUTED, true), UiKit.full(this, 14));
        latestFile = UiKit.text(this, "ยังไม่มีไฟล์", c ? 12 : 14, UiKit.TEXT, true);
        latestFile.setSingleLine(true);
        latestFile.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        latestFile.setPadding(dp(12), dp(12), dp(12), dp(12));
        latestFile.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, this));
        body.addView(latestFile, UiKit.full(this, 7));

        primaryAction = UiKit.button(this, "เริ่ม Auto Export", true);
        primaryAction.setOnClickListener(v -> toggleAutoExport());
        body.addView(primaryAction, UiKit.full(this, 12));

        TextView finebi = UiKit.button(this, "เปิด FineBI", false);
        finebi.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        TextView flash = UiKit.button(this, "เปิด Flashlink", false);
        flash.setOnClickListener(v -> {
            if (!FlashlinkHelper.open(this)) {
                Toast.makeText(this, "เปิด Flashlink ไม่สำเร็จ", Toast.LENGTH_SHORT).show();
            }
        });
        addResponsive(body, finebi, flash, 8);

        batteryButton = UiKit.button(this, "ตั้งค่า Battery = Unrestricted", false);
        batteryButton.setOnClickListener(v -> BatteryHelper.requestUnrestricted(this));
        body.addView(batteryButton, UiKit.full(this, 7));

        footer = UiKit.text(this, "", c ? 10 : 12, UiKit.MUTED, false);
        footer.setPadding(dp(3), dp(11), dp(3), 0);
        body.addView(footer);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        setPage(scroll);
        refreshStatus();
    }

    private LinearLayout metricCard(String title, String value, String detail) {
        boolean c = compact();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c ? 12 : 14), dp(c ? 10 : 13), dp(c ? 12 : 14), dp(c ? 10 : 13));
        card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, this));
        card.addView(UiKit.text(this, title, c ? 11 : 12, UiKit.MUTED, false));
        TextView v = UiKit.text(this, value, c ? 14 : 15, UiKit.TEXT, true);
        v.setPadding(0, dp(3), 0, 0);
        card.addView(v);
        TextView d = UiKit.text(this, detail, c ? 10 : 11, UiKit.MUTED, false);
        d.setPadding(0, dp(2), 0, 0);
        card.addView(d);
        return card;
    }

    private void addResponsive(LinearLayout body, View a, View b, int topDp) {
        if (compact()) {
            body.addView(a, UiKit.full(this, topDp));
            body.addView(b, UiKit.full(this, 7));
        } else {
            body.addView(pair(a, b), UiKit.full(this, topDp));
        }
    }

    private LinearLayout pair(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.rightMargin = dp(5);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.leftMargin = dp(5);
        row.addView(a, p1);
        row.addView(b, p2);
        return row;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(5), dp(4), dp(5), dp(5));
        nav.setBackgroundColor(Color.WHITE);
        UiKit.elevation(nav, 8);
        TextView status = navButton("สถานะ");
        TextView finebi = navButton("FineBI");
        TextView files = navButton("ไฟล์");
        status.setOnClickListener(v -> showStatusPage());
        finebi.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        files.setOnClickListener(v -> showFilesPage());
        nav.addView(status, navLp());
        nav.addView(finebi, navLp());
        nav.addView(files, navLp());
        return nav;
    }

    private TextView navButton(String s) {
        TextView v = UiKit.text(this, s, compact() ? 12 : 13, UiKit.MUTED, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(6), dp(9), dp(6), dp(9));
        v.setClickable(true);
        return v;
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void showFilesPage() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(compact() ? 11 : 16), dp(10), dp(compact() ? 11 : 16), dp(10));
        body.addView(UiKit.text(this, "ไฟล์ Export", compact() ? 19 : 22, UiKit.TEXT, true));
        body.addView(UiKit.text(this, "เปิดดูไฟล์ที่บันทึกไว้ได้แม้ออฟไลน์", 11, UiKit.MUTED, false), UiKit.full(this, 2));

        List<ExportStore.Item> items = ExportStore.list(this, 100);
        ListView list = new ListView(this);
        list.setDivider(null);
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
                Toast.makeText(this, "ไม่พบแอปเปิด Excel", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setPage(body);
    }

    private void refreshStatus() {
        if (heroTitle == null) return;

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
        boolean batteryReady = BatteryHelper.isUnrestricted(this);
        long now = System.currentTimeMillis();
        boolean pollFresh = lastPollOk > 0L && now - lastPollOk <= 12 * 60_000L;
        boolean serviceFresh = heartbeat > 0L && now - heartbeat <= 12 * 60_000L;

        liveBadge.setText(online ? "LIVE" : "OFFLINE");
        liveBadge.setBackground(UiKit.rounded(online ? UiKit.GREEN : UiKit.AMBER, 20, this));

        sessionValue.setText(sessionReady ? "พร้อม" : (online ? "กำลังกู้คืน" : "รอออนไลน์"));
        sessionValue.setTextColor(sessionReady ? UiKit.GREEN : UiKit.AMBER);
        templateValue.setText(templateReady ? "พร้อม • จำไว้แล้ว" : "ตั้งค่าครั้งแรก");
        templateValue.setTextColor(templateReady ? UiKit.GREEN : UiKit.AMBER);
        backendValue.setText(backend == null || backend.isEmpty() ? "-" : backend);
        latestFile.setText(file == null || file.isEmpty() || "-".equals(file) ? "ยังไม่มีไฟล์" : file);

        pollValue.setText(pollMode == null || pollMode.isEmpty() ? "-" : pollMode);
        if ("FAST".equals(pollMode)) pollValue.setTextColor(UiKit.AMBER);
        else if ("ECO".equals(pollMode) || "SYNC".equals(pollMode)) pollValue.setTextColor(UiKit.GREEN);
        else pollValue.setTextColor(UiKit.TEXT);

        batteryValue.setText(batteryReady ? "UNRESTRICTED" : "ควรตั้งค่า");
        batteryValue.setTextColor(batteryReady ? UiKit.GREEN : UiKit.AMBER);
        batteryButton.setText(batteryReady ? "Battery = Unrestricted ✓" : "ตั้งค่า Battery = Unrestricted");

        boolean dailyReady = enabled
                && templateReady
                && flashInstalled
                && batteryReady
                && (online ? (pollFresh || sessionReady || "STARTING".equals(state) || "SESSION".equals(state)) : true)
                && errors < 3;

        if (dailyReady) {
            dailyValue.setText("พร้อมใช้ประจำวัน");
            dailyValue.setTextColor(UiKit.GREEN);
        } else if (!templateReady) {
            dailyValue.setText("ต้อง Setup ครั้งแรก");
            dailyValue.setTextColor(UiKit.AMBER);
        } else if (!batteryReady) {
            dailyValue.setText("ตั้ง Battery ก่อน");
            dailyValue.setTextColor(UiKit.AMBER);
        } else if (!enabled) {
            dailyValue.setText("กดเริ่ม Auto Export");
            dailyValue.setTextColor(UiKit.AMBER);
        } else if (!flashInstalled) {
            dailyValue.setText("ไม่พบ Flashlink");
            dailyValue.setTextColor(UiKit.RED);
        } else if (online && !serviceFresh && !pollFresh) {
            dailyValue.setText("กำลังกู้คืนระบบ");
            dailyValue.setTextColor(UiKit.AMBER);
        } else {
            dailyValue.setText("กำลังเตรียมพร้อม");
            dailyValue.setTextColor(UiKit.AMBER);
        }

        if (!templateReady) {
            heroTitle.setText("ตั้งค่าครั้งแรก 1 ครั้ง");
            heroSubtitle.setText("เปิด Flashlink → Login FineBI → Export Excel 1 ครั้ง จากนั้นใช้งานประจำวันได้");
        } else if (!batteryReady) {
            heroTitle.setText("ตั้ง Battery = Unrestricted");
            heroSubtitle.setText("ช่วยให้ Auto Export ทำงานต่อเมื่อปิดหน้าจอหรือไม่ได้เปิดแอปค้างไว้");
        } else if (!enabled) {
            heroTitle.setText("พร้อมเริ่มใช้งานประจำวัน");
            heroSubtitle.setText("กดเริ่มครั้งเดียว • ระบบจำสถานะและเริ่มใหม่หลังรีบูต/อัปเดตแอป");
        } else if (!online) {
            heroTitle.setText("ออฟไลน์ • ระบบกำลังรอ");
            heroSubtitle.setText("หยุด polling เพื่อประหยัดแบต • กลับมาทำงานเองเมื่อเครือข่ายกลับมา");
        } else if (dailyReady) {
            heroTitle.setText("พร้อมใช้ประจำวัน");
            heroSubtitle.setText("Auto Export ทำงานเบื้องหลัง • ไม่ต้องเปิดหน้าจอแอปค้างไว้");
        } else {
            heroTitle.setText("ระบบกำลังกู้คืน/เตรียมพร้อม");
            heroSubtitle.setText(message == null || message.isEmpty() ? state : message);
        }

        primaryAction.setText(enabled ? "หยุด Auto Export" : "เริ่ม Auto Export");
        primaryAction.setBackground(UiKit.rounded(enabled ? UiKit.RED : UiKit.BLUE, 12, this));

        StringBuilder f = new StringBuilder();
        f.append("Auto start: หลังเปิดเครื่องและหลังอัปเดตแอป");
        if (nextCheck > 0L) f.append("\nตรวจครั้งถัดไป: ").append(formatTime(nextCheck));
        if (lastPollOk > 0L) f.append("\nFineBI ตอบล่าสุด: ").append(formatTime(lastPollOk));
        if (errors > 0) f.append("\nRetry/Error ต่อเนื่อง: ").append(errors);
        if (message != null && !message.isEmpty()) f.append("\n").append(message);
        footer.setText(f.toString());
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
                    NetworkHelper.isOnline(this) ? "Daily Auto Export เริ่มทำงาน" : "เปิด Auto Export แล้ว • รอออนไลน์",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAutoExport() {
        startService(new Intent(this, AutoExportService.class).setAction(AutoExportService.ACTION_STOP));
        Toast.makeText(this, "หยุด Auto Export แล้ว", Toast.LENGTH_SHORT).show();
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
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    private final class FileAdapter extends BaseAdapter {
        private final List<ExportStore.Item> items;
        FileAdapter(List<ExportStore.Item> items) { this.items = items; }
        @Override public int getCount() { return items.isEmpty() ? 1 : items.size(); }
        @Override public Object getItem(int p) { return items.isEmpty() ? null : items.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int p, View cv, ViewGroup parent) {
            if (items.isEmpty()) {
                TextView e = UiKit.text(DailyMainActivity.this, "ยังไม่มีไฟล์ Export", 14, UiKit.MUTED, false);
                e.setGravity(Gravity.CENTER);
                e.setPadding(dp(20), dp(36), dp(20), dp(36));
                return e;
            }

            ExportStore.Item item = items.get(p);
            LinearLayout card = new LinearLayout(DailyMainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(11), dp(12), dp(11));
            card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, DailyMainActivity.this));

            TextView name = UiKit.text(DailyMainActivity.this, item.name, compact() ? 12 : 13, UiKit.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            card.addView(name);

            TextView date = UiKit.text(DailyMainActivity.this, formatTime(item.modified), 10, UiKit.MUTED, false);
            date.setPadding(0, dp(3), 0, 0);
            card.addView(date);

            LinearLayout wrap = new LinearLayout(DailyMainActivity.this);
            wrap.setPadding(0, 0, 0, dp(8));
            wrap.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return wrap;
        }
    }
}
