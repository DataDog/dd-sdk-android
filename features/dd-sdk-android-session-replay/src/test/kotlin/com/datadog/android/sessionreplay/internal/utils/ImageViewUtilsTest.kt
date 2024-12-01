/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import com.datadog.android.internal.utils.ImageViewUtils
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.utils.isCloseToOrGreaterThan
import com.datadog.android.utils.isCloseToOrLessThan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ImageViewUtilsTest {
    private val testedImageViewUtils = ImageViewUtils

    // region calculateClipping

    @Test
    fun `M return empty clip W calculateClipping() { no clipping required }`(
        @Mock mockParentRect: Rect,
        @Mock mockChildRect: Rect,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()

        mockParentRect.left = fakeGlobalX
        mockParentRect.top = fakeGlobalY
        mockParentRect.right = fakeGlobalX + fakeWidth
        mockParentRect.bottom = fakeGlobalY + fakeHeight

        mockChildRect.left = mockParentRect.left
        mockChildRect.top = mockParentRect.top
        mockChildRect.right = mockParentRect.right
        mockChildRect.bottom = mockParentRect.bottom

        val expectedClipping = MobileSegment.WireframeClip(0, 0, 0, 0)

        // When
        val result = testedImageViewUtils.calculateClipping(
            parentRect = mockParentRect,
            childRect = mockChildRect,
            density = 1f
        )

        // Then
        assertThat(result.toWireframeClip()).isEqualTo(expectedClipping)
    }

    @Test
    fun `M return clip W calculateClipping() { overlaps left }`(
        @Mock mockParentRect: Rect,
        @Mock mockChildRect: Rect,
        forge: Forge
    ) {
        // Given
        val fakeOverlap = 100

        mockParentRect.left = forge.aPositiveInt()
        mockParentRect.top = forge.aPositiveInt()
        mockParentRect.right = forge.aPositiveInt()
        mockParentRect.bottom = forge.aPositiveInt()

        mockChildRect.left = mockParentRect.left - fakeOverlap
        mockChildRect.top = mockParentRect.top
        mockChildRect.right = mockParentRect.right
        mockChildRect.bottom = mockParentRect.bottom

        val expectedClipping = MobileSegment.WireframeClip(0, 0, fakeOverlap.toLong(), 0)

        // When
        val result = testedImageViewUtils.calculateClipping(
            parentRect = mockParentRect,
            childRect = mockChildRect,
            density = 1f
        )

        // Then
        assertThat(result.toWireframeClip()).isEqualTo(expectedClipping)
    }

    @Test
    fun `M return clip W calculateClipping() { overlaps right }`(
        @Mock mockParentRect: Rect,
        @Mock mockChildRect: Rect,
        forge: Forge
    ) {
        // Given
        val fakeOverlap = 100

        mockParentRect.left = forge.aPositiveInt()
        mockParentRect.top = forge.aPositiveInt()
        mockParentRect.right = forge.aPositiveInt()
        mockParentRect.bottom = forge.aPositiveInt()

        mockChildRect.left = mockParentRect.left
        mockChildRect.top = mockParentRect.top
        mockChildRect.right = mockParentRect.right + fakeOverlap
        mockChildRect.bottom = mockParentRect.bottom

        val expectedClipping = MobileSegment.WireframeClip(0, 0, 0, fakeOverlap.toLong())

        // When
        val result = testedImageViewUtils.calculateClipping(
            parentRect = mockParentRect,
            childRect = mockChildRect,
            density = 1f
        )

        // Then
        assertThat(result.toWireframeClip()).isEqualTo(expectedClipping)
    }

    @Test
    fun `M return clip W calculateClipping() { overlaps top }`(
        @Mock mockParentRect: Rect,
        @Mock mockChildRect: Rect,
        forge: Forge
    ) {
        // Given
        val fakeOverlap = 100

        mockParentRect.left = forge.aPositiveInt()
        mockParentRect.top = forge.aPositiveInt()
        mockParentRect.right = forge.aPositiveInt()
        mockParentRect.bottom = forge.aPositiveInt()

        mockChildRect.left = mockParentRect.left
        mockChildRect.top = mockParentRect.top - fakeOverlap
        mockChildRect.right = mockParentRect.right
        mockChildRect.bottom = mockParentRect.bottom

        val expectedClipping = MobileSegment.WireframeClip(fakeOverlap.toLong(), 0, 0, 0)

        // When
        val result = testedImageViewUtils.calculateClipping(
            parentRect = mockParentRect,
            childRect = mockChildRect,
            density = 1f
        )

        // Then
        assertThat(result.toWireframeClip()).isEqualTo(expectedClipping)
    }

    @Test
    fun `M return clip W calculateClipping() { overlaps bottom }`(
        @Mock mockParentRect: Rect,
        @Mock mockChildRect: Rect,
        forge: Forge
    ) {
        // Given
        val fakeOverlap = 100

        mockParentRect.left = forge.aPositiveInt()
        mockParentRect.top = forge.aPositiveInt()
        mockParentRect.right = forge.aPositiveInt()
        mockParentRect.bottom = forge.aPositiveInt()

        mockChildRect.left = mockParentRect.left
        mockChildRect.top = mockParentRect.top
        mockChildRect.right = mockParentRect.right
        mockChildRect.bottom = mockParentRect.bottom + fakeOverlap

        val expectedClipping = MobileSegment.WireframeClip(0, fakeOverlap.toLong(), 0, 0)

        // When
        val result = testedImageViewUtils.calculateClipping(
            parentRect = mockParentRect,
            childRect = mockChildRect,
            density = 1f
        )

        // Then
        assertThat(result.toWireframeClip()).isEqualTo(expectedClipping)
    }

    // endregion

    @Test
    fun `M return abs position on screen W resolveParentRectAbsPosition()`(forge: Forge) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeTopPadding = forge.aPositiveInt()
        val fakeBottomPadding = forge.aPositiveInt()
        val fakeLeftPadding = forge.aPositiveInt()
        val fakeRightPadding = forge.aPositiveInt()
        val mockView: View = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.paddingTop).thenReturn(fakeTopPadding)
            whenever(it.paddingLeft).thenReturn(fakeLeftPadding)
            whenever(it.paddingRight).thenReturn(fakeRightPadding)
            whenever(it.paddingBottom).thenReturn(fakeBottomPadding)
        }

        // When
        val result = testedImageViewUtils.resolveParentRectAbsPosition(mockView)

        // Then
        assertThat(result.left).isEqualTo(fakeGlobalX + fakeLeftPadding)
        assertThat(result.top).isEqualTo(fakeGlobalY + fakeTopPadding)
        assertThat(result.right).isEqualTo(fakeGlobalX + fakeWidth - fakeRightPadding)
        assertThat(result.bottom).isEqualTo(fakeGlobalY + fakeHeight - fakeBottomPadding)
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { FIT_START }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.aPositiveInt()
        val fakeDrawableHeight = forge.aPositiveInt()
        val fakeScaleType = ImageView.ScaleType.FIT_START
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.left).isEqualTo(parentRect.left)
        assertThat(result.top).isEqualTo(parentRect.top)
        assertThat(result.width().isCloseToOrLessThan(fakeWidth)).isTrue
        assertThat(result.height().isCloseToOrLessThan(fakeHeight)).isTrue
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { FIT_END }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.aPositiveInt()
        val fakeDrawableHeight = forge.aPositiveInt()
        val fakeScaleType = ImageView.ScaleType.FIT_END
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.right).isEqualTo(parentRect.right)
        assertThat(result.bottom).isEqualTo(parentRect.bottom)
        assertThat(result.width().isCloseToOrLessThan(fakeWidth)).isTrue
        assertThat(result.height().isCloseToOrLessThan(fakeHeight)).isTrue
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { FIT_CENTER gt parent }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.anInt(min = fakeWidth + 1)
        val fakeDrawableHeight = forge.anInt(min = fakeHeight + 1)
        val fakeScaleType = ImageView.ScaleType.FIT_CENTER
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.left).isEqualTo(parentRect.centerX() - (result.width() / 2))
        assertThat(result.top).isEqualTo(parentRect.centerY() - (result.height() / 2))
        assertThat(result.width().isCloseToOrLessThan(fakeWidth)).isTrue
        assertThat(result.height().isCloseToOrLessThan(fakeHeight)).isTrue
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { FIT_CENTER lt parent }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.anInt(min = 1, max = fakeWidth - 1)
        val fakeDrawableHeight = forge.anInt(min = 1, max = fakeHeight - 1)
        val fakeScaleType = ImageView.ScaleType.FIT_CENTER
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.left).isEqualTo(parentRect.centerX() - (result.width() / 2))
        assertThat(result.top).isEqualTo(parentRect.centerY() - (result.height() / 2))
        assertThat(result.width().isCloseToOrLessThan(parentRect.width())).isTrue
        assertThat(result.height().isCloseToOrLessThan(parentRect.height())).isTrue

        // must scale up
        if (result.width() > result.height()) {
            assertThat(result.width()).isGreaterThan(fakeDrawableWidth)
        } else {
            assertThat(result.height()).isGreaterThan(fakeDrawableHeight)
        }
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { CENTER_INSIDE gt parent }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.anInt(min = fakeWidth + 1)
        val fakeDrawableHeight = forge.anInt(min = fakeHeight + 1)
        val fakeScaleType = ImageView.ScaleType.CENTER_INSIDE
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.left).isEqualTo(parentRect.centerX() - (result.width() / 2))
        assertThat(result.top).isEqualTo(parentRect.centerY() - (result.height() / 2))
        assertThat(result.width().isCloseToOrLessThan(fakeWidth)).isTrue
        assertThat(result.height().isCloseToOrLessThan(fakeHeight)).isTrue
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { CENTER_INSIDE lt parent }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.anInt(min = 1, max = fakeWidth - 1)
        val fakeDrawableHeight = forge.anInt(min = 1, max = fakeHeight - 1)
        val fakeScaleType = ImageView.ScaleType.CENTER_INSIDE
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.left).isEqualTo(parentRect.centerX() - (result.width() / 2))
        assertThat(result.top).isEqualTo(parentRect.centerY() - (result.height() / 2))
        assertThat(result.width().isCloseToOrLessThan(fakeWidth)).isTrue
        assertThat(result.height().isCloseToOrLessThan(fakeHeight)).isTrue

        // do not scale up
        assertThat(result.width()).isEqualTo(fakeDrawableWidth)
        assertThat(result.height()).isEqualTo(fakeDrawableHeight)
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { CENTER }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.aPositiveInt()
        val fakeDrawableHeight = forge.aPositiveInt()
        val fakeScaleType = ImageView.ScaleType.CENTER
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.left).isEqualTo(parentRect.centerX() - (result.width() / 2))
        assertThat(result.top).isEqualTo(parentRect.centerY() - (result.height() / 2))

        // do not scale
        assertThat(result.width()).isEqualTo(fakeDrawableWidth)
        assertThat(result.height()).isEqualTo(fakeDrawableHeight)
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { CENTER_CROP }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.aPositiveInt()
        val fakeDrawableHeight = forge.aPositiveInt()
        val fakeScaleType = ImageView.ScaleType.CENTER_CROP
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result.width().isCloseToOrGreaterThan(parentRect.width())).isTrue
        assertThat(result.height().isCloseToOrGreaterThan(parentRect.height())).isTrue
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { FIT_XY }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.aPositiveInt()
        val fakeDrawableHeight = forge.aPositiveInt()
        val fakeScaleType = ImageView.ScaleType.FIT_XY
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result).isEqualTo(parentRect)
    }

    @Test
    fun `M return content rect W resolveContentRectWithScaling() { MATRIX }`(
        @Mock mockDrawable: Drawable,
        forge: Forge
    ) {
        // Given
        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val fakeDrawableWidth = forge.aPositiveInt()
        val fakeDrawableHeight = forge.aPositiveInt()
        val fakeScaleType = ImageView.ScaleType.MATRIX
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)

        val mockImageView: ImageView = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
            whenever(it.scaleType).thenReturn(fakeScaleType)
        }

        val parentRect = Rect(
            fakeGlobalX,
            fakeGlobalY,
            fakeGlobalX + fakeWidth,
            fakeGlobalY + fakeHeight
        )

        // When
        val result = testedImageViewUtils.resolveContentRectWithScaling(
            imageView = mockImageView,
            drawable = mockDrawable
        )

        // Then
        assertThat(result).isEqualTo(parentRect)
    }
}
