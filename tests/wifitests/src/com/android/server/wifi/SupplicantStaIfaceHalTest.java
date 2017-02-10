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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.net.IpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.os.HandlerThread;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.util.SparseArray;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for SupplicantStaIfaceHal
 */
public class SupplicantStaIfaceHalTest {
    private static final String TAG = "SupplicantStaIfaceHalTest";
    private SupplicantStaIfaceHal mDut;
    @Mock IServiceManager mServiceManagerMock;
    @Mock ISupplicant mISupplicantMock;
    @Mock ISupplicantIface mISupplicantIfaceMock;
    @Mock ISupplicantStaIface mISupplicantStaIfaceMock;
    @Mock HandlerThread mHandlerThread;
    private static final ArrayList<Integer> NETWORK_IDS =
            new ArrayList<>(Arrays.asList(1, 2, 3));
    private static final ArrayList<String> NETWORK_SSIDS =
            new ArrayList<>(Arrays.asList("ssid1", "ssid2", "ssid3"));
    @Mock SupplicantStaNetworkHal mSupplicantStaNetworkMock1;
    @Mock SupplicantStaNetworkHal mSupplicantStaNetworkMock2;
    @Mock SupplicantStaNetworkHal mSupplicantStaNetworkMock3;

    SupplicantStatus mStatusSuccess;
    ISupplicant.IfaceInfo mStaIface;
    ISupplicant.IfaceInfo mP2pIface;
    ArrayList<ISupplicant.IfaceInfo> mIfaceInfoList;

    private ArgumentCaptor<IHwBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IServiceNotification.Stub> mServiceNotificationCaptor =
            ArgumentCaptor.forClass(IServiceNotification.Stub.class);
    private TestLooper mTestLooper;
    private InOrder mInOrder;

    private class SupplicantStaIfaceHalSpy extends SupplicantStaIfaceHal {
        SupplicantStaIfaceHalSpy(HandlerThread handlerThread) {
            super(handlerThread);
        }
        @Override
        protected IServiceManager getServiceManagerMockable() throws RemoteException {
            return mServiceManagerMock;
        }

        @Override
        protected ISupplicant getSupplicantMockable() throws RemoteException {
            return mISupplicantMock;
        }

        @Override
        protected ISupplicantStaIface getStaIfaceMockable(ISupplicantIface iface) {
            return mISupplicantStaIfaceMock;
        }

        @Override
        protected SupplicantStaNetworkHal getNetworkMockable(int id) {
            if (id == 1) {
                return mSupplicantStaNetworkMock1;
            } else if (id == 2) {
                return mSupplicantStaNetworkMock2;
            } else if (id == 3) {
                return mSupplicantStaNetworkMock3;
            } else {
                return null;
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        mStatusSuccess = createSupplicantStatus(SupplicantStatusCode.SUCCESS);
        mStaIface = createIfaceInfo(IfaceType.STA, "wlan0");
        mP2pIface = createIfaceInfo(IfaceType.P2P, "p2p0");

        mIfaceInfoList = new ArrayList<>();
        mIfaceInfoList.add(mStaIface);
        mIfaceInfoList.add(mP2pIface);

        when(mServiceManagerMock.linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong())).thenReturn(true);
        when(mServiceManagerMock.registerForNotifications(anyString(), anyString(),
                any(IServiceNotification.Stub.class))).thenReturn(true);
        when(mHandlerThread.getLooper()).thenReturn(mTestLooper.getLooper());
        mDut = new SupplicantStaIfaceHalSpy(mHandlerThread);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_success() throws Exception {
        executeAndValidateInitializationSequence(false, false, false);
    }

    /**
     * Tests the initialization flow, with a RemoteException occurring when 'getInterface' is called
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_remoteExceptionFailure() throws Exception {
        executeAndValidateInitializationSequence(true, false, false);
    }

    /**
     * Tests the initialization flow, with listInterfaces returning 0 interfaces.
     * Ensures failure
     */
    @Test
    public void testInitialize_zeroInterfacesFailure() throws Exception {
        executeAndValidateInitializationSequence(false, true, false);
    }

    /**
     * Tests the initialization flow, with a null interface being returned by getInterface.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_nullInterfaceFailure() throws Exception {
        executeAndValidateInitializationSequence(false, false, true);
    }

    /**
     * Tests the loading of networks using {@link SupplicantStaNetworkHal}.
     * Fills up only the SSID field of configs and uses it as a configKey as well.
     */
    @Test
    public void testLoadNetworks() throws Exception {
        executeAndValidateInitializationSequence(false, false, false);
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, NETWORK_IDS);
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                config.SSID = NETWORK_SSIDS.get(0);
                config.networkId = 1;
                networkExtra.put(SupplicantStaNetworkHal.ID_STRING_KEY_CONFIG_KEY, config.SSID);
                return true;
            }
        }).when(mSupplicantStaNetworkMock1)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                config.SSID = NETWORK_SSIDS.get(1);
                config.networkId = 2;
                networkExtra.put(SupplicantStaNetworkHal.ID_STRING_KEY_CONFIG_KEY, config.SSID);
                return true;
            }
        }).when(mSupplicantStaNetworkMock2)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                config.SSID = NETWORK_SSIDS.get(2);
                config.networkId = 3;
                networkExtra.put(SupplicantStaNetworkHal.ID_STRING_KEY_CONFIG_KEY, config.SSID);
                return true;
            }
        }).when(mSupplicantStaNetworkMock3)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));

        Map<String, WifiConfiguration> configs = new HashMap<>();
        SparseArray<Map<String, String>> extras = new SparseArray<>();
        assertTrue(mDut.loadNetworks(configs, extras));

        assertEquals(3, configs.size());
        assertEquals(3, extras.size());
        WifiConfiguration config1 = configs.get(NETWORK_SSIDS.get(0));
        WifiConfiguration config2 = configs.get(NETWORK_SSIDS.get(1));
        WifiConfiguration config3 = configs.get(NETWORK_SSIDS.get(2));
        assertEquals(NETWORK_SSIDS.get(0), config1.SSID);
        assertEquals(NETWORK_SSIDS.get(1), config2.SSID);
        assertEquals(NETWORK_SSIDS.get(2), config3.SSID);
        assertEquals(IpConfiguration.IpAssignment.DHCP, config1.getIpAssignment());
        assertEquals(IpConfiguration.IpAssignment.DHCP, config2.getIpAssignment());
        assertEquals(IpConfiguration.IpAssignment.DHCP, config3.getIpAssignment());
        assertEquals(IpConfiguration.ProxySettings.NONE, config1.getProxySettings());
        assertEquals(IpConfiguration.ProxySettings.NONE, config2.getProxySettings());
        assertEquals(IpConfiguration.ProxySettings.NONE, config3.getProxySettings());
    }

    /**
     * Tests the loading of networks using {@link SupplicantStaNetworkHal}.
     * Fills up only the SSID field of configs and uses it as a configKey as well.
     */
    @Test
    public void testLoadNetworksRemovesDuplicates() throws Exception {
        executeAndValidateInitializationSequence(false, false, false);
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, NETWORK_IDS);
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(int id) {
                return  mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMock).removeNetwork(anyInt());
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                config.SSID = NETWORK_SSIDS.get(0);
                config.networkId = 1;
                networkExtra.put(SupplicantStaNetworkHal.ID_STRING_KEY_CONFIG_KEY, config.SSID);
                return true;
            }
        }).when(mSupplicantStaNetworkMock1)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                config.SSID = NETWORK_SSIDS.get(1);
                config.networkId = 2;
                networkExtra.put(SupplicantStaNetworkHal.ID_STRING_KEY_CONFIG_KEY, config.SSID);
                return true;
            }
        }).when(mSupplicantStaNetworkMock2)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                config.SSID = NETWORK_SSIDS.get(1);
                config.networkId = 2;
                networkExtra.put(SupplicantStaNetworkHal.ID_STRING_KEY_CONFIG_KEY,
                        NETWORK_SSIDS.get(1));
                return true;
            }
        }).when(mSupplicantStaNetworkMock3)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));

        Map<String, WifiConfiguration> configs = new HashMap<>();
        SparseArray<Map<String, String>> extras = new SparseArray<>();
        assertTrue(mDut.loadNetworks(configs, extras));

        assertEquals(2, configs.size());
        assertEquals(2, extras.size());
        WifiConfiguration config1 = configs.get(NETWORK_SSIDS.get(0));
        WifiConfiguration config2 = configs.get(NETWORK_SSIDS.get(1));
        assertEquals(NETWORK_SSIDS.get(0), config1.SSID);
        assertEquals(NETWORK_SSIDS.get(1), config2.SSID);
        assertEquals(IpConfiguration.IpAssignment.DHCP, config1.getIpAssignment());
        assertEquals(IpConfiguration.IpAssignment.DHCP, config2.getIpAssignment());
        assertEquals(IpConfiguration.ProxySettings.NONE, config1.getProxySettings());
        assertEquals(IpConfiguration.ProxySettings.NONE, config2.getProxySettings());
    }

    /**
     * Tests the failure to load networks because of listNetworks failure.
     */
    @Test
    public void testLoadNetworksFailedDueToListNetworks() throws Exception {
        executeAndValidateInitializationSequence(false, false, false);
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, null);
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));

        Map<String, WifiConfiguration> configs = new HashMap<>();
        SparseArray<Map<String, String>> extras = new SparseArray<>();
        assertFalse(mDut.loadNetworks(configs, extras));
    }

    /**
     * Tests the failure to load networks because of loadWifiConfiguration failure.
     */
    @Test
    public void testLoadNetworksFailedDueToLoadWifiConfiguration() throws Exception {
        executeAndValidateInitializationSequence(false, false, false);
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, NETWORK_IDS);
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                config.SSID = NETWORK_SSIDS.get(0);
                config.networkId = 1;
                networkExtra.put(SupplicantStaNetworkHal.ID_STRING_KEY_CONFIG_KEY, config.SSID);
                return true;
            }
        }).when(mSupplicantStaNetworkMock1)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra) {
                return false;
            }
        }).when(mSupplicantStaNetworkMock2)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));

        Map<String, WifiConfiguration> configs = new HashMap<>();
        SparseArray<Map<String, String>> extras = new SparseArray<>();
        assertFalse(mDut.loadNetworks(configs, extras));
    }
    /**
     * Calls.initialize(), mocking various call back answers and verifying flow, asserting for the
     * expected result. Verifies if ISupplicantStaIface manager is initialized or reset.
     * Each of the arguments will cause a different failure mode when set true.
     */
    private void executeAndValidateInitializationSequence(boolean causeRemoteException,
            boolean getZeroInterfaces, boolean getNullInterface) throws Exception {
        boolean shouldSucceed = !causeRemoteException && !getZeroInterfaces && !getNullInterface;
        // Setup callback mock answers
        ArrayList<ISupplicant.IfaceInfo> interfaces;
        if (getZeroInterfaces) {
            interfaces = new ArrayList<>();
        } else {
            interfaces = mIfaceInfoList;
        }
        doAnswer(new GetListInterfacesAnswer(interfaces)).when(mISupplicantMock)
                .listInterfaces(any(ISupplicant.listInterfacesCallback.class));
        if (causeRemoteException) {
            doThrow(new RemoteException("Some error!!!"))
                    .when(mISupplicantMock).getInterface(any(ISupplicant.IfaceInfo.class),
                    any(ISupplicant.getInterfaceCallback.class));
        } else {
            doAnswer(new GetGetInterfaceAnswer(getNullInterface))
                    .when(mISupplicantMock).getInterface(any(ISupplicant.IfaceInfo.class),
                    any(ISupplicant.getInterfaceCallback.class));
        }

        mInOrder = inOrder(mServiceManagerMock, mISupplicantMock);
        // Initialize SupplicantStaIfaceHal, should call serviceManager.registerForNotifications
        assertTrue(mDut.initialize());
        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(
                eq(ISupplicant.kInterfaceName), eq(""), mServiceNotificationCaptor.capture());
        // act: cause the onRegistration(...) callback to execute
        mServiceNotificationCaptor.getValue().onRegistration(ISupplicant.kInterfaceName, "", true);

        assertTrue(mDut.isInitializationComplete() == shouldSucceed);
        // verify: listInterfaces is called
        mInOrder.verify(mISupplicantMock).listInterfaces(
                any(ISupplicant.listInterfacesCallback.class));
        if (!getZeroInterfaces) {
            mInOrder.verify(mISupplicantMock)
                    .getInterface(any(ISupplicant.IfaceInfo.class),
                    any(ISupplicant.getInterfaceCallback.class));
        }
    }


    private SupplicantStatus createSupplicantStatus(int code) {
        SupplicantStatus status = new SupplicantStatus();
        status.code = code;
        return status;
    }

    /**
     * Create an IfaceInfo with given type and name
     */
    private ISupplicant.IfaceInfo createIfaceInfo(int type, String name) {
        ISupplicant.IfaceInfo info = new ISupplicant.IfaceInfo();
        info.type = type;
        info.name = name;
        return info;
    }

    private class GetListInterfacesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ArrayList<ISupplicant.IfaceInfo> mInterfaceList;

        GetListInterfacesAnswer(ArrayList<ISupplicant.IfaceInfo> ifaces) {
            mInterfaceList = ifaces;
        }

        public void answer(ISupplicant.listInterfacesCallback cb) {
            cb.onValues(mStatusSuccess, mInterfaceList);
        }
    }

    private class GetGetInterfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        boolean mGetNullInterface;

        GetGetInterfaceAnswer(boolean getNullInterface) {
            mGetNullInterface = getNullInterface;
        }

        public void answer(ISupplicant.IfaceInfo iface, ISupplicant.getInterfaceCallback cb) {
            if (mGetNullInterface) {
                cb.onValues(mStatusSuccess, null);
            } else {
                cb.onValues(mStatusSuccess, mISupplicantIfaceMock);
            }
        }
    }
}
