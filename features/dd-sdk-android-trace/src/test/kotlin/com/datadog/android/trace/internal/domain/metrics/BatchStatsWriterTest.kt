/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.trace.assertj.MsgPackAssert
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class BatchStatsWriterTest {

    private lateinit var testedWriter: BatchStatsWriter

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @LongForgery(min = 0L)
    var fakeBucketStart: Long = 0L

    @LongForgery(min = 1L)
    var fakeBucketDuration: Long = 0L

    @StringForgery
    lateinit var fakeRuntimeID: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)) doReturn mockFeatureScope
        whenever(mockFeatureScope.withWriteContext(eq(emptySet()), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }

        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID)
    }

    // region write

    @Test
    fun `M write payload to batch writer W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        verify(mockEventBatchWriter).write(any(), eq(null), eq(EventType.DEFAULT))
    }

    @Test
    fun `M encode context env in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(captor.firstValue.data).hasField("Env", fakeDatadogContext.env)
    }

    @Test
    fun `M encode context version in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(captor.firstValue.data).hasField("Version", fakeDatadogContext.version)
    }

    @Test
    fun `M encode context service in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(captor.firstValue.data).hasField("Service", fakeDatadogContext.service)
    }

    @Test
    fun `M encode context sdkVersion as tracer version in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(captor.firstValue.data).hasField("TracerVersion", fakeDatadogContext.sdkVersion)
    }

    @Test
    fun `M use sequence number 0 on first call W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(captor.firstValue.data).hasField("Sequence", 0L)
    }

    @Test
    fun `M increment sequence number on each call W write() { multiple calls }`(
        @IntForgery(min = 2, max = 10) callCount: Int
    ) {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        repeat(callCount) { testedWriter.write(fakeBuckets) }

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(callCount)).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        captor.allValues.forEachIndexed { index, event ->
            MsgPackAssert.assertThat(event.data).hasField("Sequence", index.toLong())
        }
    }

    @Test
    fun `M encode runtimeID in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(captor.firstValue.data).hasField("RuntimeID", fakeRuntimeID)
    }

    @Test
    fun `M do nothing W write() { feature not registered }`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)) doReturn null
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets)

        // Then
        verify(mockEventBatchWriter, never()).write(any(), any(), any())
    }

    // endregion
}
