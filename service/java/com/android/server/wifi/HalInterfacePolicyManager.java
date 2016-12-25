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

import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IWifiEventCallback;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IWifiP2pIface;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.RemoteException;
import android.util.Log;
import android.util.MutableBoolean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proxies chip & interface management for IWifi HAL. Provides
 * policy decisions as to which interfaces have priority over other interfaces.
 */
public class HalInterfacePolicyManager {
    private static final String TAG = "HalInterfacePolicyMgr";
    private static final boolean DBG = false;

    private static final String HAL_HIDL_WIFI_SERVICE_NAME = "wifi";
    private static final String HAL_HIDL_MANAGER_SERVICE_NAME = "manager";

    private static final int TYPE_STA = 0;
    private static final int TYPE_AP = 1;
    private static final int TYPE_P2P = 2;
    private static final int TYPE_NAN = 3;

    // to be used for synchronizing access to any class member
    private final Object mLock = new Object();

    private Set<ManagerStatusCallback> mManagerStatusCallbacks = new HashSet<>();
    private Map<String, IfaceCacheEntry> mCacheByName = new HashMap<>();
    private Map<Integer, List<IWifiIface>> mCacheByType = new HashMap<>();
    private IServiceManager mServiceManager = null;
    private IWifi mWifi = null;
    private boolean mIsReady = false;
    private WifiEventCallback mWifiEventCallback = new WifiEventCallback();

    public HalInterfacePolicyManager() {
        mCacheByType.put(TYPE_STA, new ArrayList<>());
        mCacheByType.put(TYPE_AP, new ArrayList<>());
        mCacheByType.put(TYPE_P2P, new ArrayList<>());
        mCacheByType.put(TYPE_NAN, new ArrayList<>());

        start();
    }

    public void registerStatusCallback(ManagerStatusCallback callback) {
        synchronized (mLock) {
            mManagerStatusCallbacks.add(callback);
            if (mIsReady) {
                callback.onReady();
            }
        }
    }

    public IWifiStaIface createStaIface(InterfaceStatusCallback statusCallback) {
        return (IWifiStaIface) createIface(TYPE_STA, statusCallback);
    }

    public ArrayList<String> getStaIfaceNames() {
        return getIfaceNames(TYPE_STA);
    }

    public IWifiStaIface getStaIface(String ifname) {
        return (IWifiStaIface) getIface(ifname);
    }

    public void removeStaIface(String ifname) {
        removeIface(TYPE_STA, ifname);
    }

    public IWifiApIface createApIface(InterfaceStatusCallback statusCallback) {
        return (IWifiApIface) createIface(TYPE_AP, statusCallback);
    }

    public ArrayList<String> getApIfaceNames() {
        return getIfaceNames(TYPE_AP);
    }

    public IWifiApIface getApIface(String ifname) {
        return (IWifiApIface) getIface(ifname);
    }

    public void removeApIface(String ifname) {
        removeIface(TYPE_AP, ifname);
    }

    public IWifiP2pIface createP2pIface(InterfaceStatusCallback statusCallback) {
        return (IWifiP2pIface) createIface(TYPE_P2P, statusCallback);
    }

    public ArrayList<String> getP2pIfaceNames() {
        return getIfaceNames(TYPE_P2P);
    }

    public IWifiP2pIface getP2pIface(String ifname) {
        return (IWifiP2pIface) getIface(ifname);
    }

    public void removeP2pIface(String ifname) {
        removeIface(TYPE_P2P, ifname);
    }

    public IWifiNanIface createNanIface(InterfaceStatusCallback statusCallback) {
        return (IWifiNanIface) createIface(TYPE_NAN, statusCallback);
    }

    public ArrayList<String> getNanIfaceNames() {
        return getIfaceNames(TYPE_NAN);
    }

    public IWifiNanIface getNanIface(String ifname) {
        return (IWifiNanIface) getIface(ifname);
    }

    public void removeNanIface(String ifname) {
        removeIface(TYPE_NAN, ifname);
    }

    IWifiRttController createRttController(IWifiIface boundIface) {
        IWifiChip chip = getChip(boundIface);
        try {
            Mutable<IWifiRttController> rttResp = new Mutable<>();
            chip.createRttController(boundIface, (WifiStatus status, IWifiRttController rtt) -> {
                if (status.code == WifiStatusCode.SUCCESS) {
                    rttResp.value = rtt;
                } else {
                    Log.e(TAG, "Error when calling createRttController: code=" + status.code
                            + " (" + status.description + ")");
                }
            });
            return rttResp.value;
        } catch (RemoteException e) {
            Log.e(TAG, "Exception when calling createRttController: " + e);
            return null;
        }
    }

    public IWifiChip getChip(IWifiIface iface) {
        synchronized (mLock) {
            Mutable<String> nameResp = new Mutable<>();
            try {
                iface.getName((WifiStatus status, String name) -> {
                    if (status.code == WifiStatusCode.SUCCESS) {
                        nameResp.value = name;
                    } else {
                        Log.e(TAG, "Error on getName: code=" + status.code + " ("
                                + status.description + ")");
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "Exception on getName: " + e);
                return null;
            }
            if (nameResp.value == null) {
                return null;
            }

            IfaceCacheEntry entry = mCacheByName.get(nameResp.value);
            if (entry == null) {
                Log.e(TAG, "No interface by that name cached -- " + nameResp.value);
                return null;
            }

            return entry.mIWifiChip;
        }
    }

    public interface ManagerStatusCallback {
        void onReady();
        void onDown();
    }

    public interface InterfaceStatusCallback {
        void onDestroyed();
        void onAvailableForRequest();
    }

    /**
     * Wrapper function to access the HIDL Wi-Fi service. Created to be mockable in unit-tests.
     */
    private IWifi getWifiServiceMockable() throws RemoteException {
        return IWifi.getService(HAL_HIDL_WIFI_SERVICE_NAME);
    }

    private IServiceManager getServiceManagerMockable() {
        return IServiceManager.getService(HAL_HIDL_MANAGER_SERVICE_NAME);
    }

    private void start() {
        synchronized (mLock) {
            if (mServiceManager == null) {
                mServiceManager = getServiceManagerMockable();
                if (mServiceManager == null) {
                    Log.wtf(TAG, "Failed to get IServiceManager instance");
                    return; // TODO: what else??
                } else {
                    try {
                        if (!mServiceManager.linkToDeath(cookie -> {
                            Log.e(TAG, "IServiceManager died!");
                            synchronized (mLock) {
                                mServiceManager = null;
                                stop();
                            }
                            start();
                        }, /* don't care */ 0)) {
                            Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                            mServiceManager = null;
                            return;  // TODO: what else??
                        }

                        if (!mServiceManager.registerForNotifications(IWifi.kInterfaceName, "",
                                new IServiceNotification.Stub() {
                                    @Override
                                    public void onRegistration(String fqName, String name,
                                            boolean preexisting) {
                                        if (DBG) Log.d(TAG, "IWifi registration notification");
                                        start();
                                    }
                                })) {
                            Log.wtf(TAG, "Failed to register a listener for IWifi service");
                            mServiceManager = null;
                            return;
                        }
                    } catch (RemoteException e) {
                        Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
                        mServiceManager = null;
                        return;  // TODO: what else??
                    }
                }
            }

            try {
                if (mWifi == null) {
                    mWifi = getWifiServiceMockable();
                    if (mWifi == null) {
                        Log.e(TAG,
                                "IWifi not (yet) available - but have a listener for it ...");
                        return;
                    }
                    if (!mWifi.linkToDeath(cookie -> {
                        Log.e(TAG, "IWifi HAL service died! Have a listener for it ...");
                        stop();
                    }, /* don't care */ 0)) {
                        Log.e(TAG, "Error on linkToDeath on IWifi");
                        mWifi = null; // TODO: what else?? I.e. what would re-trigger this?
                        return;
                    }

                    WifiStatus status = mWifi.registerEventCallback(mWifiEventCallback);
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG, "IWifi.registerEventCallback failed: code=" + status.code
                                + " (" + status.description + ")");
                        mWifi = null; // TODO: what else??
                        return;
                    }
                }

                if (!mWifi.isStarted()) {
                    WifiStatus status = mWifi.start();
                    if (status.code == WifiStatusCode.SUCCESS) {
                        mIsReady = true;
                    } else {
                        if (DBG) {
                            Log.d(TAG, "Cannot start IWifi: code=" + status.code + " ("
                                    + status.description + ")");
                        }
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while operating on IWifi: " + e);
                mWifi = null;
                return; // TODO: what else??
            }

            if (mIsReady) {
                for (ManagerStatusCallback cb : mManagerStatusCallbacks) {
                    cb.onReady();
                }
            }
        }
    }

    private void stop() {
        synchronized (mLock) {
            mIsReady = false;
            mWifi = null;

            for (Map.Entry<String, IfaceCacheEntry> entry : mCacheByName.entrySet()) {
                if (entry.getValue().mStatusCallback != null) {
                    entry.getValue().mStatusCallback.onDestroyed();
                }
            }

            mCacheByName.clear();
            for (Map.Entry<Integer, List<IWifiIface>> subList : mCacheByType.entrySet()) {
                subList.getValue().clear();
            }

            for (ManagerStatusCallback cb : mManagerStatusCallbacks) {
                cb.onDown();
            }
        }
    }

    private IWifiIface createIface(int type, InterfaceStatusCallback statusCallback) {
        // get the one chip (cache it)
        // find the mode which supports the desired type (get the first such mode)
        // find the list of up interfaces which will have to be torn down if we switch to this mode
        //    - should we do the change? (i.e. request type priority > torn down interface priority)
        //    - YES: tear down, call onDestroyed() cb
        //           configure new mode
        //           figure out new types which can be created, call onAvailableForRequest()
        //           return new interface
        //    - NO: return null
        return null;
    }

    private ArrayList<String> getIfaceNames(int type) {
        synchronized (mLock) {
            List<IWifiIface> list = mCacheByType.get(type);
            if (list == null) {
                return null;
            }
            final ArrayList<String> names = new ArrayList<>();
            try {
                for (IWifiIface iface : list) {
                    iface.getName((WifiStatus status, String name) -> {
                        if (status.code == WifiStatusCode.SUCCESS) {
                            names.add(name);
                        } else {
                            Log.e(TAG, "Error while calling IWifiIface.getName: code=" + status.code
                                    + "(" + status.description + ")");
                        }
                    });
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while calling IWifiIface.getName: " + e);
                return null;
            }

            return names;
        }
    }

    private IWifiIface getIface(String ifname) {
        synchronized (mLock) {
            IfaceCacheEntry entry = mCacheByName.get(ifname);
            if (entry == null || !entry.mValid) {
                return null;
            } else {
                return entry.mWifiIface;
            }
        }
    }

    private void removeIface(int type, String ifname) {
        synchronized (mLock) {
            if (!mIsReady) {
                Log.e(TAG, "removeIface( " + type + ", " + ifname
                        + ") called before manager is ready!?");
                return;
            }

            IfaceCacheEntry entry = mCacheByName.get(ifname);
            if (entry == null) {
                Log.e(TAG, "removeIface( " + type + ", " + ifname
                        + ") called but no such interface is known/cached");
                return;
            }

            if (entry.mType != type) {
                Log.e(TAG, "removeIface( " + type + ", " + ifname
                        + ") called but cached type (" + type + ") is not a match!?");
                return;
            }

            mCacheByName.remove(ifname);
            List<IWifiIface> list = mCacheByType.get(type);
            list.remove(ifname);

            try {
                WifiStatus status = new WifiStatus();
                status.code = WifiStatusCode.ERROR_UNKNOWN;
                switch (type) {
                    case TYPE_STA:
                        status = entry.mIWifiChip.removeStaIface(ifname);
                        break;
                    case TYPE_AP:
                        status = entry.mIWifiChip.removeApIface(ifname);
                        break;
                    case TYPE_P2P:
                        status = entry.mIWifiChip.removeP2pIface(ifname);
                        break;
                    case TYPE_NAN:
                        status = entry.mIWifiChip.removeNanIface(ifname);
                        break;
                }
                if (status.code != WifiStatusCode.SUCCESS) {
                    Log.e(TAG, "Error while execute removeXxxIface on ifname=" + ifname + ": code="
                            + status.code + " (" + status.description + ")");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while execute removeXxxIface on ifname=" + ifname + ": " + e);
            }
        }
    }

    // for now just get the first (and only) chip
    private IWifiChip getChip() {
        synchronized (mLock) {
            if (mWifi == null) {
                return null;
            }

            MutableBoolean statusOk = new MutableBoolean(false);
            Mutable<ArrayList<Integer>> chipIdsResp = new Mutable<>();

            try {
                // get all chip IDs
                mWifi.getChipIds((WifiStatus status, ArrayList<Integer> chipIds) -> {
                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                    if (statusOk.value) {
                        chipIdsResp.value = chipIds;
                    } else {
                        Log.e(TAG, "getChipIds failed: code=" + status.code
                                + " (" + status.description + ")");
                    }
                });
                if (!statusOk.value) {
                    return null;
                }

                // grab the first chip
                if (chipIdsResp.value.size() == 0) {
                    Log.e(TAG, "Should have at least 1 chip!");
                    return null;
                }
                int chipId = chipIdsResp.value.get(0);

                Mutable<IWifiChip> chipResp = new Mutable<>();
                mWifi.getChip(chipId, (WifiStatus status, IWifiChip chip) -> {
                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                    if (statusOk.value) {
                        chipResp.value = chip;
                    } else {
                        Log.e(TAG, "getChip failed: code=" + status.code
                                + " (" + status.description + ")");
                    }
                });
                if (!statusOk.value) {
                    return null;
                }

                WifiStatus status = chipResp.value.registerEventCallback(
                        new WifiChipEventCallback(chipResp.value));
                if (status.code != WifiStatusCode.SUCCESS) {
                    Log.e(TAG, "registerEventCallback failed: code=" + status.code
                            + " (" + status.description + ")");
                    return null;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "getChipIds or getChip exception: " + e);
                return null;
            }
        }
        return null;
    }


    private static class IfaceCacheEntry {
        boolean mValid;
        IWifiIface mWifiIface;
        int mType;
        IWifiChip mIWifiChip;
        InterfaceStatusCallback mStatusCallback;
    }

    private class WifiEventCallback extends IWifiEventCallback.Stub {
        @Override
        public void onStart() throws RemoteException {
            synchronized (mLock) {
                mIsReady = true;
                mManagerStatusCallbacks.forEach(ManagerStatusCallback::onReady);
            }
        }

        @Override
        public void onStop() throws RemoteException {
            stop();
        }

        @Override
        public void onFailure(WifiStatus status) throws RemoteException {
            stop();
        }
    }

    private class WifiChipEventCallback extends IWifiChipEventCallback.Stub {
        private IWifiChip mIWifiChip;

        WifiChipEventCallback(IWifiChip wifiChip) {
            mIWifiChip = wifiChip;
        }

        @Override
        public void onChipReconfigured(int modeId) throws RemoteException {

        }

        @Override
        public void onChipReconfigureFailure(WifiStatus status) throws RemoteException {

        }

        @Override
        public void onIfaceAdded(int type, String name) throws RemoteException {

        }

        @Override
        public void onIfaceRemoved(int type, String name) throws RemoteException {

        }

        @Override
        public void onDebugRingBufferDataAvailable(WifiDebugRingBufferStatus status,
                ArrayList<Byte> data) throws RemoteException {

        }

        @Override
        public void onDebugErrorAlert(int errorCode, ArrayList<Byte> debugData)
                throws RemoteException {

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
