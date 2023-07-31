/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.widget.SeekBar
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal abstract class BaseSeekBarWireframeMapperTest {

    // misc
    @Forgery
    lateinit var fakeMappingContext: MappingContext

    // fake wireframe ids
    @LongForgery
    var fakeActiveTrackId: Long = 0

    @LongForgery
    var fakeInactiveTrackId: Long = 0

    @LongForgery
    var fakeThumbId: Long = 0

    // fake slider bounds
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @LongForgery(min = 0, max = 100)
    var fakeSliderXPos: Long = 0

    @LongForgery(min = 0, max = 100)
    var fakeSliderYPos: Long = 0

    @IntForgery(min = 0, max = 10)
    var fakeSeekBarStartPadding: Int = 0

    @IntForgery(min = 0, max = 10)
    var fakeSeekBarTopPadding: Int = 0

    @LongForgery(min = 10, max = 200)
    var fakeSeekBarHeight: Long = 0

    @LongForgery(min = 10, max = 200)
    var fakeSeekBarWidth: Long = 0

    // fake track bounds
    @IntForgery(min = 1, max = 200)
    var fakeTrackWidth: Int = 0

    // fake thumb bounds
    @IntForgery(min = 1, max = 200)
    var fakeThumbHeight: Int = 0

    // fake progress values
    var fakeProgressValue: Int = 0

    @IntForgery(min = 0, max = 100 / 2)
    var fakeProgressMinValue: Int = 0

    @IntForgery(min = 100 / 2, max = 100)
    var fakeProgressMaxValue: Int = 0

    // fake track colors
    @IntForgery
    var fakeTrackColor: Int = 0

    // fake thumb colors
    @IntForgery
    var fakeThumbColor: Int = 0

    // expected thumb bounds
    var fakeExpectedThumbHeight: Long = 0
    var fakeExpectedThumbXPos: Long = 0
    var fakeExpectedThumbYPos: Long = 0

    // expected track bounds
    var fakeExpectedInactiveTrackWidth: Long = 0
    var fakeExpectedInactiveTrackHeight: Long = 0
    var fakeExpectedActiveTrackWidth: Long = 0
    var fakeExpectedActiveTrackHeight: Long = 0
    var fakeExpectedActiveTrackXPos: Long = 0
    var fakeExpectedActiveTrackYPos: Long = 0
    var fakeExpectedInactiveTrackXPos: Long = 0
    var fakeExpectedInactiveTrackYPos: Long = 0

    // fake expected track html colors
    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedTrackActiveHtmlColor: String

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedTrackInactiveHtmlColor: String

    // fake expected thumb html colors
    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeExpectedThumbHtmlColor: String

    @FloatForgery(0f, 1f)
    var fakeViewAlpha: Float = 0f

    @Mock
    lateinit var mockThumbTintColors: ColorStateList

    @Mock
    lateinit var mockTrackActiveTintColors: ColorStateList

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Mock
    lateinit var mockStringUtils: StringUtils

    @Mock
    lateinit var mockUniqueIdentifierGenerator: UniqueIdentifierGenerator

    lateinit var testedSeekBarWireframeMapper: SeekBarWireframeMapper

    lateinit var mockSeekBar: SeekBar

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewGlobalBounds = GlobalBounds(
            fakeSliderXPos,
            fakeSliderYPos,
            fakeSeekBarWidth,
            fakeSeekBarHeight
        )
        fakeProgressValue = forge.anInt(min = fakeProgressMinValue, max = fakeProgressMaxValue)
        val normalizedSliderYPos = fakeSliderYPos
        val normalizedSliderXPos = fakeSliderXPos
        val normalizedSliderTopPadding = fakeSeekBarTopPadding.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        val normalizedSliderStartPadding = fakeSeekBarStartPadding.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        val normalizedSliderHeight = fakeSeekBarHeight
        val normalizedSliderValue = (fakeProgressValue.toFloat() - fakeProgressMinValue.toFloat()) /
            (fakeProgressMaxValue.toFloat() - fakeProgressMinValue.toFloat())

        fakeExpectedInactiveTrackHeight = SeekBarWireframeMapper.TRACK_HEIGHT_IN_PX
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedActiveTrackHeight = fakeExpectedInactiveTrackHeight

        fakeExpectedInactiveTrackWidth = fakeTrackWidth.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedActiveTrackWidth = (fakeExpectedInactiveTrackWidth * normalizedSliderValue)
            .toLong()
        fakeExpectedThumbHeight = fakeThumbHeight.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        fakeExpectedInactiveTrackXPos = normalizedSliderXPos + normalizedSliderStartPadding
        fakeExpectedInactiveTrackYPos = normalizedSliderYPos + normalizedSliderTopPadding +
            (normalizedSliderHeight - fakeExpectedActiveTrackHeight) / 2
        fakeExpectedActiveTrackYPos = fakeExpectedInactiveTrackYPos
        fakeExpectedActiveTrackXPos = fakeExpectedInactiveTrackXPos
        fakeExpectedActiveTrackXPos = fakeExpectedInactiveTrackXPos
        fakeExpectedActiveTrackYPos = fakeExpectedInactiveTrackYPos
        fakeExpectedThumbXPos = fakeExpectedActiveTrackXPos + fakeExpectedActiveTrackWidth
        fakeExpectedThumbYPos = normalizedSliderYPos + normalizedSliderTopPadding +
            (normalizedSliderHeight - fakeExpectedThumbHeight) / 2
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                fakeThumbColor,
                SeekBarWireframeMapper.OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedThumbHtmlColor)

        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                fakeTrackColor,
                SeekBarWireframeMapper.OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedTrackActiveHtmlColor)
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                fakeTrackColor,
                SeekBarWireframeMapper.PARTIALLY_OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedTrackInactiveHtmlColor)

        mockSeekBar = generateMockedSeekBar(forge)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockSeekBar,
                fakeMappingContext.systemInformation.screenDensity
            )
        )
            .thenReturn(fakeViewGlobalBounds)
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockSeekBar,
                SeekBarWireframeMapper.TRACK_ACTIVE_KEY_NAME
            )
        ).thenReturn(fakeActiveTrackId)
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockSeekBar,
                SeekBarWireframeMapper.TRACK_NON_ACTIVE_KEY_NAME
            )
        ).thenReturn(fakeInactiveTrackId)
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockSeekBar,
                SeekBarWireframeMapper.THUMB_KEY_NAME
            )
        ).thenReturn(fakeThumbId)
        testedSeekBarWireframeMapper = provideTestInstance()
    }

    abstract fun provideTestInstance(): SeekBarWireframeMapper

    @Test
    fun `M return empty list W map { could not generate thumb id`() {
        // Given
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockSeekBar,
                SeekBarWireframeMapper.THUMB_KEY_NAME
            )
        ).thenReturn(null)

        // Then
        assertThat(testedSeekBarWireframeMapper.map(mockSeekBar, fakeMappingContext)).isEmpty()
    }

    @Test
    fun `M return empty list W map { could not generate active track id`() {
        // Given
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockSeekBar,
                SeekBarWireframeMapper.TRACK_ACTIVE_KEY_NAME
            )
        ).thenReturn(null)

        // Then
        assertThat(testedSeekBarWireframeMapper.map(mockSeekBar, fakeMappingContext)).isEmpty()
    }

    @Test
    fun `M return empty list W map { could not generate inactive track id`() {
        // Given
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockSeekBar,
                SeekBarWireframeMapper.TRACK_NON_ACTIVE_KEY_NAME
            )
        ).thenReturn(null)

        // Then
        assertThat(testedSeekBarWireframeMapper.map(mockSeekBar, fakeMappingContext)).isEmpty()
    }

    private fun generateMockedSeekBar(forge: Forge): SeekBar {
        return mock {
            val fakeTrackBounds: Rect = mock { rect ->
                whenever(rect.width()).thenReturn(fakeTrackWidth)
            }
            val fakeThumbBounds: Rect = mock { rect ->
                whenever(rect.height()).thenReturn(fakeThumbHeight)
            }
            val mockProgressDrawable: Drawable = mock { drawable ->
                whenever(drawable.bounds).thenReturn(fakeTrackBounds)
            }
            val mockThumbDrawable: Drawable = mock { drawable ->
                whenever(drawable.bounds).thenReturn(fakeThumbBounds)
            }
            val fakeDrawableState = forge.aList { anInt() }.toIntArray()
            val defaultColor = forge.aPositiveInt()
            whenever(it.drawableState).thenReturn(fakeDrawableState)
            whenever(it.alpha).thenReturn(fakeViewAlpha)
            whenever(it.paddingTop).thenReturn(fakeSeekBarTopPadding)
            whenever(it.paddingStart).thenReturn(fakeSeekBarStartPadding)
            whenever(it.width).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.height).thenReturn(forge.aPositiveInt(strict = true))
            whenever(it.progressDrawable).thenReturn(mockProgressDrawable)
            whenever(it.thumb).thenReturn(mockThumbDrawable)
            whenever(it.min).thenReturn(fakeProgressMinValue)
            whenever(it.max).thenReturn(fakeProgressMaxValue)
            whenever(it.progress).thenReturn(fakeProgressValue)
            whenever(it.progressTintList).thenReturn(mockTrackActiveTintColors)
            whenever(it.thumbTintList).thenReturn(mockThumbTintColors)
            whenever(mockThumbTintColors.defaultColor).thenReturn(defaultColor)
            whenever(mockTrackActiveTintColors.defaultColor).thenReturn(defaultColor)
            whenever(mockThumbTintColors.getColorForState(fakeDrawableState, defaultColor))
                .thenReturn(fakeThumbColor)
            whenever(mockTrackActiveTintColors.getColorForState(fakeDrawableState, defaultColor))
                .thenReturn(fakeTrackColor)
        }
    }
}
