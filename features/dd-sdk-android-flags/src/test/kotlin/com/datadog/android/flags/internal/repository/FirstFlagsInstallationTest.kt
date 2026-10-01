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
import com.datadog.android.flags.FirstFlagsCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.internal.FirstFlagsObserver
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
    private val events = CopyOnWriteArrayList<FlagsClientEvent>()
    private lateinit var disk: DataStoreReadCallback<FlagsStateEntry>

    private fun repository(): DefaultFlagsRepository {
        whenever(core.internalLogger).thenReturn(logger)
        whenever(core.timeProvider).thenReturn(mock())
        doAnswer {
            disk = it.getArgument(2)
            null
        }.whenever(store).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        val observer = FirstFlagsObserver(FirstFlagsCallback { _, event -> events.add(event) }, logger)
        observer.attachClient(mock<FlagsClient>())
        return DefaultFlagsRepository(core, "first", store, firstFlagsObserver = observer)
    }

    private fun restored(keys: List<String>): DataStoreContent<FlagsStateEntry> = DataStoreContent(
        1,
        FlagsStateEntry(context, keys.associateWith { mock<PrecomputedFlag>() }, 0)
    )

    @Test
    fun `M notify disk first W network subsequently installs`() {
        val tested = repository()
        disk.onSuccess(restored(emptyList()))
        tested.setFlagsAndContext(context, mapOf("network" to mock()))?.invoke()
        assertThat(events).hasSize(1)
        assertThat(events.single().flagsChanged).isEmpty()
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys("network")
    }

    @Test
    fun `M retain network winner W disk arrives before network notification`() {
        val tested = repository()
        val release = checkNotNull(tested.setFlagsAndContext(context, mapOf("network" to mock())))
        disk.onSuccess(restored(emptyList()))
        assertThat(events).isEmpty()
        tested.setFlagsAndContext(context, mapOf("later" to mock()))?.invoke()
        release()
        assertThat(events.single().flagsChanged).containsExactly("network")
        assertThat(tested.getFlagsSnapshot()).containsOnlyKeys("later")
    }

    @Test
    fun `M allow network first W missing or failed cache`() {
        listOf(true, false).forEach { missing ->
            events.clear()
            val tested = repository()
            if (missing) disk.onSuccess(null) else disk.onFailure()
            assertThat(events).isEmpty()
            tested.setFlagsAndContext(context, emptyMap())?.invoke()
            assertThat(events).hasSize(1)
            assertThat(events.single().flagsChanged).isEmpty()
        }
    }

    @Test
    fun `M retain accepted callback W persistence submission throws`() {
        val tested = repository()
        doThrow(IllegalStateException("storage unavailable")).whenever(store)
            .setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())
        tested.setFlagsAndContext(context, emptyMap())?.invoke()
        assertThat(events).hasSize(1)
        assertThat(events.single().flagsChanged).isEmpty()
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
                tested.setFlagsAndContext(context, mapOf("network" to mock()))?.invoke()
            }
            start.countDown()
            diskThread.join(5000)
            networkThread.join(5000)
            assertThat(diskThread.isAlive || networkThread.isAlive).isFalse()
            assertThat(events).hasSize(1)
            assertThat(events.single().flagsChanged).isIn(emptyList<String>(), listOf("network"))
            assertThat(tested.getFlagsSnapshot()).containsOnlyKeys("network")
        }
    }
}
