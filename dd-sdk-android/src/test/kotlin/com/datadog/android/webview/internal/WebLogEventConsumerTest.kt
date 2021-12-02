/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal

import android.util.Log
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.assertj.DeserializedLogEventAssert
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.google.gson.JsonArray
import com.google.gson.JsonParseException
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
internal class WebLogEventConsumerTest {

    lateinit var testedLogEventConsumer: WebLogEventConsumer

    @Mock
    lateinit var mockUserLogsWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockInternalLogsWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockRumContextProvider: WebRumEventContextProvider

    lateinit var originalSdkLogHandler: LogHandler

    @Mock
    lateinit var mockSdkLogHandler: LogHandler

    @StringForgery(regex = "[0-9]\\.[0-9]\\.[0-9]")
    lateinit var fakePackageVersion: String

    @StringForgery
    lateinit var fakeEnvName: String

    // region Unit Tests

    @BeforeEach
    fun `set up`() {
        CoreFeature.envName = fakeEnvName
        CoreFeature.packageVersion = fakePackageVersion

        testedLogEventConsumer = WebLogEventConsumer(
            mockUserLogsWriter,
            mockInternalLogsWriter,
            mockRumContextProvider
        )
        originalSdkLogHandler = mockSdkLogHandler(mockSdkLogHandler)
    }

    @AfterEach
    fun `tear down`() {
        restoreSdkLogHandler(originalSdkLogHandler)
    }

    @Test
    fun `M write the user event W consume { user event type }`(@Forgery fakeLogEvent: LogEvent) {

        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockUserLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue).isEqualTo(
            fakeLogEvent.copy(
                ddtags = mobileSdkDdtags() +
                    WebLogEventConsumer.DDTAGS_SEPARATOR +
                    fakeLogEvent.ddtags
            )
        )
    }

    @Test
    fun `M write the user event W consume { internal log event type }`(
        @Forgery fakeLogEvent: LogEvent
    ) {

        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockInternalLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue).isEqualTo(
            fakeLogEvent.copy(
                ddtags = mobileSdkDdtags() +
                    WebLogEventConsumer.DDTAGS_SEPARATOR +
                    fakeLogEvent.ddtags
            )
        )
    }

    @Test
    fun `M write a mapped event W consume { user event type, rum context }`(
        @Forgery fakeLogEvent: LogEvent,
        @Forgery fakeRumContext: RumContext
    ) {

        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        val resolvedProperties = fakeLogEvent.additionalProperties.toMutableMap()
        resolvedProperties[LogAttributes.RUM_APPLICATION_ID] = fakeRumContext.applicationId
        resolvedProperties[LogAttributes.RUM_SESSION_ID] = fakeRumContext.sessionId
        val expectedMappedEvent = fakeLogEvent.copy(
            additionalProperties = resolvedProperties,
            ddtags = mobileSdkDdtags() +
                WebLogEventConsumer.DDTAGS_SEPARATOR +
                fakeLogEvent.ddtags

        )
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockUserLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue)
            .isEqualTo(expectedMappedEvent)
    }

    @Test
    fun `M write a mapped event W consume { internal log event type, rum context }`(
        @Forgery fakeLogEvent: LogEvent,
        @Forgery fakeRumContext: RumContext
    ) {

        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        val resolvedProperties = fakeLogEvent.additionalProperties.toMutableMap()
        resolvedProperties[LogAttributes.RUM_APPLICATION_ID] = fakeRumContext.applicationId
        resolvedProperties[LogAttributes.RUM_SESSION_ID] = fakeRumContext.sessionId
        val expectedMappedEvent = fakeLogEvent.copy(
            additionalProperties = resolvedProperties,
            ddtags = mobileSdkDdtags() +
                WebLogEventConsumer.DDTAGS_SEPARATOR +
                fakeLogEvent.ddtags,
        )
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockInternalLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue)
            .isEqualTo(expectedMappedEvent)
    }

    @Test
    fun `M do nothing W consume { bad format json object }`(
        @Forgery fakeLogEvent: LogEvent,
        forge: Forge
    ) {

        // Given
        val fakeJsonArray = JsonArray().apply {
            forge.aList(size = forge.anInt(min = 2, max = 10)) { forge.anAlphabeticalString() }
                .forEach {
                    this.add(it)
                }
        }
        val fakeLogEventAsBrokenJson = fakeLogEvent.toJson().asJsonObject.apply {
            add("date", fakeJsonArray)
        }

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsBrokenJson,
            forge.anElementFrom(WebLogEventConsumer.LOG_EVENT_TYPES)
        )

        // Then
        verifyZeroInteractions(mockInternalLogsWriter)
        verifyZeroInteractions(mockUserLogsWriter)
    }

    @Test
    fun `M log an sdk error W consume { bad format json object }`(
        @Forgery fakeLogEvent: LogEvent,
        forge: Forge
    ) {

        // Given
        val fakeJsonArray = JsonArray().apply {
            forge.aList(size = forge.anInt(min = 2, max = 10)) { forge.anAlphabeticalString() }
                .forEach {
                    this.add(it)
                }
        }
        val fakeLogEventAsBrokenJson = fakeLogEvent.toJson().asJsonObject.apply {
            add("date", fakeJsonArray)
        }

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsBrokenJson,
            forge.anElementFrom(WebLogEventConsumer.LOG_EVENT_TYPES)
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebLogEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            argThat {
                this is JsonParseException
            },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    @Test
    fun `M merge the attached ddtags into the local ones W consume { user log event type }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // When
        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockUserLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                fakeLogEvent.copy(
                    ddtags = mobileSdkDdtags() +
                        WebLogEventConsumer.DDTAGS_SEPARATOR +
                        fakeLogEvent.ddtags
                )
            )
    }

    @Test
    fun `M merge the attached ddtags into the local ones W consume { internal log event type }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // When
        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockInternalLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                fakeLogEvent.copy(
                    ddtags = mobileSdkDdtags() +
                        WebLogEventConsumer.DDTAGS_SEPARATOR +
                        fakeLogEvent.ddtags
                )
            )
    }

    @Test
    fun `M add local ddtags W consume { user log, no extra ddtags }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // When
        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject.apply {
            this.remove(WebLogEventConsumer.DDTAGS_KEY_NAME)
        }

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockUserLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue)
            .isEqualTo(fakeLogEvent.copy(ddtags = mobileSdkDdtags()))
    }

    @Test
    fun `M add local ddtags W consume { internal log, no exta ddtags }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // When
        // Given
        val fakeLogEventAsJson = fakeLogEvent.toJson().asJsonObject.apply {
            this.remove(WebLogEventConsumer.DDTAGS_KEY_NAME)
        }

        // When
        testedLogEventConsumer.consume(
            fakeLogEventAsJson,
            WebLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        val argumentCaptor = argumentCaptor<LogEvent>()
        verify(mockInternalLogsWriter).write(argumentCaptor.capture())
        DeserializedLogEventAssert.assertThat(argumentCaptor.firstValue)
            .isEqualTo(fakeLogEvent.copy(ddtags = mobileSdkDdtags()))
    }

    // endregion

    // region Internal

    private fun mobileSdkDdtags(): String {
        return "${LogAttributes.APPLICATION_VERSION}:$fakePackageVersion" +
            ",${LogAttributes.ENV}:$fakeEnvName"
    }

    // endregion
}
