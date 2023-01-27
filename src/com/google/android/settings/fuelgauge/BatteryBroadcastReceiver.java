package com.google.android.settings.fuelgauge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.android.settings.fuelgauge.BatteryDiffEntry;
import com.android.settings.fuelgauge.BatteryEntry;

public final class BatteryBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "BatteryBroadcastReceiver";

    boolean mFetchBatteryUsageData = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case "settings.intelligence.battery.action.FETCH_BATTERY_USAGE_DATA":
                mFetchBatteryUsageData = true;
                BatteryUsageLoaderService.enqueueWork(context);
                break;
            case "settings.intelligence.battery.action.FETCH_BLUETOOTH_BATTERY_DATA":
                try {
                    BluetoothBatteryMetadataFetcher.returnBluetoothDevices(context, intent);
                } catch (Exception e) {
                    Log.e(TAG, "returnBluetoothDevices() error: ", e);
                }
                break;
            default:
                break;
        }
    }
}
