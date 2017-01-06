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

import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IWifiEventCallback;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IWifiP2pIface;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.IHwBinder;
import android.util.Log;

import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * Unit test harness for HalInterfacePolicyManager.
 */
public class HalInterfacePolicyManagerTest {
    private HalInterfacePolicyManager mDut;
    @Mock IServiceManager mServiceManagerMock;
    @Mock IWifi mWifiMock;
    @Mock IWifiChip mWifiChipMock;
    @Mock IWifiStaIface mWifiStaIfaceMock;
    @Mock IWifiApIface mWifiApIfaceMock;
    @Mock IWifiP2pIface mWifiP2pIfaceMock;
    @Mock IWifiNanIface mWifiNanIfaceMock;
    @Mock HalInterfacePolicyManager.ManagerStatusCallback mManagerStatusCallbackMock;
    private ArgumentCaptor<IHwBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IServiceNotification.Stub> mServiceNotificationCaptor =
            ArgumentCaptor.forClass(IServiceNotification.Stub.class);
    private ArgumentCaptor<IWifiEventCallback> mWifiEventCallbackCaptor = ArgumentCaptor.forClass(
            IWifiEventCallback.class);
    private ArgumentCaptor<IWifiChipEventCallback> mWifiChipEventCallbackCaptor =
            ArgumentCaptor.forClass(IWifiChipEventCallback.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();
    private WifiStatus mStatusOk;
    private WifiStatus mStatusFail;
    private ArrayList<Integer> mChipIds;
    private ArrayList<IWifiChip.ChipMode> mChipModeBaseline; // simulate single-band
    private final int mStaChipModeId = 0;
    private final int mApChipModeId = 1;

    private class HalInterfacePolicyManagerSpy extends HalInterfacePolicyManager {
        @Override
        protected IWifi getWifiServiceMockable() {
            return mWifiMock;
        }

        @Override
        protected IServiceManager getServiceManagerMockable() {
            return mServiceManagerMock;
        }
    }

    @Before
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        // initialize dummy status objects
        mStatusOk = new WifiStatus();
        mStatusOk.code = WifiStatusCode.SUCCESS;
        mStatusFail = new WifiStatus();
        mStatusFail.code = WifiStatusCode.ERROR_UNKNOWN;

        // chip Id configuration
        mChipIds = new ArrayList<>();
        mChipIds.add(10);
        mChipIds.add(5);
        mChipIds.add(1);
        mChipIds.add(25);
        doAnswer(new GetChipIdsAnswer(mStatusOk, mChipIds)).when(mWifiMock).getChipIds(
                any(IWifi.getChipIdsCallback.class));

        doAnswer(new GetChipAnswer(mStatusOk, mWifiChipMock)).when(mWifiMock).getChip(anyInt(),
                any(IWifi.getChipCallback.class));

        // initialize dummy chip modes
        IWifiChip.ChipMode cm;
        IWifiChip.ChipIfaceCombination cic;
        IWifiChip.ChipIfaceCombinationLimit cicl;

        // Simulate SB:
        //   Mode 0: 1xSTA + 1x{P2P,NAN}
        //   Mode 1: 1xAP
        mChipModeBaseline = new ArrayList<>();
        cm = new IWifiChip.ChipMode();
        cm.id = mStaChipModeId;

        cic = new IWifiChip.ChipIfaceCombination();

        cicl = new IWifiChip.ChipIfaceCombinationLimit();
        cicl.maxIfaces = 1;
        cicl.types.add(IfaceType.STA);
        cic.limits.add(cicl);

        cicl = new IWifiChip.ChipIfaceCombinationLimit();
        cicl.maxIfaces = 1;
        cicl.types.add(IfaceType.P2P);
        cicl.types.add(IfaceType.NAN);
        cic.limits.add(cicl);
        cm.availableCombinations.add(cic);
        mChipModeBaseline.add(cm);

        cm = new IWifiChip.ChipMode();
        cm.id = mApChipModeId;
        cic = new IWifiChip.ChipIfaceCombination();
        cicl = new IWifiChip.ChipIfaceCombinationLimit();
        cicl.maxIfaces = 1;
        cicl.types.add(IfaceType.AP);
        cic.limits.add(cicl);
        cm.availableCombinations.add(cic);
        mChipModeBaseline.add(cm);

        doAnswer(new GetAvailableModesAnswer(mStatusOk, mChipModeBaseline)).when(
                mWifiChipMock).getAvailableModes(any(IWifiChip.getAvailableModesCallback.class));

        when(mServiceManagerMock.linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong())).thenReturn(true);
        when(mServiceManagerMock.registerForNotifications(anyString(), anyString(),
                any(IServiceNotification.Stub.class))).thenReturn(true);
        when(mWifiMock.linkToDeath(any(IHwBinder.DeathRecipient.class), anyLong())).thenReturn(
                true);
        when(mWifiMock.registerEventCallback(any(IWifiEventCallback.class))).thenReturn(mStatusOk);
        when(mWifiMock.start()).thenReturn(mStatusOk);
        when(mWifiChipMock.registerEventCallback(any(IWifiChipEventCallback.class))).thenReturn(
                mStatusOk);
        when(mWifiChipMock.configureChip(anyInt())).thenReturn(mStatusOk);

        doAnswer(new CreateXxxIfaceAnswer(mStatusOk, mWifiStaIfaceMock)).when(
                mWifiChipMock).createStaIface(any(IWifiChip.createStaIfaceCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(mStatusOk, mWifiApIfaceMock)).when(
                mWifiChipMock).createApIface(any(IWifiChip.createApIfaceCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(mStatusOk, mWifiP2pIfaceMock)).when(
                mWifiChipMock).createP2pIface(any(IWifiChip.createP2pIfaceCallback.class));
        doAnswer(new CreateXxxIfaceAnswer(mStatusOk, mWifiNanIfaceMock)).when(
                mWifiChipMock).createNanIface(any(IWifiChip.createNanIfaceCallback.class));

        when(mWifiChipMock.removeStaIface(anyString())).thenReturn(mStatusOk);
        when(mWifiChipMock.removeApIface(anyString())).thenReturn(mStatusOk);
        when(mWifiChipMock.removeP2pIface(anyString())).thenReturn(mStatusOk);
        when(mWifiChipMock.removeNanIface(anyString())).thenReturn(mStatusOk);

        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock);

        mDut = new HalInterfacePolicyManagerSpy();
    }

    /**
     * Print out the dump of the policy manager after each test. Not used in test validation
     * (internal state) - but can help in debugging failed tests.
     */
    @After
    public void after() throws Exception {
        dumpDut("after: ");
    }

    /**
     * Test basic startup flow:
     * - IServiceManager registrations
     * - IWifi registrations
     * - IWifi startup delayed -> triggers onReady
     * - Another registration of manager start -> immediate onReady
     */
    @Test
    public void testStartupFlow() throws Exception {
        // standard startup
        validateStartupSequence();

        // another status callback registration - expect immediate callback
        HalInterfacePolicyManager.ManagerStatusCallback localMock = mock(
                HalInterfacePolicyManager.ManagerStatusCallback.class);
        mDut.registerStatusCallback(localMock);
        verify(localMock).onReady();

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock);
    }

    /**
     * Validate IWifi death listener and registration flow.
     */
    @Test
    public void testWifiDeathAndRegistration() throws Exception {
        // standard startup
        validateStartupSequence();

        // re-configure to already be started when queried
        when(mWifiMock.isStarted()).thenReturn(true);

        // act: IWifi service death
        mDeathRecipientCaptor.getValue().serviceDied(0);

        // verify: getting onDown
        mInOrder.verify(mManagerStatusCallbackMock).onDown();

        // act: service startup
        mServiceNotificationCaptor.getValue().onRegistration(IWifi.kInterfaceName, "", true);

        // verify: initialization of IWifi
        mInOrder.verify(mWifiMock).linkToDeath(mDeathRecipientCaptor.capture(), anyLong());
        mInOrder.verify(mWifiMock).registerEventCallback(mWifiEventCallbackCaptor.capture());
        mInOrder.verify(mWifiMock).isStarted();

        // verify that onReady called
        mInOrder.verify(mManagerStatusCallbackMock).onReady();

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock);
    }

    /**
     * Validate IWifi onFailure causes a restart attempt.
     */
    @Test
    public void testWifiFail() throws Exception {
        // standard startup
        validateStartupSequence();

        // re-configure to already be started when queried
        when(mWifiMock.isStarted()).thenReturn(true);

        // act: IWifi failure
        mWifiEventCallbackCaptor.getValue().onFailure(mStatusFail);

        // verify: getting onDown
        mInOrder.verify(mManagerStatusCallbackMock).onDown();

        // verify: trying to re-start Wifi (not re-initialize it)
        mInOrder.verify(mWifiMock).isStarted();

        // verify that onReady called
        mInOrder.verify(mManagerStatusCallbackMock).onReady();

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock);
    }

    /**
     * Validate creation of STA interface from blank start-up.
     */
    @Test
    public void testCreateStaInterfaceNoInitMode() throws Exception {
        HalInterfacePolicyManager.InterfaceDestroyedListener idl = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        validateInterfaceSequence(false, false, -1000, IfaceType.STA, "sta0", mStaChipModeId, null,
                idl, iafrl);

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock, idl, iafrl);
    }

    /**
     * Validate creation of AP interface from blank start-up.
     */
    @Test
    public void testCreateApInterfaceNoInitMode() throws Exception {
        HalInterfacePolicyManager.InterfaceDestroyedListener idl = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        validateInterfaceSequence(false, false, -1000, IfaceType.AP, "ap0", mApChipModeId, null,
                idl, iafrl);

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock, idl, iafrl);
    }

    /**
     * Validate creation of P2P interface from blank start-up.
     */
    @Test
    public void testCreateP2pInterfaceNoInitMode() throws Exception {
        HalInterfacePolicyManager.InterfaceDestroyedListener idl = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        validateInterfaceSequence(false, false, -1000, IfaceType.P2P, "p2p0", mStaChipModeId, null,
                idl, iafrl);

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock, idl, iafrl);
    }

    /**
     * Validate creation of NAN interface from blank start-up.
     */
    @Test
    public void testCreateNanInterfaceNoInitMode() throws Exception {
        HalInterfacePolicyManager.InterfaceDestroyedListener idl = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        validateInterfaceSequence(false, false, -1000, IfaceType.NAN, "nan0", mStaChipModeId, null,
                idl, iafrl);

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock, idl, iafrl);
    }

    /**
     * Validate creation of AP interface when in STA mode - but with no interface created.
     */
    @Test
    public void testCreateApWithStaModeUp() throws Exception {
        HalInterfacePolicyManager.InterfaceDestroyedListener idl = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        validateInterfaceSequence(false, true, mStaChipModeId, IfaceType.AP, "ap0", mApChipModeId,
                null, idl, iafrl);

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock, idl, iafrl);
    }

    /**
     * Validate AP up/down creation of AP interface when a STA already created. Expect:
     * - STA created
     * - P2P created
     * - When AP requested:
     *   - STA & P2P torn down
     *   - AP created
     * - STA creation refused
     * - When AP destroyed:
     *   - Get callback that STA available
     *   - Can create STA when requested
     */
    @Test
    public void testCreateApWithStaUp() throws Exception {
        HalInterfacePolicyManager.InterfaceDestroyedListener staDestroyedListener = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener staAvailListener = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        HalInterfacePolicyManager.InterfaceDestroyedListener apDestroyedListener = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener apAvailListener = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        HalInterfacePolicyManager.InterfaceDestroyedListener p2pDestroyedListener = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener p2pAvailListener = null;

        String apIfaceName = "ap0";

        // Request STA
        validateInterfaceSequence(false, false, -1000, IfaceType.STA, "sta0", mStaChipModeId,
                null, staDestroyedListener, staAvailListener);
        dumpDut("after sta setup: ");

        // Request P2P
        validateInterfaceSequence(true, true, mStaChipModeId, IfaceType.P2P, "p2p0", mStaChipModeId,
                null, p2pDestroyedListener, p2pAvailListener);
        dumpDut("after p2p setup: ");

        // Request AP
        validateInterfaceSequence(true, true, mStaChipModeId, IfaceType.AP, apIfaceName,
                mApChipModeId, null, apDestroyedListener, apAvailListener,
                staDestroyedListener, p2pDestroyedListener);
        dumpDut("after ap setup:");

        // Request STA: expect failure
        IWifiStaIface staIface = mDut.createStaIface(staDestroyedListener, staAvailListener);
        collector.checkThat("STA can't be created", staIface, IsNull.nullValue());
        dumpDut("after attempting STA setup:");

        // Destroy AP interface
        IWifiApIface apIface = mDut.getApIface(apIfaceName);
        collector.checkThat("AP was created - non null", apIface, IsNull.notNullValue());
        boolean status = mDut.removeApIface(apIfaceName);
        mInOrder.verify(mWifiChipMock).removeApIface(apIfaceName);
        collector.checkThat("AP removal success", status, equalTo(true));
        dumpDut("after removing AP:");

        // execute callback from chip
        mWifiChipEventCallbackCaptor.getValue().onIfaceRemoved(IfaceType.AP, apIfaceName);
        verify(apDestroyedListener).onDestroyed();
        verify(staAvailListener).onAvailableForRequest();

        // Request STA: expect success now
        validateInterfaceSequence(true, true, mApChipModeId, IfaceType.STA, "sta0", mStaChipModeId,
                null, staDestroyedListener, staAvailListener);
        dumpDut("after new STA request:");

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock, staDestroyedListener, staAvailListener,
                p2pDestroyedListener, apDestroyedListener, apAvailListener);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - Get callback that NAN available
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractions() throws Exception {
        HalInterfacePolicyManager.InterfaceDestroyedListener staDestroyedListener = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener staAvailListener = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        HalInterfacePolicyManager.InterfaceDestroyedListener nanDestroyedListener = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener nanAvailListener = mock(
                HalInterfacePolicyManager.InterfaceAvailableForRequestListener.class);

        HalInterfacePolicyManager.InterfaceDestroyedListener p2pDestroyedListener = mock(
                HalInterfacePolicyManager.InterfaceDestroyedListener.class);
        HalInterfacePolicyManager.InterfaceAvailableForRequestListener p2pAvailListener = null;

        String nanIfaceName = "nan0";
        String p2pIfaceName = "p2p0";

        // Request STA
        validateInterfaceSequence(false, false, -1000, IfaceType.STA, "sta0", mStaChipModeId,
                null, staDestroyedListener, staAvailListener);
        dumpDut("after sta setup: ");

        // Request NAN
        validateInterfaceSequence(true, true, mStaChipModeId, IfaceType.NAN, nanIfaceName,
                mStaChipModeId, null, nanDestroyedListener, nanAvailListener);
        IWifiNanIface nanIface = mDut.getNanIface(nanIfaceName);
        collector.checkThat("NAN was created - non null", nanIface, IsNull.notNullValue());
        dumpDut("after nan setup: ");

        // Request P2P
        validateInterfaceSequence(true, true, mStaChipModeId, IfaceType.P2P, p2pIfaceName,
                mStaChipModeId, new IWifiIface[]{nanIface}, p2pDestroyedListener, p2pAvailListener,
                nanDestroyedListener);
        dumpDut("after p2p setup:");

        // Request NAN: expect failure
        nanIface = mDut.createNanIface(nanDestroyedListener, nanAvailListener);
        collector.checkThat("NAN can't be created", nanIface, IsNull.nullValue());
        dumpDut("after attempting NAN setup:");

        // Destroy P2P interface
        IWifiP2pIface p2pIface = mDut.getP2pIface(p2pIfaceName);
        collector.checkThat("P2P was created - non null", p2pIface, IsNull.notNullValue());
        boolean status = mDut.removeP2pIface(p2pIfaceName);
        mInOrder.verify(mWifiChipMock).removeP2pIface(p2pIfaceName);
        collector.checkThat("P2P removal success", status, equalTo(true));
        dumpDut("after removing P2P:");

        // execute callback from chip
        mWifiChipEventCallbackCaptor.getValue().onIfaceRemoved(IfaceType.P2P, p2pIfaceName);
        verify(p2pDestroyedListener).onDestroyed();
        verify(nanAvailListener).onAvailableForRequest();

        // Request NAN: expect success now
        validateInterfaceSequence(true, true, mStaChipModeId, IfaceType.NAN, "nan0", mStaChipModeId,
                null, nanDestroyedListener, nanAvailListener);
        dumpDut("after new NAN request:");

        verifyNoMoreInteractions(mServiceManagerMock, mWifiMock, mWifiChipMock,
                mManagerStatusCallbackMock, staDestroyedListener, staAvailListener,
                nanDestroyedListener, nanAvailListener, p2pDestroyedListener);
    }

    // utilities
    private void dumpDut(String prefix) {
        StringWriter sw = new StringWriter();
        mDut.dump(null, new PrintWriter(sw), null);
        Log.e("HalInterfacePolicyMgr", prefix + sw.toString());
    }

    private void validateStartupSequence() throws Exception {
        // configure
        when(mWifiMock.isStarted()).thenReturn(false);

        // act: start policy manager
        mDut.registerStatusCallback(mManagerStatusCallbackMock);
        mDut.start();

        // verify: start-up sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(eq(IWifi.kInterfaceName),
                eq(""), mServiceNotificationCaptor.capture());
        mInOrder.verify(mWifiMock).linkToDeath(mDeathRecipientCaptor.capture(), anyLong());
        mInOrder.verify(mWifiMock).registerEventCallback(mWifiEventCallbackCaptor.capture());
        mInOrder.verify(mWifiMock).isStarted();
        mInOrder.verify(mWifiMock).start();

        // act: trigger onStarted callback of IWifi
        mWifiEventCallbackCaptor.getValue().onStart();

        // verify: onReady called
        mInOrder.verify(mManagerStatusCallbackMock).onReady();
    }

    private void validateChipStartupSequence() throws Exception {
        mInOrder.verify(mWifiMock).getChipIds(any(IWifi.getChipIdsCallback.class));
        mInOrder.verify(mWifiMock).getChip(eq(mChipIds.get(0)), any(IWifi.getChipCallback.class));
        mInOrder.verify(mWifiChipMock).registerEventCallback(
                mWifiChipEventCallbackCaptor.capture());
        mInOrder.verify(mWifiChipMock).getAvailableModes(
                any(IWifiChip.getAvailableModesCallback.class));
        mInOrder.verify(mWifiChipMock).getMode(any(IWifiChip.getModeCallback.class));
    }

    private void validateInterfaceSequence(boolean isContinuation, boolean chipModeValid,
            int chipModeId, int ifaceTypeToCreate, String ifaceName, int finalChipMode,
            IWifiIface[] tearDownList,
            HalInterfacePolicyManager.InterfaceDestroyedListener destroyedListener,
            HalInterfacePolicyManager.InterfaceAvailableForRequestListener availableListener,
            HalInterfacePolicyManager.InterfaceDestroyedListener...
                    destroyedInterfacesDestroyedListeners)
            throws Exception {
        // configure chip mode response
        doAnswer(new GetModeAnswer(chipModeValid ? mStatusOk : mStatusFail, chipModeId)).when(
                mWifiChipMock).getMode(any(IWifiChip.getModeCallback.class));

        // standard starts
        if (!isContinuation) {
            validateStartupSequence();
        }

        // act: request the interface
        switch (ifaceTypeToCreate) {
            case IfaceType.STA:
                doAnswer(new GetNameAnswer(ifaceName)).when(
                        mWifiStaIfaceMock).getName(any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.STA)).when(
                        mWifiStaIfaceMock).getType(any(IWifiIface.getTypeCallback.class));
                mDut.createStaIface(destroyedListener, availableListener);
                break;
            case IfaceType.AP:
                doAnswer(new GetNameAnswer(ifaceName)).when(
                        mWifiApIfaceMock).getName(any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.AP)).when(
                        mWifiApIfaceMock).getType(any(IWifiIface.getTypeCallback.class));
                mDut.createApIface(destroyedListener, availableListener);
                break;
            case IfaceType.P2P:
                doAnswer(new GetNameAnswer(ifaceName)).when(
                        mWifiP2pIfaceMock).getName(any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.P2P)).when(
                        mWifiP2pIfaceMock).getType(any(IWifiIface.getTypeCallback.class));
                mDut.createP2pIface(destroyedListener, availableListener);
                break;
            case IfaceType.NAN:
                doAnswer(new GetNameAnswer(ifaceName)).when(
                        mWifiNanIfaceMock).getName(any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.NAN)).when(
                        mWifiNanIfaceMock).getType(any(IWifiIface.getTypeCallback.class));
                mDut.createNanIface(destroyedListener, availableListener);
                break;
        }

        // validate: chip start-up
        if (!isContinuation) {
            validateChipStartupSequence();
        }

        // validate: optional switch to the requested mode
        if (chipModeId != finalChipMode) {
            mInOrder.verify(mWifiChipMock).configureChip(finalChipMode);
        }

        // validate: optional tear down of interfaces
        if (tearDownList != null) {
            for (IWifiIface tearDownIface: tearDownList) {
                switch (getType(tearDownIface)) {
                    case IfaceType.STA:
                        mInOrder.verify(mWifiChipMock).removeStaIface(getName(tearDownIface));
                        break;
                    case IfaceType.AP:
                        mInOrder.verify(mWifiChipMock).removeApIface(getName(tearDownIface));
                        break;
                    case IfaceType.P2P:
                        mInOrder.verify(mWifiChipMock).removeP2pIface(getName(tearDownIface));
                        break;
                    case IfaceType.NAN:
                        mInOrder.verify(mWifiChipMock).removeNanIface(getName(tearDownIface));
                        break;
                }
            }
        }

        // validate: create STA
        switch (ifaceTypeToCreate) {
            case IfaceType.STA:
                mInOrder.verify(mWifiChipMock).createStaIface(
                        any(IWifiChip.createStaIfaceCallback.class));
                break;
            case IfaceType.AP:
                mInOrder.verify(mWifiChipMock).createApIface(
                        any(IWifiChip.createApIfaceCallback.class));
                break;
            case IfaceType.P2P:
                mInOrder.verify(mWifiChipMock).createP2pIface(
                        any(IWifiChip.createP2pIfaceCallback.class));
                break;
            case IfaceType.NAN:
                mInOrder.verify(mWifiChipMock).createNanIface(
                        any(IWifiChip.createNanIfaceCallback.class));
                break;
        }

        // act: callback on chip mode change
        mWifiChipEventCallbackCaptor.getValue().onChipReconfigured(finalChipMode);

        // verify: callbacks on deleted interfaces
        for (int i = 0; i < destroyedInterfacesDestroyedListeners.length; ++i) {
            verify(destroyedInterfacesDestroyedListeners[i]).onDestroyed();
        }
    }

    private int getType(IWifiIface iface) throws Exception {
        Mutable<Integer> typeResp = new Mutable<>();
        iface.getType((WifiStatus status, int type) -> {
            typeResp.value = type;
        });
        return typeResp.value;
    }

    private String getName(IWifiIface iface) throws Exception {
        Mutable<String> nameResp = new Mutable<>();
        iface.getName((WifiStatus status, String name) -> {
            nameResp.value = name;
        });
        return nameResp.value;
    }

    private static class Mutable<E> {
        public E value;

        Mutable() {
            value = null;
        }

        Mutable(E value) {
            this.value = value;
        }
    }

    // Answer objects
    private class GetChipIdsAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private ArrayList<Integer> mChipIds;

        GetChipIdsAnswer(WifiStatus status, ArrayList<Integer> chipIds) {
            mStatus = status;
            mChipIds = chipIds;
        }

        public void answer(IWifi.getChipIdsCallback cb) {
            cb.onValues(mStatus, mChipIds);
        }
    }

    private class GetChipAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private IWifiChip mChip;

        GetChipAnswer(WifiStatus status, IWifiChip chip) {
            mStatus = status;
            mChip = chip;
        }

        public void answer(int chipId, IWifi.getChipCallback cb) {
            cb.onValues(mStatus, mChip);
        }
    }

    private class GetAvailableModesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private ArrayList<IWifiChip.ChipMode> mAvailableModes;

        GetAvailableModesAnswer(WifiStatus status, ArrayList<IWifiChip.ChipMode> availableModes) {
            mStatus = status;
            mAvailableModes = availableModes;
        }

        public void answer(IWifiChip.getAvailableModesCallback cb) {
            cb.onValues(mStatus, mAvailableModes);
        }
    }

    private class GetModeAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private int mMode;

        GetModeAnswer(WifiStatus status, int mode) {
            mStatus = status;
            mMode = mode;
        }

        public void answer(IWifiChip.getModeCallback cb) {
            cb.onValues(mStatus, mMode);
        }
    }

    private class CreateXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private IWifiIface mWifiIface;

        CreateXxxIfaceAnswer(WifiStatus status, IWifiIface wifiIface) {
            mStatus = status;
            mWifiIface = wifiIface;
        }

        public void answer(IWifiChip.createStaIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiStaIface) mWifiIface);
        }

        public void answer(IWifiChip.createApIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiApIface) mWifiIface);
        }

        public void answer(IWifiChip.createP2pIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiP2pIface) mWifiIface);
        }

        public void answer(IWifiChip.createNanIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiNanIface) mWifiIface);
        }
    }

    private class GetNameAnswer extends MockAnswerUtil.AnswerWithArguments {
        private String mName;

        GetNameAnswer(String name) {
            mName = name;
        }

        public void answer(IWifiIface.getNameCallback cb) {
            cb.onValues(mStatusOk, mName);
        }
    }

    private class GetTypeAnswer extends MockAnswerUtil.AnswerWithArguments {
        private int mType;

        GetTypeAnswer(int type) {
            mType = type;
        }

        public void answer(IWifiIface.getTypeCallback cb) {
            cb.onValues(mStatusOk, mType);
        }
    }
}
