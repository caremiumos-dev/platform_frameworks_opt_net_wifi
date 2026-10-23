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

import static com.android.server.wifi.util.ApConfigUtil.ERROR_GENERIC;
import static com.android.server.wifi.util.ApConfigUtil.ERROR_NO_CHANNEL;
import static com.android.server.wifi.util.ApConfigUtil.SUCCESS;

import android.content.Context;
import android.net.InterfaceConfiguration;
import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.IInterfaceEventCallback;
import android.net.wifi.IWificond;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.net.BaseNetworkObserver;
import com.android.server.wifi.util.ApConfigUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Manage WiFi in AP mode.
 * The internal state machine runs under "WifiStateMachine" thread context.
 */
public class SoftApManager implements ActiveModeManager {
    private static final String TAG = "SoftApManager";
    private static final boolean DBG = true;
    private static final int DNSMASQ_POLLING_INTERVAL = 1000;
    private static final int DNSMASQ_POLLING_MAX_TIMES = 10;

    private final WifiNative mWifiNative;
    private final String mCountryCode;
    private final SoftApStateMachine mStateMachine;
    private final Listener mListener;
    private final IApInterface mApInterface;
    private final INetworkManagementService mNwService;
    private final WifiApConfigStore mWifiApConfigStore;
    private final WifiMetrics mWifiMetrics;
    private final Context mContext;
    private final WifiSoftApNotificationManager mWifiSoftApNotificationManager;

    private WifiConfiguration mApConfig;
    private int mSoftApChannel = 0;
    private int mLastSoftApNotificationId = 0;
    private boolean mDualSapMode = false;

    /**
     * Listener for soft AP state changes.
     */
    public interface Listener {
        /**
         * Invoke when AP state changed.
         * @param state new AP state
         * @param failureReason reason when in failed state
         */
        void onStateChanged(int state, int failureReason);
    }

    /**
     * CAF/QTI vendor extension: receive wificond interface/soft AP client events.
     */
    private static class InterfaceEventHandler extends IInterfaceEventCallback.Stub {
        private final SoftApStateMachine mSoftApStateMachine;

        InterfaceEventHandler(SoftApStateMachine softApStateMachine) {
            mSoftApStateMachine = softApStateMachine;
        }

        @Override
        public void OnApInterfaceReady(IApInterface networkInterface) {
        }

        @Override
        public void OnApTorndownEvent(IApInterface networkInterface) {
        }

        @Override
        public void OnClientInterfaceReady(IClientInterface networkInterface) {
        }

        @Override
        public void OnClientTorndownEvent(IClientInterface networkInterface) {
        }

        @Override
        public void OnSoftApClientEvent(byte[] macAddress, boolean connected) {
            StringBuilder sb = new StringBuilder(18);
            for (byte b : macAddress) {
                if (sb.length() > 0) {
                    sb.append(':');
                }
                sb.append(String.format("%02x", b));
            }
            Log.d(TAG, "Client mac_addr = " + sb.toString() + " status = " + connected);
            mSoftApStateMachine.sendMessage(
                    SoftApStateMachine.CMD_SOFTAP_CLIENT_CONNECT_STATUS_CHANGED,
                    connected ? 1 : 0, 0, sb.toString());
        }
    }

    private class SoftApStateMachine extends StateMachine {
        public static final int CMD_START = 0;
        public static final int CMD_STOP = 1;
        public static final int CMD_AP_INTERFACE_BINDER_DEATH = 2;
        public static final int CMD_INTERFACE_STATUS_CHANGED = 3;
        public static final int CMD_SOFTAP_CLIENT_CONNECT_STATUS_CHANGED = 4;

        private final State mIdleState = new IdleState();
        private final State mStartedState = new StartedState();

        private final StateMachineDeathRecipient mDeathRecipient =
                new StateMachineDeathRecipient(this, CMD_AP_INTERFACE_BINDER_DEATH);

        private final WifiInjector mWifiInjector;
        private IWificond mWificond;
        private IInterfaceEventCallback mInterfaceEventHandler;
        private NetworkObserver mNetworkObserver;

        private class NetworkObserver extends BaseNetworkObserver {
            private final String mIfaceName;

            NetworkObserver(String ifaceName) {
                mIfaceName = ifaceName;
            }

            @Override
            public void interfaceLinkStateChanged(String iface, boolean up) {
                if (mIfaceName.equals(iface)) {
                    sendMessage(CMD_INTERFACE_STATUS_CHANGED, up ? 1 : 0, 0, this);
                }
            }
        }

        SoftApStateMachine(Looper looper, WifiInjector wifiInjector) {
            super(TAG, looper);
            mWifiInjector = wifiInjector;
            mInterfaceEventHandler = new InterfaceEventHandler(this);
            addState(mIdleState);
            addState(mStartedState);
            setInitialState(mIdleState);
            start();
        }

        private class IdleState extends State {
            @Override
            public void enter() {
                mDeathRecipient.unlinkToDeath();
                unregisterObserver();
                mWificond = mWifiInjector.makeWificond();
                if (mWificond == null) {
                    Log.w(TAG, "Failed to get wificond binder handler");
                } else {
                    try {
                        mWificond.RegisterCallback(mInterfaceEventHandler);
                    } catch (RemoteException e) {
                    }
                }
            }

            private void unregisterObserver() {
                if (mNetworkObserver == null) {
                    return;
                }
                try {
                    mNwService.unregisterObserver(mNetworkObserver);
                } catch (RemoteException e) {
                }
                mNetworkObserver = null;
            }

            @Override
            public boolean processMessage(Message message) {
                switch (message.what) {
                    case CMD_START:
                        updateApState(WifiManager.WIFI_AP_STATE_ENABLING, 0);
                        if (!mDeathRecipient.linkToDeath(mApInterface.asBinder())) {
                            mDeathRecipient.unlinkToDeath();
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                    WifiManager.SAP_START_FAILURE_GENERAL);
                            mWifiMetrics.incrementSoftApStartResult(
                                    false, WifiManager.SAP_START_FAILURE_GENERAL);
                            break;
                        }
                        try {
                            mNetworkObserver = new NetworkObserver(
                                    mApInterface.getInterfaceName());
                            mNwService.registerObserver(mNetworkObserver);
                            int result = startSoftAp((WifiConfiguration) message.obj);
                            if (result == SUCCESS) {
                                transitionTo(mStartedState);
                            } else {
                                int failureReason = WifiManager.SAP_START_FAILURE_GENERAL;
                                if (result == ERROR_NO_CHANNEL) {
                                    failureReason = WifiManager.SAP_START_FAILURE_NO_CHANNEL;
                                }
                                mDeathRecipient.unlinkToDeath();
                                unregisterObserver();
                                updateApState(WifiManager.WIFI_AP_STATE_FAILED, failureReason);
                                mWifiMetrics.incrementSoftApStartResult(false, failureReason);
                            }
                        } catch (RemoteException e) {
                            mDeathRecipient.unlinkToDeath();
                            unregisterObserver();
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                    WifiManager.SAP_START_FAILURE_GENERAL);
                            mWifiMetrics.incrementSoftApStartResult(
                                    false, WifiManager.SAP_START_FAILURE_GENERAL);
                        }
                        break;
                    default:
                        return true;
                }
                return HANDLED;
            }
        }

        private class StartedState extends State {
            private boolean mIfaceIsUp;

            private void onUpChanged(boolean isUp) {
                if (isUp == mIfaceIsUp) {
                    return;  // no change
                }
                mIfaceIsUp = isUp;
                if (isUp) {
                    Log.d(TAG, "SoftAp is ready for use");
                    updateApState(WifiManager.WIFI_AP_STATE_ENABLED, 0);
                    mWifiMetrics.incrementSoftApStartResult(true, 0);
                }
            }

            @Override
            public void enter() {
                mIfaceIsUp = false;
                InterfaceConfiguration config = null;
                try {
                    config = mNwService.getInterfaceConfig(mApInterface.getInterfaceName());
                } catch (RemoteException e) {
                }
                if (config != null) {
                    onUpChanged(config.isActive());
                }
            }

            @Override
            public boolean processMessage(Message message) {
                switch (message.what) {
                    case CMD_START:
                        // Already started, ignore this command.
                        return true;
                    case CMD_STOP:
                    case CMD_AP_INTERFACE_BINDER_DEATH:
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING, 0);
                        stopSoftAp();
                        if (message.what == CMD_AP_INTERFACE_BINDER_DEATH) {
                            updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                    WifiManager.SAP_START_FAILURE_GENERAL);
                        } else {
                            updateApState(WifiManager.WIFI_AP_STATE_DISABLED, 0);
                        }
                        transitionTo(mIdleState);
                        mWifiSoftApNotificationManager.clearSoftApClientsNotification();
                        mWifiSoftApNotificationManager.clearConnectedDevice();
                        try {
                            if (mWificond != null) {
                                mWificond.UnregisterCallback(mInterfaceEventHandler);
                            }
                        } catch (RemoteException e) {
                        }
                        mInterfaceEventHandler = null;
                        return true;
                    case CMD_INTERFACE_STATUS_CHANGED:
                        if (message.obj == mNetworkObserver) {
                            onUpChanged(message.arg1 == 1);
                        }
                        return true;
                    case CMD_SOFTAP_CLIENT_CONNECT_STATUS_CHANGED:
                        mWifiSoftApNotificationManager.connectionStatusChange(message);
                        return true;
                    default:
                        return NOT_HANDLED;
                }
            }
        }
    }

    public SoftApManager(Looper looper,
                         WifiNative wifiNative,
                         String countryCode,
                         Listener listener,
                         IApInterface apInterface,
                         INetworkManagementService nms,
                         WifiApConfigStore wifiApConfigStore,
                         WifiConfiguration config,
                         WifiMetrics wifiMetrics,
                         WifiInjector wifiInjector,
                         Context context) {
        mStateMachine = new SoftApStateMachine(looper, wifiInjector);
        mWifiSoftApNotificationManager = WifiSoftApNotificationManager.getInstance(context);

        mWifiNative = wifiNative;
        mCountryCode = countryCode;
        mListener = listener;
        mApInterface = apInterface;
        mNwService = nms;
        mWifiApConfigStore = wifiApConfigStore;
        if (config == null) {
            mApConfig = mWifiApConfigStore.getApConfiguration();
        } else {
            mApConfig = config;
        }
        mWifiMetrics = wifiMetrics;
        mContext = context;
    }

    private static int getIApInterfaceEncryptionType(WifiConfiguration localConfig) {
        switch (localConfig.getAuthType()) {
            case KeyMgmt.NONE:
                return IApInterface.ENCRYPTION_TYPE_NONE;
            case KeyMgmt.WPA_PSK:
                return IApInterface.ENCRYPTION_TYPE_WPA;
            case KeyMgmt.WPA2_PSK:
                return IApInterface.ENCRYPTION_TYPE_WPA2;
            default:
                // We really shouldn't default to None, but this was how NetworkManagementService
                // used to do this.
                return IApInterface.ENCRYPTION_TYPE_NONE;
        }
    }

    private int startDualSoftAp(WifiConfiguration config) {
        WifiConfiguration localConfig = new WifiConfiguration(config);

        if (mCountryCode != null
                && !mWifiNative.setCountryCodeHal(mCountryCode.toUpperCase(Locale.ROOT))) {
            Log.e(TAG, "Failed to set country code, required for setting up soft ap in 5GHz");
            return ERROR_GENERIC;
        }

        try {
            if (!writeDualHostapdConfig(localConfig)) {
                Log.e(TAG, "Failed to write dual hostapd configuration");
                return ERROR_GENERIC;
            }
            boolean success = mApInterface.startHostapd(mDualSapMode);
            mWifiNative.runQsapCmd("softap bridge up ", mApInterface.getInterfaceName());
            if (!success) {
                Log.e(TAG, "Failed to start hostapd.");
                return ERROR_GENERIC;
            }
            Log.d(TAG, "Dual Soft AP is started");
            return SUCCESS;
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in starting dual soft AP: " + e);
        }
        return ERROR_GENERIC;
    }

    /**
     * Start a soft AP instance with the given configuration.
     * @param config AP configuration
     * @return integer result code
     */
    private int startSoftAp(WifiConfiguration config) {
        if (config == null || config.SSID == null) {
            Log.e(TAG, "Unable to start soft AP without valid configuration");
            return ERROR_GENERIC;
        }

        if (mDualSapMode) {
            return startDualSoftAp(config);
        }

        // Make a copy of configuration for updating AP band and channel.
        WifiConfiguration localConfig = new WifiConfiguration(config);

        int result = ApConfigUtil.updateApChannelConfig(
                mWifiNative, mCountryCode,
                mWifiApConfigStore.getAllowed2GChannel(), localConfig);
        if (result != SUCCESS) {
            Log.e(TAG, "Failed to update AP band and channel");
            return result;
        }

        // Setup country code if it is provided.
        if (mCountryCode != null) {
            // Country code is mandatory for 5GHz band, return an error if failed to set
            // country code when AP is configured for 5GHz band.
            if (!mWifiNative.setCountryCodeHal(mCountryCode.toUpperCase(Locale.ROOT))
                    && config.apBand == WifiConfiguration.AP_BAND_5GHZ) {
                Log.e(TAG, "Failed to set country code, required for setting up soft ap in 5GHz");
                return ERROR_GENERIC;
            }
        }

        int encryptionType = getIApInterfaceEncryptionType(localConfig);

        if (localConfig.hiddenSSID) {
            Log.d(TAG, "SoftAP is a hidden network");
        }

        try {
            // CAF/QTI vendor extension: force the requested SAP channel for the 2.4GHz band.
            if (localConfig.apBand != WifiConfiguration.AP_BAND_5GHZ && mSoftApChannel != 0) {
                localConfig.apBand = WifiConfiguration.AP_BAND_2GHZ;
                localConfig.apChannel = mSoftApChannel;
                config.apChannel = localConfig.apChannel;
            }

            // Note that localConfig.SSID is intended to be either a hex string or "double quoted".
            // However, it seems that whatever is handing us these configurations does not obey
            // this convention.
            boolean success = mApInterface.writeHostapdConfig(
                    localConfig.SSID.getBytes(StandardCharsets.UTF_8), localConfig.hiddenSSID,
                    localConfig.apChannel, encryptionType,
                    (localConfig.preSharedKey != null)
                            ? localConfig.preSharedKey.getBytes(StandardCharsets.UTF_8)
                            : new byte[0]);
            if (!success) {
                Log.e(TAG, "Failed to write hostapd configuration");
                return ERROR_GENERIC;
            }

            // CAF/QTI vendor extension: enable automatic channel selection when configured.
            try {
                if (Settings.Secure.getInt(mContext.getContentResolver(),
                        "wifi_softap_acs_enabled", 0) > 0) {
                    mWifiNative.runQsapCmd("softap qccmd set channel=", "0");
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Exception in get softap_acs");
            }

            success = mApInterface.startHostapd(false);
            if (!success) {
                Log.e(TAG, "Failed to start hostapd.");
                return ERROR_GENERIC;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in starting soft AP: " + e);
        }

        Log.d(TAG, "Soft AP is started");

        return SUCCESS;
    }

    /**
     * Teardown soft AP.
     */
    private void stopSoftAp() {
        try {
            mApConfig.apChannel = 0;
            mApInterface.stopHostapd(mDualSapMode);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in stopping soft AP: " + e);
            return;
        }
        Log.d(TAG, "Soft AP is stopped");
    }

    private boolean writeDualHostapdConfig(WifiConfiguration config) {
        String[] dualSapInterfaces = mWifiApConfigStore.getDualSapInterfaces();
        if (dualSapInterfaces == null || dualSapInterfaces.length != 2) {
            Log.e(TAG, " dualApInterfaces is not set or length is not 2");
            return false;
        }
        String ssid = String.format("%x",
                new BigInteger(1, config.SSID.getBytes(StandardCharsets.UTF_8)));
        String security;
        switch (config.getAuthType()) {
            case KeyMgmt.WPA_PSK:
                security = "wpa-psk " + config.preSharedKey;
                break;
            case KeyMgmt.WPA2_PSK:
                security = "wpa2-psk " + config.preSharedKey;
                break;
            default:
                security = "open";
                break;
        }
        try {
            return mWifiNative.runQsapCmd("softap setsoftap dual2g "
                            + dualSapInterfaces[0] + " " + ssid + " visible 0 " + security, "")
                    && mWifiNative.runQsapCmd("softap setsoftap dual5g "
                            + dualSapInterfaces[1] + " " + ssid + " visible 0 " + security, "")
                    && mWifiNative.runQsapCmd("softap qccmd set dual2g hw_mode=", "g")
                    && mWifiNative.runQsapCmd("softap qccmd set dual5g hw_mode=", "a")
                    && mWifiNative.runQsapCmd("softap qccmd set dual2g bridge=",
                            mApInterface.getInterfaceName())
                    && mWifiNative.runQsapCmd("softap qccmd set dual5g bridge=",
                            mApInterface.getInterfaceName());
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in configuring softap for dual mode: " + e);
        }
        return false;
    }

    /**
     * CAF/QTI vendor extension: enable/disable dual SAP mode.
     */
    public void setDualSapMode(boolean dualSapMode) {
        mDualSapMode = dualSapMode;
    }

    /**
     * CAF/QTI vendor extension: set the SAP channel to force.
     */
    public void setSapChannel(int channel) {
        mSoftApChannel = channel;
    }

    /**
     * Update AP state.
     * @param state new AP state
     * @param reason Failure reason if the new AP state is in failure state
     */
    private void updateApState(int state, int reason) {
        if (mListener != null) {
            mListener.onStateChanged(state, reason);
        }
    }

    /**
     * Start soft AP with the supplied config.
     */
    @Override
    public void start() {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_START, mApConfig);
    }

    /**
     * Stop soft AP.
     */
    @Override
    public void stop() {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_STOP);
    }
}
