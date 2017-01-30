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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
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
import android.os.IHwBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Unit tests for SupplicantStaIfaceHal
 */
public class SupplicantStaIfaceHalTest {
    @Mock ISupplicantStaIface mISupplicantStaIfaceMock;
    @Mock WifiSupplicantHal.ServiceDiedListener mServiceDiedListenerMock;
    private final Object mLock = new Object();
    private SupplicantStaIfaceHal mDut;

    @Before
    public void setUp() throws Exception {
        mDut = new SupplicantStaIfaceHal(mLock);
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Sunny day scenario for WifiSupplicantHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_success() throws Exception {
    }
}
