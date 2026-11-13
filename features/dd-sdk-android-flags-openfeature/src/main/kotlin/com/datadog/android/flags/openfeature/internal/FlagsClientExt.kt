/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal

import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsContextSupersededException
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.FlagsInvalidContextException
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Extension function to convert callback-based [setEvaluationContext] to suspend function.
 *
 * Wraps the callback API in a [suspendCoroutine], converting success/failure callbacks
 * to [resume]/[resumeWithException].
 *
 * A failed refresh or timeout with matching cached assignments leaves the provider stale.
 *
 * @param context The evaluation context to set
 * @param telemetryOptedOut The typed subject decision, or null to preserve native telemetry configuration.
 * @return [ContextUpdateOutcome.STALE] when matching cached assignments remain usable, otherwise
 * [ContextUpdateOutcome.READY].
 * @throws [CancellationException] if a newer request replaces this request before its result is processed while
 * experimentation opt-out or subject-authorization ordering is active.
 * @throws [OpenFeatureError.GeneralError] if setting the context fails without usable cached assignments. The first
 * context operation also fails when the configured Flags initialization timeout elapses without a usable cache.
 */
internal suspend fun FlagsClient.setEvaluationContextSuspend(
    context: EvaluationContext,
    telemetryOptedOut: Boolean? = null
): ContextUpdateOutcome {
    // Subsequent invocation of any resume function will produce
    // an IllegalStateException, but we are safe here
    @Suppress("UnsafeThirdPartyFunctionCall")
    return suspendCoroutine { continuation ->
        _FlagsInternalProxy(this).setEvaluationContext(context, telemetryOptedOut) { state, error ->
            when {
                error is FlagsContextSupersededException -> {
                    continuation.resumeWithException(CancellationException(error.message))
                }
                state == FlagsClientState.Stale ||
                    (error is FlagsInitializationTimeoutException && state == FlagsClientState.Ready) -> {
                    continuation.resume(state.toContextUpdateOutcome())
                }
                error == null -> continuation.resume(state.toContextUpdateOutcome())
                else -> continuation.resumeWithException(
                    if (error is FlagsInvalidContextException) {
                        OpenFeatureError.InvalidContextError(error.message ?: "Invalid evaluation context")
                    } else {
                        OpenFeatureError.GeneralError(
                            error.message ?: "Unknown error: ${error::class.simpleName}"
                        )
                    }
                )
            }
        }
    }
}

internal enum class ContextUpdateOutcome {
    READY,
    STALE
}

private fun FlagsClientState.toContextUpdateOutcome(): ContextUpdateOutcome =
    if (this == FlagsClientState.Stale) ContextUpdateOutcome.STALE else ContextUpdateOutcome.READY
