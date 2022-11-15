/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview

import android.util.Log
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import com.datadog.android.utils.assertj.JsonElementAssert.Companion.assertThat
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MixedWebViewEventConsumerTest {

    lateinit var testedWebViewEventConsumer: MixedWebViewEventConsumer

    @Mock
    lateinit var mockRumEventConsumer: WebViewRumEventConsumer

    @Mock
    lateinit var mockLogsEventConsumer: WebViewLogEventConsumer

    @BeforeEach
    fun `set up`() {
        testedWebViewEventConsumer = MixedWebViewEventConsumer(
            mockRumEventConsumer,
            mockLogsEventConsumer
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
            assertThat(firstValue).isEqualTo(fakeBundledEvent)
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
            assertThat(firstValue.first).isEqualTo(fakeBundledEvent)
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
        verify(logger.mockSdkLogHandler).handleLog(
            Log.ERROR,
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
        verify(logger.mockSdkLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
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
        verify(logger.mockSdkLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
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
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(
                MixedWebViewEventConsumer.WEB_EVENT_PARSING_ERROR_MESSAGE.format(
                    US,
                    fakeBadJsonFormatEvent
                )
            ),
            argThat<Throwable> { this is JsonParseException },
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
            fakeWebEvent.add(MixedWebViewEventConsumer.EVENT_KEY, it)
        }
        eventType?.let {
            fakeWebEvent.addProperty(MixedWebViewEventConsumer.EVENT_TYPE_KEY, it)
        }
        return fakeWebEvent
    }

    // endregion

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
