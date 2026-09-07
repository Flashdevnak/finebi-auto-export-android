package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

public final class WebViewFactory {
    private WebViewFactory() {}

    public static WebView create(Context context, FineBiWebViewClient.Listener listener) {
        Context webContext = context;
        if (!(context instanceof Activity)) {
            webContext = new ContextThemeWrapper(
                    context,
                    android.R.style.Theme_Material_Light
            );
        }
        WebView webView = new WebView(webContext);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new FineBiWebViewClient(listener));
        return webView;
    }
}
