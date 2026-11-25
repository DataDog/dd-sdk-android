/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Rect
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.material.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.material.internal.FloatingActionButtonWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.CornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.roundToLong

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class FloatingActionButtonWireframeMapperTest {

    private lateinit var testedMapper: FloatingActionButtonWireframeMapper

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    @IntForgery(min = 0, max = 100)
    var fakeCircleLeft: Int = 0

    @IntForgery(min = 0, max = 100)
    var fakeCircleTop: Int = 0

    @IntForgery(min = 1, max = 300)
    var fakeCircleWidth: Int = 0

    @IntForgery(min = 1, max = 300)
    var fakeCircleHeight: Int = 0

    @FloatForgery(min = 0f, max = 200f)
    var fakeCornerSize: Float = 0f

    @FloatForgery(min = 0f, max = 1f)
    var fakeAlpha: Float = 0f

    @IntForgery
    var fakeDefaultColor: Int = 0

    @IntForgery
    var fakeStateColor: Int = 0

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeColorHexString: String

    private val fakeDrawableState = intArrayOf(android.R.attr.state_enabled)

    @Mock
    lateinit var mockFab: FloatingActionButton

    @Mock
    lateinit var mockTint: ColorStateList

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockShapeAppearanceModel: ShapeAppearanceModel

    @Mock
    lateinit var mockCornerSize: CornerSize

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        val density = fakeMappingContext.systemInformation.screenDensity
        val fakeDisplayMetrics = DisplayMetrics().apply { this.density = density }
        whenever(mockResources.displayMetrics) doReturn fakeDisplayMetrics
        whenever(mockCornerSize.getCornerSize(any())) doReturn fakeCornerSize
        whenever(mockShapeAppearanceModel.topLeftCornerSize) doReturn mockCornerSize
        whenever(mockTint.defaultColor) doReturn fakeDefaultColor
        whenever(mockTint.getColorForState(fakeDrawableState, fakeDefaultColor)) doReturn fakeStateColor
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeStateColor)) doReturn fakeColorHexString
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockFab, density)) doReturn fakeGlobalBounds
        whenever(mockFab.resources) doReturn mockResources
        whenever(mockFab.backgroundTintList) doReturn mockTint
        whenever(mockFab.drawableState) doReturn fakeDrawableState
        whenever(mockFab.shapeAppearanceModel) doReturn mockShapeAppearanceModel
        whenever(mockFab.alpha) doReturn fakeAlpha
        doAnswer {
            (it.arguments[0] as Rect).apply {
                left = fakeCircleLeft
                top = fakeCircleTop
                right = fakeCircleLeft + fakeCircleWidth
                bottom = fakeCircleTop + fakeCircleHeight
            }
        }.whenever(mockFab).getMeasuredContentRect(any())

        testedMapper = FloatingActionButtonWireframeMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    @Test
    fun `M map the background to a circle shape W map()`() {
        // Given
        val density = fakeMappingContext.systemInformation.screenDensity

        // When
        val wireframes = testedMapper.map(mockFab, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        val shape = checkNotNull(wireframes.singleOrNull() as? MobileSegment.Wireframe.ShapeWireframe) {
            "Expected a single shape wireframe"
        }
        assertThat(shape.x).isEqualTo(fakeGlobalBounds.x + (fakeCircleLeft.toLong() / density).toLong())
        assertThat(shape.y).isEqualTo(fakeGlobalBounds.y + (fakeCircleTop.toLong() / density).toLong())
        assertThat(shape.width).isEqualTo((fakeCircleWidth / density).toLong())
        assertThat(shape.height).isEqualTo((fakeCircleHeight / density).toLong())
        assertThat(shape.shapeStyle).isEqualTo(
            MobileSegment.ShapeStyle(
                backgroundColor = fakeColorHexString,
                opacity = fakeAlpha,
                cornerRadius = (fakeCornerSize / density).roundToLong()
            )
        )
        assertThat(shape.border).isNull()
    }

    @Test
    fun `M not map a shape W map() { no background tint }`() {
        // Given
        whenever(mockFab.backgroundTintList) doReturn null

        // When
        val wireframes = testedMapper.map(mockFab, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M not map a shape W map() { content rect is empty }`() {
        // Given
        doAnswer { }.whenever(mockFab).getMeasuredContentRect(any())

        // When
        val wireframes = testedMapper.map(mockFab, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(wireframes).isEmpty()
    }
}
