package com.datadog.android.tracing.internal.domain

import com.datadog.android.utils.extension.assertMatches
import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonParser
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Java6Assertions.assertThat
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
internal class SpanSerializerTest {

    lateinit var underTest: SpanSerializer

    @BeforeEach
    fun `set up`() {
        underTest = SpanSerializer()
    }

    @Test
    fun `it will serialize a span to it Json string representation`(@Forgery span: DDSpan) {
        // when
        val serialized = underTest.serialize(span)

        // then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        jsonObject.assertMatches(span)
        // check special metrics
        val metricsObject = jsonObject.get(SpanSerializer.METRICS_KEY).asJsonObject
        assertThat(metricsObject.getAsJsonPrimitive(SpanSerializer.METRICS_KEY_TOP_LEVEL).asInt)
            .isEqualTo(1)
        assertThat(metricsObject.getAsJsonPrimitive(SpanSerializer.METRICS_KEY_SAMPLING).asInt)
            .isEqualTo(1)
    }
}
