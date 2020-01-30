package com.datadog.android.tracing.internal.domain

import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.utils.extension.toHexString
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.SpanForgeryFactory
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class SpanSerializerTest {

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    lateinit var underTest: SpanSerializer

    @BeforeEach
    fun `set up`() {
        underTest = SpanSerializer(mockTimeProvider)
    }

    @Test
    fun `serializes a span to a Json string representation`(
        @Forgery span: DDSpan,
        @LongForgery serverOffsetNanos: Long
    ) {
        // given
        whenever(mockTimeProvider.getServerOffsetNanos()) doReturn serverOffsetNanos
        val serialized = underTest.serialize(span)

        // when
        val jsonObject = JsonParser.parseString(serialized).asJsonObject

        // then
        assertThat(jsonObject)
            .hasField(SpanSerializer.START_TIMESTAMP_KEY, span.startTime + serverOffsetNanos)
            .hasField(SpanSerializer.DURATION_KEY, span.durationNano)
            .hasField(SpanSerializer.SERVICE_NAME_KEY, span.serviceName)
            .hasField(SpanSerializer.TRACE_ID_KEY, span.traceId.toHexString())
            .hasField(SpanSerializer.SPAN_ID_KEY, span.spanId.toHexString())
            .hasField(SpanSerializer.PARENT_ID_KEY, span.parentId.toHexString())
            .hasField(SpanSerializer.RESOURCE_KEY, span.resourceName)
            .hasField(SpanSerializer.OPERATION_NAME_KEY, span.operationName)
            .hasField(SpanSerializer.META_KEY, span.meta)
            .hasField(SpanSerializer.METRICS_KEY, span.metrics)
            .hasField(SpanSerializer.METRICS_KEY) {
                // additional "magic" metrics key
                hasField(SpanSerializer.METRICS_KEY_TOP_LEVEL, 1)
                hasField(SpanSerializer.METRICS_KEY_SAMPLING, 1)
            }
    }

    @Test
    fun `it will only add the metrics key top level for the top span`(forge: Forge) {
        // given
        val parentSpan =
            SpanForgeryFactory.TEST_TRACER
                .buildSpan(forge.anAlphabeticalString())
                .start()
        val childSpan =
            SpanForgeryFactory.TEST_TRACER
                .buildSpan(forge.anAlphabeticalString())
                .asChildOf(parentSpan)
                .start()

        // when
        val serializedParent = JsonParser.parseString(underTest.serialize(parentSpan)).asJsonObject
        val serializedChild = JsonParser.parseString(underTest.serialize(childSpan)).asJsonObject

        // then
        assertThat(serializedParent).hasField(SpanSerializer.METRICS_KEY) {
            hasField(SpanSerializer.METRICS_KEY_SAMPLING, 1)
        }
        assertThat(serializedChild).hasField(SpanSerializer.METRICS_KEY) {
            doesNotHaveField(SpanSerializer.METRICS_KEY_TOP_LEVEL)
        }
    }
}
