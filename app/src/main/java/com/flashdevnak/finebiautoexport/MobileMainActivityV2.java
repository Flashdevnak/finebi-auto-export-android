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

public final class MobileMainActivityV2 extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout content;
    private TextView liveBadge, heroTitle, heroSubtitle, sessionValue, templateValue, backendValue, latestFile, primaryAction, footer;

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
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
        labels.addView(UiKit.text(this, "HUB Departure Monitor", c ? 10 : 12, Color.rgb(191,201,216), false));
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
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        heroTitle = UiKit.text(this, "กำลังตรวจสถานะ", c ? 19 : 22, Color.WHITE, true);
        heroSubtitle = UiKit.text(this, "FineBI Auto Export", c ? 11 : 13, Color.rgb(219,234,254), false);
        heroSubtitle.setPadding(0, dp(4), 0, 0);
        hero.addView(heroTitle);
        hero.addView(heroSubtitle);
        body.addView(hero);

        body.addView(UiKit.text(this, "สถานะระบบ", 12, UiKit.MUTED, true), UiKit.full(this, c ? 12 : 18));

        LinearLayout c1 = metricCard("FineBI Session", "รอ Session", "Session เก็บเฉพาะใน RAM");
        sessionValue = (TextView) c1.getChildAt(1);
        LinearLayout c2 = metricCard("Export Template", "กำลังตรวจ", "จำไว้ในเครื่อง ไม่ต้องกดซ้ำ");
        templateValue = (TextView) c2.getChildAt(1);
        LinearLayout c3 = metricCard("Backend", "-", "th_update_time ล่าสุด");
        backendValue = (TextView) c3.getChildAt(1);
        LinearLayout c4 = metricCard("HUB Filter", "SELECT ALL", "บังคับก่อน Export ทุกครั้ง");
        ((TextView)c4.getChildAt(1)).setTextColor(UiKit.BLUE);

        if (c) {
            body.addView(c1, UiKit.full(this, 8));
            body.addView(c2, UiKit.full(this, 7));
            body.addView(c3, UiKit.full(this, 7));
            body.addView(c4, UiKit.full(this, 7));
        } else {
            body.addView(pair(c1,c2), UiKit.full(this, 8));
            body.addView(pair(c3,c4), UiKit.full(this, 8));
        }

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
        flash.setOnClickListener(v -> { if (!FlashlinkHelper.open(this)) Toast.makeText(this, "เปิด Flashlink ไม่สำเร็จ", Toast.LENGTH_SHORT).show(); });
        if (c) {
            body.addView(finebi, UiKit.full(this, 8));
            body.addView(flash, UiKit.full(this, 7));
        } else {
            body.addView(pair(finebi, flash), UiKit.full(this, 8));
        }

        TextView battery = UiKit.button(this, "Battery: Unrestricted", false);
        battery.setOnClickListener(v -> openBatterySettings());
        body.addView(battery, UiKit.full(this, 7));

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

    private LinearLayout pair(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); p1.rightMargin = dp(5);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); p2.leftMargin = dp(5);
        row.addView(a,p1); row.addView(b,p2);
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
        nav.addView(status, navLp()); nav.addView(finebi, navLp()); nav.addView(files, navLp());
        return nav;
    }

    private TextView navButton(String s) {
        TextView v = UiKit.text(this, s, compact() ? 12 : 13, UiKit.MUTED, true);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(6), dp(9), dp(6), dp(9)); v.setClickable(true); return v;
    }
    private LinearLayout.LayoutParams navLp() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }

    private void showFilesPage() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(compact()?11:16), dp(10), dp(compact()?11:16), dp(10));
        body.addView(UiKit.text(this, "ไฟล์ Export", compact()?19:22, UiKit.TEXT, true));
        body.addView(UiKit.text(this, "เปิดดูไฟล์ที่บันทึกไว้ได้แม้ออฟไลน์", 11, UiKit.MUTED, false), UiKit.full(this, 2));
        List<ExportStore.Item> items = ExportStore.list(this, 100);
        ListView list = new ListView(this); list.setDivider(null); list.setAdapter(new FileAdapter(items));
        list.setOnItemClickListener((p,v,pos,id) -> {
            if (pos >= items.size()) return;
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(items.get(pos).uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i);
            } catch (Exception e) { Toast.makeText(this, "ไม่พบแอปเปิด Excel", Toast.LENGTH_SHORT).show(); }
        });
        body.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
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
            heroSubtitle.setText(templateReady ? "Template จำไว้แล้ว • ไม่ต้องกด Export ซ้ำ" : "หลังติดตั้งครั้งแรกเท่านั้น: Login และ Export Excel 1 ครั้ง");
        }
        primaryAction.setText(enabled ? "หยุด Auto Export" : "เริ่ม Auto Export");
        primaryAction.setBackground(UiKit.rounded(enabled ? UiKit.RED : UiKit.BLUE, 12, this));
        StringBuilder f = new StringBuilder();
        if (!online) f.append("โหมดออฟไลน์: เปิดแอปและไฟล์ XLSX ได้ แต่ FineBI/Export ต้องมีเครือข่ายและ Flashlink");
        else { f.append("สถานะ: ").append(state); if (message != null && !message.isEmpty()) f.append(" • ").append(message); }
        if (templateReady) f.append("\nExport Template ถูกจำในเครื่องแล้ว • ไม่ต้องกด Export ก่อนเริ่มทุกครั้ง");
        footer.setText(f.toString());
    }

    private void toggleAutoExport() { if (Prefs.get(this).getBoolean(Prefs.ENABLED,false)) stopAutoExport(); else startAutoExport(true); }
    private void startAutoExport(boolean toast) {
        Prefs.get(this).edit().putBoolean(Prefs.ENABLED,true).apply();
        Intent i = new Intent(this, AutoExportService.class).setAction(AutoExportService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        if (toast) Toast.makeText(this, NetworkHelper.isOnline(this)?"Auto Export เริ่มทำงาน":"เปิด Auto Export แล้ว • รอออนไลน์", Toast.LENGTH_SHORT).show();
    }
    private void stopAutoExport() {
        startService(new Intent(this, AutoExportService.class).setAction(AutoExportService.ACTION_STOP));
        Toast.makeText(this, "หยุด Auto Export แล้ว", Toast.LENGTH_SHORT).show();
    }
    private void openBatterySettings() {
        try { Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); i.setData(Uri.parse("package:"+getPackageName())); startActivity(i); } catch (Exception ignored) {}
    }
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1001);
    }
    private int dp(int v) { return UiKit.dp(this,v); }
    private static String formatTime(long ms) { return ms<=0?"-":new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ms)); }

    private final class FileAdapter extends BaseAdapter {
        private final List<ExportStore.Item> items;
        FileAdapter(List<ExportStore.Item> items) { this.items=items; }
        @Override public int getCount() { return items.isEmpty()?1:items.size(); }
        @Override public Object getItem(int p) { return items.isEmpty()?null:items.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            if (items.isEmpty()) {
                TextView e = UiKit.text(MobileMainActivityV2.this, "ยังไม่มีไฟล์ Export", 14, UiKit.MUTED, false);
                e.setGravity(Gravity.CENTER); e.setPadding(dp(20),dp(36),dp(20),dp(36)); return e;
            }
            ExportStore.Item item = items.get(p);
            LinearLayout card = new LinearLayout(MobileMainActivityV2.this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(11),dp(12),dp(11)); card.setBackground(UiKit.outlined(Color.WHITE,UiKit.BORDER,12,MobileMainActivityV2.this));
            TextView name = UiKit.text(MobileMainActivityV2.this,item.name,compact()?12:13,UiKit.TEXT,true);
            name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.MIDDLE); card.addView(name);
            TextView date = UiKit.text(MobileMainActivityV2.this,formatTime(item.modified),10,UiKit.MUTED,false); date.setPadding(0,dp(3),0,0); card.addView(date);
            LinearLayout wrap = new LinearLayout(MobileMainActivityV2.this); wrap.setPadding(0,0,0,dp(8));
            wrap.addView(card,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); return wrap;
        }
    }
}
