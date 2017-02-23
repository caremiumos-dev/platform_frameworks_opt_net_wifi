/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import static android.net.wifi.WifiManager.WIFI_FEATURE_CONTROL_ROAMING;

import android.util.Log;

import java.util.ArrayList;

/**
 * This class provides helper functions for Wifi connectivity related modules to
 * access WifiNative. It starts with firmware roaming. TODO(b/34819513): Move operations
 * such as connection to network and legacy framework roaming here.
 */
public class WifiConnectivityHelper {
    private static final String TAG = "WifiConnectivityHelper";
    private final WifiNative mWifiNative;
    private boolean mFirmwareRoamingSupported = false;
    private int mMaxNumBlacklistBssid = -1;
    private int mMaxNumWhitelistSsid = -1;

    WifiConnectivityHelper(WifiNative wifiNative) {
        mWifiNative = wifiNative;
    }

    /**
     * Query firmware if it supports
     * {@link android.net.wifi.WifiManager#WIFI_FEATURE_CONTROL_ROAMING}. If yes, get the firmware
     * roaming capabilities.
     */
    public void getFirmwareRoamingInfo() {
        int fwFeatureSet = mWifiNative.getSupportedFeatureSet();
        Log.d(TAG, "Firmware supported feature set: " + Integer.toHexString(fwFeatureSet));

        mFirmwareRoamingSupported = (fwFeatureSet & WIFI_FEATURE_CONTROL_ROAMING) > 0;
        mMaxNumBlacklistBssid = -1;
        mMaxNumWhitelistSsid = -1;
        if (mFirmwareRoamingSupported) {
            WifiNative.RoamingCapabilities roamingCap = new WifiNative.RoamingCapabilities();
            if (mWifiNative.getRoamingCapabilities(roamingCap)) {
                mMaxNumBlacklistBssid = roamingCap.maxBlacklistSize;
                mMaxNumWhitelistSsid = roamingCap.maxWhitelistSize;
                Log.d(TAG, "Firmware roaming capabilities: max num blacklist bssid="
                        + mMaxNumBlacklistBssid + " max num whitelist ssid="
                        + mMaxNumWhitelistSsid);
            } else {
                Log.e(TAG, "Failed to get firmware roaming capabilities");
            }
        }
    }

    /**
     * Return if firmware roaming is supported.
     */
    public boolean isFirmwareRoamingSupported() {
        return mFirmwareRoamingSupported;
    }

    /**
     * Return the maximum size of BSSID blacklist.
     */
    public int getMaxNumBlacklistBssid() {
        if (mFirmwareRoamingSupported) {
            return mMaxNumBlacklistBssid;
        } else {
            Log.e(TAG, "Firmware roaming is not supported");
            return -1;
        }
    }

    /**
     * Return the maximum size of SSID whitelist.
     */
    public int getMaxNumWhitelistSsid() {
        if (mFirmwareRoamingSupported) {
            return mMaxNumWhitelistSsid;
        } else {
            Log.e(TAG, "Firmware roaming is not supported");
            return -1;
        }
    }

    /**
     * Write firmware roaming configuration to firmware.
     *
     * @param blacklistBssids BSSIDs to be blacklisted
     * @param whitelistSsids  SSIDs to be whitelisted
     * @return true if succeeded, false otherwise.
     */
    public boolean setFirmwareRoamingConfiguration(ArrayList<String> blacklistBssids,
                ArrayList<String> whitelistSsids) {
        if (!mFirmwareRoamingSupported) {
            Log.e(TAG, "Firmware roaming is not supported");
            return false;
        }

        if (blacklistBssids == null || whitelistSsids == null) {
            Log.e(TAG, "Invalid firmware roaming configuration settings");
            return false;
        }

        int blacklistSize = blacklistBssids.size();
        int whitelistSize = whitelistSsids.size();

        if (blacklistSize > mMaxNumBlacklistBssid || whitelistSize > mMaxNumWhitelistSsid) {
            Log.e(TAG, "Invalid BSSID blacklist size " + blacklistSize + " SSID whitelist size "
                    + whitelistSize + ". Max blacklist size: " + mMaxNumBlacklistBssid
                    + ", max whitelist size: " + mMaxNumWhitelistSsid);
            return false;
        }

        WifiNative.RoamingConfig roamConfig = new WifiNative.RoamingConfig();
        roamConfig.blacklistBssids = blacklistBssids;
        roamConfig.whitelistSsids = whitelistSsids;

        return mWifiNative.configureRoaming(roamConfig);
    }
}
