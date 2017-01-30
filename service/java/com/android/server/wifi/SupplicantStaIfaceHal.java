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

import android.hardware.wifi.supplicant.V1_0.ISupplicantNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.os.RemoteException;
import android.util.Log;
import android.util.MutableBoolean;

import libcore.util.HexEncoding;

import java.util.ArrayList;
/**
 *
 * {@hide}
 */
public class SupplicantStaIfaceHal {
    /** Invalid Supplicant Iface type */
    public static final int INVALID_IFACE_TYPE = -1;
    private static final String TAG = "SupplicantStaIfaceHal";
    private static final boolean DBG = true;
    // Supplicant HAL HIDL objects
    private ISupplicantStaIface mSupplicantStaIfaceHal;

    private final WifiSupplicantHal mWifiSupplicantHal;
    private final Object mLock = new Object();

    ISupplicantStaIface mSupplicantStaIface;
    SupplicantStaIfaceHal(WifiSupplicantHal wifiSupplicantHal,
            ISupplicantStaIface iSupplicantStaIface) {
        mWifiSupplicantHal = wifiSupplicantHal;
        mSupplicantStaIface = iSupplicantStaIface;
    }

    /**
     * @return returns the name of Iface or null if the call fails
     */
    public String getName() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            final String methodStr = "getName";
            if (DBG) Log.i(TAG, methodStr);
            final StringBuilder builder = new StringBuilder();
            try {
                mSupplicantStaIfaceHal.getName((SupplicantStatus status, String name) -> {
                    statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                    if (!statusSuccess.value) {
                        Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                    } else {
                        builder.append(name);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception: " + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
            }
            if (statusSuccess.value) {
                return builder.toString();
            } else {
                return null;
            }
        }
    }
    /**
     * @return the IfaceType value for this interface, or INVALID_IFACE_TYPE if call fails
     */
    public int getType() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<Integer> gotType = new Mutable<>();
            final String methodStr = "getType";
            if (DBG) Log.i(TAG, methodStr);
            try {
                mSupplicantStaIfaceHal.getType((SupplicantStatus status, int type) -> {
                    statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                    if (!statusSuccess.value) {
                        Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                    } else {
                        gotType.value = type;
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception: " + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
            }
            if (statusSuccess.value) {
                return gotType.value;
            } else {
                return INVALID_IFACE_TYPE;
            }
        }
    }
    /**
     * @return The ISupplicantNetwork object for the new network, or null if the call fails
     */
    public SupplicantStaNetwork addNetwork() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<ISupplicantNetwork> newNetwork = new Mutable<>();
            final String methodStr = "addNetwork";
            if (DBG) Log.i(TAG, methodStr);
            try {
                mSupplicantStaIfaceHal.addNetwork((SupplicantStatus status,
                        ISupplicantNetwork network) -> {
                    statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                    if (!statusSuccess.value) {
                        Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                    } else {
                        newNetwork.value = network;
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception: " + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
            }
            if (statusSuccess.value) {
                return new SupplicantStaNetwork(ISupplicantStaNetwork.asInterface(
                        newNetwork.value.asBinder()));
            } else {
                return null;
            }
        }
    }
    /**
     * Remove network from supplicant with network Id
     * @return true on success of the command
     */
    public boolean removeNetwork(int id) {
        synchronized (mLock) {
            final String methodStr = "removeNetwork";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.removeNetwork(id);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /**
     * @return The ISupplicantNetwork object for the given SupplicantNetworkId int, returns null if
     * the call fails
     */
    public SupplicantStaNetwork getNetwork(int id) {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<ISupplicantNetwork> gotNetwork = new Mutable<>();
            final String methodStr = "getNetwork";
            if (DBG) Log.i(TAG, methodStr);
            try {
                mSupplicantStaIfaceHal.getNetwork(id, (SupplicantStatus status,
                        ISupplicantNetwork network) -> {
                    statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                    if (!statusSuccess.value) {
                        Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                    } else {
                        gotNetwork.value = network;
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception: " + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
            }
            if (statusSuccess.value) {
                return new SupplicantStaNetwork(ISupplicantStaNetwork.asInterface(
                        gotNetwork.value.asBinder()));
            } else {
                return null;
            }
        }
    }
        /**
     * @return a list of SupplicantNetworkID ints for all networks controlled by supplicant, returns
     * null if the call fails
     */
    public java.util.ArrayList<Integer> listNetworks() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<ArrayList<Integer>> networkIdList = new Mutable<>();
            final String methodStr = "listNetworks";
            if (DBG) Log.i(TAG, methodStr);
            try {
                mSupplicantStaIfaceHal.listNetworks((SupplicantStatus status,
                        java.util.ArrayList<Integer> networkIds) -> {
                    statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                    if (!statusSuccess.value) {
                        Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                    } else {
                        networkIdList.value = networkIds;
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception: " + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
            }
            if (statusSuccess.value) {
                return networkIdList.value;
            } else {
                return null;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean reassociate() {
        synchronized (mLock) {
            final String methodStr = "reassociate";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.reassociate();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean reconnect() {
        synchronized (mLock) {
            final String methodStr = "reconnect";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.reconnect();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean disconnect() {
        synchronized (mLock) {
            final String methodStr = "disconnect";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.disconnect();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean setPowerSave(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setPowerSave";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.setPowerSave(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean initiateTdlsDiscover(byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsDiscover";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.initiateTdlsDiscover(macAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean initiateTdlsSetup(byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsSetup";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.initiateTdlsSetup(macAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean initiateTdlsTeardown(byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsTeardown";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.initiateTdlsTeardown(macAddress);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean initiateAnqpQuery(byte[/* 6 */] macAddress,
            java.util.ArrayList<Short> infoElements, java.util.ArrayList<Integer> subTypes) {
        synchronized (mLock) {
            final String methodStr = "initiateAnqpQuery";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.initiateAnqpQuery(macAddress,
                        infoElements, subTypes);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean initiateHs20IconQuery(byte[/* 6 */] macAddress, String fileName) {
        synchronized (mLock) {
            final String methodStr = "initiateHs20IconQuery";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.initiateHs20IconQuery(macAddress,
                        fileName);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /**
     * Makes a callback to HIDL to getMacAddress from supplicant
     * @return string containing the MAC address, or null on a failed call
     */
    public String getMacAddress() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            final String methodStr = "getMacAddress";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return null;
            Mutable<String> gotMac = new Mutable<>();
            try {
                mSupplicantStaIfaceHal.getMacAddress((SupplicantStatus status,
                        byte[/* 6 */] macAddr) -> {
                    statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                    if (!statusSuccess.value) {
                        Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                    } else {
                        gotMac.value = String.valueOf(HexEncoding.encode(macAddr));
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception: " + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
            }
            if (statusSuccess.value) {
                return gotMac.value;
            } else {
                return null;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean startRxFilter() {
        synchronized (mLock) {
            final String methodStr = "startRxFilter";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.startRxFilter();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean stopRxFilter() {
        synchronized (mLock) {
            final String methodStr = "stopRxFilter";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.stopRxFilter();
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean addRxFilter(byte type) {
        synchronized (mLock) {
            final String methodStr = "addRxFilter";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.addRxFilter(type);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean removeRxFilter(byte type) {
        synchronized (mLock) {
            final String methodStr = "removeRxFilter";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.removeRxFilter(type);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean setBtCoexistenceMode(byte mode) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceMode";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.setBtCoexistenceMode(mode);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean setBtCoexistenceScanModeEnabled(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceScanModeEnabled";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status =
                        mSupplicantStaIfaceHal.setBtCoexistenceScanModeEnabled(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean setSuspendModeEnabled(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setSuspendModeEnabled";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.setSuspendModeEnabled(enable);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }
    /** See SupplicantStaIface.hal for documentation */
    public boolean setCountryCode(byte[/* 2 */] code) {
        synchronized (mLock) {
            final String methodStr = "setCountryCode";
            if (DBG) Log.i(TAG, methodStr);
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            try {
                SupplicantStatus status = mSupplicantStaIfaceHal.setCountryCode(code);
                return checkStatusAndLogFailure(status, methodStr);
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantStaIface." + methodStr + ": exception:" + e);
                mWifiSupplicantHal.supplicantServiceDiedHandler();
                return false;
            }
        }
    }

    /**
     * Returns false if SupplicantStaIface is null, and logs failure to call methodStr
     */
    public boolean checkSupplicantStaIfaceAndLogFailure(final String methodStr) {
        if (DBG) Log.i(TAG, methodStr);
        if (mSupplicantStaIfaceHal == null) {
            Log.e(TAG, "Can't call " + methodStr + ", ISupplicantStaIface is null");
            return false;
        }
        return true;
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    public static boolean checkStatusAndLogFailure(SupplicantStatus status,
            final String methodStr) {
        if (DBG) Log.i(TAG, methodStr);
        if (status.code != SupplicantStatusCode.SUCCESS) {
            Log.e(TAG, methodStr + " failed: " + supplicantStatusCodeToString(status.code) + ", "
                    + status.debugMessage);
            return false;
        }
        return true;
    }

    /**
     * Converts SupplicantStatus code values to strings for debug logging
     * TODO(b/34811152) Remove this, or make it more break resistance
     */
    public static String supplicantStatusCodeToString(int code) {
        switch (code) {
            case 0:
                return "SUCCESS";
            case 1:
                return "FAILURE_UNKNOWN";
            case 2:
                return "FAILURE_ARGS_INVALID";
            case 3:
                return "FAILURE_IFACE_INVALID";
            case 4:
                return "FAILURE_IFACE_UNKNOWN";
            case 5:
                return "FAILURE_IFACE_EXISTS";
            case 6:
                return "FAILURE_IFACE_DISABLED";
            case 7:
                return "FAILURE_IFACE_NOT_DISCONNECTED";
            case 8:
                return "FAILURE_NETWORK_INVALID";
            case 9:
                return "FAILURE_NETWORK_UNKNOWN";
            default:
                return "??? UNKNOWN_CODE";
        }
    }

    private static class Mutable<E> {
        public E value;

        Mutable() {
            value = null;
        }

        Mutable(E value) {
            this.value = value;
        }
    }
}
