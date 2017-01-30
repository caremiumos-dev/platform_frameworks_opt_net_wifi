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

import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.util.Log;

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
    private ISupplicantStaIface mISupplicantStaIface;
    private WifiSupplicantHal.ServiceDiedListener mServiceDiedListener;
    private final Object mLock;

    SupplicantStaIfaceHal(Object lock) {
        mLock = lock;
        reset();
    }

    /**
     * Sets the ISupplicantStaIface to be used for HAL functionality, and a listener to be called
     * whenever any StaIface commands cause a service failure.
     */
    public void initialize(ISupplicantStaIface iSupplicantStaIface,
            WifiSupplicantHal.ServiceDiedListener serviceDiedListener) {
        if (DBG) Log.i(TAG, "initialize");
        mISupplicantStaIface = iSupplicantStaIface;
        mServiceDiedListener = serviceDiedListener;
    }

    /**
     * Nulls the ISupplicantStaIface and death listener, call this when something goes wrong with
     * the ISupplicant service.
     */
    public void reset() {
        if (DBG) Log.i(TAG, "reset()");
        mISupplicantStaIface = null;
        mServiceDiedListener = null;
    }
}
