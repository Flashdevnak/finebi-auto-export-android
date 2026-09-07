package com.flashdevnak.finebiautoexport;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

public final class TemplateStore {
    private static final String FILE_NAME = "finebi_export_template.json";

    public static final class ExportTemplate {
        public final String url;
        public final String body;

        ExportTemplate(String url, String body) {
            this.url = url;
            this.body = body;
        }
    }

    public static final class UpdateTemplate {
        public final String url;
        public final String body;

        UpdateTemplate(String url, String body) {
            this.url = url;
            this.body = body;
        }
    }

    private TemplateStore() {}

    public static boolean isReady(Context context) {
        File f = new File(context.getFilesDir(), FILE_NAME);
        return f.isFile() && f.length() > 1000;
    }

    public static synchronized void saveCaptured(
            Context context,
            String url,
            String body
    ) throws Exception {
        if (url == null || !url.contains("/export/excel")) {
            throw new IllegalArgumentException("Not a FineBI Excel export URL");
        }
        if (body == null || body.length() < 1000) {
            throw new IllegalArgumentException("Export body is empty/too small");
        }

        JSONObject root = new JSONObject(body);
        JSONObject widgets = root.optJSONObject("widgets");
        if (widgets == null || widgets.optJSONObject(FineBiConfig.UPDATE_WIDGET_ID) == null) {
            throw new IllegalStateException("Captured export is missing th_update_time widget");
        }

        clearSessionIds(root);

        JSONObject saved = new JSONObject();
        saved.put("url", url);
        saved.put("postData", root.toString());

        File target = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        Files.write(temp.toPath(), saved.toString().getBytes(StandardCharsets.UTF_8));
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Cannot replace export template");
        }
        if (!temp.renameTo(target)) {
            throw new IllegalStateException("Cannot finalize export template");
        }
    }

    public static synchronized ExportTemplate loadExport(Context context) throws Exception {
        JSONObject saved = loadSaved(context);
        return new ExportTemplate(
                saved.getString("url"),
                saved.getString("postData")
        );
    }

    public static synchronized UpdateTemplate loadUpdate(Context context) throws Exception {
        JSONObject saved = loadSaved(context);
        JSONObject exportRoot = new JSONObject(saved.getString("postData"));
        JSONObject widgets = exportRoot.getJSONObject("widgets");
        JSONObject sourceWidget = widgets.getJSONObject(FineBiConfig.UPDATE_WIDGET_ID);
        JSONObject update = new JSONObject(sourceWidget.toString());

        update.put("page", -1);
        update.put("realData", true);
        update.put("allData", false);
        update.put("measuresToGeoms", 1);
        update.put("chartBounds", new JSONObject().put("width", 371).put("height", 10));
        update.put("filterValues", new JSONArray());
        update.put("sessionId", "");
        update.put(
                "reportId",
                exportRoot.optString("reportId", FineBiConfig.REPORT_ID)
        );

        String reportId = exportRoot.optString("reportId", FineBiConfig.REPORT_ID);
        String url = FineBiConfig.BASE_URL
                + "/webroot/decision/v5/design/widget/data?engineType=2&reportId="
                + reportId
                + "&bi_entry_type=MOUNT";

        return new UpdateTemplate(url, update.toString());
    }

    public static synchronized void clear(Context context) {
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (f.exists()) f.delete();
    }

    private static JSONObject loadSaved(Context context) throws Exception {
        File f = new File(context.getFilesDir(), FILE_NAME);
        String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        return new JSONObject(raw);
    }

    private static void clearSessionIds(Object node) throws Exception {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (obj.has("sessionId")) {
                obj.put("sessionId", "");
            }
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                clearSessionIds(obj.opt(keys.next()));
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                clearSessionIds(arr.opt(i));
            }
        }
    }
}
