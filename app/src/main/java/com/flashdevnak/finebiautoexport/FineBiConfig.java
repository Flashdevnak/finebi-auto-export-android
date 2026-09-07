package com.flashdevnak.finebiautoexport;

import android.content.Context;

import org.json.JSONObject;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class FineBiConfig {
    public static final String ENTRY_URL =
            "https://finebi.flashbi.club/webroot/decision/v10/entry/access/a687d529-8062-4bfd-bb64-daf22e54c825?dashboardType=4";
    public static final String BASE_URL = "https://finebi.flashbi.club";
    public static final String FLASHLINK_PACKAGE = "com.eagleyun.sase";
    public static final String HUB_FILTER_WIDGET_ID = "9cc31d91df727c74";

    public final String updateUrl;
    public final String updateBody;
    public final String exportUrl;
    public final String exportBody;

    private FineBiConfig(String updateUrl, String updateBody, String exportUrl, String exportBody) {
        this.updateUrl = updateUrl;
        this.updateBody = updateBody;
        this.exportUrl = exportUrl;
        this.exportBody = exportBody;
    }

    public static FineBiConfig load(Context context) throws Exception {
        JSONObject update = new JSONObject(readAsset(context, "update_time_template.json"));
        StringBuilder exportRaw = new StringBuilder();
        for (int i = 0; ; i++) {
            String name = String.format(java.util.Locale.US, "excel_export_template.part%03d", i);
            try {
                exportRaw.append(readAsset(context, name));
            } catch (java.io.FileNotFoundException e) {
                break;
            }
        }
        if (exportRaw.length() == 0) {
            throw new IllegalStateException("Missing export template parts");
        }
        JSONObject export = new JSONObject(exportRaw.toString());
        return new FineBiConfig(
                update.getString("url"),
                update.getString("postData"),
                export.getString("url"),
                export.getString("postData")
        );
    }

    private static String readAsset(Context context, String name) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(name), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) >= 0) {
                sb.append(buffer, 0, n);
            }
        }
        return sb.toString();
    }
}
