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

package com.android.server.wifi.aware;

import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.HalInterfacePolicyManager;

/**
 * Manages the interface to Wi-Fi Aware HIDL (HAL).
 */
public class WifiAwareNativeManager {
    private static final String TAG = "WifiAwareNativeManager";
    private static final boolean DBG = false;

    // to be used for synchronizing access to any of the WifiAwareNative objects
    private final Object mLock = new Object();

    private WifiAwareStateManager mWifiAwareStateManager;
    private HalInterfacePolicyManager mHalInterfacePolicyManager;
    private WifiAwareNativeApi mWifiAwareNativeApi;
    private IWifiNanIface mWifiNanIface = null;
    private InterfaceDestroyedListener mInterfaceDestroyedListener =
            new InterfaceDestroyedListener();
    private InterfaceAvailableForRequestListener mInterfaceAvailableForRequestListener =
            new InterfaceAvailableForRequestListener();

    public WifiAwareNativeManager(WifiAwareStateManager awareStateManager,
            HalInterfacePolicyManager halInterfacePolicyManager,
            WifiAwareNativeApi wifiAwareNativeApi) {
        mWifiAwareStateManager = awareStateManager;
        mHalInterfacePolicyManager = halInterfacePolicyManager;
        mWifiAwareNativeApi = wifiAwareNativeApi;
        mHalInterfacePolicyManager.registerStatusCallback(
                new HalInterfacePolicyManager.ManagerStatusCallback() {
                    @Override
                    public void onReady() {
                        if (DBG) Log.d(TAG, "HalInterfacePolicyManager is ready");
                        tryToGetAware();
                    }

                    @Override
                    public void onDown() {
                        if (DBG) Log.d(TAG, "HalInterfacePolicyManager is down");
                        awareIsDown();
                    }
                });
    }

    /* package */ IWifiNanIface getWifiNanIface() {
        synchronized (mLock) {
            return mWifiNanIface;
        }
    }

    private void tryToGetAware() {
        synchronized (mLock) {
            IWifiNanIface iface = mHalInterfacePolicyManager.createNanIface(
                    mInterfaceDestroyedListener, mInterfaceAvailableForRequestListener);
            if (iface == null) {
                if (DBG) Log.d(TAG, "Was not able to obtain an IWifiNanIface");
            } else {
                if (DBG) Log.d(TAG, "Obtained an IWifiNanIface");

                try {
                    WifiStatus status = iface.registerEventCallback(mWifiAwareNativeApi);
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG, "registerEventCallback error: code=" + status.code + " ("
                                + status.description + ")");
                        return;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "registerEventCallback exception: " + e);
                    return;
                }

                mWifiNanIface = iface;
                mWifiAwareStateManager.enableUsage();
            }
        }
    }

    private void awareIsDown() {
        if (mWifiNanIface != null) {
            synchronized (mLock) {
                mWifiNanIface = null;
                mWifiAwareStateManager.disableUsage();
            }
        }
    }

    private class InterfaceDestroyedListener implements
            HalInterfacePolicyManager.InterfaceDestroyedListener {
        @Override
        public void onDestroyed() {
            if (DBG) Log.d(TAG, "Interface was destroyed");
            awareIsDown();
        }
    }

    private class InterfaceAvailableForRequestListener implements
            HalInterfacePolicyManager.InterfaceAvailableForRequestListener {
        @Override
        public void onAvailableForRequest() {
            if (DBG) Log.d(TAG, "Interface is possibly available");
            tryToGetAware();
        }
    }
}
