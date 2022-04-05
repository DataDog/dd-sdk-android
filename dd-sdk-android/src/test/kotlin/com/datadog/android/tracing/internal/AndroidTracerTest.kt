/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.LogAttributes
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.LogHandler
import com.datadog.opentracing.scopemanager.ScopeTestHelper
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import java.util.UUID
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AndroidTracerTest {

    lateinit var testedTracerBuilder: AndroidTracer.Builder

    lateinit var fakeToken: String
    lateinit var fakeEnvName: String
    lateinit var fakeServiceName: String

    @Mock
    lateinit var mockLogsHandler: LogHandler

    @BeforeEach
    fun `set up`(forge: Forge) {
        // Prevent crash when initializing RumFeature
        mockChoreographerInstance()

        fakeServiceName = forge.anAlphabeticalString()
        fakeEnvName = forge.anAlphabeticalString()
        fakeToken = forge.anHexadecimalString()
        TracingFeature.initialize(appContext.mockInstance, Configuration.DEFAULT_TRACING_CONFIG)
        RumFeature.initialize(appContext.mockInstance, Configuration.DEFAULT_RUM_CONFIG)
        testedTracerBuilder = AndroidTracer.Builder(mockLogsHandler)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.stop()

        val tracer = GlobalTracer.get()
        val activeSpan = tracer?.activeSpan()

        @Suppress("DEPRECATION")
        val activeScope = tracer?.scopeManager()?.active()
        activeSpan?.finish()
        activeScope?.close()

        ScopeTestHelper.removeThreadLocalScope()
        RumFeature.stop()
    }

    // region Tracer

    @Test
    fun `M log a developer error W buildTracer { TracingFeature not enabled }`() {
        // GIVEN
        TracingFeature.stop()

        // WHEN
        testedTracerBuilder.build()

        // THEN
        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            AndroidTracer.TRACING_NOT_ENABLED_ERROR_MESSAGE + "\n" +
                Datadog.MESSAGE_SDK_INITIALIZATION_GUIDE
        )
    }

    @Test
    fun `M log a developer error W buildTracer { RumFeature not enabled, bundleWithRum true }`() {
        // GIVEN
        RumFeature.stop()

        // WHEN
        testedTracerBuilder.build()

        // THEN
        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            AndroidTracer.RUM_NOT_ENABLED_ERROR_MESSAGE
        )
    }

    @Test
    fun `buildSpan will inject a parent context`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String
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
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String
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
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String
    ) {
        val tracer = AndroidTracer.Builder()
            .build()

        val span = tracer.buildSpan(operationName).start() as DDSpan
        val meta = span.meta
        assertThat(meta[LogAttributes.RUM_APPLICATION_ID])
            .isEqualTo(rumMonitor.context.applicationId)
        assertThat(meta[LogAttributes.RUM_SESSION_ID])
            .isEqualTo(rumMonitor.context.sessionId)
        val viewId = rumMonitor.context.viewId
        if (viewId == null) {
            assertThat(meta.containsKey(LogAttributes.RUM_VIEW_ID)).isFalse()
        } else {
            assertThat(meta[LogAttributes.RUM_VIEW_ID]).isEqualTo(viewId)
        }
        val actionId = rumMonitor.context.actionId
        if (actionId == null) {
            assertThat(meta.containsKey(LogAttributes.RUM_ACTION_ID)).isFalse()
        } else {
            assertThat(meta[LogAttributes.RUM_ACTION_ID]).isEqualTo(actionId)
        }
    }

    @Test
    fun `M inject Rum ViewId W buildSpan { bundleWithRum enabled and ViewId not null }`(
        forge: Forge
    ) {
        // Given
        val fakeViewId = forge.getForgery<UUID>().toString()
        val fakeRumContext = forge.getForgery(RumContext::class.java).copy(viewId = fakeViewId)
        GlobalRum.updateRumContext(fakeRumContext)
        val fakeOperationName = forge.anAlphaNumericalString()
        val tracer = AndroidTracer.Builder()
            .build()

        // When
        val span = tracer.buildSpan(fakeOperationName).start() as DDSpan
        val meta = span.meta

        // Then
        assertThat(meta[LogAttributes.RUM_VIEW_ID]).isEqualTo(fakeViewId)
    }

    @Test
    fun `M not inject Rum ViewId W buildSpan { bundleWithRum enabled and ViewId is null }`(
        forge: Forge
    ) {
        // Given
        val fakeRumContext = forge.getForgery(RumContext::class.java).copy(viewId = null)
        GlobalRum.updateRumContext(fakeRumContext)
        val fakeOperationName = forge.anAlphaNumericalString()
        val tracer = AndroidTracer.Builder()
            .build()

        // When
        val span = tracer.buildSpan(fakeOperationName).start() as DDSpan
        val meta = span.meta

        // Then
        assertThat(meta.containsKey(LogAttributes.RUM_VIEW_ID)).isFalse()
    }

    @Test
    fun `M inject Rum actionId W buildSpan { bundleWithRum enabled and ActionId not null }`(
        forge: Forge
    ) {
        // Given
        val fakeActionId = forge.getForgery<UUID>().toString()
        val fakeRumContext = forge.getForgery(RumContext::class.java).copy(actionId = fakeActionId)
        GlobalRum.updateRumContext(fakeRumContext)
        val fakeOperationName = forge.anAlphaNumericalString()
        val tracer = AndroidTracer.Builder()
            .build()

        // When
        val span = tracer.buildSpan(fakeOperationName).start() as DDSpan
        val meta = span.meta

        // Then
        assertThat(meta[LogAttributes.RUM_ACTION_ID]).isEqualTo(fakeActionId)
    }

    @Test
    fun `M not inject Rum ActionId W buildSpan { bundleWithRum enabled and ActionId is null }`(
        forge: Forge
    ) {
        // Given
        val fakeRumContext = forge.getForgery(RumContext::class.java).copy(actionId = null)
        GlobalRum.updateRumContext(fakeRumContext)
        val fakeOperationName = forge.anAlphaNumericalString()
        val tracer = AndroidTracer.Builder()
            .build()

        // When
        val span = tracer.buildSpan(fakeOperationName).start() as DDSpan
        val meta = span.meta

        // Then
        assertThat(meta.containsKey(LogAttributes.RUM_ACTION_ID)).isFalse()
    }

    @Test
    fun `M not inject RumContext W buildSpan { RumFeature not initialized }`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String
    ) {
        // GIVEN
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
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) operationName: String
    ) {
        // GIVEN
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
        assertThat(span.serviceName).isEqualTo(coreFeature.fakeServiceName)
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

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val mainLooper = MainLooperTestConfiguration()
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, coreFeature, rumMonitor, mainLooper)
        }
    }
}
