/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionDetails
import dev.openfeature.kotlin.sdk.Builder
import dev.openfeature.kotlin.sdk.EvaluationMetadata
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.EvaluationContext as OpenFeatureEvaluationContext
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode as OpenFeatureErrorCode

/**
 * Converts an OpenFeature [EvaluationContext] to a Datadog [EvaluationContext].
 */
internal fun OpenFeatureEvaluationContext.toDatadogEvaluationContext(
    experimentationOptOutEnabled: Boolean = false
): EvaluationContext {
    val targetingKey = this.getTargetingKey()

    val stringAttributes = this.asMap()
        .mapNotNull { (key, value) ->
            if (key == ASSIGNMENT_TOKEN_ATTRIBUTE && value == Value.Null) {
                null
            } else if (key == ASSIGNMENT_TOKEN_ATTRIBUTE && value !is Value.String) {
                // Native validation removes this reserved field and emits the same safe warning as native callers.
                key to INVALID_ASSIGNMENT_TOKEN_SENTINEL
            } else if (
                experimentationOptOutEnabled &&
                key == "dd_experimentation_opt_out_at" &&
                value !is Value.String
            ) {
                // Preserve invalid typed input as an invalid timestamp for native validation.
                key to ""
            } else {
                key to when (value) {
                    is Value.String -> value.asString() ?: value.toString()
                    is Value.Boolean -> value.asBoolean()?.toString() ?: value.toString()
                    is Value.Integer -> value.asInteger()?.toString() ?: value.toString()
                    is Value.Double -> value.asDouble()?.toString() ?: value.toString()
                    else -> value.toString()
                }
            }
        }
        .toMap()

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
    errorMessage = this.errorMessage,
    metadata = this.flagMetadata.toEvaluationMetadata()
)

internal fun Map<String, Any>.toEvaluationMetadata(): EvaluationMetadata {
    val builder = Builder()
    forEach { (key, value) ->
        when (value) {
            is String -> builder.putString(key, value)
            is Boolean -> builder.putBoolean(key, value)
            is Int -> builder.putInt(key, value)
            is Double -> builder.putDouble(key, value)
            else -> builder.putString(key, value.toString())
        }
    }
    return builder.build()
}

/**
 * Converts a Datadog [ErrorCode] to an OpenFeature [ErrorCode].
 */
internal fun ErrorCode.toOpenFeatureErrorCode(): OpenFeatureErrorCode = when (this) {
    ErrorCode.GENERAL ->
        OpenFeatureErrorCode.GENERAL

    ErrorCode.INVALID_CONTEXT ->
        OpenFeatureErrorCode.INVALID_CONTEXT

    ErrorCode.PROVIDER_NOT_READY ->
        OpenFeatureErrorCode.PROVIDER_NOT_READY

    ErrorCode.FLAG_NOT_FOUND ->
        OpenFeatureErrorCode.FLAG_NOT_FOUND

    ErrorCode.PARSE_ERROR ->
        OpenFeatureErrorCode.PARSE_ERROR

    ErrorCode.TYPE_MISMATCH ->
        OpenFeatureErrorCode.TYPE_MISMATCH
}

private const val ASSIGNMENT_TOKEN_ATTRIBUTE = "dd_assignment_token"
private const val INVALID_ASSIGNMENT_TOKEN_SENTINEL = "\u0000"
