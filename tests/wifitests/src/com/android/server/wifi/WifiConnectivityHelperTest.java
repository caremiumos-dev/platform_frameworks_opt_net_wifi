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

import static android.net.wifi.WifiManager.WIFI_FEATURE_CONTROL_ROAMING;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConnectivityHelper}.
 */
@SmallTest
public class WifiConnectivityHelperTest {
    /** Sets up test. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setupWifiNative();

        mWifiConnectivityHelper = new WifiConnectivityHelper(mWifiNative);
    }

    /** Cleans up test. */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    private WifiConnectivityHelper mWifiConnectivityHelper;
    @Mock private WifiNative mWifiNative;
    private int mFeatureSetValue;
    private static final String TAG = "WifiConnectivityHelper Unit Test";
    private static final int MAX_BSSID_BLACKLIST_SIZE = 16;
    private static final int MAX_SSID_WHITELIST_SIZE = 8;

    private void setupWifiNative() {
        // Return firmware roaming feature as supported by default.
        when(mWifiNative.getSupportedFeatureSet()).thenReturn(WIFI_FEATURE_CONTROL_ROAMING);

        doAnswer(new AnswerWithArguments() {
            public boolean answer(WifiNative.RoamingCapabilities roamCap) throws Exception {
                roamCap.maxBlacklistSize = MAX_BSSID_BLACKLIST_SIZE;
                roamCap.maxWhitelistSize = MAX_SSID_WHITELIST_SIZE;
                return true;
            }}).when(mWifiNative).getRoamingCapabilities(anyObject());

        when(mWifiNative.configureRoaming(anyObject())).thenReturn(true);
    }

    private ArrayList<String> buildBssidBlacklist(int size) {
        ArrayList<String> bssidBlacklist = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            StringBuilder bssid = new StringBuilder("11:22:33:44:55:66");
            bssid.setCharAt(16, (char) ('0' + i));
            bssidBlacklist.add(bssid.toString());
        }

        return bssidBlacklist;
    }

    private ArrayList<String> buildSsidWhitelist(int size) {
        ArrayList<String> ssidWhitelist = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            StringBuilder ssid = new StringBuilder("Test_Ap_0");
            ssid.setCharAt(8, (char) ('0' + i));
            ssidWhitelist.add(ssid.toString());
        }

        return ssidWhitelist;
    }

    /**
     * When WifiNative has WIFI_FEATURE_CONTROL_ROAMING set, verify that
     * WifiConnectivityHelper#isFirmwareRoamingSupported returns true.
     */
    @Test
    public void returnFirmwareRoamingSupported() {
        //By default WifiNative has WIFI_FEATURE_CONTROL_ROAMING set in its feature set.
        mWifiConnectivityHelper.getFirmwareRoamingInfo();
        assertTrue(mWifiConnectivityHelper.isFirmwareRoamingSupported());
    }

    /**
     * When WifiNative doesn't have WIFI_FEATURE_CONTROL_ROAMING set, verify that
     * WifiConnectivityHelper#isFirmwareRoamingSupported returns false.
     */
    @Test
    public void returnFirmwareRoamingNotSupported() {
        when(mWifiNative.getSupportedFeatureSet()).thenReturn(~WIFI_FEATURE_CONTROL_ROAMING);
        mWifiConnectivityHelper.getFirmwareRoamingInfo();
        assertFalse(mWifiConnectivityHelper.isFirmwareRoamingSupported());
    }

    /**
     * Verify that correct firmware roaming capability values are returned if querying
     * WifiNative for roaming capability succeeded.
     */
    @Test
    public void verifyFirmwareRoamingCapabilityWithSuccessfulNativeCall() {
        mWifiConnectivityHelper.getFirmwareRoamingInfo();
        assertEquals(MAX_BSSID_BLACKLIST_SIZE, mWifiConnectivityHelper.getMaxNumBlacklistBssid());
        assertEquals(MAX_SSID_WHITELIST_SIZE, mWifiConnectivityHelper.getMaxNumWhitelistSsid());
    }

    /**
     * Verify that uninitialized firmware roaming capability values are returned if querying
     * WifiNative for roaming capability failed.
     */
    @Test
    public void verifyFirmwareRoamingCapabilityWithFailureNativeCall() {
        doAnswer(new AnswerWithArguments() {
            public boolean answer(WifiNative.RoamingCapabilities roamCap) throws Exception {
                roamCap.maxBlacklistSize = -1;
                roamCap.maxWhitelistSize = -1;
                return false;
            }}).when(mWifiNative).getRoamingCapabilities(anyObject());
        mWifiConnectivityHelper.getFirmwareRoamingInfo();
        assertEquals(-1, mWifiConnectivityHelper.getMaxNumBlacklistBssid());
        assertEquals(-1, mWifiConnectivityHelper.getMaxNumWhitelistSsid());
    }

    /**
     * Verify that correct size BSSID blacklist and SSID whitelist are accepted.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithGoodInput() {
        mWifiConnectivityHelper.getFirmwareRoamingInfo();
        ArrayList<String> blacklist = buildBssidBlacklist(MAX_BSSID_BLACKLIST_SIZE);
        ArrayList<String> whitelist = buildSsidWhitelist(MAX_SSID_WHITELIST_SIZE);
        assertTrue(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blacklist, whitelist));
    }

    /**
     * Verify that incorrect size BSSID blacklist is rejected.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithIncorrectBlacklist() {
        mWifiConnectivityHelper.getFirmwareRoamingInfo();
        ArrayList<String> blacklist = buildBssidBlacklist(MAX_BSSID_BLACKLIST_SIZE + 1);
        ArrayList<String> whitelist = buildSsidWhitelist(MAX_SSID_WHITELIST_SIZE);
        assertFalse(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blacklist, whitelist));
    }

    /**
     * Verify that incorrect size SSID whitelist is rejected.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithIncorrectWhitelist() {
        mWifiConnectivityHelper.getFirmwareRoamingInfo();
        ArrayList<String> blacklist = buildBssidBlacklist(MAX_BSSID_BLACKLIST_SIZE);
        ArrayList<String> whitelist = buildSsidWhitelist(MAX_SSID_WHITELIST_SIZE + 1);
        assertFalse(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blacklist, whitelist));
    }
}
