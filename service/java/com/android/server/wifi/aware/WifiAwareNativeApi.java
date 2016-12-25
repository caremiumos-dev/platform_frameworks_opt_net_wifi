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

package com.android.server.wifi.aware;

import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.NanBandIndex;
import android.hardware.wifi.V1_0.NanEnableRequest;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.wifi.aware.ConfigRequest;
import android.util.Log;

/**
 * Translates Wi-Fi Aware requests from the framework to the HAL (HIDL).
 *
 * Delegates the management of the NAN interface to WifiAwareNativeManager.
 */
public class WifiAwareNativeApi {
    private static final String TAG = "WifiAwareNativeApi";
    private static final boolean DBG = false;
    private static final boolean VDBG = false; // STOPSHIP if true

    private WifiAwareNativeManager mHal;

    public WifiAwareNativeApi() {
        // empty
    }

    public void setRefs(WifiAwareNativeManager wifiAwareNativeManager) {
        mHal = wifiAwareNativeManager;
    }

    /**
     * Query the firmware's capabilities.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     */
    public boolean getCapabilities(short transactionId) {
        if (VDBG) Log.v(TAG, "getCapabilities: transactionId=" + transactionId);

        synchronized (mHal.mLock) {
            IWifiNanIface iface = mHal.getWifiNanIface();
            if (iface == null) {
                Log.w(TAG, "getCapabilities: null interface");
                return false;
            }

            try {
                WifiStatus status = iface.getCapabilitiesRequest(transactionId);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.w(TAG, "getCapabilities: error: " + status.code + "(" + status.description
                            + ")");
                    return false;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "getCapabilities: exception: " + e);
                mHal.serviceDiedHandler();
                return false;
            }
        }
    }

    /**
     * Enable and configure Aware.
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param configRequest Requested Aware configuration.
     * @param initialConfiguration Specifies whether initial configuration
     *            (true) or an update (false) to the configuration.
     */
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean initialConfiguration) {
        if (VDBG) {
            Log.v(TAG, "enableAndConfigure: transactionId=" + transactionId + ", configRequest="
                    + configRequest + ", initialConfiguration=" + initialConfiguration);
        }

        synchronized (mHal.mLock) {
            IWifiNanIface iface = mHal.getWifiNanIface();
            if (iface == null) {
                Log.w(TAG, "enableAndConfigure: null interface");
                return false;
            }

            // translate framework to HIDL configuration
            NanEnableRequest req = new NanEnableRequest();
            req.debugConfigs.clusterIdHighVal = (short) configRequest.mClusterHigh;
            req.debugConfigs.clusterIdLowVal = (short) configRequest.mClusterLow;
            req.configParams.masterPref = (byte) configRequest.mMasterPreference;
            req.operateInBand.set(NanBandIndex.NAN_BAND_5GHZ, configRequest.mSupport5gBand);

            try {
                WifiStatus status = iface.enableRequest(transactionId, req);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.w(TAG, "enableAndConfigure: error: " + status.code + "("
                            + status.description + ")");
                    return false;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "enableAndConfigure: exception: " + e);
                mHal.serviceDiedHandler();
                return false;
            }
        }
    }

}
