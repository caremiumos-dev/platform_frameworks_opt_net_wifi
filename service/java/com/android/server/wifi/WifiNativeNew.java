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
package com.android.server.wifi;

import android.annotation.Nullable;
import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiRttControllerEventCallback;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.RttConfig;
import android.hardware.wifi.V1_0.RttResponder;
import android.hardware.wifi.V1_0.RttResult;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.net.apf.ApfCapabilities;
import android.net.wifi.RttManager;
import android.net.wifi.RttManager.ResponderConfig;
import android.net.wifi.WifiLinkLayerStats;
import android.net.wifi.WifiManager;
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
 * Native calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 * <p>
 * waitForEvent() is called on the monitor thread for events. All other methods
 * must be serialized from the framework.
 */
public class WifiNativeNew {

    private static boolean sDGB = true; // TODO(mplass): init false for production
    private static final String TAG = "WifiNativeNew";

    void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            sDGB = true;
        } else {
            sDGB = false;
        }
    }

    // TODO(mplass): figure out where we need locking in hidl world. b/33383725
    public static final Object sLock = new Object();

    // HAL command ids
    private static int sCmdId = 1;

    private static int getNewCmdIdLocked() {
        return sCmdId++;
    }

    // Vendor HAL HIDL interface objects.
    private static final String HAL_HIDL_SERVICE_NAME = "wifi";
    private IWifi mHidlWifi;
    //TODO(mplass): the next two should become mutable locals
    private IWifiChip mHidlWifiChip;
    private ArrayList<IWifiChip.ChipMode> mHidlWifiChipAvailableModes = null;
    private IWifiStaIface mIWifiStaIface;
    private IWifiRttController mIWifiRttController;

    // Supplicant HAL HIDL interface objects
    private ISupplicant mHidlSupplicant;
    private ISupplicantStaIface mHidlSupplicantStaIface;

    private void handleRemoteException(RemoteException e) {
        Log.e(TAG, "RemoteException in HIDL call " + e);
    }

    private void noteHidlError(WifiStatus status, String culprit) {
        Log.e(TAG, "Error in " + culprit + " code: " + status.code
                + " (" + status.description + ")");
    }

    /**
     * Bring up the HIDL Vendor HAL and configure for STA mode
     */
    public boolean startHidlHal() {
        /** Get the Wifi Service */
        try {
            mHidlWifi = IWifi.getService(HAL_HIDL_SERVICE_NAME);
        } catch (NoSuchElementException e) {
            Log.e(TAG, "HIDL service not registered: " + HAL_HIDL_SERVICE_NAME);
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
        if (mHidlWifi == null) {
            Log.e(TAG, "Failed to get Wifi HIDL interface objects.");
            return false;
        }
        /** Start the Wifi Service*/
        try {
            WifiStatus startStatus = mHidlWifi.start();
            if (startStatus.code != WifiStatusCode.SUCCESS) {
                noteHidlError(startStatus, "startHidlHal");
                return false;
            }
            /** List available Wifi chips */
            final ArrayList<Integer> chipIdsInternal = new ArrayList<>();
            mHidlWifi.getChipIds((status, chipIds) -> {
                        if (status.code != WifiStatusCode.SUCCESS) {
                            noteHidlError(status, "getChipIds");
                            return;
                        }
                        chipIdsInternal.addAll(chipIds);
                    }
            );
            if (chipIdsInternal.size() != 1) {
                Log.e(TAG, "Expected 1 chip to be present, got: " + chipIdsInternal.size());
                if (chipIdsInternal.size() < 1) return false;
            }
            /** Get chip 0 */

            mHidlWifi.getChip(chipIdsInternal.get(0).intValue(), (status, chip) -> {
                        if (status.code != WifiStatusCode.SUCCESS) {
                            noteHidlError(status, "getChip");
                            return;
                        }
                        mHidlWifiChip = chip;
                    }
            );
            if (mHidlWifiChip == null) {
                Log.e(TAG, "Failed to get WifiChip HIDL interface objects.");
                return false;
            }
            /** Configure Chip Mode for STA */
            mHidlWifiChip.getAvailableModes((status, modes) -> {
                        if (status.code != WifiStatusCode.SUCCESS) {
                            noteHidlError(startStatus, "getAvailableModes");
                            return;
                        }
                        mHidlWifiChipAvailableModes = modes;
                    }
            );
            if (mHidlWifiChipAvailableModes == null) {
                Log.e(TAG, "Failed to get WifiChip HIDL chip available modes.");
                return false;
            }
            int modeId = -1;
            int hits = 0;
            int wantedType = IfaceType.STA;
            for (IWifiChip.ChipMode mode : mHidlWifiChipAvailableModes) {
                for (IWifiChip.ChipIfaceCombination combination : mode.availableCombinations) {
                    for (IWifiChip.ChipIfaceCombinationLimit ifaceLimit : combination.limits) {
                        for (Integer ifaceType : ifaceLimit.types) {
                            if (ifaceType.equals(wantedType)) {
                                modeId = mode.id;
                                hits++;
                            }
                        }
                    }
                }
            }
            if (hits == 0) {
                Log.e(TAG, "Failed to find matching mode");
                return false;
            }
            if (hits > 1 && sDGB) {
                Log.d(TAG, "number of matching modes: " + hits + ", picked modeId " + modeId);
            }
            WifiStatus wifiStatus = mHidlWifiChip.configureChip(modeId);
            if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                noteHidlError(wifiStatus, "configureChip");
                return false;
            }

            /** Create Wifi STA Interface */

            mHidlWifiChip.createStaIface((status, iface) -> {
                        if (status.code != WifiStatusCode.SUCCESS) {
                            noteHidlError(status, "startHidlHal");
                            Log.e(TAG, "Create STA Iface failed. Error code: " + status.code);
                            return;
                        }
                        mIWifiStaIface = iface;
                    }
            );
            if (mIWifiStaIface == null) {
                return false;
            }

            /** Create the RTT controller */
            mIWifiRttController = null;
            mHidlWifiChip.createRttController(
                    mIWifiStaIface,
                    (status, rtt) -> {
                        if (status.code == WifiStatusCode.SUCCESS) {
                            mIWifiRttController = rtt;
                            Log.e(TAG, "Made " + mIWifiRttController);
                        }
                    }
            );
        } catch (RemoteException e) {
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
    public void stopHal() {
        // XXX
    }

    /**
     * Tests whether the HAL is running or not
     */
    public boolean isHalStarted() {
        return mIWifiStaIface != null;
    }

    /**
     * Gets the interface index given a name
     */
    public int queryInterfaceIndex(String interfaceName) {
        // XXX
        return -1;
    }

    /**
     * Gets the interface name given an index
     *
     * @param index of interface
     * @return interface name, or null if index is unknown
     */
    public String getInterfaceName(int index) {
        // XXX
        return "notyet0";
    }

//    /**
//     * Holds the result of getScanCapabilities
//     */
//    public static class ScanCapabilities {
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
        try {
            MutableBoolean ok = new MutableBoolean(false);
            WifiNative.ScanCapabilities out = capabilities;
            if (!isHalStarted()) {
                return false;
            }
            mIWifiStaIface.getBackgroundScanCapabilities((status, cap) -> {
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
            handleRemoteException(e);
            return false;
        }
    }

    // moving to wificond?
    // ChannelSettings
    // BucketSettings
    // HiddenNetwork

    // moving to wificond?
    // PnoNetwork
    // WifiNative.PnoSettings
    // WifiChannelInfo
    // ScanEventHandler
    // WifiNative.PnoEventHandler

    // public static WifiSsid createWifiSsid(byte[] rawSsid);
    // public static String ssidConvert(byte[] rawSsid);
    // public static boolean setSsid(byte[] rawSsid, ScanResult result);

    // public boolean startScan(ScanSettings settings, ScanEventHandler eventHandler);
    // public void stopScan();
    // public void pauseScan();
    // public void restartScan();
    // public WifiScanner.ScanData[] getScanResults(boolean flush);

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
        try {
            if (iface == null) return null;
            if (!isHalStarted()) return null;
            WifiLinkLayerStats out = new WifiLinkLayerStats();
            MutableBoolean ok = new MutableBoolean(false);
            mIWifiStaIface.getLinkLayerStats((status, stats) -> {
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
                            out.tx_time_per_level[i] = stats.radio.txTimeInMsPerLevel.get(i);
                        }
                        out.rx_time = stats.radio.rxTimeInMs;
                        out.on_time_scan = stats.radio.onTimeInMsForScan;
                        ok.value = true;
                    }
            );
            return ok.value ? out : null;
        } catch (RemoteException e) {
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
        try {
            if (iface == null) return;
            if (enable != 1) {
                Log.e(TAG, "setWifiLinkLayerStats called with enable != 1");
                return;
            }
            boolean debug = false;
            WifiStatus status = mIWifiStaIface.enableLinkLayerStatsCollection(debug);
            if (status.code != WifiStatusCode.SUCCESS) {
                Log.e(TAG, "unable to enable link layer stats collection");
            }
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
    }

    /**
     * Translation table used by getSupportedFeatureSet
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
//            {WifiManager.WIFI_FEATURE_CONFIG_NDO,
//                    IWifiStaIface.StaIfaceCapabilityMask.
//            },
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
        try {
            if (!isHalStarted()) return 0;
            final MutableInt feat = new MutableInt(0);
            mIWifiStaIface.getCapabilities((status, capabilities) -> {
                        if (status.code != WifiStatusCode.SUCCESS) return;
                        int features = 0;
                        for (int i = 0; i < sFeatureCapabilityTranslation.length; i++) {
                            if ((capabilities & sFeatureCapabilityTranslation[i][1]) != 0) {
                                features |= sFeatureCapabilityTranslation[i][0];
                            }
                        }
                        feat.value = features;
                    }
            );
            return feat.value;
        } catch (RemoteException e) {
            handleRemoteException(e);
            return 0;
        }
    }

    /* Rtt related commands/events */

//    /**
//     * Interface for handling RTT events
//     */
//    public interface RttEventHandler {
//        /**
//         * rtt result callback
//         */
//        void onRttResults(RttManager.RttResult[] result);
//    }
    private static int sRttCmdId;
    private static RttEventCallback sRttEventCallback;

    /**
     * Receives a callback from Hidl and passes it along to our client using RttEventHandler
     */
    private class RttEventCallback extends IWifiRttControllerEventCallback.Stub {
        WifiNative.RttEventHandler mRttEventHandler;
        int mRttCmdId;
        RttEventCallback(int cmdId, WifiNative.RttEventHandler rttEventHandler) {
            mRttCmdId = cmdId;
            mRttEventHandler = rttEventHandler;
        }
        public void onResults(int cmdId, java.util.ArrayList<RttResult> results) {
            synchronized (sLock) {
                if (cmdId != mRttCmdId || mRttEventHandler == null) return;
                RttManager.RttResult[] rtt = new RttManager.RttResult[results.size()];
                for (int i = 0; i < rtt.length; i++) {
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

    /** Convert a Hidl WifiInformationElement to its RttManager equivalent */
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
        RttConfig rttConfig = new RttConfig();
        try {
            byte[] addr = HexEncoding.decode(params.bssid.toCharArray(), false);
            for (int i = 0; i < rttConfig.addr.length; i++) {
                rttConfig.addr[i] = addr[i];
            }
        } catch (RuntimeException e) {
            if (mSpam++ < 10) {
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
        final int length = params.length;
        ArrayList<RttConfig> config = new ArrayList<RttConfig>(length);
        for (int i = 0; i < length; i++) {
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
        ArrayList<RttConfig> rttConfigs = rttConfigsFromRttParams(params);
        synchronized (sLock) {
            try {
                if (isHalStarted()) {
                    if (sRttCmdId != 0) {
                        Log.w(TAG, "Last one is still under measurement!");
                        return false;
                    }
                    sRttCmdId = getNewCmdIdLocked();
                    sRttEventCallback = new RttEventCallback(sRttCmdId, handler);
                    WifiStatus status = mIWifiRttController.rangeRequest(sRttCmdId, rttConfigs);
                    if (status.code == WifiStatusCode.SUCCESS) {
                        status = mIWifiRttController.registerEventCallback(sRttEventCallback);
                    }
                    if (status.code == WifiStatusCode.SUCCESS) return true;
                    noteHidlError(status, "requestRtt");
                    sRttCmdId = 0;
                }
                return false;
            } catch (RemoteException e) {
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
        ArrayList<RttConfig> rttConfigs = rttConfigsFromRttParams(params);
        synchronized (sLock) {
            try {
                if (isHalStarted()) {
                    if (sRttCmdId == 0) return false;
                    ArrayList<byte[/* 6 */]> addrs = new ArrayList<byte[]>(rttConfigs.size());
                    for (RttConfig x : rttConfigs) {
                        addrs.add(x.addr);
                    }
                    WifiStatus status = mIWifiRttController.rangeCancel(sRttCmdId, addrs);
                    sRttCmdId = 0;
                    if (status.code == WifiStatusCode.SUCCESS) return true;
                    noteHidlError(status, "cancelRtt");
                }
                return false;
            } catch (RemoteException e) {
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
        synchronized (sLock) {
            try {
                if (!isHalStarted()) return null;
                if (mIWifiRttController == null) return null;
                if (sRttResponderCmdId != 0) {
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
                    sRttResponderCmdId = id;
                    config = new ResponderConfig(); //TODO(mplass): populate this
                    // note - nobody looks at the return, anyway.
                    if (sDGB) Log.d(TAG, "enabling rtt " + sRttResponderCmdId);
                } else {
                    noteHidlError(status, "enableRttResponder");
                }
                return config;
            } catch (RemoteException e) {
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
        synchronized (sLock) {
            try {
                if (!isHalStarted()) return false;
                if (sRttResponderCmdId == 0) {
                    Log.e(TAG, "responder role not enabled yet");
                    return true; // TODO(mplass) - should this be false?
                }
                WifiStatus status = mIWifiRttController.disableResponder(sRttResponderCmdId);
                sRttResponderCmdId = 0;
                if (status.code == WifiStatusCode.SUCCESS) return true;
                noteHidlError(status, "disableRttResponder");
                return false;
            } catch (RemoteException e) {
                handleRemoteException(e);
                return false;
            }
        }
    }

    /**
     * not supported
     */
    public boolean setScanningMacOui(byte[] oui) {
        Log.e(TAG, "setScanningMacOui does nothing");
        return false;
    }

    /**
     * not supported
     */
    public int[] getChannelsForBand(int band) {
        // stub - only known caller is ApConfigUtil.updateApChannelConfig()
        return null;
    }

    /**
     * not supported
     */
    public boolean isGetChannelsForBandSupported() {
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
        if (dfsOn) return true;
        return false;
    }

    /** to be implemented */
    public RttManager.RttCapabilities getRttCapabilities() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public ApfCapabilities getApfCapabilities() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean installPacketFilter(byte[] filter) {
        throw new UnsupportedOperationException();
    }


    /** to be implemented */
    public boolean setCountryCodeHal(String countryCode) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean enableDisableTdls(boolean enable, String macAdd,
                                     WifiNative.TdlsEventHandler tdlsCallBack) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public WifiNative.TdlsStatus getTdlsStatus(String macAdd) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public WifiNative.TdlsCapabilities getTdlsCapabilities() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean setLoggingEventHandler(WifiNative.WifiLoggerEventHandler handler) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean startLoggingRingBuffer(int verboseLevel, int flags, int maxInterval,
            int minDataSize, String ringName) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public int getSupportedLoggerFeatureSet() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean resetLogHandler() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public String getDriverVersion() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public String getFirmwareVersion() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public WifiNative.RingBufferStatus[] getRingBufferStatus() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean getRingBufferData(String ringName) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public byte[] getFwMemoryDump() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public byte[] getDriverStateDump() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean startPktFateMonitoring() {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean getTxPktFates(WifiNative.TxFateReport[] reportBufs) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean getRxPktFates(WifiNative.RxFateReport[] reportBufs) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean setPnoList(WifiNative.PnoSettings settings,
                              WifiNative.PnoEventHandler eventHandler) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public boolean resetPnoList() {
        throw new UnsupportedOperationException();
    }

    // PNO - moved to wificond

    /**
     * Start sending the specified keep alive packets periodically.
     *
     * @return 0 for success, -1 for error
     */
    public int startSendingOffloadedPacket(
            int slot, KeepalivePacketData keepAlivePacket, int periodInMs) {
        Log.d(TAG, "startSendingOffloadedPacket slot=" + slot + " periodInMs=" + periodInMs);

        String macWithColons = WifiNative.getWlanNativeInterface().getMacAddress();
        String[] macAddrStr = macWithColons.split(":");
        byte[] srcMac = new byte[6];
        for (int i = 0; i < 6; i++) {
            Integer hexVal = Integer.parseInt(macAddrStr[i], 16);
            srcMac[i] = hexVal.byteValue();
        }

        ArrayList<Byte> data = new ArrayList<Byte>(keepAlivePacket.data.length);
        for (byte b : keepAlivePacket.data) {
            data.add(b);
        }
        short protocol = (short) (keepAlivePacket.protocol);

        synchronized (sLock) {
            try {
                if (!isHalStarted()) return -1;
                WifiStatus wifiStatus = mIWifiStaIface.startSendingKeepAlivePackets(
                        slot,
                        data,
                        protocol,
                        srcMac,
                        keepAlivePacket.dstMac,
                        periodInMs);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                    noteHidlError(wifiStatus, "startSendingOffloadedPacket");
                    return -1;
                }
            } catch (RemoteException e) {
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
        Log.d(TAG, "stopSendingOffloadedPacket " + slot);

        synchronized (sLock) {
            try {
                if (!isHalStarted()) return -1;
                WifiStatus wifiStatus = mIWifiStaIface.stopSendingKeepAlivePackets(slot);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                    noteHidlError(wifiStatus, "stopSendingKeepAlivePackets");
                    return -1;
                }
            } catch (RemoteException e) {
                handleRemoteException(e);
                return -1;
            }
        }
        return 0;
    }

    /** to be implemented */
    public int startRssiMonitoring(byte maxRssi, byte minRssi,
                                   WifiNative.WifiRssiEventHandler rssiEventHandler) {
        throw new UnsupportedOperationException();
    }

    /** to be implemented */
    public int stopRssiMonitoring()  {
        throw new UnsupportedOperationException();
    }

    /**
     * Fetch the host wakeup reasons stats from wlan driver.
     * @return the |WifiWakeReasonAndCounts| object retrieved from the wlan driver.
     */
    public WifiWakeReasonAndCounts getWlanWakeReasonCount() {
        throw new UnsupportedOperationException();
    }

    /**
     * Enable/Disable Neighbour discovery offload functionality in the firmware.
     */
    public boolean configureNeighborDiscoveryOffload(boolean enabled) {
        synchronized (sLock) {
            try {
                if (!isHalStarted()) return false;
                WifiStatus wifiStatus = mIWifiStaIface.enableNdOffload(enabled);
                if (wifiStatus.code != WifiStatusCode.SUCCESS) {
                    noteHidlError(wifiStatus, "configureNeighborDiscoveryOffload");
                    return false;
                }
            } catch (RemoteException e) {
                handleRemoteException(e);
                return false;
            }
        }
        return true;
    }


    private boolean startSupplicantHal() {
        return true;
    }
}
