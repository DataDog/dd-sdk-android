/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.log.LogAttributes
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockCoreFeature
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.LogHandler
import com.datadog.opentracing.scopemanager.ContextualScopeManager
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setFieldValue
import com.datadog.trace.api.Config
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.log.Fields
import io.opentracing.util.GlobalTracer
import java.math.BigInteger
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AndroidTracerTest {

    lateinit var testedTracerBuilder: AndroidTracer.Builder

    lateinit var mockAppContext: Application

    lateinit var fakeToken: String
    lateinit var fakeEnvName: String
    lateinit var fakeServiceName: String

    @Mock
    lateinit var mockLogsHandler: LogHandler

    lateinit var mockDevLogsHandler: com.datadog.android.log.internal.logger.LogHandler

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDevLogsHandler = mockDevLogHandler()
        fakeServiceName = forge.anAlphabeticalString()
        fakeEnvName = forge.anAlphabeticalString()
        fakeToken = forge.anHexadecimalString()
        mockAppContext = mockContext()
        mockCoreFeature()
        TracesFeature.initialize(mockAppContext, Configuration.DEFAULT_TRACING_CONFIG)
        RumFeature.initialize(mockAppContext, Configuration.DEFAULT_RUM_CONFIG)
        testedTracerBuilder = AndroidTracer.Builder()
        testedTracerBuilder.setFieldValue("logsHandler", mockLogsHandler)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")

        val tracer = GlobalTracer.get()
        val activeSpan = tracer?.activeSpan()
        val activeScope = tracer?.scopeManager()?.active()
        activeSpan?.finish()
        activeScope?.close()

        val tlsScope: ThreadLocal<*> =
            ContextualScopeManager::class.java.getStaticValue("tlsScope")
        tlsScope.remove()
    }

    // region Tracer

    @Test
    fun `M log a developer error W buildTracer { TracingFeature not enabled }`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        // GIVEN
        TracesFeature.stop()

        // WHEN
        testedTracerBuilder.build()

        // THEN
        verify(mockDevLogsHandler).handleLog(
            Log.ERROR,
            AndroidTracer.TRACING_NOT_ENABLED_ERROR_MESSAGE
        )
    }

    @Test
    fun `M log a developer error W buildTracer { RumFeature not enabled and bundleWithRum true }`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        // GIVEN
        RumFeature.stop()

        // WHEN
        testedTracerBuilder.build()

        // THEN
        verify(mockDevLogsHandler).handleLog(
            Log.ERROR,
            AndroidTracer.RUM_NOT_ENABLED_ERROR_MESSAGE
        )
    }

    @Test
    fun `buildSpan will inject a parent context`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        val tracer = testedTracerBuilder
            .build()

        val span = tracer.buildSpan(operationName).start() as DDSpan

        assertThat(span.traceId)
            .isGreaterThan(BigInteger.valueOf(0L))
    }

    @Test
    fun `buildSpan will generate a random Span id`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        val expectedSpanId = BigInteger(AndroidTracer.TRACE_ID_BIT_SIZE, Random(seed))
        val tracer = testedTracerBuilder
            .withRandom(Random(seed))
            .build()

        val span = tracer.buildSpan(operationName).start() as DDSpan

        assertThat(span.spanId)
            .isEqualTo(expectedSpanId)
    }

    @Test
    fun `buildSpan will not inject a parent context if one exists`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        val tracer = testedTracerBuilder
            .build()

        val span = tracer.buildSpan(operationName).start() as DDSpan
        tracer.activateSpan(span)
        val subSpan = tracer.buildSpan(operationName).start() as DDSpan

        val traceId = subSpan.traceId
        assertThat(traceId)
            .isEqualTo(span.traceId)
    }

    @Test
    fun `M inject RumContext W buildSpan { bundleWithRum enabled and RumFeature initialized }`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        forge: Forge
    ) {
        val rumContext = forge.getForgery<RumContext>()
        GlobalRum.registerIfAbsent(mock<RumMonitor>())
        GlobalRum.updateRumContext(rumContext)
        val tracer = AndroidTracer.Builder()
            .build()

        val span = tracer.buildSpan(operationName).start() as DDSpan
        val meta = span.meta
        assertThat(meta[LogAttributes.RUM_APPLICATION_ID])
            .isEqualTo(rumContext.applicationId)
        assertThat(meta[LogAttributes.RUM_SESSION_ID])
            .isEqualTo(rumContext.sessionId)
        val viewId = rumContext.viewId
        if (viewId == null) {
            assertThat(meta.containsKey(LogAttributes.RUM_VIEW_ID))
                .isFalse()
        } else {
            assertThat(meta[LogAttributes.RUM_VIEW_ID])
                .isEqualTo(viewId)
        }
    }

    @Test
    fun `M not inject RumContext W buildSpan { RumFeature not initialized }`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        // GIVEN
        val rumContext = forge.getForgery<RumContext>()
        GlobalRum.registerIfAbsent(mock<RumMonitor>())
        GlobalRum.updateRumContext(rumContext)
        RumFeature.stop()
        val tracer = AndroidTracer.Builder()
            .build()

        // WHEN
        val span = tracer.buildSpan(operationName).start() as DDSpan

        // THEN
        val meta = span.meta
        assertThat(meta[LogAttributes.RUM_APPLICATION_ID])
            .isNull()
        assertThat(meta[LogAttributes.RUM_SESSION_ID])
            .isNull()
    }

    @Test
    fun `M not inject RumContext W buildSpan { bundleWithRum disabled }`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String,
        @LongForgery seed: Long
    ) {
        // GIVEN
        val rumContext = forge.getForgery<RumContext>()
        GlobalRum.registerIfAbsent(mock<RumMonitor>())
        GlobalRum.updateRumContext(rumContext)
        val tracer = AndroidTracer.Builder()
            .setBundleWithRumEnabled(false)
            .build()

        // WHEN
        val span = tracer.buildSpan(operationName).start() as DDSpan

        // THEN
        val meta = span.meta
        assertThat(meta[LogAttributes.RUM_APPLICATION_ID])
            .isNull()
        assertThat(meta[LogAttributes.RUM_SESSION_ID])
            .isNull()
    }

    @Test
    fun `it will build a valid Tracer`(forge: Forge) {
        // Given
        val threshold = forge.anInt(max = 100)
        // When
        val tracer = testedTracerBuilder
            .setServiceName(fakeServiceName)
            .setPartialFlushThreshold(threshold)
            .build()
        val properties = testedTracerBuilder.properties()

        // Then
        assertThat(tracer).isNotNull()
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
        assertThat(span.serviceName).isEqualTo(fakeServiceName)
        assertThat(properties.getProperty(Config.PARTIAL_FLUSH_MIN_SPANS).toInt())
            .isEqualTo(threshold)
    }

    @Test
    fun `it will build a valid Tracer with global tags`(
        @StringForgery operation: String,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) value: String
    ) {
        // When
        val tracer = testedTracerBuilder
            .setServiceName(fakeServiceName)
            .addGlobalTag(key, value)
            .build()
        val properties = testedTracerBuilder.properties()

        // Then
        assertThat(tracer).isNotNull()
        val span = tracer.buildSpan(operation).start() as DDSpan
        assertThat(span.serviceName).isEqualTo(fakeServiceName)
        assertThat(span.tags).containsEntry(key, value)
    }

    @Test
    fun `it will build a valid Tracer with default values if not provided`(forge: Forge) {
        // When
        val tracer = testedTracerBuilder.build()

        // Then
        val properties = testedTracerBuilder.properties()
        assertThat(tracer).isNotNull()
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
        assertThat(span.serviceName).isEqualTo(CoreFeature.serviceName)
        assertThat(properties.getProperty(Config.PARTIAL_FLUSH_MIN_SPANS).toInt())
            .isEqualTo(AndroidTracer.DEFAULT_PARTIAL_MIN_FLUSH)
    }

    @Test
    fun `it will delegate all the span log action to the logsHandler`(forge: Forge) {
        // Given
        val tracer = testedTracerBuilder.build()
        val logEvent = forge.anAlphabeticalString()
        val logMaps = forge.aMap {
            forge.anAlphabeticalString() to forge.anAlphabeticalString()
        }
        val logTimestamp = forge.aLong()
        val span = tracer.buildSpan(logEvent).start() as DDSpan

        // When
        span.log(logEvent)
        span.log(logTimestamp, logEvent)
        span.log(logMaps)
        span.log(logTimestamp, logMaps)

        // Then
        val inOrder = inOrder(mockLogsHandler)
        inOrder.verify(mockLogsHandler).log(logEvent, span)
        inOrder.verify(mockLogsHandler).log(logTimestamp, logEvent, span)
        inOrder.verify(mockLogsHandler).log(logMaps, span)
        inOrder.verify(mockLogsHandler).log(logTimestamp, logMaps, span)
    }

    // endregion

    // region Helpers

    @Test
    fun `it will delegate to the right fields when logging a throwable for a span`(
        @Forgery throwable: Throwable
    ) {
        // Given
        val mockSpan: Span = mock()

        // When
        AndroidTracer.logThrowable(mockSpan, throwable)

        // Then
        argumentCaptor<Map<String, Any>>().apply {
            verify(mockSpan).log(capture())
            assertThat(firstValue)
                .containsEntry(Fields.ERROR_OBJECT, throwable)
                .containsOnlyKeys(Fields.ERROR_OBJECT)
        }
    }

    @Test
    fun `it will delegate to the right fields when logging an error message for a span`(
        forge: Forge
    ) {
        // Given
        val anErrorMessage: String = forge.aString()
        val mockSpan: Span = mock()

        // When
        AndroidTracer.logErrorMessage(mockSpan, anErrorMessage)

        // Then
        argumentCaptor<Map<String, Any>>().apply {
            verify(mockSpan).log(capture())
            assertThat(firstValue)
                .containsEntry(Fields.MESSAGE, anErrorMessage)
                .containsOnlyKeys(Fields.MESSAGE)
        }
    }

    @Test
    fun `M generate a different trace id W new tracer is created`(forge: Forge) {
        // Given
        val countDownLatch = CountDownLatch(2)
        val tracer1: AndroidTracer
        val tracer2: AndroidTracer
        val span1: DDSpan
        val span2: DDSpan

        // When
        Thread().run {
            tracer1 = AndroidTracer.Builder().build()
            span1 = tracer1.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
            countDownLatch.countDown()
        }
        Thread().run {
            tracer2 = AndroidTracer.Builder().build()
            span2 = tracer2.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
            countDownLatch.countDown()
        }
        countDownLatch.await(10, TimeUnit.SECONDS)

        // Then
        val traceIdSpan1 = span1.traceId
        val traceIdSpan2 = span2.traceId
        assertThat(traceIdSpan1).isNotEqualTo(traceIdSpan2)
    }

    // endregion
}
