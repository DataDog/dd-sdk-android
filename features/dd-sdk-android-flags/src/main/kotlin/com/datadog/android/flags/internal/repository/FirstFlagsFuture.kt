/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One shared, non-cancellable completion containing the first installed assignment keys. */
@Suppress("UnsafeThirdPartyFunctionCall") // Private monitor, positive latch count, and privately owned snapshot.
internal class FirstFlagsFuture : Future<List<String>> {
    private val lock = Any()
    private val completed = CountDownLatch(1)
    private val listeners = mutableListOf<(List<String>) -> Unit>()

    @Volatile
    private var keys: List<String>? = null

    fun complete(installedKeys: Collection<String>) {
        val snapshot = Collections.unmodifiableList(ArrayList(installedKeys))
        val callbacks = synchronized(lock) {
            if (keys != null) return
            keys = snapshot
            completed.countDown()
            listeners.toList().also { listeners.clear() }
        }
        callbacks.forEach { it(snapshot) }
    }

    fun whenComplete(listener: (List<String>) -> Unit) {
        val snapshot = synchronized(lock) {
            keys.also { if (it == null) listeners.add(listener) }
        }
        snapshot?.let(listener)
    }

    @Suppress("CheckInternal") // The latch opens only after the result is stored.
    override fun get(): List<String> {
        completed.await()
        return checkNotNull(keys)
    }

    // Future requires timeout failure; an open latch guarantees keys.
    @Suppress("CheckInternal", "ThrowingInternalException")
    override fun get(timeout: Long, unit: TimeUnit): List<String> {
        if (!completed.await(timeout, unit)) throw TimeoutException("No flags installed")
        return checkNotNull(keys)
    }

    override fun isDone(): Boolean = keys != null
    override fun isCancelled(): Boolean = false
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
}
