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

import java.lang.Byte;
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
    private static final int BSSID_LENGTH = 6;
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

    SupplicantStaNetwork(ISupplicantStaNetwork iSupplicantStaNetwork) {
        mISupplicantStaNetwork = iSupplicantStaNetwork;
    }

    /**
     * Save an entire WifiConfiguration to wpa_supplicant via HIDL
     */
    public boolean saveFromWifiConfiguration(WifiConfiguration config) {
        if (config == null) return false;

        /** SSID */
        if (config.SSID != null) {
            if(!setSsid(stringToByteArrayList(config.SSID))) {
                Log.e(TAG, "Failed to set SSID: " + config.SSID);
                return false;
            }
        }

        /** metadata: FQDN + ConfigKey + CreatorUid */
        // TODO(b/34284015): set metadata like 'setNetworkExtras' does when the HIDL command is

        /** BSSID */
        String bssidStr = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
        if (bssidStr != null) {
            byte[] bssid = HexEncoding.decode(bssidStr.toCharArray(), false);
            if (bssid.length != BSSID_LENGTH) {
                Log.e(TAG, "Invalid BSSID length: \"" + bssidStr + "\", length " + bssid.length);
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
        /** TODO(b/34454675) Deprecated */
        
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
        // wpa_supplicant can update the anonymous identity for these kinds of networks after
        // framework reads them, so make sure the framework doesn't try to overwrite them.
        boolean shouldNotWriteAnonIdentity =
                eapConfig.getEapMethod() == WifiEnterpriseConfig.Eap.SIM
                || eapConfig.getEapMethod() == WifiEnterpriseConfig.Eap.AKA
                || eapConfig.getEapMethod() == WifiEnterpriseConfig.Eap.AKA_PRIME;
/*
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
*/
        if (!setEapMethod(eapConfig.getEapMethod())) {
            Log.e(TAG, "Failed to set EapMethod");
            return false;
        }
/*
        if (!setEapPhase2Method(int method)) {
            Log.E(TAG, "Failed to set EapPhase2Method");
            return false;
        }
        if (!setEapIdentity(java.util.ArrayList<Byte> identity)) {
            Log.E(TAG, "Failed to set EapIdentity");
            return false;
        }
        if (!setEapAnonymousIdentity(java.util.ArrayList<Byte> identity)) {
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
                case WifiConfiguration.KeyMgmt.WPA2_PSK:
                case WifiConfiguration.KeyMgmt.OSEN:
                case WifiConfiguration.KeyMgmt.FT_PSK:
                case WifiConfiguration.KeyMgmt.FT_EAP:
                default:
                    break;
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
                    break;
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
                    break;
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
                default:
                    break;
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
                    break;
            }
        }
        return mask;
    };

    private static int createSupplicantEapMethod(BitSet eapMethod) {
        int mask = 0;
        for (int bit = eapMethod.nextSetBit(0); bit != -1; bit = eapMethod.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiEnterpriseConfig.Eap.PEAP:
                    mask |= ISupplicantStaNetwork.EapMethod.PEAP;
                    break;
                case WifiEnterpriseConfig.Eap.TLS:
                    mask |= ISupplicantStaNetwork.EapMethod.TLS;
                    break;
                case WifiEnterpriseConfig.Eap.TTLS:
                    mask |= ISupplicantStaNetwork.EapMethod.TTLS;
                    break;
                case WifiEnterpriseConfig.Eap.PWD:
                    mask |= ISupplicantStaNetwork.EapMethod.PWD;
                    break;
                case WifiEnterpriseConfig.Eap.SIM:
                    mask |= ISupplicantStaNetwork.EapMethod.SIM;
                    break;
                case WifiEnterpriseConfig.Eap.AKA:
                    mask |= ISupplicantStaNetwork.EapMethod.AKA;
                    break;
                case WifiEnterpriseConfig.Eap.AKA_PRIME:
                    mask |= ISupplicantStaNetwork.EapMethod.AKA_PRIME;
                    break;
                case WifiEnterpriseConfig.Eap.UNAUTH_TLS:
                    mask |= ISupplicantStaNetwork.EapMethod.WFA_UNAUTH_TLS;
                    break;
                case WifiEnterpriseConfig.Eap.NONE:
                default:
                    break;
            }
        }
        return mask;
    };

    private static int createSupplicantEapPhase2Method(BitSet eapPhase2Method) {
        int mask = 0;
        for (int bit = eapPhase2Method.nextSetBit(0); bit != -1; bit = eapPhase2Method.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiEnterpriseConfig.Phase2.NONE:
                    mask |= ISupplicantStaNetwork.EapPhase2Method.NONE;
                    break;
                case WifiEnterpriseConfig.Phase2.PAP:
                    mask |= ISupplicantStaNetwork.EapPhase2Method.PAP;
                    break;
                case WifiEnterpriseConfig.Phase2.MSCHAP:
                    mask |= ISupplicantStaNetwork.EapPhase2Method.MSPAP;
                    break;
                case WifiEnterpriseConfig.Phase2.MSCHAPV2:
                    mask |= ISupplicantStaNetwork.EapPhase2Method.MSPAPV2;
                    break;
                case WifiEnterpriseConfig.Phase2.GTC:
                    mask |= ISupplicantStaNetwork.EapPhase2Method.GTC;
                    break;
                default:
                    break;
            }
        }
        return mask;
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
    public boolean enable(boolean noConnect) {
        final String methodStr = "enable";
        Log.i(TAG, methodStr);
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.enable(noConnect);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
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
            Log.e(TAG, methodStr + " failed: " + status.debugMessage);
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
}
