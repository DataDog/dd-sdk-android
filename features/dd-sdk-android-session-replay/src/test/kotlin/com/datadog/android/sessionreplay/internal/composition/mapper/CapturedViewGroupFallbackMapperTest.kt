/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.drawable.Drawable
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
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
internal class CapturedViewGroupFallbackMapperTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockColorStringFormatter: ColorStringFormatter = mock()
    private val mockDrawableToColorMapper: DrawableToColorMapper = mock()
    private val mockInternalLogger: InternalLogger = mock()
    private val testedMapper = CapturedViewGroupFallbackMapper(
        viewBoundsResolver = mockViewBoundsResolver,
        backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(
            mockColorStringFormatter,
            mockDrawableToColorMapper
        ),
        internalLogger = mockInternalLogger
    )

    @Test
    fun `M emit nothing W map { no background }`(
        @StringForgery fakeScope: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float
    ) {
        // Given
        val mockView: View = mock()
        whenever(mockView.background).thenReturn(null)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "owner")
        val mappingContext = CapturedMappingContext(factory, owner, screenDensity = fakeDensity)

        // When
        val result = testedMapper.map(mockView, mappingContext)

        // Then
        assertThat(result).isEqualTo(CapturedViewMapperResult.None)
    }

    @Test
    fun `M emit a Shape wireframe W map { resolvable background color }`(
        @StringForgery fakeScope: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeColorHexString: String,
        @FloatForgery(min = 0f, max = 1f) fakeAlpha: Float,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockView: View = mock()
        val mockDrawable: Drawable = mock()
        whenever(mockView.background).thenReturn(mockDrawable)
        whenever(mockView.alpha).thenReturn(fakeAlpha)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger)).thenReturn(fakeColor)
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeColor)).thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeDensity)).thenReturn(fakeBounds)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "owner")
        val mappingContext = CapturedMappingContext(factory, owner, screenDensity = fakeDensity)

        // When
        val result = testedMapper.map(mockView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val wireframe = result.wireframes.single() as CapturedWireframe.Shape
        assertThat(wireframe.style?.backgroundColor).isEqualTo(fakeColorHexString)
        assertThat(wireframe.style?.opacity).isEqualTo(fakeAlpha)
        assertThat(wireframe.bounds.x).isEqualTo(fakeBounds.x)
    }
}
