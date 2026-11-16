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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extension function to convert callback-based [setEvaluationContext] to suspend function.
 *
 * Wraps the callback API in a cancellation-aware coroutine. The native request continues if the
 * coroutine is cancelled, but its later callback cannot complete an obsolete provider update.
 *
 * A failed refresh or timeout with matching cached assignments leaves the provider stale.
 *
 * @param context The evaluation context to set
 * @param telemetryOptedOut The typed subject decision, or null to preserve native telemetry configuration.
 * @param isCurrentOperation Whether this provider update is still the latest update.
 * @param admitUpdate Atomically checks whether the update is current and, if so, registers the native request.
 * @return [ContextUpdateOutcome.STALE] when matching cached assignments remain usable, otherwise
 * [ContextUpdateOutcome.READY].
 * @throws [CancellationException] if a newer provider update replaces this update, or if native request ordering
 * supersedes it while experimentation opt-out or subject authorization is active.
 * @throws [OpenFeatureError.GeneralError] if setting the context fails without usable cached assignments. The first
 * context operation also fails when the configured Flags initialization timeout elapses without a usable cache.
 */
internal suspend fun FlagsClient.setEvaluationContextSuspend(
    context: EvaluationContext,
    telemetryOptedOut: Boolean? = null,
    isCurrentOperation: () -> Boolean = { true },
    admitUpdate: ((() -> Unit) -> Boolean) = { registration ->
        registration()
        true
    }
): ContextUpdateOutcome {
    @Suppress("UnsafeThirdPartyFunctionCall")
    return suspendCancellableCoroutine { continuation ->
        val admitted = admitUpdate {
            _FlagsInternalProxy(this).setEvaluationContextWithOutcome(context, telemetryOptedOut) { state, error ->
                if (!continuation.isActive) return@setEvaluationContextWithOutcome
                when {
                    !isCurrentOperation() -> {
                        continuation.cancel(CancellationException(OBSOLETE_UPDATE_MESSAGE))
                    }
                    error is FlagsContextSupersededException -> {
                        continuation.cancel(CancellationException(error.message))
                    }
                    else -> {
                        val completionState = if (error is FlagsInitializationTimeoutException) {
                            this.state.getCurrentState()
                        } else {
                            state
                        }
                        if (!isCurrentOperation()) {
                            continuation.cancel(CancellationException(OBSOLETE_UPDATE_MESSAGE))
                        } else {
                            continuation.complete(completionState, error)
                        }
                    }
                }
            }
        }
        if (!admitted && continuation.isActive) {
            continuation.cancel(CancellationException(OBSOLETE_UPDATE_MESSAGE))
        }
    }
}

@Suppress("UnsafeThirdPartyFunctionCall")
private fun CancellableContinuation<ContextUpdateOutcome>.complete(
    state: FlagsClientState,
    error: Throwable?
) {
    if (!isActive) return
    val outcome = state.toContextUpdateOutcome()
    when {
        state == FlagsClientState.Stale ||
            (error is FlagsInitializationTimeoutException && state == FlagsClientState.Ready) ||
            error == null -> resume(outcome)
        else -> {
            val failure = error
            resumeWithException(
                if (failure is FlagsInvalidContextException) {
                    OpenFeatureError.InvalidContextError(failure.message ?: "Invalid evaluation context")
                } else {
                    OpenFeatureError.GeneralError(
                        failure.message ?: "Unknown error: ${failure::class.simpleName}"
                    )
                }
            )
        }
    }
}

internal enum class ContextUpdateOutcome {
    READY,
    STALE
}

private fun FlagsClientState.toContextUpdateOutcome(): ContextUpdateOutcome =
    if (this == FlagsClientState.Stale) ContextUpdateOutcome.STALE else ContextUpdateOutcome.READY

private const val OBSOLETE_UPDATE_MESSAGE = "A newer OpenFeature context replaced this update."
