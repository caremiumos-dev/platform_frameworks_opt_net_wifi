/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.net.apf.ApfCapabilities;
import android.net.wifi.RttManager;
import android.net.wifi.RttManager.ResponderConfig;
import android.net.wifi.WifiLinkLayerStats;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiWakeReasonAndCounts;

/**
 * Native calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 *
 * waitForEvent() is called on the monitor thread for events. All other methods
 * must be serialized from the framework.
 *
 */
public class WifiNative extends WifiNotiveTypes {

    private static long sWifiHalHandle = 0;             /* used by JNI to save wifi_handle */
    private static long[] sWifiIfaceHandles = null;     /* used by JNI to save interface handles */

    static boolean isHalStarted() {
         return (sWifiHalHandle != 0);
    }

    static void halIsStopped() {
        sWifiHalHandle = 0;
        sWifiIfaceHandles = null;
    }

    /* Register native functions */
    static {
        /* Native functions are defined in libwifi-service.so */
        System.loadLibrary("wifi-service");
        registerNatives();
    }

    static native int registerNatives();

    /*
     * Supplicant management
     */

    native static boolean connectToSupplicantNative();

    native static void closeSupplicantConnectionNative();

    /**
     * Wait for the supplicant to send an event, returning the event string.
     * @return the event string sent by the supplicant.
     */
    native static String waitForEventNative();

    /*
     * Supplicant Command Primitives
     */
    static native boolean doBooleanCommandNative(String command);

    static native int doIntCommandNative(String command);

    static native String doStringCommandNative(String command);

    static native byte[] readKernelLogNative();

    /* WIFI HAL support */

    static native boolean startHalNative();
    static native void stopHalNative();
    static native void waitForHalEventNative();

    static native int getInterfacesNative();

    static native String getInterfaceNameNative(int index);

    static native boolean getScanCapabilitiesNative(
            int iface, ScanCapabilities capabilities);

    static native boolean startScanNative(int iface, int id, ScanSettings settings);
    static native boolean stopScanNative(int iface, int id);
    static native WifiScanner.ScanData[] getScanResultsNative(int iface, boolean flush);
    static native WifiLinkLayerStats getWifiLinkLayerStatsNative(int iface);
    static native void setWifiLinkLayerStatsNative(int iface, int enable);

    native static boolean setHotlistNative(int iface, int id,
            WifiScanner.HotlistSettings settings);
    native static boolean resetHotlistNative(int iface, int id);


    static native boolean trackSignificantWifiChangeNative(
            int iface, int id, WifiScanner.WifiChangeSettings settings);
    static native boolean untrackSignificantWifiChangeNative(int iface, int id);

    public static native int getSupportedFeatureSetNative(int iface);

    static native boolean requestRangeNative(
            int iface, int id, RttManager.RttParams[] params);
    static native boolean cancelRangeRequestNative(
            int iface, int id, RttManager.RttParams[] params);


    static native ResponderConfig enableRttResponderNative(int iface, int commandId,
            int timeoutSeconds, WifiChannelInfo channelHint);

    static native boolean disableRttResponderNative(int iface, int commandId);

    static native boolean setScanningMacOuiNative(int iface, byte[] oui);

    static native int[] getChannelsForBandNative(
            int iface, int band);

    static native boolean isGetChannelsForBandSupportedNative();
    static native boolean setDfsFlagNative(int iface, boolean dfsOn);
    static native RttManager.RttCapabilities getRttCapabilitiesNative(int iface);
    static native ApfCapabilities getApfCapabilitiesNative(int iface);
    static native boolean installPacketFilterNative(int iface, byte[] filter);
    static native boolean setCountryCodeHalNative(int iface, String CountryCode);
    static native boolean enableDisableTdlsNative(int iface, boolean enable,
                                                          String macAddr);
    static native TdlsStatus getTdlsStatusNative(int iface, String macAddr);
    static native TdlsCapabilities getTdlsCapabilitiesNative(int iface);
    static native boolean setLoggingEventHandlerNative(int iface, int id);
    static native boolean startLoggingRingBufferNative(int iface, int verboseLevel,
                                                               int flags, int minIntervalSec ,int minDataSize, String ringName);
    static native int getSupportedLoggerFeatureSetNative(int iface);
    static native boolean resetLogHandlerNative(int iface, int id);
    static native String getDriverVersionNative(int iface);
    static native String getFirmwareVersionNative(int iface);
    static native RingBufferStatus[] getRingBufferStatusNative(int iface);
    static native boolean getRingBufferDataNative(int iface, String ringName);
    static native boolean getFwMemoryDumpNative(int iface);
    static native byte[] getDriverStateDumpNative(int iface);
    static native int startPktFateMonitoringNative(int iface);


    static native int getTxPktFatesNative(int iface, TxFateReport[] reportBufs);

    static native int getRxPktFatesNative(int iface, RxFateReport[] reportBufs);

    static native boolean setPnoListNative(int iface, int id, PnoSettings settings);

    static native boolean resetPnoListNative(int iface, int id);

    native static int startSendingOffloadedPacketNative(int iface, int idx,
                                    byte[] srcMac, byte[] dstMac, byte[] pktData, int period);

    native static int stopSendingOffloadedPacketNative(int iface, int idx);

    native static int startRssiMonitoringNative(int iface, int id,
                                        byte maxRssi, byte minRssi);

    native static int stopRssiMonitoringNative(int iface, int idx);

    static native WifiWakeReasonAndCounts getWlanWakeReasonCountNative(int iface);

    static native int configureNeighborDiscoveryOffload(int iface, boolean enabled);

}
