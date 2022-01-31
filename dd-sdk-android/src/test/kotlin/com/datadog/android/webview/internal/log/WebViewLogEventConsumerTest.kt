/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.util.Log
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.stream.Stream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
internal class WebViewLogEventConsumerTest {

    lateinit var testedConsumer: WebViewEventConsumer<Pair<JsonObject, String>>

    @Mock
    lateinit var mockUserLogsWriter: DataWriter<JsonObject>

    @Mock
    lateinit var mockInternalLogsWriter: DataWriter<JsonObject>

    @Mock
    lateinit var mockRumContextProvider: WebViewRumEventContextProvider

    lateinit var originalSdkLogHandler: LogHandler

    @Mock
    lateinit var mockSdkLogHandler: LogHandler

    @StringForgery(regex = "[0-9]\\.[0-9]\\.[0-9]")
    lateinit var fakePackageVersion: String

    @StringForgery
    lateinit var fakeEnvName: String

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    lateinit var fakeWebLogEvent: JsonObject

    var fakeTimeOffset: Long = 0L

    // region Unit Tests

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeWebLogEvent = forge.aWebLogEvent()
        fakeTimeOffset = forge.aLong()
        CoreFeature.envName = fakeEnvName
        CoreFeature.packageVersion = fakePackageVersion

        testedConsumer = WebViewLogEventConsumer(
            mockUserLogsWriter,
            mockInternalLogsWriter,
            mockRumContextProvider,
            mockTimeProvider
        )
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeTimeOffset)
        originalSdkLogHandler = mockSdkLogHandler(mockSdkLogHandler)
    }

    @AfterEach
    fun `tear down`() {
        restoreSdkLogHandler(originalSdkLogHandler)
    }

    @Test
    fun `M write the user event W consume { user event type }`() {
        // Given
        val expectedDate = fakeTimeOffset +
            fakeWebLogEvent.getAsJsonPrimitive(WebViewLogEventConsumer.DATE_KEY_NAME).asLong
        var expectedTags = mobileSdkDdtags()
        fakeWebLogEvent.get(WebViewLogEventConsumer.DDTAGS_KEY_NAME)?.asString?.let {
            expectedTags += WebViewLogEventConsumer.DDTAGS_SEPARATOR + it
        }

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockUserLogsWriter).write(capture())
            assertThat(firstValue).hasField(WebViewLogEventConsumer.DATE_KEY_NAME, expectedDate)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
        }
    }

    @Test
    fun `M write the user event W consume { internal log event type }`() {
        // Given
        val expectedDate = fakeTimeOffset +
            fakeWebLogEvent.get(WebViewLogEventConsumer.DATE_KEY_NAME).asLong
        var expectedTags = mobileSdkDdtags()
        fakeWebLogEvent.get(WebViewLogEventConsumer.DDTAGS_KEY_NAME)?.asString?.let {
            expectedTags += WebViewLogEventConsumer.DDTAGS_SEPARATOR + it
        }

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockInternalLogsWriter).write(capture())
            assertThat(firstValue).hasField(WebViewLogEventConsumer.DATE_KEY_NAME, expectedDate)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
        }
    }

    @Test
    fun `M write a mapped event W consume { user event type, rum context }`(
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        val expectedDate = fakeTimeOffset +
            fakeWebLogEvent.get(WebViewLogEventConsumer.DATE_KEY_NAME).asLong
        var expectedTags = mobileSdkDdtags()
        fakeWebLogEvent.get(WebViewLogEventConsumer.DDTAGS_KEY_NAME)?.asString?.let {
            expectedTags += WebViewLogEventConsumer.DDTAGS_SEPARATOR + it
        }

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockUserLogsWriter).write(capture())
            assertThat(firstValue).hasField(WebViewLogEventConsumer.DATE_KEY_NAME, expectedDate)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
            assertThat(firstValue).hasField(
                LogAttributes.RUM_APPLICATION_ID,
                fakeRumContext.applicationId
            )
            assertThat(firstValue).hasField(
                LogAttributes.RUM_SESSION_ID,
                fakeRumContext.sessionId
            )
        }
    }

    @Test
    fun `M write a mapped event W consume { internal log event type, rum context }`(
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        val expectedDate = fakeTimeOffset +
            fakeWebLogEvent.get(WebViewLogEventConsumer.DATE_KEY_NAME).asLong
        var expectedTags = mobileSdkDdtags()
        fakeWebLogEvent.get(WebViewLogEventConsumer.DDTAGS_KEY_NAME)?.asString?.let {
            expectedTags += WebViewLogEventConsumer.DDTAGS_SEPARATOR + it
        }

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockInternalLogsWriter).write(capture())
            assertThat(firstValue).hasField(WebViewLogEventConsumer.DATE_KEY_NAME, expectedDate)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
            assertThat(firstValue).hasField(
                LogAttributes.RUM_APPLICATION_ID,
                fakeRumContext.applicationId
            )
            assertThat(firstValue).hasField(
                LogAttributes.RUM_SESSION_ID,
                fakeRumContext.sessionId
            )
        }
    }

    @Test
    fun `M skip date correction W consume { user log, date field does not exist }`() {
        // Given
        fakeWebLogEvent.remove(WebViewLogEventConsumer.DATE_KEY_NAME)
        var expectedTags = mobileSdkDdtags()
        fakeWebLogEvent.get(WebViewLogEventConsumer.DDTAGS_KEY_NAME)?.asString?.let {
            expectedTags += WebViewLogEventConsumer.DDTAGS_SEPARATOR + it
        }

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockUserLogsWriter).write(capture())
            assertThat(firstValue).doesNotHaveField(WebViewLogEventConsumer.DATE_KEY_NAME)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
        }
    }

    @Test
    fun `M skip date correction W consume { internal log, date field does not exist }`() {
        // Given
        fakeWebLogEvent.remove(WebViewLogEventConsumer.DATE_KEY_NAME)
        var expectedTags = mobileSdkDdtags()
        fakeWebLogEvent.get(WebViewLogEventConsumer.DDTAGS_KEY_NAME)?.asString?.let {
            expectedTags += WebViewLogEventConsumer.DDTAGS_SEPARATOR + it
        }

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockInternalLogsWriter).write(capture())
            assertThat(firstValue).doesNotHaveField(WebViewLogEventConsumer.DATE_KEY_NAME)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
        }
    }

    @ParameterizedTest
    @MethodSource("brokenJsonTestData")
    fun `M send original event W consume { user log, bad format json object }`(
        fakeBrokenJsonObject: JsonObject
    ) {
        // When
        testedConsumer.consume(
            fakeBrokenJsonObject to WebViewLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        verify(mockUserLogsWriter).write(fakeBrokenJsonObject)
    }

    @ParameterizedTest
    @MethodSource("brokenJsonTestData")
    fun `M send original event W consume { internal log, bad format json object }`(
        fakeBrokenJsonObject: JsonObject
    ) {
        // When
        testedConsumer.consume(
            fakeBrokenJsonObject to WebViewLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        verify(mockInternalLogsWriter).write(fakeBrokenJsonObject)
    }

    @ParameterizedTest
    @MethodSource("brokenJsonTestData")
    fun `M log an sdk error W consume { bad format json object }`(
        fakeBrokenJsonObject: JsonObject,
        expectedThrowable: Class<Throwable>,
        forge: Forge
    ) {
        // When
        testedConsumer.consume(
            fakeBrokenJsonObject to forge.anElementFrom(WebViewLogEventConsumer.LOG_EVENT_TYPES)
        )

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(WebViewLogEventConsumer.JSON_PARSING_ERROR_MESSAGE),
            argThat {
                expectedThrowable.isAssignableFrom(this::class.java)
            },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    @Test
    fun `M add local ddtags W consume { user log, no extra ddtags }`() {
        // Given
        fakeWebLogEvent.remove(WebViewLogEventConsumer.DDTAGS_KEY_NAME)
        val expectedDate = fakeTimeOffset +
            fakeWebLogEvent.get(WebViewLogEventConsumer.DATE_KEY_NAME).asLong
        val expectedTags = mobileSdkDdtags()

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.USER_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockUserLogsWriter).write(capture())
            assertThat(firstValue).hasField(WebViewLogEventConsumer.DATE_KEY_NAME, expectedDate)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
        }
    }

    @Test
    fun `M add local ddtags W consume { internal log, no extra ddtags }`() {
        // Given
        fakeWebLogEvent.remove(WebViewLogEventConsumer.DDTAGS_KEY_NAME)
        val expectedDate = fakeTimeOffset +
            fakeWebLogEvent.get(WebViewLogEventConsumer.DATE_KEY_NAME).asLong
        val expectedTags = mobileSdkDdtags()

        // When
        testedConsumer.consume(
            fakeWebLogEvent to WebViewLogEventConsumer.INTERNAL_LOG_EVENT_TYPE
        )

        // Then
        argumentCaptor<JsonObject>() {
            verify(mockInternalLogsWriter).write(capture())
            assertThat(firstValue).hasField(WebViewLogEventConsumer.DATE_KEY_NAME, expectedDate)
            assertThat(firstValue).hasField(
                WebViewLogEventConsumer.DDTAGS_KEY_NAME,
                expectedTags
            )
        }
    }

    // endregion

    // region Internal

    private fun Forge.aWebLogEvent(): JsonObject {
        val aJsonObject: JsonObject = getForgery()
        aJsonObject.addProperty(WebViewLogEventConsumer.DATE_KEY_NAME, aLong())
        if (aBool()) {
            aJsonObject.addProperty(WebViewLogEventConsumer.DDTAGS_KEY_NAME, aString())
        }
        return aJsonObject
    }

    private fun mobileSdkDdtags(): String {
        return "${LogAttributes.APPLICATION_VERSION}:$fakePackageVersion" +
            ",${LogAttributes.ENV}:$fakeEnvName"
    }

    // endregion

    companion object {

        @JvmStatic
        fun brokenJsonTestData(): Stream<Arguments> {
            val fakeJsonObject1 = JsonObject().apply {
                val fakeJsonArray = JsonArray().apply {
                    add("1")
                    add("2")
                }
                add("date", fakeJsonArray)
            }
            val fakeJsonObject2 = JsonObject().apply {
                addProperty("date", "0.323131")
            }
            val fakeJsonObject3 = JsonObject().apply {
                add("date", JsonObject())
            }
            val fakeJsonObject4 = JsonObject().apply {
                add("ddtags", JsonObject())
            }
            val fakeJsonObject5 = JsonObject().apply {
                val fakeJsonArray = JsonArray().apply {
                    add("1")
                    add("2")
                }
                add("ddtags", fakeJsonArray)
            }
            return listOf(
                Arguments.of(fakeJsonObject1, IllegalStateException::class.java),
                Arguments.of(fakeJsonObject2, NumberFormatException::class.java),
                Arguments.of(fakeJsonObject3, UnsupportedOperationException::class.java),
                Arguments.of(fakeJsonObject4, UnsupportedOperationException::class.java),
                Arguments.of(fakeJsonObject5, java.lang.IllegalStateException::class.java)
            ).stream()
        }
    }
}
