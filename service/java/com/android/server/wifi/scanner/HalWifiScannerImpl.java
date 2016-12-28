/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.wifi.scanner;

import android.content.Context;
import android.net.wifi.WifiScanner;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.server.wifi.Clock;
import com.android.server.wifi.WifiNotive;
import com.android.server.wifi.WifiNotiveTypes;

/**
 * WifiScanner implementation that takes advantage of the gscan HAL API
 * The gscan API is used to perform background scans and wpa_supplicant is used for onehot scans.
 * @see com.android.server.wifi.scanner.WifiScannerImpl for more details on each method.
 */
public class HalWifiScannerImpl extends WifiScannerImpl implements Handler.Callback {
    private static final String TAG = "HalWifiScannerImpl";
    private static final boolean DBG = false;

    private final WifiNotive mWifiNotive;
    private final ChannelHelper mChannelHelper;
    private final SupplicantWifiScannerImpl mSupplicantScannerDelegate;
    private final boolean mHalBasedPnoSupported;

    public HalWifiScannerImpl(Context context, WifiNotive wifiNotive, Looper looper, Clock clock) {
        mWifiNotive = wifiNotive;
        mChannelHelper = new HalChannelHelper(wifiNotive);
        mSupplicantScannerDelegate =
                new SupplicantWifiScannerImpl(context, wifiNotive, mChannelHelper, looper, clock);

        // We are not going to support HAL ePNO currently.
        mHalBasedPnoSupported = false;
    }

    @Override
    public boolean handleMessage(Message msg) {
        Log.w(TAG, "Unknown message received: " + msg.what);
        return true;
    }

    @Override
    public void cleanup() {
        mSupplicantScannerDelegate.cleanup();
    }

    @Override
    public boolean getScanCapabilities(WifiNotiveTypes.ScanCapabilities capabilities) {
        return mWifiNotive.getScanCapabilities(capabilities);
    }

    @Override
    public ChannelHelper getChannelHelper() {
        return mChannelHelper;
    }

    public boolean startSingleScan(WifiNotiveTypes.ScanSettings settings,
            WifiNotiveTypes.ScanEventHandler eventHandler) {
        return mSupplicantScannerDelegate.startSingleScan(settings, eventHandler);
    }

    @Override
    public WifiScanner.ScanData getLatestSingleScanResults() {
        return mSupplicantScannerDelegate.getLatestSingleScanResults();
    }

    @Override
    public boolean startBatchedScan(WifiNotiveTypes.ScanSettings settings,
            WifiNotiveTypes.ScanEventHandler eventHandler) {
        if (settings == null || eventHandler == null) {
            Log.w(TAG, "Invalid arguments for startBatched: settings=" + settings
                    + ",eventHandler=" + eventHandler);
            return false;
        }
        return mWifiNotive.startScan(settings, eventHandler);
    }

    @Override
    public void stopBatchedScan() {
        mWifiNotive.stopScan();
    }

    @Override
    public void pauseBatchedScan() {
        mWifiNotive.pauseScan();
    }

    @Override
    public void restartBatchedScan() {
        mWifiNotive.restartScan();
    }

    @Override
    public WifiScanner.ScanData[] getLatestBatchedScanResults(boolean flush) {
        return mWifiNotive.getScanResults(flush);
    }

    @Override
    public boolean setHwPnoList(WifiNotiveTypes.PnoSettings settings,
            WifiNotiveTypes.PnoEventHandler eventHandler) {
        if (mHalBasedPnoSupported) {
            return mWifiNotive.setPnoList(settings, eventHandler);
        } else {
            return mSupplicantScannerDelegate.setHwPnoList(settings, eventHandler);
        }
    }

    @Override
    public boolean resetHwPnoList() {
        if (mHalBasedPnoSupported) {
            return mWifiNotive.resetPnoList();
        } else {
            return mSupplicantScannerDelegate.resetHwPnoList();
        }
    }

    @Override
    public boolean isHwPnoSupported(boolean isConnectedPno) {
        if (mHalBasedPnoSupported) {
            return true;
        } else {
            return mSupplicantScannerDelegate.isHwPnoSupported(isConnectedPno);
        }
    }

    @Override
    public boolean shouldScheduleBackgroundScanForHwPno() {
        if (mHalBasedPnoSupported) {
            return true;
        } else {
            return mSupplicantScannerDelegate.shouldScheduleBackgroundScanForHwPno();
        }
    }

    @Override
    public boolean setHotlist(WifiScanner.HotlistSettings settings,
            WifiNotiveTypes.HotlistEventHandler eventHandler) {
        return mWifiNotive.setHotlist(settings, eventHandler);
    }

    @Override
    public void resetHotlist() {
        mWifiNotive.resetHotlist();
    }

    @Override
    public boolean trackSignificantWifiChange(WifiScanner.WifiChangeSettings settings,
            WifiNotiveTypes.SignificantWifiChangeEventHandler handler) {
        return mWifiNotive.trackSignificantWifiChange(settings, handler);
    }

    @Override
    public void untrackSignificantWifiChange() {
        mWifiNotive.untrackSignificantWifiChange();
    }
}
