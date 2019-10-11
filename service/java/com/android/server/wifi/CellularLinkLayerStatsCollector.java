/*
 * Copyright 2019 The Android Open Source Project
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

import android.annotation.Nullable;
import android.content.Context;
import android.telephony.CellIdentity;
import android.telephony.CellSignalStrength;
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
import android.util.Log;

import java.util.List;
import java.util.Objects;

/**
 * A class collecting the latest cellular link layer stats
 */
public class CellularLinkLayerStatsCollector {
    private static final String TAG = "CellStatsCollector";
    private static final boolean DBG = false;

    private Context mContext;
    private SubscriptionManager mSubManager = null;

    private int mCachedDefaultDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    // Do not trust this value directly; use getDefaultDataTelephonyManager();
    private TelephonyManager mCachedDefaultDataTelephonyManager = null;

    private CellIdentity mLastPrimaryCellId = null;
    private int mLastDataNetworkType = NETWORK_TYPE_UNKNOWN;

    public CellularLinkLayerStatsCollector(Context context) {
        mContext = context;
    }

    /**
     * Get the latest DataNetworkType, SignalStrength, CellInfo and other information from
     * default data sim's TelephonyManager, parse the values of primary registered cell and return
     * them as an instance of CellularLinkLayerStats
     */
    public CellularLinkLayerStats update() {
        CellularLinkLayerStats cellStats = new CellularLinkLayerStats();

        TelephonyManager tm = getDefaultDataTelephonyManager();
        if (tm == null) {
            if (DBG) Log.v(TAG, cellStats.toString());
            return cellStats;
        }

        SignalStrength signalStrength = tm.getSignalStrength();
        CellSignalStrength primaryCss = null;
        if (signalStrength != null) {
            List<CellSignalStrength> cssList = signalStrength.getCellSignalStrengths();
            if (DBG) Log.v(TAG, "Cell Signal Strength List size = " + cssList.size());
            if (!cssList.isEmpty()) primaryCss = cssList.get(0);
        }

        CellIdentity primaryCellId = getRegisteredDataCellIdentity(tm);

        if (tm.getDataNetworkType() == NETWORK_TYPE_UNKNOWN
                || primaryCss == null || primaryCellId == null) {
            mLastPrimaryCellId = null;
            mLastDataNetworkType = NETWORK_TYPE_UNKNOWN;
            if (DBG) Log.v(TAG, cellStats.toString());
            return cellStats;
        }

        updateSignalStrengthDbAndNetworkTypeOfCellStats(primaryCss, cellStats);

        int networkType = cellStats.getDataNetworkType();
        cellStats.setIsSameRegisteredCell(Objects.equals(primaryCellId, mLastPrimaryCellId));

        // Update for the next call
        mLastPrimaryCellId = primaryCellId;
        mLastDataNetworkType = networkType;

        if (DBG) Log.v(TAG, cellStats.toString());
        return cellStats;
    }

    @Nullable
    private TelephonyManager getDefaultDataTelephonyManager() {
        if (!initSubManager()) return null;

        int defaultDataSubId = mSubManager.getDefaultDataSubscriptionId();
        if (DBG) Log.v(TAG, "default Data Sub ID = " + defaultDataSubId);
        if (defaultDataSubId == mCachedDefaultDataSubId) {
            // If nothing has changed, we just return what we already had.
            return mCachedDefaultDataTelephonyManager;
        } else if (defaultDataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            // If there is no default data subscription, then reset.
            mCachedDefaultDataTelephonyManager = null;
            mCachedDefaultDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            return null;
        }

        // TODO(b/132188983): Inject this using WifiInjector
        // Create a TelephonyManager using a specific subId rather than using the
        // default, which can change at any moment.
        mCachedDefaultDataSubId = defaultDataSubId;
        mCachedDefaultDataTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                        .createForSubscriptionId(mCachedDefaultDataSubId);

        return mCachedDefaultDataTelephonyManager;
    }

    private boolean initSubManager() {
        if (mSubManager == null) {
            mSubManager = (SubscriptionManager) mContext.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        }
        return (mSubManager != null);
    }

    /**
      * Update dB value and network type base on CellSignalStrength subclass type.
      * It follows the same order as that in SignalStrength.getPrimary().
      * TODO: NR may move up in the future
      */
    private static void updateSignalStrengthDbAndNetworkTypeOfCellStats(
            CellSignalStrength primaryCss, CellularLinkLayerStats cellStats) {

        cellStats.setSignalStrengthDbm(primaryCss.getDbm());

        if (primaryCss instanceof CellSignalStrengthLte) {
            CellSignalStrengthLte cssLte = (CellSignalStrengthLte) primaryCss;
            cellStats.setSignalStrengthDb(cssLte.getRsrq());
            cellStats.setDataNetworkType(NETWORK_TYPE_LTE);
        } else if (primaryCss instanceof CellSignalStrengthCdma) {
            CellSignalStrengthCdma cssCdma = (CellSignalStrengthCdma) primaryCss;
            int evdoSnr = cssCdma.getEvdoSnr();
            int cdmaEcio = cssCdma.getCdmaEcio();
            if (evdoSnr != SignalStrength.INVALID) {
                cellStats.setSignalStrengthDb(evdoSnr);
                cellStats.setDataNetworkType(NETWORK_TYPE_EVDO_0);
            } else {
                cellStats.setSignalStrengthDb(cdmaEcio);
                cellStats.setDataNetworkType(NETWORK_TYPE_CDMA);
            }
        } else if (primaryCss instanceof CellSignalStrengthTdscdma) {
            cellStats.setDataNetworkType(NETWORK_TYPE_TD_SCDMA);
        } else if (primaryCss instanceof CellSignalStrengthWcdma) {
            CellSignalStrengthWcdma cssWcdma = (CellSignalStrengthWcdma) primaryCss;
            cellStats.setSignalStrengthDb(cssWcdma.getEcNo());
            cellStats.setDataNetworkType(NETWORK_TYPE_UMTS);
        } else if (primaryCss instanceof CellSignalStrengthGsm) {
            cellStats.setDataNetworkType(NETWORK_TYPE_GSM);
        } else if (primaryCss instanceof CellSignalStrengthNr) {
            CellSignalStrengthNr cssNr = (CellSignalStrengthNr) primaryCss;
            cellStats.setSignalStrengthDb(cssNr.getCsiSinr());
            cellStats.setDataNetworkType(NETWORK_TYPE_NR);
        } else {
            Log.e(TAG, "invalid CellSignalStrength");
        }
    }

    @Nullable
    private CellIdentity getRegisteredDataCellIdentity(
            TelephonyManager defaultDataTelephonyManager) {
        ServiceState ss = defaultDataTelephonyManager.getServiceState();
        NetworkRegistrationInfo nri = ss.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, TRANSPORT_TYPE_WWAN);
        return nri == null ? null : nri.getCellIdentity();
    }
}
