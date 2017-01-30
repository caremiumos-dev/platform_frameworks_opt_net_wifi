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

import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
/**
 * Hal calls for bring up/shut down of the supplicant daemon and for
 * sending requests to the supplicant daemon
 *
 * {@hide}
 */
public class WifiSupplicantHal {
    private static final boolean DBG = false;
    private static final String TAG = "WifiSupplicantHal";
    // Supplicant HAL HIDL interface objects
    private ISupplicant mISupplicantHal;
    private ISupplicantStaIface mISupplicantStaIfaceHal;
    private ISupplicantP2pIface mISupplicantP2pIfaceHal;
    // Wrapper/caller of the mISupplicantStaIfaceHal
    private SupplicantStaIfaceHal mSupplicantStaIfaceHal;
    private final Object mLock;
    private final HalDeviceManager mHalDeviceManager;
    private final HalDeviceManagerStatusListener mHalDeviceManagerStatusCallbacks;
    private final ServiceDiedListener mServiceDiedListener;
    private final HandlerThread mWifiStateMachineHandlerThread;

    @VisibleForTesting
    public boolean initializationComplete;
    public WifiSupplicantHal(HalDeviceManager halDeviceManager,
                         HandlerThread wifiStateMachineHandlerThread,
                         SupplicantStaIfaceHal supplicantStaIfaceHal, Object supplicantHalLock) {
        mHalDeviceManager = halDeviceManager;
        // This object is going to be used by both WifiService & WifiP2pService, so we may
        // need to use different loopers here.
        mWifiStateMachineHandlerThread = wifiStateMachineHandlerThread;
        mHalDeviceManagerStatusCallbacks = new HalDeviceManagerStatusListener();
        mServiceDiedListener = new ServiceDiedListener();
        mLock = supplicantHalLock;
        mSupplicantStaIfaceHal = supplicantStaIfaceHal;
        initializationComplete = false;
    }

    /**
     * Registers a service notification for the ISupplicant service, which gets the service,
     * ISupplicantStaIface and ISupplicantP2pIface.
     * @return true if the service notification was successfully registered
     */
    public boolean initialize() {
        Log.i(TAG, "Registering SupplicantHidl service ready callback.");
        synchronized (mLock) {
            mISupplicantHal = null;
            mISupplicantStaIfaceHal = null;
            mISupplicantP2pIfaceHal = null;
            try {
                final IServiceManager serviceManager = getServiceManagerMockable();
                if (serviceManager == null) {
                    Log.e(TAG, "Failed to get HIDL Service Manager");
                    return false;
                }
                if (!serviceManager.linkToDeath(cookie -> {
                    Log.wtf(TAG, "IServiceManager died: cookie=" + cookie);
                    synchronized (mLock) {
                        supplicantServiceDiedHandler();
                    }
                }, 0)) {
                    Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                    supplicantServiceDiedHandler();
                    return false;
                }
                IServiceNotification serviceNotificationCb = new IServiceNotification.Stub() {
                    public void onRegistration(String fqName, String name, boolean preexisting) {
                        Log.i(TAG, "IServiceNotification.onRegistration for: " + fqName + ", "
                                + name + " preexisting=" + preexisting);
                        if (!getSupplicantService() || !getSupplicantStaIface()
                                || !getSupplicantP2pIface()) {
                            Log.e(TAG, "initalizing ISupplicantIfaces failed.");
                            supplicantServiceDiedHandler();
                        } else {
                            Log.i(TAG, "Completed initialization of ISupplicant interfaces.");
                            initializationComplete = true;
                        }
                    }
                };
                /* TODO(b/33639391) : Use the new ISupplicant.registerForNotifications() once it
                   exists */
                if (!serviceManager.registerForNotifications(ISupplicant.kInterfaceName,
                        "", serviceNotificationCb)) {
                    Log.e(TAG, "Failed to register for notifications to "
                            + ISupplicant.kInterfaceName);
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while trying to register a listener for ISupplicant service: "
                        + e);
            }

            mHalDeviceManager.initialize();
            mHalDeviceManager.registerStatusListener(
                    mHalDeviceManagerStatusCallbacks, mWifiStateMachineHandlerThread.getLooper());
            return true;
        }
    }

    private boolean getSupplicantService() {
        synchronized (mLock) {
            try {
                mISupplicantHal = getSupplicantMockable();
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.getService exception: " + e);
                return false;
            }
            if (mISupplicantHal == null) {
                Log.e(TAG, "Got null ISupplicant service. Stopping supplicant HIDL startup");
                return false;
            }
        }
        return true;
    }

    /**
     * @return ISupplicantIface of the requested type
     */
    private ISupplicantIface getSupplicantIface(int ifaceType) {
        synchronized (mLock) {
            /** List all supplicant Ifaces */
            final ArrayList<ISupplicant.IfaceInfo> supplicantIfaces = new ArrayList<>();
            try {
                mISupplicantHal.listInterfaces((SupplicantStatus status,
                        ArrayList<ISupplicant.IfaceInfo> ifaces) -> {
                    if (status.code != SupplicantStatusCode.SUCCESS) {
                        Log.e(TAG, "Getting Supplicant Interfaces failed: " + status.code);
                        return;
                    }
                    supplicantIfaces.addAll(ifaces);
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.listInterfaces exception: " + e);
                return null;
            }
            if (supplicantIfaces.size() == 0) {
                Log.e(TAG, "Got zero HIDL supplicant ifaces. Stopping supplicant HIDL startup.");
                return null;
            }
            /** Get the STA iface */
            boolean hasStaIface = false;
            Mutable<ISupplicantIface> supplicantIface = new Mutable<>();
            for (ISupplicant.IfaceInfo ifaceInfo : supplicantIfaces) {
                if (ifaceInfo.type == ifaceType) {
                    hasStaIface = true;
                    try {
                        mISupplicantHal.getInterface(ifaceInfo,
                                (SupplicantStatus status, ISupplicantIface iface) -> {
                                if (status.code != SupplicantStatusCode.SUCCESS) {
                                    Log.e(TAG, "Failed to get ISupplicantIface " + status.code);
                                    return;
                                }
                                supplicantIface.value = iface;
                            });
                    } catch (RemoteException e) {
                        Log.e(TAG, "ISupplicant.getInterface exception: " + e);
                        return null;
                    }
                    break;
                }
            }
            if (!hasStaIface) {
                Log.e(TAG, "No ISupplicantIface matching requested type: " + ifaceType + ", got "
                        + supplicantIfaces.size() + " ifaces.");
            }
            return supplicantIface.value;
        }
    }

    private boolean getSupplicantStaIface() {
        synchronized (mLock) {
            /** Cast ISupplicantIface into ISupplicantStaIface*/
            ISupplicantIface supplicantIface = getSupplicantIface(IfaceType.STA);
            if (supplicantIface == null) {
                Log.e(TAG, "getSupplicantStaIface failed");
                return false;
            }
            mISupplicantStaIfaceHal = getStaIfaceMockable(supplicantIface);
            mSupplicantStaIfaceHal.initialize(mISupplicantStaIfaceHal, mServiceDiedListener);
            return true;
        }
    }

    private boolean getSupplicantP2pIface() {
        synchronized (mLock) {
            /** Cast ISupplicantIface into ISupplicantStaIface*/
            ISupplicantIface supplicantIface = getSupplicantIface(IfaceType.P2P);
            if (supplicantIface == null) {
                Log.e(TAG, "getSupplicantP2pIface failed");
                return false;
            }
            mISupplicantP2pIfaceHal = getP2pIfaceMockable(supplicantIface);
            return true;
        }
    }

    private void resetHandles() {
        synchronized (mLock) {
            mISupplicantHal = null;
            mISupplicantStaIfaceHal = null;
            mISupplicantP2pIfaceHal = null;
            mSupplicantStaIfaceHal.reset();
            initializationComplete = false;
        }
    }

    void supplicantServiceDiedHandler() {
        resetHandles();
    }

    /**
     * Hal Device Manager callbacks.
     */
    public class HalDeviceManagerStatusListener implements HalDeviceManager.ManagerStatusListener {
        @Override
        public void onStatusChanged() {
            Log.i(TAG, "Device Manager onStatusChanged. isReady(): " + mHalDeviceManager.isReady()
                    + "isStarted(): " + mHalDeviceManager.isStarted());
            // Reset all our cached handles.
            if (!mHalDeviceManager.isReady() || !mHalDeviceManager.isStarted())  {
                resetHandles();
            }
        }
    }

    /**
     * Callback to register HAL service failure from other supplicant Hal callers
     */
    public class ServiceDiedListener {
        /**
         * Indicates a service failure, which we treat as death
         */
        public void onServiceDied() {
            Log.i(TAG, "onServiceDied");
            supplicantServiceDiedHandler();
        }
    }

    /**
     * Wrapper functions to access HIDL services, created to be mockable in unit tests
     */
    protected IServiceManager getServiceManagerMockable() throws RemoteException {
        return IServiceManager.getService("manager");
    }

    protected ISupplicant getSupplicantMockable() throws RemoteException {
        return ISupplicant.getService();
    }

    protected ISupplicantStaIface getStaIfaceMockable(ISupplicantIface iface) {
        return ISupplicantStaIface.asInterface(iface.asBinder());
    }

    protected ISupplicantP2pIface getP2pIfaceMockable(ISupplicantIface iface) {
        return ISupplicantP2pIface.asInterface(iface.asBinder());
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
