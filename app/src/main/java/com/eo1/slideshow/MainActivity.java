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
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private DeviceConfig deviceConfig;
    private UpdateManager updateManager;

    // JavaScript interface for hardware control and app management
    public class EO1Interface {
        private Activity activity;
        private UpdateManager updateManager;
        private DeviceConfig deviceConfig;

        public EO1Interface(Activity activity, UpdateManager updateManager, DeviceConfig deviceConfig) {
            this.activity = activity;
            this.updateManager = updateManager;
            this.deviceConfig = deviceConfig;
        }

        @JavascriptInterface
        public void setBrightness(int percent) {
            final float brightness = Math.max(0.01f, percent / 100f);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    WindowManager.LayoutParams layout = activity.getWindow().getAttributes();
                    layout.screenBrightness = brightness;
                    activity.getWindow().setAttributes(layout);
                }
            });
        }

        @JavascriptInterface
        public void checkForUpdate() {
            if (updateManager != null) {
                updateManager.checkForUpdate();
            }
        }

        @JavascriptInterface
        public int getVersionCode() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public String getVersionName() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public String getDeviceId() {
            return deviceConfig != null ? deviceConfig.getDeviceId() : null;
        }

        @JavascriptInterface
        public void setDeviceId(final String deviceId) {
            if (deviceConfig != null && deviceId != null && !deviceId.isEmpty()) {
                deviceConfig.setDeviceId(deviceId);
                // Reload with new device ID
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((MainActivity) activity).reloadWithDeviceId();
                    }
                });
            }
        }

        @JavascriptInterface
        public void resetDevice() {
            if (deviceConfig != null) {
                deviceConfig.clear();
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((MainActivity) activity).reloadWithDeviceId();
                    }
                });
            }
        }
    }

    private Handler handler;
    private static final String SERVER_BASE = "http://93.127.216.80:3000";
    private static final int RETRY_DELAY = 5000;
    private static final int WIFI_CHECK_DELAY = 2000;
    private boolean pageLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        // Initialize device config and update manager
        deviceConfig = new DeviceConfig(this);
        updateManager = new UpdateManager(this);

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

        // Register JavaScript interface
        webView.addJavascriptInterface(
            new EO1Interface(this, updateManager, deviceConfig),
            "EO1"
        );

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
                scheduleRetry();
            }
        });

        hideSystemUI();

        // Wait for network then load
        waitForNetworkAndLoad();

        // Start periodic update checks
        updateManager.startPeriodicChecks();
    }

    /**
     * Get the URL to load based on device configuration
     */
    private String getTargetUrl() {
        String deviceId = deviceConfig.getDeviceId();
        if (deviceId != null && !deviceId.isEmpty()) {
            return SERVER_BASE + "/d/" + deviceId;
        } else {
            // No device configured, show setup page
            return SERVER_BASE + "/d/setup";
        }
    }

    /**
     * Reload the WebView with the current device ID
     */
    void reloadWithDeviceId() {
        pageLoaded = false;
        webView.loadUrl(getTargetUrl());
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void waitForNetworkAndLoad() {
        if (isNetworkAvailable()) {
            loadPage();
        } else {
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
        webView.loadUrl(getTargetUrl());
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
        if (!pageLoaded) {
            waitForNetworkAndLoad();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateManager != null) {
            updateManager.cleanup();
        }
    }

    @Override
    public void onBackPressed() {
        // Disable back button
    }
}
