/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import java.util.concurrent.CopyOnWriteArraySet

/**
 * A utility that holds listeners and notifies them. It satisfies the following requirements:
 * 1. All methods can be called from any thread.
 * 2. It is possible to call [addListener] and [removeListener] inside a listener callback.
 * 3. Listeners are notified in the order [addListener] is called on them.
 */
interface DDCoreSubscription<T : Any> {
    /**
     * Add listener.
     */
    fun addListener(listener: T)

    /**
     * Remove listener.
     */
    fun removeListener(listener: T)

    /**
     * Notify all listeners.
     */
    fun notifyListeners(block: T.() -> Unit)

    /**
     * Return current number of listeners.
     */
    val listenersCount: Int

    companion object {
        /**
         * Creates an instance of [DDCoreSubscription].
         */
        fun <T : Any> create(): DDCoreSubscription<T> {
            return DDCoreSubscriptionImpl()
        }
    }
}

private class DDCoreSubscriptionImpl<T : Any> : DDCoreSubscription<T> {
    private val listeners = CopyOnWriteArraySet<T>()

    override fun addListener(listener: T) {
        listeners.add(listener)
    }

    override fun removeListener(listener: T) {
        listeners.remove(listener)
    }

    override fun notifyListeners(block: T.() -> Unit) {
        listeners.forEach { it.block() }
    }

    override val listenersCount: Int get() = listeners.size
}
