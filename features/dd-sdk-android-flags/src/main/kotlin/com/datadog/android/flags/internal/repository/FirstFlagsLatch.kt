/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opens once with the first installed flag keys, retaining them for late listeners. */
internal class FirstFlagsLatch {
    private val lock = Any()

    @Suppress("UnsafeThirdPartyFunctionCall") // CountDownLatch rejects negative counts; the constant 1 is valid.
    private val completed = CountDownLatch(1)
    private val listeners = mutableListOf<(List<String>) -> Unit>()

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
    // Interruption propagates to the repository, which restores the thread's interrupt status.
    @Suppress("UnsafeThirdPartyFunctionCall")
    fun await(timeout: Long, unit: TimeUnit): Boolean = completed.await(timeout, unit)
}
