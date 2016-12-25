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

package com.android.server.wifi.aware;

import android.hardware.wifi.V1_0.IWifiNanIfaceEventCallback;
import android.hardware.wifi.V1_0.NanBeaconSdfPayloadInd;
import android.hardware.wifi.V1_0.NanCapabilities;
import android.hardware.wifi.V1_0.NanClusterEventInd;
import android.hardware.wifi.V1_0.NanDataPathConfirmInd;
import android.hardware.wifi.V1_0.NanDataPathRequestInd;
import android.hardware.wifi.V1_0.NanFollowupReceivedInd;
import android.hardware.wifi.V1_0.NanMatchInd;
import android.hardware.wifi.V1_0.WifiNanStatus;

/**
 * Translates Wi-Fi Aware callbacks from the HAL (HIDL) to the framework.
 *
 * Delegates the management of the NAN interface to WifiAwareNativeManager.
 */
public class WifiAwareNativeCallbacks extends IWifiNanIfaceEventCallback.Stub {
    private static final String TAG = "WifiAwareNativeCbs";
    private static final boolean DBG = false;
    private static final boolean VDBG = false; // STOPSHIP if true

    private final WifiAwareStateManager mWifiAwareStateManager;

    public WifiAwareNativeCallbacks(WifiAwareStateManager wifiAwareStateManager) {
        mWifiAwareStateManager = wifiAwareStateManager;
    }

    @Override
    public void notifyCapabilitiesResponse(short id, WifiNanStatus status,
            NanCapabilities capabilities) {
    }

    @Override
    public void notifyEnableResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyConfigResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyDisableResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyStartPublishResponse(short id, WifiNanStatus status, short publishId) {

    }

    @Override
    public void notifyStopPublishResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyStartSubscribeResponse(short id, WifiNanStatus status, short subscribeId) {

    }

    @Override
    public void notifyStopSubscribeResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyTransmitFollowupResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyCreateDataInterfaceResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyDeleteDataInterfaceResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyInitiateDataPathResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyRespondToDataPathIndicationResponse(short id, WifiNanStatus status,
            int ndpInstanceId) {

    }

    @Override
    public void notifyTerminateDataPathResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void notifyBeaconSdfPayloadResponse(short id, WifiNanStatus status) {

    }

    @Override
    public void eventClusterEvent(NanClusterEventInd event) {

    }

    @Override
    public void eventDisabled(WifiNanStatus status) {

    }

    @Override
    public void eventPublishTerminated(short sessionId, WifiNanStatus status) {

    }

    @Override
    public void eventSubscribeTerminated(short sessionId, WifiNanStatus status) {

    }

    @Override
    public void eventMatch(NanMatchInd event) {

    }

    @Override
    public void eventMatchExpired(short discoverySessionId, int peerId) {

    }

    @Override
    public void eventFollowupReceived(NanFollowupReceivedInd event) {

    }

    @Override
    public void eventTransmitFollowup(short id, WifiNanStatus status) {

    }

    @Override
    public void eventDataPathRequest(NanDataPathRequestInd event) {

    }

    @Override
    public void eventDataPathConfirm(NanDataPathConfirmInd event) {

    }

    @Override
    public void eventDataPathTerminated(int ndpInstanceId) {

    }

    @Override
    public void eventBeaconSdfPayload(NanBeaconSdfPayloadInd event) {

    }
}
