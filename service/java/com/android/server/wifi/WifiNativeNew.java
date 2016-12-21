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

import libcore.util.HexEncoding;
/**
 * Native calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 *
 * waitForEvent() is called on the monitor thread for events. All other methods
 * must be serialized from the framework.
 *
 * {@hide}
 */
public class WifiNativeNew {
    /** Invalid Supplicant Iface type */
    public static final int INVALID_IFACE_TYPE = -1;

    private static final boolean DBG = false;
    private static final String TAG = "WifiNativeNew";
    private static final String SUPPLICANT_HIDL_SERVICE_NAME = "wpa_supplicant";

    // Supplicant HAL HIDL interface objects
    private ISupplicant mHidlSupplicant;
    private ISupplicantIface mHidlSupplicantIface;
    private ISupplicantStaIface mHidlSupplicantStaIface;

    private final WifiMonitorNew mWifiMonitorNew;

    private final Object mLock = new Object();

    WifiNativeNew(WifiMonitorNew wifiMonitorNew) {
        mWifiMonitorNew = wifiMonitorNew;
    }

    /**
     * Register for service notification & bring up Supplicant HIDL interfaces when service is ready
     */
    public boolean registerForSupplicantHidlServiceNotification() {
        Log.i(TAG, "Registering SupplicantHidl service ready callback.");
        synchronized (mLock) {
            final IServiceManager serviceManager = IServiceManager.getService("manager");
            if (serviceManager == null) {
                Log.e(TAG, "Failed to get HIDL Service Manager");
                return false;
            }
            IServiceNotification serviceNotificationCb = new IServiceNotification.Stub() {
                public void onRegistration(String fqName, String name, boolean preexisting) {
                    Log.i(TAG, "Registered " + fqName + ", " + name + " preexisting=" + preexisting);
                    startSupplicantHidl();
                }
            };
            /* TODO(b/33639391) : Use the new ISupplicant.registerForNotifications() once it exists */
            if (!serviceManager.registerForNotifications(ISupplicant.kInterfaceName,
                    "", serviceNotificationCb)) {
                Log.e(TAG, "Failed to register for notifications to " + ISupplicant.kInterfaceName
                        + ", " + SUPPLICANT_HIDL_SERVICE_NAME);
                return false;
            }
            return true;
        }
    }

    /**
     * Bring up the HIDL Vendor HAL and configure for STA mode
     */
    public boolean startHidlHal() {
        /** TBC **/
        return true;
    }

    /**
     * Bring up the Supplicant Hal
     */
    public boolean startSupplicantHidl() {
        Log.i(TAG, "Bringing up supplicant HIDL");
        synchronized (mLock) {
            mHidlSupplicantStaIface = null;
            mHidlSupplicant = ISupplicant.getService(SUPPLICANT_HIDL_SERVICE_NAME);
            if (mHidlSupplicant == null) {
                Log.e(TAG, "Failed to get wpa_supplicant HIDL service.");
                return false;
            }

            /** List all supplicant Ifaces */
            final ArrayList<ISupplicant.IfaceInfo> supplicantIfaces = new ArrayList<>();
            mHidlSupplicant.listInterfaces((SupplicantStatus status,
                    ArrayList<ISupplicant.IfaceInfo> ifaces) -> {
                if (status.code != SupplicantStatusCode.SUCCESS) {
                        Log.e(TAG, "Getting Supplicant Interfaces failed: " + status.code);
                    return;
                }
                supplicantIfaces.addAll(ifaces);
            });
            if (supplicantIfaces.size() == 0) {
                Log.e(TAG, "Got zero HIDL supplicant ifaces.");
                return false;
            }

            /** Get the STA iface */
            boolean hasStaIface = false;
            for (ISupplicant.IfaceInfo ifaceInfo : supplicantIfaces) {
                if (ifaceInfo.type == IfaceType.STA) {
                    hasStaIface = true;
                    mHidlSupplicant.getInterface(ifaceInfo,
                            (SupplicantStatus status, ISupplicantIface iface) -> {
                        if (status.code != SupplicantStatusCode.SUCCESS) {
                            Log.e(TAG, "Failed to get Supplicant STA Interface " + status.code);
                            return;
                        }
                        mHidlSupplicantIface = iface;
                    });
                    break;
                }
            }
            if (!hasStaIface) {
                Log.e(TAG, "Supplicant HIDL has no STA interface, got " + supplicantIfaces.size()
                        + " ifaces.");
            }
            if (mHidlSupplicantIface == null) {
                Log.e(TAG, "Supplicant STA Interface is null");
                return false;
            }

            /** Cast ISupplicantIface into ISupplicantStaIface*/
            mHidlSupplicantStaIface =
                ISupplicantStaIface.asInterface(mHidlSupplicantIface.asBinder());
            Log.i(TAG, "Got HIDL SupplicantStaIface");

            /** Register SupplicantStaIfaceCallback handler*/
            SupplicantStatus status = mHidlSupplicantStaIface.registerCallback(
                    mWifiMonitorNew.getSupplicantStaIfaceCallback());
            if (status.code != SupplicantStatusCode.SUCCESS) {
                Log.e(TAG, "ISupplicantStaIface.registerCallback failed: " + status.debugMessage);
                return false;
            }
            Log.i(TAG, "Registered WifiMonitorNew as handler for SupplicantStaIfaceCallback");
            return true;
        }
    }

    /** ISupplicantStaIface super calls */
    /**
     * @return returns the name of Iface or null if the call fails
     */
    private String getName() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            final String methodStr = "getName";
            final StringBuilder builder = new StringBuilder();
            mHidlSupplicantStaIface.getName((SupplicantStatus status, String name) -> {
                statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                if(!statusSuccess.value) {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                } else {
                    builder.append(name);
                }});
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
    private int getType() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<Integer> gotType = new Mutable<>();
            final String methodStr = "getType";
            mHidlSupplicantStaIface.getType((SupplicantStatus status, int type) -> {
                statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                if(!statusSuccess.value) {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                } else {
                    gotType.value = type;
                }});
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
    private ISupplicantNetwork addNetwork() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<ISupplicantNetwork> newNetwork = new Mutable<>();
            final String methodStr = "addNetwork";
            mHidlSupplicantStaIface.addNetwork((SupplicantStatus status,
                    ISupplicantNetwork network) -> {
                statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                if(!statusSuccess.value) {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                } else {
                    newNetwork.value = network;
                }});
            if (statusSuccess.value) {
                return newNetwork.value;
            } else {
                return null;
            }
        }
    }
    private boolean removeNetwork(int id) {
        synchronized (mLock) {
            final String methodStr = "removeNetwork";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.removeNetwork(id);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    /**
     * @return The ISupplicantNetwork object for the given SupplicantNetworkId int, returns null if
     * the call fails
     */
    private ISupplicantNetwork getNetwork(int id) {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<ISupplicantNetwork> gotNetwork = new Mutable<>();
            final String methodStr = "getNetwork";
            mHidlSupplicantStaIface.getNetwork(id, (SupplicantStatus status,
                    ISupplicantNetwork network) -> {
                statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                if(!statusSuccess.value) {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                } else {
                    gotNetwork.value = network;
                }});
            if (statusSuccess.value) {
                return gotNetwork.value;
            } else {
                return null;
            }
        }
    }
    /**
     * @return a list of SupplicantNetworkID ints for all networks controlled by supplicant, returns
     * null if the call fails
     */
    private java.util.ArrayList<Integer> listNetworks() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            Mutable<ArrayList<Integer>> networkIdList = new Mutable<>();
            final String methodStr = "listNetworks";
            mHidlSupplicantStaIface.listNetworks((SupplicantStatus status,
                    java.util.ArrayList<Integer> networkIds) -> {
                statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                if(!statusSuccess.value) {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                } else {
                    networkIdList.value = networkIds;
                }});
            if (statusSuccess.value) {
                return networkIdList.value;
            } else {
                return null;
            }
        }
    }

    /** ISupplicantStaIface callers */
    private boolean reassociate() {
        synchronized (mLock) {
            final String methodStr = "reassociate";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.reassociate();
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean reconnect() {
        synchronized (mLock) {
            final String methodStr = "reconnect";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.reconnect();
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean disconnect() {
        synchronized (mLock) {
            final String methodStr = "disconnect";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.disconnect();
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean setPowerSave(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setPowerSave";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.setPowerSave(enable);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean initiateTdlsDiscover(byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsDiscover";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.initiateTdlsDiscover(macAddress);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean initiateTdlsSetup(byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsSetup";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.initiateTdlsSetup(macAddress);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean initiateTdlsTeardown(byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsTeardown";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.initiateTdlsTeardown(macAddress);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean initiateAnqpQuery(byte[/* 6 */] macAddress,
            java.util.ArrayList<Short> infoElements, java.util.ArrayList<Integer> subTypes) {
        synchronized (mLock) {
            final String methodStr = "initiateAnqpQuery";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.initiateAnqpQuery(macAddress,
                    infoElements, subTypes);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean initiateHs20IconQuery(byte[/* 6 */] macAddress, String fileName) {
        synchronized (mLock) {
            final String methodStr = "initiateHs20IconQuery";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.initiateHs20IconQuery(macAddress,
                    fileName);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    /**
     * Makes a callback to HIDL to getMacAddress from supplicant
     * @return string containing the MAC address, or null on a failed call
     */
    private String getMacAddress() {
        synchronized (mLock) {
            MutableBoolean statusSuccess = new MutableBoolean(false);
            final String methodStr = "getMacAddress";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return null;
            Mutable<String> gotMac = new Mutable<>();
            mHidlSupplicantStaIface.getMacAddress((SupplicantStatus status, byte[/* 6 */] macAddr) -> {
                statusSuccess.value = status.code == SupplicantStatusCode.SUCCESS;
                if (!statusSuccess.value) {
                    Log.e(TAG, methodStr + " failed: " + status.debugMessage);
                } else {
                    gotMac.value = String.valueOf(HexEncoding.encode(macAddr));
                }
            });
            if (statusSuccess.value) {
                return gotMac.value;
            } else {
                return null;
            }
        }
    }
    private boolean startRxFilter() {
        synchronized (mLock) {
            final String methodStr = "startRxFilter";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.startRxFilter();
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean stopRxFilter() {
        synchronized (mLock) {
            final String methodStr = "stopRxFilter";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.stopRxFilter();
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean addRxFilter(byte type) {
        synchronized (mLock) {
            final String methodStr = "addRxFilter";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.addRxFilter(type);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean removeRxFilter(byte type) {
        synchronized (mLock) {
            final String methodStr = "removeRxFilter";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.removeRxFilter(type);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean setBtCoexistenceMode(byte mode) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceMode";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.setBtCoexistenceMode(mode);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean setBtCoexistenceScanModeEnabled(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceScanModeEnabled";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.setBtCoexistenceScanModeEnabled(enable);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean setSuspendModeEnabled(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setSuspendModeEnabled";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.setSuspendModeEnabled(enable);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }
    private boolean setCountryCode(byte[/* 2 */] code) {
        synchronized (mLock) {
            final String methodStr = "setCountryCode";
            if (!checkSupplicantStaIfaceAndLogFailure(methodStr)) return false;
            SupplicantStatus status = mHidlSupplicantStaIface.setCountryCode(code);
            return checkStatusAndLogFailure(status, methodStr);
        }
    }

    /**
     * Returns false if SupplicantStaIface is null, and logs failure to call methodStr
     */
    private boolean checkSupplicantStaIfaceAndLogFailure(final String methodStr) {
        if (mHidlSupplicantStaIface == null) {
            Log.e(TAG, "Can't call " + methodStr + ", ISupplicantStaIface is null");
            return false;
        }
        return true;
    }

    /**
     * Returns true if provided status code is SUCCESS, logs debug message and returns false
     * otherwise
     */
    public static boolean checkStatusAndLogFailure(SupplicantStatus status, final String methodStr) {
        if (status.code != SupplicantStatusCode.SUCCESS) {
            Log.e(TAG, methodStr + " failed: " + status.debugMessage);
            return false;
        }
        return true;
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
