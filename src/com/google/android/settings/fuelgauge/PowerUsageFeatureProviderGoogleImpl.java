package com.google.android.settings.fuelgauge;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.SparseIntArray;
import com.android.settings.R;
import com.android.settings.fuelgauge.BatteryHistEntry;
import com.android.settings.fuelgauge.PowerUsageFeatureProviderImpl;
import com.android.settingslib.fuelgauge.Estimate;
import com.android.settingslib.utils.PowerUtil;
import com.google.android.settings.experiments.PhenotypeProxy;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class PowerUsageFeatureProviderGoogleImpl extends PowerUsageFeatureProviderImpl {
    static final String ACTION_RESUME_CHARGING = "PNW.defenderResumeCharging.settings";
    static final String AVERAGE_BATTERY_LIFE_COL = "average_battery_life";
    static final String BATTERY_ESTIMATE_BASED_ON_USAGE_COL = "is_based_on_usage";
    static final String BATTERY_ESTIMATE_COL = "battery_estimate";
    static final String BATTERY_LEVEL_COL = "battery_level";
    static final int CUSTOMIZED_TO_USER = 1;
    static final String GFLAG_ADDITIONAL_BATTERY_INFO_ENABLED = "settingsgoogle:additional_battery_info_enabled";
    static final String GFLAG_BATTERY_ADVANCED_UI_ENABLED = "settingsgoogle:battery_advanced_ui_enabled";
    static final String GFLAG_POWER_ACCOUNTING_TOGGLE_ENABLED = "settingsgoogle:power_accounting_toggle_enabled";
    static final String IS_EARLY_WARNING_COL = "is_early_warning";
    static final int NEED_EARLY_WARNING = 1;
    static final String PACKAGE_NAME_SYSTEMUI = "com.android.systemui";
    static final String TIMESTAMP_COL = "timestamp_millis";
    private static boolean sChartGraphEnabled;
    private static boolean sChartGraphSlotsEnabled;
    private static final String[] PACKAGES_SERVICE = {"com.google.android.gms", "com.google.android.apps.gcs"};
    static boolean sChartConfigurationLoaded = false;

    public PowerUsageFeatureProviderGoogleImpl(Context context) {
        super(context);
    }

    private Uri getEnhancedBatteryPredictionCurveUri() {
        return new Uri.Builder().scheme("content").authority("com.google.android.apps.turbo.estimated_time_remaining").appendPath("discharge_curve").build();
    }

    private Uri getEnhancedBatteryPredictionUri() {
        return new Uri.Builder().scheme("content").authority("com.google.android.apps.turbo.estimated_time_remaining").appendPath("time_remaining").build();
    }

    private boolean isSettingsIntelligenceExist(Context context) {
        boolean z = false;
        if (!context.getPackageManager().getPackageInfo("com.google.android.settings.intelligence", 512).applicationInfo.enabled) {
            return false;
        }
        z = true;
        return z;
    }

    private void loadChartConfiguration(Context context) {
        if (sChartConfigurationLoaded) {
            return;
        }
        boolean isSettingsIntelligenceExist = isSettingsIntelligenceExist(context);
        sChartGraphEnabled = isSettingsIntelligenceExist && DatabaseUtils.isContentProviderEnabled(context);
        boolean z = false;
        if (isSettingsIntelligenceExist) {
            z = false;
            if (PhenotypeProxy.getFlagByPackageAndKey(context, "com.google.android.settings.intelligence", "BatteryUsage__is_time_slot_supported", false)) {
                z = true;
            }
        }
        sChartGraphSlotsEnabled = z;
        sChartConfigurationLoaded = true;
    }

    public String getAdvancedUsageScreenInfoString() {
        return ((PowerUsageFeatureProviderImpl) this).mContext.getString(R.string.advanced_battery_graph_subtext);
    }

    public Map<Long, Map<String, BatteryHistEntry>> getBatteryHistory(Context context) {
        return DatabaseUtils.getHistoryMap(context, Clock.systemUTC(), true);
    }

    public Uri getBatteryHistoryUri() {
        return DatabaseUtils.BATTERY_CONTENT_URI;
    }

    public boolean getEarlyWarningSignal(Context context, String str) {
        Uri.Builder appendPath = new Uri.Builder().scheme("content").authority("com.google.android.apps.turbo.estimated_time_remaining").appendPath("early_warning").appendPath("id");
        if (TextUtils.isEmpty(str)) {
            appendPath.appendPath(context.getPackageName());
        } else {
            appendPath.appendPath(str);
        }
        Cursor query = context.getContentResolver().query(appendPath.build(), null, null, null, null);
        boolean z = false;
        if (query != null) {
            try {
                if (query.moveToFirst()) {
                    if (1 == query.getInt(query.getColumnIndex(IS_EARLY_WARNING_COL))) {
                        z = true;
                    }
                    query.close();
                    return z;
                }
            } catch (Throwable th) {
                try {
                    query.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        }
        if (query != null) {
            query.close();
            return false;
        }
        return false;
    }

    public Estimate getEnhancedBatteryPrediction(Context context) {
        long j;
        Cursor query = context.getContentResolver().query(getEnhancedBatteryPredictionUri(), null, null, null, null);
        if (query != null) {
            try {
                if (query.moveToFirst()) {
                    int columnIndex = query.getColumnIndex(BATTERY_ESTIMATE_BASED_ON_USAGE_COL);
                    boolean z = true;
                    if (columnIndex != -1) {
                        z = 1 == query.getInt(columnIndex);
                    }
                    int columnIndex2 = query.getColumnIndex(AVERAGE_BATTERY_LIFE_COL);
                    if (columnIndex2 != -1) {
                        long j2 = query.getLong(columnIndex2);
                        if (j2 != -1) {
                            long millis = Duration.ofMinutes(15L).toMillis();
                            if (Duration.ofMillis(j2).compareTo(Duration.ofDays(1L)) >= 0) {
                                millis = Duration.ofHours(1L).toMillis();
                            }
                            j = PowerUtil.roundTimeToNearestThreshold(j2, millis);
                            Estimate estimate = new Estimate(query.getLong(query.getColumnIndex(BATTERY_ESTIMATE_COL)), z, j);
                            query.close();
                            return estimate;
                        }
                    }
                    j = -1;
                    Estimate estimate2 = new Estimate(query.getLong(query.getColumnIndex(BATTERY_ESTIMATE_COL)), z, j);
                    query.close();
                    return estimate2;
                }
            } catch (Throwable th) {
                try {
                    query.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        }
        if (query != null) {
            query.close();
            return null;
        }
        return null;
    }

    public SparseIntArray getEnhancedBatteryPredictionCurve(Context context, long j) {
        try {
            Cursor query = context.getContentResolver().query(getEnhancedBatteryPredictionCurveUri(), null, null, null, null);
            if (query == null) {
                if (query == null) {
                    return null;
                }
                query.close();
                return null;
            }
            int columnIndex = query.getColumnIndex(TIMESTAMP_COL);
            int columnIndex2 = query.getColumnIndex(BATTERY_LEVEL_COL);
            SparseIntArray sparseIntArray = new SparseIntArray(query.getCount());
            while (query.moveToNext()) {
                sparseIntArray.append((int) (query.getLong(columnIndex) - j), query.getInt(columnIndex2));
            }
            query.close();
            return sparseIntArray;
        } catch (NullPointerException e) {
            return null;
        }
    }

    public CharSequence[] getHideApplicationEntries(Context context) {
        return context.getResources().getTextArray(R.array.allowlist_hide_entry_in_battery_usage);
    }

    public CharSequence[] getHideApplicationSummary(Context context) {
        return context.getResources().getTextArray(R.array.allowlist_hide_summary_in_battery_usage);
    }

    public Set<CharSequence> getHideBackgroundUsageTimeSet(Context context) {
        ArraySet arraySet = new ArraySet();
        Collections.addAll(arraySet, context.getResources().getTextArray(R.array.allowlist_hide_background_in_battery_usage));
        return arraySet;
    }

    public Intent getResumeChargeIntent() {
        return new Intent(ACTION_RESUME_CHARGING).setPackage(PACKAGE_NAME_SYSTEMUI).addFlags(1342177280);
    }

    public boolean isChartGraphEnabled(Context context) {
        loadChartConfiguration(context);
        return sChartGraphEnabled;
    }

    public boolean isChartGraphSlotsEnabled(Context context) {
        loadChartConfiguration(context);
        return sChartGraphSlotsEnabled;
    }

    public boolean isEnhancedBatteryPredictionEnabled(Context context) {
        if (!isTurboEnabled(context)) {
            return false;
        }
        try {
            return ((PowerUsageFeatureProviderImpl) this).mPackageManager.getPackageInfo("com.google.android.apps.turbo", 512).applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    boolean isTurboEnabled(Context context) {
        return PhenotypeProxy.getFlagByPackageAndKey(context, "com.google.android.apps.turbo", "NudgesBatteryEstimates__estimated_time_remaining_provider_enabled", false);
    }

    void setPackageManager(PackageManager packageManager) {
        ((PowerUsageFeatureProviderImpl) this).mPackageManager = packageManager;
    }
}
