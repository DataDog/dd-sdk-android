package com.datadog.android.tracing.internal.utils

import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.tracing.Tracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.noop.NoopTracerFactory
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class TracerExtensionsTest {

    @AfterEach
    fun `tear down`() {
        GlobalTracer.get().setFieldValue("isRegistered", false)
        GlobalTracer::class.java.setStaticValue("tracer", NoopTracerFactory.create())
    }

    @Test
    fun `it will return the trace id and span id if there is an active span`(forge: Forge) {
        // given
        val config = DatadogConfig.Builder(forge.anAlphabeticalString()).build()
        Datadog.initialize(mockContext(), config)
        GlobalTracer.registerIfAbsent(Tracer.Builder().build())

        // when
        val tracer = GlobalTracer.get()
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
        tracer.activateSpan(span)

        // then
        assertThat(tracer.traceId()).isEqualTo(span.traceId.toString())
        assertThat(tracer.spanId()).isEqualTo(span.spanId.toString())
    }
    @Test
    fun `it will return null for trace an span id if there is no active span`(forge: Forge) {
        // given
        val config = DatadogConfig.Builder(forge.anAlphabeticalString()).build()
        Datadog.initialize(mockContext(), config)
        GlobalTracer.registerIfAbsent(Tracer.Builder().build())

        // when
        val tracer = GlobalTracer.get()

        // then
        assertThat(tracer.traceId()).isNull()
        assertThat(tracer.spanId()).isNull()
    }

    @Test
    fun `it will return null for trace and span id if tracer was not initialized`(forge: Forge) {
        // when
        val tracer = GlobalTracer.get()

        // then
        assertThat(tracer.traceId()).isNull()
        assertThat(tracer.spanId()).isNull()
    }
}
