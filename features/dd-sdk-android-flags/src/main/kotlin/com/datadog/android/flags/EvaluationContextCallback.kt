/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Callback interface for asynchronous evaluation context update operations.
 *
 * This callback is invoked on a background thread after the [FlagsClient.setEvaluationContext]
 * operation completes. The callback is guaranteed to be invoked after the corresponding
 * [FlagsClientState] transition.
 */
interface EvaluationContextCallback {
    /**
     * Invoked when the evaluation context update completes successfully.
     *
     * This method is called on a background executor thread after the state transitions
     * to [FlagsClientState.Ready]. The new flag evaluations are now available for
     * subsequent flag resolution calls.
     */
    fun onSuccess()

    /**
     * Invoked when the evaluation context update fails.
     *
     * This method is called on a background executor thread after the state transitions
     * to either [FlagsClientState.Stale] (network failed but cached flags available) or
     * [FlagsClientState.Error] (network failed with no cached flags).
     *
     * @param error A [Throwable] containing details about the failure, typically including
     * a message explaining the network request failure.
     */
    fun onFailure(error: Throwable)
}
