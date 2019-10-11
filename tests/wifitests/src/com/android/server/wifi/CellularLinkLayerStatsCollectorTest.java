/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.TelephonyManager.NETWORK_TYPE_CDMA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0;
import static android.telephony.TelephonyManager.NETWORK_TYPE_GSM;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_NR;
import static android.telephony.TelephonyManager.NETWORK_TYPE_TD_SCDMA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UMTS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.validateMockitoUsage;

import android.content.Context;
import android.telephony.CellIdentity;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.NetworkType;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/**
 * Unit tests for {@link com.android.server.wifi.CellularLinkLayerStatsCollector}.
 */
@SmallTest
public class CellularLinkLayerStatsCollectorTest {
    private static final String TAG = "CellCollectorTest";

    private static final int DBM_VAL = -110;
    private static final int DB_VAL = -10;
    private static final int DB_VAL_EVDO = 4;
    private static final int SUBID = 23;

    private static final int[] ALL_NETWORK_TYPES = new int[] {
            NETWORK_TYPE_UNKNOWN,
            NETWORK_TYPE_LTE,
            NETWORK_TYPE_CDMA,
            NETWORK_TYPE_EVDO_0,
            NETWORK_TYPE_TD_SCDMA,
            NETWORK_TYPE_UMTS,
            NETWORK_TYPE_GSM,
            NETWORK_TYPE_NR};

    private MockitoSession mMockingSession;
    private CellularLinkLayerStatsCollector mCollectorUT;

    private static class StubCellIdentity extends CellIdentity {
        private final int mKey;

        StubCellIdentity(int equalityKey) {
            super("StubCellIdentity", 0, null, null, null, null);
            mKey = equalityKey;
        }

        @Override
        public CellLocation asCellLocation() {
            return null;
        }

        @Override
        public boolean equals(Object rhs) {
            if (!(rhs instanceof StubCellIdentity)) return false;
            return mKey == ((StubCellIdentity) rhs).mKey;
        }

        @Override
        public String toString() {
            return super.toString() + ", mKey=" + mKey;
        }
    }

    @Mock Context mMockContext;
    @Mock TelephonyManager mMockTelephonyManager;
    @Mock SubscriptionManager mMockSubscriptionManager;

    @Mock ServiceState mMockServiceState;
    @Mock NetworkRegistrationInfo mMockNetworkRegistrationInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                .thenReturn(mMockSubscriptionManager);
        when(mMockTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.getServiceState()).thenReturn(mMockServiceState);

        when(mMockServiceState.getNetworkRegistrationInfo(
                    eq(NetworkRegistrationInfo.DOMAIN_PS),
                    eq(TRANSPORT_TYPE_WWAN))).thenReturn(mMockNetworkRegistrationInfo);

        mMockingSession = mockitoSession().mockStatic(SubscriptionManager.class).startMocking();
        when(SubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(SUBID);

        mCollectorUT = new CellularLinkLayerStatsCollector(mMockContext);
    }

    @After
    public void cleanUp() throws Exception {
        validateMockitoUsage();
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    private SignalStrength generateSignalStrength(int ssDbm, int ssDb,
                @NetworkType int networkType) {
        final int invalid = SignalStrength.INVALID;
        CellSignalStrengthLte lteSs = new CellSignalStrengthLte();
        CellSignalStrengthNr nrSs = new CellSignalStrengthNr();
        CellSignalStrengthGsm gsmSs = new CellSignalStrengthGsm();
        CellSignalStrengthCdma cdmaSs = new CellSignalStrengthCdma();
        CellSignalStrengthTdscdma tdscdmaSs = new CellSignalStrengthTdscdma();
        CellSignalStrengthWcdma wcdmaSs = new CellSignalStrengthWcdma();

        if (networkType == NETWORK_TYPE_UNKNOWN) {
            return new SignalStrength();
        } else if (networkType == NETWORK_TYPE_LTE) {
            lteSs = new CellSignalStrengthLte(invalid, ssDbm, ssDb, invalid, invalid, invalid);
        } else if (networkType == NETWORK_TYPE_CDMA) {
            cdmaSs = new CellSignalStrengthCdma(ssDbm, ssDb, ssDbm, invalid, invalid);
        } else if (networkType == NETWORK_TYPE_EVDO_0) {
            cdmaSs = new CellSignalStrengthCdma(ssDbm, invalid, ssDbm, invalid, ssDb);
        } else if (networkType == NETWORK_TYPE_TD_SCDMA) {
            tdscdmaSs = new CellSignalStrengthTdscdma(invalid, invalid, ssDbm);
        } else if (networkType == NETWORK_TYPE_UMTS) {
            wcdmaSs = new CellSignalStrengthWcdma(invalid, invalid, ssDbm, ssDb);
        } else if (networkType == NETWORK_TYPE_GSM) {
            // There is no SNR equivalent for GSM
            gsmSs = new CellSignalStrengthGsm(ssDbm, invalid, invalid);
        } else if (networkType == NETWORK_TYPE_NR) {
            nrSs = new CellSignalStrengthNr(invalid , invalid, invalid, ssDbm, ssDb, invalid);
        } else {
            return null;
        }
        return new SignalStrength(cdmaSs, gsmSs, wcdmaSs, tdscdmaSs, lteSs, nrSs);
    }

    @Test
    public void testEmptySignalStrengthLte() throws Exception {
        // Technically not needed due to Mockito default behavior;
        // but here since it's the point of the test...
        when(mMockTelephonyManager.getSignalStrength()).thenReturn(null);

        when(mMockNetworkRegistrationInfo.getCellIdentity()).thenReturn(new StubCellIdentity(0));
        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(NETWORK_TYPE_LTE);

        CellularLinkLayerStats testStats = mCollectorUT.update();

        assertEquals(false, testStats.getIsSameRegisteredCell());
        assertEquals(SignalStrength.INVALID, testStats.getSignalStrengthDb());
        assertEquals(SignalStrength.INVALID, testStats.getSignalStrengthDbm());
        assertEquals(NETWORK_TYPE_UNKNOWN, testStats.getDataNetworkType());
    }

    private void verifyCollectorUpdate(
            @NetworkType int networkType, int equalityKey, boolean isSameCell) throws Exception {
        int ssDb, ssDbm;
        switch(networkType) {
            case NETWORK_TYPE_UNKNOWN:
                ssDb = ssDbm = SignalStrength.INVALID;
                break;
            case NETWORK_TYPE_EVDO_0:
                ssDb = DB_VAL_EVDO;
                ssDbm = DBM_VAL;
                break;
            // There are no SNR measurements defined for GSM or TD-SCDMA
            case NETWORK_TYPE_GSM: /* fall through */
            case NETWORK_TYPE_TD_SCDMA:
                ssDb = SignalStrength.INVALID;
                ssDbm = DBM_VAL;
                break;
            default:
                ssDb = DB_VAL;
                ssDbm = DBM_VAL;
                break;
        }

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(networkType);
        when(mMockNetworkRegistrationInfo.getCellIdentity())
                .thenReturn(new StubCellIdentity(equalityKey));
        when(mMockTelephonyManager.getSignalStrength()).thenReturn(
                generateSignalStrength(DBM_VAL, ssDb, networkType));

        CellularLinkLayerStats testStats = mCollectorUT.update();

        assertEquals(isSameCell, testStats.getIsSameRegisteredCell());
        assertEquals(ssDb, testStats.getSignalStrengthDb());
        assertEquals(ssDbm, testStats.getSignalStrengthDbm());
        assertEquals(networkType, testStats.getDataNetworkType());
    }

    @Test
    public void testRepeatCellInfoTypeTwiceWithSameCell() throws Exception {
        for (int nwType : ALL_NETWORK_TYPES) {
            // Ensure that the CellIdentities match; use NW-type to ensure different CIDs have
            // different keys for testing equality.
            verifyCollectorUpdate(nwType, nwType, false);
            // True unless the network type is unknown, in which case always false
            verifyCollectorUpdate(nwType, nwType, nwType != NETWORK_TYPE_UNKNOWN);
        }
    }

    @Test
    public void testRepeatCellInfoTypeTwiceWithCellChange() throws Exception {
        for (int nwType : ALL_NETWORK_TYPES) {
            // Ensure that the CellIdentities don't match when CellIdentities don't match
            verifyCollectorUpdate(nwType, 1, false);
            verifyCollectorUpdate(nwType, 2, false);
        }
    }

    @Test
    public void testLoopOverAllNetworksWithoutRepeat() throws Exception {
        for (int nwType : ALL_NETWORK_TYPES) {
            verifyCollectorUpdate(nwType, nwType, false);
        }
    }
}
