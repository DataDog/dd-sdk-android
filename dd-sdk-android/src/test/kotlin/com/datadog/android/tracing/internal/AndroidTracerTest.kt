/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.content.Context
import com.datadog.android.Datadog
import com.datadog.android.log.LogAttributes
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.tracing.TracingHeaderType
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.NoOpSdkCore
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.LogHandler
import com.datadog.opentracing.scopemanager.ScopeTestHelper
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.trace.api.Config
import com.datadog.trace.common.writer.Writer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.log.Fields
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.math.BigInteger
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Mock
    lateinit var mockTracingFeature: TracingFeature

    @Mock
    lateinit var mockTraceWriter: Writer

    @Mock
    lateinit var mockRumFeature: RumFeature

    @Mock
    lateinit var mockSdkCore: DatadogCore

    @Forgery
    lateinit var fakeRumContext: RumContext

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeEnvName = forge.anAlphabeticalString()
        fakeToken = forge.anHexadecimalString()

        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "view_id" to fakeRumContext.viewId,
            "action_id" to fakeRumContext.actionId
        )
        whenever(mockSdkCore.tracingFeature) doReturn mockTracingFeature
        whenever(mockSdkCore.rumFeature) doReturn mockRumFeature
        whenever(mockSdkCore.coreFeature) doReturn coreFeature.mockInstance

        whenever(mockTracingFeature.dataWriter) doReturn mockTraceWriter

        Datadog.globalSdkCore = mockSdkCore

        testedTracerBuilder = AndroidTracer.Builder(mockLogsHandler)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.globalSdkCore = NoOpSdkCore()

        val tracer = GlobalTracer.get()
        val activeSpan = tracer?.activeSpan()

        @Suppress("DEPRECATION")
        val activeScope = tracer?.scopeManager()?.active()
        activeSpan?.finish()
        activeScope?.close()

        ScopeTestHelper.removeThreadLocalScope()
    }

    // region Tracer

    @Test
    fun `M log a developer error W buildTracer { TracingFeature not enabled }`() {
        // GIVEN
        whenever((Datadog.globalSdkCore as DatadogCore).tracingFeature) doReturn null

        // WHEN
        testedTracerBuilder.build()

        // THEN
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            AndroidTracer.TRACING_NOT_ENABLED_ERROR_MESSAGE + "\n" +
                Datadog.MESSAGE_SDK_INITIALIZATION_GUIDE
        )
    }

    @Test
    fun `M log a developer error W buildTracer { RumFeature not enabled, bundleWithRum true }`() {
        // GIVEN
        whenever((Datadog.globalSdkCore as DatadogCore).rumFeature) doReturn null

        // WHEN
        testedTracerBuilder.build()

        // THEN
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
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
            .setServiceName(fakeServiceName)
            .build()

        val span = tracer.buildSpan(operationName).start() as DDSpan
        val meta = span.meta
        assertThat(meta[LogAttributes.RUM_APPLICATION_ID])
            .isEqualTo(fakeRumContext.applicationId)
        assertThat(meta[LogAttributes.RUM_SESSION_ID])
            .isEqualTo(fakeRumContext.sessionId)
        val viewId = fakeRumContext.viewId
        if (viewId == null) {
            assertThat(meta.containsKey(LogAttributes.RUM_VIEW_ID)).isFalse()
        } else {
            assertThat(meta[LogAttributes.RUM_VIEW_ID]).isEqualTo(viewId)
        }
        val actionId = fakeRumContext.actionId
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
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "view_id" to fakeViewId,
            "action_id" to fakeRumContext.actionId
        )
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
    fun `M not inject Rum ViewId W buildSpan { bundleWithRum enabled and ViewId is missing }`(
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "action_id" to fakeRumContext.actionId
        )
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
    fun `M not inject Rum ViewId W buildSpan { bundleWithRum enabled and ViewId is null }`(
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "view_id" to null,
            "action_id" to fakeRumContext.actionId
        )
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
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "view_id" to fakeRumContext.actionId,
            "action_id" to fakeActionId
        )
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
    fun `M not inject Rum ActionId W buildSpan { bundleWithRum enabled and ActionId is missing }`(
        forge: Forge
    ) {
        // Given
        val fakeRumContext = forge.getForgery(RumContext::class.java).copy(actionId = null)
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "view_id" to fakeRumContext.actionId
        )
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
    fun `M not inject Rum ActionId W buildSpan { bundleWithRum enabled and ActionId is null }`(
        forge: Forge
    ) {
        // Given
        val fakeRumContext = forge.getForgery(RumContext::class.java).copy(actionId = null)
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "view_id" to fakeRumContext.actionId,
            "action_id" to null
        )
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
        whenever((Datadog.globalSdkCore as DatadogCore).rumFeature) doReturn null
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
    fun `it will build a valid Tracer with sampling rate`(
        @DoubleForgery(0.0, 1.0) samplingRate: Double
    ) {
        // Given

        // When
        val tracer = testedTracerBuilder
            .setSamplingRate(samplingRate * 100.0)
            .build()
        val properties = testedTracerBuilder.properties()

        // Then
        assertThat(tracer).isNotNull()
        assertThat(properties.getProperty(Config.TRACE_SAMPLE_RATE).toDouble())
            .isCloseTo(samplingRate, offset(0.005))
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

    @Test
    fun `M set correct propagating style W setting tracing header types`(forge: Forge) {
        // Given
        val tracingHeaderStyles = forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()
        // When
        val tracer = testedTracerBuilder
            .setServiceName(fakeServiceName)
            .setTracingHeaderTypes(tracingHeaderStyles)
            .build()
        val properties = testedTracerBuilder.properties()

        // Then
        assertThat(tracer).isNotNull()

        val injectionStyles =
            properties.getProperty(Config.PROPAGATION_STYLE_INJECT).toString().split(",").toSet()
        val extractionStyles =
            properties.getProperty(Config.PROPAGATION_STYLE_EXTRACT).toString().split(",").toSet()

        assertThat(injectionStyles).isEqualTo(tracingHeaderStyles.map { it.headerType }.toSet())
        assertThat(extractionStyles).isEqualTo(tracingHeaderStyles.map { it.headerType }.toSet())
    }

    // endregion

    @Test
    fun `M report active span context for the thread W build`(forge: Forge) {
        // Given
        val tracer = testedTracerBuilder
            .setServiceName(fakeServiceName)
            .build()
        // call to updateFeatureContext is guarded by "synchronize" in the real implementation,
        // but since we are using mock here, let's use thread-safe map instead.
        val tracingContext = ConcurrentHashMap<String, Any?>()
        whenever(
            mockSdkCore.updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any())
        ) doAnswer {
            val callback = it.getArgument<(context: MutableMap<String, Any?>) -> Unit>(1)
            callback.invoke(tracingContext)
        }
        val errorCollector = mutableListOf<Throwable>()

        // When+Then
        val threads = forge.aList(forge.anInt(min = 2, max = 5)) {
            Thread {
                val threadName = Thread.currentThread().name

                val parentSpan = tracer.buildSpan(forge.anAlphabeticalString()).start()
                val parentActiveScope = tracer.activateSpan(parentSpan)

                with(tracingContext.activeContext(threadName)) {
                    assertThat(this!!["span_id"]).isEqualTo(parentSpan.context().toSpanId())
                    assertThat(this["trace_id"]).isEqualTo(parentSpan.context().toTraceId())
                }

                // should update the context for the child span
                val childActiveSpan = tracer.buildSpan(forge.anAlphabeticalString())
                    .asChildOf(parentSpan).start()
                val childActiveScope = tracer.activateSpan(childActiveSpan)

                with(tracingContext.activeContext(threadName)) {
                    assertThat(this!!["span_id"]).isEqualTo(childActiveSpan.context().toSpanId())
                    assertThat(this["trace_id"]).isEqualTo(childActiveSpan.context().toTraceId())
                }

                // should not update the context for the child non-active span
                val childNonActiveSpan = tracer.buildSpan(forge.anAlphabeticalString())
                    .asChildOf(parentSpan).start()

                with(tracingContext.activeContext(threadName)) {
                    assertThat(this!!["span_id"]).isEqualTo(childActiveSpan.context().toSpanId())
                    assertThat(this["trace_id"]).isEqualTo(childActiveSpan.context().toTraceId())
                }

                childNonActiveSpan.finish()

                with(tracingContext.activeContext(threadName)) {
                    assertThat(this!!["span_id"]).isEqualTo(childActiveSpan.context().toSpanId())
                    assertThat(this["trace_id"]).isEqualTo(childActiveSpan.context().toTraceId())
                }

                // should restore context of parent span
                childActiveSpan.finish()
                childActiveScope.close()

                with(tracingContext.activeContext(threadName)) {
                    assertThat(this!!["span_id"]).isEqualTo(parentSpan.context().toSpanId())
                    assertThat(this["trace_id"]).isEqualTo(parentSpan.context().toTraceId())
                }

                // should clean everything
                parentSpan.finish()
                parentActiveScope.close()

                assertThat(tracingContext.activeContext(threadName)).isNull()
            }.apply {
                setUncaughtExceptionHandler { _, e ->
                    synchronized(errorCollector) {
                        errorCollector += e
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        if (errorCollector.isNotEmpty()) {
            // if there are multiple, we need only one to start debugging
            throw errorCollector[0]
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.activeContext(threadName: String) =
        this["context@$threadName"] as? Map<String, String>

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, coreFeature)
        }
    }
}
