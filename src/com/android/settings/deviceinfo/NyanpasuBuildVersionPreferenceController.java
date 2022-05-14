/*
 * Copyright (C) 2022 Project Nyanpasu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;

public class NyanpasuBuildVersionPreferenceController extends BasePreferenceController implements
        LifecycleObserver, OnStart {

    private static final String TAG = "NyanpasuBuildVersionCtrl";

    private static final String KEY_NYANPASU_BUILD_VERSION_PROP = "ro.nyanpasu.version";
        
    private Toast mMoeBuildHitToast;
        
    public NyanpasuBuildVersionPreferenceController(Context context, String key) {
        super(context, key);
    }
        
    public void onStart() {
        mMoeBuildHitToast = null;
    }
        
    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return SystemProperties.get(KEY_NYANPASU_BUILD_VERSION_PROP,
            mContext.getString(R.string.unknown));
        }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }
        if (Utils.isMonkeyRunning()) {
            return false;
        }
        if (mMoeBuildHitToast != null) {
            mMoeBuildHitToast.cancel();
        }
        mMoeBuildHitToast = Toast.makeText(mContext, R.string.nyanpasu_build_version_toast,
            Toast.LENGTH_SHORT);
        mMoeBuildHitToast.show();
        return true;
    }
}

