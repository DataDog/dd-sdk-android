/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreSubscription

/**
 * Channel for managing and notifying state change listeners.
 *
 * This class wraps [DDCoreSubscription] and provides semantic methods for each state
 * transition, abstracting the underlying notification mechanism. All notification methods
 * are thread-safe and guarantee ordered delivery to listeners.
 *
 * @param subscription the underlying subscription for managing listeners
 * @param notificationLock lock for ensuring ordered delivery of state notifications
 */
internal class FlagsStateChannel(
    private val subscription: DDCoreSubscription<FlagsStateListener>,
    private val notificationLock: Any = Any()
) {
    /**
     * Notifies all listeners that the client has transitioned to NOT_READY state.
     *
     * This state indicates the client has been created but no evaluation context has been set.
     */
    fun notifyNotReady() {
        notifyState(FlagsClientState.NOT_READY, null)
    }

    /**
     * Notifies all listeners that the client has transitioned to READY state.
     *
     * This state indicates flags have been successfully loaded and are available for evaluation.
     */
    fun notifyReady() {
        notifyState(FlagsClientState.READY, null)
    }

    /**
     * Notifies all listeners that the client has transitioned to RECONCILING state.
     *
     * This state indicates the client is currently fetching new flags for a context change.
     * Cached flags may still be available for evaluation during this state.
     */
    fun notifyReconciling() {
        notifyState(FlagsClientState.RECONCILING, null)
    }

    /**
     * Notifies all listeners that the client has transitioned to ERROR state.
     *
     * This state indicates an unrecoverable error has occurred.
     *
     * @param error the error that caused the transition to ERROR state, or null if unknown
     */
    fun notifyError(error: Throwable? = null) {
        notifyState(FlagsClientState.ERROR, error)
    }

    /**
     * Registers a listener to receive state change notifications.
     *
     * @param listener the listener to register
     */
    fun addListener(listener: FlagsStateListener) {
        subscription.addListener(listener)
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @param listener the listener to unregister
     */
    fun removeListener(listener: FlagsStateListener) {
        subscription.removeListener(listener)
    }

    /**
     * Notifies all registered listeners of a state change.
     *
     * This method is thread-safe and guarantees ordered delivery. Multiple concurrent
     * calls will be serialized to prevent out-of-order notifications.
     *
     * @param newState the new state
     * @param error optional error associated with the state change
     */
    private fun notifyState(newState: FlagsClientState, error: Throwable?) {
        synchronized(notificationLock) {
            subscription.notifyListeners {
                onStateChanged(newState, error)
            }
        }
    }
}
