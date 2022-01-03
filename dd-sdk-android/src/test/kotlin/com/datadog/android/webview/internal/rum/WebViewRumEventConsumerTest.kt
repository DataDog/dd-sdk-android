/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import android.util.Log
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.assertj.DeserializedActionEventAssert
import com.datadog.android.utils.assertj.DeserializedErrorEventAssert
import com.datadog.android.utils.assertj.DeserializedLongTaskEventAssert
import com.datadog.android.utils.assertj.DeserializedResourceEventAssert
import com.datadog.android.utils.assertj.DeserializedViewEventAssert
import com.datadog.android.utils.config.SessionScopeTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import kotlin.collections.LinkedHashMap
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
internal class WebViewRumEventConsumerTest {
    lateinit var testedRumEventConsumer: WebViewRumEventConsumer

    @Mock
    lateinit var mockDataWriter: DataWriter<Any>

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Forgery
    lateinit var fakeMappedViewEvent: ViewEvent

    @Forgery
    lateinit var fakeMappedResourceEvent: ResourceEvent

    @Forgery
    lateinit var fakeMappedActionEvent: ActionEvent

    @Forgery
    lateinit var fakeMappedErrorEvent: ErrorEvent

    @Forgery
    lateinit var fakeMappedLongTaskEvent: LongTaskEvent

    @LongForgery
    var fakeServerTimeOffsetInMillis: Long = 0L

    lateinit var fakeTags: Map<String, String>

    @Mock
    lateinit var mockWebViewRumEventMapper: WebViewRumEventMapper

    @Mock
    lateinit var mockSdkLogHandler: LogHandler

    lateinit var originalSdkLogHandler: LogHandler

    @Mock
    lateinit var mockRumContextProvider: WebViewRumEventContextProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        originalSdkLogHandler = mockSdkLogHandler(mockSdkLogHandler)
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(sessionScopeTestConfiguration.fakeRumContext)
        fakeTags = if (forge.aBool()) {
            forge.aMap {
                forge.anAlphabeticalString() to forge.anAlphaNumericalString()
            }
        } else {
            emptyMap()
        }
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeServerTimeOffsetInMillis)
        testedRumEventConsumer = WebViewRumEventConsumer(
            mockDataWriter,
            mockTimeProvider,
            mockWebViewRumEventMapper,
            mockRumContextProvider
        )
    }

    @AfterEach
    fun `tear down`() {
        restoreSdkLogHandler(originalSdkLogHandler)
    }

    // region View

    @Test
    fun `M send a noop WebViewEvent W consume { View event }`(forge: Forge) {
        // Given
        val fakeViewEvent: ViewEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { View event }`(forge: Forge) {
        // Given
        val fakeViewEvent: ViewEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<JsonObject>()
        verify(mockDataWriter).write(argumentCaptor.capture())
        val capturedMapViewEvent = ViewEvent.fromJson(argumentCaptor.firstValue.toString())
        DeserializedViewEventAssert(capturedMapViewEvent).isEqualTo(fakeMappedViewEvent)
    }

    @Test
    fun `M write the unmapped event W consume(){ no RUM context, view event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventAsJson = fakeViewEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEventAsJson,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )

        // Then
        verify(mockDataWriter).write(fakeViewEventAsJson)
    }

    @Test
    fun `M do nothing W consume(){ view event broken json }`(forge: Forge) {
        // Given
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventBrokenJson = fakeViewEvent
            .toJson()
            .asJsonObject
        fakeViewEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verifyZeroInteractions(mockDataWriter)
    }

    @Test
    fun `M log an sdk error W consume(){ view event broken json }`(forge: Forge) {
        // Given
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventBrokenJson = fakeViewEvent
            .toJson()
            .asJsonObject
        fakeViewEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            argThat { this is JsonParseException },
            any(),
            any(),
            anyOrNull()
        )
    }

    // endregion

    // region Action

    @Test
    fun `M send a noop WebViewEvent W consume { Action event }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapActionEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedActionEvent)

        // When
        testedRumEventConsumer.consume(
            fakeActionEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.ACTION_EVENT_TYPE
        )

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { Action event }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapActionEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedActionEvent)

        // When
        testedRumEventConsumer.consume(
            fakeActionEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.ACTION_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<JsonObject>()
        verify(mockDataWriter).write(argumentCaptor.capture())
        val capturedActionEvent = ActionEvent.fromJson(argumentCaptor.firstValue.toString())
        DeserializedActionEventAssert(capturedActionEvent).isEqualTo(fakeMappedActionEvent)
    }

    @Test
    fun `M write the unmapped event W consume(){ no RUM context, action event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventAsJson = fakeActionEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapActionEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedActionEvent)

        // When
        testedRumEventConsumer.consume(
            fakeActionEventAsJson,
            WebViewRumEventConsumer.ACTION_EVENT_TYPE
        )

        // Then
        verify(mockDataWriter).write(fakeActionEventAsJson)
    }

    @Test
    fun `M do nothing W consume(){ action event broken json }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventBrokenJson = fakeActionEvent
            .toJson()
            .asJsonObject
        fakeActionEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapActionEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeActionEvent)

        // When
        testedRumEventConsumer.consume(
            fakeActionEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verifyZeroInteractions(mockDataWriter)
    }

    @Test
    fun `M log an sdk error W consume(){ action event broken json }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventBrokenJson = fakeActionEvent
            .toJson()
            .asJsonObject
        fakeActionEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapActionEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeActionEvent)

        // When
        testedRumEventConsumer.consume(
            fakeActionEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            argThat { this is JsonParseException },
            any(),
            any(),
            anyOrNull()
        )
    }

    // endregion

    // region Resource

    @Test
    fun `M send a noop WebViewEvent W consume { Resource event }`(forge: Forge) {
        // Given
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapResourceEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedResourceEvent)

        // When
        testedRumEventConsumer.consume(
            fakeResourceEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.RESOURCE_EVENT_TYPE
        )

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }
    @Test
    fun `M consume the event W consume() { Resource event }`(forge: Forge) {
        // Given
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapResourceEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedResourceEvent)

        // When
        testedRumEventConsumer.consume(
            fakeResourceEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.RESOURCE_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<JsonObject>()
        verify(mockDataWriter).write(argumentCaptor.capture())
        val capturedResourceEvent = ResourceEvent.fromJson(argumentCaptor.firstValue.toString())
        DeserializedResourceEventAssert(capturedResourceEvent).isEqualTo(fakeMappedResourceEvent)
    }

    @Test
    fun `M write the unampped event W consume(){ no RUM context, resource event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventAsJson = fakeResourceEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapResourceEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedResourceEvent)

        // When
        testedRumEventConsumer.consume(
            fakeResourceEventAsJson,
            WebViewRumEventConsumer.RESOURCE_EVENT_TYPE
        )

        // Then
        verify(mockDataWriter).write(fakeResourceEventAsJson)
    }

    @Test
    fun `M do nothing W consume(){ resource event broken json }`(forge: Forge) {
        // Given
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventBrokenJson = fakeResourceEvent
            .toJson()
            .asJsonObject
        fakeResourceEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapResourceEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeResourceEvent)

        // When
        testedRumEventConsumer.consume(
            fakeResourceEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verifyZeroInteractions(mockDataWriter)
    }

    @Test
    fun `M log an sdk error W consume(){ resource event broken json }`(forge: Forge) {
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventBrokenJson = fakeResourceEvent
            .toJson()
            .asJsonObject
        fakeResourceEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapResourceEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeResourceEvent)

        // When
        testedRumEventConsumer.consume(
            fakeResourceEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            argThat { this is JsonParseException },
            any(),
            any(),
            anyOrNull()
        )
    }

    // endregion

    // region Error

    @Test
    fun `M send a noop WebViewEvent W consume { Error event }`(forge: Forge) {
        // Given
        val fakeActionEvent: ErrorEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapErrorEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedErrorEvent)

        // When
        testedRumEventConsumer.consume(
            fakeActionEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.ERROR_EVENT_TYPE
        )

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { Error event }`(forge: Forge) {
        // Given
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapErrorEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedErrorEvent)

        // When
        testedRumEventConsumer.consume(
            fakeErrorEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.ERROR_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<JsonObject>()
        verify(mockDataWriter).write(argumentCaptor.capture())
        val capturedErrorEvent = ErrorEvent.fromJson(argumentCaptor.firstValue.toString())
        DeserializedErrorEventAssert(capturedErrorEvent).isEqualTo(fakeMappedErrorEvent)
    }

    @Test
    fun `M write the unmapped event W consume(){ no RUM context, error event}`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventAsJson = fakeErrorEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapErrorEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedErrorEvent)

        // When
        testedRumEventConsumer.consume(
            fakeErrorEventAsJson,
            WebViewRumEventConsumer.ERROR_EVENT_TYPE
        )

        // Then
        verify(mockDataWriter).write(fakeErrorEventAsJson)
    }

    @Test
    fun `M do nothing W consume(){ error event broken json }`(forge: Forge) {
        // Given
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventBrokenJson = fakeErrorEvent
            .toJson()
            .asJsonObject
        fakeErrorEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapErrorEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeErrorEvent)

        // When
        testedRumEventConsumer.consume(
            fakeErrorEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verifyZeroInteractions(mockDataWriter)
    }

    @Test
    fun `M log an sdk error W consume(){ error event broken json }`(forge: Forge) {
        // Given
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventBrokenJson = fakeErrorEvent
            .toJson()
            .asJsonObject
        fakeErrorEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapErrorEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeErrorEvent)

        // When
        testedRumEventConsumer.consume(
            fakeErrorEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            argThat { this is JsonParseException },
            any(),
            any(),
            anyOrNull()
        )
    }

    // endregion

    // region LongTask

    @Test
    fun `M send a noop WebViewEvent W consume { LongTask event }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapLongTaskEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(
            fakeLongTaskEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { LongTask event }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapLongTaskEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(
            fakeLongTaskEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<JsonObject>()
        verify(mockDataWriter).write(argumentCaptor.capture())
        val capturedLongTaskEvent = LongTaskEvent.fromJson(argumentCaptor.firstValue.toString())
        DeserializedLongTaskEventAssert(capturedLongTaskEvent).isEqualTo(fakeMappedLongTaskEvent)
    }

    @Test
    fun `M write the unmapped event W consume(){ no RUM context, longtask event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventAsJson = fakeLongTaskEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapLongTaskEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(
            fakeLongTaskEventAsJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verify(mockDataWriter).write(fakeLongTaskEventAsJson)
    }

    @Test
    fun `M do nothing W consume(){ longtask event broken json }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventBrokenJson = fakeLongTaskEvent
            .toJson()
            .asJsonObject
        fakeLongTaskEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapLongTaskEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(
            fakeLongTaskEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verifyZeroInteractions(mockDataWriter)
    }

    @Test
    fun `M log an sdk error W consume(){ longtask event broken json }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventBrokenJson = fakeLongTaskEvent
            .toJson()
            .asJsonObject
        fakeLongTaskEventBrokenJson.addProperty("date", "aDate")
        whenever(
            mockWebViewRumEventMapper.mapLongTaskEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(
            fakeLongTaskEventBrokenJson,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            argThat { this is JsonParseException },
            any(),
            any(),
            anyOrNull()
        )
    }

    // endregion

    // region Offset Correction

    @Test
    fun `M persist the offset correction W consume(){ view with children }`(forge: Forge) {
        // Given
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerTimeOffsetInMillis)
            .thenReturn(forge.aLong())
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeViewResourceEvent = fakeResourceEvent.copy(
            view = fakeResourceEvent.view.copy(id = fakeViewEvent.view.id)
        )
        val fakeViewActionEvent = fakeActionEvent.copy(
            view = fakeActionEvent.view.copy(id = fakeViewEvent.view.id)
        )
        val fakeViewErrorEvent = fakeErrorEvent.copy(
            view = fakeErrorEvent.view.copy(id = fakeViewEvent.view.id)
        )
        val fakeViewLongTaskEvent = fakeLongTaskEvent.copy(
            view = fakeLongTaskEvent.view.copy(id = fakeViewEvent.view.id)
        )
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)
        whenever(
            mockWebViewRumEventMapper.mapLongTaskEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)
        whenever(
            mockWebViewRumEventMapper.mapResourceEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedResourceEvent)
        whenever(
            mockWebViewRumEventMapper.mapActionEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedActionEvent)
        whenever(
            mockWebViewRumEventMapper.mapErrorEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedErrorEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )
        testedRumEventConsumer.consume(
            fakeViewResourceEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.RESOURCE_EVENT_TYPE
        )
        testedRumEventConsumer.consume(
            fakeViewActionEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.ACTION_EVENT_TYPE
        )
        testedRumEventConsumer.consume(
            fakeViewLongTaskEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )
        testedRumEventConsumer.consume(
            fakeViewErrorEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.ERROR_EVENT_TYPE
        )

        // Then
        verify(mockWebViewRumEventMapper).mapViewEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeServerTimeOffsetInMillis)
        )
        verify(mockWebViewRumEventMapper).mapErrorEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeServerTimeOffsetInMillis)
        )
        verify(mockWebViewRumEventMapper).mapActionEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeServerTimeOffsetInMillis)
        )
        verify(mockWebViewRumEventMapper).mapResourceEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeServerTimeOffsetInMillis)
        )
        verify(mockWebViewRumEventMapper).mapLongTaskEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeServerTimeOffsetInMillis)
        )
    }

    @Test
    fun `M use same offset correct W consume { consecutive view event updates }`(forge: Forge) {
        // Given
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerTimeOffsetInMillis)
            .thenReturn(forge.aLong())
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEvent2: ViewEvent = fakeViewEvent.copy()
        val fakeViewEvent3: ViewEvent = fakeViewEvent2.copy()
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )
        testedRumEventConsumer.consume(
            fakeViewEvent2.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )
        testedRumEventConsumer.consume(
            fakeViewEvent3.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )

        // Then
        verify(mockWebViewRumEventMapper, times(3)).mapViewEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeServerTimeOffsetInMillis)
        )
    }

    @Test
    fun `M use dedicated offset correction W consume { consecutive different views }`(
        forge: Forge
    ) {
        // Given
        val fakeSecondServerTimeOffset = forge.aLong()
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerTimeOffsetInMillis)
            .thenReturn(fakeSecondServerTimeOffset)
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEvent2: ViewEvent = forge.getForgery()
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        )
            .thenReturn(fakeMappedViewEvent)
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeSecondServerTimeOffset)
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(
            fakeViewEvent.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )
        testedRumEventConsumer.consume(
            fakeViewEvent2.toJson().asJsonObject,
            WebViewRumEventConsumer.VIEW_EVENT_TYPE
        )

        // Then
        verify(mockWebViewRumEventMapper).mapViewEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeServerTimeOffsetInMillis)
        )
        verify(mockWebViewRumEventMapper).mapViewEvent(
            any(),
            eq(sessionScopeTestConfiguration.fakeRumContext),
            eq(fakeSecondServerTimeOffset)
        )
    }

    @Test
    fun `M purge the last used view W consume{ consecutive different views }`(forge: Forge) {
        // Given
        val size = forge.anInt(min = 1, max = 10)
        val fakeServerOffsets = forge.aList(size) {
            forge.aLong()
        }
        val fakeViewEvents = forge.aList(size) {
            forge.getForgery(ViewEvent::class.java)
        }
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerOffsets[0], *(fakeServerOffsets.drop(1).toTypedArray()))
        val expectedOffsets = LinkedHashMap<String, Long>()
        val expectedOffsetsKeys =
            fakeViewEvents
                .takeLast(WebViewRumEventConsumer.MAX_VIEW_TIME_OFFSETS_RETAIN)
                .map { it.view.id }
        val expectedOffsetsValues =
            fakeServerOffsets.takeLast(WebViewRumEventConsumer.MAX_VIEW_TIME_OFFSETS_RETAIN)
        expectedOffsetsKeys.forEachIndexed { index, key ->
            expectedOffsets[key] = expectedOffsetsValues[index]
        }
        whenever(
            mockWebViewRumEventMapper.mapViewEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                any()
            )
        )
            .thenReturn(fakeMappedViewEvent)

        // When
        fakeViewEvents.forEach {
            testedRumEventConsumer.consume(
                it.toJson().asJsonObject,
                WebViewRumEventConsumer.VIEW_EVENT_TYPE
            )
        }

        // Then
        assertThat(testedRumEventConsumer.offsets.entries)
            .containsExactlyElementsOf(expectedOffsets.entries)
    }

    // endregion

    // region Event Type

    @Test
    fun `M log an SDK error W consume() { eventType unknown, viewEvent }`(forge: Forge) {
        // Given
        val fakeUnknownEventType = forge.anUnknownEventType()
        val fakeRumEventAsJsonObject = forge.aRumEventAsJson()

        // When

        testedRumEventConsumer.consume(
            fakeRumEventAsJsonObject,
            fakeUnknownEventType
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewRumEventConsumer.WRONG_EVENT_TYPE_ERROR_MESSAGE.format(
                Locale.US,
                fakeUnknownEventType
            )
        )
    }

    @Test
    fun `M do nothing W consume() { eventType unknown }`(forge: Forge) {
        // Given
        val fakeUnknownEventType = forge.anUnknownEventType()
        val fakeRumEventAsJsonObject = forge.aRumEventAsJson()

        // When
        testedRumEventConsumer.consume(
            fakeRumEventAsJsonObject,
            fakeUnknownEventType
        )

        // Then
        verifyZeroInteractions(mockDataWriter)
    }

    // endregion

    // region Internal

    private fun Forge.aRumEventAsJson(): JsonObject {
        return anElementFrom(
            this.getForgery<ViewEvent>().toJson().asJsonObject,
            this.getForgery<LongTaskEvent>().toJson().asJsonObject,
            this.getForgery<ActionEvent>().toJson().asJsonObject,
            this.getForgery<ResourceEvent>().toJson().asJsonObject,
            this.getForgery<ErrorEvent>().toJson().asJsonObject
        )
    }

    private fun Forge.anUnknownEventType(): String {
        val knownTypes = setOf(
            WebViewRumEventConsumer.VIEW_EVENT_TYPE,
            WebViewRumEventConsumer.ACTION_EVENT_TYPE,
            WebViewRumEventConsumer.ERROR_EVENT_TYPE,
            WebViewRumEventConsumer.RESOURCE_EVENT_TYPE,
            WebViewRumEventConsumer.LONG_TASK_EVENT_TYPE
        )
        while (true) {
            val type = this.anAlphaNumericalString()
            if (type !in knownTypes) {
                return type
            }
        }
    }

    // endregion

    companion object {
        val sessionScopeTestConfiguration = SessionScopeTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(sessionScopeTestConfiguration)
        }
    }
}
