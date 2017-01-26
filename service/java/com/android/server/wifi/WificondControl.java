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

import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.IWificond;
import android.os.Binder;
import android.os.RemoteException;
import android.util.Log;

/**
 * This class provides methods for WifiNative to send control commands to wificond.
 * NOTE: This class should only be used from WifiNative.
 */
public class WificondControl {

    private static final String TAG = "WificondControl";
    private IWificond mWificond;
    private IClientInterface mClientInterface;
    private IApInterface mApInterface;

    WificondControl() {
    }

    /**
    * Setup driver for client mode via wificond.
    * @param wificond - Wificond binder handler.
    * @return An IClientInterface as wificond client interface binder handler.
    * Returns null on failure.
    */
    public IClientInterface setupDriverForClientMode(IWificond wificond) {
        if (wificond == null) {
            Log.e(TAG, "Invalid parameter: reference to wificond is null");
            return null;
        }

        IClientInterface clientInterface = null;
        try {
            clientInterface = wificond.createClientInterface();
        } catch (RemoteException e1) { }

        if (clientInterface == null) {
            Log.e(TAG, "Could not get IClientInterface instance from wificond");
            return null;
        }
        Binder.allowBlocking(clientInterface.asBinder());

        // Refresh Handlers
        mWificond = wificond;
        mClientInterface = clientInterface;

        return clientInterface;
    }

    /**
    * Setup driver for softAp mode via wificond.
    * @param wificond - Wificond binder handler.
    * @return An IApInterface as wificond Ap interface binder handler.
    * Returns null on failure.
    */
    public IApInterface setupDriverForSoftApMode(IWificond wificond) {
        if (wificond == null) {
            Log.e(TAG, "Invalid parameter: reference to wificond is null");
            return null;
        }

        IApInterface apInterface = null;
        try {
            apInterface = wificond.createApInterface();
        } catch (RemoteException e1) { }

        if (apInterface == null) {
            Log.e(TAG, "Could not get IApInterface instance from wificond");
            return null;
        }
        Binder.allowBlocking(apInterface.asBinder());

        // Refresh Handlers
        mWificond = wificond;
        mApInterface = apInterface;

        return apInterface;
    }

}
