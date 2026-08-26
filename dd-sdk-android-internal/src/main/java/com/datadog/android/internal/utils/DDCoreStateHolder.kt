/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A utility that holds a state and notifies listeners about its changes. Built on
 * [DDCoreSubscription], it satisfies the following requirements:
 * 1. The current state can be read synchronously from any thread.
 * 2. A listener added through [addListener] is notified with the current state before [addListener]
 * returns, and is guaranteed not to miss any subsequent state change.
 * 3. Each state change is broadcast to the listeners registered at the time, in the order they were
 * registered, and the broadcasts happen in the order [updateState] is entered, whichever thread
 * each call comes from.
 * 4. A listener callback may re-enter the holder ([updateState], [addListener] or [removeListener])
 * without deadlocking. A state passed to [updateState] from within a callback is queued rather than
 * delivered on top of the notification in flight, so requirement 3 holds for it too: it is
 * delivered after the updates that other threads requested earlier, and not before.
 *
 * **Important:** listener callbacks are invoked synchronously while holding an internal lock, so
 * they should be fast and non-blocking. Exceptions thrown by a listener are not swallowed: a
 * listener throwing skips the listeners registered after it, and the exception is rethrown to the
 * caller of [updateState]/[addListener] that requested *that* state - never to another thread that
 * happened to deliver it. The states queued behind the failed one are still delivered, since other
 * threads may be waiting on them. The exception of a re-entrant [updateState], which returns before
 * its delivery, surfaces on the call that is draining the queue.
 *
 * An [Error] - [OutOfMemoryError] and the like - is not deferred that way: it aborts the delivery
 * on the spot and propagates on the thread that raised it, rather than having us keep running
 * listener code in a process that is already in trouble. The states still queued at that point are
 * not lost, they are delivered by the next call that drains the queue.
 *
 * @param S the type of the state being held.
 * @param L the type of the listeners to notify.
 */
interface DDCoreStateHolder<S : Any, L : Any> {

    /**
     * The state most recently delivered to the listeners, which is the latest state requested
     * through [updateState] once the delivery caught up. Safe to call from any thread.
     */
    val currentState: S

    /**
     * Updates the state and notifies all the registered listeners with the new state.
     *
     * This blocks until the listeners have been notified, unless it is called from within a
     * listener callback: the new state is then queued and delivered by the delivery already in
     * progress, so this returns before the listeners see it, and [currentState] only reflects it
     * once that happens.
     *
     * @param newState the new state to transition to.
     */
    fun updateState(newState: S)

    /**
     * Registers a listener, immediately notifying it with the current state.
     *
     * That initial notification is part of registering, not part of a broadcast, and it is not
     * ordered against a broadcast in flight: registering from within a listener callback hands the
     * new listener the state being broadcast right away, ahead of the listeners that have not been
     * reached yet. The alternative - deferring until the broadcast finishes - would mean returning
     * from this method with the listener not yet holding a state, which is the worse surprise of
     * the two.
     *
     * The listener is registered before that initial notification, so it stays registered even if
     * its callback throws.
     *
     * @param listener the listener to register.
     */
    fun addListener(listener: L)

    /**
     * Unregisters a previously registered listener.
     *
     * @param listener the listener to unregister.
     */
    fun removeListener(listener: L)

    companion object {
        /**
         * Creates a new [DDCoreStateHolder].
         *
         * @param S the type of the state being held.
         * @param L the type of the listeners to notify.
         * @param initialState the state the holder starts with.
         * @param onStateChanged called to deliver a state to a single listener.
         */
        fun <S : Any, L : Any> create(
            initialState: S,
            onStateChanged: L.(S) -> Unit
        ): DDCoreStateHolder<S, L> {
            return DDCoreStateHolderImpl(initialState, onStateChanged)
        }
    }
}

private class DDCoreStateHolderImpl<S : Any, L : Any>(
    initialState: S,
    private val onStateChanged: L.(S) -> Unit
) : DDCoreStateHolder<S, L> {

    private val subscription = DDCoreSubscription.create<L>()

    /**
     * Guards the delivery of the states and the fields below. Read operations can proceed
     * concurrently, while delivery is exclusive. The fair parameter keeps a steady stream of
     * [currentState] readers from starving the delivery; the delivery *order* comes from
     * [pendingStates], not from this lock.
     */
    private val stateLock = ReentrantReadWriteLock(true)

    /**
     * The state most recently handed over to the listeners. Guarded by the write lock of
     * [stateLock].
     */
    private var state: S = initialState

    /**
     * The states requested through [updateState] and not delivered yet, in the order they were
     * requested. Deliberately *not* guarded by [stateLock]: appending to it is what orders the
     * updates, and it has to happen before the calling thread contends for the lock.
     */
    private val pendingStates = ConcurrentLinkedQueue<PendingUpdate<S>>()

    /**
     * Whether this thread is inside [deliverPendingStates]. Guarded by the write lock of
     * [stateLock], which is exclusive, so this is only ever true for a re-entrant call.
     */
    private var isNotifying = false

    override val currentState: S
        get() = stateLock.read { state }

    override fun updateState(newState: S) {
        val update = PendingUpdate(
            state = newState,
            // A re-entrant call returns before its state is delivered, so it is in no position to
            // hand a listener failure back to its caller. The draining thread reports that one.
            isReportedBySubmitter = !stateLock.isWriteLockedByCurrentThread
        )
        // Queueing before contending for the lock: this, and not the lock acquisition, is what
        // orders the updates. A thread parked on the lock has not published anything yet, so
        // ordering by lock acquisition would let a re-entrant update - which reacquires the lock
        // immediately, fairness notwithstanding - overtake an update requested earlier by
        // another thread.
        @Suppress("UnsafeThirdPartyFunctionCall") // item added is not null
        pendingStates.offer(update)

        var failure: Exception? = null
        stateLock.write {
            // A delivery is already running further up this thread's stack: its loop picks up what
            // we just queued, once it is done with the state it is currently delivering. Delivering
            // from here instead would nest the notifications and reverse their order.
            if (!isNotifying) {
                failure = deliverPendingStates()
            }
            // Read under the lock: whichever thread delivered this update recorded the outcome
            // while holding it.
            failure = combineFailures(update.failure, failure)
        }

        val reportedFailure = failure
        if (reportedFailure != null) {
            @Suppress("ThrowingInternalException") // we are just propagating the listener's own
            throw reportedFailure
        }
    }

    /**
     * Delivers every queued state, recording on each update the failure its listeners raised, so
     * that the thread which requested it reports it rather than whichever thread happened to drain
     * the queue.
     *
     * @return the failures of the updates nobody else will report - the re-entrant ones - or null.
     */
    private fun deliverPendingStates(): Exception? {
        isNotifying = true
        var orphanFailure: Exception? = null
        try {
            while (true) {
                val next = pendingStates.poll() ?: break
                state = next.state
                val failure = notifyListenersCatching(next.state)
                next.failure = failure
                if (failure != null && !next.isReportedBySubmitter) {
                    orphanFailure = combineFailures(orphanFailure, failure)
                }
            }
        } finally {
            isNotifying = false
        }
        return orphanFailure
    }

    /**
     * Notifies the listeners of [newState], returning the exception a listener raised, or null.
     *
     * A listener throwing already skipped the listeners registered after it. It must not also
     * discard the states queued behind this one, which other threads may be waiting on.
     *
     * Only [Exception] is held back this way. An [Error] means the runtime itself is failing, so it
     * propagates immediately instead of being deferred to another thread once the queue has drained
     * - the remaining states are then delivered by the next call to enter the holder.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun notifyListenersCatching(newState: S): Exception? {
        return try {
            subscription.notifyListeners { onStateChanged(newState) }
            null
        } catch (e: Exception) {
            e
        }
    }

    override fun addListener(listener: L) {
        // Write lock (and not read lock): the callback below may call back into this holder, and a
        // read lock cannot be upgraded to a write lock, which would deadlock the calling thread.
        stateLock.write {
            // Registering before the initial notification, so that a callback re-entering
            // updateState doesn't leave this listener with a state it will never see again.
            subscription.addListener(listener)
            // A delivery in flight does not reach a listener registered while it is running, so
            // this listener joins the stream at the last delivered state and receives whatever is
            // queued behind it right after.
            listener.onStateChanged(state)
        }
    }

    override fun removeListener(listener: L) {
        stateLock.write {
            subscription.removeListener(listener)
        }
    }

    private fun combineFailures(first: Exception?, second: Exception?): Exception? {
        if (first == null) return second
        // A listener rethrowing a shared instance would otherwise make addSuppressed throw.
        if (second != null && second !== first) {
            first.addSuppressed(second)
        }
        return first
    }
}

/**
 * One requested state, along with the exception its delivery raised, so that the exception can be
 * reported to the thread that requested the update rather than to the one that delivered it.
 *
 * @param S the type of the state being held.
 * @property state the requested state.
 * @property isReportedBySubmitter whether the thread that requested this update will observe
 * [failure] itself, which a re-entrant caller cannot do since it returns before the delivery.
 */
private class PendingUpdate<S : Any>(
    val state: S,
    val isReportedBySubmitter: Boolean
) {
    /**
     * The exception raised while delivering [state]. Written and read under the write lock of the
     * holder's state lock.
     */
    var failure: Exception? = null
}
