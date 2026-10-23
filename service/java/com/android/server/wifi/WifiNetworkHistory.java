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

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiSsid;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.android.server.net.DelayedDiskWrite;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists and restores the per-network history (scan details, network
 * selection status and assorted counters).
 *
 * @hide
 */
public class WifiNetworkHistory {
    private static final String TAG = "WifiNetworkHistory";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    static final String NETWORK_HISTORY_CONFIG_FILE =
            Environment.getDataDirectory() + "/misc/wifi/networkHistory.txt";
    static final String CONFIG_KEY = "CONFIG";
    static final String CREATOR_UID_KEY = "CREATOR_UID_KEY";
    static final String SHARED_KEY = "SHARED";

    private static final String SSID_KEY = "SSID";
    private static final String BSSID_KEY = "BSSID";
    private static final String BSSID_KEY_END = "/BSSID";
    private static final String CONFIG_BSSID_KEY = "CONFIG_BSSID";
    private static final String FQDN_KEY = "FQDN";
    private static final String DEFAULT_GW_KEY = "DEFAULT_GW";
    private static final String SELF_ADDED_KEY = "SELF_ADDED";
    private static final String DID_SELF_ADD_KEY = "DID_SELF_ADD";
    private static final String NO_INTERNET_ACCESS_REPORTS_KEY = "NO_INTERNET_ACCESS_REPORTS";
    private static final String NO_INTERNET_ACCESS_EXPECTED_KEY = "NO_INTERNET_ACCESS_EXPECTED";
    private static final String VALIDATED_INTERNET_ACCESS_KEY = "VALIDATED_INTERNET_ACCESS";
    private static final String CREATION_TIME_KEY = "CREATION_TIME";
    private static final String UPDATE_TIME_KEY = "UPDATE_TIME";
    private static final String EPHEMERAL_KEY = "EPHEMERAL";
    private static final String METERED_HINT_KEY = "METERED_HINT";
    private static final String METERED_OVERRIDE_KEY = "METERED_OVERRIDE";
    private static final String USE_EXTERNAL_SCORES_KEY = "USE_EXTERNAL_SCORES";
    private static final String SCORER_OVERRIDE_KEY = "SCORER_OVERRIDE";
    private static final String SCORER_OVERRIDE_AND_SWITCH_KEY = "SCORER_OVERRIDE_AND_SWITCH";
    private static final String NUM_ASSOCIATION_KEY = "NUM_ASSOCIATION";
    private static final String CONNECT_UID_KEY = "CONNECT_UID_KEY";
    private static final String UPDATE_UID_KEY = "UPDATE_UID";
    private static final String PEER_CONFIGURATION_KEY = "PEER_CONFIGURATION";
    private static final String NETWORK_SELECTION_STATUS_KEY = "NETWORK_SELECTION_STATUS";
    private static final String NETWORK_SELECTION_DISABLE_REASON_KEY =
            "NETWORK_SELECTION_DISABLE_REASON";
    private static final String CHOICE_KEY = "CHOICE";
    private static final String CHOICE_TIME_KEY = "CHOICE_TIME";
    private static final String LINK_KEY = "LINK";
    private static final String RSSI_KEY = "RSSI";
    private static final String FREQ_KEY = "FREQ";
    private static final String DATE_KEY = "DATE";
    private static final String DELETED_EPHEMERAL_KEY = "DELETED_EPHEMERAL";
    private static final String CREATOR_NAME_KEY = "CREATOR_NAME";
    private static final String UPDATE_NAME_KEY = "UPDATE_NAME";
    private static final String USER_APPROVED_KEY = "USER_APPROVED";
    private static final String HAS_EVER_CONNECTED_KEY = "HAS_EVER_CONNECTED";
    private static final String AUTH_KEY = "AUTH";
    private static final String PRIORITY_KEY = "PRIORITY";
    private static final String BSSID_STATUS_KEY = "BSSID_STATUS";
    private static final String SEPARATOR = ":  ";
    private static final String NL = "\n";

    Context mContext;
    HashSet<String> mLostConfigsDbg = new HashSet<>();
    protected final DelayedDiskWrite mWriter;

    public WifiNetworkHistory(Context context, DelayedDiskWrite writer) {
        mContext = context;
        mWriter = writer;
    }

    private ScanDetailCache getScanDetailCache(WifiConfiguration config,
            Map<Integer, ScanDetailCache> scanDetailCaches) {
        if (config == null || scanDetailCaches == null) {
            return null;
        }
        ScanDetailCache cache = scanDetailCaches.get(config.networkId);
        if (cache != null || config.networkId == WifiConfiguration.INVALID_NETWORK_ID) {
            return cache;
        }
        cache = new ScanDetailCache(config, WifiConfigManager.SCAN_CACHE_ENTRIES_MAX_SIZE,
                WifiConfigManager.SCAN_CACHE_ENTRIES_TRIM_SIZE);
        scanDetailCaches.put(config.networkId, cache);
        return cache;
    }

    private static String makeString(BitSet set, String[] strings) {
        StringBuffer sb = new StringBuffer();
        BitSet printed = set.get(0, strings.length);
        int next = -1;
        while (true) {
            next = printed.nextSetBit(next + 1);
            if (next == -1) {
                break;
            }
            sb.append(strings[next].replace('_', '-')).append(' ');
        }
        if (printed.cardinality() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    public boolean isValid(WifiConfiguration config) {
        if (config.allowedKeyManagement == null) {
            return false;
        }
        if (config.allowedKeyManagement.cardinality() > 1) {
            if (config.allowedKeyManagement.cardinality() != 2
                    || !config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)) {
                return false;
            }
            if (!config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)
                    && !config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                return false;
            }
        }
        return true;
    }

    protected void log(String s) {
        Log.d(TAG, s);
    }

    protected void logd(String s) {
        Log.d(TAG, s);
    }

    protected void loge(String s) {
        loge(s, false);
    }

    protected void loge(String s, boolean stack) {
        if (stack) {
            Log.e(TAG, s + " stack:" + Thread.currentThread().getStackTrace()[2].getMethodName()
                    + " - " + Thread.currentThread().getStackTrace()[3].getMethodName()
                    + " - " + Thread.currentThread().getStackTrace()[4].getMethodName()
                    + " - " + Thread.currentThread().getStackTrace()[5].getMethodName());
        } else {
            Log.e(TAG, s);
        }
    }

    protected void logv(String s) {
        Log.v(TAG, s);
    }

    public void readNetworkHistory(Map<String, WifiConfiguration> configs,
            Map<Integer, ScanDetailCache> scanDetailCaches, Set<String> deletedEphemeralSSIDs) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(NETWORK_HISTORY_CONFIG_FILE)));
            try {
                String line;
                String ssid = null;
                String bssid = null;
                int freq = 0;
                int level = 0;
                long tsf = 0;
                int err = WifiConfiguration.INVALID_RSSI;
                String caps = "";
                WifiConfiguration config = null;
                while (true) {
                    line = in.readUTF();
                    if (line == null) {
                        break;
                    }
                    int index = line.indexOf(':');
                    if (index < 0) {
                        continue;
                    }
                    String key = line.substring(0, index).trim();
                    String value = line.substring(index + 1).trim();
                    if (key.equals(CONFIG_KEY)) {
                        WifiConfiguration config2 = configs.get(value);
                        if (config2 == null) {
                            Log.e(TAG, "readNetworkHistory didnt find netid for hash="
                                    + Integer.toString(value.hashCode()) + " key: " + value);
                            mLostConfigsDbg.add(value);
                            config = null;
                        } else if (config2.creatorName == null || config2.lastUpdateName == null) {
                            config2.creatorName = mContext.getPackageManager().getNameForUid(
                                    android.os.Process.SYSTEM_UID);
                            config2.lastUpdateName = config2.creatorName;
                            Log.w(TAG, "Upgrading network " + config2.networkId + " to "
                                    + config2.creatorName);
                            config = config2;
                        } else {
                            config = config2;
                        }
                    } else if (config != null) {
                        NetworkSelectionStatus status = config.getNetworkSelectionStatus();
                        if (key.equals(SSID_KEY)) {
                            if (config.isPasspoint()) {
                                continue;
                            }
                            if (config.SSID == null || config.SSID.equals(value)) {
                                config.SSID = value;
                            } else {
                                loge("Error parsing network history file, mismatched SSIDs");
                                config = null;
                            }
                        } else if (key.equals(CONFIG_BSSID_KEY)) {
                            config.BSSID = value.equals("null") ? null : value;
                        } else if (key.equals(FQDN_KEY)) {
                            config.FQDN = value.equals("null") ? null : value;
                        } else if (key.equals(DEFAULT_GW_KEY)) {
                            config.defaultGwMacAddress = value;
                        } else if (key.equals(SELF_ADDED_KEY)) {
                            config.selfAdded = Boolean.parseBoolean(value);
                        } else if (key.equals(DID_SELF_ADD_KEY)) {
                            config.didSelfAdd = Boolean.parseBoolean(value);
                        } else if (key.equals(NO_INTERNET_ACCESS_REPORTS_KEY)) {
                            config.numNoInternetAccessReports = Integer.parseInt(value);
                        } else if (key.equals(VALIDATED_INTERNET_ACCESS_KEY)) {
                            config.validatedInternetAccess = Boolean.parseBoolean(value);
                        } else if (key.equals(NO_INTERNET_ACCESS_EXPECTED_KEY)) {
                            config.noInternetAccessExpected = Boolean.parseBoolean(value);
                        } else if (key.equals(CREATION_TIME_KEY)) {
                            config.creationTime = value;
                        } else if (key.equals(UPDATE_TIME_KEY)) {
                            config.updateTime = value;
                        } else if (key.equals(EPHEMERAL_KEY)) {
                            config.ephemeral = Boolean.parseBoolean(value);
                        } else if (key.equals(METERED_HINT_KEY)) {
                            config.meteredHint = Boolean.parseBoolean(value);
                        } else if (key.equals(METERED_OVERRIDE_KEY)) {
                            config.meteredOverride = Integer.parseInt(value);
                        } else if (key.equals(USE_EXTERNAL_SCORES_KEY)) {
                            config.useExternalScores = Boolean.parseBoolean(value);
                        } else if (key.equals(CREATOR_UID_KEY)) {
                            config.creatorUid = Integer.parseInt(value);
                        } else if (key.equals(SCORER_OVERRIDE_KEY)) {
                            config.numScorerOverride = Integer.parseInt(value);
                        } else if (key.equals(SCORER_OVERRIDE_AND_SWITCH_KEY)) {
                            config.numScorerOverrideAndSwitchedNetwork = Integer.parseInt(value);
                        } else if (key.equals(NUM_ASSOCIATION_KEY)) {
                            config.numAssociation = Integer.parseInt(value);
                        } else if (key.equals(CONNECT_UID_KEY)) {
                            config.lastConnectUid = Integer.parseInt(value);
                        } else if (key.equals(UPDATE_UID_KEY)) {
                            config.lastUpdateUid = Integer.parseInt(value);
                        } else if (key.equals(PEER_CONFIGURATION_KEY)) {
                            config.peerWifiConfiguration = value;
                        } else if (key.equals(NETWORK_SELECTION_STATUS_KEY)) {
                            int networkStatus = Integer.parseInt(value);
                            if (networkStatus == 1) {
                                networkStatus = 0;
                            }
                            status.setNetworkSelectionStatus(networkStatus);
                        } else if (key.equals(NETWORK_SELECTION_DISABLE_REASON_KEY)) {
                            status.setNetworkSelectionDisableReason(Integer.parseInt(value));
                        } else if (key.equals(CHOICE_KEY)) {
                            status.setConnectChoice(value);
                        } else if (key.equals(CHOICE_TIME_KEY)) {
                            status.setConnectChoiceTimestamp(Long.parseLong(value));
                        } else if (key.equals(LINK_KEY)) {
                            if (config.linkedConfigurations == null) {
                                config.linkedConfigurations = new HashMap<>();
                            } else {
                                config.linkedConfigurations.put(value, -1);
                            }
                        } else if (key.equals(BSSID_KEY)) {
                            ssid = null;
                            bssid = null;
                            freq = 0;
                            level = 0;
                            tsf = 0;
                            err = WifiConfiguration.INVALID_RSSI;
                            caps = "";
                        } else if (key.equals(RSSI_KEY)) {
                            err = Integer.parseInt(value);
                        } else if (key.equals(FREQ_KEY)) {
                            freq = Integer.parseInt(value);
                        } else if (key.equals(DATE_KEY)) {
                            // ignored
                        } else if (key.equals(BSSID_KEY_END)) {
                            if (bssid != null && getScanDetailCache(config, scanDetailCaches) != null) {
                                getScanDetailCache(config, scanDetailCaches).put(
                                        new ScanDetail(WifiSsid.createFromAsciiEncoded(ssid),
                                                bssid, caps, err, freq, 0L, tsf));
                            }
                        } else if (key.equals(DELETED_EPHEMERAL_KEY)) {
                            if (!TextUtils.isEmpty(value)) {
                                deletedEphemeralSSIDs.add(value);
                            }
                        } else if (key.equals(CREATOR_NAME_KEY)) {
                            config.creatorName = value;
                        } else if (key.equals(UPDATE_NAME_KEY)) {
                            config.lastUpdateName = value;
                        } else if (key.equals(USER_APPROVED_KEY)) {
                            config.userApproved = Integer.parseInt(value);
                        } else if (key.equals(SHARED_KEY)) {
                            config.shared = Boolean.parseBoolean(value);
                        } else if (key.equals(HAS_EVER_CONNECTED_KEY)) {
                            status.setHasEverConnected(Boolean.parseBoolean(value));
                        }
                    }
                }
            } finally {
                in.close();
            }
        } catch (EOFException e) {
            // NORMAL
        } catch (FileNotFoundException e) {
            Log.i(TAG, "readNetworkHistory: no config file, " + e);
        } catch (IOException e) {
            Log.e(TAG, "readNetworkHistory: failed to read, " + e, e);
        } catch (NumberFormatException e) {
            Log.e(TAG, "readNetworkHistory: failed to parse, " + e, e);
        }
    }

    public void writeKnownNetworkHistory(final List<WifiConfiguration> networks,
            final ConcurrentHashMap<Integer, ScanDetailCache> scanDetailCaches,
            final Set<String> deletedEphemeralSSIDs) {
        mWriter.write(NETWORK_HISTORY_CONFIG_FILE, new DelayedDiskWrite.Writer() {
            @Override
            public void onWriteCalled(DataOutputStream out) throws IOException {
                for (WifiConfiguration config : networks) {
                    NetworkSelectionStatus status = config.getNetworkSelectionStatus();
                    logd("saving network history: " + config.configKey() + " gw: "
                            + config.defaultGwMacAddress + " Network Selection-status: "
                            + status.getNetworkStatusString()
                            + (config.getNetworkSelectionStatus().isNetworkEnabled() ? ""
                                    : "Disable time: " + java.text.DateFormat.getInstance().format(
                                            Long.valueOf(status.getDisableTime())))
                            + " ephemeral=" + config.ephemeral + " choice:"
                            + status.getConnectChoice() + " link:"
                            + (config.linkedConfigurations != null
                                    ? config.linkedConfigurations.size() : 0)
                            + " status:" + config.status + " nid:" + config.networkId
                            + " hasEverConnected: " + status.getHasEverConnected());
                    if (!isValid(config)) {
                        continue;
                    }
                    if (config.SSID == null) {
                        logv("writeKnownNetworkHistory trying to write config with null SSID");
                        continue;
                    }
                    logv("writeKnownNetworkHistory write config " + config.configKey());
                    out.writeUTF(CONFIG_KEY + SEPARATOR + config.configKey() + NL);
                    out.writeUTF(SSID_KEY + SEPARATOR + config.SSID + NL);
                    if (config.BSSID != null) {
                        out.writeUTF(CONFIG_BSSID_KEY + SEPARATOR + config.BSSID + NL);
                    } else {
                        out.writeUTF(CONFIG_BSSID_KEY + SEPARATOR + "null" + NL);
                    }
                    if (config.FQDN != null) {
                        out.writeUTF(FQDN_KEY + SEPARATOR + config.FQDN + NL);
                    }
                    out.writeUTF(PRIORITY_KEY + SEPARATOR + Integer.toString(config.priority) + NL);
                    out.writeUTF("ID" + SEPARATOR + Integer.toString(config.networkId) + NL);
                    out.writeUTF(SELF_ADDED_KEY + SEPARATOR + Boolean.toString(config.selfAdded)
                            + NL);
                    out.writeUTF(DID_SELF_ADD_KEY + SEPARATOR + Boolean.toString(config.didSelfAdd)
                            + NL);
                    out.writeUTF(NO_INTERNET_ACCESS_REPORTS_KEY + SEPARATOR
                            + Integer.toString(config.numNoInternetAccessReports) + NL);
                    out.writeUTF(VALIDATED_INTERNET_ACCESS_KEY + SEPARATOR
                            + Boolean.toString(config.validatedInternetAccess) + NL);
                    out.writeUTF(NO_INTERNET_ACCESS_EXPECTED_KEY + SEPARATOR
                            + Boolean.toString(config.noInternetAccessExpected) + NL);
                    out.writeUTF(EPHEMERAL_KEY + SEPARATOR + Boolean.toString(config.ephemeral)
                            + NL);
                    out.writeUTF(METERED_HINT_KEY + SEPARATOR
                            + Boolean.toString(config.meteredHint) + NL);
                    out.writeUTF(METERED_OVERRIDE_KEY + SEPARATOR
                            + Integer.toString(config.meteredOverride) + NL);
                    out.writeUTF(USE_EXTERNAL_SCORES_KEY + SEPARATOR
                            + Boolean.toString(config.useExternalScores) + NL);
                    if (config.creationTime != null) {
                        out.writeUTF(CREATION_TIME_KEY + SEPARATOR + config.creationTime + NL);
                    }
                    if (config.updateTime != null) {
                        out.writeUTF(UPDATE_TIME_KEY + SEPARATOR + config.updateTime + NL);
                    }
                    if (config.peerWifiConfiguration != null) {
                        out.writeUTF(PEER_CONFIGURATION_KEY + SEPARATOR
                                + config.peerWifiConfiguration + NL);
                    }
                    out.writeUTF(SCORER_OVERRIDE_KEY + SEPARATOR
                            + Integer.toString(config.numScorerOverride) + NL);
                    out.writeUTF(SCORER_OVERRIDE_AND_SWITCH_KEY + SEPARATOR
                            + Integer.toString(config.numScorerOverrideAndSwitchedNetwork) + NL);
                    out.writeUTF(NUM_ASSOCIATION_KEY + SEPARATOR
                            + Integer.toString(config.numAssociation) + NL);
                    out.writeUTF(CREATOR_UID_KEY + SEPARATOR
                            + Integer.toString(config.creatorUid) + NL);
                    out.writeUTF(CONNECT_UID_KEY + SEPARATOR
                            + Integer.toString(config.lastConnectUid) + NL);
                    out.writeUTF(UPDATE_UID_KEY + SEPARATOR
                            + Integer.toString(config.lastUpdateUid) + NL);
                    out.writeUTF(CREATOR_NAME_KEY + SEPARATOR + config.creatorName + NL);
                    out.writeUTF(UPDATE_NAME_KEY + SEPARATOR + config.lastUpdateName + NL);
                    out.writeUTF(USER_APPROVED_KEY + SEPARATOR
                            + Integer.toString(config.userApproved) + NL);
                    out.writeUTF(SHARED_KEY + SEPARATOR + Boolean.toString(config.shared) + NL);
                    out.writeUTF(AUTH_KEY + SEPARATOR
                            + makeString(config.allowedKeyManagement,
                                    WifiConfiguration.KeyMgmt.strings) + NL);
                    out.writeUTF(NETWORK_SELECTION_STATUS_KEY + SEPARATOR
                            + status.getNetworkSelectionStatus() + NL);
                    out.writeUTF(NETWORK_SELECTION_DISABLE_REASON_KEY + SEPARATOR
                            + status.getNetworkSelectionDisableReason() + NL);
                    if (status.getConnectChoice() != null) {
                        out.writeUTF(CHOICE_KEY + SEPARATOR + status.getConnectChoice() + NL);
                        out.writeUTF(CHOICE_TIME_KEY + SEPARATOR
                                + status.getConnectChoiceTimestamp() + NL);
                    }
                    if (config.linkedConfigurations != null) {
                        log("writeKnownNetworkHistory write linked "
                                + config.linkedConfigurations.size());
                        for (String link : config.linkedConfigurations.keySet()) {
                            out.writeUTF(LINK_KEY + SEPARATOR + link + NL);
                        }
                    }
                    if (config.defaultGwMacAddress != null) {
                        out.writeUTF(DEFAULT_GW_KEY + SEPARATOR + config.defaultGwMacAddress + NL);
                    }
                    if (getScanDetailCache(config, scanDetailCaches) != null) {
                        for (ScanDetail scanDetail
                                : getScanDetailCache(config, scanDetailCaches).values()) {
                            ScanResult result = scanDetail.getScanResult();
                            out.writeUTF(BSSID_KEY + SEPARATOR + result.BSSID + NL);
                            out.writeUTF(FREQ_KEY + SEPARATOR
                                    + Integer.toString(result.frequency) + NL);
                            out.writeUTF(RSSI_KEY + SEPARATOR
                                    + Integer.toString(result.level) + NL);
                            out.writeUTF(BSSID_KEY_END + NL);
                        }
                    }
                    out.writeUTF(HAS_EVER_CONNECTED_KEY + SEPARATOR
                            + Boolean.toString(status.getHasEverConnected()) + NL);
                    out.writeUTF(NL);
                    out.writeUTF(NL);
                    out.writeUTF(NL);
                }
                if (deletedEphemeralSSIDs == null || deletedEphemeralSSIDs.size() <= 0) {
                    return;
                }
                for (String ssid : deletedEphemeralSSIDs) {
                    out.writeUTF(DELETED_EPHEMERAL_KEY);
                    out.writeUTF(ssid);
                    out.writeUTF(NL);
                }
            }
        });
    }
}
