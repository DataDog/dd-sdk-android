/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal

import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extension function to convert callback-based [setEvaluationContext] to suspend function.
 *
 * Wraps the callback API in [suspendCancellableCoroutine], converting success/failure callbacks
 * to [resume]/[resumeWithException].
 *
 * Cancellation stops waiting without cancelling the native request or suppressing its state events.
 * A late callback cannot resume a cancelled OpenFeature lifecycle operation and overwrite its
 * replacement status.
 *
 * A timeout with matching cached assignments completes successfully and leaves the native client stale.
 * OpenFeature Kotlin 0.8 independently sets its status to Ready on successful lifecycle completion;
 * that status can differ from the native state depending on when provider events are collected.
 *
 * @param context The evaluation context to set
 * @throws [OpenFeatureError.GeneralError] if setting the context fails. The first context operation also
 * fails when the configured Flags initialization timeout elapses without matching cached assignments.
 */
internal suspend fun FlagsClient.setEvaluationContextSuspend(context: EvaluationContext) {
    // The native API completes this callback once; a callback arriving after cancellation is ignored.
    @Suppress("UnsafeThirdPartyFunctionCall")
    suspendCancellableCoroutine<Unit> { continuation ->
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() {
                continuation.resume(Unit)
            }

            override fun onFailure(error: Throwable) {
                val isUsableTimeout = error is FlagsInitializationTimeoutException &&
                    when (state.getCurrentState()) {
                        FlagsClientState.Ready, FlagsClientState.Stale -> true
                        else -> false
                    }
                if (isUsableTimeout) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        OpenFeatureError.GeneralError(
                            error.message ?: "Unknown error: ${error::class.simpleName}"
                        )
                    )
                }
            }
        }

        setEvaluationContext(context, callback)
    }
}
