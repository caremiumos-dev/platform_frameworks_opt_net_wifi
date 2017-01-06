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
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.RemoteException;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.Pair;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proxies chip & interface management for IWifi HAL. Provides
 * policy decisions as to which interfaces have priority over other interfaces.
 */
public class HalInterfacePolicyManager {
    private static final String TAG = "HalInterfacePolicyMgr";
    private static final boolean DBG = true;

    private static final String HAL_HIDL_WIFI_SERVICE_NAME = "wifi";
    private static final String HAL_HIDL_MANAGER_SERVICE_NAME = "manager";

    // to be used for synchronizing access to any class member
    private final Object mLock = new Object();

    private final Set<ManagerStatusCallback> mManagerStatusCallbacks = new HashSet<>();
    private final Map<String, IfaceCacheEntry> mCacheByName = new HashMap<>();
    private final Map<Integer, Set<InterfaceAvailableForRequestListener>> mAvailableListeners =
            new HashMap<>();
    private IServiceManager mServiceManager = null;
    private IWifi mWifi = null;
    private boolean mIsReady = false;
    private final WifiEventCallback mWifiEventCallback = new WifiEventCallback();

    // single chip operation: cache
    // TODO: extend beyond single chip
    private final WifiChipEventCallback mWifiChipEventCallback = new WifiChipEventCallback();
    private IWifiChip mChip;
    private int mChipId;
    private ArrayList<IWifiChip.ChipMode> mAvailableChipModes;
    private boolean mValidCurrentChipMode = false;
    private int mCurrentChipModeId;


    public HalInterfacePolicyManager() {
        mAvailableListeners.put(IfaceType.STA, new HashSet<>());
        mAvailableListeners.put(IfaceType.AP, new HashSet<>());
        mAvailableListeners.put(IfaceType.P2P, new HashSet<>());
        mAvailableListeners.put(IfaceType.NAN, new HashSet<>());
    }

    /**
     * Register a ManagerStatusCallback to get information about the policy manager status.
     * The status indicates when the policy manager is ready to accept requests for interfaces,
     * indicating that Wi-Fi HAL service has started, and when the HAL service is down.
     *
     * It is safe to re-register the same callback object - duplicates are detected and only a
     * single copy kept.
     *
     * @param callback ManagerStatusCallback callback object.
     */
    public void registerStatusCallback(ManagerStatusCallback callback) {
        synchronized (mLock) {
            mManagerStatusCallbacks.add(callback);
            if (mIsReady) {
                callback.onReady();
            }
        }
    }

    /**
     * Corresponds to IWifiChip.createStaIface(). Returns a null on failure. The
     * destroyedListener will be called whenever the interface is destroyed (even by request).
     * The availableListener will be called: only once + only if the original request was denied
     * (a null was returned) + and only when there is a potential for the request to be fulfilled.
     * Either listener can be a null.
     */
    public IWifiStaIface createStaIface(InterfaceDestroyedListener destroyedListener,
            InterfaceAvailableForRequestListener availableListener) {
        return (IWifiStaIface) createIface(IfaceType.STA, destroyedListener, availableListener);
    }

    /**
     * Corresponds to IWifiChip.getStaIfaceNames().
     */
    public ArrayList<String> getStaIfaceNames() {
        return getIfaceNames(IfaceType.STA);
    }

    /**
     * Corresponds to IWifiChip.getStaIface(ifname).
     */
    public IWifiStaIface getStaIface(String ifname) {
        return (IWifiStaIface) getIface(ifname);
    }

    /**
     * Corresponds to IWifiChip.removeStaIface(ifname). Returns true on success.
     */
    public boolean removeStaIface(String ifname) {
        return removeIface(IfaceType.STA, ifname);
    }

    /**
     * Corresponds to IWifiChip.createApIface(). Returns a null on failure. The
     * destroyedListener will be called whenever the interface is destroyed (even by request).
     * The availableListener will be called: only once + only if the original request was denied
     * (a null was returned) + and only when there is a potential for the request to be fulfilled.
     * Either listener can be a null.
     */
    public IWifiApIface createApIface(InterfaceDestroyedListener destroyedListener,
            InterfaceAvailableForRequestListener availableListener) {
        return (IWifiApIface) createIface(IfaceType.AP, destroyedListener, availableListener);
    }

    /**
     * Corresponds to IWifiChip.getApIfaceNames().
     */
    public ArrayList<String> getApIfaceNames() {
        return getIfaceNames(IfaceType.AP);
    }

    /**
     * Corresponds to IWifiChip.getApIface(ifname).
     */
    public IWifiApIface getApIface(String ifname) {
        return (IWifiApIface) getIface(ifname);
    }

    /**
     * Corresponds to IWifiChip.removeApIface(ifname).  Returns true on success.
     */
    public boolean removeApIface(String ifname) {
        return removeIface(IfaceType.AP, ifname);
    }

    /**
     * Corresponds to IWifiChip.createP2pIface(). Returns a null on failure. The
     * destroyedListener will be called whenever the interface is destroyed (even by request).
     * The availableListener will be called: only once + only if the original request was denied
     * (a null was returned) + and only when there is a potential for the request to be fulfilled.
     * Either listener can be a null.
     */
    public IWifiP2pIface createP2pIface(InterfaceDestroyedListener destroyedListener,
            InterfaceAvailableForRequestListener availableListener) {
        return (IWifiP2pIface) createIface(IfaceType.P2P, destroyedListener, availableListener);
    }

    /**
     * Corresponds to IWifiChip.getP2pIfaceNames().
     */
    public ArrayList<String> getP2pIfaceNames() {
        return getIfaceNames(IfaceType.P2P);
    }

    /**
     * Corresponds to IWifiChip.getP2pIface(ifname).
     */
    public IWifiP2pIface getP2pIface(String ifname) {
        return (IWifiP2pIface) getIface(ifname);
    }

    /**
     * Corresponds to IWifiChip.removeP2pIface(ifname).  Returns true on success.
     */
    public boolean removeP2pIface(String ifname) {
        return removeIface(IfaceType.P2P, ifname);
    }

    /**
     * Corresponds to IWifiChip.createNanIface(). Returns a null on failure. The
     * destroyedListener will be called whenever the interface is destroyed (even by request).
     * The availableListener will be called: only once + only if the original request was denied
     * (a null was returned) + and only when there is a potential for the request to be fulfilled.
     * Either listener can be a null.
     */
    public IWifiNanIface createNanIface(InterfaceDestroyedListener destroyedListener,
            InterfaceAvailableForRequestListener availableListener) {
        return (IWifiNanIface) createIface(IfaceType.NAN, destroyedListener, availableListener);
    }

    /**
     * Corresponds to IWifiChip.getNanIfaceNames().
     */
    public ArrayList<String> getNanIfaceNames() {
        return getIfaceNames(IfaceType.NAN);
    }

    /**
     * Corresponds to IWifiChip.getNanIface(ifname).
     */
    public IWifiNanIface getNanIface(String ifname) {
        return (IWifiNanIface) getIface(ifname);
    }

    /**
     * Corresponds to IWifiChip.removeNanIface(ifname).  Returns true on success.
     */
    public boolean removeNanIface(String ifname) {
        return removeIface(IfaceType.NAN, ifname);
    }

    /**
     * Corresponds to IWifiChip.createRttController(boundIface).
     */
    IWifiRttController createRttController(IWifiIface boundIface) {
        IWifiChip chip = getChip(boundIface);
        if (chip == null) {
            Log.e(TAG, "createRttController: cannot find chip corresponding to interface");
            return null;
        }
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

    /**
     * Returns the IWifiChip from which the IWifiIface was obtained.
     */
    public IWifiChip getChip(IWifiIface iface) {
        if (iface == null) {
            return null;
        }

        synchronized (mLock) {
            String name = getName(iface);
            if (name == null) {
                return null;
            }

            IfaceCacheEntry entry = mCacheByName.get(name);
            if (entry == null) {
                Log.e(TAG, "No interface by that name cached -- " + name);
                return null;
            }
            if (entry.pendingDeletion) {
                Log.e(TAG, "Interface pending deletion -- not valid for usage");
                return null;
            }

            return entry.wifiChip;
        }
    }

    /**
     * HAL interface policy manager status callbacks. May be called on a different thread then
     * the one on which registered.
     */
    public interface ManagerStatusCallback {
        /**
         * Indicates that the policy manager can be queried to create interfaces. Means that the
         * IWifi service is available and started.
         */
        void onReady();

        /**
         * Indicates that the policy manager is down - all interfaces are destroyed (may not get
         * individual onDestroyed calls). No interface queries are valid. Means that the IWifi
         * service is down.
         */
        void onDown();
    }

    /**
     * Listener for indications that a specific interface (on which registered) is down. Will be
     * called on a different thread than the one on which registered.
     */
    public interface InterfaceDestroyedListener {
        /**
         * Called when the interface to which attached is destroyed.
         */
        void onDestroyed();
    }

    /**
     * Listener for indications that a specific interface type (on which registered) is available
     * for request. This only happens if the original request was denied (a null was returned).
     * The callee (which will be called on a different thread than that on which the listener was
     * registered) can retry the request - there are no guarantees that the call will succeed (e.g.
     * multiple registrations exist and multiple entities will attempt to get the interface or other
     * interfaces). The callee will only be called once for each registration - i.e. it should
     * re-register the listener on the next attempt.
     */
    public interface InterfaceAvailableForRequestListener {
        /**
         * Called when the interface type to which attached is available for a request.
         */
        void onAvailableForRequest();
    }

    /**
     * Wrapper function to access the HIDL Wi-Fi service. Created to be mockable in unit-tests.
     */
    protected IWifi getWifiServiceMockable() {
        try {
            return IWifi.getService(HAL_HIDL_WIFI_SERVICE_NAME);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IWifi service: " + e);
            return null;
        }
    }

    protected IServiceManager getServiceManagerMockable() {
        try {
            return IServiceManager.getService(HAL_HIDL_MANAGER_SERVICE_NAME);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IServiceManager: " + e);
            return null;
        }
    }

    /**
     * Initializes the IServiceManager and IWifi objects and registers for their events (death,
     * startup). Can be safely re-executed after partial setup to complete the setup.
     */
    public void start() {
        if (DBG) Log.d(TAG, "start: mServiceManager=" + mServiceManager + ", mWifi=" + mWifi);
        synchronized (mLock) {
            if (mServiceManager == null) {
                mServiceManager = getServiceManagerMockable();
                if (mServiceManager == null) {
                    Log.wtf(TAG, "Failed to get IServiceManager instance");
                    return; // TODO: what else?
                } else {
                    try {
                        if (!mServiceManager.linkToDeath(cookie -> {
                            Log.e(TAG, "IServiceManager died: cookie=" + cookie);
                            synchronized (mLock) {
                                mServiceManager = null;
                                reset(true);
                                start();
                            }
                        }, /* don't care */ 0)) {
                            Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                            mServiceManager = null;
                            return; // TODO: what else?
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
                            return; // TODO: what else?
                        }
                    } catch (RemoteException e) {
                        Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
                        mServiceManager = null;
                        return; // TODO: what else?
                    }
                }
            }

            try {
                if (mWifi == null) {
                    mWifi = getWifiServiceMockable();
                    if (mWifi == null) {
                        Log.e(TAG, "IWifi not (yet) available - but have a listener for it ...");
                        return;
                    }
                    if (!mWifi.linkToDeath(cookie -> {
                        Log.e(TAG, "IWifi HAL service died! Have a listener for it ... cookie="
                                + cookie);
                        reset(true);
                    }, /* don't care */ 0)) {
                        Log.e(TAG, "Error on linkToDeath on IWifi");
                        mWifi = null; // TODO: what else?
                        return;
                    }

                    WifiStatus status = mWifi.registerEventCallback(mWifiEventCallback);
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG, "IWifi.registerEventCallback failed: code=" + status.code
                                + " (" + status.description + ")");
                        mWifi = null; // TODO: what else?
                        return;
                    }
                }

                mIsReady = mWifi.isStarted();
                if (!mIsReady) {
                    WifiStatus status = mWifi.start();
                    if (status.code == WifiStatusCode.SUCCESS) {
                        mIsReady = true;
                    } else {
                        Log.e(TAG, "Cannot start IWifi: code=" + status.code + " ("
                                + status.description + ")");
                        mWifi = null; // TODO: what else?
                        return;
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while operating on IWifi: " + e);
                mWifi = null;
                return; // TODO: what else?
            }


            if (mIsReady) {
                mManagerStatusCallbacks.forEach(ManagerStatusCallback::onReady);
            }
        }
    }

    /**
     * Cleans up the local variables when the underlying services are shut-down. Notifies listeners.
     */
    private void reset(boolean resetWifi) {
        if (DBG) Log.d(TAG, "reset: resetWifi=" + resetWifi);
        synchronized (mLock) {
            mIsReady = false;
            if (resetWifi) {
                mWifi = null;
            }
            mChip = null;
            mCacheByName.clear();
            mAvailableListeners.clear();
            mAvailableChipModes = null;
            mValidCurrentChipMode = false;
            mManagerStatusCallbacks.forEach(ManagerStatusCallback::onDown);
        }
    }

    private Pair<Integer, List<IfaceCacheEntry>> getMatchingMode(int ifaceType) {
        if (DBG) Log.d(TAG, "getMatchingMode: ifaceType=" + ifaceType);
        synchronized (mLock) {
            if (!updateChipCachedInfo()) {
                return null;
            }

            boolean supportingChipModeFound = false;
            int bestChipModeId = 0;
            List<IfaceCacheEntry> bestTearDownIfaces = new ArrayList<>();
            for (IWifiChip.ChipMode cm : mAvailableChipModes) {
                List<IfaceCacheEntry> tearDownIfaces = new ArrayList<>();
                if (doesChipModeAndPolicySupportRequest(ifaceType, cm.id, tearDownIfaces)) {
                    boolean replaceCurrentBest =
                            !supportingChipModeFound || isProposedChipModePreferredByPolicy(cm.id,
                                    tearDownIfaces, bestChipModeId, bestTearDownIfaces);

                    if (DBG) {
                        Log.d(TAG, "createIface: chip mode " + cm.id
                                + " supports request and is better than current best="
                                + replaceCurrentBest);
                    }
                    if (replaceCurrentBest) {
                        supportingChipModeFound = true;
                        bestChipModeId = cm.id;
                        bestTearDownIfaces = tearDownIfaces;
                    }
                }
            }

            if (DBG) {
                Log.d(TAG, "createIface: supportingChipModeFound=" + supportingChipModeFound
                        + ": bestChipModeId=" + bestChipModeId);
            }

            if (supportingChipModeFound) {
                return new Pair<>(bestChipModeId, bestTearDownIfaces);
            } else {
                return null;
            }
        }
    }

    private IWifiIface createIface(int ifaceType, InterfaceDestroyedListener destroyedListener,
            InterfaceAvailableForRequestListener availableListener) {
        if (DBG) Log.d(TAG, "createIface: ifaceType=" + ifaceType);
        synchronized (mLock) {
            Pair<Integer, List<IfaceCacheEntry>> supportingInfo = getMatchingMode(ifaceType);

            if (supportingInfo != null) {
                if (executeChipReconfiguration(supportingInfo.first, supportingInfo.second)) {
                    // allocate and return interface (chip mode should be configured)
                    try {
                        Mutable<WifiStatus> statusResponse = new Mutable<>();
                        Mutable<IWifiIface> ifaceResponse = new Mutable<>();
                        switch (ifaceType) {
                            case IfaceType.STA:
                                mChip.createStaIface((WifiStatus status, IWifiStaIface iface) -> {
                                    statusResponse.value = status;
                                    ifaceResponse.value = iface;
                                });
                                break;
                            case IfaceType.AP:
                                mChip.createApIface((WifiStatus status, IWifiApIface iface) -> {
                                    statusResponse.value = status;
                                    ifaceResponse.value = iface;
                                });
                                break;
                            case IfaceType.P2P:
                                mChip.createP2pIface((WifiStatus status, IWifiP2pIface iface) -> {
                                    statusResponse.value = status;
                                    ifaceResponse.value = iface;
                                });
                                break;
                            case IfaceType.NAN:
                                mChip.createNanIface((WifiStatus status, IWifiNanIface iface) -> {
                                    statusResponse.value = status;
                                    ifaceResponse.value = iface;
                                });
                                break;
                        }


                        if (ifaceResponse != null
                                && statusResponse.value.code == WifiStatusCode.SUCCESS) {
                            IfaceCacheEntry cacheEntry = new IfaceCacheEntry();
                            cacheEntry.wifiIface = ifaceResponse.value;
                            cacheEntry.type = ifaceType;
                            cacheEntry.name = getName(ifaceResponse.value);
                            cacheEntry.wifiChip = mChip;
                            if (destroyedListener != null) {
                                cacheEntry.destroyedListeners.add(destroyedListener);
                            }
                            mCacheByName.put(cacheEntry.name, cacheEntry);

                            return ifaceResponse.value;
                        } else {
                            Log.e(TAG, "createIface: error or null return, ifaceType=" + ifaceType
                                    + " error: code=" + statusResponse.value.code + " ("
                                    + statusResponse.value.description + ")");
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "createIface: ifaceType=" + ifaceType + " exception: " + e);
                    }
                }
            }

            // only get here if interface not created (either because of an error or because policy
            // didn't permit).
            if (availableListener != null) {
                mAvailableListeners.get(ifaceType).add(availableListener);
            }

            return null;
        }
    }

    // Only announce if possible to allocate
    private void announceAvailabilityForType(int ifaceType) {
        if (DBG) Log.d(TAG, "announceAvailabilityForType: ifaceType=" + ifaceType);
        if (mAvailableListeners.get(ifaceType).size() == 0) {
            if (DBG) Log.d(TAG, "none waiting - skipping");
            return;
        }
        Pair<Integer, List<IfaceCacheEntry>> matchingInfo = getMatchingMode(ifaceType);
        if (matchingInfo != null) {
            mAvailableListeners.get(ifaceType).forEach(
                    InterfaceAvailableForRequestListener::onAvailableForRequest);
            mAvailableListeners.get(ifaceType).clear();
        }
    }

    // Try all types by priority. I.e. if 2 types are available then (hopefully) the higher priority
    // gets the first try.
    private void announceAvailability() {
        announceAvailabilityForType(IfaceType.AP);
        announceAvailabilityForType(IfaceType.STA);
        announceAvailabilityForType(IfaceType.P2P);
        announceAvailabilityForType(IfaceType.NAN);
    }

    private ArrayList<String> getIfaceNames(int type) {
        synchronized (mLock) {
            ArrayList<String> names = new ArrayList<>();
            for (IfaceCacheEntry cacheEntry : mCacheByName.values()) {
                if (!cacheEntry.pendingDeletion && cacheEntry.type == type) {
                    names.add(cacheEntry.name);
                }
            }

            return names;
        }
    }

    private IWifiIface getIface(String ifname) {
        synchronized (mLock) {
            IfaceCacheEntry entry = mCacheByName.get(ifname);
            if (entry == null) {
                return null;
            } else {
                return entry.wifiIface;
            }
        }
    }

    private boolean removeIface(int type, String ifname) {
        Log.d(TAG, "removeIface: type=" + type + ", ifname=" + ifname);
        synchronized (mLock) {
            IfaceCacheEntry entry = mCacheByName.get(ifname);
            if (entry == null) {
                Log.e(TAG, "removeIface( " + type + ", " + ifname
                        + ") called but no such interface is known/cached");
                return false;
            }

            if (entry.type != type) {
                Log.e(TAG, "removeIface( " + type + ", " + ifname
                        + ") called but cached type (" + type + ") is not a match!?");
                return false;
            }

            try {
                WifiStatus status = new WifiStatus();
                status.code = WifiStatusCode.ERROR_UNKNOWN;
                switch (type) {
                    case IfaceType.STA:
                        status = entry.wifiChip.removeStaIface(ifname);
                        break;
                    case IfaceType.AP:
                        status = entry.wifiChip.removeApIface(ifname);
                        break;
                    case IfaceType.P2P:
                        status = entry.wifiChip.removeP2pIface(ifname);
                        break;
                    case IfaceType.NAN:
                        status = entry.wifiChip.removeNanIface(ifname);
                        break;
                }
                if (status.code == WifiStatusCode.SUCCESS) {
                    entry.pendingDeletion = true;
                } else {
                    Log.e(TAG, "Error while execute removeXxxIface on ifname=" + ifname + ": code="
                            + status.code + " (" + status.description + ")");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while execute removeXxxIface on ifname=" + ifname + ": " + e);
            }

            return entry.pendingDeletion;
        }
    }

    private boolean updateChipCachedInfo() {
        if (DBG) {
            Log.d(TAG, "updateChipCachedInfo: mChip=" + mChip + ", mAvailableChipModes="
                    + mAvailableChipModes);
        }
        synchronized (mLock) {
            if (mWifi == null || !mIsReady) {
                return false;
            }

            MutableBoolean statusOk = new MutableBoolean(false);

            try {
                if (mChip == null) {
                    Mutable<ArrayList<Integer>> chipIdsResp = new Mutable<>();

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
                        return false;
                    }

                    // grab the first chip
                    if (chipIdsResp.value.size() == 0) {
                        Log.e(TAG, "Should have at least 1 chip!");
                        return false;
                    }
                    mChipId = chipIdsResp.value.get(0);

                    mWifi.getChip(mChipId, (WifiStatus status, IWifiChip chip) -> {
                        statusOk.value = status.code == WifiStatusCode.SUCCESS;
                        if (statusOk.value) {
                            mChip = chip;
                        } else {
                            Log.e(TAG, "getChip failed: code=" + status.code
                                    + " (" + status.description + ")");
                        }
                    });
                    if (!statusOk.value) {
                        return false;
                    }

                    WifiStatus status = mChip.registerEventCallback(
                            mWifiChipEventCallback);
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG, "registerEventCallback failed: code=" + status.code
                                + " (" + status.description + ")");
                        mChip = null;
                        return false;
                    }
                }

                if (mAvailableChipModes == null) {
                    mValidCurrentChipMode = false;
                    mChip.getAvailableModes(
                            (WifiStatus status, ArrayList<IWifiChip.ChipMode> modes) -> {
                                statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                if (statusOk.value) {
                                    mAvailableChipModes = modes;
                                } else {
                                    Log.e(TAG,
                                            "getAvailableModes failed: code=" + status.code + " ("
                                                    + status.description + ")");
                                }
                            });
                    if (!statusOk.value) {
                        return false;
                    }
                }

                if (!mValidCurrentChipMode) {
                    mChip.getMode((WifiStatus status, int modeId) -> {
                        statusOk.value = status.code == WifiStatusCode.SUCCESS;
                        if (statusOk.value) {
                            mValidCurrentChipMode = true;
                            mCurrentChipModeId = modeId;
                        } else {
                            Log.w(TAG, "getMode failed: code=" + status.code + " ("
                                    + status.description + ")");
                        }
                    });
                    if (!statusOk.value) {
                        return true; // ok not to have a current chip mode
                    }

                    // validate the chip mode ID
                    boolean chipModeMatchFound = false;
                    for (IWifiChip.ChipMode cm : mAvailableChipModes) {
                        if (cm.id == mCurrentChipModeId) {
                            chipModeMatchFound = true;
                            break;
                        }
                    }
                    if (!chipModeMatchFound) {
                        Log.e(TAG, "Can't find the current chip mode ID (" + mCurrentChipModeId
                                + ") in the list of available modes!?");
                        mAvailableChipModes = null; // just got good measure (re-obtain next time)
                        mValidCurrentChipMode = false;
                        return false;
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IWifiChip operations exception: " + e);
                return false;
            }
        }

        return true;
    }

    /**
     * Checks whether the chip supports the requested interface type with the chip mode specified
     * by the chip mode ID *and* policy allows the change. Returns true if supported, false
     * otherwise.
     *
     * If supported then returns the list of interfaces which will have to be torn down in order
     * to meet the request. This list will include:
     * - All interfaces which have to be torn down in the current chip mode (i.e. it is the same as
     *   the current chip mode) in order to meet the request (just a single one most likely?).
     *
     * POLICY:
     *  - NAN < P2P < STA < AP
     *  - will not tear down same for same, e.g. P2P for P2P
     *  - will tear down arbitrary (first?) interface of the lower priority
     *  - do not allow mode change for same or lower priority interfaces
     */
    private boolean doesChipModeAndPolicySupportRequest(int requestedIfaceType,
            int chipModeId, List<IfaceCacheEntry> tearDownIfaces) {
        if (DBG) {
            Log.d(TAG,
                    "doesChipModeAndPolicySupportRequest: requestedIfaceType=" + requestedIfaceType
                            + ", chipModeId=" + chipModeId + ", tearDownIfaces=" + tearDownIfaces);
        }
        synchronized (mLock) {
            IWifiChip.ChipMode chipMode = null;
            for (IWifiChip.ChipMode cm : mAvailableChipModes) {
                if (cm.id == chipModeId) {
                    chipMode = cm;
                    break;
                }
            }
            if (chipMode == null) {
                Log.e(TAG, "Queried chip mode ID doesn't exist: " + chipModeId);
                return false;
            }

            int[] numberOfInterfacesPerType = getNumberOfInterfacesByType();
            if (mValidCurrentChipMode && chipModeId != mCurrentChipModeId) {
                switch (requestedIfaceType) {
                    case IfaceType.NAN:
                        if (numberOfInterfacesPerType[IfaceType.NAN] != 0
                                || numberOfInterfacesPerType[IfaceType.P2P] != 0
                                || numberOfInterfacesPerType[IfaceType.STA] != 0
                                || numberOfInterfacesPerType[IfaceType.AP] != 0) {
                            return false;
                        }
                        break;
                    case IfaceType.P2P:
                        if (numberOfInterfacesPerType[IfaceType.P2P] != 0
                                || numberOfInterfacesPerType[IfaceType.STA] != 0
                                || numberOfInterfacesPerType[IfaceType.AP] != 0) {
                            return false;
                        }

                        break;
                    case IfaceType.STA:
                        if (numberOfInterfacesPerType[IfaceType.STA] != 0
                                || numberOfInterfacesPerType[IfaceType.AP] != 0) {
                            return false;
                        }

                        break;
                    case IfaceType.AP:
                        if (numberOfInterfacesPerType[IfaceType.AP] != 0) {
                            return false;
                        }

                        break;
                }
            }

            // chip mode works if ANY of the combos could support an additional interface of the
            // requested type
            for (IWifiChip.ChipIfaceCombination chipIfaceCombo : chipMode.availableCombinations) {
                for (IWifiChip.ChipIfaceCombinationLimit chipIfaceComboLimit : chipIfaceCombo
                        .limits) {
                    // the only limits which are relevant are those which contain the requested
                    // interface type
                    if (chipIfaceComboLimit.types.contains(requestedIfaceType)) {
                        // now count all existing interfaces of the types included here
                        int numberOfLimitedInterfaces = 0;
                        for (int type : chipIfaceComboLimit.types) {
                            numberOfLimitedInterfaces += numberOfInterfacesPerType[type];
                        }
                        if (numberOfLimitedInterfaces < chipIfaceComboLimit.maxIfaces) {
                            return true;
                        } else {

                            tearDownIfaces.clear();
                            switch (requestedIfaceType) {
                                case IfaceType.NAN:
                                    // NOP: lowest priority - won't tear down anything for it
                                    break;
                                case IfaceType.P2P:
                                    if (tearDownIfAvailable(IfaceType.NAN, chipIfaceComboLimit,
                                            numberOfInterfacesPerType, tearDownIfaces)) {
                                        return true;
                                    }
                                    break;
                                case IfaceType.STA:
                                    // will tear down NAN and P2P (?) for this
                                    // TODO: figure out policy for STA
                                    if (tearDownIfAvailable(IfaceType.NAN, chipIfaceComboLimit,
                                            numberOfInterfacesPerType, tearDownIfaces)) {
                                        return true;
                                    }
                                    if (tearDownIfAvailable(IfaceType.P2P, chipIfaceComboLimit,
                                            numberOfInterfacesPerType, tearDownIfaces)) {
                                        return true;
                                    }
                                    break;
                                case IfaceType.AP:
                                    if (tearDownIfAvailable(IfaceType.NAN, chipIfaceComboLimit,
                                            numberOfInterfacesPerType, tearDownIfaces)) {
                                        return true;
                                    }
                                    if (tearDownIfAvailable(IfaceType.P2P, chipIfaceComboLimit,
                                            numberOfInterfacesPerType, tearDownIfaces)) {
                                        return true;
                                    }
                                    if (tearDownIfAvailable(IfaceType.STA, chipIfaceComboLimit,
                                            numberOfInterfacesPerType, tearDownIfaces)) {
                                        return true;
                                    }

                                    break;

                            }
                        }
                    }
                }
            }

            return false;
        }
    }

    /**
     * Compares the proposed chip mode ID (with it's corresponding tear-down list) to the current
     * best option and returns true if it's 'better' (based on policy).
     *
     * Policy:
     * - Prefer mechanism to meet request without chip mode change
     * - If both don't change mode (or no current mode):
     *    - Prefer the one that tears down fewer interfaces (0 or 1 realistically)
     *    - Prefer the one tearing down the lower priority interface
     * - If both change mode then (ignore tear down list - should be empty)
     *    - Arbitrary (TODO)
     */
    private boolean isProposedChipModePreferredByPolicy(int proposedChipModeId,
            List<IfaceCacheEntry> proposedTearDownList, int currentBestChipModeId,
            List<IfaceCacheEntry> currentBestTearDownList) {
        if (DBG) {
            Log.d(TAG,
                    "isProposedChipModePreferredByPolicy: proposedChipModeId=" + proposedChipModeId
                            + ", proposedTearDownList=" + proposedTearDownList
                            + ", currentBestChipModeId=" + currentBestChipModeId
                            + ", currentBestTearDownList=" + currentBestTearDownList);
        }
        // if no current mode (i.e. have to change to one of the proposal): arbitrary pick
        if (!mValidCurrentChipMode) {
            return false;
        }

        // only one proposal doesn't change mode -> clear favorite
        if (proposedChipModeId != currentBestChipModeId) {
            if (currentBestChipModeId == mCurrentChipModeId) {
                return false;
            }
            if (proposedChipModeId == mCurrentChipModeId) {
                return true;
            }
        } else if (proposedChipModeId == mCurrentChipModeId) { // both proposals don't change mode
            if (proposedTearDownList.size() == 0) {
                return true; // arbitrary: doesn't check if both length is 0
            } else if (currentBestTearDownList.size() == 0) {
                return false;
            }

            // no mode change in either proposal but both tear down interfaces
            IfaceCacheEntry currentBest = currentBestTearDownList.get(0);
            IfaceCacheEntry proposed = proposedTearDownList.get(0);

            switch (proposed.type) {
                case IfaceType.NAN:
                    if (currentBest.type != IfaceType.NAN) {
                        return true;
                    }
                    break;
                case IfaceType.P2P:
                    if (currentBest.type != IfaceType.NAN) {
                        return true;
                    }
                    break;
                case IfaceType.STA:
                    if (currentBest.type == IfaceType.AP) {
                        return true;
                    }
                    break;
                case IfaceType.AP:
                    return false;
            }
        }

        // both proposals change mode -> arbitrary
        return false;
    }

    /**
     * Execute change to new chip mode: reconfiguring chip (only if necessary) and tearing down
     * interfaces (only if necessary, i.e. chip mode wasn't changed).
     *
     * Returns false on any failure
     */
    private boolean executeChipReconfiguration(int newChipModeId,
            List<IfaceCacheEntry> tearDownIfaceList) {
        if (DBG) {
            Log.d(TAG, "executeChipReconfiguration: newChipModeId=" + newChipModeId
                    + ", tearDownIfaceList=" + tearDownIfaceList);
        }
        synchronized (mLock) {
            try {
                // either re-configure OR tear down interfaces - don't need both
                if (!mValidCurrentChipMode || mCurrentChipModeId != newChipModeId) {
                    mValidCurrentChipMode = true;
                    mCurrentChipModeId = newChipModeId;
                    for (IfaceCacheEntry entry: mCacheByName.values()) {
                        entry.pendingDeletion = true;
                    }

                    WifiStatus status = mChip.configureChip(newChipModeId);
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG,
                                "executeChipReconfiguration: error in chip reconfiguration: code="
                                        + status.code + " (" + status.description + ")");
                        return false;
                    }
                } else {
                    for (IfaceCacheEntry cacheEntry : tearDownIfaceList) {
                        WifiStatus status = new WifiStatus();
                        status.code = WifiStatusCode.ERROR_UNKNOWN;
                        switch (cacheEntry.type) {
                            case IfaceType.STA:
                                status = cacheEntry.wifiChip.removeStaIface(cacheEntry.name);
                                break;
                            case IfaceType.AP:
                                status = cacheEntry.wifiChip.removeApIface(cacheEntry.name);
                                break;
                            case IfaceType.P2P:
                                status = cacheEntry.wifiChip.removeP2pIface(cacheEntry.name);
                                break;
                            case IfaceType.NAN:
                                status = cacheEntry.wifiChip.removeNanIface(cacheEntry.name);
                                break;
                        }
                        if (status.code != WifiStatusCode.SUCCESS) {
                            Log.e(TAG, "Error while execute removeXxxIface on ifname="
                                    + cacheEntry.name + ": code=" + status.code + " ("
                                    + status.description + ")");
                            return false;
                        }
                        cacheEntry.pendingDeletion = true;
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "executeChipReconfiguration: exception in chip reconfiguration: " + e);
                return false;
            }
        }

        return true;
    }

    private static class IfaceCacheEntry {
        public boolean pendingDeletion = false; // set when interface deleted by us, waiting for cb
        public IWifiIface wifiIface;
        public String name;
        public int type;
        public IWifiChip wifiChip;
        public Set<InterfaceDestroyedListener> destroyedListeners = new HashSet<>();

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{pendingDeletion=").append(pendingDeletion).append(", name=")
                    .append(name).append(", type=").append(type)
                    .append(", destroyedListeners.size()=").append(destroyedListeners.size())
                    .append("}");
            return sb.toString();
        }
    }

    private static String getName(IWifiIface iface) {
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

        return nameResp.value;
    }

    private int[] getNumberOfInterfacesByType() {
        int[] types = { 0, 0, 0, 0 };
        synchronized (mLock) {
            for (IfaceCacheEntry entry : mCacheByName.values()) {
                if (!entry.pendingDeletion) {
                    types[entry.type] += 1;
                }
            }
        }

        return types;
    }

    private IfaceCacheEntry getFirstInterfaceOfType(int type) {
        synchronized (mLock) {
            for (IfaceCacheEntry entry : mCacheByName.values()) {
                if (!entry.pendingDeletion && entry.type == type) {
                    return entry;
                }
            }
        }

        return null;
    }

    private boolean tearDownIfAvailable(int type, IWifiChip.ChipIfaceCombinationLimit limits,
            int[] numberOfInterfacePerType, List<IfaceCacheEntry> tearDownIfaces) {
        if (limits.types.contains(type) && numberOfInterfacePerType[type] != 0) {
            IfaceCacheEntry ifaceEntry = getFirstInterfaceOfType(type);
            if (ifaceEntry != null) {
                tearDownIfaces.add(ifaceEntry);
                return true;
            }
        }

        return false;
    }

    private class WifiEventCallback extends IWifiEventCallback.Stub {
        @Override
        public void onStart() throws RemoteException {
            if (DBG) Log.v(TAG, "IWifiEventCallback.onStart");
            // NOP: only happens in reaction to my calls
        }

        @Override
        public void onStop() throws RemoteException {
            if (DBG) Log.v(TAG, "IWifiEventCallback.onStop");
            // NOP: only happens in reaction to my calls
        }

        @Override
        public void onFailure(WifiStatus status) throws RemoteException {
            if (DBG) {
                Log.v(TAG, "IWifiEventCallback.onFailure: code=" + status.code + " ("
                        + status.description + ")");
            }
            reset(false);
            start();
        }
    }

    private class WifiChipEventCallback extends IWifiChipEventCallback.Stub {
        @Override
        public void onChipReconfigured(int modeId) throws RemoteException {
            if (DBG) Log.v(TAG, "IWifiEventCallback.onChipReconfigured: modeId=" + modeId);

            synchronized (mLock) {
                if (!mValidCurrentChipMode || (modeId != mCurrentChipModeId)) {
                    Log.w(TAG,
                            "IWifiEventCallback.onChipReconfigured - seems like another entity is "
                                    + "modifying the chip mode: modeId="
                                    + modeId + ", but mValidCurrentChipMode="
                                    + mValidCurrentChipMode
                                    + ", mCurrentChipModeId=" + mCurrentChipModeId);
                }
                Iterator<Map.Entry<String, IfaceCacheEntry>> it =
                        mCacheByName.entrySet().iterator();
                while (it.hasNext()) {
                    IfaceCacheEntry entry = it.next().getValue();
                    if (entry.pendingDeletion) {
                        entry.destroyedListeners.forEach(InterfaceDestroyedListener::onDestroyed);
                        it.remove();
                    }
                }
                announceAvailability();
            }
        }

        @Override
        public void onChipReconfigureFailure(WifiStatus status) throws RemoteException {
            if (DBG) {
                Log.v(TAG, "IWifiEventCallback.onChipReconfigureFailure: code=" + status.code + " ("
                        + status.description + ")");
            }
            for (IfaceCacheEntry entry: mCacheByName.values()) {
                entry.destroyedListeners.forEach(InterfaceDestroyedListener::onDestroyed);
            }
            mCacheByName.clear();
        }

        @Override
        public void onIfaceAdded(int type, String name) throws RemoteException {
            if (DBG) Log.v(TAG, "IWifiEventCallback.onIfaceAdded: type=" + type + ", name=" + name);
        }

        @Override
        public void onIfaceRemoved(int type, String name) throws RemoteException {
            if (DBG) {
                Log.v(TAG, "IWifiEventCallback.onIfaceRemoved: type=" + type + ", name=" + name);
            }

            synchronized (mLock) {
                IfaceCacheEntry entry = mCacheByName.get(name);
                if (entry == null) {
                    Log.e(TAG,
                            "IWifiEventCallback.onIfaceRemoved for an interface not in cache: type="
                                    + type + ", name=" + name);
                } else {
                    if (!entry.pendingDeletion) {
                        Log.e(TAG,
                                "IWifiEventCallback.onIfaceRemoved for an interface not pending "
                                        + "deletion: type="
                                        + type + ", name=" + name);
                    }
                    entry.destroyedListeners.forEach(InterfaceDestroyedListener::onDestroyed);
                    mCacheByName.remove(name);
                }
            }
            announceAvailability();
        }

        @Override
        public void onDebugRingBufferDataAvailable(WifiDebugRingBufferStatus status,
                ArrayList<Byte> data) throws RemoteException {
            // NOP
        }

        @Override
        public void onDebugErrorAlert(int errorCode, ArrayList<Byte> debugData)
                throws RemoteException {
            // NOP
        }
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("HalInterfacePolicyManager:");
        pw.println("  mManagerStatusCallbacks: " + mManagerStatusCallbacks);
        pw.println("  mCacheByName: " + mCacheByName);
        pw.println("  mAvailableListeners: " + mAvailableListeners);
        pw.println("  mServiceManager: " + mAvailableListeners);
        pw.println("  mWifi: " + mWifi);
        pw.println("  mIsReady: " + mIsReady);
        pw.println("  mWifiEventCallback: " + mWifiEventCallback);
        pw.println("  mWifiChipEventCallback: " + mWifiChipEventCallback);
        pw.println("  mChip: " + mChip);
        pw.println("  mChipId: " + mChipId);
        pw.println("  mAvailableChipModes: " + mAvailableChipModes);
        pw.println("  mValidCurrentChipMode: " + mValidCurrentChipMode);
        pw.println("  mCurrentChipModeId: " + mCurrentChipModeId);
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
