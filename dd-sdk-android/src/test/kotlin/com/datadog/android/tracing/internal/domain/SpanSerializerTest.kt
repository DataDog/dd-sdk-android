package com.datadog.android.tracing.internal.domain

import com.datadog.android.log.assertj.JsonObjectAssert
import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonParser
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
class SpanSerializerTest {

    lateinit var underTest: SpanSerializer

    @BeforeEach
    fun `set up`() {
        underTest = SpanSerializer()
    }


    @Test
    fun `it will serialize a span to it Json string representation`(@Forgery span:DDSpan){
        // when
        val serialized = underTest.serialize(span)

        // then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        JsonObjectAssert.assertThat(jsonObject)
            .hasField(START_TIMESTAMP_KEY, span.startTime)
            .hasField(DURATION_KEY, span.durationNano)
            .hasField(SERVICE_NAME_KEY, span.serviceName)
            .hasField(TRACE_ID_KEY, span.traceId)
            .hasField(SPAN_ID_KEY, span.spanId)
            .hasField(PARENT_ID_KEY, span.parentId)
            .hasField(RESOURCE_KEY, span.resourceName)
            .hasField(OPERATION_NAME_KEY, span.operationName)
    }

    companion object {
        const val START_TIMESTAMP_KEY="start"
        const val DURATION_KEY="duration"
        const val SERVICE_NAME_KEY="service"
        const val TRACE_ID_KEY="trace_id"
        const val SPAN_ID_KEY="span_id"
        const val PARENT_ID_KEY="parent_id"
        const val RESOURCE_KEY="resource"
        const val OPERATION_NAME_KEY="name"
    }
}