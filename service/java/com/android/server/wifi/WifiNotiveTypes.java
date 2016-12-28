/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.os.SystemClock;

import com.android.internal.annotations.Immutable;
import com.android.internal.util.HexDump;
import com.android.server.wifi.util.FrameParser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Types formerly declared in WifiNative
 */
public class WifiNotiveTypes {

    // TODO: Change variable names to camel style.
    public static class ScanCapabilities {
        public int  max_scan_cache_size;
        public int  max_scan_buckets;
        public int  max_ap_cache_per_scan;
        public int  max_rssi_sample_size;
        public int  max_scan_reporting_threshold;
        public int  max_hotlist_bssids;
        public int  max_significant_wifi_change_aps;
        public int  max_bssid_history_entries;
        public int  max_number_epno_networks;
        public int  max_number_epno_networks_by_ssid;
        public int  max_number_of_white_listed_ssid;
    }

    public static class ChannelSettings {
        public int frequency;
        public int dwell_time_ms;
        public boolean passive;
    }

    public static class BucketSettings {
        public int bucket;
        public int band;
        public int period_ms;
        public int max_period_ms;
        public int step_count;
        public int report_events;
        public int num_channels;
        public ChannelSettings[] channels;
    }

    /**
     * Network parameters for hidden networks to be scanned for.
     */
    public static class HiddenNetwork {
        public String ssid;

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            } else if (otherObj == null || getClass() != otherObj.getClass()) {
                return false;
            }
            HiddenNetwork other = (HiddenNetwork) otherObj;
            return Objects.equals(ssid, other.ssid);
        }
    }

    public static class ScanSettings {
        public int base_period_ms;
        public int max_ap_per_scan;
        public int report_threshold_percent;
        public int report_threshold_num_scans;
        public int num_buckets;
        /* Not used for bg scans. Only works for single scans. */
        public HiddenNetwork[] hiddenNetworks;
        public BucketSettings[] buckets;
    }

    /**
     * Network parameters to start PNO scan.
     */
    public static class PnoNetwork {
        public String ssid;
        public byte flags;
        public byte auth_bit_field;

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            } else if (otherObj == null || getClass() != otherObj.getClass()) {
                return false;
            }
            PnoNetwork other = (PnoNetwork) otherObj;
            return ((Objects.equals(ssid, other.ssid)) && (flags == other.flags)
                    && (auth_bit_field == other.auth_bit_field));
        }
    }

    /**
     * Parameters to start PNO scan. This holds the list of networks which are going to used for
     * PNO scan.
     */
    public static class PnoSettings {
        public int min5GHzRssi;
        public int min24GHzRssi;
        public int initialScoreMax;
        public int currentConnectionBonus;
        public int sameNetworkBonus;
        public int secureBonus;
        public int band5GHzBonus;
        public boolean isConnected;
        public PnoNetwork[] networkList;
    }

    /**
     * Wi-Fi channel information.
     */
    public static class WifiChannelInfo {
        int mPrimaryFrequency;
        int mCenterFrequency0;
        int mCenterFrequency1;
        int mChannelWidth;
        // TODO: add preamble once available in HAL.
    }

    public static interface ScanEventHandler {
        /**
         * Called for each AP as it is found with the entire contents of the beacon/probe response.
         * Only called when WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT is specified.
         */
        void onFullScanResult(ScanResult fullScanResult, int bucketsScanned);
        /**
         * Callback on an event during a gscan scan.
         * See WifiNotive.WIFI_SCAN_* for possible values.
         */
        void onScanStatus(int event);
        /**
         * Called with the current cached scan results when gscan is paused.
         */
        void onScanPaused(WifiScanner.ScanData[] data);
        /**
         * Called with the current cached scan results when gscan is resumed.
         */
        void onScanRestarted();
    }

    /**
     * Handler to notify the occurrence of various events during PNO scan.
     */
    public interface PnoEventHandler {
        /**
         * Callback to notify when one of the shortlisted networks is found during PNO scan.
         * @param results List of Scan results received.
         */
        void onPnoNetworkFound(ScanResult[] results);

        /**
         * Callback to notify when the PNO scan schedule fails.
         */
        void onPnoScanFailed();
    }

    public static interface HotlistEventHandler {
        void onHotlistApFound (ScanResult[] result);
        void onHotlistApLost  (ScanResult[] result);
    }

    public static interface SignificantWifiChangeEventHandler {
        void onChangesFound(ScanResult[] result);
    }

    /* Rtt related commands/events */
    public abstract class TdlsEventHandler {
        abstract public void onTdlsStatus(String macAddr, int status, int reason);
    }

    // Once TDLS per mac and event feature is implemented, this class definition should be
    // moved to the right place, like WifiManager etc
    public static class TdlsStatus {
        int channel;
        int global_operating_class;
        int state;
        int reason;
    }

    //ToFix: Once TDLS per mac and event feature is implemented, this class definition should be
    // moved to the right place, like WifiStateMachine etc
    public static class TdlsCapabilities {
        /* Maximum TDLS session number can be supported by the Firmware and hardware */
        int maxConcurrentTdlsSessionNumber;
        boolean isGlobalTdlsSupported;
        boolean isPerMacTdlsSupported;
        boolean isOffChannelTdlsSupported;
    }

    public static class RingBufferStatus{
        String name;
        int flag;
        int ringBufferId;
        int ringBufferByteSize;
        int verboseLevel;
        int writtenBytes;
        int readBytes;
        int writtenRecords;

        @Override
        public String toString() {
            return "name: " + name + " flag: " + flag + " ringBufferId: " + ringBufferId +
                    " ringBufferByteSize: " +ringBufferByteSize + " verboseLevel: " +verboseLevel +
                    " writtenBytes: " + writtenBytes + " readBytes: " + readBytes +
                    " writtenRecords: " + writtenRecords;
        }
    }

    public static interface WifiLoggerEventHandler {
        void onRingBufferData(RingBufferStatus status, byte[] buffer);
        void onWifiAlert(int errorCode, byte[] buffer);
    }

    @Immutable
    abstract static class FateReport {
        final static int USEC_PER_MSEC = 1000;
        // The driver timestamp is a 32-bit counter, in microseconds. This field holds the
        // maximal value of a driver timestamp in milliseconds.
        final static int MAX_DRIVER_TIMESTAMP_MSEC = (int) (0xffffffffL / 1000);
        final static SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

        final byte mFate;
        final long mDriverTimestampUSec;
        final byte mFrameType;
        final byte[] mFrameBytes;
        final long mEstimatedWallclockMSec;

        FateReport(byte fate, long driverTimestampUSec, byte frameType, byte[] frameBytes) {
            mFate = fate;
            mDriverTimestampUSec = driverTimestampUSec;
            mEstimatedWallclockMSec =
                    convertDriverTimestampUSecToWallclockMSec(mDriverTimestampUSec);
            mFrameType = frameType;
            mFrameBytes = frameBytes;
        }

        public String toTableRowString() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            FrameParser parser = new FrameParser(mFrameType, mFrameBytes);
            dateFormatter.setTimeZone(TimeZone.getDefault());
            pw.format("%-15s  %12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    mDriverTimestampUSec,
                    dateFormatter.format(new Date(mEstimatedWallclockMSec)),
                    directionToString(), fateToString(), parser.mMostSpecificProtocolString,
                    parser.mTypeString, parser.mResultString);
            return sw.toString();
        }

        public String toVerboseStringWithPiiAllowed() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            FrameParser parser = new FrameParser(mFrameType, mFrameBytes);
            pw.format("Frame direction: %s\n", directionToString());
            pw.format("Frame timestamp: %d\n", mDriverTimestampUSec);
            pw.format("Frame fate: %s\n", fateToString());
            pw.format("Frame type: %s\n", frameTypeToString(mFrameType));
            pw.format("Frame protocol: %s\n", parser.mMostSpecificProtocolString);
            pw.format("Frame protocol type: %s\n", parser.mTypeString);
            pw.format("Frame length: %d\n", mFrameBytes.length);
            pw.append("Frame bytes");
            pw.append(HexDump.dumpHexString(mFrameBytes));  // potentially contains PII
            pw.append("\n");
            return sw.toString();
        }

        /* Returns a header to match the output of toTableRowString(). */
        public static String getTableHeader() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.format("\n%-15s  %-12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    "Time usec", "Walltime", "Direction", "Fate", "Protocol", "Type", "Result");
            pw.format("%-15s  %-12s  %-9s  %-32s  %-12s  %-23s  %s\n",
                    "---------", "--------", "---------", "----", "--------", "----", "------");
            return sw.toString();
        }

        protected abstract String directionToString();

        protected abstract String fateToString();

        private static String frameTypeToString(byte frameType) {
            switch (frameType) {
                case WifiLoggerHal.FRAME_TYPE_UNKNOWN:
                    return "unknown";
                case WifiLoggerHal.FRAME_TYPE_ETHERNET_II:
                    return "data";
                case WifiLoggerHal.FRAME_TYPE_80211_MGMT:
                    return "802.11 management";
                default:
                    return Byte.toString(frameType);
            }
        }

        /**
         * Converts a driver timestamp to a wallclock time, based on the current
         * BOOTTIME to wallclock mapping. The driver timestamp is a 32-bit counter of
         * microseconds, with the same base as BOOTTIME.
         */
        private static long convertDriverTimestampUSecToWallclockMSec(long driverTimestampUSec) {
            final long wallclockMillisNow = System.currentTimeMillis();
            final long boottimeMillisNow = SystemClock.elapsedRealtime();
            final long driverTimestampMillis = driverTimestampUSec / USEC_PER_MSEC;

            long boottimeTimestampMillis = boottimeMillisNow % MAX_DRIVER_TIMESTAMP_MSEC;
            if (boottimeTimestampMillis < driverTimestampMillis) {
                // The 32-bit microsecond count has wrapped between the time that the driver
                // recorded the packet, and the call to this function. Adjust the BOOTTIME
                // timestamp, to compensate.
                //
                // Note that overflow is not a concern here, since the result is less than
                // 2 * MAX_DRIVER_TIMESTAMP_MSEC. (Given the modulus operation above,
                // boottimeTimestampMillis must be less than MAX_DRIVER_TIMESTAMP_MSEC.) And, since
                // MAX_DRIVER_TIMESTAMP_MSEC is an int, 2 * MAX_DRIVER_TIMESTAMP_MSEC must fit
                // within a long.
                boottimeTimestampMillis += MAX_DRIVER_TIMESTAMP_MSEC;
            }

            final long millisSincePacketTimestamp = boottimeTimestampMillis - driverTimestampMillis;
            return wallclockMillisNow - millisSincePacketTimestamp;
        }
    }

    /**
     * Represents the fate information for one outbound packet.
     */
    @Immutable
    public static final class TxFateReport extends FateReport {
        TxFateReport(byte fate, long driverTimestampUSec, byte frameType, byte[] frameBytes) {
            super(fate, driverTimestampUSec, frameType, frameBytes);
        }

        @Override
        protected String directionToString() {
            return "TX";
        }

        @Override
        protected String fateToString() {
            switch (mFate) {
                case WifiLoggerHal.TX_PKT_FATE_ACKED:
                    return "acked";
                case WifiLoggerHal.TX_PKT_FATE_SENT:
                    return "sent";
                case WifiLoggerHal.TX_PKT_FATE_FW_QUEUED:
                    return "firmware queued";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_INVALID:
                    return "firmware dropped (invalid frame)";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_NOBUFS:
                    return "firmware dropped (no bufs)";
                case WifiLoggerHal.TX_PKT_FATE_FW_DROP_OTHER:
                    return "firmware dropped (other)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED:
                    return "driver queued";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_INVALID:
                    return "driver dropped (invalid frame)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_NOBUFS:
                    return "driver dropped (no bufs)";
                case WifiLoggerHal.TX_PKT_FATE_DRV_DROP_OTHER:
                    return "driver dropped (other)";
                default:
                    return Byte.toString(mFate);
            }
        }
    }

    /**
     * Represents the fate information for one inbound packet.
     */
    @Immutable
    public static final class RxFateReport extends FateReport {
        RxFateReport(byte fate, long driverTimestampUSec, byte frameType, byte[] frameBytes) {
            super(fate, driverTimestampUSec, frameType, frameBytes);
        }

        @Override
        protected String directionToString() {
            return "RX";
        }

        @Override
        protected String fateToString() {
            switch (mFate) {
                case WifiLoggerHal.RX_PKT_FATE_SUCCESS:
                    return "success";
                case WifiLoggerHal.RX_PKT_FATE_FW_QUEUED:
                    return "firmware queued";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER:
                    return "firmware dropped (filter)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_INVALID:
                    return "firmware dropped (invalid frame)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_NOBUFS:
                    return "firmware dropped (no bufs)";
                case WifiLoggerHal.RX_PKT_FATE_FW_DROP_OTHER:
                    return "firmware dropped (other)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_QUEUED:
                    return "driver queued";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_FILTER:
                    return "driver dropped (filter)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_INVALID:
                    return "driver dropped (invalid frame)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_NOBUFS:
                    return "driver dropped (no bufs)";
                case WifiLoggerHal.RX_PKT_FATE_DRV_DROP_OTHER:
                    return "driver dropped (other)";
                default:
                    return Byte.toString(mFate);
            }
        }
    }
}
