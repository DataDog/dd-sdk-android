/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreStateHolder

/**
 * Manages state transitions and notifications for a [com.datadog.android.flags.FlagsClient].
 *
 * This class handles state change notifications to registered listeners. All notification
 * methods are thread-safe and guarantee ordered delivery to listeners.
 *
 * The current state is stored and emitted to new listeners immediately upon registration,
 * ensuring every listener receives the current state.
 *
 * **Important:** Listener callbacks are invoked synchronously while holding an internal lock.
 * Listeners should be fast and non-blocking. If long-running operations are needed,
 * dispatch them to a background thread. Listeners must catch and handle exceptions to prevent
 * them from bubbling up and crashing the application.
 *
 * @param stateHolder the underlying state holder, taking care of the listeners and the locking.
 */
internal class FlagsStateManager(
    private val stateHolder: DDCoreStateHolder<FlagsClientState, FlagsStateListener>
) : StateObservable {

    /**
     * Returns the current state synchronously.
     *
     * @return The current [FlagsClientState].
     */
    override fun getCurrentState(): FlagsClientState = stateHolder.currentState

    /**
     * Updates the state and notifies all listeners synchronously.
     *
     * @param newState The new state to transition to.
     */
    internal fun updateState(newState: FlagsClientState) {
        stateHolder.updateState(newState)
    }

    override fun addListener(listener: FlagsStateListener) {
        stateHolder.addListener(listener)
    }

    override fun removeListener(listener: FlagsStateListener) {
        stateHolder.removeListener(listener)
    }
}
