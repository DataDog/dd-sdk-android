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
    fun `M retain immutable first keys W completed before subscription`() {
        val latch = FirstFlagsLatch()
        val input = mutableListOf("first")
        latch.complete(input)
        input.clear()
        latch.complete(listOf("later"))
        var delivered: List<String>? = null
        latch.whenComplete { delivered = it }
        assertThat(delivered).containsExactly("first")
        assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isTrue()
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
            assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isTrue()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M remain pending W timeout`() {
        val latch = FirstFlagsLatch()
        assertThat(latch.await(1, TimeUnit.MILLISECONDS)).isFalse()
        assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isFalse()
    }

    @Test
    fun `M remain pending W no op repository`() {
        assertThat(NoOpFlagsRepository().waitForFlags().await(0, TimeUnit.MILLISECONDS)).isFalse()
    }

    @Test
    fun `M distinguish empty installed keys from pending W completing empty configuration`() {
        val latch = FirstFlagsLatch()
        var delivered = false
        latch.whenComplete { keys ->
            assertThat(keys).isEmpty()
            delivered = true
        }
        assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isFalse()
        assertThat(delivered).isFalse()
        latch.complete(emptyList())
        assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isTrue()
        assertThat(delivered).isTrue()
        assertThat(latch.await(0, TimeUnit.NANOSECONDS)).isTrue()
    }

    @Test
    fun `M propagate interruption W wait is interrupted`() {
        val latch = FirstFlagsLatch()
        try {
            Thread.currentThread().interrupt()
            assertThrows<InterruptedException> { latch.await(1, TimeUnit.SECONDS) }
            assertThat(Thread.currentThread().isInterrupted).isFalse()
            assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isFalse()
        } finally {
            Thread.interrupted()
        }
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
