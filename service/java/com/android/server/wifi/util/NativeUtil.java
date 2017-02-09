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

package com.android.server.wifi.util;

import libcore.util.HexEncoding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Provide utility functions for native interfacing modules.
 */
public class NativeUtil {
    private static final String ANY_MAC_STR = "any";
    private static final byte[] ANY_MAC_BYTES = {0, 0, 0, 0, 0, 0};
    private static final int MAC_LENGTH = 6;

    /**
     * Convert the string to byte array list.
     *
     * @return the UTF_8 char byte values of str, as an ArrayList.
     * @throws IllegalArgumentException if a null string is sent.
     */
    public static ArrayList<Byte> stringToByteArrayList(String str) {
        if (str == null) {
            throw new IllegalArgumentException("null string");
        }
        ArrayList<Byte> byteArrayList = new ArrayList<Byte>();
        for (byte b : str.getBytes(StandardCharsets.UTF_8)) {
            byteArrayList.add(new Byte(b));
        }
        return byteArrayList;
    }

    /**
     * Convert the byte array list to string.
     *
     * @return the string decoded from UTF_8 byte values in byteArrayList.
     * @throws IllegalArgumentException if a null byte array list is sent.
     */
    public static String stringFromByteArrayList(ArrayList<Byte> byteArrayList) {
        if (byteArrayList == null) {
            throw new IllegalArgumentException("null byte array list");
        }
        byte[] byteArray = new byte[byteArrayList.size()];
        int i = 0;
        for (Byte b : byteArrayList) {
            byteArray[i] = b;
            i++;
        }
        return new String(byteArray, StandardCharsets.UTF_8);
    }

    /**
     * Convert the string to byte array.
     *
     * @return the UTF_8 char byte values of str, as an Array.
     * @throws IllegalArgumentException if a null string is sent.
     */
    public static byte[] stringToByteArray(String str) {
        if (str == null) {
            throw new IllegalArgumentException("null string");
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convert the byte array list to string.
     *
     * @return the string decoded from UTF_8 byte values in byteArray.
     * @throws IllegalArgumentException if a null byte array is sent.
     */
    public static String stringFromByteArray(byte[] byteArray) {
        if (byteArray == null) {
            throw new IllegalArgumentException("null byte array");
        }
        return new String(byteArray);
    }

    /**
     * Converts a mac address string to an array of Byes
     *
     * @param macStr string of format: "XX:XX:XX:XX:XX:XX", where X is any hexadecimal digit.
     *        Passing "any" is the same as 00:00:00:00:00:00
     * @throws IllegalArgumentException for various malformed inputs.
     */
    public static byte[] macAddressToByteArray(String macStr) {
        if (macStr == null) {
            throw new IllegalArgumentException("null mac string");
        }
        if (ANY_MAC_STR.equals(macStr)) return ANY_MAC_BYTES;
        String cleanMac = macStr.replace(":", "");
        if (cleanMac.length() != MAC_LENGTH * 2) {
            throw new IllegalArgumentException("invalid mac string length: " + cleanMac);
        }
        return HexEncoding.decode(cleanMac.toCharArray(), false);
    }

    /**
     * Converts an array of 6 bytes to a HexEncoded String with format: "XX:XX:XX:XX:XX:XX", where X
     * is any hexadecimal digit.
     *
     * @param macArray byte array of mac values, must have length 6
     * @throws IllegalArgumentException for malformed inputs.
     */
    public static String macAddressFromByteArray(byte[] macArray) {
        if (macArray == null) {
            throw new IllegalArgumentException("null mac bytes");
        }
        if (macArray.length != MAC_LENGTH) {
            throw new IllegalArgumentException("invalid macArray length: " + macArray.length);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < macArray.length; i++) {
            if (i != 0) sb.append(":");
            sb.append(new String(HexEncoding.encode(macArray, i, 1)));
        }
        return sb.toString();
    }

    /**
     * Converts an ssid string to an arraylist of UTF_8 byte values.
     * Removes encapsulating quotation marks if they exist
     *
     * @param ssid String to be converted.
     * @throws IllegalArgumentException for null string.
     */
    public static ArrayList<Byte> decodeSsid(String ssid) {
        if (ssid == null) {
            throw new IllegalArgumentException("null ssid string");
        }
        int length = ssid.length();
        if ((length > 1) && (ssid.charAt(0) == '"') && (ssid.charAt(length - 1) == '"')) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return stringToByteArrayList(ssid);
    }

    /**
     * Converts an ArrayList<Byte> of UTF_8 byte values to an SSID String, encapsulated in quotes.
     *
     * @param ssidBytes List of bytes for ssid.
     * @throws IllegalArgumentException for null bytes.
     */
    public static String encodeSsid(ArrayList<Byte> ssidBytes) {
        if (ssidBytes == null) {
            throw new IllegalArgumentException("null ssid bytes");
        }
        if (ssidBytes.size() == 0) return "\"\"";
        byte[] ssidArray = new byte[ssidBytes.size()];
        int i = 0;
        for (Byte b : ssidBytes) {
            ssidArray[i] = b;
            i++;
        }
        return "\"" + (new String(ssidArray, StandardCharsets.UTF_8)) + "\"";
    }

    /**
     * Converts a hex string to byte array.
     *
     * @param hexStr String to be converted.
     * @throws IllegalArgumentException for null string.
     */
    public static byte[] hexStringToByteArray(String hexStr) {
        if (hexStr == null) {
            throw new IllegalArgumentException("null hex string");
        }
        return HexEncoding.decode(hexStr.toCharArray(), false);
    }

    /**
     * Converts a byte array to hex string.
     *
     * @param bytes List of bytes for ssid.
     * @throws IllegalArgumentException for null bytes.
     */
    public static String hexStringFromByteArray(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("null hex bytes");
        }
        return new String(HexEncoding.encode(bytes));
    }
}
