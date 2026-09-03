/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistration
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedEmbeddedContentMapperTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockViewUtilsInternal: ViewUtilsInternal = mock()
    private val embeddedContentSlotRegistry = EmbeddedContentSlotRegistry()
    private val testedMapper = CapturedEmbeddedContentMapper(
        embeddedContentSlotRegistry = embeddedContentSlotRegistry,
        viewBoundsResolver = mockViewBoundsResolver,
        viewUtilsInternal = mockViewUtilsInternal
    )

    private lateinit var factoryOwner: com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
    private lateinit var mappingContext: CapturedMappingContext

    @BeforeEach
    fun `set up`(@StringForgery fakeScope: String) {
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val window = factory.window("window")
        factoryOwner = factory.view(window, "embedded-content-owner")
        mappingContext = CapturedMappingContext(
            factory,
            factoryOwner,
            screenDensity = 2f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )
    }

    private fun taggedView(slotId: String): View {
        val view: View = mock()
        val registration = EmbeddedContentSlotRegistration(slotId)
        whenever(view.getTag(R.id.datadog_session_replay_slot_id)) doReturn slotId
        whenever(view.getTag(R.id.datadog_session_replay_slot_registration)) doReturn registration
        return view
    }

    @Test
    fun `M return true W hasSlotId { view is tagged }`(@StringForgery fakeSlotId: String) {
        assertThat(testedMapper.hasSlotId(taggedView(fakeSlotId))).isTrue()
    }

    @Test
    fun `M return false W hasSlotId { view is not tagged }`() {
        assertThat(testedMapper.hasSlotId(mock())).isFalse()
    }

    @Test
    fun `M return None W map { view is not tagged }`() {
        val result = testedMapper.map(mock(), mappingContext)
        assertThat(result).isEqualTo(CapturedViewMapperResult.None)
    }

    @Test
    fun `M emit a visible EmbeddedContent wireframe W map { view is visible }`(
        @StringForgery fakeSlotId: String,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val view = taggedView(fakeSlotId)
        whenever(mockViewUtilsInternal.isNotVisible(view)) doReturn false
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)) doReturn fakeBounds

        // When
        val result = testedMapper.map(view, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val wireframe = result.wireframes.single() as CapturedWireframe.EmbeddedContent
        assertThat(wireframe.slotId).isEqualTo(fakeSlotId)
        assertThat(wireframe.isVisible).isTrue()
        assertThat(wireframe.bounds.x).isEqualTo(fakeBounds.x)
        assertThat(wireframe.bounds.y).isEqualTo(fakeBounds.y)
        assertThat(wireframe.bounds.width).isEqualTo(fakeBounds.width)
        assertThat(wireframe.bounds.height).isEqualTo(fakeBounds.height)
        assertThat(result.pixelFallbackTerminal).isTrue()
    }

    @Test
    fun `M emit a hidden zero-bounds EmbeddedContent wireframe W map { view is not visible }`(
        @StringForgery fakeSlotId: String
    ) {
        // Given
        val view = taggedView(fakeSlotId)
        whenever(mockViewUtilsInternal.isNotVisible(view)) doReturn true

        // When
        val result = testedMapper.map(view, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val wireframe = result.wireframes.single() as CapturedWireframe.EmbeddedContent
        assertThat(wireframe.slotId).isEqualTo(fakeSlotId)
        assertThat(wireframe.isVisible).isFalse()
        assertThat(wireframe.bounds.width).isEqualTo(0L)
        assertThat(wireframe.bounds.height).isEqualTo(0L)
    }

    @Test
    fun `M return no hidden wireframes W finishCapture { slot was refreshed this round }`(
        @StringForgery fakeSlotId: String,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val view = taggedView(fakeSlotId)
        whenever(mockViewUtilsInternal.isNotVisible(view)) doReturn false
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)) doReturn fakeBounds

        // When
        testedMapper.beginCapture()
        testedMapper.map(view, mappingContext)

        // Then
        assertThat(testedMapper.finishCapture()).isEmpty()
    }

    @Test
    fun `M emit a hidden placeholder W finishCapture { slot active but not refreshed this round }`(
        @StringForgery fakeSlotId: String,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given: recorded on one capture, but the view isn't walked (and map() never called) on the next.
        val view = taggedView(fakeSlotId)
        whenever(mockViewUtilsInternal.isNotVisible(view)) doReturn false
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)) doReturn fakeBounds
        testedMapper.beginCapture()
        testedMapper.map(view, mappingContext)
        testedMapper.finishCapture()

        // When
        testedMapper.beginCapture()
        val hidden = testedMapper.finishCapture()

        // Then
        val wireframe = hidden.single() as CapturedWireframe.EmbeddedContent
        assertThat(wireframe.slotId).isEqualTo(fakeSlotId)
        assertThat(wireframe.isVisible).isFalse()
        assertThat(wireframe.bounds.width).isEqualTo(0L)
        assertThat(wireframe.bounds.height).isEqualTo(0L)
    }

    @Test
    fun `M drop the slot entirely W finishCapture { registration deactivated }`(
        @StringForgery fakeSlotId: String,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val view = taggedView(fakeSlotId)
        val registration = view.getTag(R.id.datadog_session_replay_slot_registration) as EmbeddedContentSlotRegistration
        whenever(mockViewUtilsInternal.isNotVisible(view)) doReturn false
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)) doReturn fakeBounds
        testedMapper.beginCapture()
        testedMapper.map(view, mappingContext)
        testedMapper.finishCapture()

        // When: the app explicitly detaches the slot.
        embeddedContentSlotRegistry.notifySlotChanged(registration, null)
        testedMapper.beginCapture()
        val hidden = testedMapper.finishCapture()

        // Then: no hidden placeholder - the wireframe is dropped from the next capture entirely,
        // matching setEmbeddedContentSlotId(view, null)'s own documented contract.
        assertThat(hidden).isEmpty()
    }
}
