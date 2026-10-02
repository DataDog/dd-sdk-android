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
internal class FirstFlagsFuture : Future<List<String>> {
    private val lock = Any()

    @Suppress("UnsafeThirdPartyFunctionCall") // CountDownLatch rejects negative counts; the constant 1 is valid.
    private val completed = CountDownLatch(1)
    private val listeners = mutableListOf<(List<String>) -> Unit>()

    @Volatile
    private var keys: List<String> = emptyList()

    fun complete(installedKeys: Collection<String>) {
        // Both calls reject null inputs; installedKeys and its newly allocated copy are non-null.
        @Suppress("UnsafeThirdPartyFunctionCall")
        val snapshot = Collections.unmodifiableList(ArrayList(installedKeys))
        val callbacks = synchronized(lock) {
            if (completed.count == 0L) return
            keys = snapshot
            completed.countDown()
            listeners.toList().also { listeners.clear() }
        }
        callbacks.forEach { it(snapshot) }
    }

    fun whenComplete(listener: (List<String>) -> Unit) {
        val snapshot = synchronized(lock) {
            if (completed.count == 0L) {
                keys
            } else {
                listeners.add(listener)
                null
            }
        }
        snapshot?.let(listener)
    }

    @Throws(InterruptedException::class)
    // await can throw on interruption; Future.get requires propagation, so this is not a nonthrowing call.
    @Suppress("UnsafeThirdPartyFunctionCall")
    override fun get(): List<String> {
        completed.await()
        return keys
    }

    @Throws(InterruptedException::class, TimeoutException::class)
    // await can throw on interruption; Future.get requires propagation and an exception on timeout.
    @Suppress("UnsafeThirdPartyFunctionCall", "ThrowingInternalException")
    override fun get(timeout: Long, unit: TimeUnit): List<String> {
        if (!completed.await(timeout, unit)) throw TimeoutException("No flags installed")
        return keys
    }

    override fun isDone(): Boolean = completed.count == 0L
    override fun isCancelled(): Boolean = false
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
}
