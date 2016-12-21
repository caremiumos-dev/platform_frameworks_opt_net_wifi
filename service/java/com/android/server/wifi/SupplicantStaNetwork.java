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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    public boolean saveNetworkVariables(WifiConfiguration config) {
        if (config == null) return false;

        /** SSID */
        if (config.SSID != null && !setSsid(config.SSID.getBytes(StandardCharsets.UTF_8))) {
            Log.e(TAG, "Failed to set SSID: " + config.SSID);
            return false;
        }

        /** metadata: FQDN + ConfigKey + CreatorUid */
        // TODO(b/34284015): set metadata like 'setNetworkExtras' does when the HIDL command is

        /** BSSID */
        String bssidStr = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
        if (bssidStr != null) {
            byte[] bssid = HexEncoding.decode(bssidStr.toCharArray(), false);
            if (bssid.length != BSSID_LENGTH) {
                throw new IllegalArgumentException()
            }
            if (!setBssid(bssid)) {
                Log.e(TAG, "Failed to set BSSID: " + bssidStr);
            }
        }

        /** key_mgmt */
        if (!setKeyMgmt(createSupplicantKeyMgmtMask(config.allowedKeyManagement))) {
            Log.e(TAG, "Failed to set Key Management");
        }

        /** security protocol */

    }

    /**
     * Maps WifiConfiguration Key Management BitSet to Supplicant HIDL bitmask int
     * TODO(b/32571829): Update mapping when fast transition keys are added
     * @return bitmask int describing the allowed Key Management schemes, readable by the Supplicant
     *         HIDL hal
     */
    private int createSupplicantKeyMgmtMask(BitSet keyMgmt) {
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

/** WPA/IEEE 802.11i/D3.0 */
        public static final int WPA = 0;
        /** WPA2/IEEE 802.11i */
        public static final int RSN = 1;
        /** HS2.0 r2 OSEN
         * @hide
         */
        public static final int OSEN = 2;
    private int createSupplicantProtoMask(BitSet protoMask) {
        int mask = 0;
        for (int bit = protoMask.nextSetBit(0); bit != -1; bit  = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                mask |= ISupplicantStaNetwork.ProtoMask.WPA;
                mask |= ISupplicantStaNetwork.ProtoMask.RSN;
                mask |= ISupplicantStaNetwork.ProtoMask.OSEN;
            }
        }
    };

/** Open System authentication (required for WPA/WPA2) */
        public static final int OPEN = 0;
        /** Shared Key authentication (requires static WEP keys) */
        public static final int SHARED = 1;
        /** LEAP/Network EAP (only used with LEAP) */
        public static final int LEAP = 2;
    private int createSupplicantAuthAlgMask(BitSet authAlgMask) {
        int mask = 0;
        for (int bit = authAlgMask.nextSetBit(0); bit != -1; bit  = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                mask |= ISupplicantStaNetwork.ProtoMask.OPEN;
                mask |= ISupplicantStaNetwork.ProtoMask.SHARED;
                mask |= ISupplicantStaNetwork.ProtoMask.LEAP;
            }
        }
    };

/** WEP40 = WEP (Wired Equivalent Privacy) with 40-bit key (original 802.11) */
        public static final int WEP40 = 0;
        /** WEP104 = WEP (Wired Equivalent Privacy) with 104-bit key */
        public static final int WEP104 = 1;
        /** Temporal Key Integrity Protocol [IEEE 802.11i/D7.0] */
        public static final int TKIP = 2;
        /** AES in Counter mode with CBC-MAC [RFC 3610, IEEE 802.11i/D7.0] */
        public static final int CCMP = 3;
        /** Hotspot 2.0 r2 OSEN
         * @hide
         */
        public static final int GTK_NOT_USED = 4;
    private int createSupplicantGroupCipherMask(BitSet groupCipherMask) {
        int mask = 0;
        for (int bit = groupCipherMask.nextSetBit(0); bit != -1; bit  = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                mask |= ISupplicantStaNetwork.ProtoMask.WEP40;
                mask |= ISupplicantStaNetwork.ProtoMask.WEP104;
                mask |= ISupplicantStaNetwork.ProtoMask.TKIP;
                mask |= ISupplicantStaNetwork.ProtoMask.CCMP;
            }
        }
    };

/** Use only Group keys (deprecated) */
        public static final int NONE = 0;
        /** Temporal Key Integrity Protocol [IEEE 802.11i/D7.0] */
        public static final int TKIP = 1;
        /** AES in Counter mode with CBC-MAC [RFC 3610, IEEE 802.11i/D7.0] */
        public static final int CCMP = 2;
    private int createSupplicantPairwiseCipherMask(BitSet pairwiseCipherMask) {
        int mask = 0;
        for (int bit = pairwiseCipherMask.nextSetBit(0); bit != -1; bit  = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                mask |= ISupplicantStaNetwork.ProtoMask.NONE;
                mask |= ISupplicantStaNetwork.ProtoMask.TKIP;
                mask |= ISupplicantStaNetwork.ProtoMask.CCMP;
            }
        }
    };

/** No EAP method used. Represents an empty config */
        public static final int NONE    = -1;
        /** Protected EAP */
        public static final int PEAP    = 0;
        /** EAP-Transport Layer Security */
        public static final int TLS     = 1;
        /** EAP-Tunneled Transport Layer Security */
        public static final int TTLS    = 2;
        /** EAP-Password */
        public static final int PWD     = 3;
        /** EAP-Subscriber Identity Module */
        public static final int SIM     = 4;
        /** EAP-Authentication and Key Agreement */
        public static final int AKA     = 5;
        /** EAP-Authentication and Key Agreement Prime */
        public static final int AKA_PRIME = 6;
        /** Hotspot 2.0 r2 OSEN */
        public static final int UNAUTH_TLS = 7;
    private int createSupplicantEapMethod(BitSet eapMethod) {
        int mask = 0;
        for (int bit = eapMethod.nextSetBit(0); bit != -1; bit  = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                mask |= ISupplicantStaNetwork.ProtoMask.PEAP;
                mask |= ISupplicantStaNetwork.ProtoMask.TLS;
                mask |= ISupplicantStaNetwork.ProtoMask.TTLS;
                mask |= ISupplicantStaNetwork.ProtoMask.PWD;
                mask |= ISupplicantStaNetwork.ProtoMask.SIM;
                mask |= ISupplicantStaNetwork.ProtoMask.AKA;
                mask |= ISupplicantStaNetwork.ProtoMask.AKA_PRIME;
                mask |= ISupplicantStaNetwork.ProtoMask.WFA_UNAUTH_TLS;
            }
        }
    };

public static final int NONE        = 0;
        /** Password Authentication Protocol */
        public static final int PAP         = 1;
        /** Microsoft Challenge Handshake Authentication Protocol */
        public static final int MSCHAP      = 2;
        /** Microsoft Challenge Handshake Authentication Protocol v2 */
        public static final int MSCHAPV2    = 3;
        /** Generic Token Card */
        public static final int GTC         = 4;
    private int createSupplicantEapPhase2Method(BitSet eapPhase2Method) {
        int mask = 0;
        for (int bit = eapPhase2Method.nextSetBit(0); bit != -1; bit  = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                mask |= ISupplicantStaNetwork.ProtoMask.NONE;
                mask |= ISupplicantStaNetwork.ProtoMask.PAP;
                mask |= ISupplicantStaNetwork.ProtoMask.MSPAP;
                mask |= ISupplicantStaNetwork.ProtoMask.MSPAPV2;
                mask |= ISupplicantStaNetwork.ProtoMask.GTC;
            }
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
}
