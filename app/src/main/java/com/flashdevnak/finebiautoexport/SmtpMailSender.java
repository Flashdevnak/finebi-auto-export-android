package com.flashdevnak.finebiautoexport;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class SmtpMailSender {
    private SmtpMailSender() {}

    public static void send(
            Context context,
            String subject,
            String body,
            String attachmentName,
            byte[] attachment
    ) throws Exception {
        String sender = MailSettings.get(context).getString(MailSettings.SENDER, "").trim();
        String password = MailSettings.appPassword(context);
        List<String> to = parseRecipients(MailSettings.get(context).getString(MailSettings.TO, ""));
        List<String> cc = parseRecipients(MailSettings.get(context).getString(MailSettings.CC, ""));
        if (sender.isEmpty() || password.isEmpty() || to.isEmpty()) {
            throw new IllegalStateException("ตั้งค่า Gmail/ผู้รับยังไม่ครบ");
        }

        SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket("smtp.gmail.com", 465);
        socket.setSoTimeout(30_000);
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
            expect(in, 220);
            command(out, in, "EHLO finebi-auto-export", 250);
            command(out, in, "AUTH LOGIN", 334);
            command(out, in, b64(sender), 334);
            command(out, in, b64(password), 235);
            command(out, in, "MAIL FROM:<" + sender + ">", 250);
            for (String r : to) command(out, in, "RCPT TO:<" + r + ">", 250, 251);
            for (String r : cc) command(out, in, "RCPT TO:<" + r + ">", 250, 251);
            command(out, in, "DATA", 354);

            String boundary = "----FineBIAutoExport" + System.currentTimeMillis();
            write(out, "From: " + sender);
            write(out, "To: " + String.join(", ", to));
            if (!cc.isEmpty()) write(out, "Cc: " + String.join(", ", cc));
            write(out, "Subject: " + encodedHeader(subject));
            write(out, "Date: " + rfc2822Now());
            write(out, "MIME-Version: 1.0");
            write(out, "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"");
            write(out, "");
            write(out, "--" + boundary);
            write(out, "Content-Type: text/plain; charset=UTF-8");
            write(out, "Content-Transfer-Encoding: base64");
            write(out, "");
            writeBase64Lines(out, body.getBytes(StandardCharsets.UTF_8));

            if (attachment != null && attachment.length > 0) {
                String safeName = attachmentName == null || attachmentName.isEmpty() ? "report.xlsx" : attachmentName;
                write(out, "--" + boundary);
                write(out, "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; name=\"" + safeName + "\"");
                write(out, "Content-Disposition: attachment; filename=\"" + safeName + "\"");
                write(out, "Content-Transfer-Encoding: base64");
                write(out, "");
                writeBase64Lines(out, attachment);
            }
            write(out, "--" + boundary + "--");
            write(out, ".");
            out.flush();
            expect(in, 250);
            command(out, in, "QUIT", 221);
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static List<String> parseRecipients(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split("[,;\\s]+")) {
            String v = s.trim();
            if (!v.isEmpty() && v.contains("@")) out.add(v);
        }
        return out;
    }

    private static void command(BufferedWriter out, BufferedReader in, String value, int... expected) throws Exception {
        write(out, value);
        out.flush();
        int code = readCode(in);
        for (int e : expected) if (code == e) return;
        throw new IllegalStateException("SMTP command failed: " + code);
    }

    private static void expect(BufferedReader in, int... expected) throws Exception {
        int code = readCode(in);
        for (int e : expected) if (code == e) return;
        throw new IllegalStateException("SMTP response: " + code);
    }

    private static int readCode(BufferedReader in) throws Exception {
        String line = in.readLine();
        if (line == null || line.length() < 3) throw new IllegalStateException("SMTP no response");
        int code = Integer.parseInt(line.substring(0, 3));
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) break;
            if (line.length() > 3 && line.startsWith(String.valueOf(code)) && line.charAt(3) == ' ') break;
        }
        return code;
    }

    private static void write(BufferedWriter out, String line) throws Exception {
        out.write(line == null ? "" : line);
        out.write("\r\n");
    }

    private static void writeBase64Lines(BufferedWriter out, byte[] data) throws Exception {
        String encoded = Base64.encodeToString(data, Base64.NO_WRAP);
        for (int i = 0; i < encoded.length(); i += 76) {
            write(out, encoded.substring(i, Math.min(encoded.length(), i + 76)));
        }
    }

    private static String b64(String s) {
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String encodedHeader(String s) {
        return "=?UTF-8?B?" + b64(s == null ? "" : s) + "?=";
    }

    private static String rfc2822Now() {
        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date());
    }
}
