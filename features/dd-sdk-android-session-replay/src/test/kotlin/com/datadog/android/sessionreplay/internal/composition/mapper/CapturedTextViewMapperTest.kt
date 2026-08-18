/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedTextViewMapperTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockColorStringFormatter: ColorStringFormatter = mock()
    private val mockInternalLogger: InternalLogger = mock()
    private val testedMapper = CapturedTextViewMapper(
        viewBoundsResolver = mockViewBoundsResolver,
        colorStringFormatter = mockColorStringFormatter,
        backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(),
        internalLogger = mockInternalLogger
    )

    @Test
    fun `M capture raw unmasked text W map()`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView: TextView = mock()
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.background).thenReturn(null)
        whenever(mockTextView.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockColorStringFormatter.formatColorAndAlphaAsHexString(fakeTextColor, OPAQUE_ALPHA_VALUE))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "text-owner")
        val mappingContext = CapturedMappingContext(factory, owner, screenDensity = fakeDensity)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val textWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(textWireframe.text).isEqualTo(fakeText)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeColorHexString)
        assertThat(textWireframe.bounds.x).isEqualTo(fakeBounds.x)
    }

    @Test
    fun `M not emit a Shape wireframe W map { no background }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView: TextView = mock()
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.background).thenReturn(null)
        whenever(mockTextView.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockColorStringFormatter.formatColorAndAlphaAsHexString(fakeTextColor, OPAQUE_ALPHA_VALUE))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "text-owner")
        val mappingContext = CapturedMappingContext(factory, owner, screenDensity = fakeDensity)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>()).isEmpty()
    }
}
