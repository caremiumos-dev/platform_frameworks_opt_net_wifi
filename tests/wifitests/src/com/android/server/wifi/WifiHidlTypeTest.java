/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.hardware.wifi.V1_0.RttBw;
import android.hardware.wifi.V1_0.RttPeerType;
import android.hardware.wifi.V1_0.RttPreamble;
import android.hardware.wifi.V1_0.RttType;
import android.hardware.wifi.V1_0.WifiChannelWidthInMhz;
import android.net.wifi.RttManager;
import android.net.wifi.ScanResult;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/**
 * {@link com.android.server.wifi.WifiNativeNew}.
 * <p>
 * Just the vendor stuff.
 */
public class WifiHidlTypeTest {

    WifiNativeNew mWifiNativeNew;

    @Mock
    Context mContext;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * Cleans up after test
     */
    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test that the RttManager type codes match up with the Hidl versions
     */
    @Test
    public void rttManagerTypeMatchesHidl() {
        Assert.assertTrue("ONE_SIDED", RttManager.RTT_TYPE_ONE_SIDED == RttType.ONE_SIDED);
        Assert.assertTrue("TWO_SIDED", RttManager.RTT_TYPE_TWO_SIDED == RttType.TWO_SIDED);
    }

    /**
     * Test that the RttManager peer type codes match up with the Hidl versions
     */
    @Test
    public void rttManagerPeerTypeMatchesHidl() {
        Assert.assertTrue("AP", RttManager.RTT_PEER_TYPE_AP == RttPeerType.AP);
        Assert.assertTrue("STA", RttManager.RTT_PEER_TYPE_STA == RttPeerType.STA);
        Assert.assertTrue("P2P_GO", RttManager.RTT_PEER_P2P_GO == RttPeerType.P2P_GO);
        Assert.assertTrue("P2P_CLIENT", RttManager.RTT_PEER_P2P_CLIENT == RttPeerType.P2P_CLIENT);
        Assert.assertTrue("NAN", RttManager.RTT_PEER_NAN == RttPeerType.NAN);
    }

    /**
     * Test that the RttManager/ScanResult channel width codes match up with the Hidl versions
     */
    @Test
    public void scanResultChannelWidthMatchesHidl() {
        Assert.assertTrue("WIDTH_20",
                ScanResult.CHANNEL_WIDTH_20MHZ == WifiChannelWidthInMhz.WIDTH_20);
        Assert.assertTrue("WIDTH_40",
                ScanResult.CHANNEL_WIDTH_40MHZ == WifiChannelWidthInMhz.WIDTH_40);
        Assert.assertTrue("WIDTH_80",
                ScanResult.CHANNEL_WIDTH_80MHZ == WifiChannelWidthInMhz.WIDTH_80);
        Assert.assertTrue("WIDTH_160",
                ScanResult.CHANNEL_WIDTH_160MHZ == WifiChannelWidthInMhz.WIDTH_160);
        Assert.assertTrue("WIDTH_80P80",
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ == WifiChannelWidthInMhz.WIDTH_80P80);
//        Assert.assertTrue("WIDTH_5",
//                ScanResult.CHANNEL_WIDTH_5MHZ == WifiChannelWidthInMhz.WIDTH_5);
//        Assert.assertTrue("WIDTH_10",
//                ScanResult.CHANNEL_WIDTH_10MHZ == WifiChannelWidthInMhz.WIDTH_10);
//        Assert.assertTrue("WIDTH_INVALID",
//                ScanResult.CHANNEL_WIDTH_INVALID == WifiChannelWidthInMhz.WIDTH_INVALID);
    }

    /**
     * Test that the RttManager bandwidth codes match up with the Hidl versions
     */
    @Test
    public void rttManagerRttBwMatchesHidl() {
        Assert.assertTrue("BW_5MHZ", RttManager.RTT_BW_5_SUPPORT == RttBw.BW_5MHZ);
        Assert.assertTrue("BW_10MHZ", RttManager.RTT_BW_10_SUPPORT == RttBw.BW_10MHZ);
        Assert.assertTrue("BW_20MHZ", RttManager.RTT_BW_20_SUPPORT == RttBw.BW_20MHZ);
        Assert.assertTrue("BW_40MHZ", RttManager.RTT_BW_40_SUPPORT == RttBw.BW_40MHZ);
        Assert.assertTrue("BW_80MHZ", RttManager.RTT_BW_80_SUPPORT == RttBw.BW_80MHZ);
        Assert.assertTrue("BW_160MHZ", RttManager.RTT_BW_160_SUPPORT == RttBw.BW_160MHZ);
    }

    /**
     * Test that the RttManager preamble codes match up with the Hidl versions
     */
    @Test
    public void rttManagerRttPreambleMatchesHidl() {
        Assert.assertTrue("LEGACY", RttManager.PREAMBLE_LEGACY == RttPreamble.LEGACY);
        Assert.assertTrue("HT", RttManager.PREAMBLE_HT == RttPreamble.HT);
        Assert.assertTrue("VHT", RttManager.PREAMBLE_VHT == RttPreamble.VHT);
    }
}
