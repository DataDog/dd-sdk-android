/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionDetails
import dev.openfeature.kotlin.sdk.ProviderEvaluation
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
