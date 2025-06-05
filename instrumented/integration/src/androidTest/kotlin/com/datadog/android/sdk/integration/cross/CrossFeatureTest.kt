/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.cross

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android._InternalProxy
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.log.model.LogEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.sdk.okhttp.RecordingDispatcher
import com.datadog.android.sdk.utils.isLogsUrl
import com.datadog.android.sdk.utils.isRumUrl
import com.datadog.android.sdk.utils.isTracesUrl
import com.datadog.android.sdk.utils.overrideProcessImportance
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.model.SpanEvent
import com.datadog.android.trace.withinSpan
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CrossFeatureTest {

    private val mockWebServer = MockWebServer()
    private val rumEvents = mutableListOf<JsonObject>()
    private val logEvents = mutableListOf<JsonObject>()
    private val spanEvents = mutableListOf<JsonObject>()
    private lateinit var logger: Logger
    private lateinit var openTracingTracer: AndroidTracer
    private val forge = Forge()

    @Before
    fun setUp() {
        mockWebServer.dispatcher = RecordingDispatcher(true) { request, _, textBody ->
            with(checkNotNull(request.requestUrl).toString()) {
                if (isRumUrl()) {
                    rumEvents += textBody
                        .split("\n")
                        .map { JsonParser.parseString(it).asJsonObject }
                } else if (isLogsUrl()) {
                    logEvents += textBody
                        .let { JsonParser.parseString(it).asJsonArray }
                        .map { it.asJsonObject }
                } else if (isTracesUrl()) {
                    spanEvents += textBody
                        .split("\n")
                        .map { JsonParser.parseString(it).asJsonObject }
                        .flatMap { it.getAsJsonArray("spans").map { it.asJsonObject } }
                }
            }
        }
        mockWebServer.start()
        val sdkConfig = Configuration.Builder("fake-token", "fake-env")
            .setBatchProcessingLevel(BatchProcessingLevel.HIGH)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .apply {
                _InternalProxy.allowClearTextHttp(this)
            }
            .build()
        Datadog.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext,
            sdkConfig,
            TrackingConsent.GRANTED
        )

        val rumConfiguration = RumConfiguration.Builder(UUID.randomUUID().toString())
            .useCustomEndpoint(mockWebServer.url("/rum").toString())
            .build()
        overrideProcessImportance()
        Rum.enable(rumConfiguration)
        val traceConfiguration = TraceConfiguration.Builder()
            .useCustomEndpoint(mockWebServer.url("/traces").toString())
            .build()
        Trace.enable(traceConfiguration)
        openTracingTracer = AndroidTracer.Builder()
            .setBundleWithRumEnabled(true)
            .build()
        // don't register GlobalTracer, because call to unregister it
        // GlobalTracer::class.java.setStaticValue("isRegistered", false) will fail on API 21,
        // it is impossible to change static final field there
        // TODO RUM-0000 missing Otel tracer
        val logsConfiguration = LogsConfiguration.Builder()
            .useCustomEndpoint(mockWebServer.url("/logs").toString())
            .build()
        Logs.enable(logsConfiguration)
        logger = Logger.Builder()
            .setBundleWithRumEnabled(true)
            .setBundleWithTraceEnabled(true)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        GlobalRumMonitor.get().stopSession()
        Datadog.stopInstance()
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .cacheDir.deleteRecursively()
    }

    @Test
    fun tracesAndLogsMustLinkToRumWhenCalledWithinActiveRumView() {
        // Given
        val fakeOperationName = forge.aString()
        val fakeRumViewName = forge.aString()
        val fakeRumActionName = forge.aString()
        val fakeLogMessage = forge.aString()

        // When
        GlobalRumMonitor.get().startView(fakeRumViewName, fakeRumViewName)
        GlobalRumMonitor.get().startAction(RumActionType.TAP, fakeRumActionName, attributes = emptyMap())
        openTracingTracer.withinSpan(fakeOperationName) {
            logger.i(fakeLogMessage)
        }
        GlobalRumMonitor.get().stopAction(RumActionType.TAP, fakeRumActionName, attributes = emptyMap())
        GlobalRumMonitor.get().stopView(fakeRumViewName)

        // Then
        ConditionWatcher {
            val viewEvent = rumEvents.filter { it.get("type").asString == "view" }
                .map { ViewEvent.fromJsonObject(it) }
                .firstOrNull { it.view.name == fakeRumViewName }
            val actionEvent = rumEvents.filter { it.get("type").asString == "action" }
                .map { ActionEvent.fromJsonObject(it) }
                .firstOrNull { it.action.target?.name == fakeRumActionName }
            val spanEvent = spanEvents.firstOrNull { it.get("name").asString == fakeOperationName }
                ?.let { SpanEvent.fromJsonObject(it) }
            val logEvent = logEvents.firstOrNull { it.get("message").asString == fakeLogMessage }
                ?.let { LogEvent.fromJsonObject(it) }
            assertThat(viewEvent).isNotNull
            assertThat(actionEvent).isNotNull
            assertThat(spanEvent).isNotNull
            assertThat(logEvent).isNotNull
            // just to benefit from smart cast
            checkNotNull(viewEvent)
            checkNotNull(actionEvent)
            checkNotNull(spanEvent)
            checkNotNull(logEvent)
            SpanEventAssert.assertThat(spanEvent)
                .hasApplicationId(viewEvent.application.id)
                .hasSessionId(viewEvent.session.id)
                .hasViewId(viewEvent.view.id)
            LogEventAssert.assertThat(logEvent)
                .hasApplicationId(viewEvent.application.id)
                .hasSessionId(viewEvent.session.id)
                .hasViewId(viewEvent.view.id)
                .hasActionId(checkNotNull(actionEvent.action.id))
                .hasTraceId(spanEvent.meta.additionalProperties["_dd.p.id"] + spanEvent.traceId)
                .hasSpanId(spanEvent.spanId.toLong(radix = 16).toString())
            true
        }.doWait(TimeUnit.SECONDS.toMillis(30))
    }

    @Test
    fun tracesAndLogsMustLinkToRumWithoutViewIdWhenCalledBeforeActiveRumView() {
        // Given
        val fakeOperationName = forge.aString()
        val fakeRumViewName = forge.aString()
        val fakeRumActionName = forge.aString()
        val fakeLogMessage = forge.aString()

        // When
        openTracingTracer.withinSpan(fakeOperationName) {
            logger.i(fakeLogMessage)
        }
        GlobalRumMonitor.get().startView(fakeRumViewName, fakeRumViewName)
        GlobalRumMonitor.get().startAction(RumActionType.TAP, fakeRumActionName, attributes = emptyMap())
        GlobalRumMonitor.get().stopAction(RumActionType.TAP, fakeRumActionName, attributes = emptyMap())
        GlobalRumMonitor.get().stopView(fakeRumViewName)

        // Then
        ConditionWatcher {
            val viewEvent = rumEvents.filter { it.get("type").asString == "view" }
                .map { ViewEvent.fromJsonObject(it) }
                .firstOrNull { it.view.name == fakeRumViewName }
            val spanEvent = spanEvents.firstOrNull { it.get("name").asString == fakeOperationName }
                ?.let { SpanEvent.fromJsonObject(it) }
            val logEvent = logEvents.firstOrNull { it.get("message").asString == fakeLogMessage }
                ?.let { LogEvent.fromJsonObject(it) }
            assertThat(viewEvent).isNotNull
            assertThat(spanEvent).isNotNull
            assertThat(logEvent).isNotNull
            // just to benefit from smart cast
            checkNotNull(viewEvent)
            checkNotNull(spanEvent)
            checkNotNull(logEvent)
            SpanEventAssert.assertThat(spanEvent)
                .hasApplicationId(viewEvent.application.id)
                .hasSessionId(viewEvent.session.id)
                .hasNoViewId()
            LogEventAssert.assertThat(logEvent)
                .hasApplicationId(viewEvent.application.id)
                .hasSessionId(viewEvent.session.id)
                .hasNoViewId()
                .hasNoActionId()
                .hasTraceId(spanEvent.meta.additionalProperties["_dd.p.id"] + spanEvent.traceId)
                .hasSpanId(spanEvent.spanId.toLong(radix = 16).toString())
            true
        }.doWait(TimeUnit.SECONDS.toMillis(30))
    }

    @Test
    fun tracesAndLogsMustLinkToRumWithoutViewIdWhenCalledAfterActiveRumView() {
        // Given
        val fakeOperationName = forge.aString()
        val fakeRumViewName = forge.aString()
        val fakeRumActionName = forge.aString()
        val fakeLogMessage = forge.aString()

        // When
        GlobalRumMonitor.get().startView(fakeRumViewName, fakeRumViewName)
        GlobalRumMonitor.get().startAction(RumActionType.TAP, fakeRumActionName, attributes = emptyMap())
        GlobalRumMonitor.get().stopAction(RumActionType.TAP, fakeRumActionName, attributes = emptyMap())
        GlobalRumMonitor.get().stopView(fakeRumViewName)
        openTracingTracer.withinSpan(fakeOperationName) {
            logger.i(fakeLogMessage)
        }

        // Then
        ConditionWatcher {
            val viewEvent = rumEvents.filter { it.get("type").asString == "view" }
                .map { ViewEvent.fromJsonObject(it) }
                .firstOrNull { it.view.name == fakeRumViewName }
            val spanEvent = spanEvents.firstOrNull { it.get("name").asString == fakeOperationName }
                ?.let { SpanEvent.fromJsonObject(it) }
            val logEvent = logEvents.firstOrNull { it.get("message").asString == fakeLogMessage }
                ?.let { LogEvent.fromJsonObject(it) }
            assertThat(viewEvent).isNotNull
            assertThat(spanEvent).isNotNull
            assertThat(logEvent).isNotNull
            // just to benefit from smart cast
            checkNotNull(viewEvent)
            checkNotNull(spanEvent)
            checkNotNull(logEvent)
            SpanEventAssert.assertThat(spanEvent)
                .hasApplicationId(viewEvent.application.id)
                .hasSessionId(viewEvent.session.id)
                .hasNoViewId()
            LogEventAssert.assertThat(logEvent)
                .hasApplicationId(viewEvent.application.id)
                .hasSessionId(viewEvent.session.id)
                .hasNoViewId()
                .hasNoActionId()
                .hasTraceId(spanEvent.meta.additionalProperties["_dd.p.id"] + spanEvent.traceId)
                .hasSpanId(spanEvent.spanId.toLong(radix = 16).toString())
            true
        }.doWait(TimeUnit.SECONDS.toMillis(30))
    }

    @Test
    fun logsMustNoLinkToTracesWhenCalledBefore() {
        // Given
        val fakeOperationName = forge.aString()
        val fakeLogMessage = forge.aString()

        // When
        logger.i(fakeLogMessage)
        openTracingTracer.withinSpan(fakeOperationName) {
            // nothing
        }

        // Then
        ConditionWatcher {
            val logEvent = logEvents.firstOrNull { it.get("message").asString == fakeLogMessage }
                ?.let { LogEvent.fromJsonObject(it) }
            assertThat(logEvent).isNotNull
            // just to benefit from smart cast
            checkNotNull(logEvent)
            LogEventAssert.assertThat(logEvent)
                .hasNoTraceId()
                .hasNoSpanId()
            true
        }.doWait(TimeUnit.SECONDS.toMillis(30))
    }

    @Test
    fun logsMustNoLinkToTracesWhenCalledAfter() {
        // Given
        val fakeOperationName = forge.aString()
        val fakeLogMessage = forge.aString()

        // When
        openTracingTracer.withinSpan(fakeOperationName) {
            // nothing
        }
        logger.i(fakeLogMessage)

        // Then
        ConditionWatcher {
            val logEvent = logEvents.firstOrNull { it.get("message").asString == fakeLogMessage }
                ?.let { LogEvent.fromJsonObject(it) }
            assertThat(logEvent).isNotNull
            // just to benefit from smart cast
            checkNotNull(logEvent)
            LogEventAssert.assertThat(logEvent)
                .hasNoTraceId()
                .hasNoSpanId()
            true
        }.doWait(TimeUnit.SECONDS.toMillis(30))
    }

    private fun AndroidTracer.withinSpan(operationName: String, block: () -> Unit) {
        val span = buildSpan(operationName).start()
        activateSpan(span).use {
            block()
        }
        span.finish()
    }

    private class SpanEventAssert(actual: SpanEvent) :
        AbstractObjectAssert<SpanEventAssert, SpanEvent>(
            actual,
            SpanEventAssert::class.java
        ) {

        fun hasApplicationId(applicationId: String): SpanEventAssert {
            assertThat(actual.meta.dd.application?.id)
                .overridingErrorMessage(
                    "Expected span with operation name = ${actual.name} to have" +
                        " RUM Application ID = $applicationId, but was ${actual.meta.dd.application?.id}."
                )
                .isEqualTo(applicationId)
            return this
        }

        fun hasNoApplicationId(): SpanEventAssert {
            assertThat(actual.meta.dd.application?.id)
                .overridingErrorMessage(
                    "Expected span with operation name = ${actual.name} to not have" +
                        " RUM Application ID, but was ${actual.meta.dd.application?.id}."
                )
                .isNull()
            return this
        }

        fun hasSessionId(sessionId: String): SpanEventAssert {
            assertThat(actual.meta.dd.session?.id)
                .overridingErrorMessage(
                    "Expected span with operation name = ${actual.name} to have" +
                        " RUM Session ID = $sessionId, but was ${actual.meta.dd.session?.id}."
                )
                .isEqualTo(sessionId)
            return this
        }

        fun hasNoSessionId(): SpanEventAssert {
            assertThat(actual.meta.dd.session?.id)
                .overridingErrorMessage(
                    "Expected span with operation name = ${actual.name} to not have" +
                        " RUM Session ID, but was ${actual.meta.dd.session?.id}."
                )
                .isNull()
            return this
        }

        fun hasViewId(viewId: String): SpanEventAssert {
            assertThat(actual.meta.dd.view?.id)
                .overridingErrorMessage(
                    "Expected span with operation name = ${actual.name} to have" +
                        " RUM View ID = $viewId, but was ${actual.meta.dd.view?.id}."
                )
                .isEqualTo(viewId)
            return this
        }

        fun hasNoViewId(): SpanEventAssert {
            assertThat(actual.meta.dd.view?.id)
                .overridingErrorMessage(
                    "Expected span with operation name = ${actual.name} to not have" +
                        " RUM View ID, but was ${actual.meta.dd.view?.id}."
                )
                .isNull()
            return this
        }

        companion object {
            fun assertThat(actual: SpanEvent) = SpanEventAssert(actual)
        }
    }

    private class LogEventAssert(actual: LogEvent) :
        AbstractObjectAssert<LogEventAssert, LogEvent>(
            actual,
            LogEventAssert::class.java
        ) {

        fun hasApplicationId(applicationId: String): LogEventAssert {
            val actualApplicationId =
                (actual.additionalProperties[LogAttributes.RUM_APPLICATION_ID] as? JsonPrimitive)?.asString
            assertThat(actualApplicationId)
                .overridingErrorMessage(
                    "Expected log event to have" +
                        " RUM Application ID = $applicationId, but was $actualApplicationId."
                )
                .isEqualTo(applicationId)
            return this
        }

        fun hasSessionId(sessionId: String): LogEventAssert {
            val actualSessionId =
                (actual.additionalProperties[LogAttributes.RUM_SESSION_ID] as? JsonPrimitive)?.asString
            assertThat(actualSessionId)
                .overridingErrorMessage(
                    "Expected log event to have" +
                        " RUM Session ID = $sessionId, but was $actualSessionId."
                )
                .isEqualTo(sessionId)
            return this
        }

        fun hasViewId(viewId: String): LogEventAssert {
            val actualViewId = (actual.additionalProperties[LogAttributes.RUM_VIEW_ID] as? JsonPrimitive)?.asString
            assertThat(actualViewId)
                .overridingErrorMessage(
                    "Expected log event to have" +
                        " RUM View ID = $viewId, but was $actualViewId."
                )
                .isEqualTo(viewId)
            return this
        }

        fun hasNoViewId(): LogEventAssert {
            val actualViewId = actual.additionalProperties[LogAttributes.RUM_VIEW_ID]
            assertThat(actualViewId)
                .overridingErrorMessage(
                    "Expected log event to not have" +
                        " RUM View ID, but was $actualViewId."
                )
                .isInstanceOf(JsonNull::class.java)
            return this
        }

        fun hasActionId(actionId: String): LogEventAssert {
            val actualActionId = (actual.additionalProperties[LogAttributes.RUM_ACTION_ID] as? JsonPrimitive)?.asString
            assertThat(actualActionId)
                .overridingErrorMessage(
                    "Expected log event to have" +
                        " RUM Action ID = $actionId, but was $actualActionId."
                )
                .isEqualTo(actionId)
            return this
        }

        fun hasNoActionId(): LogEventAssert {
            val actualActionId = actual.additionalProperties[LogAttributes.RUM_ACTION_ID]
            assertThat(actualActionId)
                .overridingErrorMessage(
                    "Expected log event to not have" +
                        " RUM Action ID, but was $actualActionId."
                )
                .isInstanceOf(JsonNull::class.java)
            return this
        }

        fun hasTraceId(traceId: String): LogEventAssert {
            val actualTraceId = (actual.additionalProperties["dd.trace_id"] as? JsonPrimitive)?.asString
            assertThat(actualTraceId)
                .overridingErrorMessage(
                    "Expected log event to have" +
                        " Trace ID = $traceId, but was $actualTraceId."
                )
                .isEqualTo(traceId)
            return this
        }

        fun hasNoTraceId(): LogEventAssert {
            val actualTraceId = actual.additionalProperties["dd.trace_id"]
            assertThat(actualTraceId)
                .overridingErrorMessage(
                    "Expected log event to not have" +
                        " Trace ID, but was $actualTraceId."
                )
                .isNull()
            return this
        }

        fun hasSpanId(spanId: String): LogEventAssert {
            val actualSpanId = (actual.additionalProperties["dd.span_id"] as? JsonPrimitive)?.asString
            assertThat(actualSpanId)
                .overridingErrorMessage(
                    "Expected log event to have" +
                        " Span ID = $spanId, but was $actualSpanId."
                )
                .isEqualTo(spanId)
            return this
        }

        fun hasNoSpanId(): LogEventAssert {
            val actualSpanId = actual.additionalProperties["dd.span_id"]
            assertThat(actualSpanId)
                .overridingErrorMessage(
                    "Expected log event to not have" +
                        " Span ID, but was $actualSpanId."
                )
                .isNull()
            return this
        }

        companion object {
            fun assertThat(actual: LogEvent) = LogEventAssert(actual)
        }
    }
}
