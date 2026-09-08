package com.flashdevnak.finebiautoexport;

import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Gmail API sender using a short-lived OAuth access token. */
public final class GmailApiSender {
    private GmailApiSender() {}

    public static void send(
            String accessToken,
            List<String> to,
            List<String> cc,
            String subject,
            String body,
            String attachmentName,
            byte[] attachment
    ) throws Exception {
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalStateException("ไม่มี Google access token");
        }
        if (to == null || to.isEmpty()) {
            throw new IllegalStateException("ไม่มีผู้รับอีเมล");
        }

        String mime = buildMime(to, cc, subject, body, attachmentName, attachment);
        String raw = Base64.encodeToString(
                mime.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );

        JSONObject payload = new JSONObject();
        payload.put("raw", raw);

        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
        ).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("Accept", "application/json");

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(bytes);
        }

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            String response = read(c.getErrorStream());
            if (response.length() > 240) response = response.substring(0, 240);
            throw new GmailHttpException(code, response);
        }
        try (InputStream in = c.getInputStream()) {
            while (in.read() != -1) { /* consume */ }
        } finally {
            c.disconnect();
        }
    }

    private static String buildMime(
            List<String> to,
            List<String> cc,
            String subject,
            String body,
            String attachmentName,
            byte[] attachment
    ) {
        String boundary = "FineBIAutoExport_" + System.currentTimeMillis();
        StringBuilder s = new StringBuilder();
        line(s, "To: " + String.join(", ", to));
        if (cc != null && !cc.isEmpty()) line(s, "Cc: " + String.join(", ", cc));
        line(s, "Subject: " + encodedHeader(subject));
        line(s, "Date: " + rfc2822Now());
        line(s, "MIME-Version: 1.0");
        line(s, "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"");
        line(s, "");

        line(s, "--" + boundary);
        line(s, "Content-Type: text/plain; charset=UTF-8");
        line(s, "Content-Transfer-Encoding: base64");
        line(s, "");
        base64Lines(s, body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));

        if (attachment != null && attachment.length > 0) {
            String safeName = sanitizeFileName(
                    attachmentName == null || attachmentName.isEmpty() ? "report.xlsx" : attachmentName
            );
            line(s, "--" + boundary);
            line(s, "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; name=\"" + safeName + "\"");
            line(s, "Content-Disposition: attachment; filename=\"" + safeName + "\"");
            line(s, "Content-Transfer-Encoding: base64");
            line(s, "");
            base64Lines(s, attachment);
        }

        line(s, "--" + boundary + "--");
        return s.toString();
    }

    private static String encodedHeader(String value) {
        String v = value == null ? "" : value;
        return "=?UTF-8?B?" + Base64.encodeToString(
                v.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP
        ) + "?=";
    }

    private static void base64Lines(StringBuilder s, byte[] data) {
        String encoded = Base64.encodeToString(data, Base64.NO_WRAP);
        for (int i = 0; i < encoded.length(); i += 76) {
            line(s, encoded.substring(i, Math.min(encoded.length(), i + 76)));
        }
    }

    private static void line(StringBuilder s, String value) {
        s.append(value == null ? "" : value).append("\r\n");
    }

    private static String rfc2822Now() {
        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date());
    }

    private static String sanitizeFileName(String value) {
        return value.replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private static String read(InputStream in) {
        if (in == null) return "";
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) s.append(line);
        } catch (Exception ignored) {}
        return s.toString();
    }

    public static final class GmailHttpException extends Exception {
        public final int code;
        GmailHttpException(int code, String message) {
            super("Gmail API HTTP " + code + (message == null || message.isEmpty() ? "" : " • " + message));
            this.code = code;
        }
    }
}
