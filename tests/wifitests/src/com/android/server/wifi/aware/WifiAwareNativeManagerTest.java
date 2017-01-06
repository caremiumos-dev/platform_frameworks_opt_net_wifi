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

package com.android.server.wifi.aware;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.hardware.wifi.V1_0.IWifiNanIface;

import com.android.server.wifi.HalInterfacePolicyManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test harness for WifiAwareNativeManager.
 */
public class WifiAwareNativeManagerTest {
    private WifiAwareNativeManager mDut;
    @Mock private WifiAwareStateManager mWifiAwareStateManagerMock;
    @Mock private HalInterfacePolicyManager mHalInterfacePolicyManagerMock;
    @Mock private IWifiNanIface mWifiNanIfaceMock;
    private ArgumentCaptor<HalInterfacePolicyManager.ManagerStatusCallback> mManagerStatusCbCaptor =
            ArgumentCaptor.forClass(HalInterfacePolicyManager.ManagerStatusCallback.class);
    private ArgumentCaptor<HalInterfacePolicyManager.InterfaceDestroyedListener>
            mDestroyedListenerCaptor = ArgumentCaptor.forClass(
            HalInterfacePolicyManager.InterfaceDestroyedListener.class);
    private ArgumentCaptor<HalInterfacePolicyManager.InterfaceAvailableForRequestListener>
            mAvailListenerCaptor = ArgumentCaptor.forClass(
            HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDut = new WifiAwareNativeManager(mWifiAwareStateManagerMock,
                mHalInterfacePolicyManagerMock);

        mInOrder = inOrder(mWifiAwareStateManagerMock, mHalInterfacePolicyManagerMock);
    }

    /**
     * Test the control flow of the manager:
     * 1. onReady
     * 2. null NAN iface
     * 3. onAvailableForRequest
     * 4. non-null NAN iface -> enableUsage
     * 5. onDown -> disableUsage
     * 6. onReady
     * 7. non-null NAN iface -> enableUsage
     * 8. onDestroyed -> disableUsage
     * 9. onDown
     */
    @Test
    public void testControlFlow() {
        // validate (and capture) that register manage status callback
        mInOrder.verify(mHalInterfacePolicyManagerMock).registerStatusCallback(
                mManagerStatusCbCaptor.capture());

        // 1 & 2 onReady: validate that trying to get a NAN interface (make sure gets a NULL)
        when(mHalInterfacePolicyManagerMock.createNanIface(
                any(HalInterfacePolicyManager.InterfaceDestroyedListener.class),
                any(HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class)))
                .thenReturn(null);
        mManagerStatusCbCaptor.getValue().onReady();
        mInOrder.verify(mHalInterfacePolicyManagerMock).createNanIface(
                mDestroyedListenerCaptor.capture(), mAvailListenerCaptor.capture());
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 3 & 4 onAvailableForRequest + non-null return value: validate that enables usage
        when(mHalInterfacePolicyManagerMock.createNanIface(
                any(HalInterfacePolicyManager.InterfaceDestroyedListener.class),
                any(HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class)))
                .thenReturn(mWifiNanIfaceMock);
        mAvailListenerCaptor.getValue().onAvailableForRequest();
        mInOrder.verify(mHalInterfacePolicyManagerMock).createNanIface(
                mDestroyedListenerCaptor.capture(), mAvailListenerCaptor.capture());
        mInOrder.verify(mWifiAwareStateManagerMock).enableUsage();
        collector.checkThat("non-null interface", mDut.getWifiNanIface(),
                equalTo(mWifiNanIfaceMock));

        // 5 onDown: disable usage
        mManagerStatusCbCaptor.getValue().onDown();
        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage();
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 6 & 7 onReady + non-null NAN interface: enable usage
        when(mHalInterfacePolicyManagerMock.createNanIface(
                any(HalInterfacePolicyManager.InterfaceDestroyedListener.class),
                any(HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class)))
                .thenReturn(mWifiNanIfaceMock);
        mManagerStatusCbCaptor.getValue().onReady();
        mInOrder.verify(mHalInterfacePolicyManagerMock).createNanIface(
                mDestroyedListenerCaptor.capture(), mAvailListenerCaptor.capture());
        mInOrder.verify(mWifiAwareStateManagerMock).enableUsage();
        collector.checkThat("non-null interface", mDut.getWifiNanIface(),
                equalTo(mWifiNanIfaceMock));

        // 8 onDestroyed: disable usage
        mDestroyedListenerCaptor.getValue().onDestroyed();
        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage();
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 9 onDown: nothing more
        mManagerStatusCbCaptor.getValue().onDown();

        verifyNoMoreInteractions(mWifiAwareStateManagerMock, mHalInterfacePolicyManagerMock);
    }
}
