/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Callback interface for asynchronous evaluation context update operations.
 *
 * This callback is invoked when the [FlagsClient.setEvaluationContext] operation completes.
 * With [ClientReadyPolicy.CACHE_OR_NETWORK], the first operation may complete synchronously
 * from already loaded assignments, or on the disk-loading thread. The network refresh continues.
 * Other operations normally complete on a background executor thread. It is normally invoked after the corresponding [FlagsClientState]
 * transition. The first operation can fail with [FlagsInitializationTimeoutException].
 * The request continues and can transition the client to [FlagsClientState.Ready] later.
 */
interface EvaluationContextCallback {
    /**
     * Invoked when the evaluation context update completes successfully.
     *
     * Assignments are available for subsequent resolution calls. During initialization these
     * may be cached assignments accepted by the readiness policy, rather than a fresh response.
     * The initial callback completes only once, even when a later network response updates flags.
     */
    fun onSuccess()

    /**
     * Invoked when the evaluation context update fails.
     *
     * This method is normally called on a background executor thread after the state transitions
     * to either [FlagsClientState.Stale] (a subsequent context update failed with matching cached flags) or
     * [FlagsClientState.Error] (network failed with no cached flags). An initialization timeout uses
     * the same state selection based on matching cached assignments. The request continues and can
     * transition the client to [FlagsClientState.Ready] later.
     *
     * @param error A [Throwable] containing details about the failure, typically including
     * a message explaining the network request failure.
     */
    fun onFailure(error: Throwable)
}
