package com.eo1.slideshow;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles automatic app updates:
 * - Periodic version checks against server
 * - APK download via DownloadManager
 * - Installation trigger via system UI
 */
public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String SERVER_BASE = "http://93.127.216.80:3000";
    private static final long CHECK_INTERVAL = 4 * 60 * 60 * 1000; // 4 hours

    private Context context;
    private Handler handler;
    private Runnable periodicCheck;
    private long downloadId = -1;
    private BroadcastReceiver downloadReceiver;

    public UpdateManager(Context context) {
        this.context = context;
        this.handler = new Handler();
    }

    /**
     * Start periodic update checks
     */
    public void startPeriodicChecks() {
        periodicCheck = new Runnable() {
            @Override
            public void run() {
                checkForUpdate();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };

        // Initial check after 30 seconds (allow app to fully start)
        handler.postDelayed(periodicCheck, 30000);
    }

    /**
     * Stop periodic update checks
     */
    public void stopPeriodicChecks() {
        if (periodicCheck != null) {
            handler.removeCallbacks(periodicCheck);
        }
    }

    /**
     * Check server for available update
     */
    public void checkForUpdate() {
        Log.i(TAG, "Checking for updates...");
        new CheckUpdateTask().execute();
    }

    /**
     * Simple JSON parser for update response (no external dependencies)
     */
    private static class UpdateInfo {
        boolean updateAvailable;
        String downloadUrl;
        int latestVersion;

        static UpdateInfo parse(String json) {
            UpdateInfo info = new UpdateInfo();
            info.updateAvailable = json.contains("\"update_available\":true");

            // Extract download_url
            int urlStart = json.indexOf("\"download_url\":\"");
            if (urlStart != -1) {
                urlStart += 16;
                int urlEnd = json.indexOf("\"", urlStart);
                if (urlEnd != -1) {
                    info.downloadUrl = json.substring(urlStart, urlEnd);
                }
            }

            // Extract latest_version
            int verStart = json.indexOf("\"latest_version\":");
            if (verStart != -1) {
                verStart += 17;
                int verEnd = json.indexOf(",", verStart);
                if (verEnd == -1) verEnd = json.indexOf("}", verStart);
                if (verEnd != -1) {
                    try {
                        info.latestVersion = Integer.parseInt(
                            json.substring(verStart, verEnd).trim()
                        );
                    } catch (NumberFormatException e) {
                        info.latestVersion = 0;
                    }
                }
            }

            return info;
        }
    }

    /**
     * AsyncTask to check for updates in background
     */
    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {
        @Override
        protected UpdateInfo doInBackground(Void... params) {
            HttpURLConnection conn = null;
            try {
                int currentVersion = BuildConfig.VERSION_CODE;
                URL url = new URL(SERVER_BASE + "/api/app/version?v=" + currentVersion);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                    );
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    return UpdateInfo.parse(response.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return null;
        }

        @Override
        protected void onPostExecute(UpdateInfo info) {
            if (info != null && info.updateAvailable && info.downloadUrl != null) {
                Log.i(TAG, "Update available: v" + info.latestVersion);
                downloadUpdate(SERVER_BASE + info.downloadUrl);
            } else {
                Log.i(TAG, "No update available");
            }
        }
    }

    /**
     * Download APK using DownloadManager
     */
    private void downloadUpdate(String apkUrl) {
        Log.i(TAG, "Downloading update from: " + apkUrl);

        // Clean up any previous download
        File apkFile = getApkFile();
        if (apkFile.exists()) {
            apkFile.delete();
        }

        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE
            );

            DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse(apkUrl)
            );

            request.setTitle("EO1 Update");
            request.setDescription("Downloading app update...");
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE
            );

            // Download to external cache (visible to system installer)
            File destDir = context.getExternalCacheDir();
            if (destDir == null) {
                destDir = context.getCacheDir();
            }

            File destFile = new File(destDir, "eo1-update.apk");
            request.setDestinationUri(Uri.fromFile(destFile));

            // Allow download over any network
            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI |
                DownloadManager.Request.NETWORK_MOBILE
            );

            downloadId = dm.enqueue(request);

            // Register receiver for download completion
            registerDownloadReceiver();

        } catch (Exception e) {
            Log.e(TAG, "Download failed to start", e);
        }
    }

    /**
     * Get the APK file location
     */
    private File getApkFile() {
        File dir = context.getExternalCacheDir();
        if (dir == null) {
            dir = context.getCacheDir();
        }
        return new File(dir, "eo1-update.apk");
    }

    /**
     * Register receiver for download completion
     */
    private void registerDownloadReceiver() {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                // Ignore if not registered
            }
        }

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID, -1
                );
                if (id == downloadId) {
                    onDownloadComplete();
                }
            }
        };

        context.registerReceiver(
            downloadReceiver,
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        );
    }

    /**
     * Called when download completes
     */
    private void onDownloadComplete() {
        Log.i(TAG, "Download complete");

        DownloadManager dm = (DownloadManager) context.getSystemService(
            Context.DOWNLOAD_SERVICE
        );

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);

        Cursor cursor = dm.query(query);
        if (cursor.moveToFirst()) {
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);

            if (statusIndex != -1) {
                int status = cursor.getInt(statusIndex);

                if (status == DownloadManager.STATUS_SUCCESSFUL && uriIndex != -1) {
                    String localUri = cursor.getString(uriIndex);
                    Log.i(TAG, "APK downloaded to: " + localUri);

                    // Trigger installation
                    installApk(Uri.parse(localUri));
                } else {
                    Log.e(TAG, "Download failed with status: " + status);
                }
            }
        }
        cursor.close();

        // Cleanup receiver
        try {
            context.unregisterReceiver(downloadReceiver);
        } catch (Exception e) {
            // Ignore
        }
        downloadReceiver = null;
    }

    /**
     * Trigger APK installation via system UI
     * On Android 4.4, this requires user interaction
     */
    private void installApk(Uri apkUri) {
        Log.i(TAG, "Installing APK: " + apkUri);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch installer", e);
        }
    }

    /**
     * Cleanup when activity/service is destroyed
     */
    public void cleanup() {
        stopPeriodicChecks();
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
