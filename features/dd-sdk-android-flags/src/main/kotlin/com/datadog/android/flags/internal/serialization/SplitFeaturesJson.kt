/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.model.SplitFeature
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** Reads and writes the `features` of a split, in the precomputed response format. */
internal object SplitFeaturesJson {
    /**
     * Features never decide a variation, so a malformed list or entry is dropped instead of failing the
     * assignment. An entry needs a non-empty key, a string, number or boolean value and at least one
     * non-empty destination. On a repeated key the first valid entry wins.
     */
    fun read(json: JsonElement?): List<SplitFeature> {
        if (json !is JsonArray || json.size() == 0) return emptyList()
        val keys = mutableSetOf<String>()
        return json.map { (it as? JsonObject)?.let(::readFeature) }
            .mapNotNull { feature -> feature?.takeIf { keys.add(it.key) } }
    }

    private fun readFeature(json: JsonObject): SplitFeature? {
        val stringOrNull = { element: JsonElement ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        }
        val key = json.get(FEATURE_KEY)?.let(stringOrNull)
        val value = (json.get(FEATURE_VALUE) as? JsonPrimitive)?.let {
            when {
                it.isString -> it.asString
                it.isBoolean -> it.asBoolean
                it.isNumber -> it.asDouble
                else -> null
            }
        }
        val destinations = (json.get(FEATURE_DESTINATIONS) as? JsonArray)
            ?.map(stringOrNull)
            ?.mapNotNull { destination -> destination?.takeIf { it.isNotEmpty() } }
            .orEmpty()
        return if (key.isNullOrEmpty() || value == null || destinations.isEmpty()) {
            null
        } else {
            SplitFeature(key, value, destinations)
        }
    }

    fun write(features: List<SplitFeature>): JsonArray = JsonArray().apply {
        features.forEach { feature ->
            add(
                JsonObject().apply {
                    addProperty(FEATURE_KEY, feature.key)
                    when (val value = feature.value) {
                        is String -> addProperty(FEATURE_VALUE, value)
                        is Boolean -> addProperty(FEATURE_VALUE, value)
                        is Number -> addProperty(FEATURE_VALUE, value)
                    }
                    add(FEATURE_DESTINATIONS, JsonArray().apply { feature.destinations.forEach { add(it) } })
                }
            )
        }
    }

    private const val FEATURE_KEY = "key"
    private const val FEATURE_VALUE = "value"
    private const val FEATURE_DESTINATIONS = "destinations"
}
