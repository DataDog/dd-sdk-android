/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreSubscription
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages state transitions and notifications for a [com.datadog.android.flags.FlagsClient].
 *
 * This class handles state change notifications to registered listeners. All notification
 * methods are thread-safe and guarantee ordered delivery to listeners by using a
 * fair [ReentrantReadWriteLock].
 *
 * The current state is stored and emitted to new listeners immediately upon registration,
 * ensuring every listener receives the current state.
 *
 * **Important:** Listener callbacks are invoked synchronously while holding an internal lock.
 * Listeners should be fast and non-blocking. If long-running operations are needed,
 * dispatch them to a background thread. Listeners must catch and handle exceptions to prevent
 * them from bubbling up and crashing the application.
 *
 * @param subscription the underlying subscription for managing listeners
 */
internal class FlagsStateManager(private val subscription: DDCoreSubscription<FlagsStateListener>) : StateObservable {
    /**
     * Fair read-write lock to ensure FIFO ordering of state mutations and allow concurrent reads.
     * The fair parameter ensures that threads acquire the lock in the order they requested it.
     * Read operations can proceed concurrently, while write operations are exclusive.
     */
    private val stateLock = ReentrantReadWriteLock(true)

    /**
     * The current state of the client.
     * Thread-safe: access is protected by [stateLock].
     */
    private var currentState: FlagsClientState = FlagsClientState.NotReady

    /**
     * Returns the current state synchronously.
     *
     * @return The current [FlagsClientState].
     */
    override fun getCurrentState(): FlagsClientState = stateLock.read {
        currentState
    }

    /**
     * Updates the state and notifies all listeners synchronously.
     *
     * This method stores the new state and notifies all registered listeners
     * within the write lock, ensuring ordered and atomic delivery.
     *
     * @param newState The new state to transition to.
     */
    internal fun updateState(newState: FlagsClientState) {
        stateLock.write {
            currentState = newState
            subscription.notifyListeners {
                onStateChanged(newState)
            }
        }
    }

    override fun addListener(listener: FlagsStateListener) {
        listener.onStateChanged(currentState)
        subscription.addListener(listener)
    }

    override fun removeListener(listener: FlagsStateListener) {
        subscription.removeListener(listener)
    }
}
