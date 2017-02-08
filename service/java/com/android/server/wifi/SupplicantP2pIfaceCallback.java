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

import android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIfaceCallback;
import android.hardware.wifi.supplicant.V1_0.WpsConfigMethods;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.util.Log;

import com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStatus;

import java.util.ArrayList;

public class SupplicantP2pIfaceCallback extends ISupplicantP2pIfaceCallback.Stub {
    private static final String TAG = "SupplicantP2pIfaceCallback";

    private final String mInterface;
    private final WifiMonitor mMonitor;

    public SupplicantP2pIfaceCallback(String iface, WifiMonitor monitor) {
        mInterface = iface;
        mMonitor = monitor;
    }


    /**
     * Used to indicate that a new network has been added.
     *
     * @param id Network ID allocated to the corresponding network.
     */
    public void onNetworkAdded(int networkId) {}


    /**
     * Used to indicate that a network has been removed.
     *
     * @param id Network ID allocated to the corresponding network.
     */
    public void onNetworkRemoved(int networkId) {}


    /**
     * Used to indicate that a P2P device has been found.
     *
     * @param srcAddress MAC address of the device found. This must either
     *        be the P2P device address or the P2P interface address.
     * @param p2pDeviceAddress P2P device address.
     * @param primaryDeviceType Type of device. Refer to section B.1 of Wifi P2P
     *        Technical specification v1.2.
     * @param deviceName Name of the device.
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @param deviceCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param groupCapabilites Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param wfdDeviceInfo WFD device info as described in section 5.1.2 of WFD
     *        technical specification v1.0.0.
     */
    public void onDeviceFound(byte[] srcAddress, byte[] p2pDeviceAddress, byte[] primaryDeviceType,
            String deviceName, short configMethods, byte deviceCapabilities, int groupCapabilities,
            byte[] wfdDeviceInfo) {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = deviceName;
        device.deviceAddress = bytesToString(p2pDeviceAddress, ":");
        device.primaryDeviceType = bytesToString(primaryDeviceType, null);
        device.deviceCapability = deviceCapabilities;
        device.groupCapability = groupCapabilities;
        device.wpsConfigMethodsSupported = configMethods;
        device.status = WifiP2pDevice.AVAILABLE;

        if (wfdDeviceInfo.length >= 6) {
            device.wfdInfo = new WifiP2pWfdInfo(
                    (wfdDeviceInfo[0] << 8) + wfdDeviceInfo[1],
                    (wfdDeviceInfo[2] << 8) + wfdDeviceInfo[3],
                    (wfdDeviceInfo[4] << 8) + wfdDeviceInfo[5]);
        }

        Log.i(TAG, "Device discovered on " + mInterface + ": " + device);
        mMonitor.handleDeviceFound(mInterface, device);
    }


    /**
     * Used to indicate that a P2P device has been lost.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    public void onDeviceLost(byte[] p2pDeviceAddress) {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceAddress = bytesToString(p2pDeviceAddress, ":");
        device.status = WifiP2pDevice.UNAVAILABLE;

        Log.i(TAG, "Device lost on " + mInterface + ": " + device);
        mMonitor.handleDeviceLost(mInterface, device);
    }


    /**
     * Used to indicate the termination of P2P find operation.
     */
    public void onFindStopped() {
        Log.i(TAG, "Search stopped on " + mInterface);
        mMonitor.handleFindStopped(mInterface);
    }


    /**
     * Used to indicate the reception of a P2P Group Owner negotiation request.
     *
     * @param srcAddress MAC address of the device that initiated the GO
     *        negotiation request.
     * @param passwordId Type of password.
     */
    public void onGoNegotiationRequest(byte[] srcAddress, short passwordId) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = bytesToString(srcAddress, ":");
        config.wps = new WpsInfo();

        switch (passwordId) {
            case WpsDevPasswordId.USER_SPECIFIED:
                config.wps.setup = WpsInfo.DISPLAY;
                break;

            case WpsDevPasswordId.PUSHBUTTON:
                config.wps.setup = WpsInfo.PBC;
                break;

            case WpsDevPasswordId.REGISTRAR_SPECIFIED:
                config.wps.setup = WpsInfo.KEYPAD;
                break;

            default:
                config.wps.setup = WpsInfo.PBC;
                break;
        }

        Log.i(TAG, "Group Owner negotiation initiated on " + mInterface + ": " + config);
        mMonitor.handleGoNegotiationRequest(mInterface, config);
    }


    /**
     * Used to indicate the completion of a P2P Group Owner negotiation request.
     *
     * @param status Status of the GO negotiation.
     */
    public void onGoNegotiationCompleted(int status) {
        Log.i(TAG, "Group Owner negotiation completed with status: " + status);
        P2pStatus result = halStatusToP2pStatus(status);

        if (result == P2pStatus.SUCCESS) {
            mMonitor.handleGoNegotiationSuccess(mInterface);
        } else {
            mMonitor.handleGoNegotiationFailure(mInterface, result);
        }
    }


    /**
     * Used to indicate a successful formation of a P2P group.
     */
    public void onGroupFormationSuccess() {
        Log.i(TAG, "Group formation successful on " + mInterface);
        mMonitor.handleGroupFormationSuccess(mInterface);
    }


    /**
     * Used to indicate a failure to form a P2P group.
     *
     * @param failureReason Failure reason string for debug purposes.
     */
    public void onGroupFormationFailure(String failureReason) {
        // TODO(ender): failureReason should probably be an int (P2pStatusCode).
        Log.i(TAG, "Group formation failed on " + mInterface + ": " + failureReason);
        mMonitor.handleGroupFormationFailure(mInterface, failureReason);
    }


    /**
     * Used to indicate the start of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGo Whether this device is owner of the group.
     * @param ssid SSID of the group.
     * @param frequency Frequency on which this group is created.
     * @param psk PSK used to secure the group.
     * @param passphrase PSK passphrase used to secure the group.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param isPersistent Whether this group is persisted or not.
     */
    public void onGroupStarted(String groupIfName, boolean isGo, ArrayList<Byte> ssid,
            int frequency, byte[] psk, String passphrase, byte[] goDeviceAddress,
            boolean isPersistent) {
        Log.i(TAG, "Group " + groupIfName + " started on " + mInterface);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setInterface(groupIfName);
        group.setNetworkName(byteListToString(ssid));
        group.setIsGroupOwner(isGo);
        group.setPassphrase(passphrase);

        if (isPersistent) {
            group.setNetworkId(WifiP2pGroup.PERSISTENT_NET_ID);
        } else {
            group.setNetworkId(WifiP2pGroup.TEMPORARY_NET_ID);
        }

        WifiP2pDevice owner = new WifiP2pDevice();
        owner.deviceAddress = bytesToString(goDeviceAddress, ":");
        group.setOwner(owner);

        mMonitor.handleGroupStarted(mInterface, group);
    }


    /**
     * Used to indicate the removal of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGo Whether this device is owner of the group.
     */
    public void onGroupRemoved(String groupIfName, boolean isGo) {
        Log.i(TAG, "Group " + groupIfName + " removed from " + mInterface);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setInterface(groupIfName);
        group.setIsGroupOwner(isGo);
        mMonitor.handleGroupRemoved(mInterface, group);
    }


    /**
     * Used to indicate the reception of a P2P invitation.
     *
     * @param srcAddress MAC address of the device that sent the invitation.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param Bssid Bssid of the group.
     * @param persistentNetworkId Persistent network Id of the group.
     * @param operatingFrequency Frequency on which the invitation was received.
     */
    public void onInvitationReceived(byte[] srcAddress, byte[] goDeviceAddress,
            byte[] bssid, int persistentNetworkId, int operatingFrequency) {
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(persistentNetworkId);

        WifiP2pDevice client = new WifiP2pDevice();
        client.deviceAddress = bytesToString(srcAddress, ":");
        group.addClient(client);

        WifiP2pDevice owner = new WifiP2pDevice();
        owner.deviceAddress = bytesToString(goDeviceAddress, ":");
        group.setOwner(owner);

        Log.i(TAG, "Invitation received on " + mInterface + ": " + group);
        mMonitor.handleInvitationReceived(mInterface, group);
    }


    /**
     * Used to indicate the result of the P2P invitation request.
     *
     * @param Bssid Bssid of the group.
     * @param status Status of the invitation.
     */
    public void onInvitationResult(byte[] bssid, int status) {
        Log.i(TAG, "Invitation completed with status: " + status);
        mMonitor.handleInvitationResult(mInterface, halStatusToP2pStatus(status));
    }


    /**
     * Used to indicate a push-button request generated during provision discovery.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    public void onProvisionDiscoveryPbcRequest(byte[] p2pDeviceAddress) {
        WifiP2pProvDiscEvent event = new WifiP2pProvDiscEvent();
        event.event = WifiP2pProvDiscEvent.PBC_REQ;
        event.device = new WifiP2pDevice();
        event.device.deviceAddress = bytesToString(p2pDeviceAddress, ":");
        Log.i(TAG, "Provision discovery push button request on " + mInterface);
        mMonitor.handleProvisionDiscoveryPbcRequest(mInterface, event);
    }


    /**
     * Used to indicate a push-button response generated during provision discovery.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    public void onProvisionDiscoveryPbcResponse(byte[] p2pDeviceAddress) {
        WifiP2pProvDiscEvent event = new WifiP2pProvDiscEvent();
        event.event = WifiP2pProvDiscEvent.PBC_RSP;
        event.device = new WifiP2pDevice();
        event.device.deviceAddress = bytesToString(p2pDeviceAddress, ":");
        Log.i(TAG, "Provision discovery push button response on " + mInterface);
        mMonitor.handleProvisionDiscoveryPbcResponse(mInterface, event);
    }


    /**
     * Used to indicate the pin generated during provision discovery.
     *
     * @param p2pDeviceAddress P2P device address.
     * @param generatedPin 8 digit pin generated.
     */
    public void onProvisionDiscoveryShowPin(byte[] p2pDeviceAddress, String generatedPin) {
        WifiP2pProvDiscEvent event = new WifiP2pProvDiscEvent();
        event.event = WifiP2pProvDiscEvent.SHOW_PIN;
        event.device = new WifiP2pDevice();
        event.device.deviceAddress = bytesToString(p2pDeviceAddress, ":");
        event.pin = generatedPin;
        Log.i(TAG, "Provision discovery pin show request on " + mInterface);
        mMonitor.handleProvisionDiscoveryShowPin(mInterface, event);
    }


    /**
     * Used to indicate that a pin needs to be entered during provision discovery.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    public void onProvisionDiscoveryEnterPin(byte[] p2pDeviceAddress) {
        WifiP2pProvDiscEvent event = new WifiP2pProvDiscEvent();
        event.event = WifiP2pProvDiscEvent.ENTER_PIN;
        event.device = new WifiP2pDevice();
        event.device.deviceAddress = bytesToString(p2pDeviceAddress, ":");
        Log.i(TAG, "Provision discovery pin enter request on " + mInterface);
        mMonitor.handleProvisionDiscoveryEnterPin(mInterface, event);
    }


    /**
     * Used to indicate a provision discovery failure.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    public void onProvisionDiscoveryFailure(byte[] p2pDeviceAddress) {
        Log.i(TAG, "Provision discovery failure on " + mInterface);
        mMonitor.handleProvisionDiscoveryFailure(mInterface);
    }


    /**
     * Used to indicate the completion of a P2P provision discovery request.
     *
     * @param p2pDeviceAddress P2P device address.
     * @param isRequest Whether we received or sent the provision discovery.
     * @param status Status of the provision discovery.
     * @param configMethods Mask of WPS configuration methods supported.
     * @param generatedPin 8 digit pin generated.
     */
    public void onProvisionDiscoveryCompleted(byte[] p2pDeviceAddress, boolean isRequest,
            byte status, short configMethods, String generatedPin) {
        Log.i(TAG, "Provision discovery completed on " + mInterface);
        // TODO(ender): there seems to be no need for this event in WifiMonitor.
    }


    /**
     * Used to indicate the reception of a P2P service discovery response.
     *
     * @param srcAddress MAC address of the device that sent the service discovery.
     * @param updateIndicator Service update indicator. Refer to section 3.1.3 of
     *        Wifi P2P Technical specification v1.2.
     * @param tlvs Refer to section 3.1.3.1 of Wifi P2P Technical specification v1.2.
     */
    public void onServiceDiscoveryResponse(byte[] srcAddress, short updateIndicator,
            ArrayList<Byte> tlvs) {}


    /**
     * Used to indicate when a STA device is connected to this device.
     *
     * @param srcAddress MAC address of the device that was authorized.
     * @param p2pDeviceAddress P2P device address.
     */
    public void onStaAuthorized(byte[] srcAddress, byte[] p2pDeviceAddress) {}


    /**
     * Used to indicate when a STA device is disconnected from this device.
     *
     * @param srcAddress MAC address of the device that was deauthorized.
     * @param p2pDeviceAddress P2P device address.
     */
    public void onStaDeauthorized(byte[] srcAddress, byte[] p2pDeviceAddress) {}


    private static String bytesToString(byte[] byteArray, String separator) {
        if (byteArray == null) {
            return null;
        }

        int separatorLength = 0;
        if (separator != null) {
            separatorLength = separator.length();
        }

        StringBuilder result =
                new StringBuilder(byteArray.length * (2 + separatorLength));

        for (int i = 0; i < byteArray.length; ++i) {
            if (i > 0) {
                if (separator != null) result.append(separator);
            }
            result.append(String.format("%02x", byteArray[i]));
        }
        return result.toString();
    }

    private static String byteListToString(ArrayList<Byte> list) {
        if (list == null) {
            Log.e(TAG, "Cannot process null array list.");
            return null;
        }

        byte[] array = new byte[list.size()];
        int index = 0;
        for (Byte current : list) {
            array[index++] = current;
        }
        return new String(array);
    }

    private static P2pStatus halStatusToP2pStatus(int status) {
        P2pStatus result = P2pStatus.UNKNOWN;

        switch (status) {
            case P2pStatusCode.SUCCESS:
            case P2pStatusCode.SUCCESS_DEFERRED:
                result = P2pStatus.SUCCESS;
                break;

            case P2pStatusCode.FAIL_INFO_CURRENTLY_UNAVAILABLE:
                result = P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE;
                break;

            case P2pStatusCode.FAIL_INCOMPATIBLE_PARAMS:
                result = P2pStatus.INCOMPATIBLE_PARAMETERS;
                break;

            case P2pStatusCode.FAIL_LIMIT_REACHED:
                result = P2pStatus.LIMIT_REACHED;
                break;

            case P2pStatusCode.FAIL_INVALID_PARAMS:
                result = P2pStatus.INVALID_PARAMETER;
                break;

            case P2pStatusCode.FAIL_UNABLE_TO_ACCOMMODATE:
                result = P2pStatus.UNABLE_TO_ACCOMMODATE_REQUEST;
                break;

            case P2pStatusCode.FAIL_PREV_PROTOCOL_ERROR:
                result = P2pStatus.PREVIOUS_PROTOCOL_ERROR;
                break;

            case P2pStatusCode.FAIL_NO_COMMON_CHANNELS:
                result = P2pStatus.NO_COMMON_CHANNEL;
                break;

            case P2pStatusCode.FAIL_UNKNOWN_GROUP:
                result = P2pStatus.UNKNOWN_P2P_GROUP;
                break;

            case P2pStatusCode.FAIL_BOTH_GO_INTENT_15:
                result = P2pStatus.BOTH_GO_INTENT_15;
                break;

            case P2pStatusCode.FAIL_INCOMPATIBLE_PROV_METHOD:
                result = P2pStatus.INCOMPATIBLE_PROVISIONING_METHOD;
                break;

            case P2pStatusCode.FAIL_REJECTED_BY_USER:
                result = P2pStatus.REJECTED_BY_USER;
                break;
        };
        return result;
    }
}

