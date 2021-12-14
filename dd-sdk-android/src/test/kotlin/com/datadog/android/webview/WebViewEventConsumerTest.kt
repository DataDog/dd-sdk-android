/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import android.util.Log
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale.US
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
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewEventConsumerTest {

    lateinit var testedWebViewEventConsumer: WebViewEventConsumer

    @Mock
    lateinit var mockRumEventConsumer: WebViewRumEventConsumer

    @Mock
    lateinit var mockLogsEventConsumer: WebViewLogEventConsumer

    @Mock
    lateinit var mockSdkLogHandler: LogHandler

    lateinit var originalSdkLogHandler: LogHandler

    @BeforeEach
    fun `set up`() {
        originalSdkLogHandler = mockSdkLogHandler(mockSdkLogHandler)
        testedWebViewEventConsumer = WebViewEventConsumer(
            mockRumEventConsumer,
            mockLogsEventConsumer
        )
    }

    @AfterEach
    fun `tear down`() {
        restoreSdkLogHandler(originalSdkLogHandler)
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
        verify(mockRumEventConsumer).consume(
            fakeBundledEvent,
            fakeRumEventType
        )
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
        verify(mockLogsEventConsumer).consume(fakeBundledEvent, fakeLogEventType)
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
        verify(mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewEventConsumer.WRONG_EVENT_TYPE_ERROR_MESSAGE.format(
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
        verify(mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewEventConsumer.WEB_EVENT_MISSING_TYPE_ERROR_MESSAGE.format(
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
        verify(mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewEventConsumer.WEB_EVENT_MISSING_WRAPPED_EVENT.format(US, fakeWebEvent)
        )
    }

    @Test
    fun `M do nothing W consume(){ bad json format }`(forge: Forge) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        var fakeEventType = forge.anAlphabeticalString()
        while (fakeEventType == WebViewEventConsumer.LOG_EVENT_TYPE) {
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
        while (fakeEventType == WebViewEventConsumer.LOG_EVENT_TYPE) {
            fakeEventType = forge.anAlphabeticalString()
        }
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeEventType)
        val fakeBadJsonFormatEvent = fakeWebEvent.toString() + forge.anAlphabeticalString()

        // When
        testedWebViewEventConsumer.consume(fakeBadJsonFormatEvent)

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                WebViewEventConsumer.WEB_EVENT_PARSING_ERROR_MESSAGE.format(
                    US,
                    fakeBadJsonFormatEvent
                )
            ),
            argThat { this is JsonParseException },
            any(),
            any(),
            anyOrNull()
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
            fakeWebEvent.add(WebViewEventConsumer.EVENT_KEY, it)
        }
        eventType?.let {
            fakeWebEvent.addProperty(WebViewEventConsumer.EVENT_TYPE_KEY, it)
        }
        return fakeWebEvent
    }

    // endregion
}
