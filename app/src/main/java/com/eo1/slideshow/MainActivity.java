package com.eo1.slideshow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private Handler handler;
    private boolean pageLoaded = false;

    private static final String SLIDESHOW_URL = "http://93.127.216.80:3000/d/living-room";
    private static final int RETRY_DELAY = 5000;
    private static final int LOAD_TIMEOUT = 30000;

    private Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pageLoaded && webView != null) {
                loadPage();
            }
        }
    };

    private Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pageLoaded && webView != null) {
                webView.stopLoading();
                handler.postDelayed(retryRunnable, RETRY_DELAY);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webview);
        setupWebView();
        hideSystemUI();

        // Small delay to ensure WebView is ready
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadPage();
            }
        }, 500);
    }

    private void loadPage() {
        pageLoaded = false;
        handler.removeCallbacks(timeoutRunnable);
        handler.removeCallbacks(retryRunnable);

        // Load HTML that immediately redirects - this ensures WebView processes it internally
        String html = "<!DOCTYPE html><html><head>" +
            "<meta http-equiv=\"refresh\" content=\"0;url=" + SLIDESHOW_URL + "\">" +
            "<script>window.location.href='" + SLIDESHOW_URL + "';</script>" +
            "</head><body style=\"background:#000;\"></body></html>";

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);

        handler.postDelayed(timeoutRunnable, LOAD_TIMEOUT);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        // Allow mixed content (HTTP on HTTPS) for Android 5+
        try {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        } catch (NoSuchMethodError e) {
            // Android 4.4 doesn't have this method, ignore
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Always handle URLs internally - never open external browser
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Only mark as not loaded if it's not our target URL yet
                if (!url.contains("93.127.216.80")) {
                    pageLoaded = false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Only mark as loaded when we reach the actual slideshow
                if (url.contains("93.127.216.80")) {
                    pageLoaded = true;
                    handler.removeCallbacks(timeoutRunnable);
                    handler.removeCallbacks(retryRunnable);
                }
                hideSystemUI();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                pageLoaded = false;
                handler.removeCallbacks(timeoutRunnable);
                handler.postDelayed(retryRunnable, RETRY_DELAY);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.setBackgroundColor(0xFF000000);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (webView != null) {
            webView.onResume();
            if (!pageLoaded) {
                loadPage();
            }
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(retryRunnable);
        handler.removeCallbacks(timeoutRunnable);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Disable back button
    }
}
