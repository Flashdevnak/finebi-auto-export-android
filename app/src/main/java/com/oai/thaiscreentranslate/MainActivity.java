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

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_NOTIFY = 1002;

    private MediaProjectionManager projectionManager;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(buildUi());

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFY);
        }

        UpdateChecker.check(this, false);
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

        TextView version = new TextView(this);
        version.setText("v1.2.0 • แปลทั้งหน้าจอ + ตรวจสถานะ OCR");
        version.setTextSize(14);
        version.setTextColor(Color.GRAY);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(6), 0, dp(12));
        root.addView(version, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("สถานะ: พร้อมเริ่ม");
        status.setTextSize(16);
        status.setTextColor(Color.rgb(210, 95, 0));
        status.setPadding(0, dp(8), 0, dp(16));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button overlay = button("1) อนุญาตแสดงทับแอปอื่น");
        overlay.setOnClickListener(v -> requestOverlay());
        root.addView(overlay, lp());

        Button test = button("2) ทดสอบโมเดลแปลจีน → ไทย");
        test.setOnClickListener(v -> testTranslation());
        root.addView(test, lp());

        Button start = button("3) เริ่มแปลทั้งหน้าจอ");
        start.setOnClickListener(v -> startTranslator());
        root.addView(start, lp());

        Button stop = button("หยุดแปล");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, ScreenTranslateService.class);
            i.setAction(ScreenTranslateService.ACTION_STOP);
            startService(i);
            status.setText("สถานะ: หยุดแล้ว");
        });
        root.addView(stop, lp());

        Button update = button("ตรวจสอบอัปเดต");
        update.setOnClickListener(v -> UpdateChecker.check(this, true));
        root.addView(update, lp());

        TextView note = new TextView(this);
        note.setText(
                "เมื่อเริ่มแล้ว จะมีแถบสีส้มเล็ก ๆ บนหน้าจอบอกสถานะจริง เช่น\n" +
                "• OCR ทำงาน • ไม่พบข้อความจีน\n" +
                "• พบจีน 8 จุด • กำลังเตรียมโมเดลแปล\n" +
                "• พบจีน 8 • แปล 8\n\n" +
                "ถ้าเห็นแถบสถานะ แต่ไม่มีคำแปล ให้แคปหน้าจอแถบนั้นส่งมาได้ทันที");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(18), 0, 0);
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
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, dp(58));
        p.setMargins(0, dp(7), 0, dp(7));
        return p;
    }

    private void requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "อนุญาตแสดงทับแอปอื่นแล้ว",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void testTranslation() {
        status.setText("สถานะ: กำลังเตรียมโมเดลทดสอบ…");

        TranslatorOptions opts = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.THAI)
                .build();

        Translator t = Translation.getClient(opts);
        t.downloadModelIfNeeded()
                .addOnSuccessListener(v ->
                        t.translate("设置")
                                .addOnSuccessListener(th -> {
                                    status.setText(
                                            "สถานะ: โมเดลแปลใช้ได้ → 设置 = " + th);
                                    Toast.makeText(this,
                                            "โมเดลแปลทำงาน: " + th,
                                            Toast.LENGTH_LONG).show();
                                    t.close();
                                })
                                .addOnFailureListener(e -> {
                                    status.setText(
                                            "สถานะ: แปลทดสอบไม่สำเร็จ: " + e.getMessage());
                                    t.close();
                                }))
                .addOnFailureListener(e -> {
                    status.setText(
                            "สถานะ: โหลดโมเดลไม่สำเร็จ: " + e.getMessage());
                    t.close();
                });
    }

    private void startTranslator() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "กรุณาอนุญาตแสดงทับแอปอื่นก่อน",
                    Toast.LENGTH_LONG).show();
            requestOverlay();
            return;
        }

        status.setText("สถานะ: รออนุญาตจับภาพหน้าจอ");
        startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAPTURE &&
                resultCode == RESULT_OK &&
                data != null) {
            Intent svc =
                    new Intent(this, ScreenTranslateService.class);
            svc.setAction(ScreenTranslateService.ACTION_START);
            svc.putExtra(
                    ScreenTranslateService.EXTRA_RESULT_CODE,
                    resultCode);
            svc.putExtra(
                    ScreenTranslateService.EXTRA_RESULT_DATA,
                    data);

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }

            status.setText("สถานะ: เริ่มแล้ว — ดูแถบสีส้มบนหน้าจอ");
            Toast.makeText(this,
                    "เริ่มแปลทั้งหน้าจอแล้ว",
                    Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        }
    }

    private int dp(int v) {
        return Math.round(
                v * getResources().getDisplayMetrics().density);
    }
}
