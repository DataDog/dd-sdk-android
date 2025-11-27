/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientState
import kotlinx.coroutines.flow.StateFlow

/**
 * Observable interface for tracking [FlagsClient] state changes.
 *
 * This interface provides three ways to observe state:
 * 1. **Synchronous getter**: [getCurrentState] for immediate state queries (Java-friendly)
 * 2. **Reactive Flow**: [flow] for coroutine-based reactive updates (Kotlin)
 * 3. **Callback pattern**: [addListener]/[removeListener] for traditional observers (Java-friendly)
 *
 * ## Usage Examples
 *
 * ```kotlin
 * // Synchronous getter (Java-friendly, no Flow dependency)
 * val current = client.state.getCurrentState()
 * if (current is FlagsClientState.Ready) {
 *     // Proceed
 * }
 *
 * // Reactive Flow (Kotlin coroutines)
 * client.state.flow.value  // Current value
 * client.state.flow.collect { state ->  // Collect updates
 *     // Handle state change
 * }
 *
 * // Callback pattern (Java-friendly)
 * client.state.addListener(object : FlagsStateListener {
 *     override fun onStateChanged(newState: FlagsClientState) {
 *         // Handle state change
 *     }
 * })
 * ```
 */
interface StateObservable {
    /**
     * Reactive Flow of state changes.
     *
     * This [StateFlow] emits the current state immediately to new collectors,
     * then emits all subsequent state changes. Suitable for Kotlin coroutines users.
     */
    val flow: StateFlow<FlagsClientState>

    /**
     * Returns the current state synchronously.
     *
     * This method is safe to call from any thread and does not require coroutines.
     * Suitable for Java users or quick state checks.
     *
     * @return The current [FlagsClientState].
     */
    fun getCurrentState(): FlagsClientState

    /**
     * Registers a listener to receive state change notifications.
     *
     * The listener will immediately receive the current state upon registration,
     * then be notified of all future state changes. Suitable for Java users or
     * when Flow is not available.
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
