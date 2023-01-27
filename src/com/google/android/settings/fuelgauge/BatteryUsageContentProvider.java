package com.google.android.settings.fuelgauge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.fuelgauge.BatteryChartPreferenceController;
import com.android.settings.fuelgauge.BatteryDiffEntry;
import com.android.settings.fuelgauge.BatteryHistEntry;

import java.util.List;
import java.util.function.Consumer;

public final class BatteryUsageContentProvider extends ContentProvider {

    private static final String TAG = "BatteryUsageContentProvider";

    private static final String BATTERY_USAGE = "com.google.android.settings.fuelgauge.provider";

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static List<BatteryDiffEntry> mCacheBatteryDiffEntries;

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    static {
        URI_MATCHER.addURI(BATTERY_USAGE, "BatteryUsageState", 1);
        mCacheBatteryDiffEntries = null;
    }

    @Override
    public boolean onCreate() {
        Log.v(TAG, "initialize provider");
        return true;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("insert() unsupported!");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
            @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("update() unsupported!");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("delete() unsupported!");
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        Log.d(TAG, "query:" + uri);
        if (URI_MATCHER.match(uri) != 1) {
            return null;
        }
        return getBatteryUsageData();
    }

    private Cursor getBatteryUsageData() {
        List<BatteryDiffEntry> list = mCacheBatteryDiffEntries;
        if (list == null) {
            list = BatteryChartPreferenceController.getBatteryLast24HrUsageData(getContext());
        }
        if (list == null || list.isEmpty()) {
            Log.w(TAG, "no data found in the getBatteryLast24HrUsageData()");
            return null;
        }
        final MatrixCursor matrixCursor = new MatrixCursor(BatteryUsageContract.KEYS_BATTERY_USAGE_STATE);
        list.forEach(batteryDiffEntry -> {
            if (batteryDiffEntry.mBatteryHistEntry == null
                    || batteryDiffEntry.getPercentOfTotal() == 0) return;
            addUsageDataRow(matrixCursor, batteryDiffEntry);
        });
        Log.d(TAG, "usage data count:" + matrixCursor.getCount());
        return matrixCursor;
    }

    private static void addUsageDataRow(MatrixCursor matrixCursor, BatteryDiffEntry batteryDiffEntry) {
        String packageName = batteryDiffEntry.getPackageName();
        if (packageName == null) {
            Log.w(TAG, "no package name found for\n" + batteryDiffEntry);
            return;
        }
        matrixCursor.addRow(new Object[]{
                batteryDiffEntry.mBatteryHistEntry.mUserId,
                packageName,
                batteryDiffEntry.getPercentOfTotal(),
                batteryDiffEntry.mForegroundUsageTimeInMs,
                batteryDiffEntry.mBackgroundUsageTimeInMs});
    }
}
