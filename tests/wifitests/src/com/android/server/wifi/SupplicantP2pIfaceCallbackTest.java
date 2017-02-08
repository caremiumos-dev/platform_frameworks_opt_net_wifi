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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pWfdInfo;

import org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for SupplicantP2pIfaceCallback
 */
public class SupplicantP2pIfaceCallbackTest {
    private static final String TAG = "SupplicantP2pIfaceCallbackTest";

    private String mIface = "test_p2p0";
    private WifiMonitor mMonitor;
    private SupplicantP2pIfaceCallback mDut;

    private byte[] mDeviceAddress1Bytes = { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 };
    private String mDeviceAddress1String = "00:11:22:33:44:55";
    private byte[] mDeviceAddress2Bytes = { 0x01, 0x12, 0x23, 0x34, 0x45, 0x56 };
    private String mDeviceAddress2String = "01:12:23:34:45:56";
    private byte[] mDeviceInfoBytes = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

    private class SupplicantP2pIfaceCallbackSpy extends SupplicantP2pIfaceCallback {
        SupplicantP2pIfaceCallbackSpy(String iface, WifiMonitor monitor) {
            super(iface, monitor);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mMonitor = mock(WifiMonitor.class);
        mDut = new SupplicantP2pIfaceCallbackSpy(mIface, mMonitor);
    }

    /**
     * Sunny day scenario for onDeviceFound call.
     */
    @Test
    public void testOnDeviceFound_success() throws Exception {
        byte[] fakePrimaryDeviceTypeBytes = { 0x01, 0x02, 0x03 };
        String fakePrimaryDeviceTypeString = "010203";
        String fakeDeviceName = "test device name";
        short fakeConfigMethods = 0x1234;
        byte fakeCapabilities = 123;
        int fakeGroupCapabilities = 456;

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pDevice device) {
                // NOTE: mDeviceAddress1Bytes seems to be ignored by
                // legacy implementation of WifiP2pDevice.
                assertEquals(iface, mIface);
                assertEquals(device.deviceName, fakeDeviceName);
                assertEquals(device.primaryDeviceType, fakePrimaryDeviceTypeString);
                assertEquals(device.deviceCapability, fakeCapabilities);
                assertEquals(device.groupCapability, fakeGroupCapabilities);
                assertEquals(device.wpsConfigMethodsSupported, fakeConfigMethods);
                assertEquals(device.deviceAddress, mDeviceAddress2String);
                assertEquals(device.status, WifiP2pDevice.AVAILABLE);
            }
        })
        .when(mMonitor).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);
    }

    /**
     * Failing scenarios for onDeviceFound call.
     */
    @Test
    public void testOnDeviceFound_invalidArguments() throws Exception {
        byte[] fakePrimaryDeviceTypeBytes = { 0x01, 0x02, 0x03 };
        String fakePrimaryDeviceTypeString = "010203";
        String fakeDeviceName = "test device name";
        short fakeConfigMethods = 0x1234;
        byte fakeCapabilities = 123;
        int fakeGroupCapabilities = 456;

        verify(mMonitor, never()).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));
        doThrow(new Exception())
                .when(mMonitor).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFound(
                null, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                null,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                null, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                null);
    }

}
