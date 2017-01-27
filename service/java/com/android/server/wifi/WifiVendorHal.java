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

import android.annotation.Nullable;
import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiRttControllerEventCallback;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.IWifiStaIfaceEventCallback;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.RttConfig;
import android.hardware.wifi.V1_0.RttResponder;
import android.hardware.wifi.V1_0.RttResult;
import android.hardware.wifi.V1_0.StaRoamingConfig;
import android.hardware.wifi.V1_0.StaRoamingState;
import android.hardware.wifi.V1_0.StaScanData;
import android.hardware.wifi.V1_0.StaScanResult;
import android.hardware.wifi.V1_0.WifiDebugHostWakeReasonStats;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiDebugRxPacketFateReport;
import android.hardware.wifi.V1_0.WifiDebugTxPacketFateReport;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.apf.ApfCapabilities;
import android.net.wifi.RttManager;
import android.net.wifi.RttManager.ResponderConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiLinkLayerStats;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiWakeReasonAndCounts;
import android.os.RemoteException;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.MutableInt;

import com.android.server.connectivity.KeepalivePacketData;

import libcore.util.HexEncoding;

import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * Vendor HAL via HIDL
 */
public class WifiVendorHal {

    private static boolean sDGB = true; // TODO(mplass): init false for production
    private static final String TAG = "WifiVendorHal";

    // TODO(mplass): figure out where we need locking in hidl world. b/33383725
    public static final Object sLock = new Object();

    // HAL command ids
    private static int sCmdId = 1;

    private static int getNewCmdIdLocked() {
        return sCmdId++;
    }

    // Vendor HAL HIDL interface objects.
    private IWifi mIWifi;
    private IWifiChip mIWifiChip;
    private ArrayList<IWifiChip.ChipMode> mIWifiChipAvailableModes = null;
    private IWifiStaIface mIWifiStaIface;
    private IWifiRttController mIWifiRttController;

    private void handleRemoteException(RemoteException e) {
        kilroy();
        Log.e(TAG, "RemoteException in HIDL call " + e);
    }

    private void noteHidlError(WifiStatus status, String culprit) {
        kilroy();
        Log.e(TAG, "Error in " + culprit + " code: " + status.code
                + " (" + status.description + ")");
    }

    /**
     * Bring up the HIDL Vendor HAL and configure for STA mode
     */
    public boolean startVendorHal() {
        kilroy();
        /** Get the Wifi Service */
        try {
            kilroy();
            mIWifi = IWifi.getService();
        } catch (NoSuchElementException e) {
            kilroy();
            Log.e(TAG, "HIDL wifi service not registered");
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
        }
        if (mIWifi == null) {
            kilroy();
            Log.e(TAG, "Failed to get Wifi HIDL interface objects.");
            return false;
        }
        /** Start the Wifi Service*/
        try {
            kilroy();
            WifiStatus startStatus = mIWifi.start();
            if (startStatus.code != WifiStatusCode.SUCCESS) {
                kilroy();
                noteHidlError(startStatus, "startHidlHal");
                return false;
            }
            /** List available Wifi chips */
            final ArrayList<Integer> chipIdsInternal = new ArrayList<>();
            mIWifi.getChipIds((status, chipIds) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) {
                            kilroy();
                            noteHidlError(status, "getChipIds");
                            return;
                        }
                        chipIdsInternal.addAll(chipIds);
                    }
            );
            if (chipIdsInternal.size() != 1) {
                kilroy();
                Log.e(TAG, "Expected 1 chip to be present, got: " + chipIdsInternal.size());
                if (chipIdsInternal.size() < 1) return false;
            }
            /** Get chip 0 */

            mIWifi.getChip(chipIdsInternal.get(0).intValue(), (status, chip) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) {
                            kilroy();
                            noteHidlError(status, "getChip");
                            return;
                        }
                        mIWifiChip = chip;
                    }
            );
            if (mIWifiChip == null) {
                kilroy();
                Log.e(TAG, "Failed to get WifiChip HIDL interface objects.");
                return false;
            }
            /** Configure Chip Mode for STA */
            mIWifiChip.getAvailableModes((status, modes) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) {
                            kilroy();
                            noteHidlError(startStatus, "getAvailableModes");
                            return;
                        }
                        mIWifiChipAvailableModes = modes;
                    }
            );
            if (mIWifiChipAvailableModes == null) {
                kilroy();
                Log.e(TAG, "Failed to get WifiChip HIDL chip available modes.");
                return false;
            }
            int modeId = -1;
            int hits = 0;
            int wantedType = IfaceType.STA;
            for (IWifiChip.ChipMode mode : mIWifiChipAvailableModes) {
                kilroy();
                for (IWifiChip.ChipIfaceCombination combination : mode.availableCombinations) {
                    kilroy();
                    for (IWifiChip.ChipIfaceCombinationLimit ifaceLimit : combination.limits) {
                        kilroy();
                        for (Integer ifaceType : ifaceLimit.types) {
                            kilroy();
                            if (ifaceType.equals(wantedType)) {
                                kilroy();
                                modeId = mode.id;
                                hits++;
                            }
                        }
                    }
                }
            }
            if (hits == 0) {
                kilroy();
                Log.e(TAG, "Failed to find matching mode");
                return false;
            }
            if (hits > 1 && sDGB) {
                kilroy();
                Log.d(TAG, "number of matching modes: " + hits + ", picked modeId " + modeId);
            }
            WifiStatus wifiStatus = mIWifiChip.configureChip(modeId);
            if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                kilroy();
                noteHidlError(wifiStatus, "configureChip");
                return false;
            }

            /** Create Wifi STA Interface */

            mIWifiChip.createStaIface((status, iface) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) {
                            kilroy();
                            noteHidlError(status, "startHidlHal");
                            Log.e(TAG, "Create STA Iface failed. Error code: " + status.code);
                            return;
                        }
                        mIWifiStaIface = iface;
                    }
            );
            if (mIWifiStaIface == null) {
                kilroy();
                return false;
            }

            /** Create the RTT controller */
            mIWifiRttController = null;
            mIWifiChip.createRttController(
                    mIWifiStaIface,
                    (status, rtt) -> {
                        kilroy();
                        if (status.code == WifiStatusCode.SUCCESS) {
                            kilroy();
                            mIWifiRttController = rtt;
                            Log.e(TAG, "Made " + mIWifiRttController);
                        }
                    }
            );
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return false;
        }
        Log.i(TAG, "Retrieved the HIDL interface objects.");
        return true;
    }

    /**
     * Stops the HAL
     * (SOMEDAY)
     */
    public void stopVendorHal() {
        kilroy();
        // TODO(mplass): Actually stop b/33383725
        mIWifi = null;
        mIWifiChip = null;
        mDriverDescription = null;
        mFirmwareDescription = null;
    }

    /**
     * Tests whether the HAL is running or not
     */
    public boolean isHalStarted() {
        kilroy();
        return mIWifiStaIface != null;
    }

//    /**
//     * Holds the result of getScanCapabilities
//     */
//    public static class ScanCapabilities { kilroy();
//        public int max_scan_cache_size;
//        public int max_scan_buckets;
//        public int max_ap_cache_per_scan;
//        public int max_rssi_sample_size;
//        public int max_scan_reporting_threshold;
//        public int max_hotlist_bssids;
//        public int max_significant_wifi_change_aps;
//        public int max_bssid_history_entries;
//        public int max_number_epno_networks;
//        public int max_number_epno_networks_by_ssid;
//        public int max_number_of_white_listed_ssid;
//    }

    /**
     * Gets the scan capabilities
     *
     * @param capabilities object to be filled in
     * @return true for success. false for failure
     */
    public boolean getScanCapabilities(WifiNative.ScanCapabilities capabilities) {
        kilroy();
        try {
            kilroy();
            MutableBoolean ok = new MutableBoolean(false);
            WifiNative.ScanCapabilities out = capabilities;
            if (!isHalStarted()) {
                kilroy();
                return false;
            }
            mIWifiStaIface.getBackgroundScanCapabilities((status, cap) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) return;
                        out.max_scan_cache_size = cap.maxCacheSize;
                        out.max_ap_cache_per_scan = cap.maxApCachePerScan;
                        out.max_scan_buckets = cap.maxBuckets;
                        out.max_rssi_sample_size = 0;
                        out.max_scan_reporting_threshold = cap.maxReportingThreshold;
                        out.max_hotlist_bssids = 0;
                        out.max_significant_wifi_change_aps = 0;
                        out.max_bssid_history_entries = 0;
                        out.max_number_epno_networks = 0;
                        out.max_number_epno_networks_by_ssid = 0;
                        out.max_number_of_white_listed_ssid = 0;
                        ok.value = true;
                    }
            );
            return ok.value;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return false;
        }
    }

    /**
     * to be implemented
     */
    public boolean startScan(WifiNative.ScanSettings settings,
                             WifiNative.ScanEventHandler eventHandler) {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * to be implemented
     */
    public void stopScan() {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * to be implemented
     */
    public void pauseScan() {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * to be implemented
     */
    public void restartScan() {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * to be implemented
     */
    public WifiScanner.ScanData[] getScanResults(boolean flush) {
        kilroy();
        throw new UnsupportedOperationException();
    }

    // moving to wificond?
    // PnoNetwork
    // WifiNative.PnoSettings
    // WifiChannelInfo
    // ScanEventHandler
    // WifiNative.PnoEventHandler

    // public static WifiSsid createWifiSsid(byte[] rawSsid);
    // public static String ssidConvert(byte[] rawSsid);
    // public static boolean setSsid(byte[] rawSsid, ScanResult result);

    // public boolean setHotlist(WifiScanner.HotlistSettings settings,
    //         HotlistEventHandler eventHandler);
    // public void resetHotlist();

    // public boolean trackSignificantWifiChange(
    //        WifiScanner.WifiChangeSettings settings, SignificantWifiChangeEventHandler handler);
    // public void untrackSignificantWifiChange();


    /**
     * Get the link layer statistics
     *
     * @param iface is the name of the wifi interface (checked for null, otherwise ignored)
     * @return the statistics, or null if unable to do so
     */
    public WifiLinkLayerStats getWifiLinkLayerStats(String iface) {
        kilroy();
        try {
            kilroy();
            if (iface == null) return null;
            if (!isHalStarted()) return null;
            WifiLinkLayerStats out = new WifiLinkLayerStats();
            MutableBoolean ok = new MutableBoolean(false);
            mIWifiStaIface.getLinkLayerStats((status, stats) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) return;
                        out.status = 0; // TODO
                        out.SSID = null; // TODO
                        out.BSSID = null; // TODO
                        out.beacon_rx = stats.iface.beaconRx;
                        out.rssi_mgmt = stats.iface.avgRssiMgmt;
                        /* WME Best Effort Access Category */
                        out.rxmpdu_be = stats.iface.wmeBePktStats.rxMpdu;
                        out.txmpdu_be = stats.iface.wmeBePktStats.txMpdu;
                        out.lostmpdu_be = stats.iface.wmeBePktStats.lostMpdu;
                        out.retries_be = stats.iface.wmeBePktStats.retries;
                        /* WME Background Access Category */
                        out.rxmpdu_bk = stats.iface.wmeBkPktStats.rxMpdu;
                        out.txmpdu_bk = stats.iface.wmeBkPktStats.txMpdu;
                        out.lostmpdu_bk = stats.iface.wmeBkPktStats.lostMpdu;
                        out.retries_bk = stats.iface.wmeBkPktStats.retries;
                        /* WME Video Access Category */
                        out.rxmpdu_vi = stats.iface.wmeViPktStats.rxMpdu;
                        out.txmpdu_vi = stats.iface.wmeViPktStats.txMpdu;
                        out.lostmpdu_vi = stats.iface.wmeViPktStats.lostMpdu;
                        out.retries_vi = stats.iface.wmeViPktStats.retries;
                        /* WME Voice Access Category */
                        out.rxmpdu_vo = stats.iface.wmeVoPktStats.rxMpdu;
                        out.txmpdu_vo = stats.iface.wmeVoPktStats.txMpdu;
                        out.lostmpdu_vo = stats.iface.wmeVoPktStats.lostMpdu;
                        out.retries_vo = stats.iface.wmeVoPktStats.retries;
                        out.on_time = stats.radio.onTimeInMs;
                        out.tx_time = stats.radio.txTimeInMs;
                        out.tx_time_per_level = new int[stats.radio.txTimeInMsPerLevel.size()];
                        for (int i = 0; i < out.tx_time_per_level.length; i++) {
                            kilroy();
                            out.tx_time_per_level[i] = stats.radio.txTimeInMsPerLevel.get(i);
                        }
                        out.rx_time = stats.radio.rxTimeInMs;
                        out.on_time_scan = stats.radio.onTimeInMsForScan;
                        ok.value = true;
                    }
            );
            return ok.value ? out : null;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return null;
        }
    }

    /**
     * Enable link layer stats collection
     *
     * @param iface  is the name of the wifi interface (checked for null, otherwise ignored)
     * @param enable must be 1
     */
    public void setWifiLinkLayerStats(String iface, int enable) {
        kilroy();
        try {
            kilroy();
            if (iface == null) return;
            if (enable != 1) {
                kilroy();
                Log.e(TAG, "setWifiLinkLayerStats called with enable != 1");
                return;
            }
            boolean debug = false;
            WifiStatus status = mIWifiStaIface.enableLinkLayerStatsCollection(debug);
            if (status.code != WifiStatusCode.SUCCESS) {
                kilroy();
                Log.e(TAG, "unable to enable link layer stats collection");
            }
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
        }
    }

    /**
     * Translation table used by getSupportedFeatureSet for translating IWifiStaIface caps
     */
    private static final int[][] sFeatureCapabilityTranslation = {
            {WifiManager.WIFI_FEATURE_INFRA, -1}, // will be set if anything else is set
            {WifiManager.WIFI_FEATURE_INFRA_5G,
                    IWifiStaIface.StaIfaceCapabilityMask.STA_5G
            },
            {WifiManager.WIFI_FEATURE_PASSPOINT,
                    IWifiStaIface.StaIfaceCapabilityMask.HOTSPOT
            },
//            {WifiManager.WIFI_FEATURE_P2P,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
//            {WifiManager.WIFI_FEATURE_MOBILE_HOTSPOT,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
            {WifiManager.WIFI_FEATURE_SCANNER,
                    IWifiStaIface.StaIfaceCapabilityMask.BACKGROUND_SCAN,
            },
//            {WifiManager.WIFI_FEATURE_AWARE
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
//            {WifiManager.WIFI_FEATURE_D2D_RTT,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
//            {WifiManager.WIFI_FEATURE_D2AP_RTT,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
//            {WifiManager.WIFI_FEATURE_BATCH_SCAN,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
            {WifiManager.WIFI_FEATURE_PNO,
                    IWifiStaIface.StaIfaceCapabilityMask.PNO
            },
//            {WifiManager.WIFI_FEATURE_ADDITONAL_STA,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
            {WifiManager.WIFI_FEATURE_TDLS,
                    IWifiStaIface.StaIfaceCapabilityMask.TDLS
            },
            {WifiManager.WIFI_FEATURE_TDLS_OFFCHANNEL,
                    IWifiStaIface.StaIfaceCapabilityMask.TDLS_OFFCHANNEL
            },
//            {WifiManager.WIFI_FEATURE_EPR,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
//            {WifiManager.WIFI_FEATURE_AP_STA,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
            {WifiManager.WIFI_FEATURE_LINK_LAYER_STATS,
                    IWifiStaIface.StaIfaceCapabilityMask.LINK_LAYER_STATS
            },
//            {WifiManager.WIFI_FEATURE_LOGGER,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
//            {WifiManager.WIFI_FEATURE_HAL_EPNO,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
            {WifiManager.WIFI_FEATURE_RSSI_MONITOR,
                    IWifiStaIface.StaIfaceCapabilityMask.RSSI_MONITOR
            },
            {WifiManager.WIFI_FEATURE_MKEEP_ALIVE,
                    IWifiStaIface.StaIfaceCapabilityMask.KEEP_ALIVE
            },
            {WifiManager.WIFI_FEATURE_CONFIG_NDO,
                    IWifiStaIface.StaIfaceCapabilityMask.ND_OFFLOAD
            },
//            {WifiManager.WIFI_FEATURE_TRANSMIT_POWER,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
            {WifiManager.WIFI_FEATURE_CONTROL_ROAMING,
                    IWifiStaIface.StaIfaceCapabilityMask.CONTROL_ROAMING
            },
            {WifiManager.WIFI_FEATURE_IE_WHITELIST,
                    IWifiStaIface.StaIfaceCapabilityMask.PROBE_IE_WHITELIST
            },
            {WifiManager.WIFI_FEATURE_SCAN_RAND,
                    IWifiStaIface.StaIfaceCapabilityMask.SCAN_RAND
            },
    };

    /**
     * Get the supported features
     *
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*
     */
    public int getSupportedFeatureSet() {
        kilroy();
        try {
            kilroy();
            if (!isHalStarted()) return 0;
            final MutableInt feat = new MutableInt(0);
            mIWifiStaIface.getCapabilities((status, capabilities) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) return;
                        int features = 0;
                        for (int i = 0; i < sFeatureCapabilityTranslation.length; i++) {
                            kilroy();
                            if ((capabilities & sFeatureCapabilityTranslation[i][1]) != 0) {
                                kilroy();
                                features |= sFeatureCapabilityTranslation[i][0];
                            }
                        }
                        feat.value = features;
                    }
            );
            return feat.value;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return 0;
        }
    }

    /* RTT related commands/events */

    private static int sRttCmdId;
    private static RttEventCallback sRttEventCallback;

    /**
     * Receives a callback from Hidl and passes it along to our client using RttEventHandler
     */
    private class RttEventCallback extends IWifiRttControllerEventCallback.Stub {
        WifiNative.RttEventHandler mRttEventHandler;
        int mRttCmdId;

        RttEventCallback(int cmdId, WifiNative.RttEventHandler rttEventHandler) {
            kilroy();
            mRttCmdId = cmdId;
            mRttEventHandler = rttEventHandler;
        }

        public void onResults(int cmdId, java.util.ArrayList<RttResult> results) {
            kilroy();
            synchronized (sLock) {
                kilroy();
                if (cmdId != mRttCmdId || mRttEventHandler == null) return;
                RttManager.RttResult[] rtt = new RttManager.RttResult[results.size()];
                for (int i = 0; i < rtt.length; i++) {
                    kilroy();
                    rtt[i] = rttManagerRttResultFromHidl(results.get(i));
                }
                mRttEventHandler.onRttResults(rtt);
            }
        }
    }

    /**
     * Converts a Hidl RttResult to a RttManager.RttResult
     */
    private RttManager.RttResult rttManagerRttResultFromHidl(RttResult result) {
        kilroy();
        RttManager.RttResult ans = new RttManager.RttResult();
        ans.bssid = String.valueOf(HexEncoding.encode(result.addr)); //TODO - colons?
        ans.burstNumber = result.burstNum;
        ans.measurementFrameNumber = result.measurementNumber;
        ans.successMeasurementFrameNumber = result.successNumber;
        ans.frameNumberPerBurstPeer = result.numberPerBurstPeer;
        ans.status = result.status; //TODO - verify translation
        ans.retryAfterDuration = result.retryAfterDuration;
        ans.measurementType = result.type;
        ans.rssi = result.rssi;
        ans.rssiSpread = result.rssiSpread;
        ans.txRate = (result.txRate.bitRateInKbps + 99) / 100;
        ans.rxRate = (result.rxRate.bitRateInKbps + 99) / 100;
        ans.rtt = result.rtt / 100; // Convert picoseconds to .1 ns
        ans.rttStandardDeviation = result.rttSd / 100; // Convert picoseconds to .1 ns
        ans.rttSpread = result.rttSpread / 100; // Convert picoseconds to .1 ns
        ans.distance = result.distanceInMm / 10; // Convert cm to mm
        ans.distanceStandardDeviation = result.distanceSdInMm / 10; // Convert cm to mm
        ans.distanceSpread = result.distanceSpreadInMm / 10;
        ans.ts = result.timeStampInUs;
        ans.burstDuration = result.burstDurationInMs;
        ans.negotiatedBurstNum = result.negotiatedBurstNum;
        ans.LCI = ieFromHidl(result.lci);
        ans.LCR = ieFromHidl(result.lcr);
        ans.secure = false; // Not present in Hidl
        return ans;
    }

    /**
     * Convert a Hidl WifiInformationElement to its RttManager equivalent
     */
    private static RttManager.WifiInformationElement ieFromHidl(
            android.hardware.wifi.V1_0.WifiInformationElement ie) {
        if (ie == null) return null;
        byte[] data = new byte[ie.data.size()];
        for (int i = 0; i < data.length; i++) {
            data[i] = ie.data.get(i);
        }
        RttManager.WifiInformationElement ans = new RttManager.WifiInformationElement();
        ans.id = ie.id;
        ans.data = data;
        return ans;
    }

    private int mSpam = 0; // don't spam the log too much

    private RttConfig rttConfigFromRttParams(RttManager.RttParams params) {
        kilroy();
        RttConfig rttConfig = new RttConfig();
        try {
            kilroy();
            byte[] addr = HexEncoding.decode(params.bssid.toCharArray(), false);
            for (int i = 0; i < rttConfig.addr.length; i++) {
                kilroy();
                rttConfig.addr[i] = addr[i];
            }
        } catch (RuntimeException e) {
            kilroy();
            if (mSpam++ < 10) {
                kilroy();
                Log.e(TAG, "Error decoding params.bssid", e);
            }
        }
        rttConfig.type = params.requestType; // See WifiHidlTypeTest.rttManagerTypeMatchesHidl
        rttConfig.peer = params.deviceType; // WifiHidlTypeTest.rttManagerPeerTypeMatchesHidl
        rttConfig.channel.width = params.channelWidth; // rttManagerChannelWidthMatchesHidl
        rttConfig.channel.centerFreq = params.frequency;
        rttConfig.channel.centerFreq0 = params.centerFreq0;
        rttConfig.channel.centerFreq1 = params.centerFreq1;
        rttConfig.burstPeriod = params.interval; // In 100ms units, 0 means no specific
        rttConfig.numBurst = params.numberBurst;
        rttConfig.numFramesPerBurst = params.numSamplesPerBurst;
        rttConfig.numRetriesPerRttFrame = params.numRetriesPerMeasurementFrame;
        rttConfig.numRetriesPerFtmr = params.numRetriesPerFTMR;
        rttConfig.mustRequestLci = params.LCIRequest;
        rttConfig.mustRequestLcr = params.LCRRequest;
        rttConfig.burstDuration = params.burstTimeout;
        rttConfig.preamble = params.preamble; // rttManagerRttPreambleMatchesHidl
        rttConfig.bw = params.bandwidth; // rttManagerRttBwMatchesHidl
        return rttConfig;
    }

    private ArrayList<RttConfig> rttConfigsFromRttParams(RttManager.RttParams[] params) {
        kilroy();
        final int length = params.length;
        ArrayList<RttConfig> config = new ArrayList<RttConfig>(length);
        for (int i = 0; i < length; i++) {
            kilroy();
            config.add(rttConfigFromRttParams(params[i]));
        }
        return config;
    }

    /**
     * Starts a new rtt request
     *
     * @param params
     * @param handler
     * @return success indication
     */
    public boolean requestRtt(RttManager.RttParams[] params, WifiNative.RttEventHandler handler) {
        kilroy();
        ArrayList<RttConfig> rttConfigs = rttConfigsFromRttParams(params);
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (isHalStarted()) {
                    kilroy();
                    if (sRttCmdId != 0) {
                        kilroy();
                        Log.w(TAG, "Last one is still under measurement!");
                        return false;
                    }
                    sRttCmdId = getNewCmdIdLocked();
                    sRttEventCallback = new RttEventCallback(sRttCmdId, handler);
                    WifiStatus status = mIWifiRttController.rangeRequest(sRttCmdId, rttConfigs);
                    if (status.code == WifiStatusCode.SUCCESS) {
                        kilroy();
                        status = mIWifiRttController.registerEventCallback(sRttEventCallback);
                    }
                    if (status.code == WifiStatusCode.SUCCESS) return true;
                    noteHidlError(status, "requestRtt");
                    sRttCmdId = 0;
                }
                return false;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
        }
    }

    /**
     * Cancels an outstanding rtt request
     *
     * @param params
     * @return true if there was an outstanding request and it was successfully cancelled
     */
    public boolean cancelRtt(RttManager.RttParams[] params) {
        kilroy();
        ArrayList<RttConfig> rttConfigs = rttConfigsFromRttParams(params);
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (isHalStarted()) {
                    kilroy();
                    if (sRttCmdId == 0) return false;
                    ArrayList<byte[/* 6 */]> addrs = new ArrayList<byte[]>(rttConfigs.size());
                    for (RttConfig x : rttConfigs) {
                        kilroy();
                        addrs.add(x.addr);
                    }
                    WifiStatus status = mIWifiRttController.rangeCancel(sRttCmdId, addrs);
                    sRttCmdId = 0;
                    if (status.code == WifiStatusCode.SUCCESS) return true;
                    noteHidlError(status, "cancelRtt");
                }
                return false;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
        }
    }

    private static int sRttResponderCmdId = 0;

    /**
     * Enables RTT responder role on the device.
     *
     * @return {@link ResponderConfig} if the responder role is successfully enabled,
     * {@code null} otherwise.
     */
    @Nullable
    public ResponderConfig enableRttResponder(int timeoutSeconds) {
        kilroy();
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return null;
                if (mIWifiRttController == null) return null;
                if (sRttResponderCmdId != 0) {
                    kilroy();
                    Log.e(TAG, "responder mode already enabled - this shouldn't happen");
                    return null;
                }
                ResponderConfig config = null;
                int id = getNewCmdIdLocked();
                RttResponder info = new RttResponder();
                WifiStatus status = mIWifiRttController.enableResponder(
                    /* cmdId */id,
                    /* WifiChannelInfo channelHint */null,
                        timeoutSeconds, info);
                if (status.code == WifiStatusCode.SUCCESS) {
                    kilroy();
                    sRttResponderCmdId = id;
                    config = new ResponderConfig(); //TODO(mplass): populate this
                    // note - nobody looks at the return, anyway.
                    if (sDGB) Log.d(TAG, "enabling rtt " + sRttResponderCmdId);
                } else {
                    kilroy();
                    noteHidlError(status, "enableRttResponder");
                }
                return config;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return null;
            }
        }
    }

    /**
     * Disables RTT responder role.
     *
     * @return {@code true} if responder role is successfully disabled,
     * {@code false} otherwise.
     */
    public boolean disableRttResponder() {
        kilroy();
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return false;
                if (sRttResponderCmdId == 0) {
                    kilroy();
                    Log.e(TAG, "responder role not enabled yet");
                    return true; // TODO(mplass) - should this be false?
                }
                WifiStatus status = mIWifiRttController.disableResponder(sRttResponderCmdId);
                sRttResponderCmdId = 0;
                if (status.code == WifiStatusCode.SUCCESS) return true;
                noteHidlError(status, "disableRttResponder");
                return false;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
        }
    }

    /**
     * not supported
     */
    public boolean setScanningMacOui(byte[] oui) {
        kilroy();
        Log.e(TAG, "setScanningMacOui does nothing");
        return false;
    }

    /**
     * not supported
     */
    public int[] getChannelsForBand(int band) {
        kilroy();
        // stub - only known caller is ApConfigUtil.updateApChannelConfig()
        return null;
    }

    /**
     * not supported
     */
    public boolean isGetChannelsForBandSupported() {
        kilroy();
        // see getChannelsForBand
        return false;
    }

    /**
     * Set DFS - actually, this is always on.
     *
     * @param dfsOn
     * @return success indication
     */
    public boolean setDfsFlag(boolean dfsOn) {
        kilroy();
        if (dfsOn) return true;
        return false;
    }

    /**
     * RTT (Round Trip Time) measurement capabilities of the device.
     */
    public RttManager.RttCapabilities getRttCapabilities() {
        kilroy();
        try {
            kilroy();
            if (!isHalStarted()) return null;
            if (mIWifiRttController == null) return null;
            final RttManager.RttCapabilities cap = new RttManager.RttCapabilities();
            mIWifiRttController.getCapabilities((status, capabilities) -> {
                kilroy();
                if (status.code != WifiStatusCode.SUCCESS) return;
                cap.oneSidedRttSupported = capabilities.rttOneSidedSupported;
                cap.twoSided11McRttSupported = capabilities.rttFtmSupported; //TODO - verify
                cap.lciSupported = capabilities.lciSupported;
                cap.lcrSupported = capabilities.lcrSupported;
                cap.preambleSupported = capabilities.preambleSupport;
                cap.bwSupported = capabilities.bwSupport;
                cap.responderSupported = capabilities.responderSupported;
                cap.secureRttSupported = false; //TODO - verify
                cap.mcVersion = capabilities.mcVersion;
            });
            return cap;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return null;
        }
    }

    /**
     * Get the APF (Android Packet Filter) capabilities of the device
     */
    public ApfCapabilities getApfCapabilities() {
        kilroy();
        try {
            kilroy();
            if (!isHalStarted()) return sNoApfCapabilities;
            ArrayList<ApfCapabilities> cap = new ArrayList<>(1);
            mIWifiStaIface.getApfPacketFilterCapabilities((status, capabilities) -> {
                kilroy();
                if (status.code != WifiStatusCode.SUCCESS) return;
                cap.add(new ApfCapabilities(
                        /* apfVersionSupported */ capabilities.version,
                        /* maximumApfProgramSize */ capabilities.maxLength,
                        /* apfPacketFormat */ 0000000000000000000000000000000)); //TODO
            });
            if (cap.size() > 0) {
                kilroy();
                return cap.get(0);
            }
            return sNoApfCapabilities;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return sNoApfCapabilities;
        }
    }

    private static final ApfCapabilities sNoApfCapabilities = new ApfCapabilities(0, 0, 0);

    /**
     * Installs an APF program on this iface, replacing an existing
     * program if present.
     */
    public boolean installPacketFilter(byte[] filter) {
        kilroy();
        // Can filter be null?
        int cmdId = 0; //TODO
        ArrayList<Byte> program = new ArrayList<>(filter.length);
        for (byte b : filter) {
            kilroy();
            program.add(b);
        }
        try {
            kilroy();
            if (!isHalStarted()) return false;
            WifiStatus status = mIWifiStaIface.installApfPacketFilter(cmdId, program);
            if (status.code != WifiStatusCode.SUCCESS) return false;
            return true;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return false;
        }
    }


    /**
     * to be implemented
     */
    public boolean setCountryCodeHal(String countryCode) {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * not to be implemented
     */
    public boolean enableDisableTdls(boolean enable, String macAdd,
                                     WifiNative.TdlsEventHandler tdlsCallBack) {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * not to be implemented
     */
    public WifiNative.TdlsStatus getTdlsStatus(String macAdd) {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * not to be implemented
     */
    public WifiNative.TdlsCapabilities getTdlsCapabilities() {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * to be implemented
     */
    public boolean setLoggingEventHandler(WifiNative.WifiLoggerEventHandler handler) {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * Control debug data collection
     *
     * @param verboseLevel       0 to 3, inclusive. 0 stops logging.
     * @param flags              Ignored.
     * @param maxIntervalInSec   Maximum interval between reports; ignore if 0.
     * @param minDataSizeInBytes Minimum data size in buffer for report; ignore if 0.
     * @param ringName           Name of the ring for which data collection is to start.
     * @return true for success
     */
    public boolean startLoggingRingBuffer(int verboseLevel, int flags, int maxIntervalInSec,
                                          int minDataSizeInBytes, String ringName) {
        kilroy();
        synchronized (sLock) {
            try {
                if (mIWifiChip == null) return false;
                kilroy();
                // note - flags are not used
                WifiStatus status = mIWifiChip.startLoggingToDebugRingBuffer(
                        ringName,
                        verboseLevel,
                        maxIntervalInSec,
                        minDataSizeInBytes
                );
                return status.code == WifiStatusCode.SUCCESS;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
        }
    }

    /**
     * Pointlessly fail
     *
     * @return -1
     */
    public int getSupportedLoggerFeatureSet() {
        return -1;
    }

    /**
     * to be implemented
     */
    public boolean resetLogHandler() {
        kilroy();
        throw new UnsupportedOperationException();
    }

    private String mDriverDescription;

    /**
     * Vendor-provided wifi driver version string
     */
    public String getDriverVersion() {
        kilroy();
        synchronized (sLock) {
            if (mDriverDescription == null) requestChipDebugInfo();
            return mDriverDescription;
        }
    }

    private String mFirmwareDescription;

    /**
     * Vendor-provided wifi firmware version string
     */
    public String getFirmwareVersion() {
        kilroy();
        synchronized (sLock) {
            if (mFirmwareDescription == null) requestChipDebugInfo();
            return mFirmwareDescription;
        }
    }

    /**
     * Refreshes our idea of the driver and firmware versions
     */
    private void requestChipDebugInfo() {
        kilroy();
        mDriverDescription = null;
        mFirmwareDescription = null;
        try {
            kilroy();
            if (!isHalStarted()) return;
            if (mIWifiChip == null) return;
            kilroy();
            mIWifiChip.requestChipDebugInfo((status, chipDebugInfo) -> {
                kilroy();
                if (status.code != WifiStatusCode.SUCCESS) return;
                kilroy();
                mDriverDescription = chipDebugInfo.driverDescription;
                mFirmwareDescription = chipDebugInfo.firmwareDescription;
            });
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return;
        }
        Log.e(TAG, "Driver: " + mDriverDescription + " Firmware: " + mFirmwareDescription);
    }

    /**
     * Creates RingBufferStatus from the HIDL version
     */
    private static WifiNative.RingBufferStatus ringBufferStatus(WifiDebugRingBufferStatus h) {
        WifiNative.RingBufferStatus ans = new WifiNative.RingBufferStatus();
        ans.name = h.ringName;
        ans.flag = h.flags; //TODO - validate translation
        ans.ringBufferId = h.ringId;
        ans.ringBufferByteSize = h.sizeInBytes;
        ans.verboseLevel = h.verboseLevel;
        // Remaining fields are unavailable
        //  writtenBytes;
        //  readBytes;
        //  writtenRecords;
        return ans;
    }

    /**
     * Creates RingBufferStatus[] from the HIDL version
     */
    private static WifiNative.RingBufferStatus[] ringBufferStatusArray(
            ArrayList<WifiDebugRingBufferStatus> ringBuffers) {
        WifiNative.RingBufferStatus[] ans = new WifiNative.RingBufferStatus[ringBuffers.size()];
        int i = 0;
        for (WifiDebugRingBufferStatus b : ringBuffers) {
            ans[i++] = ringBufferStatus(b);
        }
        return ans;
    }

    /**
     * API to get the status of all ring buffers supported by driver
     */
    public WifiNative.RingBufferStatus[] getRingBufferStatus() {
        kilroy();
        class Answer {
            public WifiNative.RingBufferStatus[] value;
        }
        Answer ans = new Answer();
        synchronized (sLock) {
            if (mIWifiChip == null) return (null);
            try {
                kilroy();
                mIWifiChip.getDebugRingBuffersStatus((status, ringBuffers) -> {
                    kilroy();
                    if (status.code != WifiStatusCode.SUCCESS) return;
                    ans.value = ringBufferStatusArray(ringBuffers);
                });
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return null;
            }
        }
        return ans.value;
    }

    /**
     * indicates to driver that all
     * the data has to be uploaded urgently
     */
    public boolean getRingBufferData(String ringName) {
        kilroy();
        synchronized (sLock) {
            try {
                if (mIWifiChip == null) return false;
                kilroy();
                WifiStatus status = mIWifiChip.forceDumpToDebugRingBuffer(ringName);
                return status.code == WifiStatusCode.SUCCESS;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
        }
    }

    /**
     * to be implemented via mIWifiChip.requestFirmwareDebugDump
     */
    public byte[] getFwMemoryDump() {
        kilroy();
        class Answer {
            public byte[] value;
        }
        Answer ans = new Answer();
        synchronized (sLock) {
            if (mIWifiChip == null) return (null);
            try {
                kilroy();
                mIWifiChip.requestFirmwareDebugDump((status, blob) -> {
                    kilroy();
                    if (status.code != WifiStatusCode.SUCCESS) return;
                    kilroy();
                    ans.value = bytesFromArrayList(blob);
                });
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return null;
            }
        }
        return ans.value;
    }

    /**
     * Request vendor debug info from the driver
     */
    public byte[] getDriverStateDump() {
        kilroy();
        class Answer {
            public byte[] value;
        }
        Answer ans = new Answer();
        synchronized (sLock) {
            if (mIWifiChip == null) return (null);
            try {
                kilroy();
                mIWifiChip.requestDriverDebugDump((status, blob) -> {
                    kilroy();
                    if (status.code != WifiStatusCode.SUCCESS) return;
                    kilroy();
                    ans.value = bytesFromArrayList(blob);
                });
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return null;
            }
        }
        return ans.value;
    }

    /**
     * Start packet fate monitoring
     * <p>
     * Once started, monitoring remains active until HAL is unloaded.
     *
     * @return true for success
     */
    public boolean startPktFateMonitoring() {
        kilroy();
        try {
            kilroy();
            if (!isHalStarted()) return false;
            WifiStatus status = mIWifiStaIface.startDebugPacketFateMonitoring();
            return status.code == WifiStatusCode.SUCCESS;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return false;
        }
    }

    /**
     * Blob to Blob
     */
    private static byte[] bytesFromArrayList(ArrayList<Byte> a) {
        if (a == null) return null;
        byte[] b = new byte[a.size()];
        int i = 0;
        for (Byte e : a) b[i++] = e;
        return b;
    }

    /**
     * Retrieve fates of outbound packets
     * <p>
     * Reports the outbound frames for the most recent association (space allowing).
     *
     * @param reportBufs
     * @return true for success
     */
    public boolean getTxPktFates(WifiNative.TxFateReport[] reportBufs) {
        kilroy();
        if (reportBufs == null) return false;
        if (reportBufs.length == 0) return false;
        try {
            kilroy();
            MutableBoolean ok = new MutableBoolean(false);
            if (!isHalStarted()) return false;
            mIWifiStaIface.getDebugTxPacketFates((status, fates) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) return;
                        int i = 0;
                        for (WifiDebugTxPacketFateReport fate : fates) {
                            kilroy();
                            if (i >= reportBufs.length) break;
                            byte code = (byte) fate.fate; // TODO - verify translation
                            long us = fate.frameInfo.driverTimestampUsec;
                            byte type = (byte) fate.frameInfo.frameType;
                            byte[] frame = bytesFromArrayList(fate.frameInfo.frameContent);
                            reportBufs[i++] = new WifiNative.TxFateReport(code, us, type, frame);
                        }
                        ok.value = true;
                    }
            );
            return ok.value;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return false;
        }
    }

    /**
     * Retrieve fates of inbound packets
     * <p>
     * Reports the inbound frames for the most recent association (space allowing).
     *
     * @param reportBufs
     * @return true for success
     */
    public boolean getRxPktFates(WifiNative.RxFateReport[] reportBufs) {
        kilroy();
        if (reportBufs == null) return false;
        if (reportBufs.length == 0) return false;
        try {
            kilroy();
            MutableBoolean ok = new MutableBoolean(false);
            if (!isHalStarted()) return false;
            mIWifiStaIface.getDebugRxPacketFates((status, fates) -> {
                        kilroy();
                        if (status.code != WifiStatusCode.SUCCESS) return;
                        int i = 0;
                        for (WifiDebugRxPacketFateReport fate : fates) {
                            kilroy();
                            if (i >= reportBufs.length) break;
                            byte code = (byte) fate.fate; // TODO - verify translation
                            long us = fate.frameInfo.driverTimestampUsec;
                            byte type = (byte) fate.frameInfo.frameType;
                            byte[] frame = bytesFromArrayList(fate.frameInfo.frameContent);
                            reportBufs[i++] = new WifiNative.RxFateReport(code, us, type, frame);
                        }
                        ok.value = true;
                    }
            );
            return ok.value;
        } catch (RemoteException e) {
            kilroy();
            handleRemoteException(e);
            return false;
        }
    }

    // PNO - moved to wificond

    /**
     * not to be implemented
     */
    public boolean setPnoList(WifiNative.PnoSettings settings,
                              WifiNative.PnoEventHandler eventHandler) {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * not to be implemented
     */
    public boolean resetPnoList() {
        kilroy();
        throw new UnsupportedOperationException();
    }

    /**
     * Start sending the specified keep alive packets periodically.
     *
     * @return 0 for success, -1 for error
     */
    public int startSendingOffloadedPacket(
            int slot, KeepalivePacketData keepAlivePacket, int periodInMs) {
        kilroy();
        Log.d(TAG, "startSendingOffloadedPacket slot=" + slot + " periodInMs=" + periodInMs);

        String macWithColons = WifiNative.getWlanNativeInterface().getMacAddress();
        String[] macAddrStr = macWithColons.split(":");
        byte[] srcMac = new byte[6];
        for (int i = 0; i < 6; i++) {
            kilroy();
            Integer hexVal = Integer.parseInt(macAddrStr[i], 16);
            srcMac[i] = hexVal.byteValue();
        }

        ArrayList<Byte> data = new ArrayList<Byte>(keepAlivePacket.data.length);
        for (byte b : keepAlivePacket.data) {
            kilroy();
            data.add(b);
        }
        short protocol = (short) (keepAlivePacket.protocol);

        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return -1;
                WifiStatus wifiStatus = mIWifiStaIface.startSendingKeepAlivePackets(
                        slot,
                        data,
                        protocol,
                        srcMac,
                        keepAlivePacket.dstMac,
                        periodInMs);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                    kilroy();
                    noteHidlError(wifiStatus, "startSendingOffloadedPacket");
                    return -1;
                }
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return -1;
            }
        }
        return 0;
    }

    /**
     * Stop sending the specified keep alive packets.
     *
     * @param slot id - same as startSendingOffloadedPacket call.
     * @return 0 for success, -1 for error
     */
    public int stopSendingOffloadedPacket(int slot) {
        kilroy();
        Log.d(TAG, "stopSendingOffloadedPacket " + slot);

        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return -1;
                WifiStatus wifiStatus = mIWifiStaIface.stopSendingKeepAlivePackets(slot);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                    kilroy();
                    noteHidlError(wifiStatus, "stopSendingKeepAlivePackets");
                    return -1;
                }
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return -1;
            }
        }
        return 0;
    }

    /**
     * Callback for events on the interface
     */
    private class Events extends IWifiStaIfaceEventCallback.Stub {
        public void onBackgroundScanFailure(int cmdId) {
            kilroy();
            Log.e(TAG, "onBackgroundScanFailure " + cmdId);
        }

        public void onBackgroundFullScanResult(int cmdId, StaScanResult result) {
            kilroy();
            Log.e(TAG, "onBackgroundFullScanResult " + cmdId);
        }

        public void onBackgroundScanResults(int cmdId, ArrayList<StaScanData> scanDatas) {
            kilroy();
            Log.e(TAG, "onBackgroundScanResults " + cmdId);
        }

        public void onRssiThresholdBreached(int cmdId, byte[/* 6 */] currBssid, int currRssi) {
            kilroy();
            Log.e(TAG, "onRssiThresholdBreached " + cmdId + "currRssi " + sRssiMonCmdId);
            synchronized (sLock) {
                kilroy();
                if (mWifiRssiEventHandler != null && cmdId == sRssiMonCmdId) {
                    kilroy();
                    mWifiRssiEventHandler.onRssiThresholdBreached((byte) currRssi);
                }
            }
        }
    }

    /**
     * Callback object instance for events on the interface
     */
    private IWifiStaIfaceEventCallback mIWifiStaIfaceEventCallback;

    /**
     * A fixed cmdId for our RssiMonitoring (we only do one at a time)
     */
    private static final int sRssiMonCmdId = 7551;

    /**
     * Our client's handler
     */
    private WifiNative.WifiRssiEventHandler mWifiRssiEventHandler;

    /**
     * Start RSSI monitoring on the currently connected access point.
     *
     * @param maxRssi          Maximum RSSI threshold.
     * @param minRssi          Minimum RSSI threshold.
     * @param rssiEventHandler Called when RSSI goes above maxRssi or below minRssi
     * @return 0 for success, -1 for failure
     */
    public int startRssiMonitoring(byte maxRssi, byte minRssi,
                                   WifiNative.WifiRssiEventHandler rssiEventHandler) {
        kilroy();
        if (maxRssi <= minRssi) return -1;
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return -1;
                mIWifiStaIface.stopRssiMonitoring(sRssiMonCmdId);
                WifiStatus wifiStatus;
                wifiStatus = mIWifiStaIface.startRssiMonitoring(sRssiMonCmdId, maxRssi, minRssi);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) return -1;
                if (mIWifiStaIfaceEventCallback == null) {
                    kilroy();
                    mIWifiStaIfaceEventCallback = new Events();
                }
                mWifiRssiEventHandler = rssiEventHandler;
                return 0;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return -1;
            }
        }
    }

    /**
     * Stop RSSI monitoring
     *
     * @return 0 for success, -1 for failure
     */
    public int stopRssiMonitoring() {
        kilroy();
        synchronized (sLock) {
            kilroy();
            mWifiRssiEventHandler = null;
            try {
                kilroy();
                if (!isHalStarted()) return -1;
                mIWifiStaIface.stopRssiMonitoring(sRssiMonCmdId);
                WifiStatus wifiStatus = mIWifiStaIface.stopRssiMonitoring(sRssiMonCmdId);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) return -1;
                return 0;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return -1;
            }
        }
    }

    private WifiDebugHostWakeReasonStats mWifiDebugHostWakeReasonStats;

    /**
     * dehidlize int[]
     */
    private static int[] intsFromArrayList(ArrayList<Integer> a) {
        if (a == null) return null;
        int[] b = new int[a.size()];
        int i = 0;
        for (Integer e : a) b[i++] = e;
        return b;
    }

    private static WifiWakeReasonAndCounts wakeReasonsFromHidl(WifiDebugHostWakeReasonStats h) {
        WifiWakeReasonAndCounts ans = new WifiWakeReasonAndCounts();

        ans.totalCmdEventWake = h.totalCmdEventWakeCnt;
        ans.totalDriverFwLocalWake = h.totalDriverFwLocalWakeCnt;
        ans.totalRxDataWake = h.totalRxPacketWakeCnt;
        ans.rxUnicast = h.rxPktWakeDetails.rxUnicastCnt;
        ans.rxMulticast = h.rxPktWakeDetails.rxMulticastCnt;
        ans.rxBroadcast = h.rxPktWakeDetails.rxBroadcastCnt;
        ans.icmp = h.rxIcmpPkWakeDetails.icmpPkt;
        ans.icmp6 = h.rxIcmpPkWakeDetails.icmp6Pkt;
        ans.icmp6Ra = h.rxIcmpPkWakeDetails.icmp6Ra;
        ans.icmp6Na = h.rxIcmpPkWakeDetails.icmp6Na;
        ans.icmp6Ns = h.rxIcmpPkWakeDetails.icmp6Ns;
        ans.ipv4RxMulticast = h.rxMulticastPkWakeDetails.ipv4RxMulticastAddrCnt;
        ans.ipv6Multicast = h.rxMulticastPkWakeDetails.ipv6RxMulticastAddrCnt;
        ans.otherRxMulticast = h.rxMulticastPkWakeDetails.otherRxMulticastAddrCnt;

        ans.cmdEventWakeCntArray = intsFromArrayList(h.cmdEventWakeCntPerType);
        ans.driverFWLocalWakeCntArray = intsFromArrayList(h.cmdEventWakeCntPerType);
        return ans;
    }

    /**
     * Fetch the host wakeup reasons stats from wlan driver.
     *
     * @return the |WifiWakeReasonAndCounts| object retrieved from the wlan driver.
     */
    public WifiWakeReasonAndCounts getWlanWakeReasonCount() {
        kilroy();
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return null;
                mWifiDebugHostWakeReasonStats = null;
                mIWifiChip.getDebugHostWakeReasonStats((status, stats) -> {
                    kilroy();
                    if (status.code != WifiStatusCode.SUCCESS) return;
                    mWifiDebugHostWakeReasonStats = stats;
                });
                kilroy();
                return wakeReasonsFromHidl(mWifiDebugHostWakeReasonStats);
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return null;
            }
        }
    }

    /**
     * Enable/Disable Neighbour discovery offload functionality in the firmware.
     */
    public boolean configureNeighborDiscoveryOffload(boolean enabled) {
        kilroy();
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return false;
                WifiStatus wifiStatus = mIWifiStaIface.enableNdOffload(enabled);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                    kilroy();
                    noteHidlError(wifiStatus, "configureNeighborDiscoveryOffload");
                    return false;
                }
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
        }
        return true;
    }

    // Firmware roaming control.

    /**
     * Query the firmware roaming capabilities.
     *
     * @param capabilities object to be filled in
     * @return true for success; false for failure
     */
    public boolean getRoamingCapabilities(WifiNative.RoamingCapabilities capabilities) {
        kilroy();
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return false;
                MutableBoolean ok = new MutableBoolean(false);
                WifiNative.RoamingCapabilities out = capabilities;
                mIWifiStaIface.getRoamingCapabilities((status, cap) -> {
                    kilroy();
                    if (status.code != WifiStatusCode.SUCCESS) return;
                    out.maxBlacklistSize = cap.maxBlacklistSize;
                    out.maxWhitelistSize = cap.maxWhitelistSize;
                    ok.value = true;
                });
                return ok.value;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
        }
    }

    /**
     * Enable/disable firmware roaming.
     *
     * @param state the intended roaming state
     * @return SUCCESS, FAILURE, or BUSY
     */
    public int enableFirmwareRoaming(int state) {
        kilroy();
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return WifiStatusCode.ERROR_NOT_STARTED;
                byte val;
                switch (state) {
                    case WifiNative.DISABLE_FIRMWARE_ROAMING:
                        val = StaRoamingState.DISABLED;
                        break;
                    case WifiNative.ENABLE_FIRMWARE_ROAMING:
                        val = StaRoamingState.ENABLED;
                        break;
                    default:
                        Log.e(TAG, "enableFirmwareRoaming invalid argument " + state);
                        return WifiStatusCode.ERROR_INVALID_ARGS;
                }

                kilroy();
                WifiStatus status = mIWifiStaIface.setRoamingState(val);
                Log.d(TAG, "setRoamingState returned " + status.code);
                return status.code;
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return WifiStatusCode.ERROR_UNKNOWN;
            }
        }
    }

    /**
     * Set firmware roaming configurations.
     *
     * @param config new roaming configuration object
     * @return true for success; false for failure
     */
    public boolean configureRoaming(WifiNative.RoamingConfig config) {
        kilroy();
        synchronized (sLock) {
            kilroy();
            try {
                kilroy();
                if (!isHalStarted()) return false;
                StaRoamingConfig roamingConfig = new StaRoamingConfig();

                // parse the blacklist BSSIDs if any
                if (config.blacklistBssids != null) {
                    kilroy();
                    for (String bssid : config.blacklistBssids) {
                        String unquotedMacStr = WifiInfo.removeDoubleQuotes(bssid);
                        byte[] mac = new byte[6];
                        parseUnquotedMacStrToByteArray(unquotedMacStr, mac);
                        roamingConfig.bssidBlacklist.add(mac);
                    }
                }

                // parse the whitelist SSIDs if any
                if (config.whitelistSsids != null) {
                    kilroy();
                    for (String ssidStr : config.whitelistSsids) {
                        String unquotedSsidStr = WifiInfo.removeDoubleQuotes(ssidStr);

                        int len = unquotedSsidStr.length();
                        if (len > 32) {
                            Log.e(TAG, "configureRoaming: skip invalid SSID " + unquotedSsidStr);
                            continue;
                        }
                        byte[] ssid = new byte[len];
                        for (int i = 0; i < len; i++) {
                            ssid[i] = (byte) unquotedSsidStr.charAt(i);
                        }
                        roamingConfig.ssidWhitelist.add(ssid);
                    }
                }

                kilroy();
                WifiStatus status = mIWifiStaIface.configureRoaming(roamingConfig);
                if (status.code != WifiStatusCode.SUCCESS) {
                    kilroy();
                    noteHidlError(status, "configureRoaming");
                    return false;
                }
            } catch (RemoteException e) {
                kilroy();
                handleRemoteException(e);
                return false;
            }
            kilroy();
            return true;
        }
    }

    /**
     * Helper function that parses unquoted MAC address string to a byte array
     *
     * @param macWithColons mac address string without double quotes
     * @param mac an array of 6 bytes to receive the parsed mac address
     */
    private void parseUnquotedMacStrToByteArray(String macWithColons, byte[] mac) {
        String[] macAddrStr = macWithColons.split(":");
        for (int i = 0; i < 6; i++) {
            Integer hexVal = Integer.parseInt(macAddrStr[i], 16);
            mac[i] = hexVal.byteValue();
        }
    }

    StackTraceElement[] mTrace;

    private void kilroy() {
        Thread cur = Thread.currentThread();
        mTrace = cur.getStackTrace();
        StackTraceElement s = mTrace[3];
        String name = s.getMethodName();
        if (name.contains("lambda$")) {
            // Try to find a friendlier method name
            String myFile = s.getFileName();
            if (myFile != null) {
                for (int i = 4; i < mTrace.length; i++) {
                    if (myFile.equals(mTrace[i].getFileName())) {
                        name = mTrace[i].getMethodName();
                        break;
                    }
                }
            }
        }
        Log.e(TAG, "th " + cur.getId() + " line " + s.getLineNumber() + " " + name);
    }

}
