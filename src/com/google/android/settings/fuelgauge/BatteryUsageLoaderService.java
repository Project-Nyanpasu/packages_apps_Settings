package com.google.android.settings.fuelgauge;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.BatteryStatsManager;
import android.os.BatteryUsageStats;
import android.os.BatteryUsageStatsQuery;
import android.util.Log;

import androidx.core.app.JobIntentService;

import com.android.settings.SettingsActivity;
import com.android.settings.fuelgauge.BatteryAppListPreferenceController;
import com.android.settings.fuelgauge.BatteryEntry;

import java.util.List;

public class BatteryUsageLoaderService extends JobIntentService {

    private static final String TAG = "BatteryUsageLoaderService";

    private static final Intent JOB_INTENT = new Intent("action.LOAD_BATTERY_USAGE_DATA");
    public static BatteryAppListPreferenceController mController;

    public static void enqueueWork(final Context context) {
        AsyncTask.execute(() -> {
            Log.d(TAG, "loadUsageDataSafely() in the AsyncTask");
            loadUsageDataSafely(context);
        });
    }

    @Override
    protected void onHandleWork(Intent intent) {
        Log.d(TAG, "onHandleWork: load usage data");
        loadUsageDataSafely(this);
    }

    private static void loadUsageDataSafely(Context context) {
        try {
            loadUsageData(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "load usage data:" + e);
        }
    }

    public static void loadUsageData(Context context) {
        if (!DatabaseUtils.isContentProviderEnabled(context)) {
            Log.w(TAG, "battery usage content provider is disabled!");
            return;
        }

        long startTime = System.currentTimeMillis();
        BatteryUsageStats batteryUsageStats = context.getSystemService(BatteryStatsManager.class)
                .getBatteryUsageStats(new BatteryUsageStatsQuery.Builder()
                .includeBatteryHistory().build());
    
        if (batteryUsageStats == null) {
            Log.w(TAG, "getBatteryUsageStats() returns null content");
        }

        if (mController == null) {
            mController = new BatteryAppListPreferenceController(
                    context, null, null, null, null);
        }

        List<BatteryEntry> batteryEntryList = batteryUsageStats != null
                ? mController.getBatteryEntryList(batteryUsageStats, true)
                : null;

        if (batteryEntryList == null || batteryEntryList.isEmpty()) {
            Log.w(TAG, "getBatteryEntryList() returns null or empty content");
        }

        Log.d(TAG, String.format("getBatteryUsageStats() in %d/ms",
                System.currentTimeMillis() - startTime));
        DatabaseUtils.sendBatteryEntryData(context, batteryEntryList, batteryUsageStats);
        if (batteryUsageStats != null) {
            try {
                batteryUsageStats.close();
            } catch (Exception e) {
                Log.e(TAG, "BatteryUsageStats.close() failed", e);
            }
        }
    }
}
