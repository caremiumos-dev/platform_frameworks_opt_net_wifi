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

import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetworkCallback;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.os.RemoteException;
import android.util.Log;
import android.util.MutableBoolean;

import java.util.ArrayList;
/**
 * Wrapper class for ISupplicantStaNetwork HAL calls. Gets and sets supplicant sta network variables
 * and interacts with networks.
 * Public fields should be treated as invalid until their 'get' method is called, which will set the
 * value if it returns true
 */
public class SupplicantStaNetworkHal {
    private static final String TAG = "SupplicantStaNetworkHal";
    private final ISupplicantStaNetwork mISupplicantStaNetwork;
    private final Object mLock = new Object();

    public int id;
    public String name;
    public int type;
    public ArrayList<Byte> apSsid;
    public byte[/* 6 */] apBssid;
    public boolean scanSsid;
    public int keyMgmtMask;
    public int protoMask;
    public int authAlgMask;
    public int groupCipherMask;
    public int pairwiseCipherMask;
    public String pskPassphrase;
    public ArrayList<Byte> wepKey;
    public int wepTxKeyIdx;
    public boolean requirePmf;
    public int eapMethod;
    public int eapPhase2Method;
    public ArrayList<Byte> eapIdentity;
    public ArrayList<Byte> eapAnonymousIdentity;
    public ArrayList<Byte> eapPassword;
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

    SupplicantStaNetworkHal(ISupplicantStaNetwork iSupplicantStaNetwork) {
        mISupplicantStaNetwork = iSupplicantStaNetwork;
    }

    /** See ISupplicantNetwork.hal for documentation */
    public boolean getId() {
        synchronized (mLock) {
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
    }

    /** See ISupplicantNetwork.hal for documentation */
    public boolean getInterfaceName() {
        synchronized (mLock) {
            final String methodStr = "getInterfaceName";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getInterfaceName((SupplicantStatus status,
                        String nameValue) -> {
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
    }

    /** See ISupplicantNetwork.hal for documentation */
    public boolean getType() {
        synchronized (mLock) {
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
    }

    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean registerCallback(ISupplicantStaNetworkCallback callback) {
        synchronized (mLock) {
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
    }

    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setSsid(java.util.ArrayList<Byte> ssid) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setBssid(byte[/* 6 */] bssid) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setScanSsid(boolean enable) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setKeyMgmt(int keyMgmtMask) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setProto(int protoMask) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setAuthAlg(int authAlgMask) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setGroupCipher(int groupCipherMask) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setPairwiseCipher(int pairwiseCipherMask) {
        synchronized (mLock) {
            final String methodStr = "setPairwiseCipher";
            Log.i(TAG, methodStr);
            try {
                SupplicantStatus status =
                        mISupplicantStaNetwork.setPairwiseCipher(pairwiseCipherMask);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ": exception: " + e);
                return false;
            }
        }
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setPskPassphrase(String psk) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setWepKey(int keyIdx, java.util.ArrayList<Byte> wepKey) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setWepTxKeyIdx(int keyIdx) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setRequirePmf(boolean enable) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapMethod(int method) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapPhase2Method(int method) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapIdentity(java.util.ArrayList<Byte> identity) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapAnonymousIdentity(java.util.ArrayList<Byte> identity) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapPassword(java.util.ArrayList<Byte> password) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapCACert(String path) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapCAPath(String path) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapClientCert(String path) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapPrivateKey(String path) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapSubjectMatch(String match) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapAltSubjectMatch(String match) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapEngine(boolean enable) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapEngineID(String id) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setEapDomainSuffixMatch(String match) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean setIdStr(String idString) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getSsid() {
        synchronized (mLock) {
            final String methodStr = "getSsid";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getSsid((SupplicantStatus status,
                        java.util.ArrayList<Byte> ssidValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getBssid() {
        synchronized (mLock) {
            final String methodStr = "getBssid";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getBssid((SupplicantStatus status,
                        byte[/* 6 */] bssidValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getScanSsid() {
        synchronized (mLock) {
            final String methodStr = "getScanSsid";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getScanSsid((SupplicantStatus status,
                        boolean enabledValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getKeyMgmt() {
        synchronized (mLock) {
            final String methodStr = "getKeyMgmt";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getKeyMgmt((SupplicantStatus status,
                        int keyMgmtMaskValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getProto() {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getAuthAlg() {
        synchronized (mLock) {
            final String methodStr = "getAuthAlg";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getAuthAlg((SupplicantStatus status,
                        int authAlgMaskValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getGroupCipher() {
        synchronized (mLock) {
            final String methodStr = "getGroupCipher";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getGroupCipher((SupplicantStatus status,
                        int groupCipherMaskValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getPairwiseCipher() {
        synchronized (mLock) {
            final String methodStr = "getPairwiseCipher";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getPairwiseCipher((SupplicantStatus status,
                        int pairwiseCipherMaskValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getPskPassphrase() {
        synchronized (mLock) {
            final String methodStr = "getPskPassphrase";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getPskPassphrase((SupplicantStatus status,
                        String pskValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getWepKeyCallback(int keyIdx) {
        synchronized (mLock) {
            final String methodStr = "keyIdx";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getWepKey(keyIdx, (SupplicantStatus status,
                        java.util.ArrayList<Byte> wepKeyValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getWepTxKeyIdx() {
        synchronized (mLock) {
            final String methodStr = "getWepTxKeyIdx";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getWepTxKeyIdx((SupplicantStatus status,
                        int keyIdxValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getRequirePmf() {
        synchronized (mLock) {
            final String methodStr = "getRequirePmf";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getRequirePmf((SupplicantStatus status,
                        boolean enabledValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapMethod() {
        synchronized (mLock) {
            final String methodStr = "getEapMethod";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapMethod((SupplicantStatus status,
                        int methodValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapPhase2Method() {
        synchronized (mLock) {
            final String methodStr = "getEapPhase2Method";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapPhase2Method((SupplicantStatus status,
                        int methodValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapIdentity() {
        synchronized (mLock) {
            final String methodStr = "getEapIdentity";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapIdentity((SupplicantStatus status,
                        ArrayList<Byte> identityValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapAnonymousIdentity() {
        synchronized (mLock) {
            final String methodStr = "getEapAnonymousIdentity";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapAnonymousIdentity((SupplicantStatus status,
                        ArrayList<Byte> identityValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapPassword() {
        synchronized (mLock) {
            final String methodStr = "getEapPassword";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapPassword((SupplicantStatus status,
                        ArrayList<Byte> passwordValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapCACert() {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapCAPath() {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapClientCert() {
        synchronized (mLock) {
            final String methodStr = "getEapClientCert";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapClientCert((SupplicantStatus status,
                        String pathValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapPrivateKey() {
        synchronized (mLock) {
            final String methodStr = "getEapPrivateKey";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapPrivateKey((SupplicantStatus status,
                        String pathValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapSubjectMatch() {
        synchronized (mLock) {
            final String methodStr = "getEapSubjectMatch";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapSubjectMatch((SupplicantStatus status,
                        String matchValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapAltSubjectMatch() {
        synchronized (mLock) {
            final String methodStr = "getEapAltSubjectMatch";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapAltSubjectMatch((SupplicantStatus status,
                        String matchValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapEngine() {
        synchronized (mLock) {
            final String methodStr = "getEapEngine";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapEngine((SupplicantStatus status,
                        boolean enabledValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapEngineID() {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getEapDomainSuffixMatch() {
        synchronized (mLock) {
            final String methodStr = "getEapDomainSuffixMatch";
            Log.i(TAG, methodStr);
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                mISupplicantStaNetwork.getEapDomainSuffixMatch((SupplicantStatus status,
                        String matchValue) -> {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean getIdStr() {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean enable(boolean noConnect) {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean disable() {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean select() {
        synchronized (mLock) {
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
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean sendNetworkEapSimGsmAuthResponse(
            ArrayList<ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams> params) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimGsmAuthResponse";
            Log.i(TAG, methodStr);
            try {
                SupplicantStatus status =
                        mISupplicantStaNetwork.sendNetworkEapSimGsmAuthResponse(params);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ":exception: " + e);
                return false;
            }
        }
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean sendNetworkEapSimGsmAuthFailure() {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimGsmAuthFailure";
            Log.i(TAG, methodStr);
            try {
                SupplicantStatus status = mISupplicantStaNetwork.sendNetworkEapSimGsmAuthFailure();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ":exception: " + e);
                return false;
            }
        }
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean sendNetworkEapSimUmtsAuthResponse(
            ISupplicantStaNetwork.NetworkResponseEapSimUmtsAuthParams params) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimUmtsAuthResponse";
            Log.i(TAG, methodStr);
            try {
                SupplicantStatus status =
                        mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthResponse(params);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ":exception: " + e);
                return false;
            }
        }
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean sendNetworkEapSimUmtsAutsResponse(byte[/* 14 */] auts) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimUmtsAutsResponse";
            Log.i(TAG, methodStr);
            try {
                SupplicantStatus status =
                        mISupplicantStaNetwork.sendNetworkEapSimUmtsAutsResponse(auts);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ":exception: " + e);
                return false;
            }
        }
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean sendNetworkEapSimUmtsAuthFailure() {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimUmtsAuthFailure";
            Log.i(TAG, methodStr);
            try {
                SupplicantStatus status = mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthFailure();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ":exception: " + e);
                return false;
            }
        }
    }
    /** See ISupplicantStaNetwork.hal for documentation */
    public boolean sendNetworkEapIdentityResponse(ArrayList<Byte> identity) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapIdentityResponse";
            Log.i(TAG, methodStr);
            try {
                SupplicantStatus status =
                        mISupplicantStaNetwork.sendNetworkEapIdentityResponse(identity);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + ":exception: " + e);
                return false;
            }
        }
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    private boolean checkStatusAndLogFailure(SupplicantStatus status, final String methodStr) {
        Log.i(TAG, methodStr);
        if (status.code != SupplicantStatusCode.SUCCESS) {
            Log.e(TAG, methodStr + " failed: "
                    + WifiSupplicantHal.supplicantStatusCodeToString(status.code) + ", "
                    + status.debugMessage);
            return false;
        }
        return true;
    }
}
