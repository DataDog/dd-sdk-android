/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Represents the current state of a [com.datadog.android.flags.FlagsClient].
 */
sealed class FlagsClientState {
    /**
     * The client is not ready to evaluate flags.
     *
     * This state occurs:
     * - Before the first [setEvaluationContext()] call (initialization)
     * - After client shutdown
     * - When the provider is not initialized or unavailable
     *
     * No flags are available for evaluation in this state.
     * Maps to OpenFeature's [NOT_READY] state.
     */
    object NotReady : FlagsClientState()

    /**
     * The client has successfully loaded flags and they are available for evaluation.
     * This is the normal operational state.
     */
    object Ready : FlagsClientState()

    /**
     * The client is currently fetching new flags for a context change.
     * Cached flags may still be available for evaluation during this state.
     */
    object Reconciling : FlagsClientState()

    /**
     * The client is currently stale.
     * Cached flags may still be available for evaluation during this state.
     */
    object Stale : FlagsClientState()

    /**
     * An unrecoverable error has occurred.
     * The client cannot provide flag evaluations in this state.
     *
     * @param error The error that caused the transition to this state, or null if unknown.
     */
    data class Error(val error: Throwable? = null) : FlagsClientState()
}
