/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;
import static android.net.wifi.WifiManager.WIFI_MODE_NO_LOCKS_HELD;
import static android.net.wifi.WifiManager.WIFI_MODE_SCAN_ONLY;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.WorkSource;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * WifiController is the class used to manage on/off state of WifiStateMachine for various operating
 * modes (normal, airplane, wifi hotspot, etc.).
 *
 * The device (CAF/QTI) additionally adds a STA + SAP concurrency state machine (the Qc* states).
 */
public class WifiController extends StateMachine {
    private static final String TAG = "WifiController";
    private static boolean DBG = false;

    private Context mContext;
    private boolean mScreenOff;
    private boolean mDeviceIdle;
    private int mPluggedType;
    private int mStayAwakeConditions;
    private long mIdleMillis;
    private int mSleepPolicy;
    private boolean mFirstUserSignOnSeen = false;

    // CAF/QTI vendor extension: the soft AP state machine and STA+SAP concurrency.
    private SoftApStateMachine mSoftApStateMachine;
    private boolean mStaAndApConcurrency = false;
    private boolean mRestartStaSapStack = false;

    private AlarmManager mAlarmManager;
    private PendingIntent mIdleIntent;
    private static final int IDLE_REQUEST = 0;

    /**
     * See {@link Settings.Global#WIFI_IDLE_MS}. This is the default value if a
     * Settings.Global value is not present. This timeout value is chosen as
     * the approximate point at which the battery drain caused by Wi-Fi
     * being enabled but not active exceeds the battery drain caused by
     * re-establishing a connection to the mobile data network.
     */
    private static final long DEFAULT_IDLE_MS = 15 * 60 * 1000; /* 15 minutes */

    /**
     * See {@link Settings.Global#WIFI_REENABLE_DELAY_MS}.  This is the default value if a
     * Settings.Global value is not present.  This is the minimum time after wifi is disabled
     * we'll act on an enable.  Enable requests received before this delay will be deferred.
     */
    private static final long DEFAULT_REENABLE_DELAY_MS = 500;

    // finding that delayed messages can sometimes be delivered earlier than expected
    // probably rounding errors..  add a margin to prevent problems
    private static final long DEFER_MARGIN_MS = 5;

    NetworkInfo mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");

    /* References to values tracked in WifiService */
    private final WifiStateMachine mWifiStateMachine;
    private final WifiSettingsStore mSettingsStore;
    private final WifiLockManager mWifiLockManager;

    /**
     * Temporary for computing UIDS that are responsible for starting WIFI.
     * Protected by mWifiStateTracker lock.
     */
    private final WorkSource mTmpWorkSource = new WorkSource();

    private long mReEnableDelayMillis;

    private FrameworkFacade mFacade;

    private static final int BASE = Protocol.BASE_WIFI_CONTROLLER;

    static final int CMD_EMERGENCY_MODE_CHANGED        = BASE + 1;
    static final int CMD_SCREEN_ON                     = BASE + 2;
    static final int CMD_SCREEN_OFF                    = BASE + 3;
    static final int CMD_BATTERY_CHANGED               = BASE + 4;
    static final int CMD_DEVICE_IDLE                   = BASE + 5;
    static final int CMD_LOCKS_CHANGED                 = BASE + 6;
    static final int CMD_SCAN_ALWAYS_MODE_CHANGED      = BASE + 7;
    static final int CMD_WIFI_TOGGLED                  = BASE + 8;
    static final int CMD_AIRPLANE_TOGGLED              = BASE + 9;
    static final int CMD_SET_AP                        = BASE + 10;
    static final int CMD_DEFERRED_TOGGLE               = BASE + 11;
    static final int CMD_USER_PRESENT                  = BASE + 12;
    static final int CMD_AP_START_FAILURE              = BASE + 13;
    static final int CMD_EMERGENCY_CALL_STATE_CHANGED  = BASE + 14;
    static final int CMD_AP_STOPPED                    = BASE + 15;
    static final int CMD_STA_START_FAILURE             = BASE + 16;
    // Command used to trigger a wifi stack restart when in active mode
    static final int CMD_RESTART_WIFI                  = BASE + 17;
    // Internal command used to complete wifi stack restart
    private static final int CMD_RESTART_WIFI_CONTINUE = BASE + 18;
    // CAF/QTI vendor extension: additional commands used for STA+SAP concurrency.
    private static final int CMD_WIFI_ENABLED          = BASE + 19;
    private static final int CMD_WIFI_DISABLED         = BASE + 20;
    static final int CMD_AP_STARTED                    = BASE + 21;
    private static final int CMD_AP_STOP_FAILURE       = BASE + 22;
    private static final int CMD_WIFI_STOP_FAILURE     = BASE + 23;
    static final int CMD_WIFI_SLEEP_POLICY_CHANGE      = BASE + 24;

    private static final String ACTION_DEVICE_IDLE =
            "com.android.server.WifiManager.action.DEVICE_IDLE";

    private DefaultState mDefaultState = new DefaultState();
    private StaEnabledState mStaEnabledState = new StaEnabledState();
    private ApStaDisabledState mApStaDisabledState = new ApStaDisabledState();
    private StaDisabledWithScanState mStaDisabledWithScanState = new StaDisabledWithScanState();
    private ApEnabledState mApEnabledState = new ApEnabledState();
    private QcApStaEnabledState mQcApStaEnabledState = new QcApStaEnabledState();
    private DeviceActiveState mDeviceActiveState = new DeviceActiveState();
    private DeviceInactiveState mDeviceInactiveState = new DeviceInactiveState();
    private ScanOnlyLockHeldState mScanOnlyLockHeldState = new ScanOnlyLockHeldState();
    private FullLockHeldState mFullLockHeldState = new FullLockHeldState();
    private FullHighPerfLockHeldState mFullHighPerfLockHeldState = new FullHighPerfLockHeldState();
    private NoLockHeldState mNoLockHeldState = new NoLockHeldState();
    private EcmState mEcmState = new EcmState();
    private QcStaEnablingState mQcStaEnablingState = new QcStaEnablingState();
    private QcStaDisablingState mQcStaDisablingState = new QcStaDisablingState();
    private QcApEnablingState mQcApEnablingState = new QcApEnablingState();
    private QcApDisablingState mQcApDisablingState = new QcApDisablingState();
    private QcApStaEnablingState mQcApStaEnablingState = new QcApStaEnablingState();
    private QcApStaDisablingState mQcApStaDisablingState = new QcApStaDisablingState();

    WifiController(Context context, WifiStateMachine wsm, WifiSettingsStore wss,
            WifiLockManager wifiLockManager, Looper looper, FrameworkFacade f,
            WifiApConfigStore wifiApConfigStore) {
        super(TAG, looper);
        mFacade = f;
        mContext = context;
        mWifiStateMachine = wsm;
        mSettingsStore = wss;
        mWifiLockManager = wifiLockManager;
        mWifiApConfigStore = wifiApConfigStore;

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        Intent idleIntent = new Intent(ACTION_DEVICE_IDLE, null);
        mIdleIntent = mFacade.getBroadcast(mContext, IDLE_REQUEST, idleIntent, 0);

        addState(mDefaultState);
            addState(mApStaDisabledState, mDefaultState);
            addState(mStaEnabledState, mDefaultState);
            addState(mQcStaEnablingState, mDefaultState);
            addState(mQcStaDisablingState, mDefaultState);
            addState(mQcApEnablingState, mDefaultState);
            addState(mQcApDisablingState, mDefaultState);
            addState(mQcApStaEnablingState, mDefaultState);
            addState(mQcApStaDisablingState, mDefaultState);
            addState(mQcApStaEnabledState, mDefaultState);
                addState(mDeviceActiveState, mStaEnabledState);
                addState(mDeviceInactiveState, mStaEnabledState);
                    addState(mScanOnlyLockHeldState, mDeviceInactiveState);
                    addState(mFullLockHeldState, mDeviceInactiveState);
                    addState(mFullHighPerfLockHeldState, mDeviceInactiveState);
                    addState(mNoLockHeldState, mDeviceInactiveState);
            addState(mStaDisabledWithScanState, mDefaultState);
            addState(mApEnabledState, mDefaultState);
            addState(mEcmState, mDefaultState);

        boolean isAirplaneModeOn = mSettingsStore.isAirplaneModeOn();
        boolean isWifiEnabled = mSettingsStore.isWifiToggleEnabled();
        boolean isScanningAlwaysAvailable = mSettingsStore.isScanAlwaysAvailable();

        log("isAirplaneModeOn = " + isAirplaneModeOn +
                ", isWifiEnabled = " + isWifiEnabled +
                ", isScanningAvailable = " + isScanningAlwaysAvailable);

        if (isScanningAlwaysAvailable) {
            setInitialState(mStaDisabledWithScanState);
        } else {
            setInitialState(mApStaDisabledState);
        }

        setLogRecSize(100);
        setLogOnlyTransitions(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_DEVICE_IDLE);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (action.equals(ACTION_DEVICE_IDLE)) {
                            sendMessage(CMD_DEVICE_IDLE);
                        } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                            mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(
                                    WifiManager.EXTRA_NETWORK_INFO);
                        } else if (action.equals(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)) {
                            int state = intent.getIntExtra(
                                    WifiManager.EXTRA_WIFI_AP_STATE,
                                    WifiManager.WIFI_AP_STATE_FAILED);
                            if (state == WifiManager.WIFI_AP_STATE_FAILED) {
                                loge(TAG + "SoftAP start failed");
                                sendMessage(CMD_AP_START_FAILURE);
                            } else if (state == WifiManager.WIFI_AP_STATE_DISABLED) {
                                sendMessage(CMD_AP_STOPPED);
                            } else if (state == WifiManager.WIFI_AP_STATE_ENABLED) {
                                sendMessage(CMD_AP_STARTED);
                            }
                        } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                            int state = intent.getIntExtra(
                                    WifiManager.EXTRA_WIFI_STATE,
                                    WifiManager.WIFI_STATE_UNKNOWN);
                            if (state == WifiManager.WIFI_STATE_UNKNOWN) {
                                loge(TAG + "Wifi turn on failed");
                                sendMessage(CMD_STA_START_FAILURE);
                            } else if (state == WifiManager.WIFI_STATE_ENABLED) {
                                sendMessage(CMD_WIFI_ENABLED);
                            } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                                sendMessage(CMD_WIFI_DISABLED);
                            }
                        }
                    }
                }, filter);

        initializeAndRegisterForSettingsChange(looper);
    }

    private final WifiApConfigStore mWifiApConfigStore;

    class DefaultState extends State {
        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_EMERGENCY_MODE_CHANGED:
                case CMD_LOCKS_CHANGED:
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                case CMD_WIFI_TOGGLED:
                case CMD_AIRPLANE_TOGGLED:
                case CMD_SET_AP:
                case CMD_AP_START_FAILURE:
                case CMD_EMERGENCY_CALL_STATE_CHANGED:
                case CMD_AP_STOPPED:
                case CMD_STA_START_FAILURE:
                case CMD_RESTART_WIFI:
                case CMD_RESTART_WIFI_CONTINUE:
                case CMD_WIFI_ENABLED:
                case CMD_WIFI_DISABLED:
                case CMD_AP_STARTED:
                case CMD_AP_STOP_FAILURE:
                case CMD_WIFI_STOP_FAILURE:
                    return true;
                case CMD_SCREEN_ON:
                    mAlarmManager.cancel(mIdleIntent);
                    mScreenOff = false;
                    mDeviceIdle = false;
                    updateBatteryWorkSource();
                    return true;
                case CMD_SCREEN_OFF:
                    mScreenOff = true;
                    if (!shouldWifiStayAwake(mPluggedType)) {
                        if (mNetworkInfo.getDetailedState()
                                == NetworkInfo.DetailedState.CONNECTED) {
                            Slog.d(TAG, "CMD_SCREEN_OFF set idle timer: " + mIdleMillis + " ms");
                            mAlarmManager.set(AlarmManager.RTC,
                                    System.currentTimeMillis() + mIdleMillis, mIdleIntent);
                        } else {
                            sendMessage(CMD_DEVICE_IDLE);
                        }
                    }
                    return true;
                case CMD_BATTERY_CHANGED:
                    int pluggedType = msg.arg1;
                    if (mScreenOff && shouldWifiStayAwake(mPluggedType)
                            && !shouldWifiStayAwake(pluggedType)) {
                        long triggerTime = System.currentTimeMillis() + mIdleMillis;
                        Slog.d(TAG, "CMD_BATTERY_CHANGED set idle timer for " + mIdleMillis
                                + "ms");
                        mAlarmManager.set(AlarmManager.RTC, triggerTime, mIdleIntent);
                    }
                    mPluggedType = pluggedType;
                    return true;
                case CMD_DEVICE_IDLE:
                    mDeviceIdle = true;
                    updateBatteryWorkSource();
                    return true;
                case CMD_DEFERRED_TOGGLE:
                    log("DEFERRED_TOGGLE ignored due to state change");
                    return true;
                case CMD_USER_PRESENT:
                    mFirstUserSignOnSeen = true;
                    return true;
                case CMD_WIFI_SLEEP_POLICY_CHANGE:
                    Slog.d(TAG, "shouldWifiStayAwake(mPluggedType)= "
                            + shouldWifiStayAwake(mPluggedType));
                    if (!shouldWifiStayAwake(mPluggedType)) {
                        if (mNetworkInfo.getDetailedState()
                                == NetworkInfo.DetailedState.CONNECTED) {
                            Slog.d(TAG, "CMD_WIFI_SLEEP_POLICY_CHANGE set idle timer: "
                                    + mIdleMillis + " ms");
                            mAlarmManager.set(AlarmManager.RTC,
                                    System.currentTimeMillis() + mIdleMillis, mIdleIntent);
                        } else {
                            sendMessage(CMD_DEVICE_IDLE);
                        }
                    }
                    return true;
                default:
                    throw new RuntimeException("WifiController.handleMessage " + msg.what);
            }
        }
    }

    class ApStaDisabledState extends State {
        private long mDisabledTimestamp;
        private int mDeferredEnableSerialNumber = 0;
        private boolean mHaveDeferredEnable = false;

        private boolean doDeferEnable(Message msg) {
            long delaySoFar = SystemClock.elapsedRealtime() - mDisabledTimestamp;
            if (delaySoFar >= mReEnableDelayMillis) {
                return false;
            }

            log("WifiController msg " + msg + " deferred for "
                    + (mReEnableDelayMillis - delaySoFar) + "ms");

            Message deferredMsg = obtainMessage(CMD_DEFERRED_TOGGLE);
            deferredMsg.obj = Message.obtain(msg);
            deferredMsg.arg1 = ++mDeferredEnableSerialNumber;
            sendMessageDelayed(deferredMsg,
                    mReEnableDelayMillis - delaySoFar + DEFER_MARGIN_MS);
            return true;
        }

        @Override
        public void enter() {
            mDisabledTimestamp = SystemClock.elapsedRealtime();
            mDeferredEnableSerialNumber++;
            mHaveDeferredEnable = false;
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    if (mSettingsStore.isScanAlwaysAvailable()) {
                        transitionTo(mStaDisabledWithScanState);
                    }
                    return true;
                case CMD_WIFI_TOGGLED:
                case CMD_AIRPLANE_TOGGLED:
                    if (mSettingsStore.isWifiToggleEnabled()) {
                        if (doDeferEnable(msg)) {
                            if (mHaveDeferredEnable) {
                                mDeferredEnableSerialNumber++;
                            }
                            mHaveDeferredEnable = !mHaveDeferredEnable;
                        } else if (mDeviceIdle) {
                            checkLocksAndTransitionWhenDeviceIdle();
                        } else if (mStaAndApConcurrency) {
                            transitionTo(mQcStaEnablingState);
                        } else {
                            mWifiStateMachine.setOperationalMode(
                                    WifiStateMachine.CONNECT_MODE);
                            transitionTo(mDeviceActiveState);
                        }
                    } else if (mSettingsStore.isScanAlwaysAvailable()) {
                        transitionTo(mStaDisabledWithScanState);
                    }
                    return true;
                case CMD_SET_AP:
                    if (msg.arg1 == 1) {
                        if (msg.arg2 == 0) {
                            mSettingsStore.setWifiSavedState(WifiSettingsStore.WIFI_DISABLED);
                        }
                        if (mStaAndApConcurrency) {
                            transitionTo(mQcApEnablingState);
                        } else {
                            mWifiStateMachine.setHostApRunning(
                                    (SoftApModeConfiguration) msg.obj, true);
                            transitionTo(mApEnabledState);
                        }
                    }
                    return true;
                case CMD_DEFERRED_TOGGLE:
                    if (msg.arg1 != mDeferredEnableSerialNumber) {
                        log("DEFERRED_TOGGLE ignored due to serial mismatch");
                    } else {
                        log("DEFERRED_TOGGLE handled");
                        sendMessage((Message) msg.obj);
                    }
                    return true;
                case CMD_RESTART_WIFI_CONTINUE:
                    if (mStaAndApConcurrency) {
                        log("ApStaDisabledState: CMD_RESTART_WIFI_CONTINUE -> mQcStaEnablingState");
                        if (mRestartStaSapStack) {
                            deferMessage(msg);
                        }
                        transitionTo(mQcStaEnablingState);
                    } else {
                        transitionTo(mDeviceActiveState);
                    }
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QcApDisablingState extends State {
        @Override
        public void enter() {
            mSoftApStateMachine.setHostApRunning(
                    (SoftApModeConfiguration) getCurrentMessage().obj, false);
        }

        @Override
        public void exit() {
            Slog.d(TAG, "QcApDisablingState: exit()");
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    log("QcApDisablingState: CMD_SCAN_ALWAYS_MODE_CHANGED defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_TOGGLED:
                    log("QcApDisablingState: CMD_WIFI_TOGGLED defered");
                    deferMessage(msg);
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    log("QcApDisablingState: CMD_AIRPLANE_TOGGLED defered");
                    deferMessage(msg);
                    return true;
                case CMD_SET_AP:
                    log("QcApDisablingState: CMD_SET_AP defered");
                    deferMessage(msg);
                    return true;
                case CMD_AP_STOPPED:
                    if (mSettingsStore.isScanAlwaysAvailable()) {
                        log("QcApDisablingState: CMD_AP_STOPPED->mStaDisabledWithScanState");
                        transitionTo(mStaDisabledWithScanState);
                    } else if (!mSettingsStore.isAirplaneModeOn()
                            || mWifiStateMachine.getOperationalMode()
                                    == WifiStateMachine.CONNECT_MODE) {
                        log("QcApDisablingState: CMD_AP_STOPPED->mApStaDisabledState");
                        transitionTo(mApStaDisabledState);
                    } else {
                        log("ApDisablingState: CMD_AP_STOPPED->mQcStaDisablingState");
                        mWifiStateMachine.setOperationalMode(WifiStateMachine.CONNECT_MODE);
                        transitionTo(mQcStaDisablingState);
                    }
                    return true;
                case CMD_AP_STOP_FAILURE:
                    if (mSettingsStore.isScanAlwaysAvailable()) {
                        log("QcApDisablingState: CMD_AP_STOP_FAILURE->mStaDisabledWithScanState");
                        transitionTo(mStaDisabledWithScanState);
                    } else {
                        log("QcApDisablingState: CMD_AP_STOP_FAILURE->mApStaDisabledState");
                        transitionTo(mApStaDisabledState);
                    }
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QcApEnablingState extends State {
        @Override
        public void enter() {
            if (DBG) {
                Slog.d(TAG, "QcApEnablingState enter");
            }
            mSoftApStateMachine.setHostApRunning(
                    (SoftApModeConfiguration) getCurrentMessage().obj, true);
        }

        @Override
        public void exit() {
            if (DBG) {
                Slog.d(TAG, "QcApEnablingState exit");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    log("QcApEnablingState: CMD_SCAN_ALWAYS_MODE_CHANGED defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_TOGGLED:
                    log("QcApEnablingState: CMD_WIFI_TOGGLED defered");
                    deferMessage(msg);
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    log("QcApEnablingState: CMD_AIRPLANE_TOGGLED defered");
                    deferMessage(obtainMessage(msg.what, msg.arg1, 1, msg.obj));
                    return true;
                case CMD_SET_AP:
                    log("QcApEnablingState: CMD_SET_AP defered");
                    deferMessage(obtainMessage(msg.what, msg.arg1, 1, msg.obj));
                    return true;
                case CMD_AP_START_FAILURE:
                    log("QcApEnablingState: CMD_AP_START_FAILURE->mApStaDisabledState");
                    transitionTo(mApStaDisabledState);
                    return true;
                case CMD_AP_STARTED:
                    log("QcApEnablingState: CMD_AP_STARTED->mApEnabledState");
                    transitionTo(mApEnabledState);
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QcApStaDisablingState extends State {
        @Override
        public void enter() {
            if (DBG) {
                Slog.d(TAG, "QcApStaDisablingState enter");
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Slog.d(TAG, "QcApStaDisablingState exit");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    log("QcApStaDisablingState: CMD_SCAN_ALWAYS_MODE_CHANGED defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_TOGGLED:
                    log("QcApStaDisablingState defer CMD_WIFI_TOGGLED");
                    deferMessage(msg);
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    log("QcApStaDisablingState defer CMD_AIRPLANE_TOGGLED");
                    deferMessage(msg);
                    return true;
                case CMD_SET_AP:
                    log("QcApStaDisablingState defer CMD_SET_AP");
                    deferMessage(obtainMessage(msg.what, msg.arg1, 1, msg.obj));
                    return true;
                case CMD_AP_STOPPED:
                    log("QcApStaDisablingState: CMD_AP_STOPPED->StaEnabledState");
                    transitionTo(mStaEnabledState);
                    return true;
                case CMD_STA_START_FAILURE:
                    log("QcApStaDisablingState: CMD_STA_START_FAILURE dropped");
                    return true;
                case CMD_RESTART_WIFI:
                    log("QcApStaDisablingState defer CMD_RESTART_WIFI");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_ENABLED:
                    log("QcApStaDisablingState CMD_WIFI_ENABLED ignored");
                    return true;
                case CMD_WIFI_DISABLED:
                    log("QcApStaDisablingState: CMD_WIFI_DISABLED-> mApEnabledState");
                    transitionTo(mApEnabledState);
                    return true;
                case CMD_AP_STARTED:
                    log("QcApStaDisablingState CMD_AP_STARTED ignored");
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QcApStaEnabledState extends State {
        private long mDisabledTimestamp;
        private State mPendingState = null;
        private int mDeferredEnableSerialNumber = 0;
        private boolean mHaveDeferredEnable = false;

        private boolean doDeferEnable(Message msg) {
            long delaySoFar = SystemClock.elapsedRealtime() - mDisabledTimestamp;
            if (delaySoFar >= mReEnableDelayMillis) {
                return false;
            }

            log("WifiController msg " + msg + " deferred for "
                    + (mReEnableDelayMillis - delaySoFar) + "ms");

            Message deferredMsg = obtainMessage(CMD_DEFERRED_TOGGLE);
            deferredMsg.obj = Message.obtain(msg);
            deferredMsg.arg1 = ++mDeferredEnableSerialNumber;
            sendMessageDelayed(deferredMsg,
                    mReEnableDelayMillis - delaySoFar + DEFER_MARGIN_MS);
            return true;
        }

        @Override
        public void enter() {
            if (DBG) {
                Slog.d(TAG, "QcApStaEnabledState enter");
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Slog.d(TAG, "QcApStaEnabledState exit");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_WIFI_TOGGLED:
                    if (!mSettingsStore.isWifiToggleEnabled()) {
                        if (doDeferEnable(msg)) {
                            if (mHaveDeferredEnable) {
                                mDeferredEnableSerialNumber++;
                            }
                            mHaveDeferredEnable = !mHaveDeferredEnable;
                        } else {
                            if (mSettingsStore.isScanAlwaysAvailable()) {
                                log("QcApStaEnabledState:CMD_WIFI_TOGGLED:"
                                        + "set:SCAN_ONLY_WITH_WIFI_OFF_MODE");
                                mWifiStateMachine.setOperationalMode(
                                        WifiStateMachine.SCAN_ONLY_WITH_WIFI_OFF_MODE);
                            } else {
                                log("QcApStaEnabledState:CMD_WIFI_TOGGLED:"
                                        + "setSupplicantRunning(false)");
                                mWifiStateMachine.setOperationalMode(
                                        WifiStateMachine.CONNECT_MODE);
                                mWifiStateMachine.setSupplicantRunning(false);
                            }
                            transitionTo(mQcApStaDisablingState);
                        }
                    }
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    log("QcApStaEnabledState: CMD_AIRPLANE_TOGGLED-> mQcApStaDisablingState");
                    mSoftApStateMachine.setHostApRunning(null, false);
                    deferMessage(msg);
                    transitionTo(mQcApStaDisablingState);
                    return true;
                case CMD_SET_AP:
                    if (msg.arg1 == 0) {
                        if (DBG) {
                            Slog.d(TAG, "QcApStaEnabledState:CMD_SET_AP:setHostApRunning(false)"
                                    + "-> mQcApStaDisablingState");
                        }
                        mSoftApStateMachine.setHostApRunning(null, false);
                        transitionTo(mQcApStaDisablingState);
                    }
                    return true;
                case CMD_AP_START_FAILURE:
                    transitionTo(mStaEnabledState);
                    return true;
                case CMD_STA_START_FAILURE:
                    transitionTo(mApEnabledState);
                    return true;
                case CMD_RESTART_WIFI:
                    log("QcApStaEnabledState: CMD_RESTART_WIFI -> setHostApRunning(false)"
                            + " -> mQcApStaDisablingState");
                    mSoftApStateMachine.setHostApRunning(null, false);
                    mRestartStaSapStack = true;
                    deferMessage(msg);
                    transitionTo(mQcApStaDisablingState);
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QcApStaEnablingState extends State {
        @Override
        public void enter() {
            if (DBG) {
                Slog.d(TAG, "QcApStaEnablingState enter");
            }
        }

        @Override
        public void exit() {
            if (DBG) {
                Slog.d(TAG, "QcApStaEnablingState exit");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    log("QcApStaEnablingState: CMD_SCAN_ALWAYS_MODE_CHANGED defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_TOGGLED:
                    log("QcApStaEnablingState defer CMD_WIFI_TOGGLED");
                    deferMessage(msg);
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    log("QcApStaEnablingState defer CMD_AIRPLANE_TOGGLED");
                    deferMessage(msg);
                    return true;
                case CMD_SET_AP:
                    log("QcApStaEnablingState defer CMD_SET_AP");
                    deferMessage(obtainMessage(msg.what, msg.arg1, 1, msg.obj));
                    return true;
                case CMD_AP_START_FAILURE:
                    log("QcApStaEnablingState: CMD_AP_START_FAILURE-> mStaEnabledState");
                    transitionTo(mStaEnabledState);
                    return true;
                case CMD_STA_START_FAILURE:
                    log("QcApStaEnablingState: CMD_STA_START_FAILURE-> mApEnabledState");
                    transitionTo(mApEnabledState);
                    return true;
                case CMD_WIFI_ENABLED:
                    log("QcApStaEnablingState: CMD_WIFI_ENABLED-> mQcApStaEnabledState");
                    transitionTo(mQcApStaEnabledState);
                    return true;
                case CMD_AP_STARTED:
                    log("QcApStaEnablingState: CMD_AP_STARTED-> mQcApStaEnabledState");
                    transitionTo(mQcApStaEnabledState);
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QcStaDisablingState extends State {
        @Override
        public void enter() {
            if (DBG) {
                Slog.d(TAG, "QcStaDisablingState enter");
            }
            mWifiStateMachine.setSupplicantRunning(false);
            mWifiStateMachine.clearANQPCache();
        }

        @Override
        public void exit() {
            if (DBG) {
                Slog.d(TAG, "QcStaDisablingState exit");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    log("QcStaDisablingState: CMD_SCAN_ALWAYS_MODE_CHANGED defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_TOGGLED:
                    log("QcStaDisablingState: CMD_WIFI_TOGGLED defered");
                    deferMessage(msg);
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    log("QcStaDisablingState: CMD_AIRPLANE_TOGGLED defered");
                    deferMessage(msg);
                    return true;
                case CMD_SET_AP:
                    log("QcStaDisablingState: CMD_SET_AP defered");
                    deferMessage(msg);
                    return true;
                case CMD_RESTART_WIFI_CONTINUE:
                    log("QcStaDisablingState: CMD_RESTART_WIFI_CONTINUE defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_DISABLED:
                case CMD_WIFI_STOP_FAILURE:
                    log("QcStaDisablingState: CMD_WIFI_DISABLED->mApStaDisabledState");
                    transitionTo(mApStaDisabledState);
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QcStaEnablingState extends State {
        @Override
        public void enter() {
            if (DBG) {
                Slog.d(TAG, "QcStaEnablingState enter");
            }
            mWifiStateMachine.setOperationalMode(WifiStateMachine.CONNECT_MODE);
            mWifiStateMachine.setSupplicantRunning(true);
        }

        @Override
        public void exit() {
            if (DBG) {
                Slog.d(TAG, "QcStaEnablingState exit");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    log("QcStaEnablingState: CMD_SCAN_ALWAYS_MODE_CHANGED defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_TOGGLED:
                    log("QcStaEnablingState: CMD_WIFI_TOGGLED defered");
                    deferMessage(msg);
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    log("QcStaEnablingState: CMD_AIRPLANE_TOGGLED defered");
                    deferMessage(msg);
                    return true;
                case CMD_SET_AP:
                    log("QcStaEnablingState: CMD_SET_AP defered");
                    deferMessage(obtainMessage(msg.what, msg.arg1, 1, msg.obj));
                    return true;
                case CMD_STA_START_FAILURE:
                    log("QcStaEnablingState: CMD_STA_START_FAILURE->mApStaDisabledState");
                    transitionTo(mApStaDisabledState);
                    return true;
                case CMD_RESTART_WIFI_CONTINUE:
                    log("QcStaEnablingState: CMD_RESTART_WIFI_CONTINUE defered");
                    deferMessage(msg);
                    return true;
                case CMD_WIFI_ENABLED:
                    log("QcStaEnablingState: CMD_WIFI_ENABLED->mDeviceActiveState");
                    transitionTo(mDeviceActiveState);
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class ApEnabledState extends State {
        /**
         * Save the pending state when stopping the AP, so that it will transition
         * to the correct state when AP is stopped.  This is to avoid a possible
         * race condition where the new state might try to update the driver/interface
         * state before AP is completely torn down.
         */
        private State mPendingState = null;

        /**
         * Determine the next state based on the current settings (e.g. saved
         * wifi state) and STA+SAP concurrency.
         */
        private State getNextWifiState() {
            if (mSettingsStore.getWifiSavedState() == WifiSettingsStore.WIFI_ENABLED
                    || mStaAndApConcurrency) {
                if (mSettingsStore.isScanAlwaysAvailable()) {
                    return mStaDisabledWithScanState;
                } else {
                    return mApStaDisabledState;
                }
            }

            return mDeviceActiveState;
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_EMERGENCY_MODE_CHANGED:
                case CMD_EMERGENCY_CALL_STATE_CHANGED:
                    if (msg.arg1 == 1) {
                        if (mStaAndApConcurrency) {
                            mSoftApStateMachine.setHostApRunning(null, false);
                        } else {
                            mWifiStateMachine.setHostApRunning(null, false);
                        }
                        mPendingState = mEcmState;
                    }
                    return true;
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    if (!mStaAndApConcurrency) {
                        return NOT_HANDLED;
                    }
                    if (mSettingsStore.isScanAlwaysAvailable()) {
                        mWifiStateMachine.setSupplicantRunning(true);
                        mWifiStateMachine.setOperationalMode(
                                WifiStateMachine.SCAN_ONLY_WITH_WIFI_OFF_MODE);
                    } else {
                        mWifiStateMachine.setOperationalMode(
                                WifiStateMachine.CONNECT_MODE);
                        mWifiStateMachine.setSupplicantRunning(false);
                    }
                    return true;
                case CMD_WIFI_TOGGLED:
                    if (mSettingsStore.isWifiToggleEnabled()) {
                        if (!mStaAndApConcurrency) {
                            mWifiStateMachine.setHostApRunning(null, false);
                            mPendingState = mDeviceActiveState;
                        } else if (mSettingsStore.isScanAlwaysAvailable()) {
                            mWifiStateMachine.setOperationalMode(
                                    WifiStateMachine.CONNECT_MODE);
                            transitionTo(mQcApStaEnabledState);
                        } else {
                            Slog.d(TAG, "ApEnabledState:CMD_WIFI_TOGGLED"
                                    + ":->QcApStaEnablingState");
                            mWifiStateMachine.setSupplicantRunning(true);
                            transitionTo(mQcApStaEnablingState);
                        }
                    }
                    return true;
                case CMD_AIRPLANE_TOGGLED:
                    if (mSettingsStore.isAirplaneModeOn()) {
                        if (mStaAndApConcurrency) {
                            transitionTo(mQcApDisablingState);
                        } else {
                            mWifiStateMachine.setHostApRunning(null, false);
                            mPendingState = mApStaDisabledState;
                        }
                    }
                    return true;
                case CMD_SET_AP:
                    if (msg.arg1 == 0) {
                        if (mStaAndApConcurrency) {
                            transitionTo(mQcApDisablingState);
                        } else {
                            mWifiStateMachine.setHostApRunning(null, false);
                            mPendingState = getNextWifiState();
                        }
                    }
                    return true;
                case CMD_AP_STARTED:
                    if (!mStaAndApConcurrency
                            && mWifiApConfigStore.getApConfiguration().apBand
                                    == WifiConfiguration.AP_BAND_DUAL) {
                        mWifiApConfigStore.setDualSapStatus(true);
                    }
                    return true;
                case CMD_AP_START_FAILURE:
                    mPendingState = getNextWifiState();
                    if (mWifiApConfigStore.getStaSapConcurrency()) {
                        mWifiApConfigStore.setDualSapStatus(false);
                        mWifiStateMachine.setDualSapMode(false);
                        setSoftApStateMachine(mWifiStateMachine.getSoftApStateMachine(), true);
                    } else if (mWifiApConfigStore.getApConfiguration().apBand
                            == WifiConfiguration.AP_BAND_DUAL) {
                        mWifiApConfigStore.setDualSapStatus(false);
                        mWifiStateMachine.setDualSapMode(false);
                    }
                    transitionTo(mPendingState);
                    return true;
                case CMD_AP_STOPPED:
                    if (mWifiApConfigStore.getDualSapStatus()) {
                        mWifiApConfigStore.setDualSapStatus(false);
                        mWifiStateMachine.setDualSapMode(false);
                        if (mWifiApConfigStore.getStaSapConcurrency()) {
                            setSoftApStateMachine(mWifiStateMachine.getSoftApStateMachine(), true);
                        }
                    }
                    if (mPendingState == null) {
                        mPendingState = getNextWifiState();
                    }
                    if (mPendingState == mDeviceActiveState && mDeviceIdle) {
                        checkLocksAndTransitionWhenDeviceIdle();
                    } else {
                        transitionTo(mPendingState);
                    }
                    return true;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class EcmState extends State {
        // we can enter EcmState either because an emergency call started or because
        // emergency callback mode started. This count keeps track of how many such
        // events happened; so we can exit after all are undone
        private int mEcmEntryCount;

        private void decrementCountAndReturnToAppropriateState() {
            boolean exitEcm = false;

            if (mEcmEntryCount == 0) {
                loge("mEcmEntryCount is 0; exiting Ecm");
            } else {
                mEcmEntryCount--;
                if (mEcmEntryCount == 0) {
                    exitEcm = true;
                }
            }

            if (exitEcm) {
                if (mSettingsStore.isWifiToggleEnabled()) {
                    if (mDeviceIdle) {
                        checkLocksAndTransitionWhenDeviceIdle();
                    } else {
                        transitionTo(mDeviceActiveState);
                    }
                } else if (mSettingsStore.isScanAlwaysAvailable()) {
                    transitionTo(mStaDisabledWithScanState);
                } else {
                    transitionTo(mApStaDisabledState);
                }
            }
        }

        @Override
        public void enter() {
            mWifiStateMachine.setSupplicantRunning(false);
            mWifiStateMachine.clearANQPCache();
            mEcmEntryCount = 1;
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            if (msg.what == CMD_EMERGENCY_CALL_STATE_CHANGED) {
                if (msg.arg1 == 1) {
                    mEcmEntryCount++;
                } else if (msg.arg1 == 0) {
                    decrementCountAndReturnToAppropriateState();
                }
                return HANDLED;
            } else if (msg.what == CMD_EMERGENCY_MODE_CHANGED) {
                if (msg.arg1 == 1) {
                    mEcmEntryCount++;
                } else if (msg.arg1 == 0) {
                    decrementCountAndReturnToAppropriateState();
                }
                return HANDLED;
            }
            return NOT_HANDLED;
        }
    }

    class DeviceActiveState extends State {
        @Override
        public void enter() {
            mWifiStateMachine.setOperationalMode(WifiStateMachine.CONNECT_MODE);
            mWifiStateMachine.setHighPerfModeEnabled(false);
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            if (msg.what == CMD_DEVICE_IDLE) {
                checkLocksAndTransitionWhenDeviceIdle();
                // We let the message continue to be processed by the parent state.
                return NOT_HANDLED;
            } else if (msg.what == CMD_USER_PRESENT) {
                if (!mFirstUserSignOnSeen) {
                    mWifiStateMachine.reloadTlsNetworksAndReconnect();
                }
                mFirstUserSignOnSeen = true;
                return HANDLED;
            } else if (msg.what == CMD_RESTART_WIFI && !mStaAndApConcurrency) {
                deferMessage(obtainMessage(CMD_RESTART_WIFI_CONTINUE));
                transitionTo(mQcStaDisablingState);
                return HANDLED;
            }
            return NOT_HANDLED;
        }
    }

    class DeviceInactiveState extends State {
        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCREEN_ON:
                    transitionTo(mDeviceActiveState);
                    // Handle the message in the new state as well.
                    return NOT_HANDLED;
                case CMD_LOCKS_CHANGED:
                    checkLocksAndTransitionWhenDeviceIdle();
                    updateBatteryWorkSource();
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class ScanOnlyLockHeldState extends State {
        @Override
        public void enter() {
            mWifiStateMachine.setOperationalMode(WifiStateMachine.SCAN_ONLY_MODE);
        }
    }

    class FullLockHeldState extends State {
        @Override
        public void enter() {
            mWifiStateMachine.setOperationalMode(WifiStateMachine.CONNECT_MODE);
            mWifiStateMachine.setHighPerfModeEnabled(false);
        }
    }

    class FullHighPerfLockHeldState extends State {
        @Override
        public void enter() {
            mWifiStateMachine.setOperationalMode(WifiStateMachine.CONNECT_MODE);
            mWifiStateMachine.setHighPerfModeEnabled(true);
        }
    }

    class NoLockHeldState extends State {
        @Override
        public void enter() {
            mWifiStateMachine.setOperationalMode(WifiStateMachine.DISABLED_MODE);
        }
    }

    class StaDisabledWithScanState extends State {
        private long mDisabledTimestamp;
        private int mDeferredEnableSerialNumber = 0;
        private boolean mHaveDeferredEnable = false;

        private boolean doDeferEnable(Message msg) {
            long delaySoFar = SystemClock.elapsedRealtime() - mDisabledTimestamp;
            if (delaySoFar >= mReEnableDelayMillis) {
                return false;
            }

            log("WifiController msg " + msg + " deferred for "
                    + (mReEnableDelayMillis - delaySoFar) + "ms");

            Message deferredMsg = obtainMessage(CMD_DEFERRED_TOGGLE);
            deferredMsg.obj = Message.obtain(msg);
            deferredMsg.arg1 = ++mDeferredEnableSerialNumber;
            sendMessageDelayed(deferredMsg,
                    mReEnableDelayMillis - delaySoFar + DEFER_MARGIN_MS);
            return true;
        }

        @Override
        public void enter() {
            mWifiStateMachine.setOperationalMode(
                    WifiStateMachine.SCAN_ONLY_WITH_WIFI_OFF_MODE);
            mWifiStateMachine.setSupplicantRunning(true);
            mDisabledTimestamp = SystemClock.elapsedRealtime();
            mDeferredEnableSerialNumber++;
            mHaveDeferredEnable = false;
            mWifiStateMachine.clearANQPCache();
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_SCAN_ALWAYS_MODE_CHANGED:
                    if (!mSettingsStore.isScanAlwaysAvailable()) {
                        mWifiStateMachine.setOperationalMode(WifiStateMachine.CONNECT_MODE);
                        transitionTo(mQcStaDisablingState);
                    }
                    return HANDLED;
                case CMD_WIFI_TOGGLED:
                    if (mSettingsStore.isWifiToggleEnabled()) {
                        if (doDeferEnable(msg)) {
                            if (mHaveDeferredEnable) {
                                mDeferredEnableSerialNumber++;
                            }
                            mHaveDeferredEnable = !mHaveDeferredEnable;
                        } else if (mDeviceIdle) {
                            checkLocksAndTransitionWhenDeviceIdle();
                        } else {
                            transitionTo(mDeviceActiveState);
                        }
                    }
                    return HANDLED;
                case CMD_AIRPLANE_TOGGLED:
                    if (mSettingsStore.isAirplaneModeOn()
                            && !mSettingsStore.isWifiToggleEnabled()) {
                        transitionTo(mQcStaDisablingState);
                    }
                    return HANDLED;
                case CMD_SET_AP:
                    if (msg.arg1 == 1) {
                        if (mStaAndApConcurrency) {
                            transitionTo(mQcApEnablingState);
                        } else {
                            mSettingsStore.setWifiSavedState(WifiSettingsStore.WIFI_DISABLED);
                            deferMessage(obtainMessage(msg.what, msg.arg1, 1, msg.obj));
                            transitionTo(mQcStaDisablingState);
                        }
                    }
                    return HANDLED;
                case CMD_DEFERRED_TOGGLE:
                    if (msg.arg1 != mDeferredEnableSerialNumber) {
                        log("DEFERRED_TOGGLE ignored due to serial mismatch");
                    } else {
                        logd("DEFERRED_TOGGLE handled");
                        sendMessage((Message) msg.obj);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class StaEnabledState extends State {
        @Override
        public void enter() {
            if (mStaAndApConcurrency) {
                return;
            }
            mWifiStateMachine.setSupplicantRunning(true);
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateAndMessage(msg, this);
            switch (msg.what) {
                case CMD_EMERGENCY_MODE_CHANGED:
                case CMD_EMERGENCY_CALL_STATE_CHANGED:
                    boolean configWiFiDisableInECBM = mFacade.getConfigWiFiDisableInECBM(mContext);
                    log("WifiController msg " + msg + " getConfigWiFiDisableInECBM "
                            + configWiFiDisableInECBM);
                    if (msg.arg1 == 1 && configWiFiDisableInECBM) {
                        transitionTo(mEcmState);
                    }
                    return HANDLED;
                case CMD_WIFI_TOGGLED:
                    if (!mSettingsStore.isWifiToggleEnabled()) {
                        if (mSettingsStore.isScanAlwaysAvailable()) {
                            transitionTo(mStaDisabledWithScanState);
                        } else {
                            transitionTo(mQcStaDisablingState);
                        }
                    }
                    return HANDLED;
                case CMD_AIRPLANE_TOGGLED:
                    if (!mSettingsStore.isWifiToggleEnabled()) {
                        transitionTo(mQcStaDisablingState);
                    }
                    return HANDLED;
                case CMD_SET_AP:
                    if (msg.arg1 == 1) {
                        if (mStaAndApConcurrency) {
                            Slog.d(TAG, "StaEnabledState:CMD_SET_AP:setHostApRunning(true)"
                                    + "-> mApStaEnableState");
                            mSoftApStateMachine.setHostApRunning(
                                    (SoftApModeConfiguration) msg.obj, true);
                            transitionTo(mQcApStaEnablingState);
                        } else {
                            mSettingsStore.setWifiSavedState(WifiSettingsStore.WIFI_ENABLED);
                            deferMessage(obtainMessage(msg.what, msg.arg1, 1, msg.obj));
                            transitionTo(mQcStaDisablingState);
                        }
                    }
                    return HANDLED;
                case CMD_STA_START_FAILURE:
                    if (!mSettingsStore.isScanAlwaysAvailable()) {
                        transitionTo(mApStaDisabledState);
                    } else {
                        transitionTo(mStaDisabledWithScanState);
                    }
                    return HANDLED;
                case CMD_RESTART_WIFI:
                    log("StaEnabledState:CMD_RESTART_WIFI ->QcStaDisablingState");
                    deferMessage(obtainMessage(CMD_RESTART_WIFI_CONTINUE));
                    transitionTo(mQcStaDisablingState);
                    return HANDLED;
                case CMD_RESTART_WIFI_CONTINUE:
                    if (mStaAndApConcurrency && mRestartStaSapStack) {
                        if (msg.obj != null) {
                            log("StaEnabledState:CMD_RESTART_WIFI_CONTINUE"
                                    + " ->mQcApStaEnablingState");
                            mSoftApStateMachine.setHostApRunning(
                                    (SoftApModeConfiguration) msg.obj, true);
                            transitionTo(mQcApStaEnablingState);
                        } else {
                            log("StaEnabledState:CMD_RESTART_WIFI_CONTINUE: SoftApConfig obj null."
                                    + " Do nothing");
                        }
                        mRestartStaSapStack = false;
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    private void initializeAndRegisterForSettingsChange(Looper looper) {
        Handler handler = new Handler(looper);
        readStayAwakeConditions();
        registerForStayAwakeModeChange(handler);
        readWifiIdleTime();
        registerForWifiIdleTimeChange(handler);
        readWifiSleepPolicy();
        registerForWifiSleepPolicyChange(handler);
        readWifiReEnableDelay();
    }

    private void readStayAwakeConditions() {
        mStayAwakeConditions = mFacade.getIntegerSetting(mContext,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
    }

    private void readWifiIdleTime() {
        mIdleMillis = mFacade.getLongSetting(mContext,
                Settings.Global.WIFI_IDLE_MS, DEFAULT_IDLE_MS);
    }

    private void readWifiReEnableDelay() {
        mReEnableDelayMillis = mFacade.getLongSetting(mContext,
                Settings.Global.WIFI_REENABLE_DELAY_MS, DEFAULT_REENABLE_DELAY_MS);
    }

    private void readWifiSleepPolicy() {
        Slog.d(TAG, "readWifiSleepPolicy mSleepPolicy=" + mSleepPolicy);
        mSleepPolicy = mFacade.getIntegerSetting(mContext,
                Settings.Global.WIFI_SLEEP_POLICY, Settings.Global.WIFI_SLEEP_POLICY_DEFAULT);
    }

    private void registerForStayAwakeModeChange(Handler handler) {
        ContentObserver contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                readStayAwakeConditions();
            }
        };
        mFacade.registerContentObserver(mContext, Settings.Global.getUriFor(
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN), false, contentObserver);
    }

    private void registerForWifiIdleTimeChange(Handler handler) {
        ContentObserver contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                readWifiIdleTime();
            }
        };
        mFacade.registerContentObserver(mContext, Settings.Global.getUriFor(
                Settings.Global.WIFI_IDLE_MS), false, contentObserver);
    }

    private void registerForWifiSleepPolicyChange(Handler handler) {
        ContentObserver contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                readWifiSleepPolicy();
                sendMessage(CMD_WIFI_SLEEP_POLICY_CHANGE);
            }
        };
        mFacade.registerContentObserver(mContext, Settings.Global.getUriFor(
                Settings.Global.WIFI_SLEEP_POLICY), false, contentObserver);
    }

    private boolean shouldDeviceStayAwake(int pluggedType) {
        return (mStayAwakeConditions & pluggedType) != 0;
    }

    private boolean shouldWifiStayAwake(int pluggedType) {
        // if wifi sleep policy is set to never
        if (mSleepPolicy == Settings.Global.WIFI_SLEEP_POLICY_NEVER) {
            return true;
        }
        // if wifi sleep policy is set to never when plugged in and we are plugged in
        if (mSleepPolicy == Settings.Global.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED
                && pluggedType != 0) {
            return true;
        }
        return shouldDeviceStayAwake(pluggedType);
    }

    private void updateBatteryWorkSource() {
        mTmpWorkSource.clear();
        if (mDeviceIdle) {
            mTmpWorkSource.add(mWifiLockManager.createMergedWorkSource());
        }
        mWifiStateMachine.updateBatteryWorkSource(mTmpWorkSource);
    }

    private void checkLocksAndTransitionWhenDeviceIdle() {
        switch (mWifiLockManager.getStrongestLockMode()) {
            case WIFI_MODE_NO_LOCKS_HELD:
                if (mSettingsStore.isScanAlwaysAvailable()) {
                    transitionTo(mScanOnlyLockHeldState);
                } else {
                    transitionTo(mNoLockHeldState);
                }
                break;
            case WIFI_MODE_FULL:
                transitionTo(mFullLockHeldState);
                break;
            case WIFI_MODE_FULL_HIGH_PERF:
                transitionTo(mFullHighPerfLockHeldState);
                break;
            case WIFI_MODE_SCAN_ONLY:
                transitionTo(mScanOnlyLockHeldState);
                break;
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);

        pw.println("mScreenOff " + mScreenOff);
        pw.println("mDeviceIdle " + mDeviceIdle);
        pw.println("mPluggedType " + mPluggedType);
        pw.println("mIdleMillis " + mIdleMillis);
        pw.println("mSleepPolicy " + mSleepPolicy);
    }

    void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            DBG = true;
        } else {
            DBG = false;
        }
    }

    /**
     * CAF/QTI vendor extension: attach the soft AP state machine and enable STA+SAP concurrency.
     */
    public void setSoftApStateMachine(SoftApStateMachine softApStateMachine, boolean concurrency) {
        mSoftApStateMachine = softApStateMachine;
        mStaAndApConcurrency = concurrency;
        mWifiStateMachine.setStaSoftApConcurrency(concurrency);
        Slog.d(TAG, "set mStaAndApConcurrency=" + mStaAndApConcurrency);
    }

    private void logStateAndMessage(Message message, State state) {
        if (DBG) {
            logd(" " + state.getClass().getSimpleName() + ", message: " + message);
        }
    }
}
