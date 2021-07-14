
package com.android.settings.deviceinfo;

import android.os.SystemProperties;

public class VersionUtils {
    public static String getAmatsukaVersion(){
        return SystemProperties.get("org.amatsuka.build_version","");
    }
}