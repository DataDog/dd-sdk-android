/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aRumEventAsJson
import com.datadog.android.utils.verifyLog
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.replay.WebViewReplayEventConsumer
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewRumEventConsumerTest {

    lateinit var testedConsumer: WebViewEventConsumer<JsonObject>

    @Mock
    lateinit var mockDataWriter: DataWriter<JsonObject>

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
    lateinit var mockRumContextProvider: WebViewRumEventContextProvider

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockWebViewRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockOffsetProvider: TimestampOffsetProvider

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    @BoolForgery
    var fakeSessionReplayEnabled = false

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeRumContext = fakeRumContext.copy(sessionState = "TRACKED")
        fakeTags = if (forge.aBool()) {
            forge.aMap {
                forge.anAlphabeticalString() to forge.anAlphaNumericalString()
            }
        } else {
            emptyMap()
        }

        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                WebViewReplayEventConsumer.SESSION_REPLAY_ENABLED_KEY to fakeSessionReplayEnabled
            )
        )
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerTimeOffsetInMillis
            ),
            featuresContext = fakeFeaturesContext
        )

        whenever(
            mockRumContextProvider.getRumContext(any())
        ) doReturn fakeRumContext

        whenever(
            mockSdkCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)
        ) doReturn mockWebViewRumFeatureScope

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(
            mockWebViewRumFeatureScope.withWriteContext(
                eq(setOf(Feature.RUM_FEATURE_NAME, Feature.SESSION_REPLAY_FEATURE_NAME)), any()
            )
        ) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(
            mockOffsetProvider.getOffset(
                any(),
                eq(fakeDatadogContext)
            )
        ) doReturn fakeServerTimeOffsetInMillis
        testedConsumer = WebViewRumEventConsumer(
            mockSdkCore,
            mockDataWriter,
            mockOffsetProvider,
            mockWebViewRumEventMapper,
            mockRumContextProvider
        )
        fakeMappedViewEvent = forge.getForgery(ViewEvent::class.java).toJson().asJsonObject
        fakeMappedResourceEvent = forge.getForgery(ResourceEvent::class.java).toJson().asJsonObject
        fakeMappedLongTaskEvent = forge.getForgery(LongTaskEvent::class.java).toJson().asJsonObject
        fakeMappedErrorEvent = forge.getForgery(ErrorEvent::class.java).toJson().asJsonObject
        fakeMappedActionEvent = forge.getForgery(ActionEvent::class.java).toJson().asJsonObject
    }

    // region View

    @Test
    fun `M send a noop WebViewEvent W consume { View event }`(forge: Forge) {
        // Given
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventAsJson = fakeViewEvent.toJson().asJsonObject

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeViewEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedConsumer.consume(fakeViewEventAsJson)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "web_view_ingested_notification"
            )
        )
    }

    @Test
    fun `M consume the event W consume() { View event }`(forge: Forge) {
        // Given
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventAsJson = fakeViewEvent.toJson().asJsonObject

        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeViewEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedConsumer.consume(fakeViewEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedViewEvent, EventType.DEFAULT)
    }

    @Test
    fun `M not write the mapped event W consume(){ no RUM context, view event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext(fakeDatadogContext)) doReturn null
        val fakeViewEvent: ViewEvent = forge.getForgery()
        val fakeViewEventAsJson = fakeViewEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeViewEventAsJson,
                null,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedConsumer.consume(fakeViewEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    // endregion

    // region Action

    @Test
    fun `M send a noop WebViewEvent W consume { Action event }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventAsJson = fakeActionEvent.toJson().asJsonObject

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeActionEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedConsumer.consume(fakeActionEventAsJson)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "web_view_ingested_notification"
            )
        )
    }

    @Test
    fun `M consume the event W consume() { Action event }`(forge: Forge) {
        // Given
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventAsJson = fakeActionEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeActionEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedConsumer.consume(fakeActionEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedActionEvent, EventType.DEFAULT)
    }

    @Test
    fun `M not write the mapped event W consume(){ no RUM context, action event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext(fakeDatadogContext)) doReturn null
        val fakeActionEvent: ActionEvent = forge.getForgery()
        val fakeActionEventAsJson = fakeActionEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeActionEventAsJson,
                null,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedConsumer.consume(fakeActionEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    // endregion

    // region Resource

    @Test
    fun `M send a noop WebViewEvent W consume { Resource event }`(forge: Forge) {
        // Given
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventAsJson = fakeResourceEvent.toJson().asJsonObject

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeResourceEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedConsumer.consume(fakeResourceEventAsJson)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "web_view_ingested_notification"
            )
        )
    }

    @Test
    fun `M consume the event W consume() { Resource event }`(forge: Forge) {
        // Given
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventAsJson = fakeResourceEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeResourceEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedConsumer.consume(fakeResourceEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedResourceEvent, EventType.DEFAULT)
    }

    @Test
    fun `M not write the mapped event W consume(){ no RUM context, resource event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext(fakeDatadogContext)) doReturn null
        val fakeResourceEvent: ResourceEvent = forge.getForgery()
        val fakeResourceEventAsJson = fakeResourceEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeResourceEventAsJson,
                null,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedConsumer.consume(fakeResourceEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    // endregion

    // region Error

    @Test
    fun `M send a noop WebViewEvent W consume { Error event }`(forge: Forge) {
        // Given
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventAsJson = fakeErrorEvent.toJson().asJsonObject

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeErrorEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedConsumer.consume(fakeErrorEventAsJson)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "web_view_ingested_notification"
            )
        )
    }

    @Test
    fun `M consume the event W consume() { Error event }`(forge: Forge) {
        // Given
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventAsJson = fakeErrorEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeErrorEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedConsumer.consume(fakeErrorEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedErrorEvent, EventType.DEFAULT)
    }

    @Test
    fun `M not write the mapped event W consume(){ no RUM context, error event}`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext(fakeDatadogContext)) doReturn null
        val fakeErrorEvent: ErrorEvent = forge.getForgery()
        val fakeErrorEventAsJson = fakeErrorEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeErrorEventAsJson,
                null,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedConsumer.consume(fakeErrorEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    // endregion

    // region LongTask

    @Test
    fun `M send a noop WebViewEvent W consume { LongTask event }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventAsJson = fakeLongTaskEvent.toJson().asJsonObject

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeLongTaskEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedLongTaskEvent)

        // When
        testedConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "web_view_ingested_notification"
            )
        )
    }

    @Test
    fun `M consume the event W consume() { LongTask event }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventAsJson = fakeLongTaskEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeLongTaskEventAsJson,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedLongTaskEvent, EventType.DEFAULT)
    }

    @Test
    fun `M not write the mapped event W consume(){ no RUM context, longtask event }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext(fakeDatadogContext)) doReturn null
        val fakeLongTaskEvent: LongTaskEvent = forge.getForgery()
        val fakeLongTaskEventAsJson = fakeLongTaskEvent.toJson().asJsonObject
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeLongTaskEventAsJson,
                null,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedLongTaskEvent)

        // When
        testedConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
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
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenThrow(fakeException)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeRumEvent, EventType.DEFAULT)
    }

    @ParameterizedTest
    @MethodSource("mapperThrowsException")
    fun `M log an sdk error W consume { mapper throws }`(fakeException: Throwable, forge: Forge) {
        // Given
        val fakeRumEvent = forge.aRumEventAsJson()
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeRumEvent,
                fakeRumContext,
                fakeServerTimeOffsetInMillis,
                fakeSessionReplayEnabled
            )
        ).thenThrow(fakeException)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
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
                fakeRumContext,
                0,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedRumEvent, EventType.DEFAULT)
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
                fakeRumContext,
                0,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeRumEvent, EventType.DEFAULT)
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
                fakeRumContext,
                0,
                fakeSessionReplayEnabled
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            WebViewRumEventConsumer.JSON_PARSING_ERROR_MESSAGE,
            IllegalStateException::class.java
        )
    }

    // endregion

    // region Offset Correction

    // endregion

    companion object {

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
