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
import java.util.concurrent.TimeoutException

internal class FirstFlagsFutureTest {
    @Test
    fun `M retain immutable first keys W completed before subscription`() {
        val future = FirstFlagsFuture()
        val input = mutableListOf("first")
        future.complete(input)
        input.clear()
        future.complete(listOf("later"))
        var delivered: List<String>? = null
        future.whenComplete { delivered = it }
        assertThat(delivered).containsExactly("first")
        assertThat(future.get()).isSameAs(delivered)
        @Suppress("DontDowncastCollectionTypes") // Verify the returned Java collection cannot be mutated.
        val mutableView = future.get() as MutableList
        assertThrows<UnsupportedOperationException> { mutableView.clear() }
    }

    @Test
    fun `M deliver outside lock W pending listener reenters from another thread`() {
        val future = FirstFlagsFuture()
        val executor = Executors.newSingleThreadExecutor()
        try {
            future.whenComplete {
                executor.submit {
                    future.whenComplete { keys -> assertThat(keys).isEmpty() }
                }.get(5, TimeUnit.SECONDS)
            }
            future.complete(emptyList())
            assertThat(future.isDone).isTrue()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M remain pending W timeout and cancellation`() {
        val future = FirstFlagsFuture()
        assertThat(future.cancel(true)).isFalse()
        assertThat(future.isCancelled).isFalse()
        assertThrows<TimeoutException> { future.get(1, TimeUnit.MILLISECONDS) }
        assertThat(future.isDone).isFalse()
    }

    @Test
    fun `M remain pending W generated no op repository`() {
        assertThat(NoOpFlagsRepository().waitForFlags().isDone).isFalse()
    }

    @Test
    fun `M distinguish empty installed keys from pending W completing empty configuration`() {
        val future = FirstFlagsFuture()
        var delivered = false
        future.whenComplete { keys ->
            assertThat(keys).isEmpty()
            delivered = true
        }
        assertThat(future.isDone).isFalse()
        assertThat(delivered).isFalse()
        future.complete(emptyList())
        assertThat(future.isDone).isTrue()
        assertThat(delivered).isTrue()
        assertThat(future.get(0, TimeUnit.NANOSECONDS)).isEmpty()
    }

    @Test
    fun `M propagate interruption W either get overload is interrupted`() {
        listOf(false, true).forEach { timed ->
            val future = FirstFlagsFuture()
            try {
                Thread.currentThread().interrupt()
                assertThrows<InterruptedException> {
                    if (timed) future.get(1, TimeUnit.SECONDS) else future.get()
                }
                assertThat(Thread.currentThread().isInterrupted).isFalse()
                assertThat(future.isDone).isFalse()
            } finally {
                Thread.interrupted()
            }
        }
    }

    @Test
    fun `M deliver exactly once W registrations race with completion`() {
        val future = FirstFlagsFuture()
        val start = CountDownLatch(1)
        val delivered = ConcurrentLinkedQueue<Int>()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val registrations = (0 until 100).map { index ->
                executor.submit {
                    start.await()
                    future.whenComplete { delivered.add(index) }
                }
            }
            start.countDown()
            future.complete(listOf("first"))
            registrations.forEach { it.get(5, TimeUnit.SECONDS) }
            assertThat(delivered).containsExactlyInAnyOrderElementsOf((0 until 100).toList())
        } finally {
            executor.shutdownNow()
        }
    }
}
