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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.IWificond;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link com.android.server.wifi.WificondControl}.
 */
@SmallTest
public class WificondControlTest {
    private WificondControl mWificondControl;

    @Before
    public void setUp() throws Exception {
        mWificondControl = new WificondControl();
    }

    /**
     * Verifies that setupDriverForClientMode() calls underlying WificondControl.
     */
    @Test
    public void testSetupDriverForClientMode() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(wificond.createClientInterface()).thenReturn(clientInterface);

        IClientInterface returnedClientInterface =
                mWificondControl.setupDriverForClientMode(wificond);
        assertEquals(clientInterface, returnedClientInterface);
        verify(wificond).createClientInterface();
    }

    /**
     * Verifies that setupDriverForSoftApMode() calls underlying WificondControl.
     */
    @Test
    public void testSetupDriverForSoftApMode() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IApInterface apInterface = mock(IApInterface.class);

        when(wificond.createApInterface()).thenReturn(apInterface);

        IApInterface returnedApInterface =
                mWificondControl.setupDriverForSoftApMode(wificond);
        assertEquals(apInterface, returnedApInterface);
        verify(wificond).createApInterface();
    }
}
