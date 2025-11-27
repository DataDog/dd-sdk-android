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
 * State updates trigger listener notifications asynchronously on the executor service.
 *
 * @param subscription the underlying subscription for managing listeners
 * @param executorService single-threaded executor for ordered state notification delivery
 */
internal class FlagsStateManager(
    private val subscription: DDCoreSubscription<FlagsStateListener>,
    private val executorService: ExecutorService
) {
    /**
     * Updates the state and notifies all listeners.
     *
     * This method asynchronously notifies all registered listeners on the executor service,
     * ensuring ordered delivery.
     *
     * @param newState The new state to transition to.
     */
    internal fun updateState(newState: FlagsClientState) {
        executorService.execute {
            subscription.notifyListeners {
                onStateChanged(newState)
            }
        }
    }

    /**
     * Registers a listener to receive state change notifications.
     *
     * @param listener The listener to add.
     */
    fun addListener(listener: FlagsStateListener) {
        subscription.addListener(listener)
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
