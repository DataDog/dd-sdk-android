/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.model.Assignment
import com.datadog.android.flags.model.FlagSnapshotFeature.Value
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** Prepares the `features` of a split for the generated assignment model. */
internal object SplitFeaturesJson {
    /**
     * Features never decide a variation, so a malformed list or entry is dropped instead of failing the
     * assignment. An entry needs a non-empty key, a string, number or boolean value and at least one
     * non-empty destination. On a repeated key the first valid entry wins. The generated readers coerce
     * numbers to strings, so these checks run on the raw JSON. Returns null when no entry is valid.
     */
    fun normalize(json: JsonElement?): JsonArray? {
        if (json !is JsonArray) return null
        val keys = mutableSetOf<String>()
        val features = json.map { (it as? JsonObject)?.let(::normalizeFeature) }
            .mapNotNull { feature -> if (feature != null && keys.add(feature.first)) feature.second else null }
        return if (features.isEmpty()) null else JsonArray().apply { features.forEach { add(it) } }
    }

    /** Returns the [String], [Double] or [Boolean] that a split feature holds. */
    fun value(value: Assignment.Value): Value = when (value) {
        is Assignment.Value.String -> Value.Text(value.item)
        is Assignment.Value.Number -> Value.Numeric(value.item.toDouble())
        is Assignment.Value.Boolean -> Value.Bool(value.item)
    }

    /** Returns the generated value for a split feature [value], a [String], [Double] or [Boolean]. */
    fun modelValue(value: Value): Assignment.Value = when (value) {
        is Value.Bool -> Assignment.Value.Boolean(value.boolean)
        is Value.Numeric -> Assignment.Value.Number(value.number)
        is Value.Text -> Assignment.Value.String(value.text)
    }

    private fun normalizeFeature(json: JsonObject): Pair<String, JsonObject>? {
        val stringOrNull = { element: JsonElement ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        }
        val key = json.get(FEATURE_KEY)?.let(stringOrNull)
        val value = (json.get(FEATURE_VALUE) as? JsonPrimitive)?.let {
            when {
                it.isString || it.isBoolean -> it
                it.isNumber -> if (it.asDouble.isFinite()) it else null
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
            key to JsonObject().apply {
                addProperty(FEATURE_KEY, key)
                add(FEATURE_VALUE, value)
                add(FEATURE_DESTINATIONS, JsonArray().apply { destinations.forEach { add(it) } })
            }
        }
    }

    private const val FEATURE_KEY = "key"
    private const val FEATURE_VALUE = "value"
    private const val FEATURE_DESTINATIONS = "destinations"
}
