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

import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
/**
 * Native calls sending requests to the P2P Hals, and callbacks for receiving P2P events
 *
 * {@hide}
 */
public class WifiP2pNativeNew {
    private static final boolean DBG = true;
    private static final String TAG = "WifiP2pNativeNew";

    private static final String SUPPLICANT_HIDL_SERVICE_NAME = "wpa_supplicant";
    // Supplicant HAL HIDL interface objects
    private ISupplicant mHidlSupplicant = null;
    private ISupplicantIface mHidlSupplicantIface = null;
    private ISupplicantP2pIface mHidlSupplicantP2pIface = null;
    private Boolean mSupplicantReady = null;
    private Object mSupplicantReadySync = new Object();

    /**
     * Register for service notification & bring up Supplicant HIDL interfaces when service is
     * ready.
     */
    public boolean registerForServiceNotification() {
        Log.i(TAG, "Registering SupplicantHidl service ready callback.");
        final IServiceManager serviceManager = IServiceManager.getService("manager");
        if (serviceManager == null) {
            Log.e(TAG, "Failed to get HIDL Service Manager");
            return false;
        }
        IServiceNotification serviceNotificationCb = new IServiceNotification.Stub() {
            public void onRegistration(String fqName, String name, boolean preexisting) {
                Log.i(TAG, "Registered " + fqName + ", " + name + " preexisting=" + preexisting);
                startSupplicantHidl();
            }
        };
        // TODO(b/33639391): update this code to use appropriate api call.
        if (!serviceManager.registerForNotifications(ISupplicant.kInterfaceName,
                "", serviceNotificationCb)) {
            Log.e(TAG, "Failed to register for notifcations to " + ISupplicant.kInterfaceName
                    + ", " + SUPPLICANT_HIDL_SERVICE_NAME);
            return false;
        }
        return true;
    }


    /**
     * Bring up the HIDL Vendor HAL and configure for P2P mode
     */
    public boolean startHidlHal() {
        /** TBC **/
        return true;
    }


    /**
     * Suspends calling thread until supplicant is ready.
     *
     * @return true, if P2P supplicant was successfully initialized.
     */
    public boolean waitSupplicantReady() {
        synchronized (mSupplicantReadySync) {
            while (mSupplicantReady == null) {
                try {
                    mSupplicantReadySync.wait();
                } catch (InterruptedException e) {
                }
            }
            return mSupplicantReady;
        }
    }


    /**
     * Bring up the Supplicant Hal
     */
    public boolean startSupplicantHidl() {
        class SupplicantInitException extends Exception {
            public SupplicantInitException(String message) {
                super(message);
            }
        }

        boolean result = false;

        try {
            Log.i(TAG, "Bringing up P2P supplicant HIDL");
            mHidlSupplicantP2pIface = null;
            mHidlSupplicant = ISupplicant.getService(SUPPLICANT_HIDL_SERVICE_NAME);
            if (mHidlSupplicant == null) {
                throw new SupplicantInitException("Failed to get wpa_supplicant HIDL service.");
            }

            /** get interfaces controlled by Supplicant*/
            /** List available Wifi chips */
            final ArrayList<ISupplicant.IfaceInfo> supplicantIfaces = new ArrayList<>();
            // Use lambda's here.
            ISupplicant.listInterfacesCallback listIfacesCb = new ISupplicant.listInterfacesCallback() {
                public void onValues(SupplicantStatus status,
                        java.util.ArrayList<ISupplicant.IfaceInfo> ifaces) {
                    if (status.code != SupplicantStatusCode.SUCCESS) {
                        Log.e(TAG, "Getting Supplicant Interfaces failed: " + status.code);
                        return;
                    }
                    supplicantIfaces.addAll(ifaces);
                }
            };
            mHidlSupplicant.listInterfaces(listIfacesCb);
            if (supplicantIfaces.size() == 0) {
                throw new SupplicantInitException("Got zero HIDL supplicant ifaces.");
            }

            /**
            * Get Supplicant HIDL iface
            */
            ISupplicant.getInterfaceCallback getIfaceCb = new ISupplicant.getInterfaceCallback() {
                public void onValues(SupplicantStatus status, ISupplicantIface iface) {
                    if (status.code != SupplicantStatusCode.SUCCESS) {
                        Log.e(TAG, "Failed to get Supplicant P2P Interface " + status.code);
                        return;
                    }
                    mHidlSupplicantIface = iface;
                }
            };
            boolean hasP2pIface = false;
            for (ISupplicant.IfaceInfo ifaceInfo : supplicantIfaces) {
                if (ifaceInfo.type == IfaceType.P2P) {
                    hasP2pIface = true;
                    mHidlSupplicant.getInterface(ifaceInfo, getIfaceCb);
                    break;
                }
            }
            if (!hasP2pIface) {
                throw new SupplicantInitException("Supplicant HIDL has no P2P interface, got " +
                        supplicantIfaces.size() + " ifaces.");
            }
            if (mHidlSupplicantIface == null) {
                throw new SupplicantInitException("Supplicant P2P Interface is null");
            }

            mHidlSupplicantP2pIface =
                    ISupplicantP2pIface.asInterface(mHidlSupplicantIface.asBinder());
            Log.i(TAG, "Got supplicant P2P HIDL Iface");
            result = true;
        } catch (SupplicantInitException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            synchronized (mSupplicantReadySync) {
                mSupplicantReady = result;
                mSupplicantReadySync.notifyAll();
            }
        }

        return result;
    }


    protected void logd(String s) {
        if (DBG) Log.d(TAG, s);
    }


    protected void logCompletion(String operation, SupplicantStatus status) {
        if (status.code == SupplicantStatusCode.SUCCESS) {
            logd(operation + " completed successfully.");
        } else {
            Log.w(TAG, operation + " failed: " + status.code + " (" + status.debugMessage + ")");
        }
    }


    private String addressToString(byte[] address) {
        if (address == null) {
            Log.e(TAG, "Cannot combine mac address from null data.");
            return null;
        }

        if (address.length != 6) {
            Log.e(TAG, "Invalid length of mac address: " + address.length);
            return null;
        }

        // MAC address length: 6 two-digit segments separated by 5 colons, terminated with \0.
        StringBuilder result = new StringBuilder(6 * 2 + 6);

        for (int i = 0; i < address.length; ++i) {
            if (result.length() > 0) {
                result.append(":");
            }
            result.append(String.format("%02x", address[i]));
        }
        return result.toString();
    }


    private byte[] stringToAddress(String address) {
        if (address == null) {
            Log.e(TAG, "Cannot parse null mac address.");
            return null;
        }

        String[] parts = address.split(":");
        byte[] result = new byte[6];
        for (int i = 0; i < 6; ++i) {
            Integer hex = Integer.parseInt(parts[i], 16);
            result[i] = hex.byteValue();
        }
        return result;
    }


    private ArrayList<Byte> hexStringToByteArray(String s) {
        int len = s.length();
        ArrayList<Byte> data = new ArrayList<Byte>(len / 2);
        for (int i = 0; i < len; i += 2) {
            byte b = (byte)((Character.digit(s.charAt(i), 16) << 4) +
                    Character.digit(s.charAt(i+1), 16));
            data.add(new Byte(b));
        }
        return data;
    }


    /**
     * Initiate a P2P service discovery with an optional timeout.
     *
     * @param timeoutInSec Max time to be spent is peforming discovery.
     *        Set to 0 to indefinely continue discovery untill and explicit
     *        |stopFind| is sent.
     * @return boolean value indicating whether operation was successful.
     */
    public boolean find(int timeout) {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering find(" + timeout + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.find(timeout);
        logCompletion("find()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Stop an ongoing P2P service discovery.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean stopFind() {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering stopFind()");
        SupplicantStatus status = mHidlSupplicantP2pIface.stopFind();
        logCompletion("stopFind()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Flush P2P peer table and state.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean flush() {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering flush()");
        SupplicantStatus status = mHidlSupplicantP2pIface.flush();
        logCompletion("flush()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * This command can be used to flush all services from the
     * device.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean serviceFlush() {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering serviceFlush()");
        SupplicantStatus status = mHidlSupplicantP2pIface.flushServices();
        logCompletion("serviceFlush()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Turn on/off power save mode for the interface.
     *
     * @param groupIfName Group interface name to use.
     * @param enable Indicate if power save is to be turned on/off.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setPowerSave(String groupIfName, boolean enable) {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering setPowerSave(" + groupIfName + ", " + enable + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.setPowerSave(groupIfName, enable);
        logCompletion("setPowerSave()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Set the Maximum idle time in seconds for P2P groups.
     * This value controls how long a P2P group is maintained after there
     * is no other members in the group. As a group owner, this means no
     * associated stations in the group. As a P2P client, this means no
     * group owner seen in scan results.
     *
     * @param groupIfName Group interface name to use.
     * @param timeoutInSec Timeout value in seconds.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setGroupIdle(String groupIfName, int timeoutInSec) {
        if (mHidlSupplicantP2pIface == null) return false;
        // Basic checking here. Leave actual parameter validation to supplicant.
        if (timeoutInSec < 0) return false;

        logd("entering setGroupIdle(" + groupIfName + ", " + timeoutInSec+ ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.setGroupIdle(
                groupIfName, timeoutInSec);
        logCompletion("setGroupIdle()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Set the postfix to be used for P2P SSID's.
     *
     * @param postfix String to be appended to SSID.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean setSsidPostfix(String postfix) {
        if (mHidlSupplicantP2pIface == null) return false;
        // Basic checking here. Leave actual parameter validation to supplicant.
        if (postfix == null) return false;

        ArrayList<Byte> ssid = new ArrayList<Byte>();
        for (byte b : postfix.getBytes()) {
            ssid.add(new Byte(b));
        }

        logd("entering setSsidPostfix(" + postfix + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.setSsidPostfix(ssid);
        logCompletion("setSsidPostfix()", status);
        return status.code == SupplicantStatusCode.SUCCESS;

    }


    /**
     * Start P2P group formation with a discovered P2P peer. This includes
     * optional group owner negotiation, group interface setup, provisioning,
     * and establishing data connection.
     *
     * @param peerAddress MAC address of the device to connect to.
     * @param provisionMethod Provisioning method to use.
     * @param preSelectedPin Pin to be used, if |provisionMethod| uses one of the
     *        preselected |PIN*| methods.
     * @param joinExistingGroup Indicates that this is a command to join an
     *        existing group as a client. It skips the group owner negotiation
     *        part. This must send a Provision Discovery Request message to the
     *        target group owner before associating for WPS provisioning.
     * @param persistent Used to request a persistent group to be formed.
     * @param goIntent Used to override the default Intent for this group owner
     *        negotiation (Values from 1-15). Refer to section 4.1.6 in
     *        Wi-Fi Peer-to-Peer (P2P) Technical Specification Version 1.7.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean connect() {
        return false;
    }

    /**
     * Cancel an ongoing P2P group formation and joining-a-group related
     * operation. This operation unauthorizes the specific peer device (if any
     * had been authorized to start group formation), stops P2P find (if in
     * progress), stops pending operations for join-a-group, and removes the
     * P2P group interface (if one was used) that is in the WPS provisioning
     * step. If the WPS provisioning step has been completed, the group is not
     * terminated.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean cancelConnect() {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering cancelConnect()");
        SupplicantStatus status = mHidlSupplicantP2pIface.cancelConnect();
        logCompletion("cancelConnect()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Send P2P provision discovery request to the specified peer. The
     * parameters for this command are the P2P device address of the peer and the
     * desired configuration method.
     *
     * @param peerAddress MAC address of the device to send discovery.
     * @param provisionMethod Provisioning method to use.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean provisionDiscovery(String peerAddress, int provisionMethod) {
        if (mHidlSupplicantP2pIface == null) return false;
        if (peerAddress == null) return false;

        int targetMethod = 0;
        switch (provisionMethod) {
            case WpsInfo.PBC:
                targetMethod = ISupplicantP2pIface.WpsProvisionMethod.PBC;
                break;

            case WpsInfo.DISPLAY:
                targetMethod = ISupplicantP2pIface.WpsProvisionMethod.DISPLAY;
                break;

            case WpsInfo.KEYPAD:
                targetMethod = ISupplicantP2pIface.WpsProvisionMethod.KEYPAD;
                break;

            case WpsInfo.LABEL:
            default:
                Log.e(TAG, "Unsupported WPS provision method " + provisionMethod);
                return false;
        }

        byte[] macAddress = stringToAddress(peerAddress);
        if (macAddress == null) return false;

        logd("entering provisionDiscovery(" + peerAddress + ", " + provisionMethod + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.provisionDiscovery(
                macAddress, targetMethod);
        logCompletion("provisionDiscovery()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Invite a device to a persistent group.
     * If the peer device is the group owner of the persistent group, the peer
     * parameter is not needed. Otherwise it is used to specify which
     * device to invite. |goDeviceAddress| parameter may be used to override
     * the group owner device address for Invitation Request should it not be
     * known for some reason (this should not be needed in most cases).
     *
     * @param groupIfName Group interface name to use.
     * @param goDeviceAddress MAC address of the group owner device.
     * @param peerAddress MAC address of the device to invite.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean invite(WifiP2pGroup group, String peerAddress) {
        if (mHidlSupplicantP2pIface == null) return false;

        byte[] ownerMacAddress = stringToAddress(group.getOwner().deviceAddress);
        byte[] peerMacAddress = stringToAddress(peerAddress);
        logd("entering invite(" + group.getInterface() + ", " +
                group.getOwner().deviceAddress + ", " + peerAddress + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.invite(
                group.getInterface(), ownerMacAddress, peerMacAddress);
        logCompletion("invite()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Reject connection attempt from a peer (specified with a device
     * address). This is a mechanism to reject a pending group owner negotiation
     * with a peer and request to automatically block any further connection or
     * discovery of the peer.
     *
     * @param peerAddress MAC address of the device to reject.
     *
     * @return boolean value indicating whether operation was successful.
     */
    public boolean reject(String peerAddress) {
        if (mHidlSupplicantP2pIface == null) return false;

        byte[] macAddress = stringToAddress(peerAddress);
        logd("entering reject(" + peerAddress + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.reject(macAddress);
        logCompletion("reject()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Gets the MAC address of the device.
     *
     * @return MAC address of the device.
     */
    public String getDeviceAddress() {
        if (mHidlSupplicantP2pIface == null) {
            Log.e(TAG, "No Supplicant P2P IFace.");
            return null;
        }

        class GetDevAddrCallback implements ISupplicantP2pIface.getDeviceAddressCallback {
            private byte[] mAddress = null;

            public void onValues(SupplicantStatus status, byte[] address) {
                logCompletion("getDeviceAddress()", status);
                if (status.code == SupplicantStatusCode.SUCCESS) {
                    mAddress = address;
                }
            }

            public byte[] getAddress() {
                return mAddress;
            }
        };

        GetDevAddrCallback callback = new GetDevAddrCallback();

        logd("entering getDeviceAddress()");
        mHidlSupplicantP2pIface.getDeviceAddress(callback);
        logd("leaving getDeviceAddress()");
        return addressToString(callback.getAddress());
    }


    /**
     * Gets the operational SSID of the device.
     *
     * @param peerAddress MAC address of the peer.
     *
     * @return SSID of the device.
     */
    public ArrayList<Byte> getSsid(String address) {
        if (mHidlSupplicantP2pIface == null) return null;

        byte[] macAddress = stringToAddress(address);
        if (macAddress == null) return null;

        class SsidCallback implements ISupplicantP2pIface.getSsidCallback {
            private ArrayList<Byte> mSsid = null;

            public void onValues(SupplicantStatus status, ArrayList<Byte> ssid) {
                logCompletion("getSsid()", status);
                if (status.code == SupplicantStatusCode.SUCCESS) {
                    mSsid = ssid;
                }
            }

            public ArrayList<Byte> getSsid() {
                return mSsid;
            }
        };

        SsidCallback callback = new SsidCallback();

        logd("entering getSsid(" + address + ")");
        mHidlSupplicantP2pIface.getSsid(macAddress, callback);
        logd("leaving getSsid()");
        return callback.getSsid();
    }


    /**
     * Reinvoke a device from a persistent group.
     *
     * @param networkId Used to specify the persistent group.
     * @param peerAddress MAC address of the device to reinvoke.
     *
     * @return true, if operation was successful.
     */
    public boolean reinvoke(int networkId, String peerAddress) {
        if (mHidlSupplicantP2pIface == null) return false;

        logd("entering reinvoke(" + networkId + ", " + peerAddress + ")");
        byte[] macAddress = stringToAddress(peerAddress);
        if (macAddress == null) return false;
        SupplicantStatus status = mHidlSupplicantP2pIface.reinvoke(networkId, macAddress);
        logCompletion("reinvoke()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Set up a P2P group owner manually (i.e., without group owner
     * negotiation with a specific peer). This is also known as autonomous
     * group owner.
     *
     * @param networkId Used to specify the restart of a persistent group.
     * @param isPersistent Used to request a persistent group to be formed.
     *
     * @return true, if operation was successful.
     */
    public boolean groupAdd(int networkId, boolean isPersistent) {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering groupAdd(" + networkId + ", " + isPersistent + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.addGroup(isPersistent, networkId);
        logCompletion("groupAdd()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Terminate a P2P group. If a new virtual network interface was used for
     * the group, it must also be removed. The network interface name of the
     * group interface is used as a parameter for this command.
     *
     * @param groupIfName Group interface name to use.
     *
     * @return true, if operation was successful.
     */
    public boolean groupRemove(String groupName) {
        if (mHidlSupplicantP2pIface == null) return false;
        logd("entering groupRemove(" + groupName + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.removeGroup(groupName);
        logCompletion("groupRemove()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Gets the capability of the group which the device is a
     * member of.
     *
     * @param peerAddress MAC address of the peer.
     *
     * @return combination of |GroupCapabilityMask| values.
     */
    public int getGroupCapability(String peerAddress) {
        if (mHidlSupplicantP2pIface == null) return 0;

        byte[] macAddress = stringToAddress(peerAddress);
        class GetGrpCapCallback implements ISupplicantP2pIface.getGroupCapabilityCallback {
            private int mCapability = 0;

            public void onValues(SupplicantStatus status, int capability) {
                logCompletion("getGroupCapability()", status);
                if (status.code == SupplicantStatusCode.SUCCESS) {
                    mCapability = capability;
                }
            }

            public int getCapability() {
                return mCapability;
            }
        };

        GetGrpCapCallback callback = new GetGrpCapCallback();

        logd("entering getGroupCapability(" + peerAddress + ")");
        mHidlSupplicantP2pIface.getGroupCapability(macAddress, callback);
        logd("leaving getGroupCapability()");
        return callback.getCapability();
    }


    /**
     * Configure Extended Listen Timing.
     *
     * If enabled, listen state must be entered every |intervalInMillis| for at
     * least |periodInMillis|. Both values have acceptable range of 1-65535
     * (with interval obviously having to be larger than or equal to duration).
     * If the P2P module is not idle at the time the Extended Listen Timing
     * timeout occurs, the Listen State operation must be skipped.
     *
     * @param periodInMillis Period in milliseconds.
     * @param intervalInMillis Interval in milliseconds.
     *
     * @return true, if operation was successful.
     */
    public boolean configureExtListen(int periodInMillis, int intervalInMillis) {
        if (mHidlSupplicantP2pIface == null) return false;
        // Verify that the integers are not negative. Leave actual parameter validation to
        // supplicant.
        if (periodInMillis < 0 || intervalInMillis < 0) return false;

        logd("entering configureExtListen(" + periodInMillis + ", " + intervalInMillis + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.configureExtListen(
                periodInMillis, intervalInMillis);
        logCompletion("configureExtListen()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * Set P2P Listen channel.
     *
     * When specifying a social channel on the 2.4 GHz band (1/6/11) there is no
     * need to specify the operating class since it defaults to 81. When
     * specifying a social channel on the 60 GHz band (2), specify the 60 GHz
     * operating class (180).
     *
     * @param channel Wifi channel. eg, 1, 6, 11.
     * @param operatingClass Operating Class indicates the channel set of the AP
     *        indicated by this BSSID
     *
     * @return true, if operation was successful.
     */
    public boolean setListenChannel(int channel, int operatingClass) {
        if (mHidlSupplicantP2pIface == null) return false;
        // Verify that the integers are not negative. Leave actual parameter validation to
        // supplicant.
        if (channel < 0 || operatingClass < 0) return false;

        logd("entering setListenChannel(" + channel + ", " + operatingClass + ")");
        SupplicantStatus status = mHidlSupplicantP2pIface.setListenChannel(
                channel, operatingClass);
        logCompletion("setListenChannel()", status);
        return status.code == SupplicantStatusCode.SUCCESS;
    }


    /**
     * This command can be used to add a upnp/bonjour service.
     *
     * @param query Hex dump of the query data.
     * @param return Hex dump of the response data.
     *
     * @return true, if operation was successful.
     *
     * TODO(ender): process parcelled string, convert hex strings to byte array,
     * call relevant methods of the mHidlSupplicantP2pIface.
     */
    public boolean serviceAdd(WifiP2pServiceInfo servInfo) {
        if (mHidlSupplicantP2pIface == null) return false;
        SupplicantStatus status;

        for (String s : servInfo.getSupplicantQueryList()) {
            String[] data = s.split(" ");
            if (data.length < 2) {
                Log.e(TAG, "Service specification invalid: " + s);
                return false;
            }

            if ("upnp".equals(data[0])) {
                int version = 0;
                try {
                    version = Integer.parseInt(data[1]);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "UPnP Service specification invalid: " + s, e);
                    return false;
                }
                logd("addUpnpService(" + data[1] + ", " + data[2] + ")");
                status = mHidlSupplicantP2pIface.addUpnpService(version, data[2]);
                logCompletion("addUpnpService()", status);
            } else if ("bonjour".equals(data[0])) {
                ArrayList<Byte> request = hexStringToByteArray(data[1]);
                ArrayList<Byte> response = hexStringToByteArray(data[2]);
                logd("addBonjourService(" + data[1] + ", " + data[2] + ")");
                status = mHidlSupplicantP2pIface.addBonjourService(request, response);
                logCompletion("addBonjourService()", status);
            } else {
                return false;
            }

            if (status.code != SupplicantStatusCode.SUCCESS) return false;
        }

        return true;
    }


    /**
     * This command can be used to remove a upnp/bonjour service.
     *
     * @param query Hex dump of the query data.
     *
     * @return true, if operation was successful.
     *
     * TODO(ender): process parcelled string, convert hex strings to byte array,
     * call relevant methods of the mHidlSupplicantP2pIface.
     */
    public boolean serviceRemove(WifiP2pServiceInfo servInfo) {
        if (mHidlSupplicantP2pIface == null) return false;
        SupplicantStatus status;

        for (String s : servInfo.getSupplicantQueryList()) {
            String[] data = s.split(" ");
            if (data.length < 2) {
                Log.e(TAG, "Service specification invalid: " + s);
                return false;
            }

            if ("upnp".equals(data[0])) {
                int version = 0;
                try {
                    version = Integer.parseInt(data[1]);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "UPnP Service specification invalid: " + s, e);
                    return false;
                }
                logd("removeUpnpService(" + data[1] + ", " + data[2] + ")");
                status = mHidlSupplicantP2pIface.removeUpnpService(version, data[2]);
                logCompletion("removeUpnpService()", status);
            } else if ("bonjour".equals(data[0])) {
                ArrayList<Byte> request = hexStringToByteArray(data[1]);
                logd("removeBonjourService(" + data[1] + ")");
                status = mHidlSupplicantP2pIface.removeBonjourService(request);
                logCompletion("removeBonjourService()", status);
            } else {
                return false;
            }

            if (status.code != SupplicantStatusCode.SUCCESS) return false;
        }

        return true;
    }
}
