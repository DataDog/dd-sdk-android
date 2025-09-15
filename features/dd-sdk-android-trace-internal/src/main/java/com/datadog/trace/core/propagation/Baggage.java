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
    private static final PercentEscaper UTF_ESCAPER = PercentEscaper.create();

    private final HashMap<String, String> values;

    public Baggage() {
        this.values = new HashMap<>();
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
            String key = decode(input.substring(start, kvSeparatorInd).trim());
            String value = decode(input.substring(kvSeparatorInd + 1, end).trim());
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

    private static String decode(final String value) {
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
