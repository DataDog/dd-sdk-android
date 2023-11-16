/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aRumEventAsJson
import com.datadog.android.utils.verifyLog
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

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

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerTimeOffsetInMillis
            )
        )

        whenever(
            mockRumContextProvider.getRumContext(any())
        ) doReturn fakeRumContext

        whenever(
            mockSdkCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)
        ) doReturn mockWebViewRumFeatureScope

        whenever(mockWebViewRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedConsumer = WebViewRumEventConsumer(
            mockSdkCore,
            mockDataWriter,
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
                fakeServerTimeOffsetInMillis
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedConsumer.consume(fakeViewEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedViewEvent)
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedConsumer.consume(fakeViewEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any())
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
                fakeServerTimeOffsetInMillis
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedConsumer.consume(fakeActionEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedActionEvent)
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedActionEvent)

        // When
        testedConsumer.consume(fakeActionEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any())
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
                fakeServerTimeOffsetInMillis
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedConsumer.consume(fakeResourceEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedResourceEvent)
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedResourceEvent)

        // When
        testedConsumer.consume(fakeResourceEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any())
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
                fakeServerTimeOffsetInMillis
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedConsumer.consume(fakeErrorEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedErrorEvent)
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedErrorEvent)

        // When
        testedConsumer.consume(fakeErrorEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any())
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
                fakeServerTimeOffsetInMillis
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
                fakeServerTimeOffsetInMillis
            )
        )
            .thenReturn(fakeMappedLongTaskEvent)

        // When
        testedConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedLongTaskEvent)
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
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeMappedLongTaskEvent)

        // When
        testedConsumer.consume(fakeLongTaskEventAsJson)

        // Then
        verify(mockDataWriter, never()).write(any(), any())
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
                fakeServerTimeOffsetInMillis
            )
        ).thenThrow(fakeException)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeRumEvent)
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
                fakeServerTimeOffsetInMillis
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
                0
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedRumEvent)
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
                0
            )
        ).thenReturn(fakeMappedRumEvent)

        // When
        testedConsumer.consume(fakeRumEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeRumEvent)
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
                0
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

    @Test
    fun `M use same offset correct W consume { consecutive event updates }`(forge: Forge) {
        // Given
        var invocationCount = 0
        whenever(mockWebViewRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(
                fakeDatadogContext.copy(
                    time = fakeDatadogContext.time.copy(
                        serverTimeOffsetMs = if (invocationCount == 0) {
                            fakeServerTimeOffsetInMillis
                        } else {
                            forge.aLong()
                        }
                    )
                ),
                mockEventBatchWriter
            )
            invocationCount++
            Unit
        }

        val fakeEvent = forge.aRumEventAsJson()
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                any(),
                eq(fakeRumContext),
                eq(fakeServerTimeOffsetInMillis)
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        testedConsumer.consume(fakeEvent)
        testedConsumer.consume(fakeEvent)
        testedConsumer.consume(fakeEvent)

        // Then
        verify(mockWebViewRumEventMapper, times(3)).mapEvent(
            fakeEvent,
            fakeRumContext,
            fakeServerTimeOffsetInMillis
        )
    }

    @Test
    fun `M use dedicated offset correction W consume { consecutive different views }`(
        forge: Forge
    ) {
        // Given
        val fakeSecondServerTimeOffset = forge.aLong()
        var invocationCount = 0
        whenever(mockWebViewRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(
                fakeDatadogContext.copy(
                    time = fakeDatadogContext.time.copy(
                        serverTimeOffsetMs = if (invocationCount == 0) {
                            fakeServerTimeOffsetInMillis
                        } else {
                            fakeSecondServerTimeOffset
                        }
                    )
                ),
                mockEventBatchWriter
            )
            invocationCount++
            Unit
        }

        val fakeEvent = forge.aRumEventAsJson()
        val fakeEvent2 = forge.aRumEventAsJson()
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeEvent,
                fakeRumContext,
                fakeServerTimeOffsetInMillis
            )
        ).thenReturn(fakeEvent)
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                fakeEvent2,
                fakeRumContext,
                fakeSecondServerTimeOffset
            )
        ).thenReturn(fakeEvent2)

        // When
        testedConsumer.consume(fakeEvent)
        testedConsumer.consume(fakeEvent2)

        // Then
        verify(mockWebViewRumEventMapper).mapEvent(
            fakeEvent,
            fakeRumContext,
            fakeServerTimeOffsetInMillis
        )
        verify(mockWebViewRumEventMapper).mapEvent(
            fakeEvent2,
            fakeRumContext,
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

        var invocationCount = 0
        whenever(mockWebViewRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(
                fakeDatadogContext.copy(
                    time = fakeDatadogContext.time.copy(
                        serverTimeOffsetMs = fakeServerOffsets[invocationCount]
                    )
                ),
                mockEventBatchWriter
            )
            invocationCount++
            Unit
        }

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
                eq(fakeRumContext),
                any()
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        fakeViewEvents.forEach {
            testedConsumer.consume(it.toJson().asJsonObject)
        }

        // Then
        val rumEventConsumer = testedConsumer as WebViewRumEventConsumer
        assertThat(rumEventConsumer.offsets.entries)
            .containsExactlyElementsOf(expectedOffsets.entries)
    }

    @Test
    fun `M purge the last used view W consume{ consecutive different views, async EWC }`(
        forge: Forge
    ) {
        // Given
        val size = forge.anInt(min = 1, max = 10)
        val fakeServerOffsets = forge.aList(size) {
            forge.aLong()
        }
        val fakeViewEvents = forge.aList(size) {
            forge.getForgery(ViewEvent::class.java)
        }

        var invocationCount = 0
        val latch = CountDownLatch(size)
        whenever(mockWebViewRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            val invocation = invocationCount
            Thread {
                callback.invoke(
                    fakeDatadogContext.copy(
                        time = fakeDatadogContext.time.copy(
                            serverTimeOffsetMs = fakeServerOffsets[invocation]
                        )
                    ),
                    mockEventBatchWriter
                )
                latch.countDown()
            }.start()
            invocationCount++
            Unit
        }

        val expectedOffsets = LinkedHashMap<String, Long>()
        val expectedOffsetsKeys = fakeViewEvents.map { it.view.id }
        expectedOffsetsKeys.forEachIndexed { index, key ->
            expectedOffsets[key] = fakeServerOffsets[index]
        }
        whenever(
            mockWebViewRumEventMapper.mapEvent(
                any(),
                eq(fakeRumContext),
                any()
            )
        ).thenReturn(fakeMappedViewEvent)

        // When
        fakeViewEvents.forEach {
            testedConsumer.consume(it.toJson().asJsonObject)
        }
        latch.await(1, TimeUnit.SECONDS)

        // Then
        val rumEventConsumer = testedConsumer as WebViewRumEventConsumer
        // Because the threads are processed in any order,
        // we can't guarantee the order of the entries, and can only assert
        // the size of the offsets, and the pairs of key values in it
        assertThat(rumEventConsumer.offsets.entries)
            .containsAnyElementsOf(expectedOffsets.entries)
            .hasSizeLessThanOrEqualTo(WebViewRumEventConsumer.MAX_VIEW_TIME_OFFSETS_RETAIN)
    }

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
