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
     * **Important:** This method is called synchronously while holding internal locks.
     * Implementations should be fast and non-blocking. If you need to perform long-running
     * operations (network calls, database queries, heavy computation), dispatch them to a
     * background thread.
     *
     * **Exception handling:** Exceptions thrown from this method will propagate to the caller
     * and may crash the application if not caught. Subsequent listeners will not be notified
     * if an exception is thrown. Always catch and handle exceptions within your implementation
     * to prevent crashes and ensure other listeners are notified.
     *
     * @param newState The new state of the client. If the state is [FlagsClientState.Error],
     *                 the error details are contained within the state object itself.
     */
    fun onStateChanged(newState: FlagsClientState)
}
