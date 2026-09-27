package com.oai.thaiscreentranslate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateChecker {
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/Flashdevnak/finebi-auto-export-android/refs/heads/chatgpt/thai-screen-translate-build/updates/thai-screen-translate.json";

    private UpdateChecker() {}

    public static void check(Activity activity, boolean userRequested) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "ThaiScreenTranslate-Android");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("HTTP " + code);
                }

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                int latestCode = json.getInt("versionCode");
                String latestName = json.getString("versionName");
                String apkUrl = json.getString("apkUrl");
                String notes = json.optString("notes", "");

                PackageInfo pi = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0);
                long currentCode = android.os.Build.VERSION.SDK_INT >= 28
                        ? pi.getLongVersionCode()
                        : pi.versionCode;

                activity.runOnUiThread(() -> {
                    if (latestCode > currentCode) {
                        new AlertDialog.Builder(activity)
                                .setTitle("มีอัปเดต " + latestName)
                                .setMessage(notes.isEmpty()
                                        ? "พบเวอร์ชันใหม่ พร้อมดาวน์โหลด"
                                        : notes)
                                .setNegativeButton("ไว้ทีหลัง", null)
                                .setPositiveButton("ดาวน์โหลด", (d, w) -> {
                                    Intent i = new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(apkUrl));
                                    activity.startActivity(i);
                                })
                                .show();
                    } else if (userRequested) {
                        Toast.makeText(activity,
                                "เป็นเวอร์ชันล่าสุดแล้ว",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (userRequested) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                    "ตรวจอัปเดตไม่สำเร็จ: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "update-check").start();
    }
}
