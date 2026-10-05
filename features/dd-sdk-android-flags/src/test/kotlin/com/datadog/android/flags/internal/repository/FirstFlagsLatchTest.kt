/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class FirstFlagsLatchTest {
    @Test
    fun `M suppress unclaimed listeners W unsubscribed after dispatch queued`() {
        val latch = FirstFlagsLatch()
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<List<String>>()
        val unsubscribe = latch.whenComplete { events.add(it) }
        latch.complete(listOf("first")) { queued.add(it) }
        unsubscribe()
        queued.single().run()
        assertThat(events).isEmpty()
        latch.whenComplete { events.add(it) }
        assertThat(events).containsExactly(listOf("first"))
    }

    @Test
    fun `M preserve first snapshot W network updates before queued delivery`() {
        val latch = FirstFlagsLatch()
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<List<String>>()
        latch.whenComplete { events.add(it) }
        latch.complete(listOf("cache")) { queued.add(it) }
        latch.complete(listOf("network")) { queued.add(it) }
        assertThat(events).isEmpty()
        queued.single().run()
        assertThat(events).containsExactly(listOf("cache"))
    }

    @Test
    fun `M skip dispatcher W no pending listeners`() {
        val latch = FirstFlagsLatch()
        var dispatched = false
        latch.complete(emptyList()) { dispatched = true }
        var replayed = false
        latch.whenComplete { replayed = true }
        assertThat(dispatched).isFalse()
        assertThat(replayed).isTrue()
    }

    @Test
    fun `M remove only cancelled listener W repeated cancellation`() {
        val latch = FirstFlagsLatch()
        val delivered = mutableListOf<String>()
        val cancelled = latch.whenComplete { delivered.add("cancelled") }
        latch.whenComplete { delivered.add("active") }
        cancelled()
        cancelled()
        latch.complete(listOf("first"))
        val late = latch.whenComplete { delivered.add("late") }
        late()
        assertThat(delivered).containsExactly("active", "late")
    }

    @Test
    fun `M cancel unclaimed callback W completion has captured listeners`() {
        val latch = FirstFlagsLatch()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var delivered = false
        latch.whenComplete {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val registration = latch.whenComplete { delivered = true }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val completion = executor.submit { latch.complete(emptyList()) }
            check(entered.await(5, TimeUnit.SECONDS))
            registration()
            release.countDown()
            completion.get(5, TimeUnit.SECONDS)
            assertThat(delivered).isFalse()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `M allow claimed callback to finish W cancelled during delivery`() {
        val latch = FirstFlagsLatch()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var delivered = false
        val registration = latch.whenComplete {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            delivered = true
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val completion = executor.submit { latch.complete(emptyList()) }
            check(entered.await(5, TimeUnit.SECONDS))
            registration()
            release.countDown()
            completion.get(5, TimeUnit.SECONDS)
            assertThat(delivered).isTrue()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `M retain immutable first keys W completed before subscription`() {
        val latch = FirstFlagsLatch()
        val input = mutableListOf("first")
        latch.complete(input)
        input.clear()
        latch.complete(listOf("later"))
        var delivered: List<String>? = null
        latch.whenComplete { delivered = it }
        assertThat(delivered).containsExactly("first")
        @Suppress("DontDowncastCollectionTypes") // Verify the returned Java collection cannot be mutated.
        val mutableView = delivered as MutableList
        assertThrows<UnsupportedOperationException> { mutableView.clear() }
    }

    @Test
    fun `M deliver outside lock W pending listener reenters from another thread`() {
        val latch = FirstFlagsLatch()
        val executor = Executors.newSingleThreadExecutor()
        try {
            latch.whenComplete {
                executor.submit {
                    latch.whenComplete { keys -> assertThat(keys).isEmpty() }
                }.get(5, TimeUnit.SECONDS)
            }
            latch.complete(emptyList())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M remain pending W no op repository`() {
        var delivered = false
        val registration = NoOpFlagsRepository().firstFlags().whenComplete { delivered = true }
        registration()
        assertThat(delivered).isFalse()
    }

    @Test
    fun `M distinguish empty installed keys from pending W completing empty configuration`() {
        val latch = FirstFlagsLatch()
        var delivered = false
        latch.whenComplete { keys ->
            assertThat(keys).isEmpty()
            delivered = true
        }
        assertThat(delivered).isFalse()
        latch.complete(emptyList())
        assertThat(delivered).isTrue()
    }

    @Test
    fun `M deliver exactly once W registrations race with completion`() {
        val latch = FirstFlagsLatch()
        val start = CountDownLatch(1)
        val delivered = ConcurrentLinkedQueue<Int>()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val registrations = (0 until 100).map { index ->
                executor.submit {
                    start.await()
                    latch.whenComplete { delivered.add(index) }
                }
            }
            start.countDown()
            latch.complete(listOf("first"))
            registrations.forEach { it.get(5, TimeUnit.SECONDS) }
            assertThat(delivered).containsExactlyInAnyOrderElementsOf((0 until 100).toList())
        } finally {
            executor.shutdownNow()
        }
    }
}
