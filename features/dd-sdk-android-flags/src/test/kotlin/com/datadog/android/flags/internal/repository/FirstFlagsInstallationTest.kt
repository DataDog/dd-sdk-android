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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class FirstFlagsInstallationTest {
    private val core = mock<FeatureSdkCore>()
    private val logger = mock<InternalLogger>()
    private val store = mock<DataStoreHandler>()
    private val context = EvaluationContext("user", emptyMap())
    private val events = CopyOnWriteArrayList<List<String>>()
    private lateinit var disk: DataStoreReadCallback<FlagsStateEntry>

    private fun repository(): DefaultFlagsRepository {
        whenever(core.internalLogger).thenReturn(logger)
        whenever(core.timeProvider).thenReturn(mock())
        doAnswer {
            disk = it.getArgument(2)
            null
        }.whenever(store).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        return DefaultFlagsRepository(core, "first", store, persistenceLoadTimeoutMs = 1).also {
            it.waitForFlags().whenComplete { keys -> events.add(keys) }
        }
    }

    private fun restored(keys: List<String>): DataStoreContent<FlagsStateEntry> = DataStoreContent(
        1,
        FlagsStateEntry(context, keys.associateWith { mock<PrecomputedFlag>() }, 0)
    )

    @Test
    fun `M notify disk first W network subsequently installs`() {
        val tested = repository()
        disk.onSuccess(restored(emptyList()))
        tested.setFlagsAndContext(context, mapOf("network" to mock()))
        assertThat(events).hasSize(1)
        assertThat(events.single()).isEmpty()
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys("network")
    }

    @Test
    fun `M retain cache winner W network replaces before cache completion`() {
        val tested = repository()
        val claimed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val flags = object : Map<String, PrecomputedFlag> by mapOf("cache" to mock<PrecomputedFlag>()) {
            override val keys: Set<String>
                get() {
                    claimed.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return setOf("cache")
                }
        }
        val loading = thread {
            disk.onSuccess(DataStoreContent(1, FlagsStateEntry(context, flags, 0)))
        }
        try {
            assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue()
            tested.setFlagsAndContext(context, mapOf("network" to mock()))
            assertThat(tested.waitForFlags().isDone).isFalse()
            assertThat(events).isEmpty()
        } finally {
            release.countDown()
            loading.join(5000)
        }
        assertThat(loading.isAlive).isFalse()
        assertThat(events.single()).containsExactly("cache")
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys("network")
    }

    @Test
    fun `M reject late cache W network installs first`() {
        val tested = repository()
        tested.setFlagsAndContext(context, mapOf("network" to mock()))
        disk.onSuccess(restored(listOf("cache")))
        assertThat(events.single()).containsExactly("network")
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys("network")
    }

    @Test
    fun `M allow network first W missing or failed cache`() {
        listOf(true, false).forEach { missing ->
            events.clear()
            val tested = repository()
            if (missing) disk.onSuccess(null) else disk.onFailure()
            assertThat(events).isEmpty()
            assertThat(tested.waitForFlags().isDone).isFalse()
            assertThat(tested.getFlagsSnapshot()).isEmpty()
            tested.setFlagsAndContext(context, emptyMap())
            assertThat(events).hasSize(1)
            assertThat(events.single()).isEmpty()
        }
    }

    @Test
    fun `M notify accepted first install W persistence submission throws`() {
        val tested = repository()
        doThrow(IllegalStateException("storage unavailable")).whenever(store)
            .setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())
        assertThrows<IllegalStateException> { tested.setFlagsAndContext(context, emptyMap()) }
        assertThat(events).hasSize(1)
        assertThat(events.single()).isEmpty()
        assertThat(tested.getEvaluationContext()).isEqualTo(context)
    }

    @Test
    fun `M claim exactly one installation W disk races network`() {
        repeat(20) {
            events.clear()
            val tested = repository()
            val start = CountDownLatch(1)
            val diskThread = thread { check(start.await(5, TimeUnit.SECONDS)); disk.onSuccess(restored(emptyList())) }
            val networkThread = thread {
                check(start.await(5, TimeUnit.SECONDS))
                tested.setFlagsAndContext(context, mapOf("network" to mock()))
            }
            start.countDown()
            diskThread.join(5000)
            networkThread.join(5000)
            assertThat(diskThread.isAlive || networkThread.isAlive).isFalse()
            assertThat(events).hasSize(1)
            assertThat(events.single()).isIn(emptyList<String>(), listOf("network"))
            assertThat(tested.getFlagsSnapshot()).containsOnlyKeys("network")
        }
    }
}
