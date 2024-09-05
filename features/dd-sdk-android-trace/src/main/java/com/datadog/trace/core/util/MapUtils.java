/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core.util;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;
import com.datadog.android.trace.internal.compat.function.Function;

public class MapUtils {

    public static <V, K> V computeIfAbsent(K key, Map<K, V> originalMap,
                                           Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v;
        if ((v = originalMap.get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                originalMap.put(key, newValue);
                return newValue;
            }
        }

        return v;
    }

    public static JsonObject getAsJsonObject(Map<String, String> map) {
        final JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            jsonObject.addProperty(entry.getKey(), entry.getValue());
        }
        return jsonObject;
    }

}
