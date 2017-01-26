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

class WificondControl {

    private static final String TAG = "WificondControl";
    private IWificond mWificond;
    private IClientInterface mClientInterface;
    private IApInterface mApInterface;

    WificondControl() {
    }

    public IClientInterface setupDriverForClientMode(IWificond wificond) {
        if (wificond == null) {
            Log.e(TAG, "Failed to get reference to wificond");
            return null;
        }

        IClientInterface clientInterface = null;
        try {
            clientInterface = wificond.createClientInterface();
        } catch (RemoteException e1) { }

        if (clientInterface == null) {
            Log.e(TAG, "Could not get IClientInterface instance from wificond");
            return null;
        } else {
            Binder.allowBlocking(clientInterface.asBinder());
        }

        // Refresh Handlers
        mWificond = wificond;
        mClientInterface = clientInterface;

        return clientInterface;
    }

    public IApInterface setupDriverForSoftApMode(IWificond wificond) {
        if (wificond == null) {
            Log.e(TAG, "Failed to get reference to wificond");
            return null;
        }

        IApInterface apInterface = null;
        try {
            apInterface = wificond.createApInterface();
        } catch (RemoteException e1) { }

        if (apInterface == null) {
            Log.e(TAG, "Could not get IApInterface instance from wificond");
            return null;
        } else {
            Binder.allowBlocking(apInterface.asBinder());
        }

        // Refresh Handlers
        mWificond = wificond;
        mApInterface = apInterface;

        return apInterface;
    }

}
