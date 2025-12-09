/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientState

/**
 * Observable interface for tracking [FlagsClient] state changes.
 *
 * This interface provides two ways to observe state:
 * 1. **Synchronous getter**: [getCurrentState] for immediate state queries
 * 2. **Callback pattern**: [addListener]/[removeListener] for reactive observers
 *
 * ## Usage Examples
 *
 * ```kotlin
 * // Synchronous getter
 * val current = client.state.getCurrentState()
 * if (current is FlagsClientState.Ready) {
 *     // Proceed
 * }
 *
 * // Callback pattern
 * client.state.addListener(object : FlagsStateListener {
 *     override fun onStateChanged(newState: FlagsClientState) {
 *         // Handle state change
 *     }
 * })
 * ```
 */
interface StateObservable {
    /**
     * Returns the current state synchronously.
     *
     * This method is safe to call from any thread.
     *
     * @return The current [FlagsClientState].
     */
    fun getCurrentState(): FlagsClientState

    /**
     * Registers a listener to receive state change notifications.
     *
     * The listener will immediately receive the current state upon registration,
     * then be notified of all future state changes.
     *
     * @param listener The [FlagsStateListener] to register.
     */
    fun addListener(listener: FlagsStateListener)

    /**
     * Unregisters a previously registered state listener.
     *
     * @param listener The [FlagsStateListener] to unregister.
     */
    fun removeListener(listener: FlagsStateListener)
}
