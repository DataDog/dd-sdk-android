/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal

import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale.US

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MixedWebViewEventConsumerTest {

    lateinit var testedWebViewEventConsumer: MixedWebViewEventConsumer

    @Mock
    lateinit var mockRumEventConsumer: WebViewRumEventConsumer

    @Mock
    lateinit var mockLogsEventConsumer: WebViewLogEventConsumer

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedWebViewEventConsumer = MixedWebViewEventConsumer(
            mockRumEventConsumer,
            mockLogsEventConsumer,
            mockInternalLogger
        )
    }

    // region Unit Tests

    @Test
    fun `M delegate to RumEventConsumer W consume() { when RUM eventType }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        val fakeRumEventType = forge.anElementFrom(WebViewRumEventConsumer.RUM_EVENT_TYPES)
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeRumEventType)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        argumentCaptor<JsonObject> {
            verify(mockRumEventConsumer).consume(capture())
            // toString call because of how Gson is comparing Float/Double vs LazilyParsedNumber
            // for JsonPrimitive https://github.com/google/gson/issues/1864
            assertThat(firstValue.toString()).isEqualTo(fakeBundledEvent.toString())
        }
    }

    @Test
    fun `M delegate to LogsEventConsumer W consume() { LOG eventType }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        val fakeLogEventType = forge.anElementFrom(WebViewLogEventConsumer.LOG_EVENT_TYPES)
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeLogEventType)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        argumentCaptor<Pair<JsonObject, String>> {
            verify(mockLogsEventConsumer).consume(capture())
            // toString call because of how Gson is comparing Float/Double vs LazilyParsedNumber
            // for JsonPrimitive https://github.com/google/gson/issues/1864
            assertThat(firstValue.first.toString()).isEqualTo(fakeBundledEvent.toString())
            assertThat(firstValue.second).isEqualTo(fakeLogEventType)
        }
    }

    @Test
    fun `M do nothing W consume() { unknown event type }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        var fakeUnknownEventType = forge.anAlphabeticalString()
        while (fakeUnknownEventType in WebViewRumEventConsumer.RUM_EVENT_TYPES ||
            fakeUnknownEventType in WebViewLogEventConsumer.LOG_EVENT_TYPES
        ) {
            fakeUnknownEventType = forge.anAlphabeticalString()
        }
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeUnknownEventType)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        verifyZeroInteractions(mockLogsEventConsumer)
        verifyZeroInteractions(mockRumEventConsumer)
    }

    @Test
    fun `M log internal error event W consume() { unknown event type }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        var fakeUnknownEventType = forge.anAlphabeticalString()
        while (fakeUnknownEventType in WebViewRumEventConsumer.RUM_EVENT_TYPES ||
            fakeUnknownEventType in WebViewLogEventConsumer.LOG_EVENT_TYPES
        ) {
            fakeUnknownEventType = forge.anAlphabeticalString()
        }
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeUnknownEventType)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            MixedWebViewEventConsumer.WRONG_EVENT_TYPE_ERROR_MESSAGE.format(
                US,
                fakeUnknownEventType
            )
        )
    }

    @Test
    fun `M do nothing W consume() { no event type }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, null)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        verifyZeroInteractions(mockLogsEventConsumer)
        verifyZeroInteractions(mockRumEventConsumer)
    }

    @Test
    fun `M log internal error event W consume() { no event type }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, null)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            MixedWebViewEventConsumer.WEB_EVENT_MISSING_TYPE_ERROR_MESSAGE.format(
                US,
                fakeWebEvent
            )
        )
    }

    @Test
    fun `M do nothing W consume() { no event }`(forge: Forge) {
        // Given
        val fakeEventType = forge.anAlphabeticalString()
        val fakeWebEvent = bundleWebEvent(null, fakeEventType)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        verifyZeroInteractions(mockLogsEventConsumer)
        verifyZeroInteractions(mockRumEventConsumer)
    }

    @Test
    fun `M log internal error event W consume() { no event }`(forge: Forge) {
        // Given
        val fakeEventType = forge.anAlphabeticalString()
        val fakeWebEvent = bundleWebEvent(null, fakeEventType)

        // When
        testedWebViewEventConsumer.consume(fakeWebEvent.toString())

        // Then
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            MixedWebViewEventConsumer.WEB_EVENT_MISSING_WRAPPED_EVENT.format(US, fakeWebEvent)
        )
    }

    @Test
    fun `M do nothing W consume(){ bad json format }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        var fakeEventType = forge.anAlphabeticalString()
        while (fakeEventType == MixedWebViewEventConsumer.LOG_EVENT_TYPE) {
            fakeEventType = forge.anAlphabeticalString()
        }
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeEventType)
        val fakeBadJsonFormatEvent = fakeWebEvent.toString() + forge.anAlphabeticalString()

        // When
        testedWebViewEventConsumer.consume(fakeBadJsonFormatEvent)

        // Then
        verifyZeroInteractions(mockLogsEventConsumer)
        verifyZeroInteractions(mockRumEventConsumer)
    }

    @Test
    fun `M log internal error W consume(){ bad json format }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        var fakeEventType = forge.anAlphabeticalString()
        while (fakeEventType == MixedWebViewEventConsumer.LOG_EVENT_TYPE) {
            fakeEventType = forge.anAlphabeticalString()
        }
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeEventType)
        val fakeBadJsonFormatEvent = fakeWebEvent.toString() + forge.anAlphabeticalString()

        // When
        testedWebViewEventConsumer.consume(fakeBadJsonFormatEvent)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            targets = eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
            eq(
                MixedWebViewEventConsumer.WEB_EVENT_PARSING_ERROR_MESSAGE.format(
                    US,
                    fakeBadJsonFormatEvent
                )
            ),
            argThat { this is JsonParseException }
        )
    }

    // endregion

    // region Internal

    private fun bundleWebEvent(
        fakeBundledEvent: JsonObject?,
        eventType: String?
    ): JsonObject {
        val fakeWebEvent = JsonObject()
        fakeBundledEvent?.let {
            fakeWebEvent.add(MixedWebViewEventConsumer.EVENT_KEY, it)
        }
        eventType?.let {
            fakeWebEvent.addProperty(MixedWebViewEventConsumer.EVENT_TYPE_KEY, it)
        }
        return fakeWebEvent
    }

    // endregion
}
