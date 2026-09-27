package com.oai.thaiscreentranslate;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_NOTIFY = 1002;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("แปลหน้าจอเป็นไทย");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("แปลข้อความภาษาจีนทั่วทั้งหน้าจอแบบต่อเนื่อง ไม่จำกัดเฉพาะซับด้านล่าง\n\nครั้งแรกต้องดาวน์โหลดโมเดลแปลภาษา และ Android จะถามอนุญาตจับภาพหน้าจอ");
        info.setTextSize(16);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0, dp(18), 0, dp(22));
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        Button overlay = button("1) อนุญาตแสดงทับแอปอื่น");
        overlay.setOnClickListener(v -> requestOverlay());
        root.addView(overlay, lp());

        Button start = button("2) เริ่มแปลทั้งหน้าจอ");
        start.setOnClickListener(v -> startTranslator());
        root.addView(start, lp());

        Button stop = button("หยุดแปล");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, ScreenTranslateService.class);
            i.setAction(ScreenTranslateService.ACTION_STOP);
            startService(i);
        });
        root.addView(stop, lp());

        TextView note = new TextView(this);
        note.setText("การทำงาน: จับภาพเต็มจอ → OCR ภาษาจีน → แปลจีน→ไทยบนเครื่อง → วางคำแปลทับตำแหน่งเดิม\n\nสแกนประมาณทุก 0.7 วินาที และจำประโยคที่เคยแปลเพื่อลดความหน่วง\n\nบางแอป/วิดีโอที่ป้องกันการจับภาพ (FLAG_SECURE/DRM) จะไม่สามารถแปลได้");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(20), 0, 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.setMargins(0, dp(8), 0, dp(8));
        return p;
    }

    private void requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "อนุญาตแล้ว", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void startTranslator() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "กรุณาอนุญาตแสดงทับแอปอื่นก่อน", Toast.LENGTH_LONG).show();
            requestOverlay();
            return;
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent svc = new Intent(this, ScreenTranslateService.class);
            svc.setAction(ScreenTranslateService.ACTION_START);
            svc.putExtra(ScreenTranslateService.EXTRA_RESULT_CODE, resultCode);
            svc.putExtra(ScreenTranslateService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
            Toast.makeText(this, "เริ่มแปลทั้งหน้าจอแล้ว", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
