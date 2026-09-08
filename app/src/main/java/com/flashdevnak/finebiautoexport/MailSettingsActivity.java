package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MailSettingsActivity extends Activity {
    private EditText to;
    private EditText cc;
    private Switch enabled;
    private TextView googleStatus;
    private TextView connectButton;
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

        body.addView(UiKit.text(this, "ส่งอีเมลอัตโนมัติ", 22, UiKit.TEXT, true));
        TextView sub = UiKit.text(this,
                "Google OAuth + Gmail API • ไม่ใช้รหัส Gmail และไม่ใช้ App Password • ขอสิทธิ์ gmail.send เท่านั้น",
                12, UiKit.MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(10));
        body.addView(sub);

        LinearLayout googleCard = new LinearLayout(this);
        googleCard.setOrientation(LinearLayout.VERTICAL);
        googleCard.setPadding(dp(13), dp(12), dp(13), dp(12));
        googleCard.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, this));
        googleCard.addView(UiKit.text(this, "บัญชี Google", 12, UiKit.MUTED, false));
        googleStatus = UiKit.text(this, googleStatusText(), 15, UiKit.TEXT, true);
        googleStatus.setPadding(0, dp(4), 0, 0);
        googleCard.addView(googleStatus);
        body.addView(googleCard, UiKit.full(this, 8));

        connectButton = UiKit.button(this, connectButtonText(), true);
        connectButton.setOnClickListener(v -> connectGoogle());
        body.addView(connectButton, UiKit.full(this, 8));

        TextView disconnect = UiKit.button(this, "ยกเลิกการเชื่อมต่อ Google", false);
        disconnect.setOnClickListener(v -> GoogleOAuthManager.disconnect(this, (ok, message) ->
                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    refreshGoogleUi();
                    status.setText(currentStatus());
                })));
        body.addView(disconnect, UiKit.full(this, 7));

        enabled = new Switch(this);
        enabled.setText("เปิดส่งอีเมลอัตโนมัติ");
        enabled.setTextSize(15);
        enabled.setChecked(MailSettings.get(this).getBoolean(MailSettings.ENABLED, false));
        body.addView(enabled, UiKit.full(this, 12));

        to = field("ผู้รับ (คั่นหลายคนด้วย ,)");
        to.setText(MailSettings.get(this).getString(MailSettings.TO, ""));
        body.addView(to, UiKit.full(this, 10));

        cc = field("CC (ไม่ใส่ก็ได้)");
        cc.setText(MailSettings.get(this).getString(MailSettings.CC, ""));
        body.addView(cc, UiKit.full(this, 9));

        TextView note = UiKit.text(this,
                "ครั้งแรกกด เชื่อมต่อ Google → เลือกบัญชี → อนุญาตส่งอีเมล หลังจากนั้นระบบขอ access token แบบสั้นจาก Google เมื่อจำเป็นและไม่บันทึก token ลงเครื่อง",
                11, UiKit.BLUE, false);
        note.setPadding(dp(2), dp(9), dp(2), 0);
        body.addView(note);

        TextView save = UiKit.button(this, "บันทึกการตั้งค่า", true);
        save.setOnClickListener(v -> saveSettings(true));
        body.addView(save, UiKit.full(this, 14));

        TextView test = UiKit.button(this, "ส่งอีเมลทดสอบ", false);
        test.setOnClickListener(v -> {
            if (!saveSettings(false)) return;
            status.setText("กำลังส่งอีเมลทดสอบ...");
            status.setTextColor(UiKit.AMBER);
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

    private void connectGoogle() {
        connectButton.setText("กำลังเปิด Google...");
        googleStatus.setText("กำลังขอสิทธิ์ Gmail send");
        googleStatus.setTextColor(UiKit.AMBER);
        GoogleOAuthManager.connect(this, new GoogleOAuthManager.ConnectCallback() {
            @Override public void onConnected(String email) {
                runOnUiThread(() -> {
                    refreshGoogleUi();
                    status.setText("เชื่อมต่อ Google สำเร็จ");
                    status.setTextColor(UiKit.GREEN);
                    MailManager.kick(MailSettingsActivity.this);
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    refreshGoogleUi();
                    status.setText(message);
                    status.setTextColor(UiKit.RED);
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (GoogleOAuthManager.handleActivityResult(this, requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override protected void onResume() {
        super.onResume();
        if (googleStatus != null) refreshGoogleUi();
    }

    private void refreshGoogleUi() {
        googleStatus.setText(googleStatusText());
        googleStatus.setTextColor(
                MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false)
                        ? UiKit.GREEN : UiKit.AMBER
        );
        connectButton.setText(connectButtonText());
    }

    private String googleStatusText() {
        boolean connected = MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);
        if (!connected) return "ยังไม่ได้เชื่อมต่อ";
        String email = MailSettings.get(this).getString(MailSettings.GOOGLE_EMAIL, "");
        return email == null || email.isEmpty() ? "เชื่อมต่อแล้ว" : "เชื่อมต่อแล้ว • " + email;
    }

    private String connectButtonText() {
        String s = MailSettings.get(this).getString(MailSettings.STATUS, "");
        if (s != null && s.startsWith("AUTH_REQUIRED")) return "เชื่อมต่อ Google ใหม่";
        return MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false)
                ? "เปลี่ยนบัญชี Google"
                : "เชื่อมต่อ Google";
    }

    private boolean saveSettings(boolean toast) {
        String t = to.getText().toString().trim();
        String c = cc.getText().toString().trim();
        boolean connected = MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);
        if (enabled.isChecked() && (!connected || t.isEmpty())) {
            Toast.makeText(this,
                    !connected ? "เชื่อมต่อ Google ก่อน" : "กรอกผู้รับอีเมลก่อน",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        MailSettings.saveRecipients(this, enabled.isChecked(), t, c);
        if (toast) Toast.makeText(this, "บันทึกแล้ว", Toast.LENGTH_SHORT).show();
        status.setText(currentStatus());
        status.setTextColor(UiKit.MUTED);
        if (MailSettings.enabled(this)) MailManager.kick(this);
        return true;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(UiKit.TEXT);
        e.setHintTextColor(UiKit.MUTED);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setSingleLine(true);
        e.setMinHeight(dp(52));
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 10, this));
        return e;
    }

    private String currentStatus() {
        String s = MailSettings.get(this).getString(MailSettings.STATUS, "");
        boolean connected = MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);
        if (!connected) return "สถานะ: รอเชื่อมต่อ Google";
        if (s != null && s.startsWith("AUTH_REQUIRED")) return "สถานะ: " + s;
        if (!MailSettings.configured(this)) return "สถานะ: เชื่อมต่อ Google แล้ว • รอกรอกผู้รับ";
        if (!MailSettings.get(this).getBoolean(MailSettings.ENABLED, false)) {
            return "สถานะ: ตั้งค่าแล้ว แต่ปิดการส่งอัตโนมัติ";
        }
        return "สถานะ: " + (s == null || s.isEmpty() || "GOOGLE_CONNECTED".equals(s)
                ? "พร้อมส่งอัตโนมัติ" : s);
    }

    private int dp(int v) { return UiKit.dp(this, v); }
}
