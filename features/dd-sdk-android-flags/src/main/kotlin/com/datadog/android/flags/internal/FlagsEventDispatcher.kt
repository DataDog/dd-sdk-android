/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.FlagsClientEventHandler
import com.datadog.android.flags.model.FlagsClientEventDetails
import com.datadog.android.flags.model.FlagsClientEventType
import java.util.ArrayDeque

/** Queues installation notifications in commit order; never invokes handlers under its lock. */
@Suppress("UnsafeThirdPartyFunctionCall") // Private mutable collections, guarded by lock; queue entries are non-null.
internal class FlagsEventDispatcher(private val internalLogger: InternalLogger) {
    private class Registration(val type: FlagsClientEventType, val handler: FlagsClientEventHandler) {
        @Volatile var active: Boolean = true
    }

    private val lock = Any()
    private val registrations = mutableListOf<Registration>()
    private val pending = ArrayDeque<List<Registration>>()
    private var draining = false
    private var deferrals = 0
    private val details = FlagsClientEventDetails(providerName = "Datadog Feature Flags Provider")

    fun addHandler(type: FlagsClientEventType, handler: FlagsClientEventHandler) {
        synchronized(lock) {
            if (registrations.none { it.type == type && it.handler === handler }) {
                registrations.add(Registration(type, handler))
            }
        }
    }

    fun removeHandler(type: FlagsClientEventType, handler: FlagsClientEventHandler) {
        synchronized(lock) {
            registrations.removeAll {
                (it.type == type && it.handler === handler).also { removed -> if (removed) it.active = false }
            }
        }
    }

    // Called at the installation boundary. Taking the subscription snapshot here avoids replay to late subscribers.
    fun enqueueConfigurationChanged() {
        synchronized(lock) {
            pending.addLast(registrations.toList())
        }
    }

    // Defer new callback delivery until the caller has finished committing its existing lifecycle outcome.
    // No lock is held while action runs. Nested/reentrant installations remain in the same ordered queue.
    fun <T> deferDispatch(action: () -> T): T {
        synchronized(lock) { deferrals++ }
        try {
            return action()
        } finally {
            synchronized(lock) { deferrals-- }
            drain()
        }
    }

    fun drain() {
        synchronized(lock) {
            if (draining || deferrals > 0) return
            draining = true
        }
        while (true) {
            val handlers = synchronized(lock) {
                if (pending.isEmpty() || deferrals > 0) {
                    draining = false
                    return
                }
                pending.removeFirst()
            }
            handlers.forEach { notifyHandler(it) }
        }
    }

    @Suppress("TooGenericExceptionCaught") // Customer callback exceptions must not prevent other handlers from running.
    private fun notifyHandler(registration: Registration) {
        if (!registration.active) return
        try {
            registration.handler.handle(details)
        } catch (exception: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Flags event handler failed" },
                exception
            )
        }
    }
}
