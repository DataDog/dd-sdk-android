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
 * callbacks whenever the client's state changes.
 */
interface FlagsStateListener {
    /**
     * Called when the state of the [FlagsClient] changes.
     *
     * @param newState The new state of the client. If the state is [FlagsClientState.Error],
     *                 the error details are contained within the state object itself.
     */
    fun onStateChanged(newState: FlagsClientState)
}
