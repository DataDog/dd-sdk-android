/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.replay

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.rum.TimestampOffsetProvider
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewReplayEventMapperTest {

    private lateinit var testedWebViewReplayEventMapper: WebViewReplayEventMapper

    @LongForgery
    var fakeServerTimeOffset: Long = 0L

    @Forgery
    lateinit var fakeRumContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @StringForgery(regex = "^[a-z0-9]{32}$")
    lateinit var fakeWebViewId: String

    @Mock
    lateinit var mockOffsetProvider: TimestampOffsetProvider

    @StringForgery(regex = "^[a-z0-9]{32}$")
    lateinit var fakeBrowserRumViewId: String

    @BeforeEach
    fun `set up`() {
        testedWebViewReplayEventMapper = WebViewReplayEventMapper(fakeWebViewId, mockOffsetProvider)
    }

    @Test
    fun `M throw W mapEvent {event is missing view data container}`(forge: Forge) {
        // Given
        val fakeEvent: JsonObject = forge.getForgery<JsonObject>()

        // Then
        assertThatThrownBy {
            testedWebViewReplayEventMapper.mapEvent(
                fakeEvent,
                fakeRumContext,
                fakeDatadogContext
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage(
                WebViewReplayEventMapper.BROWSER_EVENT_MISSING_VIEW_DATA_ERROR_MESSAGE
            )
    }

    @Test
    fun `M throw W mapEvent {event is missing view id container}`(forge: Forge) {
        // Given
        val fakeEvent: JsonObject = stubValidReplayEvent(fakeBrowserRumViewId, forge.aLong()).apply {
            get(WebViewReplayEventMapper.VIEW_OBJECT_KEY)
                .asJsonObject
                .remove(WebViewReplayEventMapper.VIEW_ID_KEY)
        }

        // Then
        assertThatThrownBy {
            testedWebViewReplayEventMapper.mapEvent(
                fakeEvent,
                fakeRumContext,
                fakeDatadogContext
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage(
                WebViewReplayEventMapper.BROWSER_EVENT_MISSING_VIEW_DATA_ERROR_MESSAGE
            )
    }

    @Test
    fun `M throw W mapEvent {event is broken}`(forge: Forge) {
        // Given
        val fakeEvent: JsonObject = stubValidReplayEvent(fakeBrowserRumViewId, forge.aLong())
        val fakeBrokenWrappedEvent: JsonPrimitive = forge.getForgery<JsonPrimitive>()
        fakeEvent.add("event", fakeBrokenWrappedEvent)

        // Then
        assertThatThrownBy {
            testedWebViewReplayEventMapper.mapEvent(
                fakeEvent,
                fakeRumContext,
                fakeDatadogContext
            )
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `M throw W mapEvent {event is missing the record}`(forge: Forge) {
        // Given
        val fakeEvent: JsonObject = stubValidReplayEvent(fakeBrowserRumViewId, forge.aLong())
            .apply { remove(WebViewReplayEventMapper.EVENT_KEY) }

        // Then
        assertThatThrownBy {
            testedWebViewReplayEventMapper.mapEvent(
                fakeEvent,
                fakeRumContext,
                fakeDatadogContext
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage(
                WebViewReplayEventMapper.BROWSER_EVENT_MISSING_RECORD_ERROR_MESSAGE
            )
    }

    @Test
    fun `M map the event to an EnrichedRecord W mapEvent`(forge: Forge) {
        // Given
        val fakeTimestamp = forge.aLong()
        val fakeEvent = stubValidReplayEvent(fakeBrowserRumViewId, fakeTimestamp)
        whenever(mockOffsetProvider.getOffset(fakeBrowserRumViewId, fakeDatadogContext))
            .thenReturn(fakeServerTimeOffset)

        // When
        val result = testedWebViewReplayEventMapper.mapEvent(
            fakeEvent,
            fakeRumContext,
            fakeDatadogContext
        )

        // Then
        val mappedWrappedEvent = result.get("records").asJsonArray[0].asJsonObject
        assertThat(mappedWrappedEvent.get(WebViewReplayEventMapper.TIMESTAMP_KEY).asLong)
            .isEqualTo(fakeTimestamp + fakeServerTimeOffset)
        assertThat(mappedWrappedEvent.get(WebViewReplayEventMapper.SLOT_ID_KEY).asString)
            .isEqualTo(fakeWebViewId.toString())
        assertThat(result.get(WebViewReplayEventMapper.ENRICHED_RECORD_APPLICATION_ID_KEY).asString)
            .isEqualTo(fakeRumContext.applicationId)
        assertThat(result.get(WebViewReplayEventMapper.ENRICHED_RECORD_SESSION_ID_KEY).asString)
            .isEqualTo(fakeRumContext.sessionId)
        assertThat(result.get(WebViewReplayEventMapper.ENRICHED_RECORD_VIEW_ID_KEY).asString)
            .isEqualTo(fakeBrowserRumViewId)
    }

    private fun stubValidReplayEvent(
        fakeBrowserViewId: String,
        fakeTimestamp: Long
    ): JsonObject {
        val fakeEvent = JsonObject()
        val fakeViewDataContainer: JsonObject = JsonObject().apply {
            addProperty(WebViewReplayEventMapper.VIEW_ID_KEY, fakeBrowserViewId)
        }
        val fakeWrappedRecord = JsonObject()
        fakeEvent.add(WebViewReplayEventMapper.EVENT_KEY, fakeWrappedRecord)
        fakeEvent.add(WebViewReplayEventMapper.VIEW_OBJECT_KEY, fakeViewDataContainer)
        fakeWrappedRecord.addProperty(WebViewReplayEventMapper.TIMESTAMP_KEY, fakeTimestamp)
        return fakeEvent
    }
}
