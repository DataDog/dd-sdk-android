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
 * operation completes. It is normally invoked after the corresponding [FlagsClientState]
 * transition. The first operation can fail with [FlagsInitializationTimeoutException].
 * The request continues and can transition the client to [FlagsClientState.Ready] later.
 */
interface EvaluationContextCallback {
    /**
     * Invoked when the evaluation context update completes successfully.
     *
     * This method is called on a background executor thread after the state transitions
     * to [FlagsClientState.Ready]. A newer context can replace the installed assignments before delivery.
     * Subsequent resolutions use the current context and can return defaults while it is loading.
     */
    fun onSuccess()

    /**
     * Invoked when the evaluation context update fails.
     *
     * This method is normally called on a background executor thread after the state transitions
     * to either [FlagsClientState.Stale] (network failed but cached flags available) or
     * [FlagsClientState.Error] (network failed with no cached flags). An initialization timeout uses
     * the same state selection based on matching cached assignments. The request continues and can
     * transition the client to [FlagsClientState.Ready] later.
     *
     * In enabled experimentation opt-out modes, malformed timestamps fail with [FlagsInvalidContextException]
     * without an assignment request. A usable response discarded after a newer context request fails with
     * [FlagsContextSupersededException], unless the initialization timeout already completed the callback.
     * Treat that exception as cancellation. It does not change the newer request's client state.
     *
     * @param error A [Throwable] containing details about the failure, typically including
     * a message explaining the network request failure.
     */
    fun onFailure(error: Throwable)
}
