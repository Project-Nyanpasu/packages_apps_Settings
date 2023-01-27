package com.google.android.settings.fuelgauge;

import static com.android.settings.fuelgauge.ConvertUtils.utcToLocalTime;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryUsageStats;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.settings.fuelgauge.BatteryEntry;
import com.android.settings.fuelgauge.BatteryHistEntry;
import com.android.settings.fuelgauge.ConvertUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseUtils {

    private static final boolean DEBUG = false;
    private static final String TAG = "DatabaseUtils";
    private static final String SETTINGS_INTELLIGENCE_PKG =
            "com.google.android.settings.intelligence";
    private static final String BATTERY_PROVIDER =
            SETTINGS_INTELLIGENCE_PKG + ".modules.battery.provider";
    private static final String BATTERY_SETTINGS_CONTENT_PROVIDER =
            SETTINGS_INTELLIGENCE_PKG + ".modules.battery.impl.BatterySettingsContentProvider";

    public static Uri BATTERY_CONTENT_URI = new Uri.Builder()
            .scheme("content")
            .authority(BATTERY_PROVIDER)
            .appendPath("BatteryState")
            .build();

    public static boolean isContentProviderEnabled(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(new ComponentName(
                SETTINGS_INTELLIGENCE_PKG,
                BATTERY_SETTINGS_CONTENT_PROVIDER)
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static List<ContentValues> sendBatteryEntryData(
            Context context, List<BatteryEntry> list, final BatteryUsageStats batteryUsageStats) {
        long startTime = System.currentTimeMillis();
        Intent batteryIntent = getBatteryIntent(context);
        if (batteryIntent == null) {
            Log.e(TAG, "sendBatteryEntryData(): cannot fetch battery intent");
            return null;
        }

        int size = 1;
        final int batteryLevel = getBatteryLevel(batteryIntent);
        final int status = batteryIntent.getIntExtra("status", 1);
        final int health = batteryIntent.getIntExtra("health", 1);
        final long millis = Clock.systemUTC().millis();
        final long elapsedRealtime = SystemClock.elapsedRealtime();
        final ArrayList<ContentValues> arrayList = new ArrayList<>();
        if (list != null) {
            list.stream().filter(batteryEntry -> {
                long timeInForegroundMs = batteryEntry.getTimeInForegroundMs();
                long timeInBackgroundMs = batteryEntry.getTimeInBackgroundMs();
                double consumedPower = batteryEntry.getConsumedPower();
                if (consumedPower == 0
                        && (timeInForegroundMs != 0 || timeInBackgroundMs != 0)) {
                    Log.w(TAG, String.format("no consumed power but has running time for %s time=%d|%d",
                            batteryEntry.getLabel(), timeInForegroundMs, timeInBackgroundMs));
                }
                return !(consumedPower == 0 || timeInForegroundMs == 0 || timeInBackgroundMs == 0);
            }).forEach(batteryEntry -> arrayList.add(
                    ConvertUtils.convert(batteryEntry, batteryUsageStats,
                            batteryLevel, status, health, millis, elapsedRealtime)));
        }

        ContentResolver contentResolver = context.getContentResolver();
        if (!arrayList.isEmpty()) {
            ContentValues[] contentValues = new ContentValues[arrayList.size()];
            try {
                size = contentResolver.bulkInsert(BATTERY_CONTENT_URI, contentValues);
            } catch (Exception e) {
                Log.e(TAG, "bulkInsert() data into database error:\n" + e);
            }
            contentResolver.notifyChange(BATTERY_CONTENT_URI, null);
            Log.d(TAG, String.format("sendBatteryEntryData() size=%d in %d/ms",
                    size, (System.currentTimeMillis() - startTime)));
            return arrayList;
        }

        ContentValues convert = ConvertUtils.convert(
                null, null, batteryLevel, status, health, elapsedRealtime, millis);
        try {
            contentResolver.insert(BATTERY_CONTENT_URI, convert);
        } catch (Exception e2) {
            Log.e(TAG, "insert() data into database error:\n" + e2);
        }

        arrayList.add(convert);
        contentResolver.notifyChange(BATTERY_CONTENT_URI, null);
        Log.d(TAG, String.format("sendBatteryEntryData() size=%d in %d/ms",
                size, (System.currentTimeMillis() - startTime)));
        return arrayList;
    }

    public static Map<Long, Map<String, BatteryHistEntry>> getHistoryMap(
            Context context, Clock clock, boolean value) {
        HashMap<Long, Map<String, BatteryHistEntry>> hashMap = new HashMap<>();
        boolean isWorkProfileUser = isWorkProfileUser(context);
        Log.d(TAG, "getHistoryMap() isWorkProfileUser:" + isWorkProfileUser);
        if (isWorkProfileUser) {
            try {
                context = context.createPackageContextAsUser(
                        context.getPackageName(), 0, UserHandle.OWNER);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "context.createPackageContextAsUser() fail:" + e);
                return null;
            }
        }

        if (isContentProviderEnabled(context)) {
            long startTime = System.currentTimeMillis();
            try (Cursor query = context.getContentResolver().query(
                    BATTERY_CONTENT_URI, null, null, null)) {
                if (query != null && query.getCount() != 0) {
                    while (query.moveToNext()) {
                        BatteryHistEntry batteryHistEntry = new BatteryHistEntry(query);
                        long timestamp = batteryHistEntry.mTimestamp;
                        String key = batteryHistEntry.getKey();
                        Map<String, BatteryHistEntry> batteryHistMap =
                                hashMap.computeIfAbsent(timestamp, k -> new HashMap<>());
                        batteryHistMap.put(key, batteryHistEntry);
                    }
                    Log.d(TAG, String.format("getHistoryMap() size=%d in %d/ms",
                            hashMap.size(), System.currentTimeMillis() - startTime));
                    if (!hashMap.isEmpty() && value) {
                        long[] timestampSlots = getTimestampSlots(clock);
                        interpolateHistory(context, timestampSlots, hashMap);
                        for (long slots : new ArrayList<>(hashMap.keySet())) {
                            if (!contains(timestampSlots, slots)) {
                                hashMap.remove(slots);
                            }
                        }
                        Log.d(TAG, String.format("interpolateHistory() size=%d in %d/ms",
                                hashMap.size(), System.currentTimeMillis() - startTime));
                    }
                }
            }
        }
        return hashMap;
    }

    private static void interpolateHistory(
            Context context,
            long[] rawTimestampList,
            Map<Long, Map<String, BatteryHistEntry>> resultMap) {
        ArrayList<Long> timestampList = new ArrayList<>(resultMap.keySet());
        Collections.sort(timestampList);
        for (long currentSlot : rawTimestampList) {
            long[] findNearestTimestamp = findNearestTimestamp(timestampList, currentSlot);
            long lowerTimestamp = findNearestTimestamp[0];
            long upperTimestamp = findNearestTimestamp[1];
            int compare = (Long.compare(upperTimestamp, 0L));
            if (compare == 0) {
                log(context, "job scheduler is delayed", currentSlot, null);
                resultMap.put(currentSlot, new HashMap<>());
            } else if (upperTimestamp - currentSlot < 5000) {
                log(context, "force align into the nearest slot", currentSlot, null);
                resultMap.put(currentSlot, resultMap.get(upperTimestamp));
            } else if (lowerTimestamp == 0) {
                log(context, "no lower timestamp slot data", currentSlot, null);
                resultMap.put(currentSlot, new HashMap<>());
            } else {
                interpolateHistory(context, currentSlot, lowerTimestamp, upperTimestamp, resultMap);
            }
        }
    }

    static void interpolateHistory(
            Context context,
            long currentSlot,
            long lowerTimestamp,
            long upperTimestamp,
            Map<Long, Map<String, BatteryHistEntry>> resultMap) {
        Map<String, BatteryHistEntry> lowerEntryDataMap = resultMap.get(lowerTimestamp);
        Map<String, BatteryHistEntry> upperEntryDataMap = resultMap.get(upperTimestamp);
        BatteryHistEntry batteryHistEntry =
                upperEntryDataMap.values().stream().findFirst().get();
        if (lowerTimestamp < batteryHistEntry.mTimestamp - batteryHistEntry.mBootTimestamp) {
            if (upperTimestamp - currentSlot < 600000) {
                log(context, "force align into the nearest slot", currentSlot, null);
                resultMap.put(currentSlot, upperEntryDataMap);
                return;
            }
            log(context, "in the different booting section", currentSlot, null);
            resultMap.put(currentSlot, new HashMap<>());
            return;
        }

        log(context, "apply interpolation arithmetic", currentSlot, null);
        HashMap<String, BatteryHistEntry> hashMap = new HashMap<String, BatteryHistEntry>();
        double timestampLength = upperTimestamp - lowerTimestamp;
        double timestampDiff = currentSlot - lowerTimestamp;
        for (String next : upperEntryDataMap.keySet()) {
            BatteryHistEntry lowerEntry = lowerEntryDataMap.get(next);
            BatteryHistEntry upperEntry = upperEntryDataMap.get(next);
            if (lowerEntry != null) {
                boolean invalidForegroundUsageTime =
                        lowerEntry.mForegroundUsageTimeInMs > upperEntry.mForegroundUsageTimeInMs;
                boolean invalidBackgroundUsageTime =
                        lowerEntry.mBackgroundUsageTimeInMs > upperEntry.mBackgroundUsageTimeInMs;
                if (invalidForegroundUsageTime || invalidBackgroundUsageTime) {
                    hashMap.put(next, upperEntry);
                    log(context, "abnormal reset condition is found", currentSlot, upperEntry);
                }
            }
            hashMap.put(next, BatteryHistEntry.interpolate(
                    currentSlot, upperTimestamp, timestampDiff / timestampLength, lowerEntry, upperEntry));
            if (lowerEntry == null) {
                log(context, "cannot find lower entry data", currentSlot, upperEntry);
            }
        }
        resultMap.put(currentSlot, hashMap);
    }

    static long[] getTimestampSlots(Clock clock) {
        long[] results = new long[25];
        long millis = (clock.millis() / 3600000) * 3600000;
        for (int i = 0; i < 25; i++) {
            results[i] = millis - (i * 3600000);
        }
        return results;
    }

    static long[] findNearestTimestamp(List<Long> timestamps, long target) {
        final long[] results = new long[] {Long.MIN_VALUE, Long.MAX_VALUE};
        timestamps.forEach(timestamp -> {
            if (timestamp <= target && timestamp > results[0]) {
                results[0] = timestamp;
            }
            if (timestamp >= target && timestamp < results[1]) {
                results[1] = timestamp;
            }
        });
        results[0] = results[0] == Long.MIN_VALUE ? 0 : results[0];
        results[1] = results[1] == Long.MAX_VALUE ? 0 : results[1];
        return results;
    }

    static boolean contains(long[] timestampSlots, long slots) {
        for (long stampSlots : timestampSlots) {
            if (stampSlots == slots) {
                return true;
            }
        }
        return false;
    }

    private static Intent getBatteryIntent(Context context) {
        return context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private static int getBatteryLevel(Intent intent) {
        int level = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", 0);
        if (scale == 0) {
            return -1;
        }
        return Math.round((level / scale) * 100.0f);
    }

    private static boolean isWorkProfileUser(Context context) {
        UserManager manager = context.getSystemService(UserManager.class);
        return manager.isManagedProfile() && !manager.isSystemUser();
    }

    private static void log(Context context, final String content, final long timestamp,
                            final BatteryHistEntry entry) {
        if (DEBUG) {
            Log.d(TAG, String.format(entry != null ? "%s %s:\n%s" : "%s %s:%s",
                    utcToLocalTime(context, timestamp), content, entry));
        }
    }
}
