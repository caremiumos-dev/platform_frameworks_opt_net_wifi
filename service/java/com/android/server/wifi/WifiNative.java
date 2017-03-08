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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.apf.ApfCapabilities;
import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.RttManager;
import android.net.wifi.RttManager.ResponderConfig;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiLinkLayerStats;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiWakeReasonAndCounts;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.Immutable;
import com.android.internal.util.HexDump;
import com.android.server.connectivity.KeepalivePacketData;
import com.android.server.wifi.hotspot2.Utils;
import com.android.server.wifi.util.FrameParser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;


/**
 * Native calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 *
 * {@hide}
 */
public class WifiNative {
    private static boolean DBG = false;
    private static final LocalLog sLocalLog = new LocalLog(8192);
    public @NonNull LocalLog getLocalLog() {
        return sLocalLog;
    }

    private final String mTAG;
    private final String mInterfaceName;
    private final SupplicantStaIfaceHal mSupplicantStaIfaceHal;
    private final SupplicantP2pIfaceHal mSupplicantP2pIfaceHal;
    private final WifiVendorHal mWifiVendorHal;
    private final WificondControl mWificondControl;

    public WifiNative(String interfaceName, WifiVendorHal vendorHal,
                       SupplicantStaIfaceHal staIfaceHal, SupplicantP2pIfaceHal p2pIfaceHal,
                       WificondControl condControl) {
        mTAG = "WifiNative-" + interfaceName;
        mInterfaceName = interfaceName;
        mWifiVendorHal = vendorHal;
        mSupplicantStaIfaceHal = staIfaceHal;
        mSupplicantP2pIfaceHal = p2pIfaceHal;
        mWificondControl = condControl;
    }

    public String getInterfaceName() {
        return mInterfaceName;
    }

    // Note this affects logging on for all interfaces
    void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            DBG = true;
        } else {
            DBG = false;
        }
        if (mWificondControl != null) {
            mWificondControl.enableVerboseLogging(verbose > 0 ? true : false);
        }
        if (mSupplicantStaIfaceHal != null) {
            mSupplicantStaIfaceHal.enableVerboseLogging(verbose > 0);
        }
        if (mWifiVendorHal != null) {
            mWifiVendorHal.enableVerboseLogging(verbose > 0);
        }
    }

    private void localLog(String s) {
        if (sLocalLog != null) sLocalLog.log(mInterfaceName + ": " + s);
    }

   /********************************************************
    * Native Initialization/Deinitialization
    ********************************************************/

   /**
    * Setup wifi native for Client mode operations.
    *
    * 1. Starts the Wifi HAL and configures it in client/STA mode.
    * 2. Setup Wificond to operate in client mode and retrieve the handle to use for client
    * operations.
    *
    * @return An IClientInterface as wificond client interface binder handler.
    * Returns null on failure.
    */
    public IClientInterface setupForClientMode() {
        if (!startHal(true)) {
            // TODO(b/34859006): Handle failures.
            Log.e(mTAG, "Failed to start HAL for client mode");
        }
        return mWificondControl.setupDriverForClientMode();
    }

    /**
     * Setup wifi native for AP mode operations.
     *
     * 1. Starts the Wifi HAL and configures it in AP mode.
     * 2. Setup Wificond to operate in AP mode and retrieve the handle to use for ap operations.
     *
     * @return An IApInterface as wificond Ap interface binder handler.
     * Returns null on failure.
     */
    public IApInterface setupForSoftApMode() {
        if (!startHal(false)) {
            // TODO(b/34859006): Handle failures.
            Log.e(mTAG, "Failed to start HAL for AP mode");
        }
        return mWificondControl.setupDriverForSoftApMode();
    }

    /**
     * Teardown all mode configurations in wifi native.
     *
     * 1. Tears down all the interfaces from Wificond.
     * 2. Stops the Wifi HAL.
     *
     * @return Returns true on success.
     */
    public boolean tearDown() {
        if (!mWificondControl.tearDownInterfaces()) {
            // TODO(b/34859006): Handle failures.
            Log.e(mTAG, "Failed to teardown interfaces from Wificond");
            return false;
        }
        stopHal();
        return true;
    }

    /********************************************************
     * Wificond operations
     ********************************************************/

    /**
    * Disable wpa_supplicant via wificond.
    * @return Returns true on success.
    */
    public boolean disableSupplicant() {
        return mWificondControl.disableSupplicant();
    }

    /**
    * Enable wpa_supplicant via wificond.
    * @return Returns true on success.
    */
    public boolean enableSupplicant() {
        return mWificondControl.enableSupplicant();
    }

    /**
    * Request signal polling to wificond.
    * Returns an SignalPollResult object.
    * Returns null on failure.
    */
    public SignalPollResult signalPoll() {
        return mWificondControl.signalPoll();
    }

    /**
     * Fetch TX packet counters on current connection from wificond.
    * Returns an TxPacketCounters object.
    * Returns null on failure.
    */
    public TxPacketCounters getTxPacketCounters() {
        return mWificondControl.getTxPacketCounters();
    }

    /**
     * Start a scan using wificond for the given parameters.
     * @param freqs list of frequencies to scan for, if null scan all supported channels.
     * @param hiddenNetworkSSIDs List of hidden networks to be scanned for.
     * @return Returns true on success.
     */
    public boolean scan(Set<Integer> freqs, Set<String> hiddenNetworkSSIDs) {
        return mWificondControl.scan(freqs, hiddenNetworkSSIDs);
    }

    /**
     * Fetch the latest scan result from kernel via wificond.
     * @return Returns an ArrayList of ScanDetail.
     * Returns an empty ArrayList on failure.
     */
    public ArrayList<ScanDetail> getScanResults() {
        return mWificondControl.getScanResults();
    }

    /**
     * Start PNO scan.
     * @param pnoSettings Pno scan configuration.
     * @return true on success.
     */
    public boolean startPnoScan(PnoSettings pnoSettings) {
        return mWificondControl.startPnoScan(pnoSettings);
    }

    /**
     * Stop PNO scan.
     * @return true on success.
     */
    public boolean stopPnoScan() {
        return mWificondControl.stopPnoScan();
    }

    /********************************************************
     * Supplicant operations
     ********************************************************/

    /**
     * This method is called repeatedly until the connection to wpa_supplicant is established.
     *
     * @return true if connection is established, false otherwise.
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public boolean connectToStaSupplicant() {
        // Start initialization if not already started.
        if (!mSupplicantStaIfaceHal.isInitializationStarted()
                && !mSupplicantStaIfaceHal.initialize()) {
            return false;
        }
        // Check if the initialization is complete.
        return mSupplicantStaIfaceHal.isInitializationComplete();
    }

    /**
     * This method is called repeatedly until the connection to wpa_supplicant is established.
     *
     * @return true if connection is established, false otherwise.
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public boolean connectToP2pSupplicant() {
        // Start initialization if not already started.
        if (!mSupplicantP2pIfaceHal.isInitializationStarted()
                && !mSupplicantP2pIfaceHal.initialize()) {
            return false;
        }
        // Check if the initialization is complete.
        return mSupplicantP2pIfaceHal.isInitializationComplete();
    }

    public void closeSupplicantConnection() {
        // Nothing to do for HIDL.
    }

    /*
     * Wrappers for supplicant commands
     */
    /**
     * Set supplicant log level
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     */
    public void setSupplicantLogLevel(boolean turnOnVerbose) {
        int logLevel = turnOnVerbose
                ? SupplicantStaIfaceHal.LOG_LEVEL_DEBUG
                : SupplicantStaIfaceHal.LOG_LEVEL_INFO;
        mSupplicantStaIfaceHal.setLogLevel(logLevel);
    }

    /*
     * Convert string to Hexadecimal before passing to wifi native layer
     * In native function "doCommand()" have trouble in converting Unicode character string to UTF8
     * conversion to hex is required because SSIDs can have space characters in them;
     * and that can confuses the supplicant because it uses space charaters as delimiters
     */
    public static String encodeSSID(String ssid) {
        int length = ssid.length();
        if ((length > 1) && (ssid.charAt(0) == '"')
                && (ssid.charAt(length - 1) == '"')) {
            ssid = ssid.substring(1, length - 1);
        }
        return Utils.toHex(ssid.getBytes(StandardCharsets.UTF_8));
    }

    public boolean reconnect() {
        return mSupplicantStaIfaceHal.reconnect();
    }

    public boolean reassociate() {
        return mSupplicantStaIfaceHal.reassociate();
    }

    public boolean disconnect() {
        return mSupplicantStaIfaceHal.disconnect();
    }

    public String getMacAddress() {
        return mSupplicantStaIfaceHal.getMacAddress();
    }

    /**
     * Start filtering out Multicast V4 packets
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     *
     * Multicast filtering rules work as follows:
     *
     * The driver can filter multicast (v4 and/or v6) and broadcast packets when in
     * a power optimized mode (typically when screen goes off).
     *
     * In order to prevent the driver from filtering the multicast/broadcast packets, we have to
     * add a DRIVER RXFILTER-ADD rule followed by DRIVER RXFILTER-START to make the rule effective
     *
     * DRIVER RXFILTER-ADD Num
     *   where Num = 0 - Unicast, 1 - Broadcast, 2 - Mutil4 or 3 - Multi6
     *
     * and DRIVER RXFILTER-START
     * In order to stop the usage of these rules, we do
     *
     * DRIVER RXFILTER-STOP
     * DRIVER RXFILTER-REMOVE Num
     *   where Num is as described for RXFILTER-ADD
     *
     * The  SETSUSPENDOPT driver command overrides the filtering rules
     */
    public boolean startFilteringMulticastV4Packets() {
        return mSupplicantStaIfaceHal.stopRxFilter()
                && mSupplicantStaIfaceHal.removeRxFilter(
                SupplicantStaIfaceHal.RX_FILTER_TYPE_V4_MULTICAST)
                && mSupplicantStaIfaceHal.stopRxFilter();
    }

    /**
     * Stop filtering out Multicast V4 packets.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean stopFilteringMulticastV4Packets() {
        return mSupplicantStaIfaceHal.stopRxFilter()
                && mSupplicantStaIfaceHal.addRxFilter(
                SupplicantStaIfaceHal.RX_FILTER_TYPE_V4_MULTICAST)
                && mSupplicantStaIfaceHal.stopRxFilter();
    }

    /**
     * Start filtering out Multicast V6 packets
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean startFilteringMulticastV6Packets() {
        return mSupplicantStaIfaceHal.stopRxFilter()
                && mSupplicantStaIfaceHal.removeRxFilter(
                SupplicantStaIfaceHal.RX_FILTER_TYPE_V6_MULTICAST)
                && mSupplicantStaIfaceHal.stopRxFilter();
    }

    /**
     * Stop filtering out Multicast V6 packets.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean stopFilteringMulticastV6Packets() {
        return mSupplicantStaIfaceHal.stopRxFilter()
                && mSupplicantStaIfaceHal.addRxFilter(
                SupplicantStaIfaceHal.RX_FILTER_TYPE_V6_MULTICAST)
                && mSupplicantStaIfaceHal.stopRxFilter();
    }

    public static final int BLUETOOTH_COEXISTENCE_MODE_ENABLED     = 0;
    public static final int BLUETOOTH_COEXISTENCE_MODE_DISABLED    = 1;
    public static final int BLUETOOTH_COEXISTENCE_MODE_SENSE       = 2;
    /**
      * Sets the bluetooth coexistence mode.
      *
      * @param mode One of {@link #BLUETOOTH_COEXISTENCE_MODE_DISABLED},
      *            {@link #BLUETOOTH_COEXISTENCE_MODE_ENABLED}, or
      *            {@link #BLUETOOTH_COEXISTENCE_MODE_SENSE}.
      * @return Whether the mode was successfully set.
      */
    public boolean setBluetoothCoexistenceMode(int mode) {
        return mSupplicantStaIfaceHal.setBtCoexistenceMode((byte) mode);
    }

    /**
     * Enable or disable Bluetooth coexistence scan mode. When this mode is on,
     * some of the low-level scan parameters used by the driver are changed to
     * reduce interference with A2DP streaming.
     *
     * @param setCoexScanMode whether to enable or disable this mode
     * @return {@code true} if the command succeeded, {@code false} otherwise.
     */
    public boolean setBluetoothCoexistenceScanMode(boolean setCoexScanMode) {
        return mSupplicantStaIfaceHal.setBtCoexistenceScanModeEnabled(setCoexScanMode);
    }

    public boolean setSuspendOptimizations(boolean enabled) {
        return mSupplicantStaIfaceHal.setSuspendModeEnabled(enabled);
    }

    public boolean setCountryCode(String countryCode) {
        return mSupplicantStaIfaceHal.setCountryCode(countryCode);
    }

    public void startTdls(String macAddr, boolean enable) {
        if (enable) {
            mSupplicantStaIfaceHal.initiateTdlsDiscover(macAddr);
            mSupplicantStaIfaceHal.initiateTdlsSetup(macAddr);
        } else {
            mSupplicantStaIfaceHal.initiateTdlsTeardown(macAddr);
        }
    }

    public boolean startWpsPbc(String bssid) {
        return mSupplicantStaIfaceHal.startWpsPbc(bssid);
    }

    public boolean startWpsPinKeypad(String pin) {
        return mSupplicantStaIfaceHal.startWpsPinKeypad(pin);
    }

    public String startWpsPinDisplay(String bssid) {
        return mSupplicantStaIfaceHal.startWpsPinDisplay(bssid);
    }

    public boolean setExternalSim(boolean external) {
        return mSupplicantStaIfaceHal.setExternalSim(external);
    }

    public boolean simAuthResponse(int id, String type, String response) {
        if ("GSM-AUTH".equals(type)) {
            return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimGsmAuthResponse(response);
        } else if ("UMTS-AUTH".equals(type)) {
            return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimUmtsAuthResponse(response);
        } else if ("UMTS-AUTS".equals(type)) {
            return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimUmtsAuthResponse(response);
        } else {
            return false;
        }
    }

    public boolean simAuthFailedResponse(int id) {
        return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimGsmAuthFailure();
    }

    public boolean umtsAuthFailedResponse(int id) {
        return mSupplicantStaIfaceHal.sendCurrentNetworkEapSimUmtsAuthFailure();
    }

    public boolean simIdentityResponse(int id, String response) {
        return mSupplicantStaIfaceHal.sendCurrentNetworkEapIdentityResponse(response);
    }

    /* Configures an access point connection */
    public boolean startWpsRegistrar(String bssid, String pin) {
        return mSupplicantStaIfaceHal.startWpsRegistrar(bssid, pin);
    }

    public boolean cancelWps() {
        return mSupplicantStaIfaceHal.cancelWps();
    }

    public boolean setDeviceName(String name) {
        return mSupplicantStaIfaceHal.setWpsDeviceName(name);
    }

    public boolean setDeviceType(String type) {
        return mSupplicantStaIfaceHal.setWpsDeviceType(type);
    }

    public boolean setConfigMethods(String cfg) {
        return mSupplicantStaIfaceHal.setWpsConfigMethods(cfg);
    }

    public boolean setManufacturer(String value) {
        return mSupplicantStaIfaceHal.setWpsManufacturer(value);
    }

    public boolean setModelName(String value) {
        return mSupplicantStaIfaceHal.setWpsModelName(value);
    }

    public boolean setModelNumber(String value) {
        return mSupplicantStaIfaceHal.setWpsModelNumber(value);
    }

    public boolean setSerialNumber(String value) {
        return mSupplicantStaIfaceHal.setWpsSerialNumber(value);
    }

    public void setPowerSave(boolean enabled) {
        mSupplicantStaIfaceHal.setPowerSave(enabled);
    }

    /**
     * "sta" prioritizes STA connection over P2P and "p2p" prioritizes
     * P2P connection over STA
     */
    public boolean setConcurrencyPriority(boolean isStaHigherPriority) {
        return mSupplicantStaIfaceHal.setConcurrencyPriority(isStaHigherPriority);
    }


    /** WifiSupplicantControl methods. TODO: These should use HIDL soon. */
    /**
     * Migrate all the configured networks from wpa_supplicant.
     *
     * @param configs       Map of configuration key to configuration objects corresponding to all
     *                      the networks.
     * @param networkExtras Map of extra configuration parameters stored in wpa_supplicant.conf
     * @return Max priority of all the configs.
     */
    public boolean migrateNetworksFromSupplicant(Map<String, WifiConfiguration> configs,
                                                 SparseArray<Map<String, String>> networkExtras) {
        return mSupplicantStaIfaceHal.loadNetworks(configs, networkExtras);
    }

    /**
     * Add the provided network configuration to wpa_supplicant and initiate connection to it.
     * This method does the following:
     * 1. Triggers disconnect command to wpa_supplicant (if |shouldDisconnect| is true).
     * 2. Remove any existing network in wpa_supplicant.
     * 3. Add a new network to wpa_supplicant.
     * 4. Save the provided configuration to wpa_supplicant.
     * 5. Select the new network in wpa_supplicant.
     * 6. Triggers reconnect command to wpa_supplicant.
     *
     * @param configuration WifiConfiguration parameters for the provided network.
     * @param shouldDisconnect whether to trigger a disconnection or not.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean connectToNetwork(WifiConfiguration configuration, boolean shouldDisconnect) {
        return mSupplicantStaIfaceHal.connectToNetwork(configuration, shouldDisconnect);
    }

    /**
     * Initiates roaming to the already configured network in wpa_supplicant. If the network
     * configuration provided does not match the already configured network, then this triggers
     * a new connection attempt (instead of roam).
     * 1. First check if we're attempting to connect to the same network as we currently have
     * configured.
     * 2. Set the new bssid for the network in wpa_supplicant.
     * 3. Triggers reassociate command to wpa_supplicant.
     *
     * @param configuration WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean roamToNetwork(WifiConfiguration configuration) {
        return mSupplicantStaIfaceHal.roamToNetwork(configuration);
    }

    /**
     * Get the framework network ID corresponding to the provided supplicant network ID for the
     * network configured in wpa_supplicant.
     *
     * @param supplicantNetworkId network ID in wpa_supplicant for the network.
     * @return Corresponding framework network ID if found, -1 if network not found.
     */
    public int getFrameworkNetworkId(int supplicantNetworkId) {
        return supplicantNetworkId;
    }

    /**
     * Remove all the networks.
     *
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean removeAllNetworks() {
        return mSupplicantStaIfaceHal.removeAllNetworks();
    }

    /**
     * Set the BSSID for the currently configured network in wpa_supplicant.
     *
     * @return true if successful, false otherwise.
     */
    public boolean setConfiguredNetworkBSSID(String bssid) {
        return mSupplicantStaIfaceHal.setCurrentNetworkBssid(bssid);
    }

    /**
     * Initiate ANQP query.
     *
     * @param bssid BSSID of the AP to be queried
     * @param anqpIds Set of anqp IDs.
     * @param hs20Subtypes Set of HS20 subtypes.
     * @return true on success, false otherwise.
     */
    public boolean requestAnqp(String bssid, Set<Integer> anqpIds, Set<Integer> hs20Subtypes) {
        if (bssid == null || ((anqpIds == null || anqpIds.isEmpty())
                && (hs20Subtypes == null || hs20Subtypes.isEmpty()))) {
            return false;
        }
        ArrayList<Short> anqpIdList = new ArrayList<>();
        for (Integer anqpId : anqpIds) {
            anqpIdList.add(anqpId.shortValue());
        }
        ArrayList<Integer> hs20SubtypeList = new ArrayList<>();
        hs20SubtypeList.addAll(hs20Subtypes);
        return mSupplicantStaIfaceHal.initiateAnqpQuery(bssid, anqpIdList, hs20SubtypeList);
    }

    /**
     * Request a passpoint icon file |filename| from the specified AP |bssid|.
     * @param bssid BSSID of the AP
     * @param fileName name of the icon file
     * @return true if request is sent successfully, false otherwise
     */
    public boolean requestIcon(String  bssid, String fileName) {
        if (bssid == null || fileName == null) return false;
        return mSupplicantStaIfaceHal.initiateHs20IconQuery(bssid, fileName);
    }

    public String getNfcWpsConfigurationToken(int netId) {
        return mSupplicantStaIfaceHal.getCurrentNetworkWpsNfcConfigurationToken();
    }

    /**
     * Populate list of available networks or update existing list.
     *
     * @return true, if list has been modified.
     */
    public boolean p2pListNetworks(WifiP2pGroupList groups) {
        return mSupplicantP2pIfaceHal.loadGroups(groups);
    }

    public boolean startWpsPbc(String iface, String bssid) {
        return mSupplicantP2pIfaceHal.startWpsPbc(iface, bssid);
    }

    public boolean startWpsPinKeypad(String iface, String pin) {
        return mSupplicantP2pIfaceHal.startWpsPinKeypad(iface, pin);
    }

    public String startWpsPinDisplay(String iface, String bssid) {
        return mSupplicantP2pIfaceHal.startWpsPinDisplay(iface, bssid);
    }

    public boolean removeP2pNetwork(int netId) {
        return mSupplicantP2pIfaceHal.removeNetwork(netId);
    }

    public boolean setP2pDeviceName(String name) {
        return mSupplicantP2pIfaceHal.setWpsDeviceName(name);
    }

    public boolean setP2pDeviceType(String type) {
        return mSupplicantP2pIfaceHal.setWpsDeviceType(type);
    }

    public boolean setP2pSsidPostfix(String postfix) {
        return mSupplicantP2pIfaceHal.setSsidPostfix(postfix);
    }

    public boolean setP2pGroupIdle(String iface, int time) {
        return mSupplicantP2pIfaceHal.setGroupIdle(iface, time);
    }

    public boolean setP2pPowerSave(String iface, boolean enabled) {
        return mSupplicantP2pIfaceHal.setPowerSave(iface, enabled);
    }

    public boolean setWfdEnable(boolean enable) {
        return mSupplicantP2pIfaceHal.enableWfd(enable);
    }

    public boolean setWfdDeviceInfo(String hex) {
        return mSupplicantP2pIfaceHal.setWfdDeviceInfo(hex);
    }

    public boolean p2pFind() {
        return mSupplicantP2pIfaceHal.find();
    }

    public boolean p2pFind(int timeout) {
        if (timeout <= 0) {
            return p2pFind();
        }
        return mSupplicantP2pIfaceHal.find(timeout);
    }

    public boolean p2pStopFind() {
        return mSupplicantP2pIfaceHal.stopFind();
    }

    public boolean p2pExtListen(boolean enable, int period, int interval) {
        if (enable && interval < period) {
            return false;
        }
        return mSupplicantP2pIfaceHal.configureExtListen(enable, period, interval);
    }

    public boolean p2pSetChannel(int lc, int oc) {
        if (DBG) Log.d(mTAG, "p2pSetChannel: lc="+lc+", oc="+oc);
        return mSupplicantP2pIfaceHal.setListenChannel(lc, oc);
    }

    public boolean p2pFlush() {
        return mSupplicantP2pIfaceHal.flush();
    }

    public String p2pConnect(WifiP2pConfig config, boolean joinExistingGroup) {
        if (config == null) return null;
        return mSupplicantP2pIfaceHal.connect(config, joinExistingGroup);
    }

    public boolean p2pCancelConnect() {
        return mSupplicantP2pIfaceHal.cancelConnect();
    }

    public boolean p2pProvisionDiscovery(WifiP2pConfig config) {
        if (config == null) return false;
        return mSupplicantP2pIfaceHal.provisionDiscovery(config);
    }

    public boolean p2pGroupAdd(boolean persistent) {
        return mSupplicantP2pIfaceHal.groupAdd(persistent);
    }

    public boolean p2pGroupAdd(int netId) {
        return mSupplicantP2pIfaceHal.groupAdd(netId, true);
    }

    public boolean p2pGroupRemove(String iface) {
        if (TextUtils.isEmpty(iface)) return false;
        return mSupplicantP2pIfaceHal.groupRemove(iface);
    }

    public boolean p2pReject(String deviceAddress) {
        return mSupplicantP2pIfaceHal.reject(deviceAddress);
    }

    /* Invite a peer to a group */
    public boolean p2pInvite(WifiP2pGroup group, String deviceAddress) {
        if (TextUtils.isEmpty(deviceAddress)) return false;
        return mSupplicantP2pIfaceHal.invite(group, deviceAddress);
    }

    /* Reinvoke a persistent connection */
    public boolean p2pReinvoke(int netId, String deviceAddress) {
        if (TextUtils.isEmpty(deviceAddress) || netId < 0) return false;
        return mSupplicantP2pIfaceHal.reinvoke(netId, deviceAddress);
    }

    public String p2pGetSsid(String deviceAddress) {
        return mSupplicantP2pIfaceHal.getSsid(deviceAddress);
    }

    public String p2pGetDeviceAddress() {
        return mSupplicantP2pIfaceHal.getDeviceAddress();
    }

    public int getGroupCapability(String deviceAddress) {
        return mSupplicantP2pIfaceHal.getGroupCapability(deviceAddress);
    }

    public boolean p2pServiceAdd(WifiP2pServiceInfo servInfo) {
        return mSupplicantP2pIfaceHal.serviceAdd(servInfo);
    }

    public boolean p2pServiceDel(WifiP2pServiceInfo servInfo) {
        return mSupplicantP2pIfaceHal.serviceRemove(servInfo);
    }

    public boolean p2pServiceFlush() {
        return mSupplicantP2pIfaceHal.serviceFlush();
    }

    public String p2pServDiscReq(String addr, String query) {
        return mSupplicantP2pIfaceHal.requestServiceDiscovery(addr, query);
    }

    public boolean p2pServDiscCancelReq(String id) {
        return mSupplicantP2pIfaceHal.cancelServiceDiscovery(id);
    }

    /* Set the current mode of miracast operation.
     *  0 = disabled
     *  1 = operating as source
     *  2 = operating as sink
     */
    public void setMiracastMode(int mode) {
        mSupplicantP2pIfaceHal.setMiracastMode(mode);
    }

    /*
     * NFC-related calls
     */
    public String getNfcHandoverRequest() {
        return mSupplicantP2pIfaceHal.getNfcHandoverRequest();
    }

    public String getNfcHandoverSelect() {
        return mSupplicantP2pIfaceHal.getNfcHandoverSelect();
    }

    public boolean initiatorReportNfcHandover(String selectMessage) {
        return mSupplicantP2pIfaceHal.initiatorReportNfcHandover(selectMessage);
    }

    public boolean responderReportNfcHandover(String requestMessage) {
        return mSupplicantP2pIfaceHal.responderReportNfcHandover(requestMessage);
    }

    public String getP2pClientList(int netId) {
        // TODO(b/36042785): Add HIDL method.
        return null;
    }

    public boolean setP2pClientList(int netId, String list) {
        // TODO(b/36042785): Add HIDL method.
        return false;
    }

    /**
     * Save the current configuration to wpa_supplicant.conf.
     */
    public boolean saveConfig() {
        // TODO(b/36042785): Add HIDL method.
        return false;
    }

    /* kernel logging support */
    private static native byte[] readKernelLogNative();

    synchronized public String readKernelLog() {
        byte[] bytes = readKernelLogNative();
        if (bytes != null) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            try {
                CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
                return decoded.toString();
            } catch (CharacterCodingException cce) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        } else {
            return "*** failed to read kernel log ***";
        }
    }

    /********************************************************
     * Vendor HAL operations
     ********************************************************/

    /**
     * Initializes the vendor HAL. This is just used to initialize the {@link HalDeviceManager}.
     */
    public boolean initializeVendorHal() {
        return mWifiVendorHal.initialize();
    }

    /**
     * Bring up the Vendor HAL and configure for STA mode or AP mode.
     *
     * @param isStaMode true to start HAL in STA mode, false to start in AP mode.
     */
    public boolean startHal(boolean isStaMode) {
        return mWifiVendorHal.startVendorHal(isStaMode);
    }

    public void stopHal() {
        mWifiVendorHal.stopVendorHal();
    }

    public boolean isHalStarted() {
        return mWifiVendorHal.isHalStarted();
    }

    // TODO: Change variable names to camel style.
    public static class ScanCapabilities {
        public int  max_scan_cache_size;
        public int  max_scan_buckets;
        public int  max_ap_cache_per_scan;
        public int  max_rssi_sample_size;
        public int  max_scan_reporting_threshold;
        public int  max_hotlist_bssids;
        public int  max_significant_wifi_change_aps;
        public int  max_bssid_history_entries;
        public int  max_number_epno_networks;
        public int  max_number_epno_networks_by_ssid;
        public int  max_number_of_white_listed_ssid;
    }

    public boolean getScanCapabilities(ScanCapabilities capabilities) {
        return mWifiVendorHal.getScanCapabilities(capabilities);
    }

    public static class ChannelSettings {
        public int frequency;
        public int dwell_time_ms;
        public boolean passive;
    }

    public static class BucketSettings {
        public int bucket;
        public int band;
        public int period_ms;
        public int max_period_ms;
        public int step_count;
        public int report_events;
        public int num_channels;
        public ChannelSettings[] channels;
    }

    /**
     * Network parameters for hidden networks to be scanned for.
     */
    public static class HiddenNetwork {
        public String ssid;

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            } else if (otherObj == null || getClass() != otherObj.getClass()) {
                return false;
            }
            HiddenNetwork other = (HiddenNetwork) otherObj;
            return Objects.equals(ssid, other.ssid);
        }

        @Override
        public int hashCode() {
            return (ssid == null ? 0 : ssid.hashCode());
        }
    }

    public static class ScanSettings {
        public int base_period_ms;
        public int max_ap_per_scan;
        public int report_threshold_percent;
        public int report_threshold_num_scans;
        public int num_buckets;
        /* Not used for bg scans. Only works for single scans. */
        public HiddenNetwork[] hiddenNetworks;
        public BucketSettings[] buckets;
    }

    /**
     * Network parameters to start PNO scan.
     */
    public static class PnoNetwork {
        public String ssid;
        public byte flags;
        public byte auth_bit_field;

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            } else if (otherObj == null || getClass() != otherObj.getClass()) {
                return false;
            }
            PnoNetwork other = (PnoNetwork) otherObj;
            return ((Objects.equals(ssid, other.ssid)) && (flags == other.flags)
                    && (auth_bit_field == other.auth_bit_field));
        }

        @Override
        public int hashCode() {
            int result = (ssid == null ? 0 : ssid.hashCode());
            result ^= ((int) flags * 31) + ((int) auth_bit_field << 8);
            return result;
        }
    }

    /**
     * Parameters to start PNO scan. This holds the list of networks which are going to used for
     * PNO scan.
     */
    public static class PnoSettings {
        public int min5GHzRssi;
        public int min24GHzRssi;
        public int initialScoreMax;
        public int currentConnectionBonus;
        public int sameNetworkBonus;
        public int secureBonus;
        public int band5GHzBonus;
        public int periodInMs;
        public boolean isConnected;
        public PnoNetwork[] networkList;
    }

    /**
     * Result of a signal poll.
     */
    public static class SignalPollResult {
        // RSSI value in dBM.
        public int currentRssi;
        //Transmission bit rate in Mbps.
        public int txBitrate;
        // Association frequency in MHz.
        public int associationFrequency;
    }

    /**
     * WiFi interface transimission counters.
     */
    public static class TxPacketCounters {
        // Number of successfully transmitted packets.
        public int txSucceeded;
        // Number of tramsmission failures.
        public int txFailed;
    }

    public static interface ScanEventHandler {
        /**
         * Called for each AP as it is found with the entire contents of the beacon/probe response.
         * Only called when WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT is specified.
         */
        void onFullScanResult(ScanResult fullScanResult, int bucketsScanned);
        /**
         * Callback on an event during a gscan scan.
         * See WifiNative.WIFI_SCAN_* for possible values.
         */
        void onScanStatus(int event);
        /**
         * Called with the current cached scan results when gscan is paused.
         */
        void onScanPaused(WifiScanner.ScanData[] data);
        /**
         * Called with the current cached scan results when gscan is resumed.
         */
        void onScanRestarted();
    }

    /**
     * Handler to notify the occurrence of various events during PNO scan.
     */
    public interface PnoEventHandler {
        /**
         * Callback to notify when one of the shortlisted networks is found during PNO scan.
         * @param results List of Scan results received.
         */
        void onPnoNetworkFound(ScanResult[] results);

        /**
         * Callback to notify when the PNO scan schedule fails.
         */
        void onPnoScanFailed();
    }

    /* scan status, keep these values in sync with gscan.h */
    public static final int WIFI_SCAN_RESULTS_AVAILABLE = 0;
    public static final int WIFI_SCAN_THRESHOLD_NUM_SCANS = 1;
    public static final int WIFI_SCAN_THRESHOLD_PERCENT = 2;
    public static final int WIFI_SCAN_FAILED = 3;

    public boolean startScan(ScanSettings settings, ScanEventHandler eventHandler) {
        return mWifiVendorHal.startScan(settings, eventHandler);
    }

    public void stopScan() {
        mWifiVendorHal.stopScan();
    }

    public void pauseScan() {
        mWifiVendorHal.pauseScan();
    }

    public void restartScan() {
        mWifiVendorHal.restartScan();
    }

    public WifiScanner.ScanData[] getScanResults(boolean flush) {
        return mWifiVendorHal.getScanResults();
    }

    public static interface HotlistEventHandler {
        void onHotlistApFound (ScanResult[] result);
        void onHotlistApLost  (ScanResult[] result);
    }

    public boolean setHotlist(WifiScanner.HotlistSettings settings,
            HotlistEventHandler eventHandler) {
        Log.e(mTAG, "setHotlist not supported");
        return false;
    }

    public void resetHotlist() {
        Log.e(mTAG, "resetHotlist not supported");
    }

    public static interface SignificantWifiChangeEventHandler {
        void onChangesFound(ScanResult[] result);
    }

    public boolean trackSignificantWifiChange(
            WifiScanner.WifiChangeSettings settings, SignificantWifiChangeEventHandler handler) {
        Log.e(mTAG, "trackSignificantWifiChange not supported");
        return false;
    }

    public void untrackSignificantWifiChange() {
        Log.e(mTAG, "untrackSignificantWifiChange not supported");
    }

    public WifiLinkLayerStats getWifiLinkLayerStats(String iface) {
        return mWifiVendorHal.getWifiLinkLayerStats();
    }

    public void setWifiLinkLayerStats(String iface, int enable) {
        // Nothing to do here. Link layer stats is enabled when the HAL is started.
    }

    public int getSupportedFeatureSet() {
        return mWifiVendorHal.getSupportedFeatureSet();
    }

    public static interface RttEventHandler {
        void onRttResults(RttManager.RttResult[] result);
    }

    public boolean requestRtt(
            RttManager.RttParams[] params, RttEventHandler handler) {
        return mWifiVendorHal.requestRtt(params, handler);
    }

    public boolean cancelRtt(RttManager.RttParams[] params) {
        return mWifiVendorHal.cancelRtt(params);
    }

    /**
     * Enable RTT responder role on the device. Returns {@link ResponderConfig} if the responder
     * role is successfully enabled, {@code null} otherwise.
     */
    @Nullable
    public ResponderConfig enableRttResponder(int timeoutSeconds) {
        return mWifiVendorHal.enableRttResponder(timeoutSeconds);
    }

    /**
     * Disable RTT responder role. Returns {@code true} if responder role is successfully disabled,
     * {@code false} otherwise.
     */
    public boolean disableRttResponder() {
        return mWifiVendorHal.disableRttResponder();
    }

    public boolean setScanningMacOui(byte[] oui) {
        return mWifiVendorHal.setScanningMacOui(oui);
    }

    public int [] getChannelsForBand(int band) {
        return mWifiVendorHal.getChannelsForBand(band);
    }

    public boolean isGetChannelsForBandSupported(){
        return mWifiVendorHal.isGetChannelsForBandSupported();
    }

    public boolean setDfsFlag(boolean dfsOn) {
        return mWifiVendorHal.setDfsFlag(dfsOn);
    }

    public RttManager.RttCapabilities getRttCapabilities() {
        return mWifiVendorHal.getRttCapabilities();
    }

    public ApfCapabilities getApfCapabilities() {
        return mWifiVendorHal.getApfCapabilities();
    }

    public boolean installPacketFilter(byte[] filter) {
        return mWifiVendorHal.installPacketFilter(filter);
    }

    public boolean setCountryCodeHal(String countryCode) {
        return mWifiVendorHal.setCountryCodeHal(countryCode);
    }

    //---------------------------------------------------------------------------------
    /* Wifi Logger commands/events */
    public static interface WifiLoggerEventHandler {
        void onRingBufferData(RingBufferStatus status, byte[] buffer);
        void onWifiAlert(int errorCode, byte[] buffer);
    }

    public boolean setLoggingEventHandler(WifiLoggerEventHandler handler) {
        return mWifiVendorHal.setLoggingEventHandler(handler);
    }

    public boolean startLoggingRingBuffer(int verboseLevel, int flags, int maxInterval,
            int minDataSize, String ringName){
        return mWifiVendorHal.startLoggingRingBuffer(
                verboseLevel, flags, maxInterval, minDataSize, ringName);
    }

    public int getSupportedLoggerFeatureSet() {
        return mWifiVendorHal.getSupportedLoggerFeatureSet();
    }

    public boolean resetLogHandler() {
        return mWifiVendorHal.resetLogHandler();
    }

    public String getDriverVersion() {
        return mWifiVendorHal.getDriverVersion();
    }


    public String getFirmwareVersion() {
        return mWifiVendorHal.getFirmwareVersion();
    }

    public static class RingBufferStatus{
        String name;
        int flag;
        int ringBufferId;
        int ringBufferByteSize;
        int verboseLevel;
        int writtenBytes;
        int readBytes;
        int writtenRecords;

        // Bit masks for interpreting |flag|
        public static final int HAS_BINARY_ENTRIES = (1 << 0);
        public static final int HAS_ASCII_ENTRIES = (1 << 1);
        public static final int HAS_PER_PACKET_ENTRIES = (1 << 2);

        @Override
        public String toString() {
            return "name: " + name + " flag: " + flag + " ringBufferId: " + ringBufferId +
                    " ringBufferByteSize: " +ringBufferByteSize + " verboseLevel: " +verboseLevel +
                    " writtenBytes: " + writtenBytes + " readBytes: " + readBytes +
                    " writtenRecords: " + writtenRecords;
        }
    }

    public RingBufferStatus[] getRingBufferStatus() {
        return mWifiVendorHal.getRingBufferStatus();
    }

    public boolean getRingBufferData(String ringName) {
        return mWifiVendorHal.getRingBufferData(ringName);
    }

    public byte[] getFwMemoryDump() {
        return mWifiVendorHal.getFwMemoryDump();
    }

    /** Fetch the driver state, for driver debugging. */
    public byte[] getDriverStateDump() {
        return mWifiVendorHal.getDriverStateDump();
    }

    //---------------------------------------------------------------------------------
    /* Packet fate API */

    @Immutable
    abstract static class FateReport {
        final static int USEC_PER_MSEC = 1000;
        // The driver timestamp is a 32-bit counter, in microseconds. This field holds the
        // maximal value of a driver timestamp in milliseconds.
        final static int MAX_DRIVER_TIMESTAMP_MSEC = (int) (0xffffffffL / 1000);
        final static SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

        final byte mFate;
        final long mDriverTimestampUSec;
        final byte mFrameType;
        final byte[] mFrameBytes;
        final long mEstimatedWallclockMSec;

        FateReport(byte fate, long driverTimestampUSec, byte frameType, byte[] frameBytes) {
            mFate = fate;
            mDriverTimestampUSec = driverTimestampUSec;
            mEstimatedWallclockMSec =
                    convertDriverTimestampUSecToWallclockMSec(mDriverTimestampUSec);
            mFrameType = frameType;
            mFrameBytes = frameBytes;
        }

        public String toTableRowString() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            FrameParser parser = new FrameParser(mFrameType, mFrameBytes);
            dateFormatter.setTimeZone(TimeZone.getDefault());
            pw.format("%-15s  %12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    mDriverTimestampUSec,
                    dateFormatter.format(new Date(mEstimatedWallclockMSec)),
                    directionToString(), fateToString(), parser.mMostSpecificProtocolString,
                    parser.mTypeString, parser.mResultString);
            return sw.toString();
        }

        public String toVerboseStringWithPiiAllowed() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            FrameParser parser = new FrameParser(mFrameType, mFrameBytes);
            pw.format("Frame direction: %s\n", directionToString());
            pw.format("Frame timestamp: %d\n", mDriverTimestampUSec);
            pw.format("Frame fate: %s\n", fateToString());
            pw.format("Frame type: %s\n", frameTypeToString(mFrameType));
            pw.format("Frame protocol: %s\n", parser.mMostSpecificProtocolString);
            pw.format("Frame protocol type: %s\n", parser.mTypeString);
            pw.format("Frame length: %d\n", mFrameBytes.length);
            pw.append("Frame bytes");
            pw.append(HexDump.dumpHexString(mFrameBytes));  // potentially contains PII
            pw.append("\n");
            return sw.toString();
        }

        /* Returns a header to match the output of toTableRowString(). */
        public static String getTableHeader() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.format("\n%-15s  %-12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    "Time usec", "Walltime", "Direction", "Fate", "Protocol", "Type", "Result");
            pw.format("%-15s  %-12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    "---------", "--------", "---------", "----", "--------", "----", "------");
            return sw.toString();
        }

        protected abstract String directionToString();

        protected abstract String fateToString();

        private static String frameTypeToString(byte frameType) {
            switch (frameType) {
                case WifiLoggerHal.FRAME_TYPE_UNKNOWN:
                    return "unknown";
                case WifiLoggerHal.FRAME_TYPE_ETHERNET_II:
                    return "data";
                case WifiLoggerHal.FRAME_TYPE_80211_MGMT:
                    return "802.11 management";
                default:
                    return Byte.toString(frameType);
            }
        }

        /**
         * Converts a driver timestamp to a wallclock time, based on the current
         * BOOTTIME to wallclock mapping. The driver timestamp is a 32-bit counter of
         * microseconds, with the same base as BOOTTIME.
         */
        private static long convertDriverTimestampUSecToWallclockMSec(long driverTimestampUSec) {
            final long wallclockMillisNow = System.currentTimeMillis();
            final long boottimeMillisNow = SystemClock.elapsedRealtime();
            final long driverTimestampMillis = driverTimestampUSec / USEC_PER_MSEC;

            long boottimeTimestampMillis = boottimeMillisNow % MAX_DRIVER_TIMESTAMP_MSEC;
            if (boottimeTimestampMillis < driverTimestampMillis) {
                // The 32-bit microsecond count has wrapped between the time that the driver
                // recorded the packet, and the call to this function. Adjust the BOOTTIME
                // timestamp, to compensate.
                //
                // Note that overflow is not a concern here, since the result is less than
                // 2 * MAX_DRIVER_TIMESTAMP_MSEC. (Given the modulus operation above,
                // boottimeTimestampMillis must be less than MAX_DRIVER_TIMESTAMP_MSEC.) And, since
                // MAX_DRIVER_TIMESTAMP_MSEC is an int, 2 * MAX_DRIVER_TIMESTAMP_MSEC must fit
                // within a long.
                boottimeTimestampMillis += MAX_DRIVER_TIMESTAMP_MSEC;
            }

            final long millisSincePacketTimestamp = boottimeTimestampMillis - driverTimestampMillis;
            return wallclockMillisNow - millisSincePacketTimestamp;
        }
    }

    /**
     * Represents the fate information for one outbound packet.
     */
    @Immutable
    public static final class TxFateReport extends FateReport {
        TxFateReport(byte fate, long driverTimestampUSec, byte frameType, byte[] frameBytes) {
            super(fate, driverTimestampUSec, frameType, frameBytes);
        }

        @Override
        protected String directionToString() {
            return "TX";
        }

        @Override
        protected String fateToString() {
            switch (mFate) {
                case WifiLoggerHal.TX_PKT_FATE_ACKED:
                    return "acked";
                case WifiLoggerHal.TX_PKT_FATE_SENT:
                    return "sent";
                case WifiLoggerHal.TX_PKT_FATE_FW_QUEUED:
                    return "firmware queued";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_INVALID:
                    return "firmware dropped (invalid frame)";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_NOBUFS:
                    return "firmware dropped (no bufs)";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_OTHER:
                    return "firmware dropped (other)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED:
                    return "driver queued";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_INVALID:
                    return "driver dropped (invalid frame)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_NOBUFS:
                    return "driver dropped (no bufs)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_OTHER:
                    return "driver dropped (other)";
                default:
                    return Byte.toString(mFate);
            }
        }
    }

    /**
     * Represents the fate information for one inbound packet.
     */
    @Immutable
    public static final class RxFateReport extends FateReport {
        RxFateReport(byte fate, long driverTimestampUSec, byte frameType, byte[] frameBytes) {
            super(fate, driverTimestampUSec, frameType, frameBytes);
        }

        @Override
        protected String directionToString() {
            return "RX";
        }

        @Override
        protected String fateToString() {
            switch (mFate) {
                case WifiLoggerHal.RX_PKT_FATE_SUCCESS:
                    return "success";
                case WifiLoggerHal.RX_PKT_FATE_FW_QUEUED:
                    return "firmware queued";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER:
                    return "firmware dropped (filter)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID:
                    return "firmware dropped (invalid frame)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_NOBUFS:
                    return "firmware dropped (no bufs)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_OTHER:
                    return "firmware dropped (other)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_QUEUED:
                    return "driver queued";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_FILTER:
                    return "driver dropped (filter)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_INVALID:
                    return "driver dropped (invalid frame)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_NOBUFS:
                    return "driver dropped (no bufs)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_OTHER:
                    return "driver dropped (other)";
                default:
                    return Byte.toString(mFate);
            }
        }
    }

    /**
     * Ask the HAL to enable packet fate monitoring. Fails unless HAL is started.
     */
    public boolean startPktFateMonitoring() {
        return mWifiVendorHal.startPktFateMonitoring();
    }

    /**
     * Fetch the most recent TX packet fates from the HAL. Fails unless HAL is started.
     */
    public boolean getTxPktFates(TxFateReport[] reportBufs) {
        return mWifiVendorHal.getTxPktFates(reportBufs);
    }

    /**
     * Fetch the most recent RX packet fates from the HAL. Fails unless HAL is started.
     */
    public boolean getRxPktFates(RxFateReport[] reportBufs) {
        return mWifiVendorHal.getRxPktFates(reportBufs);
    }

    /**
     * Set the PNO settings & the network list in HAL to start PNO.
     * @param settings PNO settings and network list.
     * @param eventHandler Handler to receive notifications back during PNO scan.
     * @return true if success, false otherwise
     */
    public boolean setPnoList(PnoSettings settings, PnoEventHandler eventHandler) {
        Log.e(mTAG, "setPnoList not supported");
        return false;
    }

    /**
     * Reset the PNO settings in HAL to stop PNO.
     * @return true if success, false otherwise
     */
    public boolean resetPnoList() {
        Log.e(mTAG, "resetPnoList not supported");
        return false;
    }

    public int
    startSendingOffloadedPacket(int slot, KeepalivePacketData keepAlivePacket, int period) {
        Log.d(mTAG, "startSendingOffloadedPacket slot=" + slot + " period=" + period);
        String[] macAddrStr = getMacAddress().split(":");
        byte[] srcMac = new byte[6];
        for (int i = 0; i < 6; i++) {
            Integer hexVal = Integer.parseInt(macAddrStr[i], 16);
            srcMac[i] = hexVal.byteValue();
        }
        return mWifiVendorHal.startSendingOffloadedPacket(
                slot, srcMac, keepAlivePacket, period);
    }

    public int
    stopSendingOffloadedPacket(int slot) {
        Log.d(mTAG, "stopSendingOffloadedPacket " + slot);
        return mWifiVendorHal.stopSendingOffloadedPacket(slot);
    }

    public static interface WifiRssiEventHandler {
        void onRssiThresholdBreached(byte curRssi);
    }

    public int startRssiMonitoring(byte maxRssi, byte minRssi,
                                   WifiRssiEventHandler rssiEventHandler) {
        Log.d(mTAG, "startRssiMonitoring: maxRssi=" + maxRssi + " minRssi=" + minRssi);
        return mWifiVendorHal.startRssiMonitoring(maxRssi, minRssi, rssiEventHandler);
    }

    public int stopRssiMonitoring() {
        return mWifiVendorHal.stopRssiMonitoring();
    }

    /**
     * Fetch the host wakeup reasons stats from wlan driver.
     * @return the |WifiWakeReasonAndCounts| object retrieved from the wlan driver.
     */
    public WifiWakeReasonAndCounts getWlanWakeReasonCount() {
        return mWifiVendorHal.getWlanWakeReasonCount();
    }

    public boolean configureNeighborDiscoveryOffload(boolean enabled) {
        final String logMsg =  "configureNeighborDiscoveryOffload(" + enabled + ")";
        return mWifiVendorHal.configureNeighborDiscoveryOffload(enabled);
    }

    // Firmware roaming control.

    /**
     * Class to retrieve firmware roaming capability parameters.
     */
    public static class RoamingCapabilities {
        public int  maxBlacklistSize;
        public int  maxWhitelistSize;
    }

    /**
     * Query the firmware roaming capabilities.
     */
    public boolean getRoamingCapabilities(RoamingCapabilities capabilities) {
        Log.d(mTAG, "getRoamingCapabilities ");
        return mWifiVendorHal.getRoamingCapabilities(capabilities);
    }

    /**
     * Macros for controlling firmware roaming.
     */
    public static final int DISABLE_FIRMWARE_ROAMING = 0;
    public static final int ENABLE_FIRMWARE_ROAMING = 1;

    /**
     * Enable/disable firmware roaming.
     */
    public int enableFirmwareRoaming(int state) {
        Log.d(mTAG, "enableFirmwareRoaming: state =" + state);
        return mWifiVendorHal.enableFirmwareRoaming(state);
    }

    /**
     * Class for specifying the roaming configurations.
     */
    public static class RoamingConfig {
        public ArrayList<String> blacklistBssids;
        public ArrayList<String> whitelistSsids;
    }

    /**
     * Set firmware roaming configurations.
     */
    public boolean configureRoaming(RoamingConfig config) {
        Log.d(mTAG, "configureRoaming ");
        return mWifiVendorHal.configureRoaming(config);
    }

    /**
     * Reset firmware roaming configuration.
     */
    public boolean resetRoamingConfiguration() {
        Log.d(mTAG, "resetRoamingConfiguration ");
        // Pass in an empty RoamingConfig object which translates to zero size
        // blacklist and whitelist to reset the firmware roaming configuration.
        return mWifiVendorHal.configureRoaming(new RoamingConfig());
    }
}
