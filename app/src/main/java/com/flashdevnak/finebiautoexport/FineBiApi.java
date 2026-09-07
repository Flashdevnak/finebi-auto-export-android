package com.flashdevnak.finebiautoexport;

import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class FineBiApi {
    public static final class Result {
        public final int code;
        public final byte[] bytes;
        public final String contentType;

        Result(int code, byte[] bytes, String contentType) {
            this.code = code;
            this.bytes = bytes;
            this.contentType = contentType;
        }

        public String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    private FineBiApi() {}

    public static Result post(String url, String jsonBody, int timeoutMs) throws Exception {
        if (!SessionStore.isReady()) {
            throw new IllegalStateException("FineBI session is not ready");
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoOutput(true);
        conn.setUseCaches(false);

        conn.setRequestProperty("Authorization", SessionStore.authorization());
        if (SessionStore.sessionId() != null && !SessionStore.sessionId().isEmpty()) {
            conn.setRequestProperty("sessionId", SessionStore.sessionId());
        }
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn.setRequestProperty("Origin", FineBiConfig.BASE_URL);
        conn.setRequestProperty("Referer", FineBiConfig.ENTRY_URL);

        String cookies = CookieManager.getInstance().getCookie(FineBiConfig.BASE_URL);
        if (cookies != null && !cookies.isEmpty()) {
            conn.setRequestProperty("Cookie", cookies);
        }

        byte[] requestBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(requestBytes.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBytes);
        }

        int code = conn.getResponseCode();
        InputStream raw = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        byte[] bytes = new byte[0];

        if (raw != null) {
            InputStream in = raw;
            String encoding = conn.getContentEncoding();
            if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                in = new GZIPInputStream(raw);
            }
            try (InputStream input = in;
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[32 * 1024];
                int n;
                while ((n = input.read(buffer)) >= 0) {
                    bos.write(buffer, 0, n);
                }
                bytes = bos.toByteArray();
            }
        }

        String contentType = conn.getContentType();
        conn.disconnect();
        return new Result(code, bytes, contentType == null ? "" : contentType);
    }
}
