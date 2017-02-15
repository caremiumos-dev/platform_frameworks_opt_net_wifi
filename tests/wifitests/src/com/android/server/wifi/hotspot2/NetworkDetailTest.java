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

package com.android.server.wifi.hotspot2;

import static org.junit.Assert.assertTrue;

import android.net.wifi.ScanResult;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link com.android.server.wifi.hotspot2.NetworkDetail}.
 *
 */
@SmallTest
public class NetworkDetailTest {
    private static final String TEST_BSSID = "11:22:33:44:55:66";

    @Test
    public void getTrimmedSsidNullSsid() throws Exception {
        NetworkDetail networkDetail =
                new NetworkDetail(TEST_BSSID, new ScanResult.InformationElement[0], null, 2412);
        assertTrue(networkDetail.getTrimmedSSID().isEmpty());
    }
}
