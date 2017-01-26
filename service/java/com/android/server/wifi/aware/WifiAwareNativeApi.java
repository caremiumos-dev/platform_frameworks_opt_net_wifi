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

import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IWifiNanIfaceEventCallback;
import android.hardware.wifi.V1_0.NanBandIndex;
import android.hardware.wifi.V1_0.NanBeaconSdfPayloadInd;
import android.hardware.wifi.V1_0.NanCapabilities;
import android.hardware.wifi.V1_0.NanClusterEventInd;
import android.hardware.wifi.V1_0.NanClusterEventType;
import android.hardware.wifi.V1_0.NanConfigRequest;
import android.hardware.wifi.V1_0.NanDataPathConfirmInd;
import android.hardware.wifi.V1_0.NanDataPathRequestInd;
import android.hardware.wifi.V1_0.NanEnableRequest;
import android.hardware.wifi.V1_0.NanFollowupReceivedInd;
import android.hardware.wifi.V1_0.NanInitiateDataPathRequest;
import android.hardware.wifi.V1_0.NanMatchInd;
import android.hardware.wifi.V1_0.NanPublishRequest;
import android.hardware.wifi.V1_0.NanRespondToDataPathIndicationRequest;
import android.hardware.wifi.V1_0.NanStatusType;
import android.hardware.wifi.V1_0.NanSubscribeRequest;
import android.hardware.wifi.V1_0.NanTransmitFollowupRequest;
import android.hardware.wifi.V1_0.NanTxType;
import android.hardware.wifi.V1_0.WifiNanStatus;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.os.RemoteException;
import android.util.Log;

import libcore.util.HexEncoding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Translates Wi-Fi Aware requests from the framework to the HAL (HIDL).
 *
 * Delegates the management of the NAN interface to WifiAwareNativeManager.
 */
public class WifiAwareNativeApi extends IWifiNanIfaceEventCallback.Stub {
    private static final String TAG = "WifiAwareNativeApi";
    private static final boolean DBG = false;
    private static final boolean VDBG = false; // STOPSHIP if true

    private WifiAwareNativeManager mHal;
    private WifiAwareStateManager mWifiAwareStateManager;

    public WifiAwareNativeApi() {
        // empty
    }

    /* package */ void setRefs(WifiAwareNativeManager wifiAwareNativeManager,
            WifiAwareStateManager wifiAwareStateManager) {
        mHal = wifiAwareNativeManager;
        mWifiAwareStateManager = wifiAwareStateManager;
    }

    /**
     * Query the firmware's capabilities.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     */
    public boolean getCapabilities(short transactionId) {
        if (VDBG) Log.v(TAG, "getCapabilities: transactionId=" + transactionId);

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "getCapabilities: null interface");
            return false;
        }

        try {
            WifiStatus status = iface.getCapabilitiesRequest(transactionId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "getCapabilities: error: " + status.code + "(" + status.description
                        + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "getCapabilities: exception: " + e);
            return false;
        }
    }

    /**
     * Enable and configure Aware.
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param configRequest Requested Aware configuration.
     * @param initialConfiguration Specifies whether initial configuration
     *            (true) or an update (false) to the configuration.
     */
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean initialConfiguration) {
        if (VDBG) {
            Log.v(TAG, "enableAndConfigure: transactionId=" + transactionId + ", configRequest="
                    + configRequest + ", initialConfiguration=" + initialConfiguration);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "enableAndConfigure: null interface");
            return false;
        }

        try {
            WifiStatus status;
            if (initialConfiguration) {
                // translate framework to HIDL configuration
                NanEnableRequest req = new NanEnableRequest();
                req.debugConfigs.clusterIdHighVal = (short) configRequest.mClusterHigh;
                req.debugConfigs.clusterIdLowVal = (short) configRequest.mClusterLow;
                req.configParams.masterPref = (byte) configRequest.mMasterPreference;
                req.operateInBand.set(NanBandIndex.NAN_BAND_5GHZ, configRequest.mSupport5gBand);

                status = iface.enableRequest(transactionId, req);
            } else {
                NanConfigRequest req = new NanConfigRequest();
                req.masterPref = (byte) configRequest.mMasterPreference;

                status = iface.configRequest(transactionId, req);
            }
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "enableAndConfigure: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "enableAndConfigure: exception: " + e);
            return false;
        }
    }

    /**
     * Disable Aware.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     */
    public boolean disable(short transactionId) {
        if (VDBG) Log.d(TAG, "disable");

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "disable: null interface");
            return false;
        }

        try {
            WifiStatus status = iface.disableRequest(transactionId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "disable: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "disable: exception: " + e);
            return false;
        }
    }

    /**
     * Start or modify a service publish session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param publishId ID of the requested session - 0 to request a new publish
     *            session.
     * @param publishConfig Configuration of the discovery session.
     */
    public boolean publish(short transactionId, int publishId, PublishConfig publishConfig) {
        if (VDBG) {
            Log.d(TAG, "publish: transactionId=" + transactionId + ", config=" + publishConfig);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "publish: null interface");
            return false;
        }

        NanPublishRequest req = new NanPublishRequest();
        req.baseConfigs.sessionId = 0;
        req.baseConfigs.ttlSec = (short) publishConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1; // TODO: DW configuration
        req.baseConfigs.discoveryCount = (byte) publishConfig.mPublishCount;
        req.baseConfigs.serviceName = new String(publishConfig.mServiceName,
                StandardCharsets.UTF_8); // TODO: consider keeping as String in framework
        convertLcByteToUcByteArray(publishConfig.mServiceSpecificInfo,
                req.baseConfigs.serviceSpecificInfo);
        convertLcByteToUcByteArray(publishConfig.mMatchFilter,
                publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED
                        ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !publishConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = true;
        req.baseConfigs.disableFollowupReceivedIndication = false;
        req.publishType = publishConfig.mPublishType;
        req.txType = NanTxType.BROADCAST;

        try {
            WifiStatus status = iface.startPublishRequest(transactionId, req);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "publish: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "publish: exception: " + e);
            return false;
        }
    }

    /**
     * Start or modify a service subscription session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param subscribeId ID of the requested session - 0 to request a new
     *            subscribe session.
     * @param subscribeConfig Configuration of the discovery session.
     */
    public boolean subscribe(short transactionId, int subscribeId,
            SubscribeConfig subscribeConfig) {
        if (VDBG) {
            Log.d(TAG, "subscribe: transactionId=" + transactionId + ", config=" + subscribeConfig);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "subscribe: null interface");
            return false;
        }

        NanSubscribeRequest req = new NanSubscribeRequest();
        req.baseConfigs.sessionId = 0;
        req.baseConfigs.ttlSec = (short) subscribeConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1; // TODO: DW configuration
        req.baseConfigs.discoveryCount = (byte) subscribeConfig.mSubscribeCount;
        req.baseConfigs.serviceName = new String(subscribeConfig.mServiceName,
                StandardCharsets.UTF_8); // TODO: consider keeping as String in framework
        convertLcByteToUcByteArray(subscribeConfig.mServiceSpecificInfo,
                req.baseConfigs.serviceSpecificInfo);
        convertLcByteToUcByteArray(subscribeConfig.mMatchFilter,
                subscribeConfig.mSubscribeType == SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE
                        ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !subscribeConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = true;
        req.baseConfigs.disableFollowupReceivedIndication = false;
        req.subscribeType = subscribeConfig.mSubscribeType;

        try {
            WifiStatus status = iface.startSubscribeRequest(transactionId, req);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "subscribe: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "subscribe: exception: " + e);
            return false;
        }
    }

    /**
     * Send a message through an existing discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @param requestorInstanceId ID of the peer to communicate with - obtained
     *            through a previous discovery (match) operation with that peer.
     * @param dest MAC address of the peer to communicate with - obtained
     *            together with requestorInstanceId.
     * @param message Message.
     * @param messageId Arbitary integer from host (not sent to HAL - useful for
     *                  testing/debugging at this level)
     */
    public boolean sendMessage(short transactionId, int pubSubId, int requestorInstanceId,
            byte[] dest, byte[] message, int messageId) {
        if (VDBG) {
            Log.d(TAG,
                    "sendMessage: transactionId=" + transactionId + ", pubSubId=" + pubSubId
                            + ", requestorInstanceId=" + requestorInstanceId + ", dest="
                            + String.valueOf(HexEncoding.encode(dest)) + ", messageId="
                            + messageId);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "sendMessage: null interface");
            return false;
        }

        NanTransmitFollowupRequest req = new NanTransmitFollowupRequest();
        req.discoverySessionId = (short) pubSubId;
        req.peerId = requestorInstanceId;
        copyArray(dest, req.addr);
        req.isHighPriority = false;
        req.shouldUseDiscoveryWindow = true;
        convertLcByteToUcByteArray(message, req.message);
        req.disableFollowupResultIndication = false;

        try {
            WifiStatus status = iface.transmitFollowupRequest(transactionId, req);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "sendMessage: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "sendMessage: exception: " + e);
            return false;
        }
    }

    /**
     * Terminate a publish discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    public boolean stopPublish(short transactionId, int pubSubId) {
        if (VDBG) {
            Log.d(TAG, "stopPublish: transactionId=" + transactionId + ", pubSubId=" + pubSubId);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "stopPublish: null interface");
            return false;
        }

        try {
            WifiStatus status = iface.stopPublishRequest(transactionId, (short) pubSubId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "stopPublish: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "stopPublish: exception: " + e);
            return false;
        }
    }

    /**
     * Terminate a subscribe discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    public boolean stopSubscribe(short transactionId, int pubSubId) {
        if (VDBG) {
            Log.d(TAG, "stopSubscribe: transactionId=" + transactionId + ", pubSubId=" + pubSubId);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "stopSubscribe: null interface");
            return false;
        }

        try {
            WifiStatus status = iface.stopSubscribeRequest(transactionId, (short) pubSubId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "stopSubscribe: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "stopSubscribe: exception: " + e);
            return false;
        }
    }

    /**
     * Create a Aware network interface. This only creates the Linux interface - it doesn't actually
     * create the data connection.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        if (VDBG) {
            Log.v(TAG, "createAwareNetworkInterface: transactionId=" + transactionId + ", "
                    + "interfaceName=" + interfaceName);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "createAwareNetworkInterface: null interface");
            return false;
        }

        try {
            WifiStatus status = iface.createDataInterfaceRequest(transactionId, interfaceName);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "createAwareNetworkInterface: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "createAwareNetworkInterface: exception: " + e);
            return false;
        }
    }

    /**
     * Deletes a Aware network interface. The data connection can (should?) be torn down previously.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        if (VDBG) {
            Log.v(TAG, "deleteAwareNetworkInterface: transactionId=" + transactionId + ", "
                    + "interfaceName=" + interfaceName);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "deleteAwareNetworkInterface: null interface");
            return false;
        }

        try {
            WifiStatus status = iface.deleteDataInterfaceRequest(transactionId, interfaceName);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "deleteAwareNetworkInterface: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "deleteAwareNetworkInterface: exception: " + e);
            return false;
        }
    }

    /**
     * Initiates setting up a data-path between device and peer.
     *
     * @param transactionId      Transaction ID for the transaction - used in the async callback to
     *                           match with the original request.
     * @param peerId             ID of the peer ID to associate the data path with. A value of 0
     *                           indicates that not associated with an existing session.
     * @param channelRequestType Indicates whether the specified channel is available, if available
     *                           requested or forced (resulting in failure if cannot be
     *                           accommodated).
     * @param channel            The channel on which to set up the data-path.
     * @param peer               The MAC address of the peer to create a connection with.
     * @param interfaceName      The interface on which to create the data connection.
     * @param message An arbitrary byte array to forward to the peer as part of the data path
     *                request.
     */
    public boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, byte[] peer, String interfaceName, byte[] message) {
        if (VDBG) {
            Log.v(TAG, "initiateDataPath: transactionId=" + transactionId + ", peerId=" + peerId
                    + ", channelRequestType=" + channelRequestType + ", channel=" + channel
                    + ", peer=" + String.valueOf(HexEncoding.encode(peer)) + ", interfaceName="
                    + interfaceName);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "initiateDataPath: null interface");
            return false;
        }

        NanInitiateDataPathRequest req = new NanInitiateDataPathRequest();
        req.peerId = peerId;
        copyArray(peer, req.peerDiscMacAddr);
        req.channelRequestType = channelRequestType;
        req.channel = channel;
        req.ifaceName = interfaceName;
        convertLcByteToUcByteArray(message, req.appInfo);

        try {
            WifiStatus status = iface.initiateDataPathRequest(transactionId, req);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "initiateDataPath: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "initiateDataPath: exception: " + e);
            return false;
        }
    }

    /**
     * Responds to a data request from a peer.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param accept Accept (true) or reject (false) the original call.
     * @param ndpId The NDP (Aware data path) ID. Obtained from the request callback.
     * @param interfaceName The interface on which the data path will be setup. Obtained from the
     *                      request callback.
     * @param message An arbitrary byte array to forward to the peer in the respond message.
     */
    public boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] message) {
        if (VDBG) {
            Log.v(TAG, "respondToDataPathRequest: transactionId=" + transactionId + ", accept="
                    + accept + ", int ndpId=" + ndpId + ", interfaceName=" + interfaceName);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "respondToDataPathRequest: null interface");
            return false;
        }

        NanRespondToDataPathIndicationRequest req = new NanRespondToDataPathIndicationRequest();
        req.acceptRequest = accept;
        req.ndpInstanceId = ndpId;
        req.ifaceName = interfaceName;
        convertLcByteToUcByteArray(message, req.appInfo);

        try {
            WifiStatus status = iface.respondToDataPathIndicationRequest(transactionId, req);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "respondToDataPathRequest: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "respondToDataPathRequest: exception: " + e);
            return false;
        }
    }

    /**
     * Terminate an existing data-path (does not delete the interface).
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param ndpId The NDP (Aware data path) ID to be terminated.
     */
    public boolean endDataPath(short transactionId, int ndpId) {
        if (VDBG) {
            Log.v(TAG, "endDataPath: transactionId=" + transactionId + ", ndpId=" + ndpId);
        }

        IWifiNanIface iface = mHal.getWifiNanIface();
        if (iface == null) {
            Log.w(TAG, "endDataPath: null interface");
            return false;
        }

        try {
            WifiStatus status = iface.terminateDataPathRequest(transactionId, ndpId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.w(TAG, "endDataPath: error: " + status.code + "("
                        + status.description + ")");
                return false;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "endDataPath: exception: " + e);
            return false;
        }
    }

    @Override
    public void notifyCapabilitiesResponse(short id, WifiNanStatus status,
            NanCapabilities capabilities) {
        if (VDBG) {
            Log.v(TAG, "notifyCapabilitiesResponse: id=" + id + ", status=" + status
                    + ", capabilities=" + capabilities);
        }

        if (status.status == NanStatusType.SUCCESS) {
            Capabilities frameworkCapabilities = new Capabilities();
            frameworkCapabilities.maxConcurrentAwareClusters = capabilities.maxConcurrentClusters;
            frameworkCapabilities.maxPublishes = capabilities.maxPublishes;
            frameworkCapabilities.maxSubscribes = capabilities.maxSubscribes;
            frameworkCapabilities.maxServiceNameLen = capabilities.maxServiceNameLen;
            frameworkCapabilities.maxMatchFilterLen = capabilities.maxMatchFilterLen;
            frameworkCapabilities.maxTotalMatchFilterLen = capabilities.maxTotalMatchFilterLen;
            frameworkCapabilities.maxServiceSpecificInfoLen =
                    capabilities.maxServiceSpecificInfoLen;
            frameworkCapabilities.maxVsaDataLen = capabilities.maxVsaDataLen;
            frameworkCapabilities.maxNdiInterfaces = capabilities.maxNdiInterfaces;
            frameworkCapabilities.maxNdpSessions = capabilities.maxNdpSessions;
            frameworkCapabilities.maxAppInfoLen = capabilities.maxAppInfoLen;
            frameworkCapabilities.maxQueuedTransmitMessages =
                    capabilities.maxQueuedTransmitFollowupMsgs;
            frameworkCapabilities.maxSubscribeInterfaceAddresses =
                    capabilities.maxSubscribeInterfaceAddresses;
            frameworkCapabilities.supportedCipherSuites = capabilities.supportedCipherSuites;

            mWifiAwareStateManager.onCapabilitiesUpdateResponse(id, frameworkCapabilities);
        } else {
            Log.e(TAG, "notifyCapabilitiesResponse: error code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyEnableResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyEnableResponse: id=" + id + ", status=" + status);

        if (status.status == NanStatusType.SUCCESS) {
            mWifiAwareStateManager.onConfigSuccessResponse(id);
        } else {
            mWifiAwareStateManager.onConfigFailedResponse(id, status.status);
        }
    }

    @Override
    public void notifyConfigResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyConfigResponse: id=" + id + ", status=" + status);

        if (status.status == NanStatusType.SUCCESS) {
            mWifiAwareStateManager.onConfigSuccessResponse(id);
        } else {
            mWifiAwareStateManager.onConfigFailedResponse(id, status.status);
        }
    }

    @Override
    public void notifyDisableResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyDisableResponse: id=" + id + ", status=" + status);

        if (status.status == NanStatusType.SUCCESS) {
            // NOP
        } else {
            Log.e(TAG, "notifyDisableResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyStartPublishResponse(short id, WifiNanStatus status, short publishId) {
        if (VDBG) {
            Log.v(TAG, "notifyStartPublishResponse: id=" + id + ", status=" + status
                    + ", publishId=" + publishId);
        }

        if (status.status == NanStatusType.SUCCESS) {
            mWifiAwareStateManager.onSessionConfigSuccessResponse(id, true, publishId);
        } else {
            mWifiAwareStateManager.onSessionConfigFailResponse(id, true, status.status);
        }
    }

    @Override
    public void notifyStopPublishResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyStopPublishResponse: id=" + id + ", status=" + status);

        if (status.status == NanStatusType.SUCCESS) {
            // NOP
        } else {
            Log.e(TAG, "notifyStopPublishResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyStartSubscribeResponse(short id, WifiNanStatus status, short subscribeId) {
        if (VDBG) {
            Log.v(TAG, "notifyStartSubscribeResponse: id=" + id + ", status=" + status
                    + ", subscribeId=" + subscribeId);
        }

        if (status.status == NanStatusType.SUCCESS) {
            mWifiAwareStateManager.onSessionConfigSuccessResponse(id, false, subscribeId);
        } else {
            mWifiAwareStateManager.onSessionConfigFailResponse(id, false, status.status);
        }
    }

    @Override
    public void notifyStopSubscribeResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyStopSubscribeResponse: id=" + id + ", status=" + status);

        if (status.status == NanStatusType.SUCCESS) {
            // NOP
        } else {
            Log.e(TAG, "notifyStopSubscribeResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyTransmitFollowupResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyTransmitFollowupResponse: id=" + id + ", status=" + status);

        if (status.status == NanStatusType.SUCCESS) {
            mWifiAwareStateManager.onMessageSendQueuedSuccessResponse(id);
        } else {
            mWifiAwareStateManager.onMessageSendQueuedFailResponse(id, status.status);
        }
    }

    @Override
    public void notifyCreateDataInterfaceResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyCreateDataInterfaceResponse: id=" + id + ", status=" + status);

        mWifiAwareStateManager.onCreateDataPathInterfaceResponse(id,
                status.status == NanStatusType.SUCCESS, status.status);
    }

    @Override
    public void notifyDeleteDataInterfaceResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyDeleteDataInterfaceResponse: id=" + id + ", status=" + status);

        mWifiAwareStateManager.onDeleteDataPathInterfaceResponse(id,
                status.status == NanStatusType.SUCCESS, status.status);
    }

    @Override
    public void notifyInitiateDataPathResponse(short id, WifiNanStatus status,
            int ndpInstanceId) {
        if (VDBG) {
            Log.v(TAG, "notifyInitiateDataPathResponse: id=" + id + ", status=" + status
                    + ", ndpInstanceId=" + ndpInstanceId);
        }

        if (status.status == NanStatusType.SUCCESS) {
            mWifiAwareStateManager.onInitiateDataPathResponseSuccess(id, ndpInstanceId);
        } else {
            mWifiAwareStateManager.onInitiateDataPathResponseFail(id, status.status);
        }
    }

    @Override
    public void notifyRespondToDataPathIndicationResponse(short id, WifiNanStatus status) {
        if (VDBG) {
            Log.v(TAG, "notifyRespondToDataPathIndicationResponse: id=" + id
                    + ", status=" + status);
        }

        mWifiAwareStateManager.onRespondToDataPathSetupRequestResponse(id,
                status.status == NanStatusType.SUCCESS, status.status);
    }

    @Override
    public void notifyTerminateDataPathResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyTerminateDataPathResponse: id=" + id + ", status=" + status);

        mWifiAwareStateManager.onEndDataPathResponse(id, status.status == NanStatusType.SUCCESS,
                status.status);
    }

    @Override
    public void notifyBeaconSdfPayloadResponse(short id, WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "notifyBeaconSdfPayloadResponse: id=" + id + ", status=" + status);

        // NOP
    }

    @Override
    public void eventClusterEvent(NanClusterEventInd event) {
        if (VDBG) {
            Log.v(TAG, "eventClusterEvent: eventType=" + event.eventType + ", addr="
                    + String.valueOf(HexEncoding.encode(event.addr)));
        }

        if (event.eventType == NanClusterEventType.DISCOVERY_MAC_ADDRESS_CHANGED) {
            mWifiAwareStateManager.onInterfaceAddressChangeNotification(event.addr);
        } else if (event.eventType == NanClusterEventType.STARTED_CLUSTER) {
            mWifiAwareStateManager.onClusterChangeNotification(
                    WifiAwareClientState.CLUSTER_CHANGE_EVENT_STARTED, event.addr);
        } else if (event.eventType == NanClusterEventType.JOINED_CLUSTER) {
            mWifiAwareStateManager.onClusterChangeNotification(
                    WifiAwareClientState.CLUSTER_CHANGE_EVENT_JOINED, event.addr);
        } else {
            Log.w(TAG, "eventClusterEvent: invalid eventType=" + event.eventType);
        }
    }

    @Override
    public void eventDisabled(WifiNanStatus status) {
        if (VDBG) Log.v(TAG, "eventDisabled: status=" + status);

        mWifiAwareStateManager.onAwareDownNotification(status.status);
    }

    @Override
    public void eventPublishTerminated(short sessionId, WifiNanStatus status) {
        if (VDBG) {
            Log.v(TAG, "eventPublishTerminated: sessionId=" + sessionId + ", status=" + status);
        }

        mWifiAwareStateManager.onSessionTerminatedNotification(sessionId, status.status, true);
    }

    @Override
    public void eventSubscribeTerminated(short sessionId, WifiNanStatus status) {
        if (VDBG) {
            Log.v(TAG, "eventSubscribeTerminated: sessionId=" + sessionId + ", status=" + status);
        }

        mWifiAwareStateManager.onSessionTerminatedNotification(sessionId, status.status, false);
    }

    @Override
    public void eventMatch(NanMatchInd event) {
        if (VDBG) {
            Log.v(TAG, "eventMatch: discoverySessionId=" + event.discoverySessionId + ", peerId="
                    + event.peerId + ", addr=" + String.valueOf(HexEncoding.encode(event.addr))
                    + ", serviceSpecificInfo=" + Arrays.toString(
                    convertUcByteToLcByteArray(event.serviceSpecificInfo)) + ", matchFilter="
                    + Arrays.toString(convertUcByteToLcByteArray(event.matchFilter)));
        }

        mWifiAwareStateManager.onMatchNotification(event.discoverySessionId, event.peerId,
                event.addr, convertUcByteToLcByteArray(event.serviceSpecificInfo),
                convertUcByteToLcByteArray(event.matchFilter));
    }

    @Override
    public void eventMatchExpired(short discoverySessionId, int peerId) {
        if (VDBG) {
            Log.v(TAG, "eventMatchExpired: discoverySessionId=" + discoverySessionId
                    + ", peerId=" + peerId);
        }

        // NOP
    }

    @Override
    public void eventFollowupReceived(NanFollowupReceivedInd event) {
        if (VDBG) {
            Log.v(TAG, "eventFollowupReceived: discoverySessionId=" + event.discoverySessionId
                    + ", peerId=" + event.peerId + ", addr=" + String.valueOf(
                    HexEncoding.encode(event.addr)));
        }

        mWifiAwareStateManager.onMessageReceivedNotification(event.discoverySessionId, event.peerId,
                event.addr, convertUcByteToLcByteArray(event.message));
    }

    @Override
    public void eventTransmitFollowup(short id, WifiNanStatus status) {
        Log.v(TAG, "eventTransmitFollowup: id=" + id + ", reason=" + status.status);

        if (status.status == NanStatusType.SUCCESS) {
            mWifiAwareStateManager.onMessageSendSuccessNotification(id);
        } else {
            mWifiAwareStateManager.onMessageSendFailNotification(id, status.status);
        }
    }

    @Override
    public void eventDataPathRequest(NanDataPathRequestInd event) {
        if (VDBG) {
            Log.v(TAG, "eventDataPathRequest: discoverySessionId=" + event.discoverySessionId
                    + ", peerDiscMacAddr=" + String.valueOf(
                    HexEncoding.encode(event.peerDiscMacAddr)) + ", ndpInstanceId="
                    + event.ndpInstanceId);
        }

        mWifiAwareStateManager.onDataPathRequestNotification(event.discoverySessionId,
                event.peerDiscMacAddr, event.ndpInstanceId,
                convertUcByteToLcByteArray(event.appInfo));
    }

    @Override
    public void eventDataPathConfirm(NanDataPathConfirmInd event) {
        if (VDBG) {
            Log.v(TAG, "onDataPathConfirm: ndpInstanceId=" + event.ndpInstanceId
                    + ", peerNdiMacAddr=" + String.valueOf(HexEncoding.encode(event.peerNdiMacAddr))
                    + ", dataPathSetupSuccess=" + event.dataPathSetupSuccess + ", reason="
                    + event.status.status);
        }

        mWifiAwareStateManager.onDataPathConfirmNotification(event.ndpInstanceId,
                event.peerNdiMacAddr, event.dataPathSetupSuccess, event.status.status,
                convertUcByteToLcByteArray(event.appInfo));
    }

    @Override
    public void eventDataPathTerminated(int ndpInstanceId) {
        if (VDBG) Log.v(TAG, "eventDataPathTerminated: ndpInstanceId=" + ndpInstanceId);

        mWifiAwareStateManager.onDataPathEndNotification(ndpInstanceId);
    }

    @Override
    public void eventBeaconSdfPayload(NanBeaconSdfPayloadInd event) {
        if (VDBG) Log.v(TAG, "eventBeaconSdfPayload");

        // NOP
        Log.e(TAG, "eventBeaconSdfPayload: not configured - should not happen!");
    }

    // utilities

    /**
     * Converts an ArrayList<Byte> to a byte[].
     *
     * @param from The input ArrayList<Byte></Byte> to convert from.
     *
     * @return A newly allocated byte[].
     */
    private byte[] convertUcByteToLcByteArray(ArrayList<Byte> from) {
        byte[] to = new byte[from.size()];
        for (int i = 0; i < from.size(); ++i) {
            to[i] = from.get(i);
        }
        return to;
    }

    /**
     * Converts a byte[] to an ArrayList<Byte>. Fills in the entries of the 'to' array if
     * provided (non-null), otherwise creates and returns a new ArrayList<>.
     *
     * @param from The input byte[] to convert from.
     * @param to An optional ArrayList<> to fill in from 'from'.
     *
     * @return A newly allocated ArrayList<> if 'to' is null, otherwise null.
     */
    private ArrayList<Byte> convertLcByteToUcByteArray(byte[] from, ArrayList<Byte> to) {
        if (to == null) {
            to = new ArrayList<>(from.length);
        }
        for (int i = 0; i < from.length; ++i) {
            to.add(Byte.valueOf(from[i]));
        }
        return to;
    }

    private void copyArray(byte[] from, byte[] to) {
        for (int i = 0; i < from.length; ++i) {
            to[i] = from[i];
        }
    }

    private static String statusString(WifiStatus status) {
        if (status == null) {
            return "status=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(status.code).append(" (").append(status.description).append(")");
        return sb.toString();
    }
}
