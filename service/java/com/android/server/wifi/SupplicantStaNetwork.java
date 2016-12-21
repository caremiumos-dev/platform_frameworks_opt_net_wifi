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

import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantCallback;
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetworkCallback;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.RemoteException;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.util.Log;
import android.util.MutableBoolean;

import com.android.server.wifi.hotspot2.Utils;

import java.lang.Byte;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

import libcore.util.HexEncoding;

    /**
     * Wrapper class for ISupplicantStaNetwork HIDL calls and callbacks
     * TBD notes on how this class is used
     */
public class SupplicantStaNetwork {
    private static final String TAG = "SupplicantStaNetwork";
    private static final String ANY_BSSID_STR = "any";
    private static final byte[] ANY_BSSID_BYTES = {0, 0, 0, 0, 0, 0};
    private static final int BSSID_LENGTH = 6;
    private static final String ID_STRING_KEY_FQDN = "fqdn";
    private static final String ID_STRING_KEY_CREATOR_UID = "creatorUid";
    private static final String ID_STRING_KEY_CONFIG_KEY = "configKey";
    private final ISupplicantStaNetwork mISupplicantStaNetwork;

    public int id;
    public String name;
    public int type;
    public java.util.ArrayList<Byte> apSsid;
    public byte[/* 6 */] apBssid;
    public boolean scanSsid;
    public int keyMgmtMask;
    public int protoMask;
    public int authAlgMask;
    public int groupCipherMask;
    public int pairwiseCipherMask;
    public String pskPassphrase;
    public java.util.ArrayList<Byte> wepKey;
    public int wepTxKeyIdx;
    public boolean requirePmf;
    public int eapMethod;
    public int eapPhase2Method;
    public java.util.ArrayList<Byte> eapIdentity;
    public java.util.ArrayList<Byte> eapAnonymousIdentity;
    public java.util.ArrayList<Byte> eapPassword;
    public String eapCACert;
    public String eapCAPath;
    public String eapClientCert;
    public String eapPrivateKey;
    public String eapSubjectMatch;
    public String eapAltSubjectMatch;
    public boolean eapEngine;
    public String eapEngineID;
    public String eapDomainSuffixMatch;
    public String idStr;

    SupplicantStaNetwork(ISupplicantStaNetwork iSupplicantStaNetwork) {
        mISupplicantStaNetwork = iSupplicantStaNetwork;
    }

    /**
     * Read network variables from wpa_supplicant into the 
     */
    public boolean readNetworkVariables(WifiConfiguration config)
    {
        /** SSID */
        config.SSID = null;
        if (getSsid()) {
            if (!TextUtils.isEmpty(apSsid)) {
                config.SSID = decodeSsid(apSsid);
            }
        }

        /** BSSID */
        config.getNetworkSelectionStatus().setNetworkSelectionBSSID(null);
        if (getBssid()) {
            try {
                config.getNetworkSelectionStatus()
                        .setNetworkSelectionBSSID(macAddressFromByteArray(apBssid));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to get BSSID: " + e.toString());
            }
        }

        /** Scan SSID (Is Hidden Network?) */
        config.hiddenSSID = false;
        if (getScanSsid()) {
            config.hiddenSSID = scanSsid;
        }

        /** Require PMF*/
        config.requirePMF = false;
        if (getRequirePmf()) {
            config.requirePMF = requirePmf;
        }

        /** WEP keys **/
        config.wepTxKeyIndex = -1;
        if (getWepTxKeyIdx()) {
            config.wepTxKeyIndex = wepTxKeyIdx;
        }
        for (int i = 0; i < 4; i++) {
            config.wepKeys[i] = null;
            if (getWepKey(i)) {
                config.wepKeys[i] = stringFromByteArrayList(wepKey);
            }
        }

        /** PSK pass phrase*/
        config.preSharedKey = null;
        if (getPskPassphrase()) {
            config.preSharedKey = pskPassphrase;
        }

        /** allowedProtocols */
        if (getProto()) {
            maskToBitSetProtoMask(protoMask, config.allowedProtocols);
        }
        /** allowedKeyManagement */
        if (getKeyMgmt()) {
            maskToBitSetKeyMgmtMask(keyMgmtMask, config.allowedKeyManagement);
        }
        /** allowedAuthAlgorithms */
        if (getAuthAlg()) {
            maskToBitSetAuthAlgMask(authAlgMask, config.allowedAuthAlgorithms);
        }
        /** allowedPairwiseCiphers */
        if (getPairwiseCipher()) {
            maskToBitSetPairwiseCipherMask(pairwiseCipherMask, config.allowedPairwiseCiphers);
        }
        /** allowedGroupCiphers */
        if (getGroupCipher()) {
            maskToBitSetGroupCipherMask(groupCipherMask, config.allowedGroupCiphers);
        }

        /** metadata: idstr */
        if (!getIdStr()) {
            Log.e(TAG, "getIdStr failed");
            return false;
        }
        Map<String, String> metadata = WifiNative.parseNetworkExtra(idStr);
        if (metadata == null) {
            extras = new HashMap<String, String>();
            // If ID_STRING_VAR_NAME did not contain a dictionary, assume that it contains
            // just a quoted FQDN. This is the legacy format that was used in Marshmallow.
            final String fqdn = Utils.unquote(mWifiNative.getNetworkVariable(
                    config.networkId, ID_STRING_VAR_NAME));
            if (fqdn != null) {
                config.FQDN = fqdn;
                // Mark the configuration as a Hotspot 2.0 network.
                config.providerFriendlyName = "";
            }
        }
        return true;
    }

    public boolean roamToNetwork(WifiConfiguration configuration) {
        return true;
    }

    /**
     * Save an entire WifiConfiguration to wpa_supplicant via HIDL
     */
    public boolean saveFromWifiConfiguration(WifiConfiguration config) {
        if (config == null) return false;

        /** SSID */
        if (config.SSID != null) {
            if(!setSsid(encodeSsid(config.SSID))) {
                Log.e(TAG, "Failed to set SSID: " + config.SSID);
                return false;
            }
        }

        /** metadata: FQDN + ConfigKey + CreatorUid */
        final Map<String, String> metadata = new HashMap<String, String>();
        if (config.isPasspoint()) {
            metadata.put(ID_STRING_KEY_FQDN, config.FQDN);
        }
        metadata.put(ID_STRING_KEY_CONFIG_KEY, config.configKey());
        metadata.put(ID_STRING_KEY_CREATOR_UID, Integer.toString(config.creatorUid));
        final String idString;
        try {
            idString = URLEncoder.encode(new JSONObject(values).toString(), "UTF-8");
        } catch (NullPointerException e) {
            Log.e(TAG, "Unable to serialize networkExtra: " + e.toString());
            return false;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unable to serialize networkExtra: " + e.toString());
            return false;
        }
        if (!setIdStr(idString)) {
            Log.e(TAG, "Failed to set idString");
            return false;
        }

        /** BSSID */
        String bssidStr = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
        if (bssidStr != null) {
            byte[] bssid;
            try {
                 bssid = macAddressToByteArray(bssidStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid BSSID: " + bssidStr);
                return false;
            }
            if (!setBssid(bssid)) {
                Log.e(TAG, "Failed to set BSSID: " + bssidStr);
                return false;
            }
        }

        /** Key Management Scheme */
        if (config.allowedKeyManagement.cardinality() != 0
                && !setKeyMgmt(createSupplicantKeyMgmtMask(config.allowedKeyManagement))) {
            Log.e(TAG, "Failed to set Key Management");
            return false;
        }

        /** Security Protocol */
        if (config.allowedProtocols.cardinality() != 0
                && !setProto(createSupplicantProtoMask(config.allowedProtocols))) {
            Log.e(TAG, "Failed to set Security Protocol");
            return false;
        }
        /** Auth Algorithm */
        if (config.allowedAuthAlgorithms.cardinality() != 0
                && !setAuthAlg(createSupplicantAuthAlgMask(config.allowedAuthAlgorithms))) {
            Log.e(TAG, "Failed to set AuthAlgorithm");
            return false;
        }
        /** Pairwise Cipher*/
        if (config.allowedPairwiseCiphers.cardinality() != 0
                && !setPairwiseCipher(createSupplicantPairwiseCipherMask(config.allowedPairwiseCiphers))) {
            Log.e(TAG, "Failed to set PairwiseCipher");
            return false;
        }
        /** Group Cipher */
        if (config.allowedGroupCiphers.cardinality() != 0
                && !setGroupCipher(createSupplicantGroupCipherMask(config.allowedGroupCiphers))) {
            Log.e(TAG, "Failed to set Group Cipher");
            return false;
        }

        /** Pre Shared Key */
        // Prevent client screw-up by passing in a WifiConfiguration we gave it
        // by preventing "*" as a key.
        if (config.preSharedKey != null && !config.preSharedKey.equals("*")
                && !setPskPassphrase(config.preSharedKey)) {
            Log.e(TAG, "failed to set psk");
            return false;
        }
        /** Wep Keys */
        boolean hasSetKey = false;
        if (config.wepKeys != null) {
            for (int i = 0; i < config.wepKeys.length; i++) {
                // Prevent client screw-up by passing in a WifiConfiguration we gave it
                // by preventing "*" as a key.
                if (config.wepKeys[i] != null && !config.wepKeys[i].equals("*")) {
                    if (!setWepKey(i, stringToByteArrayList(config.wepKeys[i]))) {
                        Log.e(TAG, "failed to set wep_key" + i + ": " + config.wepKeys[i]);
                        return false;
                    }
                    hasSetKey = true;
                }
            }
        }
        /** Wep Tx Key Idx */
        if (hasSetKey) {
            if (!setWepTxKeyIdx(config.wepTxKeyIndex)) {
                Log.e(TAG, "failed to set wep_tx_keyidx: " + config.wepTxKeyIndex);
                return false;
            }
        }
        /** Priority */
        // TODO(b/34454675): Missing from hal, might not be supported anymore

        /** HiddenSSID */
        if (!setScanSsid(config.hiddenSSID)) {
            Log.e(TAG, config.SSID + ": failed to set hiddenSSID: " + config.hiddenSSID);
            return false;
        }
        /** RequirePMF */
        if (!setRequirePmf(config.requirePMF)) {
            Log.e(TAG, config.SSID + ": failed to set requirePMF: " + config.requirePMF);
            return false;
        }
        /** UpdateIdentifier */
        /** TODO(b/34454675): Add setter for this when it exists */
        
        // Finish here if no EAP config to set
        if (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.NONE) {
            return true;
        } else {
            return saveEapNetworkVariables(config.enterpriseConfig);
        }
    }

    /**
     * Save EAP specific parameters to supplicant via HIDL
     */
    private boolean saveEapNetworkVariables(WifiEnterpriseConfig eapConfig) {
    /*  WIP...
        for (String key : mFields.keySet()) {
            if (shouldNotWriteAnonIdentity && WifiEnterpriseConfig.ANON_IDENTITY_KEY.equals(key)) {
                continue;
            }
            if (!saver.saveValue(key, mFields.get(key))) {
                return false;
            }
        }

        if (!saver.saveValue(EAP_KEY, Eap.strings[mEapMethod])) {
            return false;
        }

        if (mEapMethod != Eap.TLS && mPhase2Method != Phase2.NONE) {
            boolean is_autheap = mEapMethod == Eap.TTLS && mPhase2Method == Phase2.GTC;
            String prefix = is_autheap ? Phase2.AUTHEAP_PREFIX : Phase2.AUTH_PREFIX;
            String value = convertToQuotedString(prefix + Phase2.strings[mPhase2Method]);
            return saver.saveValue(PHASE2_KEY, value);
        } else if (mPhase2Method == Phase2.NONE) {
            // By default, send a null phase 2 to clear old configuration values.
            return saver.saveValue(PHASE2_KEY, null);
        } else {
            Log.e(TAG, "WiFi enterprise configuration is invalid as it supplies a "
                    + "phase 2 method but the phase1 method does not support it.");
            return false;
        }
        */ /*
        if (!setEapMethod(eapConfig.getEapMethod())) {
            Log.e(TAG, "Failed to set EapMethod");
            return false;
        }

        if (!setEapPhase2Method(eapConfig.getPhase2Method())) {
            Log.E(TAG, "Failed to set EapPhase2Method");
            return false;
        }
        if (!setEapIdentity(stringToByteArrayList(eapConfig.getIdentity()))) {
            Log.E(TAG, "Failed to set EapIdentity");
            return false;
        }
        // wpa_supplicant can update the anonymous identity for these kinds of networks after
        // framework reads them, so make sure the framework doesn't try to overwrite them.
        boolean shouldNotWriteAnonIdentity =
                eapConfig.getEapMethod() == WifiEnterpriseConfig.Eap.SIM
                || eapConfig.getEapMethod() == WifiEnterpriseConfig.Eap.AKA
                || eapConfig.getEapMethod() == WifiEnterpriseConfig.Eap.AKA_PRIME;
        if (!setEapAnonymousIdentity(stringToByteArrayList(eapConfig.getEapAnonymousIdentity()))) {
            Log.E(TAG, "Failed to set EapAnonymousIdentity");
            return false;
        }
        if (!setEapPassword(java.util.ArrayList<Byte> password)) {
            Log.E(TAG, "Failed to set EapPassword");
            return false;
        }
        if (!setEapCACert(String path)) {
            Log.E(TAG, "Failed to set EapCACert");
            return false;
        }
        if (!setEapCAPath(String path)) {
            Log.E(TAG, "Failed to set EapCAPath");
            return false;
        }
        if (!setEapClientCert(String path)) {
            Log.E(TAG, "Failed to set EapClientCert");
            return false;
        }
        if (!setEapPrivateKey(String path)) {
            Log.E(TAG, "Failed to set EapPrivateKey");
            return false;
        }
        if (!setEapSubjectMatch(String match)) {
            Log.E(TAG, "Failed to set EapSubjectMatch");
            return false;
        }
        if (!setEapAltSubjectMatch(String match)) {
            Log.E(TAG, "Failed to set EapAltSubjectMatch");
            return false;
        }
        if (!setEapEngine(boolean enable)) {
            Log.E(TAG, "Failed to set EapEngine");
            return false;
        }
        if (!setEapEngineID(String id)) {
            Log.E(TAG, "Failed to set EapEngineID");
            return false;
        }
        if (!setEapDomainSuffixMatch(String match)) {
            Log.E(TAG, "Failed to set EapDomainSuffixMatch");
            return false;
        }
        */
        return true;
    }

    /**
     * Maps WifiConfiguration Key Management BitSet to Supplicant HIDL bitmask int
     * TODO(b/32571829): Update mapping when fast transition keys are added
     * @return bitmask int describing the allowed Key Management schemes, readable by the Supplicant
     *         HIDL hal
     */
    private static int createSupplicantKeyMgmtMask(BitSet keyMgmt) {
        int mask = 0;
        for (int bit = keyMgmt.nextSetBit(0); bit != -1; bit = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.KeyMgmt.NONE:
                    mask |= ISupplicantStaNetwork.KeyMgmtMask.NONE;
                    break;
                case WifiConfiguration.KeyMgmt.WPA_PSK:
                    mask |= ISupplicantStaNetwork.KeyMgmtMask.WPA_PSK;
                    break;
                case WifiConfiguration.KeyMgmt.WPA_EAP:
                    mask |= ISupplicantStaNetwork.KeyMgmtMask.WPA_EAP;
                    break;
                case WifiConfiguration.KeyMgmt.IEEE8021X:
                    mask |= ISupplicantStaNetwork.KeyMgmtMask.IEEE8021X;
                    break;
                case WifiConfiguration.KeyMgmt.OSEN:
                    mask |= ISupplicantStaNetwork.KeyMgmtMask.OSEN;
                    break;
                case WifiConfiguration.KeyMgmt.FT_PSK:
                    mask |= ISupplicantStaNetwork.KeyMgmtMask.FT_PSK;
                    break;
                case WifiConfiguration.KeyMgmt.FT_EAP:
                    mask |= ISupplicantStaNetwork.KeyMgmtMask.FT_EAP;
                    break;
                case WifiConfiguration.KeyMgmt.WPA2_PSK: // This should never happen
                default:
                    throw new IllegalArgumentException(
                            "Invalid protoMask bit in keyMgmt: " + bit);
            }
        }
        return mask;
    }

    private static int createSupplicantProtoMask(BitSet protoMask) {
        int mask = 0;
        for (int bit = protoMask.nextSetBit(0); bit != -1; bit = protoMask.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.Protocol.WPA:
                    mask |= ISupplicantStaNetwork.ProtoMask.WPA;
                    break;
                case WifiConfiguration.Protocol.RSN:
                    mask |= ISupplicantStaNetwork.ProtoMask.RSN;
                    break;
                case WifiConfiguration.Protocol.OSEN:
                    mask |= ISupplicantStaNetwork.ProtoMask.OSEN;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid protoMask bit in wificonfig: " + bit);
            }
        }
        return mask;
    };

    private static int createSupplicantAuthAlgMask(BitSet authAlgMask) {
        int mask = 0;
        for (int bit = authAlgMask.nextSetBit(0); bit != -1; bit = authAlgMask.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.AuthAlgorithm.OPEN:
                    mask |= ISupplicantStaNetwork.AuthAlgMask.OPEN;
                    break;
                case WifiConfiguration.AuthAlgorithm.SHARED:
                    mask |= ISupplicantStaNetwork.AuthAlgMask.SHARED;
                    break;
                case WifiConfiguration.AuthAlgorithm.LEAP:
                    mask |= ISupplicantStaNetwork.AuthAlgMask.LEAP;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid authAlgMask bit in wificonfig: " + bit);
            }
        }
        return mask;
    };

    private static int createSupplicantGroupCipherMask(BitSet groupCipherMask) {
        int mask = 0;
        for (int bit = groupCipherMask.nextSetBit(0); bit != -1; bit = groupCipherMask.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.GroupCipher.WEP40:
                    mask |= ISupplicantStaNetwork.GroupCipherMask.WEP40;
                    break;
                case WifiConfiguration.GroupCipher.WEP104:
                    mask |= ISupplicantStaNetwork.GroupCipherMask.WEP104;
                    break;
                case WifiConfiguration.GroupCipher.TKIP:
                    mask |= ISupplicantStaNetwork.GroupCipherMask.TKIP;
                    break;
                case WifiConfiguration.GroupCipher.CCMP:
                    mask |= ISupplicantStaNetwork.GroupCipherMask.CCMP;
                    break;
                case WifiConfiguration.GroupCipher.GTK_NOT_USED:
                    mask |= ISupplicantStaNetwork.GroupCipherMask.GTK_NOT_USED;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid GroupCipherMask bit in wificonfig: " + bit);
            }
        }
        return mask;
    };

    private static int createSupplicantPairwiseCipherMask(BitSet pairwiseCipherMask) {
        int mask = 0;
        for (int bit = pairwiseCipherMask.nextSetBit(0); bit != -1; bit = pairwiseCipherMask.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.PairwiseCipher.NONE:
                    mask |= ISupplicantStaNetwork.PairwiseCipherMask.NONE;
                    break;
                case WifiConfiguration.PairwiseCipher.TKIP:
                    mask |= ISupplicantStaNetwork.PairwiseCipherMask.TKIP;
                    break;
                case WifiConfiguration.PairwiseCipher.CCMP:
                    mask |= ISupplicantStaNetwork.PairwiseCipherMask.CCMP;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid pairwiseCipherMask bit in wificonfig: " + bit);
            }
        }
        return mask;
    };

    private static void supplicantToWifiConfigurationEapMethod(int value) {
        switch (value) {
        case ISupplicantStaNetwork.EapMethod.PEAP;
            return WifiEnterpriseConfig.Eap.PEAP: 
        case ISupplicantStaNetwork.EapMethod.TLS;
            return WifiEnterpriseConfig.Eap.TLS: 
        case ISupplicantStaNetwork.EapMethod.TTLS;
            return WifiEnterpriseConfig.Eap.TTLS: 
        case ISupplicantStaNetwork.EapMethod.PWD;
            return WifiEnterpriseConfig.Eap.PWD: 
        case ISupplicantStaNetwork.EapMethod.SIM;
            return WifiEnterpriseConfig.Eap.SIM: 
        case ISupplicantStaNetwork.EapMethod.AKA;
            return WifiEnterpriseConfig.Eap.AKA: 
        case ISupplicantStaNetwork.EapMethod.AKA_PRIME;
            return WifiEnterpriseConfig.Eap.AKA_PRIME: 
        case ISupplicantStaNetwork.EapMethod.WFA_UNAUTH_TLS;
            return WifiEnterpriseConfig.Eap.UNAUTH_TLS: 
        // WifiEnterpriseConfig.Eap.NONE:
        default:
            Log.e(TAG, "invalid eap method value from supplicant: " + value);
            return -1;
        }
    };

    private static void supplicantToWifiConfigurationEapPhase2Method(int value) {
        switch (value) {
        case ISupplicantStaNetwork.EapPhase2Method.NONE;
            return WifiEnterpriseConfig.Phase2.NONE: 
        case ISupplicantStaNetwork.EapPhase2Method.PAP;
            return WifiEnterpriseConfig.Phase2.PAP: 
        case ISupplicantStaNetwork.EapPhase2Method.MSPAP;
            return WifiEnterpriseConfig.Phase2.MSCHAP: 
        case ISupplicantStaNetwork.EapPhase2Method.MSPAPV2;
            return WifiEnterpriseConfig.Phase2.MSCHAPV2: 
        case ISupplicantStaNetwork.EapPhase2Method.GTC;
            return WifiEnterpriseConfig.Phase2.GTC: 
        default:
            Log.e(TAG, "invalid eap phase2 method value from supplicant: " + value);
            return -1;
        }
    };

    private static void maskToBitSetKeyMgmtMask(int mask, BitSet bitset) {
        bitset.set(WifiConfiguration.KeyMgmt.NONE, mask & ISupplicantStaNetwork.KeyMgmtMask.NONE));
        bitset.set(WifiConfiguration.KeyMgmt.WPA_PSK, mask & ISupplicantStaNetwork.KeyMgmtMask.WPA_PSK));
        bitset.set(WifiConfiguration.KeyMgmt.WPA_EAP, mask & ISupplicantStaNetwork.KeyMgmtMask.WPA_EAP));
        bitset.set(WifiConfiguration.KeyMgmt.IEEE8021X, mask & ISupplicantStaNetwork.KeyMgmtMask.IEEE8021X));
        bitset.set(WifiConfiguration.KeyMgmt.OSEN, mask & iSupplicantStaNetwork.KeyMgmMask.OSEN));
        bitset.set(WifiConfiguration.KeyMgmt.FT_PSK, mask & iSupplicantStaNetwork.KeyMgmMask.FT_PSK));
        bitset.set(WifiConfiguration.KeyMgmt.FT_EAP, mask & iSupplicantStaNetwork.KeyMgmMask.FT_EAP));
        // WifiConfiguration.KeyMgmt.WPA2_PSK

        // Remove all valid bits from mask, and throw exception if anything is left
        mask = ~mask;
        mask |= ISupplicantStaNetwork.KeyMgmtMask.NONE;
        mask |= ISupplicantStaNetwork.KeyMgmtMask.WPA_PSK;
        mask |= ISupplicantStaNetwork.KeyMgmtMask.WPA_EAP;
        mask |= ISupplicantStaNetwork.KeyMgmtMask.IEEE8021X;
        mask |= ISupplicantStaNetwork.KeyMgmMask.OSEN;
        mask |= ISupplicantStaNetwork.KeyMgmMask.FT_PSK;
        mask |= ISupplicantStaNetwork.KeyMgmMask.FT_EAP;
        mask = ~mask;
        if (mask != 0) {
            throw new IllegalArgumentException("invalid bits set in keyMgmtMask: " + mask);
        }
    }

    private static void maskToBitSetProtoMask(int mask, BitSet bitset) {
        bitset.set(WifiConfiguration.Protocol.WPA, mask & ISupplicantStaNetwork.ProtoMask.WPA));
        bitset.set(WifiConfiguration.Protocol.RSN, mask & ISupplicantStaNetwork.ProtoMask.RSN));
        bitset.set(WifiConfiguration.Protocol.OSEN, mask & ISupplicantStaNetwork.ProtoMask.OSEN));

        mask |= ISupplicantStaNetwork.ProtoMask.WPA
        mask |= ISupplicantStaNetwork.ProtoMask.RSN
        mask |= ISupplicantStaNetwork.ProtoMask.OSEN
    };

    private static void maskToBitSetAuthAlgMask(int mask, BitSet bitset) {
        bitset.set(WifiConfiguration.AuthAlgorithm.OPEN, mask & ISupplicantStaNetwork.AuthAlgMask.OPEN));
        bitset.set(WifiConfiguration.AuthAlgorithm.SHARED, mask & ISupplicantStaNetwork.AuthAlgMask.SHARED));
        bitset.set(WifiConfiguration.AuthAlgorithm.LEAP, mask & ISupplicantStaNetwork.AuthAlgMask.LEAP));
    };

    private static void maskToBitSetGroupCipherMask(int mask, BitSet bitset) {
        bitset.set(WifiConfiguration.GroupCipher.WEP40, mask & ISupplicantStaNetwork.GroupCipherMask.WEP40));
        bitset.set(WifiConfiguration.GroupCipher.WEP104, mask & ISupplicantStaNetwork.GroupCipherMask.WEP104));
        bitset.set(WifiConfiguration.GroupCipher.TKIP, mask & ISupplicantStaNetwork.GroupCipherMask.TKIP));
        bitset.set(WifiConfiguration.GroupCipher.CCMP, mask & ISupplicantStaNetwork.GroupCipherMask.CCMP));
        bitset.set(WifiConfiguration.GroupCipher.GTK_NOT_USED, mask & ISupplicantStaNetwork.GroupCipherMask.GTK_NOT_USED));
    };

    private static void maskToBitSetPairwiseCipherMask(int mask, BitSet bitset) {
        bitset.set(WifiConfiguration.PairwiseCipher.NONE, mask & ISupplicantStaNetwork.PairwiseCipherMask.NONE));
        bitset.set(WifiConfiguration.PairwiseCipher.TKIP, mask & ISupplicantStaNetwork.PairwiseCipherMask.TKIP));
        bitset.set(WifiConfiguration.PairwiseCipher.CCMP, mask & ISupplicantStaNetwork.PairwiseCipherMask.CCMP));
    };

    private static void wifiConfigurationToSupplicantEapMethod(int value) {
        switch (value) {
        case WifiEnterpriseConfig.Eap.PEAP: 
            return ISupplicantStaNetwork.EapMethod.PEAP;
        case WifiEnterpriseConfig.Eap.TLS: 
            return ISupplicantStaNetwork.EapMethod.TLS;
        case WifiEnterpriseConfig.Eap.TTLS: 
            return ISupplicantStaNetwork.EapMethod.TTLS;
        case WifiEnterpriseConfig.Eap.PWD: 
            return ISupplicantStaNetwork.EapMethod.PWD;
        case WifiEnterpriseConfig.Eap.SIM: 
            return ISupplicantStaNetwork.EapMethod.SIM;
        case WifiEnterpriseConfig.Eap.AKA: 
            return ISupplicantStaNetwork.EapMethod.AKA;
        case WifiEnterpriseConfig.Eap.AKA_PRIME: 
            return ISupplicantStaNetwork.EapMethod.AKA_PRIME;
        case WifiEnterpriseConfig.Eap.UNAUTH_TLS: 
            return ISupplicantStaNetwork.EapMethod.WFA_UNAUTH_TLS;
        // WifiEnterpriseConfig.Eap.NONE:
        default:
            Log.e(TAG, "invalid eap method value from WifiConfiguration: " + value);
            return -1;
        }
    };

    private static void wifiConfigurationToSupplicantEapPhase2Method(int value) {
        switch (value) {
        case WifiEnterpriseConfig.Phase2.NONE: 
            return ISupplicantStaNetwork.EapPhase2Method.NONE;
        case WifiEnterpriseConfig.Phase2.PAP: 
            return ISupplicantStaNetwork.EapPhase2Method.PAP;
        case WifiEnterpriseConfig.Phase2.MSCHAP: 
            return ISupplicantStaNetwork.EapPhase2Method.MSPAP;
        case WifiEnterpriseConfig.Phase2.MSCHAPV2: 
            return ISupplicantStaNetwork.EapPhase2Method.MSPAPV2;
        case WifiEnterpriseConfig.Phase2.GTC: 
            return ISupplicantStaNetwork.EapPhase2Method.GTC;
        default:
            Log.e(TAG, "invalid eap phase2 method value from WifiConfiguration: " + value);
            return -1;
        }
    };

    public boolean getId() {
        final String methodStr = "getId";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getId((SupplicantStatus status, int idValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.id = idValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }

    public boolean getInterfaceName() {
        final String methodStr = "getInterfaceName";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getInterfaceName((SupplicantStatus status, String nameValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.name = nameValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }

    public boolean getType() {
        final String methodStr = "getType";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getType((SupplicantStatus status, int typeValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.type = typeValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }

    public boolean registerCallback(ISupplicantStaNetworkCallback callback) {
        final String methodStr = "registerCallback";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.registerCallback(callback);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }

    public boolean setSsid(java.util.ArrayList<Byte> ssid) {
        final String methodStr = "setSsid";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setSsid(ssid);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setBssid(byte[/* 6 */] bssid) {
        final String methodStr = "setBssid";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setBssid(bssid);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setScanSsid(boolean enable) {
        final String methodStr = "setScanSsid";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setScanSsid(enable);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setKeyMgmt(int keyMgmtMask) {
        final String methodStr = "setKeyMgmt";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setKeyMgmt(keyMgmtMask);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setProto(int protoMask) {
        final String methodStr = "setProto";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setProto(protoMask);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setAuthAlg(int authAlgMask) {
        final String methodStr = "setAuthAlg";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setAuthAlg(authAlgMask);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setGroupCipher(int groupCipherMask) {
        final String methodStr = "setGroupCipher";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setGroupCipher(groupCipherMask);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setPairwiseCipher(int pairwiseCipherMask) {
        final String methodStr = "setPairwiseCipher";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setPairwiseCipher(pairwiseCipherMask);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setPskPassphrase(String psk) {
        final String methodStr = "setPskPassphrase";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setPskPassphrase(psk);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setWepKey(int keyIdx, java.util.ArrayList<Byte> wepKey) {
        final String methodStr = "setWepKey";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setWepKey(keyIdx, wepKey);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setWepTxKeyIdx(int keyIdx) {
        final String methodStr = "setWepTxKeyIdx";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setWepTxKeyIdx(keyIdx);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setRequirePmf(boolean enable) {
        final String methodStr = "setRequirePmf";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setRequirePmf(enable);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapMethod(int method) {
        final String methodStr = "setEapMethod";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapMethod(method);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapPhase2Method(int method) {
        final String methodStr = "setEapPhase2Method";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapPhase2Method(method);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapIdentity(java.util.ArrayList<Byte> identity) {
        final String methodStr = "setEapIdentity";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapIdentity(identity);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapAnonymousIdentity(java.util.ArrayList<Byte> identity) {
        final String methodStr = "setEapAnonymousIdentity";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapAnonymousIdentity(identity);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapPassword(java.util.ArrayList<Byte> password) {
        final String methodStr = "setEapPassword";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapPassword(password);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapCACert(String path) {
        final String methodStr = "setEapCACert";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapCACert(path);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapCAPath(String path) {
        final String methodStr = "setEapCAPath";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapCAPath(path);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapClientCert(String path) {
        final String methodStr = "setEapClientCert";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapClientCert(path);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapPrivateKey(String path) {
        final String methodStr = "setEapPrivateKey";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapPrivateKey(path);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapSubjectMatch(String match) {
        final String methodStr = "setEapSubjectMatch";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapSubjectMatch(match);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapAltSubjectMatch(String match) {
        final String methodStr = "setEapAltSubjectMatch";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapAltSubjectMatch(match);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapEngine(boolean enable) {
        final String methodStr = "setEapEngine";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapEngine(enable);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapEngineID(String id) {
        final String methodStr = "setEapEngineID";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapEngineID(id);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setEapDomainSuffixMatch(String match) {
        final String methodStr = "setEapDomainSuffixMatch";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapDomainSuffixMatch(match);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean setIdStr(String idString) {
        final String methodStr = "setIdStr";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setIdStr(idString);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getSsid() {
        final String methodStr = "getSsid";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getSsid((SupplicantStatus status, java.util.ArrayList<Byte> ssidValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.apSsid = ssidValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getBssid() {
        final String methodStr = "getBssid";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getBssid((SupplicantStatus status, byte[/* 6 */] bssidValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.apBssid = bssidValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getScanSsid() {
        final String methodStr = "getScanSsid";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getScanSsid((SupplicantStatus status, boolean enabledValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.scanSsid = enabledValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getKeyMgmt() {
        final String methodStr = "getKeyMgmt";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getKeyMgmt((SupplicantStatus status, int keyMgmtMaskValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.keyMgmtMask = keyMgmtMaskValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getProto() {
        final String methodStr = "getProto";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getProto((SupplicantStatus status, int protoMaskValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.protoMask = protoMaskValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getAuthAlg() {
        final String methodStr = "getAuthAlg";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getAuthAlg((SupplicantStatus status, int authAlgMaskValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.authAlgMask = authAlgMaskValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getGroupCipher() {
        final String methodStr = "getGroupCipher";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getGroupCipher((SupplicantStatus status, int groupCipherMaskValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.groupCipherMask = groupCipherMaskValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getPairwiseCipher() {
        final String methodStr = "getPairwiseCipher";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getPairwiseCipher((SupplicantStatus status, int pairwiseCipherMaskValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.pairwiseCipherMask = pairwiseCipherMaskValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getPskPassphrase() {
        final String methodStr = "getPskPassphrase";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getPskPassphrase((SupplicantStatus status, String pskValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.pskPassphrase = pskValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getWepKeyCallback(int keyIdx) {
        final String methodStr = "keyIdx";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getWepKey(keyIdx, (SupplicantStatus status, java.util.ArrayList<Byte> wepKeyValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.wepKey = wepKeyValue;
                } else {
                    Log.e(TAG, methodStr + ",  failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getWepTxKeyIdx() {
        final String methodStr = "getWepTxKeyIdx";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getWepTxKeyIdx((SupplicantStatus status, int keyIdxValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.wepTxKeyIdx = keyIdxValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getRequirePmf() {
        final String methodStr = "getRequirePmf";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getRequirePmf((SupplicantStatus status, boolean enabledValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.requirePmf = enabledValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapMethod() {
        final String methodStr = "getEapMethod";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapMethod((SupplicantStatus status, int methodValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapMethod = methodValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapPhase2Method() {
        final String methodStr = "getEapPhase2Method";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapPhase2Method((SupplicantStatus status, int methodValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapPhase2Method = methodValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapIdentity() {
        final String methodStr = "getEapIdentity";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapIdentity((SupplicantStatus status, java.util.ArrayList<Byte> identityValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapIdentity = identityValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapAnonymousIdentity() {
        final String methodStr = "getEapAnonymousIdentity";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapAnonymousIdentity((SupplicantStatus status, java.util.ArrayList<Byte> identityValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapAnonymousIdentity = identityValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapPassword() {
        final String methodStr = "getEapPassword";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapPassword((SupplicantStatus status, java.util.ArrayList<Byte> passwordValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapPassword = passwordValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapCACert() {
        final String methodStr = "getEapCACert";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapCACert((SupplicantStatus status, String pathValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapCACert = pathValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapCAPath() {
        final String methodStr = "getEapCAPath";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapCAPath((SupplicantStatus status, String pathValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapCAPath = pathValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapClientCert() {
        final String methodStr = "getEapClientCert";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapClientCert((SupplicantStatus status, String pathValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapClientCert = pathValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapPrivateKey() {
        final String methodStr = "getEapPrivateKey";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapPrivateKey((SupplicantStatus status, String pathValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapPrivateKey = pathValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapSubjectMatch() {
        final String methodStr = "getEapSubjectMatch";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapSubjectMatch((SupplicantStatus status, String matchValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapSubjectMatch = matchValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapAltSubjectMatch() {
        final String methodStr = "getEapAltSubjectMatch";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapAltSubjectMatch((SupplicantStatus status, String matchValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapAltSubjectMatch = matchValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapEngine() {
        final String methodStr = "getEapEngine";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapEngine((SupplicantStatus status, boolean enabledValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapEngine = enabledValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapEngineID() {
        final String methodStr = "getEapEngineID";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapEngineID((SupplicantStatus status, String idValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapEngineID = idValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getEapDomainSuffixMatch() {
        final String methodStr = "getEapDomainSuffixMatch";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapDomainSuffixMatch((SupplicantStatus status, String matchValue) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.eapDomainSuffixMatch = matchValue;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean getIdStr() {
        final String methodStr = "getIdStr";
        Log.i(TAG, methodStr);
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getIdStr((SupplicantStatus status, String idString) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.idStr = idString;
                } else {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                }
            });
            return statusOk.value;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean enable(boolean noConnect) {
        final String methodStr = "enable";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.enable(noConnect);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean disable() {
        final String methodStr = "disable";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.disable();
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean select() {
        final String methodStr = "select";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.select();
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean sendNetworkEapSimGsmAuthResponse(ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams params) {
        final String methodStr = "sendNetworkEapSimGsmAuthResponse";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapSimGsmAuthResponse(params);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean sendNetworkEapSimUmtsAuthResponse(ISupplicantStaNetwork.NetworkResponseEapSimUmtsAuthParams params) {
        final String methodStr = "sendNetworkEapSimUmtsAuthResponse";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthResponse(params);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    public boolean sendNetworkEapIdentityResponse(java.util.ArrayList<Byte> identity) {
        final String methodStr = "sendNetworkEapIdentityResponse";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapIdentityResponse(identity);
            return checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    private boolean checkStatusAndLogFailure(SupplicantStatus status, final String methodStr) {
        Log.i(TAG, methodStr);
        if (status.code != SupplicantStatusCode.SUCCESS) {
            Log.e(TAG, methodStr + " failed: " + WifiNativeNew.supplicantStatusCodeToString(status.code) + ", " + status.debugMessage);
            return false;
        }
        return true;
    }

    /**
     * @return the UTF_8 char byte values of str, as an ArrayList
     */
    private static ArrayList<Byte> stringToByteArrayList(String str) {
        ArrayList<Byte> byteArrayList = new ArrayList<Byte>();
        for (byte b : str.getBytes(StandardCharsets.UTF_8)) {
            byteArrayList.add(new Byte(b));
        }
        return byteArrayList;
    }

    /**
     * @return the string decoded from UTF_8 byte values in byteArrayList
     *         returns null for null input
     */
    private static String stringFromByteArrayList(ArrayList<Byte> byteArrayList) {
        if (byteArrayList == null) return null;
        byte[] byteArray = new byte[byteArraylist.size()];
        int i = 0;
        for (Byte b : byteArraylist) {
            byteArray[i] = b;
            i++;
        }
        return new String(byteArray, StandardCharsets.UTF_8);
    }

    /**
     * Converts a mac address string to an array of Byes
     * @param bssidStr string of format: "XX:XX:XX:XX:XX:XX", where X is any hexadecimal digit.
     *        Passing "any" is the same as 00:00:00:00:00:00
     * @throws IllegalArgumentException for various malformed inputs
     */
    private static byte[] macAddressToByteArray(String bssidStr) {
        if (bssidStr == null) {
            throw new IllegalArgumentException("null bssid String");
        }
        if (ANY_BSSID_STR.equals(bssidStr)) return ANY_BSSID_BYTES;
        String cleanBssid = bssidStr.replace(":","");
        if (cleanBssid.length() != 12) {
            throw new IllegalArgumentException("invalid bssid String length: " + cleanBssid);
        }
        return HexEncoding.decode(cleanBssid.toCharArray(), false);
    }

    /**
     * Converts an array of 6 bytes to a HexEncoded String with format: "XX:XX:XX:XX:XX:XX", where X
     * is any hexadecimal digit.
     * @param macArray byte array of bssid values, must have length 6
     * @throws IllegalArgumentException for malformed inputs
     */
    private static String macAddressFromByteArray(byte[] macArray) {
        if (macArray == null) {
            throw new IllegalArgumentException("null macArray");
        }
        if (macArray.length() != 6) {
            throw new IllegalArgumentException("invalid macArray length: " + macArray.length());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < macArray.length(); i++) {
            sb.append(new String(HexEncoding.encode(macArray, i, 1))).append(":");
        }
        String mac = sb.toString();
        return mac.substring(0, mac.length() - 1); //remove trailing ':'
    }

    /**
     * Converts an ssid string to an arraylist of UTF_8 byte values.
     * Removes encapsulating quotation marks if they exist
     */
    private static ArrayList<Byte> encodeSsid(String ssid) {
        int length = ssid.length();
        if ((length > 1) && (ssid.charAt(0) == '"') && (ssid.charAt(length - 1) == '"')) {
            ssid = ssid.substring(1, length - 1);
        }
        return stringToByteArrayList(ssid);
    }

    /**
     * Converts an ArrayList<Byte> of UTF_8 byte values to an SSID String, encapsulated in quotes
     * Will return null given null ssidBytes
     */
    private static String decodeSsid(ArrayList<Byte> ssidBytes) {
        if (ssidBytes == null) return null;
        if (ssidBytes.size() == 0) return "\"\"";
        bytes[] ssidArray = new byte[ssidBytes.size()];
        int i = 0;
        for (Byte b : ssidBytes) {
            ssidArray[i] = b;
            i++;
        }
        return "\"" + (new String(ssidArray, StandardCharsets.UTF_8)) + "\"";
    }
}
