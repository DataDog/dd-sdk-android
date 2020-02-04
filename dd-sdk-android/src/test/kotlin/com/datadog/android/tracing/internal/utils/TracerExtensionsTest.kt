package com.datadog.android.tracing.internal.utils

import com.datadog.android.tracing.Tracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class, seed = 0x4f36670aL)
class TracerExtensionsTest {

    lateinit var tracer: Tracer

    @BeforeEach
    fun `set up`() {
        tracer = Tracer.Builder().build()
    }

    @AfterEach
    fun `tear down`() {
        tracer.scopeManager().active()?.close()
    }

    @Test
    fun `it will return the trace id and span id if there is an active span`(forge: Forge) {

        // when
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
        tracer.activateSpan(span)

        // then
        assertThat(tracer.traceId()).isEqualTo(span.traceId.toString())
        assertThat(tracer.spanId()).isEqualTo(span.spanId.toString())
    }

    @Test
    fun `it will return null for trace and span id if there is no active span`() {
        // given
        val tracer = Tracer.Builder().build()

        // then
        assertThat(tracer.traceId()).isNull()
        assertThat(tracer.spanId()).isNull()
    }
}
