/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal

import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.EvaluationContext
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Extension function to convert callback-based [setEvaluationContext] to suspend function.
 *
 * Wraps the callback API in a suspendCoroutine, converting success/failure callbacks
 * to resume/resumeWithException.
 *
 * @param context The evaluation context to set
 * @throws OpenFeatureError.GeneralError if setting the context fails or times out.
 */
internal suspend fun FlagsClient.setEvaluationContextSuspend(context: EvaluationContext) {
    suspendCoroutine<Unit> { continuation ->
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() {
                continuation.resume(Unit)
            }

            override fun onFailure(error: Throwable) {
                continuation.resumeWithException(
                    OpenFeatureError.GeneralError(
                        error.message ?: "Unknown error: ${error::class.simpleName}"
                    )
                )
            }
        }

        // setEvaluationContext is guaranteed to return within the configured timeout.
        setEvaluationContext(context, callback)
    }
}
