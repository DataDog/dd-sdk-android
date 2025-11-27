/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreSubscription
import java.util.concurrent.ExecutorService

/**
 * Manages state transitions and notifications for a [com.datadog.android.flags.FlagsClient].
 *
 * This class handles state change notifications to registered listeners. All notification
 * methods are thread-safe and guarantee ordered delivery to listeners by using a
 * single-threaded executor service.
 *
 * The current state is stored and emitted to new listeners immediately upon registration,
 * ensuring every listener receives the current state.
 *
 * @param subscription the underlying subscription for managing listeners
 * @param executorService single-threaded executor for ordered state notification delivery
 */
internal class FlagsStateManager(
    private val subscription: DDCoreSubscription<FlagsStateListener>,
    private val executorService: ExecutorService
) {
    /**
     * The current state of the client.
     * Thread-safe: uses volatile for visibility across threads.
     */
    @Volatile
    private var currentState: FlagsClientState = FlagsClientState.NotReady

    /**
     * Updates the state and notifies all listeners.
     *
     * This method stores the new state and asynchronously notifies all registered listeners
     * on the executor service, ensuring ordered delivery.
     *
     * @param newState The new state to transition to.
     */
    internal fun updateState(newState: FlagsClientState) {
        executorService.execute {
            currentState = newState
            subscription.notifyListeners {
                onStateChanged(newState)
            }
        }
    }

    /**
     * Registers a listener to receive state change notifications.
     *
     * The listener will immediately receive the current state, then be notified
     * of all future state changes.
     *
     * @param listener The listener to add.
     */
    fun addListener(listener: FlagsStateListener) {
        subscription.addListener(listener)

        // Emit current state to new listener
        val state = currentState
        executorService.execute {
            listener.onStateChanged(state)
        }
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @param listener The listener to remove.
     */
    fun removeListener(listener: FlagsStateListener) {
        subscription.removeListener(listener)
    }
}
