/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.util;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

/**
 * Utilities for the Wifi Service to interact with CarrierConfig.
 */
public class CarrierConfigUtil {
    public static final String TAG = "CarrierConfigUtil";

    private Context mContext;

    public CarrierConfigUtil(Context context) {
        mContext = context;
    }

    private PersistableBundle getBundle() {
        if (mContext == null) {
            return null;
        }
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (carrierConfigManager != null) {
            return carrierConfigManager.getConfig();
        }
        return null;
    }

    /**
     * Get the boolean value for the specified key from the Carrier Config.
     * @param key.
     * @return boolean value from the Carrier Config.
     */
    public Boolean getCarrierConfigBooleanForKey(String key) {
        PersistableBundle persistableBundle = getBundle();
        return (persistableBundle == null) ? null : persistableBundle.getBoolean(key);
    }

    /**
     * Get the Integer value for the specified key from the Carrier Config.
     * @param key.
     * @return Integer value from the Carrier Config.
     */
    public Integer getCarrierConfigIntForKey(String key) {
        PersistableBundle persistableBundle = getBundle();
        return (persistableBundle == null) ? null : persistableBundle.getInt(key);
    }

    /**
     * Get the String value for the specified key from the Carrier Config.
     * @param key.
     * @return String value from the Carrier Config.
     */
    public String getCarrierConfigStringForKey(String key) {
        PersistableBundle persistableBundle = getBundle();
        return (persistableBundle == null) ? null : persistableBundle.getString(key);
    }

    /**
     * Get the String Array for the specified key from the Carrier Config.
     * @param key.
     * @return String Array from the Carrier Config.
     */
    public String[] getCarrierConfigStringArrayForKey(String key) {
        PersistableBundle persistableBundle = getBundle();
        return (persistableBundle == null) ? null : persistableBundle.getStringArray(key);
    }
}
