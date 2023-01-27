package com.google.android.settings.fuelgauge;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.ArraySet;

import androidx.window.R;

import com.android.settings.fuelgauge.BatteryHistEntry;
import com.android.settings.fuelgauge.PowerUsageFeatureProviderImpl;

import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class PowerUsageFeatureProviderGoogleImpl extends PowerUsageFeatureProviderImpl {

    private static boolean mChartGraphEnabled;

    public PowerUsageFeatureProviderGoogleImpl(Context context) {
        super(context);
    }

    @Override
    public boolean isChartGraphEnabled(Context context) {
        return mChartGraphEnabled = DatabaseUtils.isContentProviderEnabled(context);
    }

    @Override
    public Map<Long, Map<String, BatteryHistEntry>> getBatteryHistory(Context context) {
        return DatabaseUtils.getHistoryMap(context, Clock.systemUTC(), true);
    }

    @Override
    public Uri getBatteryHistoryUri() {
        return DatabaseUtils.BATTERY_CONTENT_URI;
    }

    @Override
    public Set<CharSequence> getHideBackgroundUsageTimeSet(Context context) {
        Set<CharSequence> timeSet = new ArraySet<>();
        Collections.addAll(timeSet, context.getResources()
                .getTextArray(R.array.allowlist_hide_background_in_battery_usage));
        return timeSet;
    }

    @Override
    public CharSequence[] getHideApplicationEntries(Context context) {
        return context.getResources().getTextArray(R.array.allowlist_hide_entry_in_battery_usage);
    }

    @Override
    public CharSequence[] getHideApplicationSummary(Context context) {
        return context.getResources().getTextArray(R.array.allowlist_hide_summary_in_battery_usage);
    }
}
