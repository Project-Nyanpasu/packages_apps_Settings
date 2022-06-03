/*
 * Copyright (C) 2022 The Project Nyanpasu
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.nyanpasu;

import android.os.Bundle;

import com.android.internal.logging.nano.MetricsProto; 
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class IgnoreSecureSettings extends SettingsPreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.window_ignore_secure);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.NYANPASU_SETTINGS;
    }
}
