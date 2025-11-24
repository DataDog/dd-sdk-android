/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientState

/**
 * Listener interface for receiving state change notifications from a [FlagsClient].
 *
 * Implementations of this interface can be registered with a [FlagsClient] to receive
 * callbacks whenever the client's state changes (e.g., from NOT_READY to READY, or
 * from READY to RECONCILING when the evaluation context changes).
 */
interface FlagsStateListener {
    /**
     * Called when the state of the [FlagsClient] changes.
     *
     * @param newState The new state of the client.
     * @param error Optional error that caused the state change. This is typically provided
     *              when transitioning to the [FlagsClientState.ERROR] state.
     */
    fun onStateChanged(newState: FlagsClientState, error: Throwable? = null)
}
