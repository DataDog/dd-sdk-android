/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SwitchCompatMapperTest : BaseSwitchCompatMapperTest() {

    private val xCaptor = argumentCaptor<Long>()
    private val yCaptor = argumentCaptor<Long>()
    private val widthCaptor = argumentCaptor<Int>()
    private val heightCaptor = argumentCaptor<Int>()
    private val drawableCaptor = argumentCaptor<Drawable>()
    override fun setupTestedMapper(): SwitchCompatMapper {
        return SwitchCompatMapper(
            mockTextWireframeMapper,
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    @RepeatedTest(8)
    fun `M resolve the switch as wireframes W map()`(forge: Forge) {
        // Given
        whenever(mockSwitch.isChecked).thenReturn(forge.aBool())
        val density = fakeMappingContext.systemInformation.screenDensity
        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTrackIdentifier,
            x = expectedTrackLeft,
            y = expectedTrackTop,
            width = fakeTrackBounds.width().toLong().densityNormalized(density),
            height = fakeTrackBounds.height().toLong().densityNormalized(density),
            border = null,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeCurrentTextColorString,
                mockSwitch.alpha
            )
        )

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext.copy(imagePrivacy = ImagePrivacy.MASK_CONTENT),
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        if (fakeMappingContext.privacy != SessionReplayPrivacy.ALLOW) {
            assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedTrackWireframe)
        } else {
            assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)

            verify(fakeMappingContext.imageWireframeHelper, times(2)).createImageWireframe(
                view = eq(mockSwitch),
                imagePrivacy = eq(ImagePrivacy.MASK_CONTENT),
                currentWireframeIndex = ArgumentMatchers.anyInt(),
                x = xCaptor.capture(),
                y = yCaptor.capture(),
                width = widthCaptor.capture(),
                height = heightCaptor.capture(),
                usePIIPlaceholder = ArgumentMatchers.anyBoolean(),
                drawable = drawableCaptor.capture(),
                asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
                clipping = eq(null),
                shapeStyle = eq(null),
                border = eq(null),
                prefix = ArgumentMatchers.anyString()
            )

            assertThat(xCaptor.allValues).containsOnly(expectedThumbLeft, expectedTrackLeft)
            assertThat(yCaptor.allValues).containsOnly(expectedThumbTop, expectedTrackTop)
            assertThat(widthCaptor.allValues).containsOnly(
                mockThumbDrawable.intrinsicWidth,
                (fakeTrackBounds.width())
            )
            assertThat(heightCaptor.allValues).containsOnly(
                mockThumbDrawable.intrinsicHeight,
                (fakeTrackBounds.height())
            )
            assertThat(drawableCaptor.allValues).containsOnly(mockThumbDrawable, mockCloneDrawable)
        }
    }

    @Test
    fun `M resolve the switch as wireframes W map() { can't generate id for thumbWireframe for masked view }`(
        forge: Forge
    ) {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSwitch,
                SwitchCompatMapper.THUMB_KEY_NAME
            )
        ).thenReturn(null)
        whenever(mockSwitch.isChecked).thenReturn(forge.aBool())
        val density = fakeMappingContext.systemInformation.screenDensity
        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTrackIdentifier,
            x = expectedTrackLeft,
            y = expectedTrackTop,
            width = fakeTrackBounds.width().toLong().densityNormalized(density),
            height = fakeTrackBounds.height().toLong().densityNormalized(density),
            border = null,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeCurrentTextColorString,
                mockSwitch.alpha
            )
        )

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK, imagePrivacy = ImagePrivacy.MASK_CONTENT),
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedTrackWireframe)
    }

    @RepeatedTest(8)
    fun `M resolve the switch as wireframes W map() { can't generate id for trackWireframe }`(
        forge: Forge
    ) {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSwitch,
                SwitchCompatMapper.TRACK_KEY_NAME
            )
        ).thenReturn(null)
        whenever(mockSwitch.isChecked).thenReturn(forge.aBool())

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext.copy(privacy = SessionReplayPrivacy.MASK, imagePrivacy = ImagePrivacy.MASK_CONTENT),
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
        verify(fakeMappingContext.imageWireframeHelper, never()).createImageWireframe(
            view = any(),
            imagePrivacy = any(),
            currentWireframeIndex = any(),
            x = any(),
            y = any(),
            width = any(),
            height = any(),
            usePIIPlaceholder = any(),
            drawable = any(),
            asyncJobStatusCallback = any(),
            clipping = any(),
            shapeStyle = any(),
            border = any(),
            prefix = any()
        )
    }
}
