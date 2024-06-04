/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TimestampOffsetProviderTest {

    lateinit var testedProvider: TimestampOffsetProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @StringForgery(regex = "[a-z0-9]{32}")
    lateinit var fakeViewId: String

    @BeforeEach
    fun `set up`() {
        testedProvider = TimestampOffsetProvider(mockInternalLogger)
    }

    @Test
    fun `M return current server offset W getTimeOffset`() {
        // When
        val fakeOffset = testedProvider.getOffset(fakeViewId, fakeDatadogContext)

        // Then
        assertThat(fakeOffset).isEqualTo(fakeDatadogContext.time.serverTimeOffsetMs)
    }

    @Test
    fun `M be consistent W getTimeOffset { same view Id }`(forge: Forge) {
        // Given
        val fakeCounts = forge.anInt(min = 5, max = 10)
        val fakeOffset = testedProvider.getOffset(fakeViewId, fakeDatadogContext)

        // Then
        assertThat(fakeOffset).isEqualTo(fakeDatadogContext.time.serverTimeOffsetMs)
        repeat(fakeCounts) {
            val newOffset = testedProvider.getOffset(fakeViewId, forge.getForgery())
            assertThat(newOffset).isEqualTo(fakeOffset)
        }
    }

    @Test
    fun `M be consistent W getTimeOffset { same view Id, concurrent }`(forge: Forge) {
        // Given
        val fakeCounts = forge.anInt(min = 5, max = 10)
        val fakeOffset = testedProvider.getOffset(fakeViewId, fakeDatadogContext)

        // Then
        assertThat(fakeOffset).isEqualTo(fakeDatadogContext.time.serverTimeOffsetMs)
        val countDownLatch = CountDownLatch(fakeCounts)
        repeat(fakeCounts) {
            Thread {
                val newOffset = testedProvider.getOffset(fakeViewId, forge.getForgery())
                assertThat(newOffset).isEqualTo(fakeOffset)
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await(10000, TimeUnit.MILLISECONDS)
        assertThat(testedProvider.offsets.entries).hasSize(1)
    }

    @Test
    fun `M return the new offset W getTimeOffset { different view Ids }`(forge: Forge) {
        // Given
        val fakeCounts = forge.anInt(min = 5, max = 10)
        val fakeOffset = testedProvider.getOffset(fakeViewId, fakeDatadogContext)

        // Then
        assertThat(fakeOffset).isEqualTo(fakeDatadogContext.time.serverTimeOffsetMs)
        repeat(fakeCounts) {
            val fakeNewContext = forge.getForgery<DatadogContext>()
            val fakeNewId = forge.aStringMatching("[a-z0-9]{32}")
            val newOffset = testedProvider.getOffset(fakeNewId, fakeNewContext)
            assertThat(newOffset).isEqualTo(fakeNewContext.time.serverTimeOffsetMs)
        }
    }

    @Test
    fun `M purge the last used view W consume{ consecutive different views }`(forge: Forge) {
        // Given
        val size = forge.anInt(min = 3, max = 10)
        val fakeContexts = forge.aList(size) {
            forge.getForgery<DatadogContext>()
        }
        val fakeViewIds = forge.aList(size) {
            forge.aStringMatching("[a-z0-9]{32}")
        }

        val expectedCachedOffsets = LinkedHashMap<String, Long>()
        val expectedCachedOffsetsKeys =
            fakeViewIds
                .takeLast(TimestampOffsetProvider.MAX_VIEW_TIME_OFFSETS_RETAIN)
        val expectedCachedOffsetsValues =
            fakeContexts
                .takeLast(TimestampOffsetProvider.MAX_VIEW_TIME_OFFSETS_RETAIN)
                .map { it.time.serverTimeOffsetMs }
        expectedCachedOffsetsKeys.forEachIndexed { index, key ->
            expectedCachedOffsets[key] = expectedCachedOffsetsValues[index]
        }
        val expectedOffsets = fakeContexts.map { it.time.serverTimeOffsetMs }

        // When
        val returnedOffsets = mutableListOf<Long>()
        repeat(size) {
            val fakeNewContext = fakeContexts[it]
            val fakeNewId = fakeViewIds[it]
            returnedOffsets.add(testedProvider.getOffset(fakeNewId, fakeNewContext))
        }

        // Then
        assertThat(testedProvider.offsets.entries)
            .containsExactlyElementsOf(expectedCachedOffsets.entries)
        assertThat(returnedOffsets).containsExactlyElementsOf(expectedOffsets)
    }

    @Test
    fun `M purge the last used view W consume{ consecutive different views, concurrent }`(
        forge: Forge
    ) {
        // Given
        val size = forge.anInt(min = 3, max = 10)
        val fakeContexts = forge.aList(size) {
            forge.getForgery<DatadogContext>()
        }
        val fakeViewIds = forge.aList(size) {
            forge.aStringMatching("[a-z0-9]{32}")
        }

        val expectedCachedOffsets = LinkedHashMap<String, Long>()
        val expectedCachedOffsetsKeys =
            fakeViewIds
                .takeLast(TimestampOffsetProvider.MAX_VIEW_TIME_OFFSETS_RETAIN)
        val expectedCachedOffsetsValues =
            fakeContexts
                .takeLast(TimestampOffsetProvider.MAX_VIEW_TIME_OFFSETS_RETAIN)
                .map { it.time.serverTimeOffsetMs }
        expectedCachedOffsetsKeys.forEachIndexed { index, key ->
            expectedCachedOffsets[key] = expectedCachedOffsetsValues[index]
        }
        val expectedOffsets = fakeContexts.map { it.time.serverTimeOffsetMs }

        // When
        val countDownLatch = CountDownLatch(size)
        val returnedOffsets = LinkedList<Long>()
        repeat(size) {
            val fakeNewContext = fakeContexts[it]
            val fakeNewId = fakeViewIds[it]
            Thread {
                synchronized(returnedOffsets) {
                    returnedOffsets.add(testedProvider.getOffset(fakeNewId, fakeNewContext))
                }
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(10000, TimeUnit.MILLISECONDS)
        assertThat(testedProvider.offsets.entries.size)
            .isEqualTo(TimestampOffsetProvider.MAX_VIEW_TIME_OFFSETS_RETAIN)
        assertThat(returnedOffsets).containsExactlyInAnyOrder(*expectedOffsets.toTypedArray())
    }
}
