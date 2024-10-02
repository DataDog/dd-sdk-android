/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.content.res.ColorStateList
import androidx.cardview.widget.CardView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.material.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.material.internal.CardWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.card.MaterialCardView
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
class CardWireframeMapperTest {

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    internal lateinit var testedCardWireframeMapper: CardWireframeMapper

    @Mock
    lateinit var mockCardView: CardView

    @Mock
    lateinit var mockMaterialCardView: MaterialCardView

    @IntForgery(min = 0, max = 10)
    var fakePaddingStart: Int = 0

    @IntForgery(min = 0, max = 10)
    var fakePaddingEnd: Int = 0

    @FloatForgery(min = 0f, max = 10f)
    var fakeCornerRadius: Float = 0f

    @IntForgery(min = 0, max = 10)
    var fakeStrokeWidth: Int = 0

    @LongForgery
    var fakeViewId: Long = 0L

    @IntForgery(min = 0, max = 0xffffff)
    var fakeBackgroundColor: Int = 0

    @IntForgery(min = 0, max = 0xffffff)
    var fakeStrokeColor: Int = 0

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeBgColorHexString: String

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeStrokeColorHexString: String

    @FloatForgery(min = 0.0f, max = 1.0f)
    var fakeAlpha: Float = 0f

    @Mock
    lateinit var mockBackgroundColorStateList: ColorStateList

    @Mock
    lateinit var mockStrokeColorStateList: ColorStateList

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
        mockCardView = mockCardView()
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockCardView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeGlobalBounds)
        whenever(
            mockViewIdentifierResolver.resolveViewId(
                mockCardView
            )
        ).thenReturn(fakeViewId)

        mockMaterialCardView = mockMaterialCardView()
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockMaterialCardView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeGlobalBounds)
        whenever(
            mockViewIdentifierResolver.resolveViewId(
                mockMaterialCardView
            )
        ).thenReturn(fakeViewId)
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeBackgroundColor))
            .thenReturn(fakeBgColorHexString)
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeStrokeColor))
            .thenReturn(fakeStrokeColorHexString)
        whenever(mockBackgroundColorStateList.defaultColor).thenReturn(fakeBackgroundColor)
        whenever(mockStrokeColorStateList.defaultColor).thenReturn(fakeStrokeColor)
        testedCardWireframeMapper = CardWireframeMapper(
            viewIdentifierResolver = mockViewIdentifierResolver,
            colorStringFormatter = mockColorStringFormatter,
            viewBoundsResolver = mockViewBoundsResolver,
            drawableToColorMapper = mockDrawableToColorMapper
        )
    }

    @Test
    fun `M resolves material card view wireframe W map`() {
        // Given
        val expected = listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                fakeViewId,
                fakeGlobalBounds.x,
                fakeGlobalBounds.y,
                fakeGlobalBounds.width,
                fakeGlobalBounds.height,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = fakeBgColorHexString,
                    opacity = fakeAlpha,
                    cornerRadius = (
                        fakeCornerRadius.toLong() /
                            fakeMappingContext.systemInformation.screenDensity
                        ).toLong()
                ),
                border = MobileSegment.ShapeBorder(
                    color = fakeStrokeColorHexString,
                    width = (fakeStrokeWidth / fakeMappingContext.systemInformation.screenDensity).toLong()
                )
            )
        )

        // When
        val actual = testedCardWireframeMapper.map(
            mockMaterialCardView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `M resolves card view wireframe W map`() {
        // Given
        val expected = listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                fakeViewId,
                fakeGlobalBounds.x,
                fakeGlobalBounds.y,
                fakeGlobalBounds.width,
                fakeGlobalBounds.height,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = fakeBgColorHexString,
                    opacity = fakeAlpha,
                    cornerRadius = (
                        fakeCornerRadius.toLong() /
                            fakeMappingContext.systemInformation.screenDensity
                        ).toLong()
                )
            )
        )

        // When
        val actual = testedCardWireframeMapper.map(
            mockCardView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(actual).isEqualTo(expected)
    }

    private fun mockCardView(): CardView {
        return mock {
            whenever(it.alpha).thenReturn(fakeAlpha)
            whenever(it.paddingStart).thenReturn(fakePaddingStart)
            whenever(it.paddingEnd).thenReturn(fakePaddingEnd)
            whenever(it.cardBackgroundColor).thenReturn(mockBackgroundColorStateList)
            whenever(it.radius).thenReturn(fakeCornerRadius)
        }
    }

    private fun mockMaterialCardView(): MaterialCardView {
        return mock {
            whenever(it.alpha).thenReturn(fakeAlpha)
            whenever(it.paddingStart).thenReturn(fakePaddingStart)
            whenever(it.paddingEnd).thenReturn(fakePaddingEnd)
            whenever(it.cardBackgroundColor).thenReturn(mockBackgroundColorStateList)
            whenever(it.strokeColorStateList).thenReturn(mockStrokeColorStateList)
            whenever(it.strokeWidth).thenReturn(fakeStrokeWidth)
            whenever(it.radius).thenReturn(fakeCornerRadius)
        }
    }
}
