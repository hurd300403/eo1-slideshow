package com.eo1.slideshow;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private Handler handler;
    private static final String URL = "http://93.127.216.80:3000/d/hallway";
    private static final int RETRY_DELAY = 5000; // 5 seconds
    private static final int WIFI_CHECK_DELAY = 2000; // 2 seconds
    private boolean pageLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webview);

        // Settings for full resolution
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setInitialScale(0);
        webView.setBackgroundColor(0xFF000000);

        // WebViewClient with error handling and auto-retry
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("93.127.216.80")) {
                    pageLoaded = true;
                }
                hideSystemUI();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                pageLoaded = false;
                // Auto-retry on error
                scheduleRetry();
            }
        });

        hideSystemUI();

        // Wait for network then load
        waitForNetworkAndLoad();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void waitForNetworkAndLoad() {
        if (isNetworkAvailable()) {
            // Network available, load the page
            loadPage();
        } else {
            // No network yet, check again in 2 seconds
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    waitForNetworkAndLoad();
                }
            }, WIFI_CHECK_DELAY);
        }
    }

    private void loadPage() {
        pageLoaded = false;
        webView.loadUrl(URL);
    }

    private void scheduleRetry() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!pageLoaded) {
                    waitForNetworkAndLoad();
                }
            }
        }, RETRY_DELAY);
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
        // If page failed to load, retry
        if (!pageLoaded) {
            waitForNetworkAndLoad();
        }
    }

    @Override
    public void onBackPressed() {
        // Disable back button
    }
}
