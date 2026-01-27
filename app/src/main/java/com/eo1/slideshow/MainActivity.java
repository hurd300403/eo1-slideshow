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

    // Change this URL to your slideshow
    private static final String SLIDESHOW_URL = "http://93.127.216.80:3000/d/living-room";
    private static final int RETRY_DELAY = 5000; // 5 seconds
    private static final int LOAD_TIMEOUT = 30000; // 30 seconds

    private Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pageLoaded && webView != null) {
                webView.loadUrl(SLIDESHOW_URL);
                // Set another timeout
                handler.postDelayed(timeoutRunnable, LOAD_TIMEOUT);
            }
        }
    };

    private Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pageLoaded && webView != null) {
                // Page didn't load in time, retry
                webView.stopLoading();
                handler.postDelayed(retryRunnable, RETRY_DELAY);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        // Request fullscreen before setContentView
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Keep screen on and dismiss keyguard
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webview);
        setupWebView();

        // Hide system UI
        hideSystemUI();

        // Load the slideshow
        loadPage();
    }

    private void loadPage() {
        pageLoaded = false;
        webView.loadUrl(SLIDESHOW_URL);
        // Set timeout for initial load
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

        // Handle all URLs within WebView - never open external browser
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Always load in this WebView, never open external apps
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoaded = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoaded = true;
                handler.removeCallbacks(timeoutRunnable);
                handler.removeCallbacks(retryRunnable);
                hideSystemUI();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                // Error loading page, retry after delay
                pageLoaded = false;
                handler.removeCallbacks(timeoutRunnable);
                handler.postDelayed(retryRunnable, RETRY_DELAY);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Accept SSL errors (for self-signed certs if needed)
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // Make background black
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
            // If page wasn't loaded, try again
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
        // Disable back button to prevent accidental exits
    }
}
