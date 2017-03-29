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

import android.annotation.NonNull;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * Tracks information about applications requesting use of the LocalOnlyHotspot.
 */
public class LocalOnlyHotspotRequestInfo implements IBinder.DeathRecipient {
    private final int mPid;
    private final int mUid;
    private final IBinder mBinder;
    private final RequestingApplicationDeathCallback mCallback;

    /**
     * Callback for use with LocalOnlyHotspot to unregister requesting applications upon death.
     */
    public interface RequestingApplicationDeathCallback {
        /**
         * Called when requesting app has died.
         */
        void onLocalOnlyHotspotRequestorDeath(LocalOnlyHotspotRequestInfo requestor);
    }

    LocalOnlyHotspotRequestInfo(@NonNull IBinder binder,
            @NonNull RequestingApplicationDeathCallback callback) {
        mPid = Binder.getCallingPid();
        mUid = Binder.getCallingUid();
        mBinder = binder;
        mCallback = callback;

        // if the args are invalid, there isn't much we can do.
        if (mBinder == null || mCallback == null) {
            throw new IllegalArgumentException(
                    "Cannot create a LocalOnlyHotspotRequest with null args");
        }

        try {
            mBinder.linkToDeath(this, 0);
        } catch (RemoteException e) {
            binderDied();
        }
    }

    void unlinkDeathRecipient() {
        if (mBinder != null) {
            mBinder.unlinkToDeath(this, 0);
        }
    }

    /**
     * Application requesting LocalOnlyHotspot died
     */
    @Override
    public void binderDied() {
        if (mCallback != null) {
            mCallback.onLocalOnlyHotspotRequestorDeath(this);
        }
    }

    public int getUid() {
        return mUid;
    }
}
