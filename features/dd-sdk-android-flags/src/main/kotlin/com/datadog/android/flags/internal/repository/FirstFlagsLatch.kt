/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import java.util.Collections

/** Retains the first installed flag keys for cancellable, one-shot listeners. */
internal class FirstFlagsLatch {
    private val lock = Any()
    private val listeners = mutableListOf<PendingCallback>()
    private var keys: List<String>? = null

    fun complete(installedKeys: Collection<String>, deliver: (Runnable) -> Unit = Runnable::run) {
        // Both calls reject null inputs; installedKeys and its newly allocated copy are non-null.
        @Suppress("UnsafeThirdPartyFunctionCall")
        val snapshot = Collections.unmodifiableList(ArrayList(installedKeys))
        val callbacks = synchronized(lock) {
            if (keys != null) return
            keys = snapshot
            listeners.toList().also { listeners.clear() }
        }
        if (callbacks.isEmpty()) return
        deliver(
            Runnable {
                callbacks.forEach { pending ->
                    val callback = synchronized(lock) {
                        pending.listener.also { pending.listener = null }
                    }
                    callback?.invoke(snapshot)
                }
            }
        )
    }

    // The private mutable list supports mutation; PendingCallback uses non-throwing identity equality.
    @Suppress("UnsafeThirdPartyFunctionCall")
    fun whenComplete(listener: (List<String>) -> Unit): () -> Unit {
        val pending = PendingCallback(listener)
        val snapshot = synchronized(lock) {
            keys.also { snapshot ->
                if (snapshot == null) listeners.add(pending) else pending.listener = null
            }
        }
        snapshot?.let(listener)
        return {
            synchronized(lock) {
                pending.listener = null
                listeners.remove(pending)
            }
        }
    }

    private class PendingCallback(var listener: ((List<String>) -> Unit)?)
}
