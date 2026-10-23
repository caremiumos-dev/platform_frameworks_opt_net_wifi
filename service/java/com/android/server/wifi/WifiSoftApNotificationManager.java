/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.wifi.WifiDevice;
import android.os.Message;
import android.os.UserHandle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * XTC soft-AP connected-device tracker: watches {@code dnsmasq.leases} and
 * posts a hotspot notification / sticky broadcast.
 *
 * @hide
 */
public class WifiSoftApNotificationManager {
    private static final String TAG = "WifiSoftApNotificationManager";
    private static final boolean DBG = false;

    private static final String HOTSPOT_NOTIFICATION = "HOTSPOT_NOTIFICATION";
    private static final String dhcpLocation = "/data/misc/dhcp/dnsmasq.leases";
    private static final int DNSMASQ_POLLING_INTERVAL = 1000;
    private static final int DNSMASQ_POLLING_MAX_TIMES = 10;

    private static Context mContext;
    private static WifiSoftApNotificationManager mWifiSoftApNotificationManager;

    private String mChannelName;
    private Notification.Builder softApNotificationBuilder;
    private int mLastSoftApNotificationId = 0;
    private final HashMap<String, WifiDevice> mL2ConnectedDeviceMap = new HashMap<>();
    private final HashMap<String, WifiDevice> mConnectedDeviceMap = new HashMap<>();

    private static class DnsmasqThread extends Thread {
        private final WifiDevice mDevice;
        private final int mInterval;
        private int mMaxTimes;
        private final WifiSoftApNotificationManager mWifiSoftApNotificationmgr;

        public DnsmasqThread(WifiSoftApNotificationManager manager, WifiDevice device,
                int interval, int maxTimes) {
            super("SoftAp");
            mWifiSoftApNotificationmgr = manager;
            mInterval = interval;
            mMaxTimes = maxTimes;
            mDevice = device;
        }

        @Override
        public void run() {
            while (mMaxTimes > 0 && !mWifiSoftApNotificationmgr.readDeviceInfoFromDnsmasq(mDevice)) {
                try {
                    mMaxTimes--;
                    Thread.sleep(mInterval);
                } catch (Exception e) {
                    Log.e(TAG, "Polling " + mDevice.deviceAddress + "error" + e);
                }
            }
            WifiDevice device = mWifiSoftApNotificationmgr.mL2ConnectedDeviceMap
                    .get(mDevice.deviceAddress);
            if (device == null || device.deviceState != WifiDevice.CONNECTED) {
                return;
            }
            mWifiSoftApNotificationmgr.mConnectedDeviceMap.put(mDevice.deviceAddress, mDevice);
            mWifiSoftApNotificationmgr.sendConnectDevicesStateChangedBroadcast();
        }
    }

    private WifiSoftApNotificationManager(Context context) {
        mContext = context;
    }

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static WifiSoftApNotificationManager getInstance(Context context) {
        if (mWifiSoftApNotificationManager != null) {
            return mWifiSoftApNotificationManager;
        }
        mWifiSoftApNotificationManager = new WifiSoftApNotificationManager(context);
        return mWifiSoftApNotificationManager;
    }

    private void interfaceMessageRecevied(String address, boolean connected) {
        if (mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_softap_extension)) {
            WifiDevice device = new WifiDevice(address, connected);
            if (device.deviceState == WifiDevice.CONNECTED) {
                mL2ConnectedDeviceMap.put(device.deviceAddress, device);
                if (!readDeviceInfoFromDnsmasq(device)) {
                    new DnsmasqThread(this, device, DNSMASQ_POLLING_INTERVAL,
                            DNSMASQ_POLLING_MAX_TIMES).start();
                } else {
                    mConnectedDeviceMap.put(device.deviceAddress, device);
                    sendConnectDevicesStateChangedBroadcast();
                }
            } else if (device.deviceState == WifiDevice.DISCONNECTED) {
                mConnectedDeviceMap.remove(device.deviceAddress);
                if (mL2ConnectedDeviceMap.remove(device.deviceAddress) != null) {
                    sendConnectDevicesStateChangedBroadcast();
                }
            }
        }
    }

    public boolean readDeviceInfoFromDnsmasq(WifiDevice device) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(dhcpLocation);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new DataInputStream(in)));
            while (true) {
                String line = reader.readLine();
                if (line == null || line.length() == 0) {
                    return false;
                }
                String[] fields = line.split(" ");
                if (fields.length > 3) {
                    String mac = fields[1];
                    String name = fields[3];
                    if (mac.equals(device.deviceAddress)) {
                        device.deviceName = name;
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "readDeviceNameFromDnsmasq: " + e);
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }
    }

    private void sendConnectDevicesStateChangedBroadcast() {
        if (getConnectivityManager().isTetheringSupported()) {
            Intent intent = new Intent(
                    "codeaurora.net.conn.TETHER_CONNECT_STATE_CHANGED");
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                    | Intent.FLAG_RECEIVER_REPLACE_PENDING
                    | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    private void showSoftApClientsNotification(int icon) {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivityAsUser(mContext, 0, intent, 0,
                null, UserHandle.CURRENT);
        Resources system = Resources.getSystem();
        CharSequence title = system.getText(
                com.android.internal.R.string.tethered_notification_title);
        mChannelName = system.getText(
                com.android.internal.R.string.notification_channel_hotspot).toString();
        int size = mConnectedDeviceMap.size();
        CharSequence text;
        if (size == 0) {
            text = system.getText(
                    com.android.internal.R.string.tethered_notification_no_device_message);
        } else if (size == 1) {
            text = String.format(system.getText(
                    com.android.internal.R.string.tethered_notification_one_device_message)
                    .toString(), Integer.valueOf(size));
        } else {
            text = String.format(system.getText(
                    com.android.internal.R.string.tethered_notification_multi_device_message)
                    .toString(), Integer.valueOf(size));
        }
        if (softApNotificationBuilder == null) {
            notificationManager.createNotificationChannel(new NotificationChannel(
                    HOTSPOT_NOTIFICATION, mChannelName, NotificationManager.IMPORTANCE_LOW));
            softApNotificationBuilder = new Notification.Builder(mContext, HOTSPOT_NOTIFICATION);
            softApNotificationBuilder.setWhen(0L).setOngoing(true)
                    .setColor(mContext.getColor(com.android.internal.R.color.black))
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS);
        }
        softApNotificationBuilder.setSmallIcon(icon).setContentTitle(title).setContentText(text)
                .setContentIntent(pendingIntent).setPriority(Notification.PRIORITY_LOW);
        softApNotificationBuilder.setContentText(text);
        mLastSoftApNotificationId = icon + 10;
        notificationManager.notify(mChannelName, mLastSoftApNotificationId,
                softApNotificationBuilder.build());
    }

    public void clearConnectedDevice() {
        mConnectedDeviceMap.clear();
        mL2ConnectedDeviceMap.clear();
    }

    public void clearSoftApClientsNotification() {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager == null || mLastSoftApNotificationId == 0) {
            return;
        }
        notificationManager.cancel(mChannelName, mLastSoftApNotificationId);
        mLastSoftApNotificationId = 0;
    }

    public void connectionStatusChange(Message message) {
        boolean connected = message.arg1 == 1;
        Log.d(TAG, "devices status=" + connected);
        interfaceMessageRecevied((String) message.obj, connected);
    }

    public List<WifiDevice> getConnectedStations() {
        ArrayList<WifiDevice> stations = new ArrayList<>();
        if (mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_softap_extension)) {
            for (String address : mConnectedDeviceMap.keySet()) {
                stations.add(mConnectedDeviceMap.get(address));
            }
        }
        return stations;
    }
}
