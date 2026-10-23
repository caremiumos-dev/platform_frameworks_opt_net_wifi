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

package com.android.server.wifi.hotspot2;

import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the legacy per-provider-subscription configuration file (OMA-DM
 * management tree format).
 *
 * @hide
 */
public class LegacyPasspointConfigParser {
    private static final String TAG = "LegacyPasspointConfigParser";

    private static final String TAG_MANAGEMENT_TREE = "MgmtTree";
    private static final String TAG_PER_PROVIDER_SUBSCRIPTION = "PerProviderSubscription";
    private static final String TAG_HOMESP = "HomeSP";
    private static final String TAG_CREDENTIAL = "Credential";
    private static final String TAG_SIM = "SIM";
    private static final String TAG_FQDN = "FQDN";
    private static final String TAG_FRIENDLY_NAME = "FriendlyName";
    private static final String TAG_ROAMING_CONSORTIUM_OI = "RoamingConsortiumOI";
    private static final String TAG_REALM = "Realm";
    private static final String TAG_IMSI = "IMSI";

    private static final char START_OF_INTERNAL_NODE_INDICATOR = '+';
    private static final char STRING_PREFIX_INDICATOR = ':';
    private static final char STRING_VALUE_INDICATOR = '=';
    private static final String END_OF_INTERNAL_NODE_INDICATOR = ".";
    private static final String LONG_ARRAY_SEPARATOR = ",";

    private abstract static class Node {
        private final String mName;

        Node(String name) {
            mName = name;
        }

        public abstract List<Node> getChildren();

        public String getName() {
            return mName;
        }

        public abstract String getValue();
    }

    private static class InternalNode extends Node {
        private final List<Node> mChildren;

        InternalNode(String name, List<Node> children) {
            super(name);
            mChildren = children;
        }

        @Override
        public List<Node> getChildren() {
            return mChildren;
        }

        @Override
        public String getValue() {
            return null;
        }
    }

    private static class LeafNode extends Node {
        private final String mValue;

        LeafNode(String name, String value) {
            super(name);
            mValue = value;
        }

        @Override
        public List<Node> getChildren() {
            return null;
        }

        @Override
        public String getValue() {
            return mValue;
        }
    }

    private static Node buildNode(BufferedReader in) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) {
                return null;
            }
        } while (line.isEmpty());
        String trimmed = line.trim();
        if (TextUtils.equals(END_OF_INTERNAL_NODE_INDICATOR, trimmed)) {
            return null;
        }
        Pair<String, String> parsed = parseLine(trimmed.getBytes(StandardCharsets.UTF_8));
        if (parsed.second != null) {
            return new LeafNode(parsed.first, parsed.second);
        }
        List<Node> children = new ArrayList<>();
        while (true) {
            Node child = buildNode(in);
            if (child == null) {
                return new InternalNode(parsed.first, children);
            }
            children.add(child);
        }
    }

    private static String getValue(Node node) throws IOException {
        if (node.getValue() == null) {
            throw new IOException("Attempt to retreive value from non-leaf node: " + node.getName());
        }
        return node.getValue();
    }

    private static Pair<String, String> parseLine(byte[] line) throws IOException {
        Pair<String, Integer> name = parseString(line, 0);
        int index = name.second;
        try {
            if (line[index] == START_OF_INTERNAL_NODE_INDICATOR) {
                return Pair.create(name.first, null);
            }
            if (line[index] != STRING_VALUE_INDICATOR) {
                throw new IOException("Invalid line - missing both node and value indicator: "
                        + new String(line, StandardCharsets.UTF_8));
            }
            return Pair.create(name.first, parseString(line, index + 1).first);
        } catch (IndexOutOfBoundsException e) {
            throw new IOException("Invalid line - " + e.getMessage() + ": "
                    + new String(line, StandardCharsets.UTF_8));
        }
    }

    private static long[] parseLongArray(String value) throws IOException {
        String[] values = value.split(LONG_ARRAY_SEPARATOR);
        long[] result = new long[values.length];
        for (int i = 0; i < result.length; i++) {
            try {
                result[i] = Long.parseLong(values[i], 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid long integer value: " + values[i]);
            }
        }
        return result;
    }

    private static Pair<String, Integer> parseString(byte[] line, int start) throws IOException {
        int end = -1;
        for (int i = start; i < line.length; i++) {
            if (line[i] == STRING_PREFIX_INDICATOR) {
                end = i;
                break;
            }
        }
        if (end == -1) {
            throw new IOException("Invalid line - missing string prefix: "
                    + new String(line, StandardCharsets.UTF_8));
        }
        try {
            int length = Integer.parseInt(
                    new String(line, start, end - start, StandardCharsets.UTF_8), 16);
            int valueStart = end + 1;
            if (valueStart + length > line.length) {
                length = line.length - valueStart;
            }
            return Pair.create(
                    new String(line, valueStart, length, StandardCharsets.UTF_8),
                    Integer.valueOf(length + valueStart));
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new IOException("Invalid line - " + e.getMessage() + ": "
                    + new String(line, StandardCharsets.UTF_8));
        }
    }

    private static void processCredentialNode(Node node, LegacyPasspointConfig config)
            throws IOException {
        if (node.getChildren() == null) {
            throw new IOException("Credential node should contain at least one child node");
        }
        for (Node child : node.getChildren()) {
            String name = child.getName();
            if (name.equals(TAG_REALM)) {
                config.mRealm = getValue(child);
            } else if (name.equals(TAG_SIM)) {
                processSimNode(child, config);
            } else {
                Log.d(TAG, "Ignore uninterested field under Credential: " + child.getName());
            }
        }
    }

    private static void processHomeSPNode(Node node, LegacyPasspointConfig config)
            throws IOException {
        if (node.getChildren() == null) {
            throw new IOException("HomeSP node should contain at least one child node");
        }
        for (Node child : node.getChildren()) {
            String name = child.getName();
            if (name.equals(TAG_FQDN)) {
                config.mFqdn = getValue(child);
            } else if (name.equals(TAG_FRIENDLY_NAME)) {
                config.mFriendlyName = getValue(child);
            } else if (name.equals(TAG_ROAMING_CONSORTIUM_OI)) {
                config.mRoamingConsortiumOis = parseLongArray(getValue(child));
            } else {
                Log.d(TAG, "Ignore uninterested field under HomeSP: " + child.getName());
            }
        }
    }

    private static LegacyPasspointConfig processPpsNode(Node node) throws IOException {
        if (node.getChildren() == null || node.getChildren().size() != 1) {
            throw new IOException("PerProviderSubscription node should contain one instance node");
        }
        if (!TextUtils.equals(TAG_PER_PROVIDER_SUBSCRIPTION, node.getName())) {
            throw new IOException("Unexpected name for PPS node: " + node.getName());
        }
        Node instance = node.getChildren().get(0);
        if (instance.getChildren() == null) {
            throw new IOException("PPS instance node doesn't contained any children");
        }
        LegacyPasspointConfig config = new LegacyPasspointConfig();
        for (Node child : instance.getChildren()) {
            String name = child.getName();
            if (name.equals(TAG_HOMESP)) {
                processHomeSPNode(child, config);
            } else if (name.equals(TAG_CREDENTIAL)) {
                processCredentialNode(child, config);
            } else {
                Log.d(TAG, "Ignore uninterested field under PPS instance: " + child.getName());
            }
        }
        if (config.mFqdn == null) {
            throw new IOException("PPS instance missing FQDN");
        }
        return config;
    }

    private static void processSimNode(Node node, LegacyPasspointConfig config)
            throws IOException {
        if (node.getChildren() == null) {
            throw new IOException("SIM node should contain at least one child node");
        }
        for (Node child : node.getChildren()) {
            if (child.getName().equals(TAG_IMSI)) {
                config.mImsi = getValue(child);
            } else {
                Log.d(TAG, "Ignore uninterested field under SIM: " + child.getName());
            }
        }
    }

    public Map<String, LegacyPasspointConfig> parseConfig(String filePath) throws IOException {
        Map<String, LegacyPasspointConfig> configs = new HashMap<>();
        BufferedReader in = new BufferedReader(new FileReader(filePath));
        in.readLine();
        Node root = buildNode(in);
        if (root == null || root.getChildren() == null) {
            Log.d(TAG, "Empty configuration data");
            return configs;
        }
        if (!TextUtils.equals(TAG_MANAGEMENT_TREE, root.getName())) {
            throw new IOException("Unexpected root node: " + root.getName());
        }
        for (Node node : root.getChildren()) {
            LegacyPasspointConfig config = processPpsNode(node);
            configs.put(config.mFqdn, config);
        }
        return configs;
    }
}
