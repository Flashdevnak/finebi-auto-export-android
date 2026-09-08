package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MailSettingsActivity extends Activity {
    private EditText sender;
    private EditText to;
    private EditText cc;
    private EditText appPassword;
    private Switch enabled;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(UiKit.NAVY);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(24));
        body.setBackgroundColor(UiKit.BG);

        TextView title = UiKit.text(this, "ส่งอีเมลอัตโนมัติ", 22, UiKit.TEXT, true);
        body.addView(title);
        TextView sub = UiKit.text(this,
                "ส่งเฉพาะหลัง Export + Validate XLSX ผ่าน • App Password เก็บเข้ารหัสด้วย Android Keystore ในเครื่องนี้",
                12, UiKit.MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(10));
        body.addView(sub);

        enabled = new Switch(this);
        enabled.setText("เปิดส่งอีเมลอัตโนมัติ");
        enabled.setTextSize(15);
        enabled.setChecked(MailSettings.get(this).getBoolean(MailSettings.ENABLED, false));
        body.addView(enabled, UiKit.full(this, 8));

        sender = field("Gmail ผู้ส่ง", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        sender.setText(MailSettings.get(this).getString(MailSettings.SENDER, ""));
        body.addView(sender, UiKit.full(this, 10));

        to = field("ผู้รับ (คั่นหลายคนด้วย ,)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        to.setText(MailSettings.get(this).getString(MailSettings.TO, ""));
        body.addView(to, UiKit.full(this, 9));

        cc = field("CC (ไม่ใส่ก็ได้)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        cc.setText(MailSettings.get(this).getString(MailSettings.CC, ""));
        body.addView(cc, UiKit.full(this, 9));

        appPassword = field("Gmail App Password 16 หลัก", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        appPassword.setHint(MailSettings.configured(this) ? "บันทึกไว้แล้ว • เว้นว่างเพื่อใช้ของเดิม" : "กรอก App Password ไม่ใช่รหัส Gmail ปกติ");
        body.addView(appPassword, UiKit.full(this, 9));

        TextView note = UiKit.text(this,
                "เพื่อความปลอดภัย ห้ามใช้รหัสผ่าน Gmail ปกติ ให้สร้าง App Password จากบัญชี Google ที่เปิด 2-Step Verification แล้วกรอกในหน้านี้เท่านั้น",
                11, UiKit.AMBER, false);
        note.setPadding(dp(2), dp(8), dp(2), 0);
        body.addView(note);

        TextView save = UiKit.button(this, "บันทึกการตั้งค่า", true);
        save.setOnClickListener(v -> saveSettings(true));
        body.addView(save, UiKit.full(this, 14));

        TextView test = UiKit.button(this, "ส่งอีเมลทดสอบ", false);
        test.setOnClickListener(v -> {
            if (!saveSettings(false)) return;
            status.setText("กำลังส่งอีเมลทดสอบ...");
            MailManager.sendTest(this, (ok, message) -> runOnUiThread(() -> {
                status.setText(message);
                status.setTextColor(ok ? UiKit.GREEN : UiKit.RED);
            }));
        });
        body.addView(test, UiKit.full(this, 8));

        status = UiKit.text(this, currentStatus(), 12, UiKit.MUTED, false);
        status.setPadding(dp(2), dp(12), dp(2), 0);
        body.addView(status);

        TextView back = UiKit.button(this, "กลับ", false);
        back.setOnClickListener(v -> finish());
        body.addView(back, UiKit.full(this, 14));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private boolean saveSettings(boolean toast) {
        try {
            String s = sender.getText().toString().trim();
            String t = to.getText().toString().trim();
            String c = cc.getText().toString().trim();
            String p = appPassword.getText().toString();
            if (enabled.isChecked() && (s.isEmpty() || t.isEmpty()
                    || (p.trim().isEmpty() && !MailSettings.configured(this)))) {
                Toast.makeText(this, "กรอก Gmail, ผู้รับ และ App Password ให้ครบ", Toast.LENGTH_SHORT).show();
                return false;
            }
            MailSettings.save(this, enabled.isChecked(), s, t, c, p);
            appPassword.setText("");
            appPassword.setHint(MailSettings.configured(this)
                    ? "บันทึกไว้แล้ว • เว้นว่างเพื่อใช้ของเดิม"
                    : "Gmail App Password 16 หลัก");
            if (toast) Toast.makeText(this, "บันทึกแล้ว", Toast.LENGTH_SHORT).show();
            status.setText(currentStatus());
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "บันทึกไม่ได้: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(UiKit.TEXT);
        e.setHintTextColor(UiKit.MUTED);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setMinHeight(dp(52));
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 10, this));
        return e;
    }

    private String currentStatus() {
        String s = MailSettings.get(this).getString(MailSettings.STATUS, "");
        if (!MailSettings.configured(this)) return "สถานะ: ยังไม่ได้ตั้งค่า";
        if (!MailSettings.get(this).getBoolean(MailSettings.ENABLED, false)) return "สถานะ: ตั้งค่าแล้ว แต่ปิดการส่งอัตโนมัติ";
        return "สถานะ: " + (s.isEmpty() ? "พร้อมส่งอัตโนมัติ" : s);
    }

    private int dp(int v) { return UiKit.dp(this, v); }
}
