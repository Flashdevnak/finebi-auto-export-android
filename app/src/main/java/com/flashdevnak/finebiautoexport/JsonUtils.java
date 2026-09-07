package com.flashdevnak.finebiautoexport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public final class JsonUtils {
    public static final class NormalizeResult {
        public final String body;
        public final int forceAllCount;

        NormalizeResult(String body, int forceAllCount) {
            this.body = body;
            this.forceAllCount = forceAllCount;
        }
    }

    private JsonUtils() {}

    public static NormalizeResult normalizeFineBiBody(
            String rawJson,
            String currentSessionId,
            boolean forceHubSelectAll
    ) throws JSONException {
        JSONObject root = new JSONObject(rawJson);
        Counter counter = new Counter();
        walk(root, currentSessionId, forceHubSelectAll, counter);
        return new NormalizeResult(root.toString(), counter.value);
    }

    private static void walk(
            Object node,
            String currentSessionId,
            boolean forceHubSelectAll,
            Counter counter
    ) throws JSONException {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;

            if (obj.has("sessionId") && currentSessionId != null && !currentSessionId.isEmpty()) {
                obj.put("sessionId", currentSessionId);
            }

            if (forceHubSelectAll && obj.has(FineBiConfig.HUB_FILTER_WIDGET_ID)) {
                Object candidate = obj.opt(FineBiConfig.HUB_FILTER_WIDGET_ID);
                if (candidate instanceof JSONObject) {
                    JSONObject widget = (JSONObject) candidate;
                    JSONObject value = widget.optJSONObject("value");
                    if (value != null) {
                        value.put("type", 2);
                        value.put("value", new JSONArray());
                        counter.value++;
                    }
                }
            }

            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                walk(obj.opt(key), currentSessionId, forceHubSelectAll, counter);
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                walk(arr.opt(i), currentSessionId, forceHubSelectAll, counter);
            }
        }
    }

    private static final class Counter {
        int value;
    }
}
