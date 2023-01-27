package com.google.android.settings.fuelgauge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.android.settings.fuelgauge.BatteryChartPreferenceController;
import com.android.settings.fuelgauge.BatteryDiffEntry;
import com.android.settings.fuelgauge.BatteryHistEntry;

import java.util.List;
import java.util.function.Consumer;

public final class BatteryUsageContentProvider extends ContentProvider {

    private static final String TAG = "BatteryUsageContentProvider";
    private static final String BATTERY_USAGE = "com.google.android.settings.fuelgauge.provider";

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    public static List<BatteryDiffEntry> sCacheBatteryDiffEntries;

    @Override
    public String getType(Uri uri) {
        return null;
    }

    static {
        URI_MATCHER.addURI(BATTERY_USAGE, "BatteryUsageState", 1);
        sCacheBatteryDiffEntries = null;
    }

    @Override
    public boolean onCreate() {
        Log.v(TAG, "initialize provider");
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert() unsupported!");
    }

    @Override
    public int update(Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update() unsupported!");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete() unsupported!");
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrde) {
        Log.d(TAG, "query:" + uri);
        if (URI_MATCHER.match(uri) != 1) {
            return null;
        }
        return getBatteryUsageData();
    }

    private Cursor getBatteryUsageData() {
        if (sCacheBatteryDiffEntries == null) {
            sCacheBatteryDiffEntries =
                    BatteryChartPreferenceController.getBatteryLast24HrUsageData(getContext());
        }
        if (sCacheBatteryDiffEntries == null ||
                sCacheBatteryDiffEntries.isEmpty()) {
            Log.w(TAG, "no data found in the getBatteryLast24HrUsageData()");
            return null;
        }
        final MatrixCursor matrixCursor = new MatrixCursor(BatteryUsageContract.KEYS_BATTERY_USAGE_STATE);
        sCacheBatteryDiffEntries.forEach(batteryDiffEntry -> {
            BatteryHistEntry batteryHistEntry = batteryDiffEntry.mBatteryHistEntry;
            if (batteryHistEntry == null ||
                    batteryHistEntry.mConsumerType != 1 ||
                    batteryDiffEntry.getPercentOfTotal() == 0.0d) return;
            addUsageDataRow(matrixCursor, batteryDiffEntry);
        });
        Log.d(TAG, "usage data count:" + matrixCursor.getCount());
        return matrixCursor;
    }

    private static void addUsageDataRow(
            MatrixCursor matrixCursor, BatteryDiffEntry batteryDiffEntry) {
        String packageName = batteryDiffEntry.getPackageName();
        if (packageName == null) {
            Log.w(TAG, "no package name found for\n" + batteryDiffEntry);
            return;
        }
        matrixCursor.addRow(new Object[] {
                batteryDiffEntry.mBatteryHistEntry.mUserId,
                packageName,
                batteryDiffEntry.getPercentOfTotal(),
                batteryDiffEntry.mForegroundUsageTimeInMs,
                batteryDiffEntry.mBackgroundUsageTimeInMs});
    }
}
