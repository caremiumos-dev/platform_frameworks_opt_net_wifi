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
import android.util.Log;
import android.util.MutableBoolean;

import java.util.ArrayList;

    /**
     * Wrapper class for ISupplicantStaNetwork HIDL calls and callbacks
     * TBD notes on how this class is used
     */
public class SupplicantStaNetwork {
    private static final String TAG = "SupplicantStaNetwork";
    private final ISupplicantStaNetwork mISupplicantStaNetwork;

    public java.util.ArrayList<Byte> blahApSsid;
    public byte[/* 6 */] blahApBssid;
    public boolean blahScanSsid;
    public int blahKeyMgmtMask;
    public int blahProtoMask;
    public int blahAuthAlgMask;
    public int blahGroupCipherMask;
    public int blahPairwiseCipherMask;
    public String blahPskPassphrase;
    public java.util.ArrayList<Byte> blahWepKey;
    public int blahWepTxKeyIdx;
    public boolean blahRequirePmf;
    public int blahEapMethod;
    public int blahEapPhase2Method;
    public java.util.ArrayList<Byte> blahEapIdentity;
    public java.util.ArrayList<Byte> blahEapAnonymousIdentity;
    public java.util.ArrayList<Byte> blahEapPassword;
    public String blahEapCACert;
    public String blahEapCAPath;
    public String blahEapClientCert;
    public String blahEapPrivateKey;
    public String blahEapSubjectMatch;
    public String blahEapAltSubjectMatch;
    public boolean blahEapEngine;
    public String blahEapEngineID;
    public String blahEapDomainSuffixMatch;

    /**
     * @throws IllegalArgumentException if iSupplicantStaNetwork is null
     */
    SupplicantStaNetwork(ISupplicantStaNetwork iSupplicantStaNetwork) {
        if (iSupplicantStaNetwork == null) {
            throw new IllegalArgumentException("iSupplicantStaNetwork cannot be null");
        }
        mISupplicantStaNetwork = iSupplicantStaNetwork;
    }

    private boolean registerCallback(ISupplicantStaNetworkCallback callback) {
        SupplicantStatus status =  mISupplicantStaNetwork.registerCallback(callback);
        return WifiNativeNew.checkStatusAndLogFailure(status, "registerCallback");
    }
    private boolean setSsid(java.util.ArrayList<Byte> ssid) {
        SupplicantStatus status =  mISupplicantStaNetwork.setSsid(ssid);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setSsid");
    }
    private boolean setBssid(byte[/* 6 */] bssid) {
        SupplicantStatus status =  mISupplicantStaNetwork.setBssid(bssid);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setBssid");
    }
    private boolean setScanSsid(boolean enable) {
        SupplicantStatus status =  mISupplicantStaNetwork.setScanSsid(enable);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setScanSsid");
    }
    private boolean setKeyMgmt(int keyMgmtMask) {
        SupplicantStatus status =  mISupplicantStaNetwork.setKeyMgmt(keyMgmtMask);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setKeyMgmt");
    }
    private boolean setProto(int protoMask) {
        SupplicantStatus status =  mISupplicantStaNetwork.setProto(protoMask);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setProto");
    }
    private boolean setAuthAlg(int authAlgMask) {
        SupplicantStatus status =  mISupplicantStaNetwork.setAuthAlg(authAlgMask);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setAuthAlg");
    }
    private boolean setGroupCipher(int groupCipherMask) {
        SupplicantStatus status =  mISupplicantStaNetwork.setGroupCipher(groupCipherMask);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setGroupCipher");
    }
    private boolean setPairwiseCipher(int pairwiseCipherMask) {
        SupplicantStatus status =  mISupplicantStaNetwork.setPairwiseCipher(pairwiseCipherMask);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setPairwiseCipher");
    }
    private boolean setPskPassphrase(String psk) {
        SupplicantStatus status =  mISupplicantStaNetwork.setPskPassphrase(psk);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setPskPassphrase");
    }
    private boolean setWepKey(int keyIdx, java.util.ArrayList<Byte> wepKey) {
        SupplicantStatus status =  mISupplicantStaNetwork.setWepKey(keyIdx, wepKey);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setWepKey");
    }
    private boolean setWepTxKeyIdx(int keyIdx) {
        SupplicantStatus status =  mISupplicantStaNetwork.setWepTxKeyIdx(keyIdx);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setWepTxKeyIdx");
    }
    private boolean setRequirePmf(boolean enable) {
        SupplicantStatus status =  mISupplicantStaNetwork.setRequirePmf(enable);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setRequirePmf");
    }
    private boolean setEapMethod(int method) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapMethod(method);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapMethod");
    }
    private boolean setEapPhase2Method(int method) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapPhase2Method(method);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapPhase2Method");
    }
    private boolean setEapIdentity(java.util.ArrayList<Byte> identity) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapIdentity(identity);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapIdentity");
    }
    private boolean setEapAnonymousIdentity(java.util.ArrayList<Byte> identity) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapAnonymousIdentity(identity);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapAnonymousIdentity");
    }
    private boolean setEapPassword(java.util.ArrayList<Byte> password) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapPassword(password);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapPassword");
    }
    private boolean setEapCACert(String path) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapCACert(path);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapCACert");
    }
    private boolean setEapCAPath(String path) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapCAPath(path);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapCAPath");
    }
    private boolean setEapClientCert(String path) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapClientCert(path);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapClientCert");
    }
    private boolean setEapPrivateKey(String path) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapPrivateKey(path);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapPrivateKey");
    }
    private boolean setEapSubjectMatch(String match) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapSubjectMatch(match);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapSubjectMatch");
    }
    private boolean setEapAltSubjectMatch(String match) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapAltSubjectMatch(match);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapAltSubjectMatch");
    }
    private boolean setEapEngine(boolean enable) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapEngine(enable);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapEngine");
    }
    private boolean setEapEngineID(String id) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapEngineID(id);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapEngineID");
    }
    private boolean setEapDomainSuffixMatch(String match) {
        SupplicantStatus status =  mISupplicantStaNetwork.setEapDomainSuffixMatch(match);
        return WifiNativeNew.checkStatusAndLogFailure(status, "setEapDomainSuffixMatch");
    }
    private boolean getSsid() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getSsid((SupplicantStatus status, java.util.ArrayList<Byte> ssid) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahApSsid = ssid;
            } else {
                Log.e(TAG, "getSsid failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getBssid() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getBssid((SupplicantStatus status, byte[/* 6 */] bssid) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahApBssid = bssid;
            } else {
                Log.e(TAG, "getBssid failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getScanSsid() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getScanSsid((SupplicantStatus status, boolean enabled) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahScanSsid = enabled;
            } else {
                Log.e(TAG, "getScanSsid failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getKeyMgmt() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getKeyMgmt((SupplicantStatus status, int keyMgmtMask) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahKeyMgmtMask = keyMgmtMask;
            } else {
                Log.e(TAG, "getKeyMgmt failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getProto() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getProto((SupplicantStatus status, int protoMask) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahProtoMask = protoMask;
            } else {
                Log.e(TAG, "getProto failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getAuthAlg() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getAuthAlg((SupplicantStatus status, int authAlgMask) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahAuthAlgMask = authAlgMask;
            } else {
                Log.e(TAG, "getAuthAlg failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getGroupCipher() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getGroupCipher((SupplicantStatus status, int groupCipherMask) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahGroupCipherMask = groupCipherMask;
            } else {
                Log.e(TAG, "getGroupCipher failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getPairwiseCipher() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getPairwiseCipher((SupplicantStatus status, int pairwiseCipherMask) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahPairwiseCipherMask = pairwiseCipherMask;
            } else {
                Log.e(TAG, "getPairwiseCipher failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getPskPassphrase() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getPskPassphrase((SupplicantStatus status, String psk) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahPskPassphrase = psk;
            } else {
                Log.e(TAG, "getPskPassphrase failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getWepKeyCallback(int keyIdx) {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getWepKey(keyIdx, (SupplicantStatus status, java.util.ArrayList<Byte> wepKey) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahWepKey = wepKey;
            } else {
                Log.e(TAG, "keyIdx,  failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getWepTxKeyIdx() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getWepTxKeyIdx((SupplicantStatus status, int keyIdx) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahWepTxKeyIdx = keyIdx;
            } else {
                Log.e(TAG, "getWepTxKeyIdx failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getRequirePmf() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getRequirePmf((SupplicantStatus status, boolean enabled) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahRequirePmf = enabled;
            } else {
                Log.e(TAG, "getRequirePmf failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapMethod() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapMethod((SupplicantStatus status, int method) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapMethod = method;
            } else {
                Log.e(TAG, "getEapMethod failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapPhase2Method() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapPhase2Method((SupplicantStatus status, int method) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapPhase2Method = method;
            } else {
                Log.e(TAG, "getEapPhase2Method failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapIdentity() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapIdentity((SupplicantStatus status, java.util.ArrayList<Byte> identity) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapIdentity = identity;
            } else {
                Log.e(TAG, "getEapIdentity failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapAnonymousIdentity() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapAnonymousIdentity((SupplicantStatus status, java.util.ArrayList<Byte> identity) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapAnonymousIdentity = identity;
            } else {
                Log.e(TAG, "getEapAnonymousIdentity failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapPassword() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapPassword((SupplicantStatus status, java.util.ArrayList<Byte> password) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapPassword = password;
            } else {
                Log.e(TAG, "getEapPassword failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapCACert() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapCACert((SupplicantStatus status, String path) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapCACert = path;
            } else {
                Log.e(TAG, "getEapCACert failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapCAPath() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapCAPath((SupplicantStatus status, String path) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapCAPath = path;
            } else {
                Log.e(TAG, "getEapCAPath failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapClientCert() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapClientCert((SupplicantStatus status, String path) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapClientCert = path;
            } else {
                Log.e(TAG, "getEapClientCert failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapPrivateKey() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapPrivateKey((SupplicantStatus status, String path) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapPrivateKey = path;
            } else {
                Log.e(TAG, "getEapPrivateKey failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapSubjectMatch() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapSubjectMatch((SupplicantStatus status, String match) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapSubjectMatch = match;
            } else {
                Log.e(TAG, "getEapSubjectMatch failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapAltSubjectMatch() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapAltSubjectMatch((SupplicantStatus status, String match) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapAltSubjectMatch = match;
            } else {
                Log.e(TAG, "getEapAltSubjectMatch failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapEngine() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapEngine((SupplicantStatus status, boolean enabled) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapEngine = enabled;
            } else {
                Log.e(TAG, "getEapEngine failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapEngineID() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapEngineID((SupplicantStatus status, String id) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapEngineID = id;
            } else {
                Log.e(TAG, "getEapEngineID failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean getEapDomainSuffixMatch() {
        MutableBoolean statusOk = new MutableBoolean(false);
        mISupplicantStaNetwork.getEapDomainSuffixMatch((SupplicantStatus status, String match) -> {
            statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
            if (statusOk.value) {
                this.blahEapDomainSuffixMatch = match;
            } else {
                Log.e(TAG, "getEapDomainSuffixMatch failed: " + status.debugMessage);
            }
        });
        return statusOk.value;
    }
    private boolean enable(boolean noConnect) {
        SupplicantStatus status =  mISupplicantStaNetwork.enable(noConnect);
        return WifiNativeNew.checkStatusAndLogFailure(status, "enable");
    }
    private boolean disable() {
        SupplicantStatus status =  mISupplicantStaNetwork.disable();
        return WifiNativeNew.checkStatusAndLogFailure(status, "disable");
    }
    private boolean select() {
        SupplicantStatus status =  mISupplicantStaNetwork.select();
        return WifiNativeNew.checkStatusAndLogFailure(status, "select");
    }
    private boolean sendNetworkEapSimGsmAuthResponse(ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams params) {
        SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapSimGsmAuthResponse(params);
        return WifiNativeNew.checkStatusAndLogFailure(status, "sendNetworkEapSimGsmAuthResponse");
    }
    private boolean sendNetworkEapSimUmtsAuthResponse(ISupplicantStaNetwork.NetworkResponseEapSimUmtsAuthParams params) {
        SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthResponse(params);
        return WifiNativeNew.checkStatusAndLogFailure(status, "sendNetworkEapSimUmtsAuthResponse");
    }
    private boolean sendNetworkEapIdentityResponse(java.util.ArrayList<Byte> identity) {
        SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapIdentityResponse(identity);
        return WifiNativeNew.checkStatusAndLogFailure(status, "sendNetworkEapIdentityResponse");
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    private boolean checkStatusAndLogFailure(SupplicantStatus status, final String methodStr) {
        if (status.code != SupplicantStatusCode.SUCCESS) {
            Log.e(TAG, methodStr + " failed: " + status.debugMessage);
            return false;
        }
        return true;
    }
}
