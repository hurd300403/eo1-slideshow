package com.eo1.slideshow;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Handles device ID storage in SharedPreferences.
 * Allows single APK to work across all devices.
 */
public class DeviceConfig {
    private static final String PREFS_NAME = "eo1_config";
    private static final String KEY_DEVICE_ID = "device_id";

    private SharedPreferences prefs;

    public DeviceConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get stored device ID
     * @return device ID (e.g., "living-room") or null if not set
     */
    public String getDeviceId() {
        return prefs.getString(KEY_DEVICE_ID, null);
    }

    /**
     * Store device ID
     * @param deviceId the device identifier (e.g., "living-room", "bedroom")
     */
    public void setDeviceId(String deviceId) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
    }

    /**
     * Check if device has been configured
     */
    public boolean isConfigured() {
        return getDeviceId() != null;
    }

    /**
     * Clear stored device ID (for reset)
     */
    public void clear() {
        prefs.edit().remove(KEY_DEVICE_ID).apply();
    }
}
