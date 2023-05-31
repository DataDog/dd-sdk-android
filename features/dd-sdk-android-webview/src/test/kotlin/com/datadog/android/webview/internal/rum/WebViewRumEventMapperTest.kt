/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aRumEventAsJson
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewRumEventMapperTest {

    lateinit var testedWebViewRumEventMapper: WebViewRumEventMapper

    @LongForgery
    var fakeServerTimeOffset: Long = 0L

    @Forgery
    lateinit var fakeRumContext: RumContext

    lateinit var fakeTags: Map<String, String>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTags = if (forge.aBool()) {
            forge.aMap {
                forge.anAlphabeticalString() to forge.anAlphaNumericalString()
            }
        } else {
            emptyMap()
        }
        testedWebViewRumEventMapper = WebViewRumEventMapper()
    }

    @Test
    fun `M map the event W mapEvent { ViewEvent }()`(forge: Forge) {
        // Given
        val fakeViewEvent = forge.getForgery<ViewEvent>()
        val fakeRumJsonObject = fakeViewEvent.toJson().asJsonObject

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeViewEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { ActionEvent }`(forge: Forge) {
        // Given
        val fakeActionEvent = forge.getForgery<ActionEvent>()
        val fakeRumJsonObject = fakeActionEvent.toJson().asJsonObject

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeActionEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { ErrorEvent }`(forge: Forge) {
        // Given
        val fakeErrorEvent = forge.getForgery<ErrorEvent>()
        val fakeRumJsonObject = fakeErrorEvent.toJson().asJsonObject

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeErrorEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { ResourceEvent }`(forge: Forge) {
        // Given
        val fakeResourceEvent = forge.getForgery<ResourceEvent>()
        val fakeRumJsonObject = fakeResourceEvent.toJson().asJsonObject

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeResourceEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { LongTaskEvent }()`(forge: Forge) {
        // Given
        val fakeLongTaskEvent = forge.getForgery<LongTaskEvent>()
        val fakeRumJsonObject = fakeLongTaskEvent.toJson().asJsonObject

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeLongTaskEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { missing application and session fields  }()`(forge: Forge) {
        // Given
        val fakeLongTaskEvent = forge.getForgery<LongTaskEvent>()
        val fakeRumJsonObject = fakeLongTaskEvent.toJson().asJsonObject.apply {
            remove(WebViewRumEventMapper.APPLICATION_KEY_NAME)
            remove(WebViewRumEventMapper.SESSION_KEY_NAME)
        }

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeLongTaskEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { RumContext is missing }`(forge: Forge) {
        // Given
        val fakeRumJsonObject = forge.aRumEventAsJson()
        val expectedDate = fakeRumJsonObject.get(WebViewRumEventMapper.DATE_KEY_NAME).asLong +
            fakeServerTimeOffset
        val expectedApplicationId = fakeRumJsonObject
            .getAsJsonObject(WebViewRumEventMapper.APPLICATION_KEY_NAME)
            .getAsJsonPrimitive(WebViewRumEventMapper.ID_KEY_NAME)
            .asString
        val expectedSessionId = fakeRumJsonObject
            .getAsJsonObject(WebViewRumEventMapper.SESSION_KEY_NAME)
            .getAsJsonPrimitive(WebViewRumEventMapper.ID_KEY_NAME)
            .asString

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            null,
            fakeServerTimeOffset
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                WebViewRumEventMapper.APPLICATION_KEY_NAME,
                WebViewRumEventMapper.SESSION_KEY_NAME,
                WebViewRumEventMapper.DATE_KEY_NAME,
                WebViewRumEventMapper.DD_KEY_NAME
            )
            .isEqualTo(fakeRumJsonObject)
        assertThat(mappedEvent.getAsJsonObject(WebViewRumEventMapper.APPLICATION_KEY_NAME))
            .hasField(WebViewRumEventMapper.ID_KEY_NAME, expectedApplicationId)
        assertThat(mappedEvent.getAsJsonObject(WebViewRumEventMapper.SESSION_KEY_NAME))
            .hasField(WebViewRumEventMapper.ID_KEY_NAME, expectedSessionId)
        assertThat(mappedEvent).hasField(
            WebViewRumEventMapper.DATE_KEY_NAME,
            expectedDate
        )
        val ddSession = mappedEvent
            .getAsJsonObject(WebViewRumEventMapper.DD_KEY_NAME)
            .get(WebViewRumEventMapper.DD_SESSION_KEY_NAME).asJsonObject
        assertThat(ddSession).hasField(
            WebViewRumEventMapper.SESSION_PLAN_KEY_NAME,
            ViewEvent.Plan.PLAN_1.toJson().asLong
        )
    }

    private fun assertMappedEvent(
        expectedEvent: JsonObject,
        expectedDate: Long,
        mappedEvent: JsonObject
    ) {
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                WebViewRumEventMapper.APPLICATION_KEY_NAME,
                WebViewRumEventMapper.SESSION_KEY_NAME,
                WebViewRumEventMapper.DATE_KEY_NAME,
                WebViewRumEventMapper.DD_KEY_NAME
            )
            .isEqualTo(expectedEvent)

        assertThat(mappedEvent.getAsJsonObject(WebViewRumEventMapper.APPLICATION_KEY_NAME))
            .hasField(WebViewRumEventMapper.ID_KEY_NAME, fakeRumContext.applicationId)
        assertThat(mappedEvent.getAsJsonObject(WebViewRumEventMapper.SESSION_KEY_NAME))
            .hasField(WebViewRumEventMapper.ID_KEY_NAME, fakeRumContext.sessionId)
        assertThat(mappedEvent).hasField(
            WebViewRumEventMapper.DATE_KEY_NAME,
            expectedDate
        )
        val ddSession = mappedEvent
            .getAsJsonObject(WebViewRumEventMapper.DD_KEY_NAME)
            .get(WebViewRumEventMapper.DD_SESSION_KEY_NAME).asJsonObject
        assertThat(ddSession).hasField(
            WebViewRumEventMapper.SESSION_PLAN_KEY_NAME,
            ViewEvent.Plan.PLAN_1.toJson().asLong
        )
    }
}
