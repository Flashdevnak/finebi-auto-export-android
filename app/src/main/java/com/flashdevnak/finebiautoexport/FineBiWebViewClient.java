package com.flashdevnak.finebiautoexport;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Map;

public final class FineBiWebViewClient extends WebViewClient {
    public interface Listener {
        void onPageStarted(String url);
        void onPageFinished(String url);
        void onSessionCaptured();
        void onMainFrameError(String description);
    }

    private final Listener listener;

    public FineBiWebViewClient(Listener listener) {
        this.listener = listener;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (uri != null && "finebi.flashbi.club".equalsIgnoreCase(uri.getHost())) {
            Map<String, String> headers = request.getRequestHeaders();
            if (SessionStore.capture(headers) && listener != null) {
                listener.onSessionCaptured();
            }
        }
        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (listener != null) listener.onPageStarted(url);
        super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        optimizeLoginForTouch(view);
        if (listener != null) listener.onPageFinished(url);
        super.onPageFinished(view, url);
    }

    private void optimizeLoginForTouch(WebView view) {
        if (view == null) return;
        String js = "(function(){try{" +
                "var pw=document.querySelector('input[type=password]');if(!pw)return;" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                "m.content='width=device-width,initial-scale=1,maximum-scale=3,user-scalable=yes';" +
                "if(document.getElementById('__finebi_touch_fix'))return;" +
                "var s=document.createElement('style');s.id='__finebi_touch_fix';" +
                "s.textContent='" +
                "input[type=text],input[type=password],input:not([type]){min-height:48px!important;font-size:16px!important;box-sizing:border-box!important;touch-action:manipulation!important;}" +
                "button,input[type=submit],[role=button],.login-button,.login-btn,[class*=login][class*=button],[class*=login][class*=btn]{min-height:52px!important;min-width:140px!important;padding:12px 18px!important;font-size:16px!important;line-height:20px!important;touch-action:manipulation!important;-webkit-tap-highlight-color:rgba(37,99,235,.18)!important;}" +
                "input,button,[role=button]{touch-action:manipulation!important;}" +
                "';document.head.appendChild(s);" +
                "}catch(e){}})();";
        view.evaluateJavascript(js, null);
    }

    @Override
    public void onReceivedError(
            WebView view,
            WebResourceRequest request,
            android.webkit.WebResourceError error
    ) {
        if (request.isForMainFrame() && listener != null) {
            listener.onMainFrameError(
                    error == null ? "WebView error" : String.valueOf(error.getDescription())
            );
        }
        super.onReceivedError(view, request, error);
    }
}
