package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
    private TextView disconnectButton;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiKit.BG);
        root.addView(buildAppBar());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(26));

        body.addView(UiKit.text(this, "การส่งรายงานทางอีเมล", 22, UiKit.TEXT, true));
        TextView intro = UiKit.text(this,
                "ส่งไฟล์ XLSX อัตโนมัติหลังจาก Export และตรวจสอบข้อมูลสำเร็จ",
                12,
                UiKit.MUTED,
                false);
        intro.setPadding(0, dp(6), 0, 0);
        body.addView(intro);

        addSection(body,
                "บัญชีผู้ส่ง",
                "เชื่อมต่อบัญชี Google ที่ต้องการใช้ส่งรายงาน",
                20);

        LinearLayout googleCard = new LinearLayout(this);
        googleCard.setOrientation(LinearLayout.VERTICAL);
        googleCard.setPadding(dp(15), dp(14), dp(15), dp(14));
        googleCard.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 16, this));

        TextView accountLabel = UiKit.text(this, "Google Account", 11, UiKit.MUTED, false);
        googleCard.addView(accountLabel);
        googleStatus = UiKit.text(this, googleStatusText(), 15, UiKit.TEXT, true);
        googleStatus.setPadding(0, dp(5), 0, 0);
        googleCard.addView(googleStatus);
        TextView security = UiKit.text(this,
                "แอปใช้สิทธิ์สำหรับส่งอีเมลเท่านั้น และไม่ต้องใช้รหัสผ่าน Gmail",
                10,
                UiKit.MUTED,
                false);
        security.setPadding(0, dp(5), 0, 0);
        googleCard.addView(security);
        body.addView(googleCard, UiKit.full(this, 9));

        connectButton = UiKit.button(this, connectButtonText(), true);
        connectButton.setOnClickListener(v -> connectGoogle());
        body.addView(connectButton, UiKit.full(this, 9));

        disconnectButton = UiKit.button(this, "ยกเลิกการเชื่อมต่อบัญชี", false);
        disconnectButton.setOnClickListener(v -> GoogleOAuthManager.disconnect(this, (ok, message) ->
                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    refreshGoogleUi();
                    status.setText(currentStatus());
                    status.setTextColor(ok ? UiKit.MUTED : UiKit.RED);
                })));
        body.addView(disconnectButton, UiKit.full(this, 8));

        addSection(body,
                "ผู้รับรายงาน",
                "กำหนดผู้รับหลักและสำเนาอีเมล",
                20);

        body.addView(fieldLabel("ผู้รับหลัก"), UiKit.full(this, 9));
        to = field("example@company.com หรือหลายอีเมลคั่นด้วย ,");
        to.setText(MailSettings.get(this).getString(MailSettings.TO, ""));
        body.addView(to, UiKit.full(this, 6));

        body.addView(fieldLabel("สำเนา (CC)"), UiKit.full(this, 12));
        cc = field("ไม่จำเป็นต้องกรอก");
        cc.setText(MailSettings.get(this).getString(MailSettings.CC, ""));
        body.addView(cc, UiKit.full(this, 6));

        addSection(body,
                "การส่งอัตโนมัติ",
                "เมื่อเปิดใช้งาน ระบบจะส่งรายงานหลังบันทึกไฟล์สำเร็จโดยไม่ส่งซ้ำรอบเดิม",
                20);

        LinearLayout switchCard = new LinearLayout(this);
        switchCard.setOrientation(LinearLayout.VERTICAL);
        switchCard.setPadding(dp(15), dp(12), dp(15), dp(12));
        switchCard.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 16, this));

        enabled = new Switch(this);
        enabled.setText("ส่งรายงานอัตโนมัติ");
        enabled.setTextSize(15);
        enabled.setTextColor(UiKit.TEXT);
        enabled.setChecked(MailSettings.get(this).getBoolean(MailSettings.ENABLED, false));
        switchCard.addView(enabled);

        TextView switchHint = UiKit.text(this,
                "ระบบจะใช้บัญชีผู้ส่งและรายชื่อผู้รับที่บันทึกไว้ในหน้านี้",
                10,
                UiKit.MUTED,
                false);
        switchHint.setPadding(0, dp(3), 0, 0);
        switchCard.addView(switchHint);
        body.addView(switchCard, UiKit.full(this, 9));

        TextView save = UiKit.button(this, "บันทึกการตั้งค่า", true);
        save.setOnClickListener(v -> saveSettings(true));
        body.addView(save, UiKit.full(this, 16));

        TextView test = UiKit.button(this, "ส่งอีเมลทดสอบ", false);
        test.setOnClickListener(v -> {
            if (!saveSettings(false)) return;
            status.setText("กำลังส่งอีเมลทดสอบ...");
            status.setTextColor(UiKit.AMBER);
            MailManager.sendTest(this, (ok, message) -> runOnUiThread(() -> {
                status.setText(message);
                status.setTextColor(ok ? UiKit.GREEN : UiKit.RED);
                refreshGoogleUi();
            }));
        });
        body.addView(test, UiKit.full(this, 9));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusCard.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 14, this));
        statusCard.addView(UiKit.text(this, "สถานะ", 11, UiKit.MUTED, false));
        status = UiKit.text(this, currentStatus(), 12, UiKit.TEXT, true);
        status.setPadding(0, dp(4), 0, 0);
        statusCard.addView(status);
        body.addView(statusCard, UiKit.full(this, 14));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        setContentView(root);
        refreshGoogleUi();
    }

    private View buildAppBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        bar.setBackgroundColor(UiKit.NAVY);

        TextView back = UiKit.text(this, "‹  ภาพรวม", 13, Color.WHITE, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(11), dp(8), dp(11), dp(8));
        back.setBackground(UiKit.rounded(Color.argb(42, 255, 255, 255), 11, this));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(UiKit.text(this, "Auto Email", 16, Color.WHITE, true));
        TextView sub = UiKit.text(this,
                "ตั้งค่าผู้ส่งและผู้รับรายงาน",
                10,
                Color.rgb(203, 213, 225),
                false);
        sub.setPadding(0, dp(2), 0, 0);
        labels.addView(sub);
        bar.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        return bar;
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

    private TextView fieldLabel(String text) {
        return UiKit.text(this, text, 11, UiKit.MUTED, true);
    }

    private void connectGoogle() {
        boolean reauthorize = MailSettings.authRequired(this)
                && MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);

        connectButton.setText(reauthorize ? "กำลังยืนยันสิทธิ์..." : "กำลังเปิด Google...");
        googleStatus.setText(reauthorize ? "กำลังยืนยันสิทธิ์บัญชีเดิม" : "กำลังรอการยืนยันบัญชี");
        googleStatus.setTextColor(UiKit.AMBER);

        GoogleOAuthManager.ConnectCallback callback = new GoogleOAuthManager.ConnectCallback() {
            @Override public void onConnected(String email) {
                runOnUiThread(() -> {
                    refreshGoogleUi();
                    status.setText(reauthorize
                            ? "ยืนยันสิทธิ์บัญชี Google สำเร็จ"
                            : "เชื่อมต่อบัญชี Google สำเร็จ");
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
        };

        if (reauthorize) GoogleOAuthManager.reconnect(this, callback);
        else GoogleOAuthManager.connect(this, callback);
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
        boolean connected = MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);
        boolean authRequired = MailSettings.authRequired(this);
        googleStatus.setText(googleStatusText());
        googleStatus.setTextColor(authRequired ? UiKit.AMBER : (connected ? UiKit.GREEN : UiKit.AMBER));
        connectButton.setText(connectButtonText());
        if (disconnectButton != null) {
            disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        }
        if (status != null) {
            status.setText(currentStatus());
            status.setTextColor(authRequired ? UiKit.AMBER : UiKit.TEXT);
        }
    }

    private String googleStatusText() {
        boolean connected = MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);
        if (!connected) return "ยังไม่ได้เชื่อมต่อบัญชีผู้ส่ง";
        String email = MailSettings.get(this).getString(MailSettings.GOOGLE_EMAIL, "");
        String account = email == null || email.isEmpty() ? "เชื่อมต่อบัญชีแล้ว" : email;
        if (MailSettings.authRequired(this)) return account + " • ต้องยืนยันสิทธิ์อีกครั้ง";
        return account;
    }

    private String connectButtonText() {
        if (MailSettings.authRequired(this)) return "ยืนยันสิทธิ์บัญชี Google";
        return MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false)
                ? "เปลี่ยนบัญชีผู้ส่ง"
                : "เชื่อมต่อบัญชี Google";
    }

    private boolean saveSettings(boolean toast) {
        String t = to.getText().toString().trim();
        String c = cc.getText().toString().trim();
        boolean connected = MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);
        if (enabled.isChecked() && (!connected || t.isEmpty())) {
            Toast.makeText(this,
                    !connected ? "กรุณาเชื่อมต่อบัญชี Google ก่อน" : "กรุณากรอกอีเมลผู้รับหลัก",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        MailSettings.saveRecipients(this, enabled.isChecked(), t, c);
        if (toast) Toast.makeText(this, "บันทึกการตั้งค่าแล้ว", Toast.LENGTH_SHORT).show();
        status.setText(currentStatus());
        status.setTextColor(MailSettings.authRequired(this) ? UiKit.AMBER : UiKit.TEXT);
        if (MailSettings.enabled(this)) MailManager.kick(this);
        return true;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(UiKit.TEXT);
        e.setHintTextColor(UiKit.MUTED);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setSingleLine(true);
        e.setMinHeight(dp(52));
        e.setPadding(dp(13), dp(9), dp(13), dp(9));
        e.setBackground(UiKit.outlined(Color.WHITE, UiKit.BORDER, 12, this));
        return e;
    }

    private String currentStatus() {
        String s = MailSettings.get(this).getString(MailSettings.STATUS, "");
        boolean connected = MailSettings.get(this).getBoolean(MailSettings.GOOGLE_CONNECTED, false);
        if (!connected) return "รอเชื่อมต่อบัญชีผู้ส่ง";
        if (MailSettings.authRequired(this)) return "บัญชีผู้ส่งยังถูกบันทึกไว้ • กรุณายืนยันสิทธิ์ Google อีกครั้ง";
        if (!MailSettings.configured(this)) return "เชื่อมต่อบัญชีแล้ว • รอกำหนดผู้รับรายงาน";
        if (!MailSettings.get(this).getBoolean(MailSettings.ENABLED, false)) {
            return "ตั้งค่าครบแล้ว • ปิดการส่งอัตโนมัติ";
        }
        if ("SENDING".equals(s)) return "กำลังส่งรายงาน";
        if (s != null && s.startsWith("RETRY")) return "กำลังรอส่งใหม่";
        return "พร้อมส่งรายงานอัตโนมัติ";
    }

    private int dp(int v) { return UiKit.dp(this, v); }
}
