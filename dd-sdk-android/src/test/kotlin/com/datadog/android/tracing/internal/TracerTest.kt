package com.datadog.android.tracing.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.tracing.Tracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.invokeMethod
import datadog.opentracing.scopemanager.ContextualScopeManager
import datadog.trace.api.Config
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.util.GlobalTracer
import java.math.BigInteger
import java.util.Random
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

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class TracerTest {

    lateinit var testedBuilder: Tracer.Builder
    lateinit var mockAppContext: Application
    lateinit var fakeToken: String
    lateinit var fakeServiceName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeToken = forge.anHexadecimalString()
        mockAppContext = mockContext()
        Datadog.initialize(mockAppContext, fakeToken)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")

        val tracer = GlobalTracer.get()
        val activeSpan = tracer?.activeSpan()
        val activeScope = tracer?.scopeManager()?.active()
        activeSpan?.finish()
        activeScope?.close()

        val tlsScope: ThreadLocal<*> = ContextualScopeManager::class.java.getStaticValue("tlsScope")
        tlsScope.remove()
    }

    @Test
    fun `buildSpan will inject a parent context`(
        @StringForgery(StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        val expectedTraceId = BigInteger(Tracer.TRACE_ID_BIT_SIZE, Random(seed))
        val tracer = Tracer.Builder()
            .withRandom(Random(seed))
            .build()

        val span = tracer.buildSpan(operationName).start()

        val traceId = span.traceId
        assertThat(traceId)
            .isEqualTo(expectedTraceId)
    }

    @Test
    fun `buildSpan will not inject a parent context if one exists`(
        @StringForgery(StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        val expectedTraceId = BigInteger(Tracer.TRACE_ID_BIT_SIZE, Random(seed))
        val tracer = Tracer.Builder()
            .withRandom(Random(seed))
            .build()

        val span = tracer.buildSpan(operationName).start()
        tracer.activateSpan(span)
        val subSpan = tracer.buildSpan(operationName).start()

        val traceId = subSpan.traceId
        assertThat(traceId)
            .isEqualTo(expectedTraceId)
    }

    @Test
    fun `it will build a valid Tracer`(forge: Forge) {
        // given
        testedBuilder = Tracer.Builder()
        val threshold = forge.anInt(max = 100)
        // when
        val tracer = testedBuilder
            .setServiceName(fakeServiceName)
            .setPartialFlushThreshold(threshold)
            .build()
        val properties = testedBuilder.properties()

        // then
        assertThat(tracer).isNotNull()
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start()
        assertThat(span.serviceName).isEqualTo(fakeServiceName)
        assertThat(properties.getProperty(Config.PARTIAL_FLUSH_MIN_SPANS).toInt())
            .isEqualTo(threshold)
    }

    @Test
    fun `it will build a valid Tracer with default values if not provided`(forge: Forge) {
        // given
        testedBuilder = Tracer.Builder()

        // when
        val tracer = testedBuilder.build()

        // then
        val properties = testedBuilder.properties()
        assertThat(tracer).isNotNull()
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start()
        assertThat(span.serviceName).isEqualTo(TracesFeature.serviceName)
        assertThat(properties.getProperty(Config.PARTIAL_FLUSH_MIN_SPANS).toInt())
            .isEqualTo(Tracer.DEFAULT_PARTIAL_MIN_FLUSH)
    }
}
