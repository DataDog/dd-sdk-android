/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ExtendWith(ForgeExtension::class)
internal class FirstFlagsInstallationTest {
    @StringForgery
    lateinit var fakeNetworkKey: String

    @StringForgery
    lateinit var fakeCacheKey: String

    private val core = mock<FeatureSdkCore>()
    private val logger = mock<InternalLogger>()
    private val store = mock<DataStoreHandler>()
    private val context = EvaluationContext("user", emptyMap())
    private val events = CopyOnWriteArrayList<List<String>>()
    private lateinit var disk: DataStoreReadCallback<FlagsStateEntry>

    @Test
    fun `M not notify rejected cache W disk completes during network bookkeeping`() {
        // Given
        val tested = repository()
        val inBookkeeping = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val networkFlags = mapOf(fakeNetworkKey to mock<PrecomputedFlag>())
        val cachedState = restored(listOf(fakeCacheKey))
        val executor = Executors.newSingleThreadExecutor()
        try {
            // When
            val installation = executor.submit {
                tested.setFlagsAndContext(context, networkFlags) {
                    inBookkeeping.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                }
            }

            // Then
            assertThat(inBookkeeping.await(5, TimeUnit.SECONDS)).isTrue()
            disk.onSuccess(cachedState)
            assertThat(events).isEmpty()
            finish.countDown()
            installation.get(5, TimeUnit.SECONDS)
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
        assertThat(events.single()).containsExactly(fakeNetworkKey)
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys(fakeNetworkKey)
    }

    @Test
    fun `M read cached state without waiting W first flags listener runs on disk worker`() {
        // Given
        val tested = repository(TimeUnit.MINUTES.toMillis(1))
        val observed = CopyOnWriteArrayList<EvaluationContext?>()
        tested.firstFlags.whenComplete { observed.add(tested.getEvaluationContext()) }
        val cachedState = restored(listOf(fakeCacheKey))
        val executor = Executors.newSingleThreadExecutor()
        try {
            // When
            executor.submit { disk.onSuccess(cachedState) }.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        // Then
        assertThat(observed).containsExactly(context)
        assertThat(events.single()).containsExactly(fakeCacheKey)
    }

    @Test
    fun `M read installed state without waiting W first flags listener runs on network worker`() {
        // Given
        val tested = repository(TimeUnit.MINUTES.toMillis(1))
        val observed = CopyOnWriteArrayList<Set<String>>()
        tested.firstFlags.whenComplete { observed.add(tested.getFlagsSnapshot().keys) }
        val networkFlags = mapOf(fakeNetworkKey to mock<PrecomputedFlag>())
        val executor = Executors.newSingleThreadExecutor()
        try {
            // When
            executor.submit { tested.setFlagsAndContext(context, networkFlags) }.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        // Then
        assertThat(observed.single()).containsExactly(fakeNetworkKey)
    }

    @Test
    fun `M propagate terminal failure W storage submission also throws`() {
        // Given
        listOf(false, true).forEach { sameError ->
            events.clear()
            val tested = repository()
            val storageError = IllegalStateException("storage unavailable")
            val terminalError = if (sameError) storageError else IllegalStateException("terminal failure")
            doThrow(storageError).whenever(store)
                .setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())
            var terminalCalls = 0

            // When
            val thrown = assertThrows<IllegalStateException> {
                tested.setFlagsAndContext(context, emptyMap()) {
                    terminalCalls++
                    throw terminalError
                }
            }

            // Then
            assertThat(thrown).isSameAs(terminalError)
            assertThat(terminalCalls).isEqualTo(1)
            assertThat(thrown.suppressed).isEmpty()
            assertThat(events).hasSize(1)
        }
    }

    @Test
    fun `M wait for terminal bookkeeping W listener registers during installation`() {
        // Given
        val tested = repository()
        val installed = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            // When
            val installation = executor.submit {
                tested.setFlagsAndContext(context, emptyMap()) {
                    installed.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                }
            }

            // Then
            assertThat(installed.await(5, TimeUnit.SECONDS)).isTrue()
            tested.firstFlags.whenComplete { delivered.countDown() }
            assertThat(delivered.count).isEqualTo(1)
            assertThat(events).isEmpty()
            finish.countDown()
            installation.get(5, TimeUnit.SECONDS)
            assertThat(delivered.count).isZero()
            assertThat(events).hasSize(1)
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `M retain accepted event and propagate failure W terminal bookkeeping throws`() {
        // Given
        val tested = repository()

        // When
        assertThrows<IllegalStateException> {
            tested.setFlagsAndContext(context, emptyMap()) { error("terminal failure") }
        }

        // Then
        assertThat(events).hasSize(1)
        assertThat(tested.getEvaluationContext()).isEqualTo(context)
    }

    @Test
    fun `M unblock getters W disk attempt ends without flags`() {
        // Given
        listOf(true, false).forEach { missing ->
            val tested = repository(TimeUnit.MINUTES.toMillis(1))
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                // When
                val reads = executor.submit {
                    // Then
                    assertThat(tested.getPrecomputedFlag("unknown")).isNull()
                    assertThat(tested.getEvaluationContext()).isNull()
                    assertThat(tested.getFlagsSnapshot()).isEmpty()
                    assertThat(tested.hasFlags()).isFalse()
                    assertThat(tested.getPrecomputedFlagWithContext("unknown")).isNull()
                }
                if (missing) disk.onSuccess(null) else disk.onFailure()
                reads.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `M notify disk first W network subsequently installs`() {
        // Given
        val tested = repository()

        // When
        disk.onSuccess(restored(emptyList()))
        tested.setFlagsAndContext(context, mapOf(fakeNetworkKey to mock()))

        // Then
        assertThat(events).hasSize(1)
        assertThat(events.single()).isEmpty()
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys(fakeNetworkKey)
    }

    @Test
    fun `M retain cache winner W network replaces before cache completion`() {
        // Given
        val tested = repository()
        val claimed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val flags = object : Map<String, PrecomputedFlag> by mapOf(fakeCacheKey to mock<PrecomputedFlag>()) {
            override val keys: Set<String>
                get() {
                    claimed.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return setOf(fakeCacheKey)
                }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            // When
            val loading = executor.submit {
                disk.onSuccess(DataStoreContent(1, FlagsStateEntry(context, flags, 0)))
            }

            // Then
            assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue()
            tested.setFlagsAndContext(context, mapOf(fakeNetworkKey to mock()))
            assertThat(events).isEmpty()
            release.countDown()
            loading.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
        assertThat(events.single()).containsExactly(fakeCacheKey)
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys(fakeNetworkKey)
    }

    @Test
    fun `M reject late cache W network installs first`() {
        // Given
        val tested = repository()

        // When
        tested.setFlagsAndContext(context, mapOf(fakeNetworkKey to mock()))
        disk.onSuccess(restored(listOf(fakeCacheKey)))

        // Then
        assertThat(events.single()).containsExactly(fakeNetworkKey)
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys(fakeNetworkKey)
    }

    @Test
    fun `M allow network first W missing or failed cache`() {
        // Given
        listOf(true, false).forEach { missing ->
            events.clear()
            val tested = repository()

            // When
            if (missing) disk.onSuccess(null) else disk.onFailure()

            // Then
            assertThat(events).isEmpty()
            assertThat(tested.getFlagsSnapshot()).isEmpty()
            tested.setFlagsAndContext(context, emptyMap())
            assertThat(events).hasSize(1)
            assertThat(events.single()).isEmpty()
        }
    }

    @Test
    fun `M notify accepted first install W persistence submission throws`() {
        // Given
        val tested = repository()
        doThrow(IllegalStateException("storage unavailable")).whenever(store)
            .setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())

        // When
        tested.setFlagsAndContext(context, emptyMap())

        // Then
        assertThat(events).hasSize(1)
        assertThat(events.single()).isEmpty()
        assertThat(tested.getEvaluationContext()).isEqualTo(context)
        verify(logger).log(
            eq(InternalLogger.Level.ERROR),
            eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
            any(),
            isA<IllegalStateException>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M claim exactly one installation W disk races network`() {
        // Given
        repeat(20) {
            events.clear()
            val tested = repository()
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                // When
                val diskWorker = executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    disk.onSuccess(restored(emptyList()))
                }
                val networkWorker = executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    tested.setFlagsAndContext(context, mapOf(fakeNetworkKey to mock()))
                }
                start.countDown()
                diskWorker.get(5, TimeUnit.SECONDS)
                networkWorker.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }

            // Then
            assertThat(events).hasSize(1)
            assertThat(events.single()).isIn(emptyList<String>(), listOf(fakeNetworkKey))
            assertThat(tested.getFlagsSnapshot()).containsOnlyKeys(fakeNetworkKey)
        }
    }

    private fun repository(timeoutMs: Long = 1): DefaultFlagsRepository {
        whenever(core.internalLogger).thenReturn(logger)
        whenever(core.timeProvider).thenReturn(mock())
        doAnswer {
            disk = it.getArgument(2)
            null
        }.whenever(store).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        return DefaultFlagsRepository(core, "first", store, persistenceLoadTimeoutMs = timeoutMs).also {
            it.firstFlags.whenComplete { keys -> events.add(keys) }
        }
    }

    private fun restored(keys: List<String>): DataStoreContent<FlagsStateEntry> = DataStoreContent(
        1,
        FlagsStateEntry(context, keys.associateWith { mock<PrecomputedFlag>() }, 0)
    )
}
