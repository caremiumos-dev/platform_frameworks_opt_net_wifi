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

    SupplicantStaNetwork(ISupplicantStaNetwork iSupplicantStaNetwork) {
        mISupplicantStaNetwork = iSupplicantStaNetwork;
    }

    private boolean registerCallback(ISupplicantStaNetworkCallback callback) {
        final String methodStr = "registerCallback";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.registerCallback(callback);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setSsid(java.util.ArrayList<Byte> ssid) {
        final String methodStr = "setSsid";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setSsid(ssid);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setBssid(byte[/* 6 */] bssid) {
        final String methodStr = "setBssid";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setBssid(bssid);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setScanSsid(boolean enable) {
        final String methodStr = "setScanSsid";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setScanSsid(enable);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setKeyMgmt(int keyMgmtMask) {
        final String methodStr = "setKeyMgmt";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setKeyMgmt(keyMgmtMask);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setProto(int protoMask) {
        final String methodStr = "setProto";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setProto(protoMask);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setAuthAlg(int authAlgMask) {
        final String methodStr = "setAuthAlg";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setAuthAlg(authAlgMask);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setGroupCipher(int groupCipherMask) {
        final String methodStr = "setGroupCipher";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setGroupCipher(groupCipherMask);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setPairwiseCipher(int pairwiseCipherMask) {
        final String methodStr = "setPairwiseCipher";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setPairwiseCipher(pairwiseCipherMask);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setPskPassphrase(String psk) {
        final String methodStr = "setPskPassphrase";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setPskPassphrase(psk);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setWepKey(int keyIdx, java.util.ArrayList<Byte> wepKey) {
        final String methodStr = "setWepKey";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setWepKey(keyIdx, wepKey);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setWepTxKeyIdx(int keyIdx) {
        final String methodStr = "setWepTxKeyIdx";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setWepTxKeyIdx(keyIdx);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setRequirePmf(boolean enable) {
        final String methodStr = "setRequirePmf";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setRequirePmf(enable);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapMethod(int method) {
        final String methodStr = "setEapMethod";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapMethod(method);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapPhase2Method(int method) {
        final String methodStr = "setEapPhase2Method";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapPhase2Method(method);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapIdentity(java.util.ArrayList<Byte> identity) {
        final String methodStr = "setEapIdentity";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapIdentity(identity);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapAnonymousIdentity(java.util.ArrayList<Byte> identity) {
        final String methodStr = "setEapAnonymousIdentity";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapAnonymousIdentity(identity);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapPassword(java.util.ArrayList<Byte> password) {
        final String methodStr = "setEapPassword";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapPassword(password);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapCACert(String path) {
        final String methodStr = "setEapCACert";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapCACert(path);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapCAPath(String path) {
        final String methodStr = "setEapCAPath";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapCAPath(path);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapClientCert(String path) {
        final String methodStr = "setEapClientCert";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapClientCert(path);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapPrivateKey(String path) {
        final String methodStr = "setEapPrivateKey";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapPrivateKey(path);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapSubjectMatch(String match) {
        final String methodStr = "setEapSubjectMatch";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapSubjectMatch(match);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapAltSubjectMatch(String match) {
        final String methodStr = "setEapAltSubjectMatch";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapAltSubjectMatch(match);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapEngine(boolean enable) {
        final String methodStr = "setEapEngine";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapEngine(enable);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapEngineID(String id) {
        final String methodStr = "setEapEngineID";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapEngineID(id);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean setEapDomainSuffixMatch(String match) {
        final String methodStr = "setEapDomainSuffixMatch";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.setEapDomainSuffixMatch(match);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean getSsid() {
        final String methodStr = "getSsid";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getSsid((SupplicantStatus status, java.util.ArrayList<Byte> ssid) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahApSsid = ssid;
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
    private boolean getBssid() {
        final String methodStr = "getBssid";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getBssid((SupplicantStatus status, byte[/* 6 */] bssid) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahApBssid = bssid;
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
    private boolean getScanSsid() {
        final String methodStr = "getScanSsid";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getScanSsid((SupplicantStatus status, boolean enabled) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahScanSsid = enabled;
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
    private boolean getKeyMgmt() {
        final String methodStr = "getKeyMgmt";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getKeyMgmt((SupplicantStatus status, int keyMgmtMask) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahKeyMgmtMask = keyMgmtMask;
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
    private boolean getProto() {
        final String methodStr = "getProto";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getProto((SupplicantStatus status, int protoMask) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahProtoMask = protoMask;
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
    private boolean getAuthAlg() {
        final String methodStr = "getAuthAlg";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getAuthAlg((SupplicantStatus status, int authAlgMask) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahAuthAlgMask = authAlgMask;
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
    private boolean getGroupCipher() {
        final String methodStr = "getGroupCipher";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getGroupCipher((SupplicantStatus status, int groupCipherMask) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahGroupCipherMask = groupCipherMask;
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
    private boolean getPairwiseCipher() {
        final String methodStr = "getPairwiseCipher";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getPairwiseCipher((SupplicantStatus status, int pairwiseCipherMask) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahPairwiseCipherMask = pairwiseCipherMask;
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
    private boolean getPskPassphrase() {
        final String methodStr = "getPskPassphrase";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getPskPassphrase((SupplicantStatus status, String psk) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahPskPassphrase = psk;
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
    private boolean getWepKeyCallback(int keyIdx) {
        final String methodStr = "keyIdx";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getWepKey(keyIdx, (SupplicantStatus status, java.util.ArrayList<Byte> wepKey) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahWepKey = wepKey;
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
    private boolean getWepTxKeyIdx() {
        final String methodStr = "getWepTxKeyIdx";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getWepTxKeyIdx((SupplicantStatus status, int keyIdx) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahWepTxKeyIdx = keyIdx;
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
    private boolean getRequirePmf() {
        final String methodStr = "getRequirePmf";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getRequirePmf((SupplicantStatus status, boolean enabled) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahRequirePmf = enabled;
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
    private boolean getEapMethod() {
        final String methodStr = "getEapMethod";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapMethod((SupplicantStatus status, int method) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapMethod = method;
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
    private boolean getEapPhase2Method() {
        final String methodStr = "getEapPhase2Method";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapPhase2Method((SupplicantStatus status, int method) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapPhase2Method = method;
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
    private boolean getEapIdentity() {
        final String methodStr = "getEapIdentity";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapIdentity((SupplicantStatus status, java.util.ArrayList<Byte> identity) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapIdentity = identity;
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
    private boolean getEapAnonymousIdentity() {
        final String methodStr = "getEapAnonymousIdentity";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapAnonymousIdentity((SupplicantStatus status, java.util.ArrayList<Byte> identity) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapAnonymousIdentity = identity;
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
    private boolean getEapPassword() {
        final String methodStr = "getEapPassword";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapPassword((SupplicantStatus status, java.util.ArrayList<Byte> password) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapPassword = password;
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
    private boolean getEapCACert() {
        final String methodStr = "getEapCACert";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapCACert((SupplicantStatus status, String path) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapCACert = path;
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
    private boolean getEapCAPath() {
        final String methodStr = "getEapCAPath";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapCAPath((SupplicantStatus status, String path) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapCAPath = path;
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
    private boolean getEapClientCert() {
        final String methodStr = "getEapClientCert";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapClientCert((SupplicantStatus status, String path) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapClientCert = path;
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
    private boolean getEapPrivateKey() {
        final String methodStr = "getEapPrivateKey";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapPrivateKey((SupplicantStatus status, String path) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapPrivateKey = path;
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
    private boolean getEapSubjectMatch() {
        final String methodStr = "getEapSubjectMatch";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapSubjectMatch((SupplicantStatus status, String match) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapSubjectMatch = match;
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
    private boolean getEapAltSubjectMatch() {
        final String methodStr = "getEapAltSubjectMatch";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapAltSubjectMatch((SupplicantStatus status, String match) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapAltSubjectMatch = match;
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
    private boolean getEapEngine() {
        final String methodStr = "getEapEngine";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapEngine((SupplicantStatus status, boolean enabled) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapEngine = enabled;
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
    private boolean getEapEngineID() {
        final String methodStr = "getEapEngineID";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapEngineID((SupplicantStatus status, String id) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapEngineID = id;
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
    private boolean getEapDomainSuffixMatch() {
        final String methodStr = "getEapDomainSuffixMatch";
        try {
            MutableBoolean statusOk = new MutableBoolean(false);
            mISupplicantStaNetwork.getEapDomainSuffixMatch((SupplicantStatus status, String match) -> {
                statusOk.value = status.code == SupplicantStatusCode.SUCCESS;
                if (statusOk.value) {
                    this.blahEapDomainSuffixMatch = match;
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
    private boolean enable(boolean noConnect) {
        final String methodStr = "enable";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.enable(noConnect);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean disable() {
        final String methodStr = "disable";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.disable();
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean select() {
        final String methodStr = "select";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.select();
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean sendNetworkEapSimGsmAuthResponse(ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams params) {
        final String methodStr = "sendNetworkEapSimGsmAuthResponse";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapSimGsmAuthResponse(params);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean sendNetworkEapSimUmtsAuthResponse(ISupplicantStaNetwork.NetworkResponseEapSimUmtsAuthParams params) {
        final String methodStr = "sendNetworkEapSimUmtsAuthResponse";
        try {
            SupplicantStatus status =  mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthResponse(params);
            return WifiNativeNew.checkStatusAndLogFailure(status, methodStr);
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
            return false;
        }
    }
    private boolean sendNetworkEapIdentityResponse(java.util.ArrayList<Byte> identity) {
        final String methodStr = "sendNetworkEapIdentityResponse";
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
        if (status.code != SupplicantStatusCode.SUCCESS) {
            Log.e(TAG, methodStr + " failed: " + status.debugMessage);
            return false;
        }
        return true;
    }
}
