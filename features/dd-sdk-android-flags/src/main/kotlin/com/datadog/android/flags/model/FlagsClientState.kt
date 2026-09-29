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
     * This is the initial state before loading begins. A cache readiness policy can
     * publish Ready before the first evaluation context is set.
     */
    object NotReady : FlagsClientState()

    /**
     * The client has usable assignments available under its readiness policy.
     * These can come from disk with a cache-first policy or from an accepted network response.
     */
    object Ready : FlagsClientState()

    /**
     * The client is currently fetching new flags for a context change.
     * Cached flags may still be available for evaluation during this state.
     */
    object Reconciling : FlagsClientState()

    /**
     * Reconciliation failed and the client has retained usable assignments.
     * Initialization can complete successfully in this state. Per-flag resolution reasons separately
     * describe disk origin (CACHED) or a requested-context mismatch (STALE).
     */
    object Stale : FlagsClientState()

    /**
     * An error has occurred.
     * The client can recover when a later operation loads valid flag assignments.
     *
     * @param error The error that caused the transition to this state, or null if unknown.
     */
    data class Error(val error: Throwable? = null) : FlagsClientState()
}
