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
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;

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
    // Supplicant HAL interface objects
    private ISupplicant mISupplicantHal;
    private ISupplicantStaIface mISupplicantStaIfaceHal;
    private final Object mLock = new Object();
    private final HalDeviceManager mHalDeviceManager;
    private final HalDeviceManagerStatusListener mHalDeviceManagerStatusCallbacks;
    private final HandlerThread mHandlerThread;
    private boolean mInitializationComplete;
    public WifiSupplicantHal(HalDeviceManager halDeviceManager, HandlerThread handlerThread) {
        mHalDeviceManager = halDeviceManager;
        // This object is going to be used by both WifiService, so we may
        // need to use different loopers here.
        mHandlerThread = handlerThread;
        mHalDeviceManagerStatusCallbacks = new HalDeviceManagerStatusListener();
        mInitializationComplete = false;
    }

    /**
     * Registers a service notification for the ISupplicant service, which triggers intialization of
     * the ISupplicantStaIface
     * @return true if the service notification was successfully registered
     */
    public boolean initialize() {
        Log.i(TAG, "Registering SupplicantHidl service ready callback.");
        synchronized (mLock) {
            mISupplicantHal = null;
            mISupplicantStaIfaceHal = null;
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
                        if (!initSupplicantService() || !initSupplicantStaIface()) {
                            Log.e(TAG, "initalizing ISupplicantIfaces failed.");
                            supplicantServiceDiedHandler();
                        } else {
                            Log.i(TAG, "Completed initialization of ISupplicant interfaces.");
                            mInitializationComplete = true;
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
                supplicantServiceDiedHandler();
            }

            mHalDeviceManager.initialize();
            mHalDeviceManager.registerStatusListener(
                    mHalDeviceManagerStatusCallbacks, mHandlerThread.getLooper());
            return true;
        }
    }

    private boolean initSupplicantService() {
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
    private ISupplicantIface getSupplicantStaIface(int ifaceType) {
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
            boolean hasIface = false;
            Mutable<ISupplicantIface> supplicantIface = new Mutable<>();
            for (ISupplicant.IfaceInfo ifaceInfo : supplicantIfaces) {
                if (ifaceInfo.type == ifaceType) {
                    hasIface = true;
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
            if (!hasIface) {
                Log.e(TAG, "No ISupplicantIface matching requested type: " + ifaceType + ", got "
                        + supplicantIfaces.size() + " ifaces.");
            }
            return supplicantIface.value;
        }
    }

    private boolean initSupplicantStaIface() {
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
                return false;
            }
            if (supplicantIfaces.size() == 0) {
                Log.e(TAG, "Got zero HIDL supplicant ifaces. Stopping supplicant HIDL startup.");
                return false;
            }
            boolean hasIface = false;
            Mutable<ISupplicantIface> supplicantIface = new Mutable<>();
            for (ISupplicant.IfaceInfo ifaceInfo : supplicantIfaces) {
                if (ifaceInfo.type == IfaceType.STA) {
                    hasIface = true;
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
                        return false;
                    }
                    break;
                }
            }
            if (!hasIface) {
                Log.e(TAG, "No ISupplicantIface matching IfaceType.STA: " + ", got "
                        + supplicantIfaces.size() + " ifaces.");
            }
            if (supplicantIface.value == null) {
                Log.e(TAG, "initSupplicantStaIface got null iface");
                return false;
            }
            mISupplicantStaIfaceHal = getStaIfaceMockable(supplicantIface.value);
            return true;
        }
    }

    private void supplicantServiceDiedHandler() {
        synchronized (mLock) {
            mISupplicantHal = null;
            mISupplicantStaIfaceHal = null;
            mInitializationComplete = false;
        }
    }

    /**
     * Signals whether Initialization completed successfully. Only necessary for testing, is not
     * needed to guard calls etc.
     */
    public boolean isInitializationComplete() {
        return mInitializationComplete;
    }

    /**
     * Hal Device Manager callbacks.
     */
    public class HalDeviceManagerStatusListener implements HalDeviceManager.ManagerStatusListener {
        @Override
        public void onStatusChanged() {
            boolean isReady = mHalDeviceManager.isReady();
            boolean isStarted = mHalDeviceManager.isStarted();
            if (DBG) {
                Log.i(TAG, "Device Manager onStatusChanged. isReady(): " + isReady
                        + "isStarted(): " + isStarted);
            }
            // Reset all our cached handles.
            if (!isReady || !isStarted)  {
                supplicantServiceDiedHandler();
            }
        }
    }

    /**
     * Wrapper functions to access static HAL methods, created to be mockable in unit tests
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
