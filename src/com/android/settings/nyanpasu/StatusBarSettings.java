/*
 * Copyright (C) 2022 The Project Nyanpasu
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.nyanpasu;

import android.os.Bundle;
import android.text.format.DateFormat;

import com.android.internal.logging.nano.MetricsProto; 
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import moe.reallysnow.support.preferences.SecureSettingListPreference;

public class StatusBarSettings extends SettingsPreferenceFragment {
    private static final String KEY_STATUS_BAR_AM_PM = "status_bar_am_pm";

    SecureSettingListPreference mStatusBarAmPm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_settings);
        mStatusBarAmPm = findPreference(KEY_STATUS_BAR_AM_PM);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (DateFormat.is24HourFormat(requireContext())) {
            mStatusBarAmPm.setEnabled(false);
            mStatusBarAmPm.setSummary(R.string.status_bar_am_pm_unavailable);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.NYANPASU_SETTINGS;
    }
}
