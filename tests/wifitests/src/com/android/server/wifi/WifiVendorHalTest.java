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

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.os.HandlerThread;
import android.os.Looper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


/**
 * Unit tests for {@link com.android.server.wifi.WifiVendorHal}.
 */
public class WifiVendorHalTest {

    WifiVendorHal mWifiVendorHal;
    private WifiStatus mWifiStatusSuccess;
    private WifiStatus mWifiStatusFailure;
    @Mock private HalDeviceManager mHalDeviceManager;
    @Mock private HandlerThread mWifiStateMachineHandlerThread;
    @Mock private WifiVendorHal.HalDeviceManagerStatusListener mHalDeviceManagerStatusCallbacks;
    @Mock private IWifiApIface mIWifiApIface;
    @Mock private IWifiChip mIWifiChip;
    @Mock private IWifiStaIface mIWifiStaIface;
    @Mock private IWifiRttController mIWifiRttController;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiStatusSuccess = new WifiStatus();
        mWifiStatusSuccess.code = WifiStatusCode.SUCCESS;
        mWifiStatusFailure = new WifiStatus();
        mWifiStatusFailure.code = WifiStatusCode.ERROR_UNKNOWN;
        mWifiStatusFailure.description = "I don't even know what a Mock Turtle is.";

        // Setup the HalDeviceManager mock's start/stop behaviour. This can be overridden in
        // individual tests, if needed.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(true);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
                return true;
            }
        }).when(mHalDeviceManager).start();

        doAnswer(new AnswerWithArguments() {
            public void answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(false);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
            }
        }).when(mHalDeviceManager).stop();
        when(mHalDeviceManager.createStaIface(eq(null), eq(null)))
                .thenReturn(mIWifiStaIface);
        when(mHalDeviceManager.createApIface(eq(null), eq(null)))
                .thenReturn(mIWifiApIface);
        when(mHalDeviceManager.getChip(any(IWifiIface.class)))
                .thenReturn(mIWifiChip);
        when(mHalDeviceManager.createRttController(any(IWifiIface.class)))
                .thenReturn(mIWifiRttController);

        // Create the vendor HAL object under test.
        mWifiVendorHal = new WifiVendorHal(mHalDeviceManager, mWifiStateMachineHandlerThread);

        // Initialize the vendor HAL to capture the registered callback.
        mWifiVendorHal.initialize();
        ArgumentCaptor<WifiVendorHal.HalDeviceManagerStatusListener> callbackCaptor =
                ArgumentCaptor.forClass(WifiVendorHal.HalDeviceManagerStatusListener.class);
        verify(mHalDeviceManager).registerStatusListener(
                callbackCaptor.capture(), any(Looper.class));
        mHalDeviceManagerStatusCallbacks = callbackCaptor.getValue();
    }

    /**
     * Test that parsing a typical colon-delimited MAC adddress works
     */
    @Test
    public void testTypicalHexParse() throws Exception {
        byte[] sixBytes = new byte[6];
        mWifiVendorHal.parseUnquotedMacStrToByteArray("61:52:43:34:25:16", sixBytes);
        Assert.assertArrayEquals(new byte[]{0x61, 0x52, 0x43, 0x34, 0x25, 0x16}, sixBytes);
    }

    /**
     * Tests the successful starting of HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalSuccessInStaMode() {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiStaIface));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the successful starting of HAL in AP mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalSuccessInApMode() {
        assertTrue(mWifiVendorHal.startVendorHal(false));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiApIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInStaMode() {
        // No callbacks are invoked in this case since the start itself failed. So, override
        // default AnswerWithArguments that we setup.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() {
                return false;
            }
        }).when(mHalDeviceManager).start();
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInIfaceCreationInStaMode() {
        when(mHalDeviceManager.createStaIface(eq(null), eq(null))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInRttControllerCreationInStaMode() {
        when(mHalDeviceManager.createRttController(any(IWifiIface.class))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInChipGetInStaMode() {
        when(mHalDeviceManager.getChip(any(IWifiIface.class))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInApMode() {
        when(mHalDeviceManager.createApIface(eq(null), eq(null))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(false));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the stopping of HAL in STA mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInStaMode() {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiStaIface));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the stopping of HAL in AP mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInApMode() {
        assertTrue(mWifiVendorHal.startVendorHal(false));
        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiApIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Test enablement of link layer stats after startup
     *
     * Start the HAL in STA mode
     *  - stats should not be enabled
     * Request that link layer stats be enabled
     *  - request should be noted
     * Request link layer stats
     *  - enable request should make it to the HAL layer
     *  - HAL layer should have been called to make the request
     */
    @Test
    public void testLinkLayerStatsEnableAfterStartup() throws Exception {
        when(mIWifiStaIface.enableLinkLayerStatsCollection(false)).thenReturn(mWifiStatusSuccess);
        when(mIWifiStaIface.enableLinkLayerStatsCollection(true)).thenReturn(mWifiStatusSuccess);
        doNothing().when(mIWifiStaIface).getLinkLayerStats(any());

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.isHalStarted());

        assertFalse(mWifiVendorHal.mLinkLayerStatsEnabled);
        assertFalse(mWifiVendorHal.mLinkLayerStatsReallyEnabled);
        mWifiVendorHal.setWifiLinkLayerStats("wlan0", 1);
        assertTrue(mWifiVendorHal.mLinkLayerStatsEnabled);
        assertFalse(mWifiVendorHal.mLinkLayerStatsReallyEnabled);
        mWifiVendorHal.getWifiLinkLayerStats("wlan0");
        assertTrue(mWifiVendorHal.mLinkLayerStatsEnabled);
        assertTrue(mWifiVendorHal.mLinkLayerStatsReallyEnabled);
        verify(mHalDeviceManager).start();
        verify(mIWifiStaIface).enableLinkLayerStatsCollection(false);
        verify(mIWifiStaIface).getLinkLayerStats(any());
    }

    // TODO(b/34900534) add test for correct MOVE CORRESPONDING of fields

    /**
     * Test a failure case for LinkLayerStats
     *
     * If the HAL says it cannot enable LinkLayerStats, don't really enable it.
     */
    @Test
    public void testLinkLayerStatsFailure() throws Exception {
        when(mIWifiStaIface.enableLinkLayerStatsCollection(false)).thenReturn(mWifiStatusFailure);
        doNothing().when(mIWifiStaIface).getLinkLayerStats(any());

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.isHalStarted());
        mWifiVendorHal.setWifiLinkLayerStats("wlan0", 1);
        mWifiVendorHal.getWifiLinkLayerStats("wlan0");
        assertFalse(mWifiVendorHal.mLinkLayerStatsReallyEnabled);

    }

}
