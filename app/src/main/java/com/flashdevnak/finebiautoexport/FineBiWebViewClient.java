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
        if (listener != null) listener.onPageFinished(url);
        super.onPageFinished(view, url);
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
