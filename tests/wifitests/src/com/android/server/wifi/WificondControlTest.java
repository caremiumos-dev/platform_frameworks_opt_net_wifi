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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.IWificond;
import android.net.wifi.wificond.NativeScanResult;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link com.android.server.wifi.WificondControl}.
 */
@SmallTest
public class WificondControlTest {
    private WifiInjector mWifiInjector;
    private WificondControl mWificondControl;
    private static final byte[] TEST_SSID =
            new byte[] {'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    private static final byte[] TEST_BSSID =
            new byte[] {(byte) 0x12, (byte) 0xef, (byte) 0xa1,
                        (byte) 0x2c, (byte) 0x97, (byte) 0x8b};
    private static final byte[] TEST_INFO_ELEMENT =
            new byte[] {(byte) 0x01, (byte) 0x03, (byte) 0x12, (byte) 0xbe, (byte) 0xff};
    private static final int TEST_FREQUENCY = 2456;
    private static final int TEST_SIGNAL_MBM = -45;
    private static final long TEST_TSF = 34455441;
    private static final BitSet TEST_CAPABILITY = new BitSet(16) {{ set(2); set(5); }};
    private static final boolean TEST_ASSOCIATED = true;
    private static final NativeScanResult MOCK_NATIVE_SCAN_RESULT = new NativeScanResult() {{
    }}

    @Before
    public void setUp() throws Exception {
        mWifiInjector = mock(WifiInjector.class);
        mWificondControl = new WificondControl(mWifiInjector);
    }

    /**
     * Verifies that setupDriverForClientMode() calls Wificond.
     */
    @Test
    public void testSetupDriverForClientMode() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);
        verify(wificond).createClientInterface();
    }

    /**
     * Verifies that setupDriverForClientMode() returns null when wificond is not started.
     */
    @Test
    public void testSetupDriverForClientModeErrorWhenWificondIsNotStarted() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);

        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(null, returnedClientInterface);
    }

    /**
     * Verifies that setupDriverForClientMode() returns null when wificond failed to setup client
     * interface.
     */
    @Test
    public void testSetupDriverForClientModeErrorWhenWificondFailedToSetupInterface()
            throws Exception {
        IWificond wificond = mock(IWificond.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(null);

        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(null, returnedClientInterface);
    }

    /**
     * Verifies that setupDriverForSoftApMode() calls wificond.
     */
    @Test
    public void testSetupDriverForSoftApMode() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IApInterface apInterface = mock(IApInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createApInterface()).thenReturn(apInterface);

        IApInterface returnedApInterface = mWificondControl.setupDriverForSoftApMode();
        assertEquals(apInterface, returnedApInterface);
        verify(wificond).createApInterface();
    }

    /**
     * Verifies that setupDriverForSoftAp() returns null when wificond is not started.
     */
    @Test
    public void testSetupDriverForSoftApModeErrorWhenWificondIsNotStarted() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);

        IApInterface returnedApInterface = mWificondControl.setupDriverForSoftApMode();

        assertEquals(null, returnedApInterface);
    }

    /**
     * Verifies that setupDriverForSoftApMode() returns null when wificond failed to setup
     * AP interface.
     */
    @Test
    public void testSetupDriverForSoftApModeErrorWhenWificondFailedToSetupInterface()
            throws Exception {
        IWificond wificond = mock(IWificond.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createApInterface()).thenReturn(null);

        IApInterface returnedApInterface = mWificondControl.setupDriverForSoftApMode();
        assertEquals(null, returnedApInterface);
    }

    /**
     * Verifies that enableSupplicant() calls wificond.
     */
    @Test
    public void testEnableSupplicant() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);
        when(clientInterface.enableSupplicant()).thenReturn(true);

        mWificondControl.setupDriverForClientMode();
        assertTrue(mWificondControl.enableSupplicant());
        verify(clientInterface).enableSupplicant();
    }

    /**
     * Verifies that enableSupplicant() returns false when there is no configured
     * client interface.
     */
    @Test
    public void testEnableSupplicantErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Enabling supplicant should fail.
        assertFalse(mWificondControl.enableSupplicant());
    }

    /**
     * Verifies that disableSupplicant() calls wificond.
     */
    @Test
    public void testDisableSupplicant() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);
        when(clientInterface.disableSupplicant()).thenReturn(true);

        mWificondControl.setupDriverForClientMode();
        assertTrue(mWificondControl.disableSupplicant());
        verify(clientInterface).disableSupplicant();
    }

    /**
     * Verifies that disableSupplicant() returns false when there is no configured
     * client interface.
     */
    @Test
    public void testDisableSupplicantErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Disabling supplicant should fail.
        assertFalse(mWificondControl.disableSupplicant());
    }

    /**
     * Verifies that tearDownInterfaces() calls wificond.
     */
    @Test
    public void testTearDownInterfaces() throws Exception {
        IWificond wificond = mock(IWificond.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);

        assertTrue(mWificondControl.tearDownInterfaces());
        verify(wificond).tearDownInterfaces();
    }

    /**
     * Verifies that tearDownInterfaces() returns false when wificond is not started.
     */
    @Test
    public void testTearDownInterfacesErrorWhenWificondIsNotStarterd() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);

        assertFalse(mWificondControl.tearDownInterfaces());
    }

    /**
     * Verifies that signalPoll() calls wificond.
     */
    @Test
    public void testSignalPoll() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        mWificondControl.setupDriverForClientMode();
        mWificondControl.signalPoll();
        verify(clientInterface).signalPoll();
    }

    /**
     * Verifies that signalPoll() returns null when there is no configured client interface.
     */
    @Test
    public void testSignalPollErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Signal poll should fail.
        assertEquals(null, mWificondControl.signalPoll());
    }

    /**
     * Verifies that getTxPacketCounters() calls wificond.
     */
    @Test
    public void testGetTxPacketCounters() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        mWificondControl.setupDriverForClientMode();
        mWificondControl.getTxPacketCounters();
        verify(clientInterface).getPacketCounters();
    }

    /**
     * Verifies that getTxPacketCounters() returns null when there is no configured client
     * interface.
     */
    @Test
    public void testGetTxPacketCountersErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Signal poll should fail.
        assertEquals(null, mWificondControl.getTxPacketCounters());
    }

    /**
     * Verifies that getScanResults() returns null when there is no configured client
     * interface.
     */
    @Test
    public void testGetScanResultsErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // getScanResults should fail.
        assertEquals(0, mWificondControl.getScanResults().size());
    }

    /**
     * Verifies that getScanResults() can parse NativeScanResult from wificond correctly,
     */
    @Test
    public void testGetScanResults() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);
        when(clientInterface.get).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);
    }

}
