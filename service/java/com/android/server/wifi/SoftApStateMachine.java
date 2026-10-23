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
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.IApInterface;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import com.android.internal.app.IBatteryStats;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.util.ApConfigUtil;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * CAF/QTI soft AP state machine.
 *
 * The device pulls the soft AP controller out of {@link WifiController} into this top level class
 * and extends it with dual SAP support. It is driven by {@link WifiServiceImpl}.
 */
public class SoftApStateMachine extends StateMachine {
    static final int BASE = 131072;
    static final int CMD_START_AP = BASE + 21;
    static final int CMD_START_AP_FAILURE = BASE + 22;
    static final int CMD_STOP_AP = BASE + 23;
    static final int CMD_AP_STOPPED = BASE + 24;
    public static final int CMD_BOOT_COMPLETED = BASE + 134;

    private static final String TAG = "SoftApStateMachine";
    private static boolean DBG = false;

    private final Context mContext;
    private final WifiInjector mWifiInjector;
    private final WifiNative mWifiNative;
    private final WifiStateTracker mWifiStateTracker;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiApConfigStore mWifiApConfigStore;
    private final INetworkManagementService mNwService;
    private final IBatteryStats mBatteryStats;

    private final AtomicInteger mWifiApState = new AtomicInteger(WifiManager.WIFI_AP_STATE_DISABLED);
    private final State mInitialState = new InitialState();
    private final State mSoftApState = new SoftApState();

    private ConnectivityManager mCm;

    private String mInterfaceName;
    private int mSoftApChannel;

    class InitialState extends State {
        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case CMD_START_AP:
                    transitionTo(mSoftApState);
                    return true;
                default:
                    return false;
            }
        }
    }

    class SoftApState extends State {
        private String mIfaceName;
        private int mMode;
        private SoftApManager mSoftApManager;

        private class SoftApListener implements SoftApManager.Listener {
            @Override
            public void onStateChanged(int state, int reason) {
                if (state == WifiManager.WIFI_AP_STATE_DISABLED) {
                    mWifiNative.addOrRemoveInterface(mInterfaceName, false, false);
                    sendMessage(CMD_AP_STOPPED);
                } else if (state == WifiManager.WIFI_AP_STATE_FAILED) {
                    mWifiNative.addOrRemoveInterface(mInterfaceName, false, false);
                    sendMessage(CMD_START_AP_FAILURE);
                }
                setWifiApState(state, reason, mIfaceName, mMode);
            }
        }

        @Override
        public void enter() {
            Message currentMessage = getCurrentMessage();
            if (currentMessage.what != CMD_START_AP) {
                throw new RuntimeException("Illegal transition to SoftApState: " + currentMessage);
            }
            SoftApModeConfiguration softApModeConfiguration =
                    (SoftApModeConfiguration) currentMessage.obj;
            mMode = softApModeConfiguration.getTargetMode();

            Pair<Integer, IApInterface> result =
                    mWifiNative.setupForSoftApMode(mInterfaceName, false);
            IApInterface apInterface = null;
            if (result.first == WifiNative.SETUP_SUCCESS) {
                apInterface = result.second;
            } else {
                Log.e(TAG, "Error in wifi onFailure due to HAL");
            }
            if (apInterface == null) {
                mWifiNative.addOrRemoveInterface(mInterfaceName, false, false);
                setWifiApState(WifiManager.WIFI_AP_STATE_FAILED, 0, null, mMode);
                transitionTo(mInitialState);
                return;
            }
            try {
                mIfaceName = apInterface.getInterfaceName();
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to get AP interface name");
            }

            WifiStateMachine wifiStateMachine = mWifiInjector.getWifiStateMachine();
            WifiConfiguration currentWifiConfiguration =
                    wifiStateMachine.getCurrentWifiConfiguration();
            if (currentWifiConfiguration != null && currentWifiConfiguration.shareThisAp) {
                softApModeConfiguration =
                        new SoftApModeConfiguration(mMode, currentWifiConfiguration);
                WifiConfiguration configuredNetworkWithPassword =
                        mWifiConfigManager.getConfiguredNetworkWithPassword(
                                currentWifiConfiguration.networkId);
                softApModeConfiguration.mConfig.SSID =
                        ApConfigUtil.removeDoubleQuotes(configuredNetworkWithPassword.SSID);
                softApModeConfiguration.mConfig.apBand = configuredNetworkWithPassword.apBand;
                softApModeConfiguration.mConfig.apChannel =
                        configuredNetworkWithPassword.apChannel;
                softApModeConfiguration.mConfig.hiddenSSID =
                        configuredNetworkWithPassword.hiddenSSID;
                softApModeConfiguration.mConfig.preSharedKey =
                        ApConfigUtil.removeDoubleQuotes(configuredNetworkWithPassword.preSharedKey);
                softApModeConfiguration.mConfig.allowedKeyManagement =
                        configuredNetworkWithPassword.allowedKeyManagement;
                mSoftApChannel = configuredNetworkWithPassword.apChannel;
                WifiInfo wifiInfo = wifiStateMachine.getWifiInfo();
                if (wifiInfo != null && softApModeConfiguration.mConfig.apChannel == 0) {
                    softApModeConfiguration.mConfig.apChannel =
                            ApConfigUtil.convertFrequencyToChannel(wifiInfo.getFrequency());
                    mSoftApChannel = softApModeConfiguration.mConfig.apChannel;
                }
            } else if (currentWifiConfiguration != null && !currentWifiConfiguration.shareThisAp) {
                mSoftApChannel = 0;
            }

            checkAndSetConnectivityInstance();
            mSoftApManager = mWifiInjector.makeSoftApManager(mNwService, new SoftApListener(),
                    apInterface, softApModeConfiguration.getWifiConfiguration());
            if (mSoftApChannel != 0) {
                mSoftApManager.setSapChannel(mSoftApChannel);
            }
            mSoftApManager.start();
            mWifiStateTracker.updateState(WifiStateTracker.SOFT_AP);
        }

        @Override
        public void exit() {
            mSoftApManager = null;
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case CMD_START_AP:
                    return true;
                case CMD_START_AP_FAILURE:
                    transitionTo(mInitialState);
                    return true;
                case CMD_STOP_AP:
                    mSoftApManager.stop();
                    return true;
                case CMD_AP_STOPPED:
                    transitionTo(mInitialState);
                    return true;
                default:
                    return false;
            }
        }
    }

    public SoftApStateMachine(Context context, WifiInjector wifiInjector, WifiNative wifiNative,
            INetworkManagementService nwService, IBatteryStats batteryStats) {
        super(TAG);
        mContext = context;
        mWifiInjector = wifiInjector;
        mWifiStateTracker = wifiInjector.getWifiStateTracker();
        mWifiConfigManager = wifiInjector.getWifiConfigManager();
        mWifiApConfigStore = wifiInjector.getWifiApConfigStore();
        mWifiNative = wifiNative;
        mNwService = nwService;
        mBatteryStats = batteryStats;
        addState(mInitialState);
        addState(mSoftApState, mInitialState);
        setInitialState(mInitialState);
        start();
    }

    private void checkAndSetConnectivityInstance() {
        if (mCm == null) {
            mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }

    private Message obtainMessageWithWhatAndArg2(Message message, int what) {
        Message obtained = Message.obtain();
        obtained.what = what;
        obtained.arg2 = message.arg2;
        return obtained;
    }

    /**
     * Update the soft AP state, notify battery stats and the driver, and broadcast the change.
     * The device additionally decides whether the wifi driver can be fully unloaded or must be
     * kept because the client interface is still enabled/enabling.
     */
    public void setWifiApState(int apState, int reason, String ifaceName, int mode) {
        int previousApState = mWifiApState.get();

        try {
            if (apState == WifiManager.WIFI_AP_STATE_ENABLED) {
                mBatteryStats.noteWifiOn();
            } else if (apState == WifiManager.WIFI_AP_STATE_DISABLED) {
                mBatteryStats.noteWifiOff();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to note battery stats in wifi");
        }

        WifiStateMachine wifiStateMachine = mWifiInjector.getWifiStateMachine();
        int wifiState = wifiStateMachine.syncGetWifiState();
        int operationalMode = wifiStateMachine.getOperationalMode();
        if (wifiState != WifiManager.WIFI_STATE_ENABLING
                || operationalMode == WifiStateMachine.CONNECT_MODE) {
            Log.d(TAG, "Avoid unload driver, WIFI_STATE is enabled/enabling");
            mWifiNative.tearDownAp();
        } else {
            wifiStateMachine.cleanup();
        }

        mWifiApState.set(apState);
        if (DBG) {
            Log.d(TAG, "setWifiApState: " + syncGetWifiApStateByName());
        }

        Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra("wifi_state", apState);
        intent.putExtra("previous_wifi_state", previousApState);
        if (apState == WifiManager.WIFI_AP_STATE_FAILED) {
            intent.putExtra("wifi_ap_error_code", reason);
        }
        if (ifaceName == null) {
            Log.e(TAG, "Updating wifiApState with a null iface name");
        }
        intent.putExtra("wifi_ap_interface_name", ifaceName);
        intent.putExtra("wifi_ap_mode", mode);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    void enableVerboseLogging(int verbose) {
        DBG = verbose > 0;
    }

    public boolean isExtendingNetworkCoverage() {
        WifiConfiguration currentWifiConfiguration =
                mWifiInjector.getWifiStateMachine().getCurrentWifiConfiguration();
        return currentWifiConfiguration != null && currentWifiConfiguration.shareThisAp;
    }

    public void setHostApRunning(SoftApModeConfiguration softApModeConfiguration, boolean enable) {
        if (enable) {
            sendMessage(CMD_START_AP, softApModeConfiguration);
        } else {
            sendMessage(CMD_STOP_AP);
        }
    }

    public void setSoftApChannel(int channel) {
        mSoftApChannel = channel;
    }

    public void setSoftApInterfaceName(String interfaceName) {
        mInterfaceName = interfaceName;
    }

    public int syncGetWifiApState() {
        return mWifiApState.get();
    }

    public String syncGetWifiApStateByName() {
        switch (mWifiApState.get()) {
            case WifiManager.WIFI_AP_STATE_DISABLING:
                return "disabling";
            case WifiManager.WIFI_AP_STATE_DISABLED:
                return "disabled";
            case WifiManager.WIFI_AP_STATE_ENABLING:
                return "enabling";
            case WifiManager.WIFI_AP_STATE_ENABLED:
                return "enabled";
            case WifiManager.WIFI_AP_STATE_FAILED:
                return "failed";
            default:
                return "[invalid state]";
        }
    }
}
