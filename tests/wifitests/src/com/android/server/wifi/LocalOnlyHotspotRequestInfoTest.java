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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/*
 * Unit tests for {@link com.android.server.wifi.LocalOnlyHotspotRequestInfo}.
 */
@SmallTest
public class LocalOnlyHotspotRequestInfoTest {

    private static final String TAG = "LocalOnlyHotspotRequestInfoTest";
    @Mock IBinder mAppBinder;
    @Mock LocalOnlyHotspotRequestInfo.RequestingApplicationDeathCallback mCallback;
    RemoteException mRemoteException;
    private LocalOnlyHotspotRequestInfo mLOHSRequestInfo;

    /**
     * Setup test.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mRemoteException = new RemoteException("Test Remote Exception");
    }

    /**
     * Make sure we link the call to request LocalOnlyHotspot by an app is linked to their Binder
     * call.  This allows us to clean up if the app dies.
     */
    @Test
    public void verifyBinderLinkToDeathIsCalled() throws Exception {
        mLOHSRequestInfo = new LocalOnlyHotspotRequestInfo(mAppBinder, mCallback);
        verify(mAppBinder).linkToDeath(eq(mLOHSRequestInfo), eq(0));
    }

    /**
     * Calls to create link the requestor to binder death should not pass null callback.
     */
    @Test(expected = IllegalArgumentException.class)
    public void verifyNullCallbackChecked() throws Exception {
        mLOHSRequestInfo = new LocalOnlyHotspotRequestInfo(mAppBinder, null);
    }

    /**
     * Calls to link the requestor to binder death should not pass null binder
     */
    @Test(expected = IllegalArgumentException.class)
    public void verifyNullBinderChecked() throws Exception {
        mLOHSRequestInfo = new LocalOnlyHotspotRequestInfo(null, mCallback);
    }

    /**
     * Calls to unlink the DeathRecipient should call to unlink from Binder.
     */
    @Test
    public void verifyUnlinkDeathRecipientUnlinksFromBinder() throws Exception {
        mLOHSRequestInfo = new LocalOnlyHotspotRequestInfo(mAppBinder, mCallback);
        mLOHSRequestInfo.unlinkDeathRecipient();
        verify(mAppBinder).unlinkToDeath(eq(mLOHSRequestInfo), eq(0));
    }

    /**
     * Binder death notification should trigger a callback on the requestor.
     */
    @Test
    public void verifyBinderDeathTriggersCallback() {
        mLOHSRequestInfo = new LocalOnlyHotspotRequestInfo(mAppBinder, mCallback);
        mLOHSRequestInfo.binderDied();
        verify(mCallback).onLocalOnlyHotspotRequestorDeath(eq(mLOHSRequestInfo));
    }

    /**
     * Verify a RemoteException when calling linkToDeath triggers the callback.
     */
    @Test
    public void verifyRemoteExceptionTriggersCallback() throws Exception {
        doThrow(mRemoteException).when(mAppBinder)
                .linkToDeath(any(IBinder.DeathRecipient.class), eq(0));
        mLOHSRequestInfo = new LocalOnlyHotspotRequestInfo(mAppBinder, mCallback);
        verify(mCallback).onLocalOnlyHotspotRequestorDeath(eq(mLOHSRequestInfo));
    }

    /**
     * Verify call to get the Binder UID does not crash and returns the UID of the test.
     */
    @Test
    public void verifyGetUid() {
        mLOHSRequestInfo = new LocalOnlyHotspotRequestInfo(mAppBinder, mCallback);
        assertEquals(Process.myUid(), mLOHSRequestInfo.getUid());
    }
}
