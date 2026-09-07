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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MobileMainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private LinearLayout page;
    private TextView liveBadge;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView templateValue;
    private TextView sessionValue;
    private TextView backendValue;
    private TextView latestFile;
    private TextView primaryAction;
    private TextView footer;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStatus();
            ui.postDelayed(this, 1500L);
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
        if (Prefs.get(this).getBoolean(Prefs.ENABLED, false)) {
            startAutoExport(false);
        }
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

    private boolean compactPhone() {
        return getResources().getConfiguration().screenWidthDp < 600;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiKit.BG);
        root.addView(buildAppBar());
        showStatusPage();
        root.addView(buildBottomNav());
        setContentView(root);
    }

    private View buildAppBar() {
        boolean compact = compactPhone();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(compact ? 12 : 18), dp(compact ? 8 : 12),
                dp(compact ? 12 : 18), dp(compact ? 8 : 12));
        bar.setBackgroundColor(UiKit.NAVY);

        TextView mark = UiKit.text(this, "F", compact ? 16 : 18, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(UiKit.rounded(UiKit.BLUE, 11, this));
        int markSize = dp(compact ? 34 : 40);
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(markSize, markSize);
        markLp.rightMargin = dp(compact ? 9 : 12);
        bar.addView(mark, markLp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(UiKit.text(this, "FineBI Auto Export", compact ? 16 : 18, Color.WHITE, true));
        labels.addView(UiKit.text(this, "HUB Departure Monitor", compact ? 10 : 12,
                Color.rgb(191, 201, 216), false));
        bar.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        liveBadge = UiKit.text(this, "LIVE", compact ? 9 : 11, Color.WHITE, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(dp(8), dp(5), dp(8), dp(5));
        liveBadge.setBackground(UiKit.rounded(UiKit.GREEN, 20, this));
        bar.addView(liveBadge);
        return bar;
    }

    private void replacePage(View newPage) {
        if (page != null) root.removeView(page);
        page = (LinearLayout) newPage;
        root.addView(page, root.getChildCount() - 1,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void showStatusPage() {
        boolean compact = compactPhone();
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(compact ? 11 : 16), dp(compact ? 10 : 14),
                dp(compact ? 11 : 16), dp(18));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(compact ? 14 : 18), dp(compact ? 13 : 18),
                dp(compact ? 14 : 18), dp(compact ? 13 : 18));
        hero.setBackground(UiKit.gradient(Color.rgb(25, 45, 78), Color.rgb(35, 83, 169), 16, this));
        heroTitle = UiKit.text(this, "กำลังตรวจสถานะ", compact ? 19 : 22, Color.WHITE, true);
        heroSubtitle = UiKit.text(this, "FineBI Auto Export", compact ? 11 : 13,
                Color.rgb(219, 234, 254), false);
        heroSubtitle.setPadding(0, dp(4), 0, 0);
        hero.addView(heroTitle);
        hero.addView(heroSubtitle);
        body.addView(hero);

        TextView section = UiKit.text(this, "สถานะระบบ", 12, UiKit.MUTED, true);
        body.addView(section, UiKit.full(this, compact ? 12 : 18));

        LinearLayout c1 = metricCard("FineBI Session", "รอ Session", "Session เก็บเฉพาะใน RAM");
        sessionValue = (TextView) c1.getChildAt(1);
        LinearLayout c2 = metricCard("Export Template", "กำลังตรวจ", "จำไว้ในเครื่อง ไม่ต้องกดซ้ำ");
        templateValue = (TextView) c2.getChildAt(1);
        LinearLayout c3 = metricCard("Backend", "-", "th_update_time ล่าสุด");
        backendValue = (TextView) c3.getChildAt(1);
        LinearLayout c4 = metricCard("HUB Filter", "SELECT ALL", "บังคับก่อน Export ทุกครั้ง");
        ((TextView) c4.getChildAt(1)).setTextColor(UiKit.BLUE);

        if (compact) {
            body.addView(c1, UiKit.full(this, 8));
            body.addView(c2, UiKit.full(this, 7));
            body.addView(c3, UiKit.full(this, 7));
            body.addView(c4, UiKit.full(this, 7));
        } else {
            body.addView(pair(c1, c2), UiKit.full(this, 8));
            body.addView(pair(c3, c4), UiKit.full(this, 8));
        }

        TextView recent = UiKit.text(this, "ไฟล์ล่าสุด", 12, UiKit.MUTED, true);
        body.addView(recent, UiKit.full(this, 14));
        latestFile = UiKit.text(this, "ยังไม่มีไฟล์", compact ? 12 : 14, UiKit.TEXT, true);
        latestFile.setSingleLine(true);
        latestFile.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        latestFile.setPadding(dp(12), dp(12), dp(12), dp(12));
        latestFile.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, this));
        body.addView(latestFile, UiKit.full(this, 7));

        primaryAction = UiKit.button(this, "เริ่ม Auto Export", true);
        primaryAction.setOnClickListener(v -> toggleAutoExport());
        body.addView(primaryAction, UiKit.full(this, 12));

        TextView fineBi = UiKit.button(this, "เปิด FineBI", false);
        fineBi.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        TextView flash = UiKit.button(this, "เปิด Flashlink", false);
        flash.setOnClickListener(v -> {
            if (!FlashlinkHelper.open(this)) {
                Toast.makeText(this, "เปิด Flashlink ไม่สำเร็จ", Toast.LENGTH_SHORT).show();
            }
        });

        if (compact) {
            body.addView(fineBi, UiKit.full(this, 8));
            body.addView(flash, UiKit.full(this, 7));
        } else {
            body.addView(pair(fineBi, flash), UiKit.full(this, 8));
        }

        TextView battery = UiKit.button(this, "Battery: Unrestricted", false);
        battery.setOnClickListener(v -> openBatterySettings());
        body.addView(battery, UiKit.full(this, 7));

        footer = UiKit.text(this, "", compact ? 10 : 12, UiKit.MUTED, false);
        footer.setPadding(dp(3), dp(11), dp(3), 0);
        body.addView(footer);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        replacePage(holder);
    }

    private LinearLayout metricCard(String title, String value, String detail) {
        boolean compact = compactPhone();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(compact ? 12 : 14), dp(compact ? 10 : 13),
                dp(compact ? 12 : 14), dp(compact ? 10 : 13));
        card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, this));
        card.addView(UiKit.text(this, title, compact ? 11 : 12, UiKit.MUTED, false));
        TextView v = UiKit.text(this, value, compact ? 14 : 15, UiKit.TEXT, true);
        v.setPadding(0, dp(3), 0, 0);
        card.addView(v);
        TextView d = UiKit.text(this, detail, compact ? 10 : 11, UiKit.MUTED, false);
        d.setPadding(0, dp(2), 0, 0);
        card.addView(d);
        return card;
    }

    private LinearLayout pair(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a.rightMargin = dp(5);
        row.addView(left, a);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b.leftMargin = dp(5);
        row.addView(right, b);
        return row;
    }

    private void showFilesPage() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(compactPhone() ? 11 : 16), dp(10), dp(compactPhone() ? 11 : 16), dp(10));

        TextView title = UiKit.text(this, "ไฟล์ Export", compactPhone() ? 19 : 22, UiKit.TEXT, true);
        body.addView(title);
        TextView sub = UiKit.text(this, "เปิดดูไฟล์ที่บันทึกไว้ได้แม้ออฟไลน์", 11, UiKit.MUTED, false);
        body.addView(sub, UiKit.full(this, 2));

        List<ExportStore.Item> items = ExportStore.list(this, 100);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setAdapter(new FileAdapter(items));
        list.setOnItemClickListener((parent, view, position, id) -> {
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
        body.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        replacePage(body);
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

    private TextView navButton(String text) {
        TextView v = UiKit.text(this, text, compactPhone() ? 12 : 13, UiKit.MUTED, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(6), dp(9), dp(6), dp(9));
        v.setClickable(true);
        return v;
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void refreshStatus() {
        if (heroTitle == null) return;
        SharedPreferences p = Prefs.get(this);
        boolean enabled = p.getBoolean(Prefs.ENABLED, false);
        String state = p.getString(Prefs.SERVICE_STATE, "STOPPED");
        String message = p.getString(Prefs.MESSAGE, "");
        String backend = p.getString(Prefs.BACKEND_UPDATE, "-");
        String file = p.getString(Prefs.LAST_EXPORT_NAME, "-");
        boolean online = NetworkHelper.isOnline(this);
        boolean sessionReady = SessionStore.isReady();
        boolean templateReady = TemplateStore.isReady(this);

        liveBadge.setText(online ? "LIVE" : "OFFLINE");
        liveBadge.setBackground(UiKit.rounded(online ? UiKit.GREEN : UiKit.AMBER, 20, this));
        sessionValue.setText(sessionReady ? "พร้อม" : (online ? "รอ Session" : "รอออนไลน์"));
        sessionValue.setTextColor(sessionReady ? UiKit.GREEN : UiKit.AMBER);
        templateValue.setText(templateReady ? "พร้อม" : "ตั้งค่าครั้งแรก");
        templateValue.setTextColor(templateReady ? UiKit.GREEN : UiKit.AMBER);
        backendValue.setText(backend == null || backend.isEmpty() ? "-" : backend);
        latestFile.setText(file == null || file.isEmpty() || "-".equals(file) ? "ยังไม่มีไฟล์" : file);

        if (!online) {
            heroTitle.setText("ออฟไลน์ • แอปยังเปิดได้");
            heroSubtitle.setText("ดูไฟล์ย้อนหลังได้ • Auto Export จะรอเครือข่ายกลับมา");
        } else if (enabled && ("RUNNING".equals(state) || "EXPORTING".equals(state))) {
            heroTitle.setText("Auto Export กำลังทำงาน");
            heroSubtitle.setText("Backend ล่าสุด • " + backend);
        } else if (enabled) {
            heroTitle.setText("ระบบกำลังเตรียมพร้อม");
            heroSubtitle.setText(message == null || message.isEmpty() ? "กำลังเชื่อมต่อ FineBI" : message);
        } else {
            heroTitle.setText("พร้อมเริ่ม Auto Export");
            heroSubtitle.setText(templateReady
                    ? "Template จำไว้แล้ว • ไม่ต้องกด Export ซ้ำ"
                    : "หลังติดตั้งครั้งแรกเท่านั้น: Login และ Export Excel 1 ครั้ง");
        }

        primaryAction.setText(enabled ? "หยุด Auto Export" : "เริ่ม Auto Export");
        primaryAction.setBackground(UiKit.rounded(enabled ? UiKit.RED : UiKit.BLUE, 12, this));

        StringBuilder f = new StringBuilder();
        if (!online) {
            f.append("โหมดออฟไลน์: เปิดแอปและไฟล์ XLSX ได้ แต่ FineBI/Export ต้องมีเครือข่ายและ Flashlink");
        } else {
            f.append("สถานะ: ").append(state);
            if (message != null && !message.isEmpty()) f.append(" • ").append(message);
        }
        if (templateReady) f.append("\nExport Template ถูกจำในเครื่องแล้ว • ไม่ต้องกด Export ก่อนเริ่มทุกครั้ง");
        footer.setText(f.toString());
    }

    private void toggleAutoExport() {
        if (Prefs.get(this).getBoolean(Prefs.ENABLED, false)) stopAutoExport();
        else startAutoExport(true);
    }

    private void startAutoExport(boolean toast) {
        Prefs.get(this).edit().putBoolean(Prefs.ENABLED, true).apply();
        Intent i = new Intent(this, AutoExportService.class).setAction(AutoExportService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        if (toast) Toast.makeText(this,
                NetworkHelper.isOnline(this) ? "Auto Export เริ่มทำงาน" : "เปิด Auto Export แล้ว • รอออนไลน์",
                Toast.LENGTH_SHORT).show();
    }

    private void stopAutoExport() {
        Intent i = new Intent(this, AutoExportService.class).setAction(AutoExportService.ACTION_STOP);
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

    private int dp(int value) { return UiKit.dp(this, value); }

    private static String formatTime(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    private final class FileAdapter extends BaseAdapter {
        private final List<ExportStore.Item> items;
        FileAdapter(List<ExportStore.Item> items) { this.items = items; }
        @Override public int getCount() { return items.isEmpty() ? 1 : items.size(); }
        @Override public Object getItem(int position) { return items.isEmpty() ? null : items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (items.isEmpty()) {
                TextView empty = UiKit.text(MobileMainActivity.this, "ยังไม่มีไฟล์ Export", 14, UiKit.MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(20), dp(36), dp(20), dp(36));
                return empty;
            }
            ExportStore.Item item = items.get(position);
            LinearLayout card = new LinearLayout(MobileMainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(11), dp(12), dp(11));
            card.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, MobileMainActivity.this));
            TextView name = UiKit.text(MobileMainActivity.this, item.name, compactPhone() ? 12 : 13, UiKit.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            card.addView(name);
            TextView date = UiKit.text(MobileMainActivity.this, formatTime(item.modified), 10, UiKit.MUTED, false);
            date.setPadding(0, dp(3), 0, 0);
            card.addView(date);
            LinearLayout wrap = new LinearLayout(MobileMainActivity.this);
            wrap.setPadding(0, 0, 0, dp(8));
            wrap.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return wrap;
        }
    }
}
