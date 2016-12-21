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
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.util.Log;

import java.util.ArrayList;
/**
 * Native calls sending requests to the P2P Hals, and callbacks for receiving P2P events
 *
 * {@hide}
 */
public class WifiP2pNativeNew {
    private static final boolean DBG = true;
    private static final String TAG = "WifiP2pNativeNew";

    private static final String SUPPLICANT_HIDL_SERVICE_NAME = "wpa_supplicant";
    // Supplicant HAL HIDL interface objects
    private ISupplicant mHidlSupplicant = null;
    private ISupplicantIface mHidlSupplicantIface = null;
    private ISupplicantP2pIface mHidlSupplicantP2pIface = null;
    private Boolean mSupplicantReady = null;
    private Object mSupplicantReadySync = new Object();

    /**
     * Register for service notification & bring up Supplicant HIDL interfaces when service is
     * ready.
     */
    public boolean registerForServiceNotification() {
        Log.i(TAG, "Registering SupplicantHidl service ready callback.");
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
        // TODO(b/33639391): update this code to use appropriate api call.
        if (!serviceManager.registerForNotifications(ISupplicant.kInterfaceName,
                "", serviceNotificationCb)) {
            Log.e(TAG, "Failed to register for notifcations to " + ISupplicant.kInterfaceName
                    + ", " + SUPPLICANT_HIDL_SERVICE_NAME);
            return false;
        }
        return true;
    }


    /**
     * Bring up the HIDL Vendor HAL and configure for P2P mode
     */
    public boolean startHidlHal() {
        /** TBC **/
        return true;
    }


    /**
     * Suspends calling thread until supplicant is ready.
     *
     * @return true, if P2P supplicant was successfully initialized.
     */
    public boolean waitSupplicantReady() {
        synchronized (mSupplicantReadySync) {
            while (mSupplicantReady == null) {
                try {
                    mSupplicantReadySync.wait();
                } catch (InterruptedException e) {
                }
            }
            return mSupplicantReady;
        }
    }


    /**
     * Bring up the Supplicant Hal
     */
    public boolean startSupplicantHidl() {
        class SupplicantException extends Exception {
            public SupplicantException(String message) {
                super(message);
            }
        }

        boolean result = false;

        try {
            Log.i(TAG, "Bringing up P2P supplicant HIDL");
            mHidlSupplicantP2pIface = null;
            mHidlSupplicant = ISupplicant.getService(SUPPLICANT_HIDL_SERVICE_NAME);
            if (mHidlSupplicant == null) {
                throw new SupplicantException("Failed to get wpa_supplicant HIDL service.");
            }

            /** get interfaces controlled by Supplicant*/
            /** List available Wifi chips */
            final ArrayList<ISupplicant.IfaceInfo> supplicantIfaces = new ArrayList<>();
            // Use lambda's here.
            ISupplicant.listInterfacesCallback listIfacesCb = new ISupplicant.listInterfacesCallback() {
                public void onValues(SupplicantStatus status,
                        java.util.ArrayList<ISupplicant.IfaceInfo> ifaces) {
                    if (status.code != SupplicantStatusCode.SUCCESS) {
                        Log.e(TAG, "Getting Supplicant Interfaces failed: " + status.code);
                        return;
                    }
                    supplicantIfaces.addAll(ifaces);
                }
            };
            mHidlSupplicant.listInterfaces(listIfacesCb);
            if (supplicantIfaces.size() == 0) {
                throw new SupplicantException("Got zero HIDL supplicant ifaces.");
            }

            /**
            * Get Supplicant HIDL iface
            */
            ISupplicant.getInterfaceCallback getIfaceCb = new ISupplicant.getInterfaceCallback() {
                public void onValues(SupplicantStatus status, ISupplicantIface iface) {
                    if (status.code != SupplicantStatusCode.SUCCESS) {
                        Log.e(TAG, "Failed to get Supplicant P2P Interface " + status.code);
                        return;
                    }
                    mHidlSupplicantIface = iface;
                }
            };
            boolean hasP2pIface = false;
            for (ISupplicant.IfaceInfo ifaceInfo : supplicantIfaces) {
                if (ifaceInfo.type == IfaceType.P2P) {
                    hasP2pIface = true;
                    mHidlSupplicant.getInterface(ifaceInfo, getIfaceCb);
                    break;
                }
            }
            if (!hasP2pIface) {
                throw new SupplicantException("Supplicant HIDL has no P2P interface, got " +
                        supplicantIfaces.size() + " ifaces.");
            }
            if (mHidlSupplicantIface == null) {
                throw new SupplicantException("Supplicant P2P Interface is null");
            }

            mHidlSupplicantP2pIface =
                    ISupplicantP2pIface.asInterface(mHidlSupplicantIface.asBinder());
            Log.i(TAG, "Got supplicant P2P HIDL Iface");
            result = true;
        } catch (SupplicantException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            synchronized (mSupplicantReadySync) {
                mSupplicantReady = result;
                mSupplicantReadySync.notifyAll();
            }
        }

        return result;
    }


    protected void logd(String s) {
        if (DBG) Log.d(TAG, s);
    }


    protected void logCompletion(String operation, SupplicantStatus status) {
        if (status.code == SupplicantStatusCode.SUCCESS) {
            logd(operation + " completed successfully.");
        } else {
            Log.w(TAG, operation + " failed: " + status.code + " (" + status.debugMessage + ")");
        }
    }


    private String addressToString(byte[] address) {
        if (address == null) {
            Log.e(TAG, "Cannot combine mac address from null data.");
            return null;
        }

        if (address.length != 6) {
            Log.e(TAG, "Invalid length of mac address: " + address.length);
            return null;
        }

        // MAC address length: 6 two-digit segments separated by 5 colons, terminated with \0.
        StringBuilder result = new StringBuilder(6 * 2 + 6);

        for (int i = 0; i < address.length; ++i) {
            if (result.length() > 0) {
                result.append(":");
            }
            result.append(String.format("%02x", address[i]));
        }
        return result.toString();
    }


    private byte[] stringToAddress(String address) {
        if (address == null) {
            Log.e(TAG, "Cannot parse null mac address.");
            return null;
        }

        String[] parts = address.split(":");
        byte[] result = new byte[6];
        for (int i = 0; i < 6; ++i) {
            Integer hex = Integer.parseInt(parts[i], 16);
            result[i] = hex.byteValue();
        }
        return result;
    }


    /**
     * Initiate a P2P service discovery with an optional timeout.
     *
     * @param timeoutInSec Max time to be spent is peforming discovery.
     *        Set to 0 to indefinely continue discovery untill and explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether operation was successful.
     */
    public boolean find(int timeout) {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering find(" + timeout + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.find(timeout);
        logCompletion("find()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Stop an ongoing P2P service discovery.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean stopFind() {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering stopFind()");
        SupplicantStatus status = mHidlSupplicantP2pIface.stopFind();
        logCompletion("stopFind()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Flush P2P peer table and state.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean flush() {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering flush()");
        SupplicantStatus status = mHidlSupplicantP2pIface.flush();
        logCompletion("flush()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Cancel an ongoing P2P group formation and joining-a-group related
     * operation. This operation unauthorizes the specific peer device (if any
     * had been authorized to start group formation), stops P2P find (if in
     * progress), stops pending operations for join-a-group, and removes the
     * P2P group interface (if one was used) that is in the WPS provisioning
     * step. If the WPS provisioning step has been completed, the group is not
     * terminated.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean cancelConnect() {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering cancelConnect()");
        SupplicantStatus status = mHidlSupplicantP2pIface.cancelConnect();
        logCompletion("cancelConnect()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Gets the MAC address of the device.
     *
     * @return MAC address of the device.
     */
    public String getDeviceAddress() {
        if (mHidlSupplicantP2pIface == null) {
            Log.e(TAG, "No Supplicant P2P IFace.");
            return null;
        }

        class GetDevAddrCallback implements ISupplicantP2pIface.getDeviceAddressCallback {
            private byte[] mAddress = null;

            public void onValues(SupplicantStatus status, byte[] address) {
                logCompletion("getDeviceAddress()", status);
                if (status.code == SupplicantStatusCode.SUCCESS) {
                    mAddress = address;
                }
            }

            public byte[] getAddress() {
                return mAddress;
            }
        };

        GetDevAddrCallback callback = new GetDevAddrCallback();

        logd("entering getDeviceAddress()");
        mHidlSupplicantP2pIface.getDeviceAddress(callback);
        logd("leaving getDeviceAddress()");
        return addressToString(callback.getAddress());
    }


    /**
     * Gets the operational SSID of the device.
     *
     * @param peerAddress MAC address of the peer.
     *
     * @return SSID of the device.
     */
    public ArrayList<Byte> getSsid(String address) {
        if (mHidlSupplicantP2pIface == null) return null;

        byte[] macAddress = stringToAddress(address);
        class SsidCallback implements ISupplicantP2pIface.getSsidCallback {
            private ArrayList<Byte> mSsid = null;

            public void onValues(SupplicantStatus status, ArrayList<Byte> ssid) {
                logCompletion("getSsid()", status);
                if (status.code == SupplicantStatusCode.SUCCESS) {
                    mSsid = ssid;
                }
            }

            public ArrayList<Byte> getSsid() {
                return mSsid;
            }
        };

        SsidCallback callback = new SsidCallback();

        logd("entering getSsid(" + address + ")");
        mHidlSupplicantP2pIface.getSsid(macAddress, callback);
        logd("leaving getSsid()");
        return callback.getSsid();
    }
}
