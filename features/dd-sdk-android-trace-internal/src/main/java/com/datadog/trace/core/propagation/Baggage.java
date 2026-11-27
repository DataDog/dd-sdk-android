/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core.propagation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.trace.util.PercentEscaper;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

// Source: https://github.com/DataDog/dd-trace-java/blob/master/dd-trace-core/src/main/java/datadog/trace/core/baggage/BaggagePropagator.java
public final class Baggage {
    private static final char PAIR_SEPARATOR = ',';
    private static final char KEY_VALUE_SEPARATOR = '=';
    private static final char PROPERTY_SEPARATOR = ';';
    private static final PercentEscaper UTF_ESCAPER = PercentEscaper.create();

    private final HashMap<String, String> values = new HashMap<>();

    public Baggage() {
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Baggage put(@NonNull String key, @NonNull String value) {
        values.put(key, value);
        return this;
    }

    @NonNull
    public Baggage mergeWith(@Nullable Baggage other) {
        if (other != null) {
            values.putAll(other.asMap());
        }
        return this;
    }

    @NonNull
    public String toString() {
        int processedItems = 0;
        StringBuilder baggageText = new StringBuilder();
        for (final Map.Entry<String, String> entry : asMap().entrySet()) {
            if (processedItems > 0) baggageText.append(',');

            PercentEscaper.Escaped escapedKey = UTF_ESCAPER.escapeKey(entry.getKey());
            PercentEscaper.Escaped escapedVal = UTF_ESCAPER.escapeValue(entry.getValue());

            baggageText.append(escapedKey.data);
            baggageText.append('=');
            baggageText.append(escapedVal.data);

            processedItems++;
        }

        return baggageText.toString();
    }

    @NonNull
    public Map<String, String> asMap() {
        return new HashMap<>(values);
    }

    @NonNull
    public static Baggage from(@Nullable String input) {
        if (input == null) return new Baggage();
        int start = 0;
        int pairSeparatorInd = input.indexOf(PAIR_SEPARATOR);
        pairSeparatorInd = pairSeparatorInd == -1 ? input.length() : pairSeparatorInd;
        int kvSeparatorInd = input.indexOf(KEY_VALUE_SEPARATOR);
        Baggage baggage = new Baggage();
        while (kvSeparatorInd != -1) {
            int end = pairSeparatorInd;
            if (kvSeparatorInd > end) {
                return new Baggage();
            }
            String key = urlDecode(input.substring(start, kvSeparatorInd).trim());
            String value = urlDecode(input.substring(kvSeparatorInd + 1, end).trim());
            if (key.isEmpty() || value.isEmpty()) {
                return new Baggage();
            }
            baggage.put(key, value);

            kvSeparatorInd = input.indexOf(KEY_VALUE_SEPARATOR, pairSeparatorInd + 1);
            pairSeparatorInd = input.indexOf(PAIR_SEPARATOR, pairSeparatorInd + 1);
            pairSeparatorInd = pairSeparatorInd == -1 ? input.length() : pairSeparatorInd;
            start = end + 1;
        }

        return baggage;
    }

    /**
     * Removes every list member with the given key from a raw baggage header value. The key of each
     * member is decoded like in {@link #from(String)} before comparison, while the other members are
     * kept byte-for-byte, including empty values, properties and malformed members.
     *
     * @param header the raw baggage header value.
     * @param key the decoded key to remove.
     * @return {@code header} itself when no member has the key, {@code null} when no member is left,
     * or the remaining members otherwise.
     */
    @Nullable
    public static String removeKey(@NonNull final String header, @NonNull final String key) {
        final String[] members = header.split(String.valueOf(PAIR_SEPARATOR), -1);
        final StringBuilder kept = new StringBuilder(header.length());
        boolean removed = false;
        boolean hasContent = false;
        boolean first = true;
        for (final String member : members) {
            if (key.equals(memberKey(member))) {
                removed = true;
                continue;
            }
            if (!first) {
                kept.append(PAIR_SEPARATOR);
            }
            first = false;
            kept.append(member);
            hasContent = hasContent || !member.trim().isEmpty();
        }
        if (!removed) {
            return header;
        }
        return hasContent ? kept.toString() : null;
    }

    /**
     * Merges two raw baggage header values. The members of {@code newHeader} replace the members of
     * {@code oldHeader} with the same key, while the other members of {@code oldHeader} are kept
     * byte-for-byte, so that application baggage the parser can't read is never dropped or decoded.
     *
     * @param oldHeader the existing raw baggage header value, which may be null.
     * @param newHeader the raw baggage header value to add.
     * @return the merged raw baggage header value.
     */
    @NonNull
    public static String mergeRaw(@Nullable final String oldHeader, @NonNull final String newHeader) {
        String kept = oldHeader;
        for (final String member : newHeader.split(String.valueOf(PAIR_SEPARATOR), -1)) {
            if (kept == null) {
                break;
            }
            final String key = memberKey(member);
            if (!key.isEmpty()) {
                kept = removeKey(kept, key);
            }
        }
        if (kept == null || kept.trim().isEmpty()) {
            return newHeader;
        }
        if (newHeader.trim().isEmpty()) {
            return kept;
        }
        return kept + PAIR_SEPARATOR + newHeader;
    }

    @NonNull
    private static String memberKey(@NonNull final String member) {
        String key = member;
        final int kvSeparatorInd = key.indexOf(KEY_VALUE_SEPARATOR);
        if (kvSeparatorInd != -1) {
            key = key.substring(0, kvSeparatorInd);
        }
        final int propertySeparatorInd = key.indexOf(PROPERTY_SEPARATOR);
        if (propertySeparatorInd != -1) {
            key = key.substring(0, propertySeparatorInd);
        }
        return urlDecode(key.trim());
    }

    @NonNull
    private static String urlDecode(@NonNull final String value) {
        String decoded = value;
        try {
            // Suppressing decode(String s, Charset charset) because it requires minSdk = 33.
            // noinspection CharsetObjectCanBeUsed
            decoded = URLDecoder.decode(value, "UTF-8");
        } catch (final UnsupportedEncodingException | IllegalArgumentException ignored) {
        }
        return decoded;
    }
}
