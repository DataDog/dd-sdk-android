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
import com.datadog.android.webview.internal.rum.domain.NativeRumViewsCache
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
internal class WebViewRumEventMapperTest {

    lateinit var testedWebViewRumEventMapper: WebViewRumEventMapper

    @LongForgery
    var fakeServerTimeOffset: Long = 0L

    @Forgery
    lateinit var fakeRumContext: RumContext

    @Mock
    lateinit var mockNativeRumViewsCache: NativeRumViewsCache

    @StringForgery
    lateinit var fakeResolvedNativeViewId: String

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
        testedWebViewRumEventMapper = WebViewRumEventMapper(mockNativeRumViewsCache)
    }

    @Test
    fun `M map the event W mapEvent { ViewEvent }`(forge: Forge) {
        // Given
        val fakeViewEvent = forge.getForgery<ViewEvent>()
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeViewEvent.date))
            .thenReturn(fakeResolvedNativeViewId)
        val fakeRumJsonObject = fakeViewEvent.toJson().asJsonObject

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset,
            true
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
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeActionEvent.date))
            .thenReturn(fakeResolvedNativeViewId)

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset,
            true
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
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeErrorEvent.date))
            .thenReturn(fakeResolvedNativeViewId)

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset,
            true
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
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeResourceEvent.date))
            .thenReturn(fakeResolvedNativeViewId)

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset,
            true
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeResourceEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { LongTaskEvent }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent = forge.getForgery<LongTaskEvent>()
        val fakeRumJsonObject = fakeLongTaskEvent.toJson().asJsonObject
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeLongTaskEvent.date))
            .thenReturn(fakeResolvedNativeViewId)

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset,
            true
        )

        // Then
        assertMappedEvent(
            fakeRumJsonObject,
            fakeLongTaskEvent.date + fakeServerTimeOffset,
            mappedEvent
        )
    }

    @Test
    fun `M map the event W mapEvent { missing application and session fields  }`(forge: Forge) {
        // Given
        val fakeLongTaskEvent = forge.getForgery<LongTaskEvent>()
        val fakeRumJsonObject = fakeLongTaskEvent.toJson().asJsonObject.apply {
            remove(WebViewRumEventMapper.APPLICATION_KEY_NAME)
            remove(WebViewRumEventMapper.SESSION_KEY_NAME)
        }
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeLongTaskEvent.date))
            .thenReturn(fakeResolvedNativeViewId)

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            fakeRumContext,
            fakeServerTimeOffset,
            true
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
        val fakeEventDate = fakeRumJsonObject.get(WebViewRumEventMapper.DATE_KEY_NAME).asLong
        val expectedDate = fakeEventDate +
            fakeServerTimeOffset
        val expectedApplicationId = fakeRumJsonObject
            .getAsJsonObject(WebViewRumEventMapper.APPLICATION_KEY_NAME)
            .getAsJsonPrimitive(WebViewRumEventMapper.ID_KEY_NAME)
            .asString
        val expectedSessionId = fakeRumJsonObject
            .getAsJsonObject(WebViewRumEventMapper.SESSION_KEY_NAME)
            .getAsJsonPrimitive(WebViewRumEventMapper.ID_KEY_NAME)
            .asString
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeEventDate))
            .thenReturn(fakeResolvedNativeViewId)

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            null,
            fakeServerTimeOffset,
            true
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                WebViewRumEventMapper.APPLICATION_KEY_NAME,
                WebViewRumEventMapper.SESSION_KEY_NAME,
                WebViewRumEventMapper.DATE_KEY_NAME,
                WebViewRumEventMapper.DD_KEY_NAME,
                WebViewRumEventMapper.CONTAINER_KEY_NAME
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
        val container = mappedEvent.getAsJsonObject(WebViewRumEventMapper.CONTAINER_KEY_NAME)
        assertThat(container).hasField(
            WebViewRumEventMapper.SOURCE_KEY_NAME,
            WebViewRumEventMapper.SOURCE_VALUE
        )
        assertThat(container.getAsJsonObject(WebViewRumEventMapper.VIEW_KEY_NAME))
            .hasField(WebViewRumEventMapper.ID_KEY_NAME, fakeResolvedNativeViewId)
    }

    @Test
    fun `M map the event W mapEvent { parent native id could not be resolved }`(forge: Forge) {
        // Given
        val fakeRumJsonObject = forge.aRumEventAsJson()
        val fakeEventDate = fakeRumJsonObject.get(WebViewRumEventMapper.DATE_KEY_NAME).asLong
        whenever(mockNativeRumViewsCache.resolveLastParentIdForBrowserEvent(fakeEventDate))
            .thenReturn(null)

        // When
        val mappedEvent = testedWebViewRumEventMapper.mapEvent(
            fakeRumJsonObject,
            null,
            fakeServerTimeOffset,
            true
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                WebViewRumEventMapper.APPLICATION_KEY_NAME,
                WebViewRumEventMapper.SESSION_KEY_NAME,
                WebViewRumEventMapper.DATE_KEY_NAME,
                WebViewRumEventMapper.DD_KEY_NAME,
                WebViewRumEventMapper.CONTAINER_KEY_NAME
            )
            .isEqualTo(fakeRumJsonObject)
        val container = mappedEvent
            .getAsJsonObject(WebViewRumEventMapper.CONTAINER_KEY_NAME)
        assertThat(container).hasField(
            WebViewRumEventMapper.SOURCE_KEY_NAME,
            WebViewRumEventMapper.SOURCE_VALUE
        )
        assertThat(container).doesNotHaveField(WebViewRumEventMapper.VIEW_KEY_NAME)
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
        val container = mappedEvent.getAsJsonObject(WebViewRumEventMapper.CONTAINER_KEY_NAME)
        assertThat(container).hasField(
            WebViewRumEventMapper.SOURCE_KEY_NAME,
            WebViewRumEventMapper.SOURCE_VALUE
        )
        assertThat(container.getAsJsonObject(WebViewRumEventMapper.VIEW_KEY_NAME))
            .hasField(WebViewRumEventMapper.ID_KEY_NAME, fakeResolvedNativeViewId)
    }
}
