/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.data

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.event.EventMapper
import com.datadog.android.trace.internal.domain.event.ContextAwareMapper
import com.datadog.android.trace.internal.storage.ContextAwareSerializer
import com.datadog.android.trace.model.SpanEvent
import com.datadog.android.trace.utils.verifyLog
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.trace.core.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class OtelTraceWriterTest {

    private lateinit var testedWriter: OtelTraceWriter

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockLegacyMapper: ContextAwareMapper<DDSpan, SpanEvent>

    @Mock
    lateinit var mockEventMapper: EventMapper<SpanEvent>

    @Mock
    lateinit var mockSerializer: ContextAwareSerializer<SpanEvent>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTracingFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    // region Unit Tests

    @BeforeEach
    fun `set up`() {
        whenever(
            mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
        ) doReturn mockTracingFeatureScope

        whenever(mockTracingFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        whenever(mockEventMapper.map(any())) doAnswer { it.getArgument(0) }

        testedWriter = OtelTraceWriter(
            sdkCore = mockSdkCore,
            ddSpanToSpanEventMapper = mockLegacyMapper,
            eventMapper = mockEventMapper,
            serializer = mockSerializer,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M write spans W write()`(forge: Forge) {
        // GIVEN
        val ddSpans = createNonEmptyDdSpans(
            forge = forge,
            includeDropSamplingPriority = false
        )
        val spanEvents = ddSpans.map { forge.getForgery<SpanEvent>() }
        val serializedSpans = ddSpans.map { forge.aString() }

        ddSpans.forEachIndexed { index, ddSpan ->
            whenever(mockLegacyMapper.map(fakeDatadogContext, ddSpan)) doReturn spanEvents[index]
        }

        spanEvents.forEachIndexed { index, spanEvent ->
            whenever(
                mockSerializer.serialize(fakeDatadogContext, spanEvent)
            ) doReturn serializedSpans[index]
        }

        // WHEN
        testedWriter.write(ddSpans)

        // THEN
        serializedSpans.forEach {
            verify(mockEventBatchWriter)
                .write(RawBatchEvent(data = it.toByteArray()), null, EventType.DEFAULT)
        }
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M not write spans with drop sampling priority W write()`(forge: Forge) {
        // GIVEN
        val ddSpans = createNonEmptyDdSpans(
            forge = forge,
            includeDropSamplingPriority = true
        )

        // WHEN
        testedWriter.write(ddSpans)

        // THEN
        verifyNoInteractions(mockEventBatchWriter)

        ddSpans.forEach {
            it.finish()
        }
    }

    @Test
    fun `M not write non-mapped spans W write()`(forge: Forge) {
        // GIVEN
        val ddSpans = createNonEmptyDdSpans(
            forge = forge,
            includeDropSamplingPriority = false
        )
        val spanEvents = ddSpans.map { forge.getForgery<SpanEvent>() }
        val mappedEvents = spanEvents.map { forge.aNullable { it } }

        val serializedSpans = mappedEvents.filterNotNull().map { forge.aString() }

        ddSpans.forEachIndexed { index, ddSpan ->
            whenever(mockLegacyMapper.map(fakeDatadogContext, ddSpan)) doReturn spanEvents[index]
        }

        spanEvents.forEachIndexed { index, event ->
            whenever(mockEventMapper.map(event)) doReturn mappedEvents[index]
        }

        mappedEvents.filterNotNull().forEachIndexed { index, spanEvent ->
            whenever(
                mockSerializer.serialize(fakeDatadogContext, spanEvent)
            ) doReturn serializedSpans[index]
        }

        // WHEN
        testedWriter.write(ddSpans)

        // THEN
        serializedSpans.forEach {
            verify(mockEventBatchWriter)
                .write(RawBatchEvent(data = it.toByteArray()), null, EventType.DEFAULT)
        }
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M not write non-serialized spans W write()`(forge: Forge) {
        // GIVEN
        val ddSpans = createNonEmptyDdSpans(
            forge = forge,
            includeDropSamplingPriority = false
        )
        val spanEvents = ddSpans.map { forge.getForgery<SpanEvent>() }

        val serializedSpans = spanEvents.map { forge.aNullable { aString() } }

        ddSpans.forEachIndexed { index, ddSpan ->
            whenever(mockLegacyMapper.map(fakeDatadogContext, ddSpan)) doReturn spanEvents[index]
        }

        spanEvents.forEachIndexed { index, spanEvent ->
            whenever(
                mockSerializer.serialize(fakeDatadogContext, spanEvent)
            ) doReturn serializedSpans[index]
        }

        // WHEN
        testedWriter.write(ddSpans)

        // THEN
        serializedSpans.filterNotNull().forEach {
            verify(mockEventBatchWriter)
                .write(RawBatchEvent(data = it.toByteArray()), null, EventType.DEFAULT)
        }
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M do nothing W write() { null trace }`() {
        // WHEN
        testedWriter.write(null)

        // THEN
        verifyNoInteractions(
            mockEventBatchWriter,
            mockEventMapper,
            mockSerializer,
            mockSdkCore,
            mockLegacyMapper,
            mockInternalLogger
        )
    }

    @Test
    fun `M log error and proceed W write() { serialization failed }`(forge: Forge) {
        // GIVEN
        val ddSpans = createNonEmptyDdSpans(
            forge = forge,
            includeDropSamplingPriority = false
        )
        val spanEvents = ddSpans.map { forge.getForgery<SpanEvent>() }
        val serializedSpans = ddSpans.map { forge.aString() }

        ddSpans.forEachIndexed { index, ddSpan ->
            whenever(mockLegacyMapper.map(fakeDatadogContext, ddSpan)) doReturn spanEvents[index]
        }

        val faultySpanIndex = forge.anInt(min = 0, max = spanEvents.size)
        val fakeThrowable = forge.aThrowable()
        spanEvents.forEachIndexed { index, spanEvent ->
            if (index == faultySpanIndex) {
                whenever(
                    mockSerializer.serialize(
                        fakeDatadogContext,
                        spanEvent
                    )
                ) doThrow fakeThrowable
            } else {
                whenever(
                    mockSerializer.serialize(fakeDatadogContext, spanEvent)
                ) doReturn serializedSpans[index]
            }
        }

        // WHEN
        testedWriter.write(ddSpans)

        // THEN
        serializedSpans.forEachIndexed { index, serializedSpan ->
            if (index != faultySpanIndex) {
                verify(mockEventBatchWriter)
                    .write(RawBatchEvent(data = serializedSpan.toByteArray()), null, EventType.DEFAULT)
            }
        }
        verifyNoMoreInteractions(mockEventBatchWriter)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            TraceWriter.ERROR_SERIALIZING.format(Locale.US, SpanEvent::class.java.simpleName),
            fakeThrowable
        )
    }

    @Test
    fun `M request event write context once W write()`(forge: Forge) {
        // GIVEN
        val ddSpans = createNonEmptyDdSpans(
            forge = forge,
            includeDropSamplingPriority = false
        )

        // WHEN
        testedWriter.write(ddSpans)

        // THEN
        verify(mockSdkCore, times(1)).getFeature(Feature.TRACING_FEATURE_NAME)
        verify(mockTracingFeatureScope, times(1)).withWriteContext(any(), any())

        verifyNoMoreInteractions(mockSdkCore, mockTracingFeatureScope)
    }

    private fun createNonEmptyDdSpans(forge: Forge, includeDropSamplingPriority: Boolean): List<DDSpan> {
        val predicate: (DDSpan) -> Boolean = if (includeDropSamplingPriority) {
            { it.samplingPriority() in OtelTraceWriter.DROP_SAMPLING_PRIORITIES }
        } else {
            { it.samplingPriority() !in OtelTraceWriter.DROP_SAMPLING_PRIORITIES }
        }

        var spans = emptyList<DDSpan>()
        while (spans.isEmpty()) {
            spans = forge.aList { getForgery<DDSpan>() }
                .filter(predicate)
        }

        return spans
    }

    // endregion
}
