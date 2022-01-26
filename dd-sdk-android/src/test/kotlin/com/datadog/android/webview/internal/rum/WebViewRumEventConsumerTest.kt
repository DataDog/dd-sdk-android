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
import com.datadog.android.utils.config.SessionScopeTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aRumEventAsJson
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ClassCastException
import kotlin.collections.LinkedHashMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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

    lateinit var fakeMappedViewEvent: JsonObject

    lateinit var fakeMappedResourceEvent: JsonObject

    lateinit var fakeMappedActionEvent: JsonObject

    lateinit var fakeMappedErrorEvent: JsonObject

    lateinit var fakeMappedLongTaskEvent: JsonObject

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
        fakeMappedViewEvent = forge.getForgery(ViewEvent::class.java).toJson().asJsonObject
        fakeMappedResourceEvent = forge.getForgery(ResourceEvent::class.java).toJson().asJsonObject
        fakeMappedLongTaskEvent = forge.getForgery(LongTaskEvent::class.java).toJson().asJsonObject
        fakeMappedErrorEvent = forge.getForgery(ErrorEvent::class.java).toJson().asJsonObject
        fakeMappedActionEvent = forge.getForgery(ActionEvent::class.java).toJson().asJsonObject
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
        val fakeViewEventAsJson = fakeViewEvent.toJson().asJsonObject

        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeViewEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(fakeViewEventAsJson)

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { View event }`(forge: Forge) {
        // Given
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventAsJson = fakeViewEvent.toJson().asJsonObject

        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeViewEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(fakeViewEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedViewEvent)
    }

    @Test
    fun `M write the mapped event W consume(){ no RUM context, view event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventAsJson = fakeViewEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeViewEventAsJson,
                null,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(fakeViewEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedViewEvent)
    }

    // endregion

    // region Action

    @Test
    fun `M send a noop WebViewEvent W consume { Action event }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventAsJson = fakeActionEvent.toJson().asJsonObject

        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeActionEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedRumEventConsumer.consume(fakeActionEventAsJson)

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { Action event }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventAsJson = fakeActionEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeActionEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedRumEventConsumer.consume(fakeActionEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedActionEvent)
    }

    @Test
    fun `M write the mapped event W consume(){ no RUM context, action event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventAsJson = fakeActionEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeActionEventAsJson,
                null,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedRumEventConsumer.consume(fakeActionEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedActionEvent)
    }

    // endregion

    // region Resource

    @Test
    fun `M send a noop WebViewEvent W consume { Resource event }`(forge: Forge) {
        // Given
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventAsJson = fakeResourceEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeResourceEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedRumEventConsumer.consume(fakeResourceEventAsJson)

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { Resource event }`(forge: Forge) {
        // Given
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventAsJson = fakeResourceEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeResourceEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedRumEventConsumer.consume(fakeResourceEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedResourceEvent)
    }

    @Test
    fun `M write the mapped event W consume(){ no RUM context, resource event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventAsJson = fakeResourceEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeResourceEventAsJson,
                null,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedRumEventConsumer.consume(fakeResourceEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedResourceEvent)
    }

    // endregion

    // region Error

    @Test
    fun `M send a noop WebViewEvent W consume { Error event }`(forge: Forge) {
        // Given
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventAsJson = fakeErrorEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeErrorEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedRumEventConsumer.consume(fakeErrorEventAsJson)

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { Error event }`(forge: Forge) {
        // Given
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventAsJson = fakeErrorEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeErrorEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedRumEventConsumer.consume(fakeErrorEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedErrorEvent)
    }

    @Test
    fun `M write the mapped event W consume(){ no RUM context, error event}`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventAsJson = fakeErrorEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeErrorEventAsJson,
                null,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedRumEventConsumer.consume(fakeErrorEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedErrorEvent)
    }

    // endregion

    // region LongTask

    @Test
    fun `M send a noop WebViewEvent W consume { LongTask event }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventAsJson = fakeLongTaskEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeLongTaskEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        val mockedMonitor = GlobalRum.monitor as AdvancedRumMonitor
        verify(mockedMonitor).sendWebViewEvent()
    }

    @Test
    fun `M consume the event W consume() { LongTask event }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventAsJson = fakeLongTaskEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeLongTaskEventAsJson,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedLongTaskEvent)
    }

    @Test
    fun `M write the mapped event W consume(){ no RUM context, longtask event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(null)
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventAsJson = fakeLongTaskEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeLongTaskEventAsJson,
                null,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedLongTaskEvent)

        // When
        testedRumEventConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        verify(mockDataWriter).write(fakeMappedLongTaskEvent)
    }

    // endregion

    // region Json Parsing

    @ParameterizedTest
    @MethodSource("mapperThrowsException")
    fun `M send original event W consume { mapper throws }`(
        fakeException: Throwable,
        forge: Forge
    ) {
        // Given
        val fakeRumEvent = forge.aRumEventAsJson()
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeRumEvent,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenThrow(fakeException)

        // When
        testedRumEventConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(fakeRumEvent)
    }

    @ParameterizedTest
    @MethodSource("mapperThrowsException")
    fun `M log an sdk error W consume { mapper throws }`(fakeException: Throwable, forge: Forge) {
        // Given
        val fakeRumEvent = forge.aRumEventAsJson()
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeRumEvent,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenThrow(fakeException)

        // When
        testedRumEventConsumer.consume(fakeRumEvent)

        // Then
        verify(mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE,
            fakeException
        )
    }

    @Test
    fun `M not correct timestamp W consume { viewId does not exist }`(forge: Forge) {
        // Given
        val fakeRumEvent = forge.aRumEventAsJson()
        val fakeMappedRumEvent = forge.aRumEventAsJson()
        fakeRumEvent.remove(WebViewRumEventConsumer.VIEW_KEY_NAME)
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeRumEvent,
                sessionScopeTestConfiguration.fakeRumContext,
                0
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedRumEventConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(fakeMappedRumEvent)
    }

    @Test
    fun `M send original event W consume { viewId has a wrong format }`(forge: Forge) {
        // Given
        val fakeRumEvent = forge.aRumEventAsJson()
        val fakeMappedRumEvent = forge.aRumEventAsJson()
        fakeRumEvent.getAsJsonObject(WebViewRumEventConsumer.VIEW_KEY_NAME)
            .add(WebViewRumEventConsumer.VIEW_ID_KEY_NAME, JsonArray())
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeRumEvent,
                sessionScopeTestConfiguration.fakeRumContext,
                0
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedRumEventConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(fakeRumEvent)
    }

    @Test
    fun `M log an sdk error W consume { viewId has a wrong format }`(forge: Forge) {
        // Given
        val fakeRumEvent = forge.aRumEventAsJson()
        val fakeMappedRumEvent = forge.aRumEventAsJson()
        fakeRumEvent.getAsJsonObject(WebViewRumEventConsumer.VIEW_KEY_NAME)
            .add(WebViewRumEventConsumer.VIEW_ID_KEY_NAME, JsonArray())
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeRumEvent,
                sessionScopeTestConfiguration.fakeRumContext,
                0
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedRumEventConsumer.consume(fakeRumEvent)

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    // endregion

    // region Offset Correction

    @Test
    fun `M use same offset correct W consume { consecutive event updates }`(forge: Forge) {
        // Given
        whenever(mockTimeProvider.getServerOffsetMillis())
            .thenReturn(fakeServerTimeOffsetInMillis)
            .thenReturn(forge.aLong())
        val fakeEvent = forge.aRumEventAsJson()
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedRumEventConsumer.consume(fakeEvent)
        testedRumEventConsumer.consume(fakeEvent)
        testedRumEventConsumer.consume(fakeEvent)

        // Then
        verify(mockWebViewRumEventMapper, times(3)).mapEvent(
            fakeEvent,
            sessionScopeTestConfiguration.fakeRumContext,
            fakeServerTimeOffsetInMillis
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
        val fakeEvent = forge.aRumEventAsJson()
        val fakeEvent2 = forge.aRumEventAsJson()
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeEvent,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeEvent)
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeEvent2,
                sessionScopeTestConfiguration.fakeRumContext,
                fakeSecondServerTimeOffset
            )
        )
            .thenReturn(fakeEvent2)

        // When
        testedRumEventConsumer.consume(fakeEvent)
        testedRumEventConsumer.consume(fakeEvent2)

        // Then
        verify(mockWebViewRumEventMapper).mapEvent(
            fakeEvent,
            sessionScopeTestConfiguration.fakeRumContext,
            fakeServerTimeOffsetInMillis
        )
        verify(mockWebViewRumEventMapper).mapEvent(
            fakeEvent2,
            sessionScopeTestConfiguration.fakeRumContext,
            fakeSecondServerTimeOffset
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
            mockWebViewRumEventMapper.mapEvent(
                any(),
                eq(sessionScopeTestConfiguration.fakeRumContext),
                any()
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        fakeViewEvents.forEach {
            testedRumEventConsumer.consume(it.toJson().asJsonObject)
        }

        // Then
        assertThat(testedRumEventConsumer.offsets.entries)
            .containsExactlyElementsOf(expectedOffsets.entries)
    }

    // endregion

    companion object {
        val sessionScopeTestConfiguration = SessionScopeTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(sessionScopeTestConfiguration)
        }

        @JvmStatic
        fun mapperThrowsException(): List<Throwable> {
            return listOf(
                ClassCastException(),
                NumberFormatException(),
                IllegalStateException(),
                UnsupportedOperationException()
            )
        }
    }
}
