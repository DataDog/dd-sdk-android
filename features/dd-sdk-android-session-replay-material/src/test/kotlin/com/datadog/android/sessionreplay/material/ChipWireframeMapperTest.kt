/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.material.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.material.internal.ChipWireframeMapper
import com.datadog.android.sessionreplay.material.internal.densityNormalized
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.chip.Chip
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
class ChipWireframeMapperTest {

    private lateinit var testedChipWireframeMapper: ChipWireframeMapper

    @Mock
    lateinit var mockChipView: Chip

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockChipDrawable: Drawable

    @LongForgery
    var fakeViewId: Long = 0L

    @StringForgery
    var fakeText: String = ""

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    lateinit var fakeDrawableBounds: Rect

    @IntForgery
    private var fakeDrawableHeight: Int = 0

    @IntForgery
    private var fakeDrawableWidth: Int = 0

    @Mock
    lateinit var mockLayout: Layout

    @FloatForgery(0f, 255f)
    var fakeFontSize: Float = 0f

    @IntForgery(min = 0, max = 0xffffff)
    var fakeTextColor: Int = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeDrawableBounds = Rect(
            forge.aSmallInt(),
            forge.aSmallInt(),
            forge.aSmallInt(),
            forge.aSmallInt()
        )
        mockChipView = mockChip()
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockChipView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeGlobalBounds)
        whenever(
            mockViewIdentifierResolver.resolveViewId(
                mockChipView
            )
        ).thenReturn(fakeViewId)
        testedChipWireframeMapper = ChipWireframeMapper(
            viewIdentifierResolver = mockViewIdentifierResolver,
            colorStringFormatter = mockColorStringFormatter,
            viewBoundsResolver = mockViewBoundsResolver,
            drawableToColorMapper = mockDrawableToColorMapper
        )
    }

    @Test
    fun `M resolves card view wireframe W map`() {
        // When
        testedChipWireframeMapper.map(
            mockChipView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        val density = fakeMappingContext.systemInformation.screenDensity

        verify(fakeMappingContext.imageWireframeHelper).createImageWireframeByDrawable(
            view = eq(mockChipView),
            // Background drawable doesn't need to be masked.
            imagePrivacy = eq(ImagePrivacy.MASK_NONE),
            currentWireframeIndex = anyInt(),
            x = eq(
                fakeGlobalBounds.x + fakeDrawableBounds.left.toLong()
                    .densityNormalized(density)
            ),
            y = eq(
                fakeGlobalBounds.y + fakeDrawableBounds.top.toLong()
                    .densityNormalized(density)
            ),
            width = eq(fakeDrawableWidth),
            height = eq(fakeDrawableHeight),
            usePIIPlaceholder = eq(false),
            drawable = eq(mockChipDrawable),
            drawableCopier = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = isNull(),
            shapeStyle = isNull(),
            border = isNull(),
            prefix = any(),
            customResourceIdCacheKey = anyOrNull()
        )
    }

    private fun mockChip(): Chip {
        return mock {
            whenever(it.text).thenReturn(fakeText)
            whenever(it.chipDrawable).thenReturn(mockChipDrawable)
            whenever(mockChipDrawable.bounds).thenReturn(fakeDrawableBounds)
            whenever(mockChipDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
            whenever(mockChipDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

            whenever(it.layout) doReturn mockLayout
            whenever(it.typeface) doReturn Typeface.SERIF
            whenever(it.textSize) doReturn fakeFontSize
            whenever(it.currentTextColor) doReturn fakeTextColor
            whenever(it.textAlignment) doReturn 0
            whenever(it.gravity) doReturn 0
            whenever(
                mockColorStringFormatter.formatColorAndAlphaAsHexString(
                    fakeTextColor,
                    OPAQUE_ALPHA_VALUE
                )
            ) doReturn ""
        }
    }
}
