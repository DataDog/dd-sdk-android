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
        // Given
        val latch = FirstFlagsLatch()
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<List<String>>()
        val unsubscribe = latch.whenComplete { events.add(it) }
        latch.complete(listOf("first")) { queued.add(it) }

        // When
        unsubscribe()
        queued.single().run()

        // Then
        assertThat(events).isEmpty()
        latch.whenComplete { events.add(it) }
        assertThat(events).containsExactly(listOf("first"))
    }

    @Test
    fun `M preserve first snapshot W network updates before queued delivery`() {
        // Given
        val latch = FirstFlagsLatch()
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<List<String>>()
        latch.whenComplete { events.add(it) }

        // When
        latch.complete(listOf("cache")) { queued.add(it) }
        latch.complete(listOf("network")) { queued.add(it) }

        // Then
        assertThat(events).isEmpty()
        queued.single().run()
        assertThat(events).containsExactly(listOf("cache"))
    }

    @Test
    fun `M skip dispatcher W no pending listeners`() {
        // Given
        val latch = FirstFlagsLatch()
        var dispatched = false

        // When
        latch.complete(emptyList()) { dispatched = true }
        var replayed = false
        latch.whenComplete { replayed = true }

        // Then
        assertThat(dispatched).isFalse()
        assertThat(replayed).isTrue()
    }

    @Test
    fun `M remove only cancelled listener W repeated cancellation`() {
        // Given
        val latch = FirstFlagsLatch()
        val delivered = mutableListOf<String>()
        val cancelled = latch.whenComplete { delivered.add("cancelled") }
        latch.whenComplete { delivered.add("active") }

        // When
        cancelled()
        cancelled()
        latch.complete(listOf("first"))
        val late = latch.whenComplete { delivered.add("late") }
        late()

        // Then
        assertThat(delivered).containsExactly("active", "late")
    }

    @Test
    fun `M cancel unclaimed callback W completion has captured listeners`() {
        // Given
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
            // When
            val completion = executor.submit { latch.complete(emptyList()) }
            check(entered.await(5, TimeUnit.SECONDS))
            registration()
            release.countDown()
            completion.get(5, TimeUnit.SECONDS)

            // Then
            assertThat(delivered).isFalse()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `M allow claimed callback to finish W cancelled during delivery`() {
        // Given
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
            // When
            val completion = executor.submit { latch.complete(emptyList()) }
            check(entered.await(5, TimeUnit.SECONDS))
            registration()
            release.countDown()
            completion.get(5, TimeUnit.SECONDS)

            // Then
            assertThat(delivered).isTrue()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `M retain immutable first keys W completed before subscription`() {
        // Given
        val latch = FirstFlagsLatch()
        val input = mutableListOf("first")

        // When
        latch.complete(input)
        input.clear()
        latch.complete(listOf("later"))
        var delivered: List<String>? = null
        latch.whenComplete { delivered = it }

        // Then
        assertThat(delivered).containsExactly("first")
        @Suppress("DontDowncastCollectionTypes") // Verify the returned Java collection cannot be mutated.
        val mutableView = delivered as MutableList
        assertThrows<UnsupportedOperationException> { mutableView.clear() }
    }

    @Test
    fun `M deliver outside lock W pending listener reenters from another thread`() {
        // Given
        val latch = FirstFlagsLatch()
        val executor = Executors.newSingleThreadExecutor()
        try {
            latch.whenComplete {
                executor.submit {
                    latch.whenComplete { keys -> assertThat(keys).isEmpty() }
                }.get(5, TimeUnit.SECONDS)
            }

            // When
            latch.complete(emptyList())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M remain pending W no op repository`() {
        // Given
        var delivered = false
        val registration = NoOpFlagsRepository().firstFlags.whenComplete { delivered = true }

        // When
        registration()

        // Then
        assertThat(delivered).isFalse()
    }

    @Test
    fun `M distinguish empty installed keys from pending W completing empty configuration`() {
        // Given
        val latch = FirstFlagsLatch()
        var delivered = false
        latch.whenComplete { keys ->
            assertThat(keys).isEmpty()
            delivered = true
        }
        assertThat(delivered).isFalse()

        // When
        latch.complete(emptyList())

        // Then
        assertThat(delivered).isTrue()
    }

    @Test
    fun `M deliver exactly once W registrations race with completion`() {
        // Given
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

            // When
            start.countDown()
            latch.complete(listOf("first"))
            registrations.forEach { it.get(5, TimeUnit.SECONDS) }

            // Then
            assertThat(delivered).containsExactlyInAnyOrderElementsOf((0 until 100).toList())
        } finally {
            executor.shutdownNow()
        }
    }
}
