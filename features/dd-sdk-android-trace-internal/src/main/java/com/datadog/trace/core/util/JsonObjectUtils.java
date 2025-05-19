/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class JsonObjectUtils {

    @Nullable
    public static String getAsString(JsonObject jsonObject, String key) {
        final JsonElement jsonValue = jsonObject.get(key);
        if (jsonValue != null && jsonValue.isJsonPrimitive()) {
            return jsonValue.getAsJsonPrimitive().getAsString();
        }
        return null;
    }

    @Nullable
    public static Map<String, String> safeGetAsMap(JsonObject jsonObject, String key) {
        final JsonElement jsonValue = jsonObject.get(key);
        if (jsonValue != null && jsonValue.isJsonObject()) {
            return safeGetAsMap(jsonValue.getAsJsonObject());
        }
        return null;
    }

    @NonNull
    private static Map<String, String> safeGetAsMap(JsonObject jsonObject) {
        final Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            final JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                map.put(entry.getKey(), value.getAsJsonPrimitive().getAsString());
            }
        }
        return map;
    }

}
