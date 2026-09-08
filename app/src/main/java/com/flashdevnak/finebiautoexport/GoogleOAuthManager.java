package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.common.api.Scope;

import java.util.Arrays;
import java.util.List;

/**
 * Google Identity Services authorization for Gmail send-only access.
 * Access tokens are short-lived and are never written to disk.
 */
public final class GoogleOAuthManager {
    public static final int REQUEST_AUTHORIZE = 7301;
    public static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final String EMAIL_SCOPE = "email";

    public interface TokenCallback {
        void onToken(String token);
        void onUserActionRequired(String message);
        void onError(String message);
    }

    public interface ConnectCallback {
        void onConnected(String email);
        void onError(String message);
    }

    public interface DisconnectCallback {
        void onDone(boolean ok, String message);
    }

    private static volatile ConnectCallback pendingConnectCallback;

    private GoogleOAuthManager() {}

    private static List<Scope> scopes() {
        return Arrays.asList(new Scope(GMAIL_SEND_SCOPE), new Scope(EMAIL_SCOPE));
    }

    private static AuthorizationRequest request(boolean forceAccountPicker) {
        AuthorizationRequest.Builder b = AuthorizationRequest.builder()
                .setRequestedScopes(scopes());
        if (forceAccountPicker) {
            b.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        }
        return b.build();
    }

    public static void connect(Activity activity, ConnectCallback callback) {
        pendingConnectCallback = callback;
        AuthorizationClient client = Identity.getAuthorizationClient(activity);
        client.authorize(request(true))
                .addOnSuccessListener(result -> handleInteractiveResult(activity, result))
                .addOnFailureListener(e -> finishConnectError(
                        "เชื่อมต่อ Google ไม่สำเร็จ: " + e.getClass().getSimpleName()
                ));
    }

    private static void handleInteractiveResult(
            Activity activity,
            AuthorizationResult result
    ) {
        if (result.hasResolution()) {
            PendingIntent pi = result.getPendingIntent();
            if (pi == null) {
                finishConnectError("Google ไม่คืนหน้าต่างอนุญาต");
                return;
            }
            try {
                activity.startIntentSenderForResult(
                        pi.getIntentSender(),
                        REQUEST_AUTHORIZE,
                        null,
                        0,
                        0,
                        0
                );
            } catch (IntentSender.SendIntentException e) {
                finishConnectError("เปิดหน้าต่าง Google ไม่สำเร็จ");
            }
            return;
        }
        finishConnected(activity, result);
    }

    public static boolean handleActivityResult(
            Activity activity,
            int requestCode,
            int resultCode,
            Intent data
    ) {
        if (requestCode != REQUEST_AUTHORIZE) return false;
        if (resultCode != Activity.RESULT_OK || data == null) {
            finishConnectError("ยกเลิกการเชื่อมต่อ Google");
            return true;
        }
        try {
            AuthorizationResult result = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(data);
            finishConnected(activity, result);
        } catch (Exception e) {
            finishConnectError("รับสิทธิ์ Google ไม่สำเร็จ: " + e.getClass().getSimpleName());
        }
        return true;
    }

    private static void finishConnected(Context context, AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null || token.isEmpty()) {
            finishConnectError("Google ไม่คืน access token");
            return;
        }

        String email = "";
        try {
            if (result.toGoogleSignInAccount() != null
                    && result.toGoogleSignInAccount().getEmail() != null) {
                email = result.toGoogleSignInAccount().getEmail();
            }
        } catch (Throwable ignored) {}

        MailSettings.markGoogleConnected(context, email);
        ConnectCallback cb = pendingConnectCallback;
        pendingConnectCallback = null;
        if (cb != null) cb.onConnected(email);
    }

    private static void finishConnectError(String message) {
        ConnectCallback cb = pendingConnectCallback;
        pendingConnectCallback = null;
        if (cb != null) cb.onError(message);
    }

    /**
     * Background/silent token request. If Google requires consent/account selection,
     * the caller is told to ask the user to reconnect from the app UI.
     */
    public static void getAccessTokenSilent(Context context, TokenCallback callback) {
        Context app = context.getApplicationContext();
        Identity.getAuthorizationClient(app)
                .authorize(request(false))
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        callback.onUserActionRequired("ต้องเปิดแอปแล้วกด เชื่อมต่อ Google อีกครั้ง");
                        return;
                    }
                    String token = result.getAccessToken();
                    if (token == null || token.isEmpty()) {
                        callback.onUserActionRequired("สิทธิ์ Gmail หมดอายุ • เชื่อมต่อ Google ใหม่");
                        return;
                    }
                    callback.onToken(token);
                })
                .addOnFailureListener(e -> callback.onError(
                        "Google authorization error: " + e.getClass().getSimpleName()
                ));
    }

    public static void disconnect(Context context, DisconnectCallback callback) {
        Context app = context.getApplicationContext();
        RevokeAccessRequest request = RevokeAccessRequest.builder()
                .setScopes(scopes())
                .build();
        Identity.getAuthorizationClient(app)
                .revokeAccess(request)
                .addOnSuccessListener(v -> {
                    MailSettings.markGoogleDisconnected(app);
                    if (callback != null) callback.onDone(true, "ยกเลิกการเชื่อมต่อ Google แล้ว");
                })
                .addOnFailureListener(e -> {
                    MailSettings.markGoogleDisconnected(app);
                    if (callback != null) callback.onDone(false,
                            "ยกเลิกสิทธิ์บน Google ไม่สำเร็จ แต่ล้างการเชื่อมต่อในแอปแล้ว");
                });
    }
}
