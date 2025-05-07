/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.replay

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewReplayEventConsumerTest {

    private lateinit var testedConsumer: WebViewReplayEventConsumer

    @Forgery
    lateinit var fakeMappedEvent: JsonObject

    @LongForgery
    var fakeServerTimeOffsetInMillis: Long = 0L

    @Mock
    lateinit var mockWebViewReplayMapper: WebViewReplayEventMapper

    @Mock
    lateinit var mockRumContextProvider: WebViewRumEventContextProvider

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSessionReplayFeatureScope: FeatureScope

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    lateinit var fakeSessionReplayFeatureContext: Map<String, Any?>

    lateinit var fakeValidBrowserEvent: JsonObject

    @Mock
    lateinit var mockDataWriter: DataWriter<JsonObject>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSessionReplayFeatureContext = forge.aMap {
            WebViewReplayEventConsumer.SESSION_REPLAY_ENABLED_KEY to true
        }
        fakeValidBrowserEvent = forge.getForgery()
        fakeRumContext = fakeRumContext.copy(
            sessionState =
            WebViewReplayEventConsumer.SESSION_TRACKED_STATE
        )
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerTimeOffsetInMillis
            ),
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to fakeSessionReplayFeatureContext
            }
        )
        whenever(
            mockRumContextProvider.getRumContext(any())
        ) doReturn fakeRumContext

        whenever(
            mockSdkCore.getFeature(WebViewReplayFeature.WEB_REPLAY_FEATURE_NAME)
        ) doReturn mockSessionReplayFeatureScope
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockSessionReplayFeatureScope.withWriteContext(any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(0)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedConsumer = WebViewReplayEventConsumer(
            mockSdkCore,
            mockDataWriter,
            mockRumContextProvider,
            mockWebViewReplayMapper
        )
    }

    @Test
    fun `M send the event W consume() { valid event }`() {
        // Given
        whenever(
            mockWebViewReplayMapper.mapEvent(
                fakeValidBrowserEvent,
                fakeRumContext,
                fakeDatadogContext
            )
        ).thenReturn(fakeMappedEvent)

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedEvent, EventType.DEFAULT)
    }

    @Test
    fun `M do nothing W consume() { sr feature not registered }`() {
        // Given
        whenever(
            mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
        ) doReturn null

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { sr feature not enabled }`(forge: Forge) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to forge.aMap {
                    WebViewReplayEventConsumer.SESSION_REPLAY_ENABLED_KEY to false
                }
            }
        )

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { sr feature context does not exist }`() {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(featuresContext = mapOf())

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { sr feature enabled entry does not exist }`(forge: Forge) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to mapOf()
            }
        )

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { datadogContext not there }`() {
        // Given
        whenever(
            mockSdkCore.getDatadogContext()
        ) doReturn null

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { rumContext not there }`() {
        // Given
        whenever(
            mockRumContextProvider.getRumContext(fakeDatadogContext)
        ) doReturn null

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { rum session sampled out }`(forge: Forge) {
        // Given
        whenever(
            mockRumContextProvider.getRumContext(fakeDatadogContext)
        ) doReturn fakeRumContext.copy(sessionState = forge.anAlphabeticalString())

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @ParameterizedTest
    @MethodSource("mapperThrowsException")
    fun `M log an sdk error W consume { mapper throws }`(fakeException: Throwable) {
        // Given
        whenever(
            mockWebViewReplayMapper.mapEvent(
                fakeValidBrowserEvent,
                fakeRumContext,
                fakeDatadogContext
            )
        ).thenThrow(fakeException)

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            WebViewReplayEventConsumer.JSON_PARSING_ERROR_MESSAGE,
            fakeException
        )
    }

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
