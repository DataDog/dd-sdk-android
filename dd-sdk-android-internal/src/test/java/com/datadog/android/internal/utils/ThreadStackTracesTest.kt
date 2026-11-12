/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(ExtendWith(ForgeExtension::class))
@ForgeConfiguration(Configurator::class)
internal class ThreadStackTracesTest {

    private val errors = mutableListOf<Pair<String, Throwable>>()
    private val onError: StackTraceErrorHandler = { message, t -> errors.add(message to t) }

    private val fakeStack = Thread.currentThread().stackTrace

    @Test
    fun `M return all stack traces W safeGetAllStackTraces() {bulk call succeeds}`(
        @StringForgery fakeThreadName: String
    ) {
        // Given
        val expected = mapOf(Thread(fakeThreadName) to fakeStack)

        // When
        val result = safeGetAllStackTraces(
            onError = onError,
            bulkStackTracesProvider = { expected },
            liveThreadsProvider = { error("should not be called") },
            threadStackTraceProvider = { error("should not be called") }
        )

        // Then
        assertThat(result).isEqualTo(expected)
        assertThat(errors).isEmpty()
    }

    @Test
    fun `M keep other threads and report failures once W safeGetAllStackTraces() {bulk and threads throw}`(
        @StringForgery fakeGoodName: String,
        @StringForgery fakeBadName1: String,
        @StringForgery fakeBadName2: String,
        @StringForgery fakeBulkMessage: String,
        @StringForgery fakeThreadMessage: String
    ) {
        // Given
        val goodThread = Thread(fakeGoodName)
        val badThread1 = Thread(fakeBadName1)
        val badThread2 = Thread(fakeBadName2)
        val bulkError = IllegalStateException(fakeBulkMessage)
        val firstThreadError = SecurityException(fakeThreadMessage)

        // When
        val result = safeGetAllStackTraces(
            onError = onError,
            bulkStackTracesProvider = { throw bulkError },
            liveThreadsProvider = { listOf(badThread1, goodThread, badThread2) },
            threadStackTraceProvider = {
                when (it) {
                    // SecurityException and IllegalStateException are both recoverable runtime
                    // failures and must be aggregated together
                    badThread1 -> throw firstThreadError
                    badThread2 ->
                        @Suppress("UseCheckOrError")
                        throw IllegalStateException()
                    else -> fakeStack
                }
            }
        )

        // Then
        assertThat(result).containsOnlyKeys(goodThread)
        assertThat(result[goodThread]).containsExactly(*fakeStack)
        assertThat(errors).containsExactly(
            BULK_CAPTURE_FAILED to bulkError,
            THREAD_CAPTURE_FAILED to firstThreadError
        )
    }

    @Test
    fun `M return empty map W safeGetAllStackTraces() {both paths throw}`(
        @StringForgery fakeBulkMessage: String,
        @StringForgery fakeEnumerateMessage: String
    ) {
        // Given
        val bulkError = IllegalStateException(fakeBulkMessage)
        val enumerateError = SecurityException(fakeEnumerateMessage)

        // When
        val result = safeGetAllStackTraces(
            onError = onError,
            bulkStackTracesProvider = { throw bulkError },
            liveThreadsProvider = { throw enumerateError },
            threadStackTraceProvider = { error("should not be called") }
        )

        // Then
        assertThat(result).isEmpty()
        assertThat(errors).containsExactly(
            BULK_CAPTURE_FAILED to bulkError,
            ENUMERATE_FAILED to enumerateError
        )
    }

    @Test
    fun `M capture current thread W safeGetAllStackTraces() {real live thread enumeration}`(
        @StringForgery fakeBulkMessage: String
    ) {
        // Given
        val bulkError = IllegalStateException(fakeBulkMessage)

        // When
        val result = safeGetAllStackTraces(
            onError = onError,
            bulkStackTracesProvider = { throw bulkError },
            liveThreadsProvider = ::enumerateLiveThreads,
            threadStackTraceProvider = { it.stackTrace }
        )

        // Then
        assertThat(result).containsKey(Thread.currentThread())
        assertThat(result[Thread.currentThread()]).isNotEmpty()
    }

    @Test
    fun `M stop growing the buffer after 3 attempts W enumerateLiveThreads() {thread count keeps growing}`() {
        // Given
        val fakeGroup = object : ThreadGroup("fake") {
            var enumerateCalls = 0
            override fun activeCount(): Int = 5
            override fun enumerate(list: Array<Thread?>, recurse: Boolean): Int {
                enumerateCalls++
                return list.size // buffer always full: simulated unbounded thread growth
            }
        }

        // When
        val result = enumerateLiveThreads(fakeGroup)

        // Then
        assertThat(fakeGroup.enumerateCalls).isEqualTo(3)
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return captured threads W enumerateLiveThreads() {count fits in buffer}`(
        @StringForgery fakeThreadName: String
    ) {
        // Given
        val fakeThread = Thread(fakeThreadName)
        val fakeGroup = object : ThreadGroup("fake") {
            override fun activeCount(): Int = 1
            override fun enumerate(list: Array<Thread?>, recurse: Boolean): Int {
                list[0] = fakeThread
                return 1
            }
        }

        // When
        val result = enumerateLiveThreads(fakeGroup)

        // Then
        assertThat(result).containsExactly(fakeThread)
    }

    @Test
    fun `M propagate OutOfMemoryError W safeGetAllStackTraces() {thread capture runs out of memory}`(
        @StringForgery fakeBulkMessage: String,
        @StringForgery fakeOomMessage: String
    ) {
        // Given
        val bulkError = IllegalStateException(fakeBulkMessage)
        val oomError = OutOfMemoryError(fakeOomMessage)

        // When / Then
        assertThatThrownBy {
            safeGetAllStackTraces(
                onError = onError,
                bulkStackTracesProvider = { throw bulkError },
                liveThreadsProvider = { listOf(Thread.currentThread()) },
                threadStackTraceProvider = { throw oomError }
            )
        }.isInstanceOf(OutOfMemoryError::class.java)
            .isSameAs(oomError)
        assertThat(errors).containsExactly(BULK_CAPTURE_FAILED to bulkError)
    }

    @Test
    fun `M propagate OutOfMemoryError W safeGetAllStackTraces() {enumeration runs out of memory}`(
        @StringForgery fakeBulkMessage: String,
        @StringForgery fakeOomMessage: String
    ) {
        // Given
        val bulkError = IllegalStateException(fakeBulkMessage)
        val oomError = OutOfMemoryError(fakeOomMessage)

        // When / Then
        assertThatThrownBy {
            safeGetAllStackTraces(
                onError = onError,
                bulkStackTracesProvider = { throw bulkError },
                liveThreadsProvider = { throw oomError },
                threadStackTraceProvider = { error("should not be called") }
            )
        }.isInstanceOf(OutOfMemoryError::class.java)
            .isSameAs(oomError)
    }
}
