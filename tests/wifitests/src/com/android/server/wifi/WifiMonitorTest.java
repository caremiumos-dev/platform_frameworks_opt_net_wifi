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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback.WpsConfigError;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback.WpsErrorIndication;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;

/**
 * Unit tests for {@link com.android.server.wifi.WifiMonitor}.
 */
@SmallTest
public class WifiMonitorTest {
    private static final String WLAN_IFACE_NAME = "wlan0";
    private WifiMonitor mWifiMonitor;
    private TestLooper mLooper;
    private Handler mHandler;
    private Handler mHandlerSpy;

    @Before
    public void setUp() throws Exception {
        final Constructor<WifiMonitor> wifiMonitorConstructor =
                WifiMonitor.class.getDeclaredConstructor();
        wifiMonitorConstructor.setAccessible(true);
        mWifiMonitor = spy(wifiMonitorConstructor.newInstance());
        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        mHandlerSpy = spy(mHandler);
        mWifiMonitor.setMonitoring(WLAN_IFACE_NAME, true);
    }

    /**
     * Handle WPS failure event.
     */
    @Test
    public void testHandleWpsEventFailDueToErrorTkipOnlyProhibhited() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.NO_ERROR,
                WpsErrorIndication.SECURITY_TKIP_ONLY_PROHIBITED);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_TKIP_ONLY_PROHIBITED, messageCaptor.getValue().arg1);
    }

    /**
     * Handle WPS failure event.
     */
    @Test
    public void testHandleWpsEventFailDueToErrorWepProhibhited() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.NO_ERROR,
                WpsErrorIndication.SECURITY_WEP_PROHIBITED);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_WEP_PROHIBITED, messageCaptor.getValue().arg1);
    }

    /**
     * Handle WPS failure event.
     */
    @Test
    public void testHandleWpsEventFailDueToConfigAuthError() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.DEV_PASSWORD_AUTH_FAILURE,
                WpsErrorIndication.NO_ERROR);

        mLooper.dispatchAll();
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_AUTH_FAILURE, messageCaptor.getValue().arg1);
    }

    /**
     * Handle WPS failure event.
     */
    @Test
    public void testHandleWpsEventFailDueToConfigPbcOverlapError() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.MULTIPLE_PBC_DETECTED,
                WpsErrorIndication.NO_ERROR);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_OVERLAP_ERROR, messageCaptor.getValue().arg1);
    }

    /**
     * Handle WPS failure event.
     */
    @Test
    public void testHandleWpsEventFailDueToConfigError() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.MSG_TIMEOUT,
                WpsErrorIndication.NO_ERROR);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.ERROR, messageCaptor.getValue().arg1);
        assertEquals(WpsConfigError.MSG_TIMEOUT, messageCaptor.getValue().arg2);
    }

    /**
     * Handle WPS success event.
     */
    @Test
    public void testHandleWpsEventSuccess() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_SUCCESS_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsSuccessEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_SUCCESS_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Handle WPS overlap event.
     */
    @Test
    public void testHandleWpsEventOverlap() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_OVERLAP_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsOverlapEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_OVERLAP_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Handle WPS timeout event.
     */
    @Test
    public void testHandleWpsEventTimeout() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_TIMEOUT_EVENT, mHandlerSpy);
        mWifiMonitor.handleWpsTimeoutEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_TIMEOUT_EVENT, messageCaptor.getValue().what);
    }
}
