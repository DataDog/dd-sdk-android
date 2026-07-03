/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.datadog.android.rum.internal.timeseries.serializer.TimeseriesAttributes
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.forge.aThrowable
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PipelineTest {

    @Mock
    lateinit var mockReader: DataPointsReader<Double>

    @Mock
    lateinit var mockSerializer: JsonSerializer<Double>

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockDataWriter: DataWriter<Any>

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockInsightsCollector: InsightsCollector

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @IntForgery(min = 2, max = 10)
    var fakeBufferSize: Int = 0

    private lateinit var buffer: Buffer<Double>
    private lateinit var testedPipeline: Pipeline<Double>

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
                .invoke(mockDatadogContext, mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            it.getArgument<(EventBatchWriter) -> Unit>(0).invoke(mockEventBatchWriter)
        }
        whenever(mockDataWriter.write(any(), any(), any())) doReturn true

        buffer = Buffer(fakeBufferSize)
        testedPipeline = Pipeline(
            sdkCore = mockSdkCore,
            reader = mockReader,
            buffer = buffer,
            serializer = mockSerializer,
            dataWriter = mockDataWriter,
            internalLogger = mockInternalLogger,
            insightsCollector = mockInsightsCollector
        )
    }

    @Test
    fun `M expose reader intervalMs W intervalMs`(@LongForgery(min = 1L) fakeIntervalMs: Long) {
        // Given
        whenever(mockReader.intervalMs) doReturn fakeIntervalMs

        // When / Then
        assertThat(testedPipeline.intervalMs).isEqualTo(fakeIntervalMs)
    }

    // region execute()

    @Test
    fun `M append sample to buffer W execute()`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        whenever(mockReader.read()) doReturn fakePoint

        // When
        testedPipeline.execute()

        // Then
        assertThat(buffer.drain()).containsExactly(fakePoint)
    }

    @Test
    fun `M not add to buffer W execute() { reader returns null }`() {
        // Given
        whenever(mockReader.read()) doReturn null

        // When
        testedPipeline.execute()

        // Then
        assertThat(buffer.drain()).isEmpty()
    }

    // endregion

    // region flush()

    @Test
    fun `M flush W execute() { buffer fills to capacity }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeTimeseriesName = "view.cpu"
        val fakeJson = fakeTimeseriesJson(fakeTimeseriesName)
        whenever(mockReader.read()) doReturn fakePoint
        whenever(mockSerializer.serialize(any(), any())) doReturn fakeJson
        repeat(fakeBufferSize - 1) { testedPipeline.execute() }

        // When
        testedPipeline.execute()

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verify(mockInsightsCollector).onTimeseries(fakeTimeseriesName)
        assertThat(buffer.drain()).isEmpty()
    }

    @Test
    fun `M not flush W execute() { buffer not full }`(forge: Forge) {
        // Given
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()

        // When
        repeat(fakeBufferSize - 1) { testedPipeline.execute() }

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M not flush W execute() { serializer returns null }`(forge: Forge) {
        // Given
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializer.serialize(any(), any())) doReturn null
        repeat(fakeBufferSize - 1) { testedPipeline.execute() }

        // When
        testedPipeline.execute()

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    // endregion

    // region flush()

    @Test
    fun `M write partial buffer W flush() { buffer has data }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeTimeseriesName = "view.memory"
        val fakeJson = fakeTimeseriesJson(fakeTimeseriesName)
        whenever(mockReader.read()) doReturn fakePoint
        whenever(mockSerializer.serialize(any(), any())) doReturn fakeJson
        val sampleCount = fakeBufferSize - 1
        repeat(sampleCount) { testedPipeline.execute() }

        // When
        testedPipeline.flush()

        // Then
        val captor = argumentCaptor<List<DataPoint<Double>>>()
        verify(mockSerializer).serialize(any(), captor.capture())
        assertThat(captor.firstValue).hasSize(sampleCount).allMatch { it == fakePoint }
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verify(mockInsightsCollector).onTimeseries(fakeTimeseriesName)
        assertThat(buffer.drain()).isEmpty()
    }

    @Test
    fun `M drain buffer before write context callback W flush()`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer { Unit }
        whenever(mockSerializer.serialize(any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        whenever(mockReader.read()) doReturn fakePoint
        testedPipeline.execute()

        // When
        testedPipeline.flush()

        // Then
        assertThat(buffer.drain()).isEmpty()
        verifyNoInteractions(mockSerializer)
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M pass only pre-flush points to serializer W execute() { point added before write context callback invoked }`(
        forge: Forge
    ) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeNextPoint = forge.getForgery<DataPoint<Double>>()
        val callbackCaptor = argumentCaptor<(DatadogContext, EventWriteScope) -> Unit>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer { Unit }
        whenever(mockSerializer.serialize(any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        whenever(mockReader.read()) doReturn fakePoint
        testedPipeline.execute()
        testedPipeline.flush()
        whenever(mockReader.read()) doReturn fakeNextPoint

        // When
        testedPipeline.execute()
        verify(mockRumFeatureScope).withWriteContext(any(), callbackCaptor.capture())
        callbackCaptor.firstValue.invoke(mockDatadogContext, mockEventWriteScope)

        // Then
        val dataPointsCaptor = argumentCaptor<List<DataPoint<Double>>>()
        verify(mockSerializer).serialize(eq(mockDatadogContext), dataPointsCaptor.capture())
        assertThat(dataPointsCaptor.firstValue).containsExactly(fakePoint)
        assertThat(buffer.drain()).containsExactly(fakeNextPoint)
    }

    @Test
    fun `M pass datadogContext from withWriteContext W flush()`(forge: Forge) {
        // Given
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializer.serialize(any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        testedPipeline.execute()

        // When
        testedPipeline.flush()

        // Then
        verify(mockSerializer).serialize(eq(mockDatadogContext), any())
    }

    @Test
    fun `M not write W flush() { buffer empty }`() {
        // When
        testedPipeline.flush()

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M log error W flush() { serializer throws }`(forge: Forge) {
        // Given
        val fakeThrowable = forge.aThrowable()
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializer.serialize(any(), any())) doThrow fakeThrowable
        testedPipeline.execute()

        // When
        testedPipeline.flush()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = "Timeseries serialization failed",
            throwable = fakeThrowable
        )
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M log error W flush() { dataWriter throws }`(forge: Forge) {
        // Given
        val fakeThrowable = forge.aThrowable()
        val fakeJson = fakeTimeseriesJson("view.cpu")
        val writeBlockCaptor = argumentCaptor<(EventBatchWriter) -> Unit>()
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializer.serialize(any(), any())) doReturn fakeJson
        whenever(mockDataWriter.write(any(), any(), any())) doThrow fakeThrowable
        whenever(mockEventWriteScope.invoke(any())) doAnswer { Unit }
        testedPipeline.execute()

        // When
        testedPipeline.flush()
        verify(mockEventWriteScope).invoke(writeBlockCaptor.capture())
        writeBlockCaptor.firstValue.invoke(mockEventBatchWriter)

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = "Timeseries flush failed",
            throwable = fakeThrowable
        )
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M not notify insights W flush() { dataWriter returns false }`(forge: Forge) {
        // Given
        val fakeJson = fakeTimeseriesJson("view.cpu")
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializer.serialize(any(), any())) doReturn fakeJson
        whenever(mockDataWriter.write(any(), any(), any())) doReturn false
        testedPipeline.execute()

        // When
        testedPipeline.flush()

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verifyNoInteractions(mockInsightsCollector)
    }

    // endregion

    private fun fakeTimeseriesJson(name: String): JsonObject = JsonObject().apply {
        add(
            TimeseriesAttributes.KEY_TIMESERIES,
            JsonObject().apply {
                addProperty("name", name)
            }
        )
    }
}
