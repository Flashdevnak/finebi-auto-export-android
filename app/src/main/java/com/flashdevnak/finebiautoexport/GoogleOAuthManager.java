package com.flashdevnak.finebiautoexport;

import android.accounts.Account;
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
 *
 * The selected Google account email is persisted by MailSettings and is used to
 * bind silent authorization requests to the same device account. This avoids
 * account ambiguity after an access token expires or after an app update.
 */
public final class GoogleOAuthManager {
    public static final int REQUEST_AUTHORIZE = 7301;
    public static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final String EMAIL_SCOPE = "email";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

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

    private static AuthorizationRequest request(boolean forceAccountPicker, String accountEmail) {
        AuthorizationRequest.Builder b = AuthorizationRequest.builder()
                .setRequestedScopes(scopes());

        if (forceAccountPicker) {
            b.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        } else if (accountEmail != null && !accountEmail.trim().isEmpty()) {
            b.setAccount(new Account(accountEmail.trim(), GOOGLE_ACCOUNT_TYPE));
        }
        return b.build();
    }

    /** Connect or deliberately change the sender account. */
    public static void connect(Activity activity, ConnectCallback callback) {
        authorizeInteractive(activity, request(true, null), callback);
    }

    /** Re-authorize the sender account already selected in the app. */
    public static void reconnect(Activity activity, ConnectCallback callback) {
        String email = MailSettings.get(activity).getString(MailSettings.GOOGLE_EMAIL, "");
        if (email == null || email.trim().isEmpty()) {
            connect(activity, callback);
            return;
        }
        authorizeInteractive(activity, request(false, email), callback);
    }

    private static void authorizeInteractive(
            Activity activity,
            AuthorizationRequest authorizationRequest,
            ConnectCallback callback
    ) {
        pendingConnectCallback = callback;
        AuthorizationClient client = Identity.getAuthorizationClient(activity);
        client.authorize(authorizationRequest)
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

        // Some re-authorization responses do not repeat the account email.
        // MailSettings preserves the previously selected sender in that case.
        MailSettings.markGoogleConnected(context, email);
        String resolvedEmail = MailSettings.get(context).getString(MailSettings.GOOGLE_EMAIL, "");
        ConnectCallback cb = pendingConnectCallback;
        pendingConnectCallback = null;
        if (cb != null) cb.onConnected(resolvedEmail);
    }

    private static void finishConnectError(String message) {
        ConnectCallback cb = pendingConnectCallback;
        pendingConnectCallback = null;
        if (cb != null) cb.onError(message);
    }

    /**
     * Background/silent token request bound to the sender account selected by
     * the user. If Google genuinely requires consent, the caller is told to ask
     * for a one-tap re-authorization instead of treating the account as removed.
     */
    public static void getAccessTokenSilent(Context context, TokenCallback callback) {
        Context app = context.getApplicationContext();
        String email = MailSettings.get(app).getString(MailSettings.GOOGLE_EMAIL, "");
        Identity.getAuthorizationClient(app)
                .authorize(request(false, email))
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        callback.onUserActionRequired("Google ต้องการยืนยันสิทธิ์การส่งอีเมลอีกครั้ง");
                        return;
                    }
                    String token = result.getAccessToken();
                    if (token == null || token.isEmpty()) {
                        callback.onUserActionRequired("Google ต้องการยืนยันสิทธิ์การส่งอีเมลอีกครั้ง");
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
        String email = MailSettings.get(app).getString(MailSettings.GOOGLE_EMAIL, "");
        RevokeAccessRequest.Builder builder = RevokeAccessRequest.builder()
                .setScopes(scopes());
        if (email != null && !email.trim().isEmpty()) {
            builder.setAccount(new Account(email.trim(), GOOGLE_ACCOUNT_TYPE));
        }
        Identity.getAuthorizationClient(app)
                .revokeAccess(builder.build())
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
