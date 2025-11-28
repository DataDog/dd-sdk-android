/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
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
 * @param internalLogger logger for error and debug messages
 */
internal class FlagsStateManager(
    private val subscription: DDCoreSubscription<FlagsStateListener>,
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger
) : StateObservable {
    /**
     * The current state of the client.
     * Thread-safe: uses volatile for visibility across threads.
     */
    @Volatile
    private var currentState: FlagsClientState = FlagsClientState.NotReady

    /**
     * Returns the current state synchronously.
     *
     * @return The current [FlagsClientState].
     */
    override fun getCurrentState(): FlagsClientState = currentState

    /**
     * Updates the state and notifies all listeners.
     *
     * This method stores the new state and asynchronously notifies all registered listeners
     * on the executor service, ensuring ordered delivery.
     *
     * @param newState The new state to transition to.
     */
    internal fun updateState(newState: FlagsClientState) {
        executorService.executeSafe(
            operationName = UPDATE_STATE_OPERATION_NAME,
            internalLogger = internalLogger
        ) {
            currentState = newState
            subscription.notifyListeners {
                onStateChanged(newState)
            }
        }
    }

    override fun addListener(listener: FlagsStateListener) {
        subscription.addListener(listener)

        // Emit current state to new listener
        executorService.executeSafe(
            operationName = NOTIFY_NEW_LISTENER_OPERATION_NAME,
            internalLogger = internalLogger
        ) {
            val state = synchronized(this) { currentState }
            listener.onStateChanged(state)
        }
    }

    override fun removeListener(listener: FlagsStateListener) {
        subscription.removeListener(listener)
    }

    companion object {
        private const val UPDATE_STATE_OPERATION_NAME = "Update flags client state"
        private const val NOTIFY_NEW_LISTENER_OPERATION_NAME = "Notify new listener of current flags state"
    }
}
