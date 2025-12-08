/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionDetails
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import org.json.JSONException
import org.json.JSONObject
import dev.openfeature.kotlin.sdk.EvaluationContext as OpenFeatureEvaluationContext
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode as OpenFeatureErrorCode

/**
 * Converts an OpenFeature [EvaluationContext] to a Datadog [EvaluationContext].
 */
internal fun OpenFeatureEvaluationContext.toDatadogEvaluationContext(): EvaluationContext {
    val targetingKey = this.getTargetingKey()

    val stringAttributes = this.asMap()
        .mapValues { (_, value) ->
            value.toString()
        }

    return EvaluationContext(
        targetingKey = targetingKey,
        attributes = stringAttributes
    )
}

/**
 * Converts a Datadog [ResolutionDetails] to an OpenFeature [ProviderEvaluation].
 */
internal fun <T : Any> ResolutionDetails<T>.toProviderEvaluation(): ProviderEvaluation<T> = ProviderEvaluation(
    value = this.value,
    variant = this.variant,
    reason = this.reason?.name,
    errorCode = this.errorCode?.toOpenFeatureErrorCode(),
    errorMessage = this.errorMessage
)

/**
 * Converts a Datadog [ErrorCode] to an OpenFeature [ErrorCode].
 */
internal fun ErrorCode.toOpenFeatureErrorCode(): OpenFeatureErrorCode = when (this) {
    ErrorCode.PROVIDER_NOT_READY ->
        OpenFeatureErrorCode.PROVIDER_NOT_READY
    ErrorCode.FLAG_NOT_FOUND ->
        OpenFeatureErrorCode.FLAG_NOT_FOUND
    ErrorCode.PARSE_ERROR ->
        OpenFeatureErrorCode.PARSE_ERROR
    ErrorCode.TYPE_MISMATCH ->
        OpenFeatureErrorCode.TYPE_MISMATCH
}

/**
 * Converts a [JSONObject] to a [Map] of String to Any.
 *
 * @param internalLogger Logger for diagnostic messages (optional)
 * @return A map containing the JSONObject's key-value pairs, or an empty map if an error occurs
 */
@Suppress("UnsafeThirdPartyFunctionCall")
internal fun JSONObject.toMap(internalLogger: InternalLogger? = null): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keys = this.keys()

    while (keys.hasNext()) {
        val key = keys.next()
        addEntryToMap(key, map, internalLogger)
    }

    return map
}

/**
 * Adds a single JSONObject entry to the map, handling errors gracefully.
 */
@Suppress("UnsafeThirdPartyFunctionCall")
private fun JSONObject.addEntryToMap(key: String, map: MutableMap<String, Any>, internalLogger: InternalLogger?) {
    try {
        // key is guaranteed to exist as we're iterating over this.keys()
        val value = this.get(key)
        // Skip null/JSONObject.NULL values
        if (value != JSONObject.NULL) {
            map[key] = value
        }
    } catch (e: JSONException) {
        // Skip individual entries that fail to convert
        internalLogger?.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { "Failed to convert JSONObject entry to map" },
            e
        )
    }
}
