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

import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
/**
 * Listens for events from the wpa_supplicant server, and passes them on
 * to the {@link StateMachine} for handling. Runs in its own thread.
 *
 * @hide
 */
public class WifiMonitorNew {

    private static final boolean DBG = false;
    private static final String TAG = "WifiMonitorNew";

    private final ISupplicantStaIfaceCallback mSupplicantStaIfaceCallback =
            new SupplicantStaIfaceCallback();

    public ISupplicantStaIfaceCallback getSupplicantStaIfaceCallback() {
        return mSupplicantStaIfaceCallback;
    }

    /** ISupplicantStaIfaceCallback handlers */
    public class SupplicantStaIfaceCallback extends ISupplicantStaIfaceCallback.Stub {
        @Override
        public void onNetworkAdded(int id){
            // No Obvious WifiMonitor handler
            // BSS_ADDED maybe?
        }
        @Override
        public void onNetworkRemoved(int id) {
            // WifiMonitor BSS_REMOVED?
        }
        @Override
        public void onStateChanged(int newState, byte[/* 6 */] bssid, int id,
                java.util.ArrayList<Byte> ssid) {
            // TBD
        }
        @Override
        public void onAnqpQueryDone(byte[/* 6 */] macAddress,
                ISupplicantStaIfaceCallback.AnqpData data,
                ISupplicantStaIfaceCallback.Hs20AnqpData hs20Data) {
            // TBD
        }
        @Override
        public void onHs20IconQueryDone(byte[/* 6 */] macAddress, String fileName,
                java.util.ArrayList<Byte> data) {
            // TBD
        }
        @Override
        public void onHs20SubscriptionRemediation(byte osuMethod, String url) {
            // TBD
        }
        @Override
        public void onHs20DeauthImminentNotice(int reasonCode, int reAuthDelayInSec, String url) {
            // TBD
        }
        @Override
        public void onDisconnected(byte[/* 6 */] bssid, boolean locallyGenerated, int reasonCode) {
            // TBD
        }
        @Override
        public void onAssociationRejected(byte[/* 6 */] bssid, int statusCode) {
            // TBD
        }
    }
}
