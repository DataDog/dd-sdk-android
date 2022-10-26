/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import kotlin.math.abs

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WireframeUtilsTest {

    lateinit var testedWireframeUtils: WireframeUtils

    @BeforeEach
    fun `set up`() {
        testedWireframeUtils = WireframeUtils()
    }

    // region Clipping resolver

    @ParameterizedTest
    @MethodSource("resolveClipWireframes")
    fun `M correctly resolve the Wireframe clip W resolveWireframeClip {clipLeft}`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeExpectedClipLeft = forge.aLong(min = 0, max = 100)

        val fakeParentWireframe = fakeWireframe.copy(
            x = fakeWireframe.x() + fakeExpectedClipLeft,
            y = fakeWireframe.y(),
            width = fakeWireframe.width(),
            height = fakeWireframe.height()
        )
        val fakExpectedClip = MobileSegment.WireframeClip(
            top = 0,
            left = fakeExpectedClipLeft,
            right = 0,
            bottom = 0
        )
        val fakeParents = listOf(fakeParentWireframe)

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeParents))
            .isEqualTo(fakExpectedClip)
    }

    @ParameterizedTest
    @MethodSource("resolveClipWireframes")
    fun `M correctly resolve the Wireframe clip W resolveWireframeClip {clipRight}`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeExpectedClipRight = forge.aLong(min = 0, max = 100)

        val fakeParentWireframe = fakeWireframe.copy(
            x = fakeWireframe.x(),
            y = fakeWireframe.y(),
            width = fakeWireframe.width() - fakeExpectedClipRight,
            height = fakeWireframe.height()
        )
        val fakExpectedClip = MobileSegment.WireframeClip(
            top = 0,
            right = fakeExpectedClipRight,
            left = 0,
            bottom = 0
        )
        val fakeParents = listOf(fakeParentWireframe)

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeParents))
            .isEqualTo(fakExpectedClip)
    }

    @ParameterizedTest
    @MethodSource("resolveClipWireframes")
    fun `M correctly resolve the Wireframe clip W resolveWireframeClip {clipTop}`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeExpectedClipTop = forge.aLong(min = 0, max = 100)

        val fakeParentWireframe = fakeWireframe.copy(
            x = fakeWireframe.x(),
            y = fakeWireframe.y() + fakeExpectedClipTop,
            width = fakeWireframe.width(),
            height = fakeWireframe.height()
        )
        val fakExpectedClip = MobileSegment.WireframeClip(
            top = fakeExpectedClipTop,
            right = 0,
            left = 0,
            bottom = 0
        )
        val fakeParents = listOf(fakeParentWireframe)

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeParents))
            .isEqualTo(fakExpectedClip)
    }

    @ParameterizedTest
    @MethodSource("resolveClipWireframes")
    fun `M correctly resolve the Wireframe clip W resolveWireframeClip {clipBottom}`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeExpectedClipBottom = forge.aLong(min = 0, max = 100)

        val fakeParentWireframe = fakeWireframe.copy(
            x = fakeWireframe.x(),
            y = fakeWireframe.y(),
            width = fakeWireframe.width(),
            height = fakeWireframe.height() - fakeExpectedClipBottom
        )
        val fakExpectedClip = MobileSegment.WireframeClip(
            top = 0,
            right = 0,
            left = 0,
            bottom = fakeExpectedClipBottom
        )
        val fakeParents = listOf(fakeParentWireframe)

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeParents))
            .isEqualTo(fakExpectedClip)
    }

    @ParameterizedTest
    @MethodSource("resolveClipWireframes")
    fun `M correctly resolve the Wireframe clip W resolveWireframeClip {clip all sides}`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeExpectedClipBottom = forge.aLong(min = 0, max = 5)
        val fakeExpectedClipTop = forge.aLong(min = 0, max = 5)
        val fakeExpectedClipLeft = forge.aLong(min = 0, max = 5)
        val fakeExpectedClipRight = forge.aLong(min = 0, max = 5)

        val fakeParentWireframe = fakeWireframe.copy(
            x = fakeWireframe.x() + fakeExpectedClipLeft,
            y = fakeWireframe.y() + fakeExpectedClipTop,
            width = fakeWireframe.width() - fakeExpectedClipRight - fakeExpectedClipLeft,
            height = fakeWireframe.height() - fakeExpectedClipBottom - fakeExpectedClipTop
        )
        val fakExpectedClip = MobileSegment.WireframeClip(
            top = fakeExpectedClipTop,
            right = fakeExpectedClipRight,
            left = fakeExpectedClipLeft,
            bottom = fakeExpectedClipBottom
        )

        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList<MobileSegment.Wireframe> {
            val aClipLeft = forge.aLong(min = -100, max = fakeExpectedClipLeft)
            val aClipTop = forge.aLong(min = -100, max = fakeExpectedClipTop)
            val aClipRight = forge.aLong(min = -100, max = fakeExpectedClipRight) - aClipLeft
            val aClipBottom = forge.aLong(min = -100, max = fakeExpectedClipBottom) - aClipTop
            fakeWireframe.apply {
                copy(
                    x = fakeWireframe.x() + aClipLeft,
                    y = fakeWireframe.y() + aClipTop,
                    width = fakeWireframe.width() - aClipRight,
                    height = fakeWireframe.height() - aClipBottom
                )
            }
        }.toMutableList()
        val randomIndex = forge.anInt(min = 0, max = fakeRandomParents.size)
        val fakeParents = fakeRandomParents.toMutableList().apply {
            add(randomIndex, fakeParentWireframe)
        }

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeParents))
            .isEqualTo(fakExpectedClip)
    }

    @ParameterizedTest
    @MethodSource("resolveClipWireframes")
    fun `M return null W resolveWireframeClip {inside parents bounds}`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeParents: List<MobileSegment.Wireframe> = forge.aList {
            val fakeSpaceTop = forge.aLong(min = 0, max = 100)
            val fakeSpaceLeft = forge.aLong(min = 0, max = 100)
            fakeWireframe.copy(
                x = fakeWireframe.x() - fakeSpaceLeft,
                y = fakeWireframe.y() - fakeSpaceTop,
                width = fakeWireframe.width() + fakeSpaceLeft + forge.aLong(min = 0, max = 100),
                height = fakeWireframe.height() + fakeSpaceTop + forge.aLong(min = 0, max = 100)
            )
        }

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeParents))
            .isNull()
    }

    @Test
    fun `M return null W resolveWireframeClip{no parents}`(forge: Forge) {
        // Given
        val wireframe: MobileSegment.Wireframe = forge.getForgery()

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(wireframe, emptyList())).isNull()
    }

    // endregion

    // region is valid Wireframe

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return false W checkIsValidWireframe(){ covered by another }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeX = forge.aLong(min = -100, max = fakeWireframe.x())
            val fakeY = forge.aLong(min = -100, max = fakeWireframe.y())
            val fakeMinWidth = abs(fakeX) - abs(fakeWireframe.x()) + fakeWireframe.width()
            val fakeMinHeight = abs(fakeY) - abs(fakeWireframe.y()) + fakeWireframe.height()
            val fakeWidth = forge.aLong(min = fakeMinWidth, max = Int.MAX_VALUE.toLong())
            val fakeHeight = forge.aLong(min = fakeMinHeight, max = Int.MAX_VALUE.toLong())
            val fakeCoverAllWireframe = fakeWireframe.copy(
                x = fakeX,
                y = fakeY,
                width = fakeWidth,
                height = fakeHeight
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isFalse
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ top is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeY = forge.aLong(min = fakeWireframe.y() + 1)
            val fakeCoverAllWireframe = fakeWireframe.copy(
                x = fakeWireframe.x(),
                y = fakeY,
                width = fakeWireframe.width(),
                height = fakeWireframe.height()
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ bottom is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeHeight = forge.aLong(min = 0, max = fakeWireframe.height() - 1)
            val fakeCoverAllWireframe = fakeWireframe.copy(
                x = fakeWireframe.x(),
                y = fakeWireframe.y(),
                width = fakeWireframe.width(),
                height = fakeHeight
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ left is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeX = forge.aLong(min = fakeWireframe.x() + 1)
            val fakeCoverAllWireframe = fakeWireframe.copy(
                x = fakeX,
                y = fakeWireframe.y(),
                width = fakeWireframe.width(),
                height = fakeWireframe.height()
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ right is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeWidth = forge.aLong(min = 0, max = fakeWireframe.width() - 1)
            val fakeCoverAllWireframe = fakeWireframe.copy(
                x = fakeWireframe.x(),
                y = fakeWireframe.y(),
                width = fakeWidth,
                height = fakeWireframe.height()
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ all sides are bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeX = forge.aLong(min = fakeWireframe.x() + 1)
            val fakeY = forge.aLong(min = fakeWireframe.y() + 1)
            val fakeWidth = forge.aLong(min = 0, max = fakeWireframe.width() - 1)
            val fakeHeight = forge.aLong(min = 0, max = fakeWireframe.height() - 1)
            val fakeCoverAllWireframe = fakeWireframe.copy(
                x = fakeX,
                y = fakeY,
                width = fakeWidth,
                height = fakeHeight
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ parent clip left is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeWireframeClipLeft = fakeWireframe.clip()?.left ?: 0
            val fakeParentClipLeft = forge.aLong(
                min = fakeWireframeClipLeft + 1,
                max = fakeWireframeClipLeft + 10
            )
            val fakeParentClip = fakeWireframe.clip()?.copy(left = fakeParentClipLeft)
                ?: MobileSegment.WireframeClip(left = fakeParentClipLeft)
            val fakeCoverAllWireframe = fakeWireframe.copy(clip = fakeParentClip)
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ parent clip right is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeWireframeClipRight = fakeWireframe.clip()?.right ?: 0
            val fakeParentClipRight = forge.aLong(
                min = fakeWireframeClipRight + 1,
                max = fakeWireframeClipRight + 10
            )
            val fakeParentClip = fakeWireframe.clip()?.copy(right = fakeParentClipRight)
                ?: MobileSegment.WireframeClip(right = fakeParentClipRight)
            val fakeCoverAllWireframe = fakeWireframe.copy(clip = fakeParentClip)
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ parent clip top is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeWireframeClipTop = fakeWireframe.clip()?.top ?: 0
            val fakeParentClipTop = forge.aLong(
                min = fakeWireframeClipTop + 1,
                max = fakeWireframeClipTop + 10
            )
            val fakeParentClip = fakeWireframe.clip()?.copy(top = fakeParentClipTop)
                ?: MobileSegment.WireframeClip(top = fakeParentClipTop)
            val fakeCoverAllWireframe = fakeWireframe.copy(clip = fakeParentClip)
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @ParameterizedTest
    @MethodSource("coverAllWireframes")
    fun `M return true W checkIsValidWireframe(){ parent clip bottom is bigger }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeWireframeClipBottom = fakeWireframe.clip()?.bottom ?: 0
            val fakeParentClipBottom = forge.aLong(
                min = fakeWireframeClipBottom + 1,
                max = fakeWireframeClipBottom + 10
            )
            val fakeParentClip = fakeWireframe.clip()?.copy(bottom = fakeParentClipBottom)
                ?: MobileSegment.WireframeClip(bottom = fakeParentClipBottom)
            val fakeCoverAllWireframe = fakeWireframe.copy(clip = fakeParentClip)
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, topWireframes)).isTrue
    }

    @Test
    fun `M return false W checkIsValidWireframe(){ wireframe width is 0 }`(
        forge: Forge
    ) {
        // Given
        val fakeTopWireframes: MutableList<MobileSegment.Wireframe> =
            forge.aList { getForgeryWithIntRangeCoordinates() }.toMutableList()
        val fakeWireframe = forge.getForgeryWithIntRangeCoordinates().copyWithWidth(width = 0)

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, fakeTopWireframes))
            .isFalse
    }

    @Test
    fun `M return true W checkIsValidWireframe(){ wireframe not covered }`(
        forge: Forge
    ) {
        // Given
        val fakeTopWireframes: MutableList<MobileSegment.Wireframe> =
            forge.aList { getForgeryWithIntRangeCoordinates() }.toMutableList()
        val fakeWireframe = forge.getForgeryWithIntRangeCoordinates().copyWithHeight(height = 0)

        // Then
        assertThat(testedWireframeUtils.checkIsValidWireframe(fakeWireframe, fakeTopWireframes))
            .isFalse
    }

    // endregion

    // region Internal

    private fun MobileSegment.Wireframe.copy(
        x: Long,
        y: Long,
        width: Long,
        height: Long,
        clip: MobileSegment.WireframeClip?
    ):
        MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> copy(
                x = x,
                y = y,
                width = width,
                height = height,
                clip = clip
            )
            is MobileSegment.Wireframe.TextWireframe -> copy(
                x = x,
                y = y,
                width = width,
                height = height,
                clip = clip
            )
        }
    }

    private fun MobileSegment.Wireframe.copy(x: Long, y: Long, width: Long, height: Long):
        MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> copy(
                x = x,
                y = y,
                width = width,
                height = height
            )
            is MobileSegment.Wireframe.TextWireframe -> copy(
                x = x,
                y = y,
                width = width,
                height = height
            )
        }
    }

    private fun MobileSegment.Wireframe.copyWithWidth(width: Long):
        MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> copy(width = width)
            is MobileSegment.Wireframe.TextWireframe -> copy(width = width)
        }
    }

    private fun MobileSegment.Wireframe.copyWithHeight(height: Long):
        MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> copy(height = height)
            is MobileSegment.Wireframe.TextWireframe -> copy(height = height)
        }
    }

    private fun MobileSegment.Wireframe.clip(): MobileSegment.WireframeClip? {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> clip
            is MobileSegment.Wireframe.TextWireframe -> clip
        }
    }

    private fun MobileSegment.Wireframe.x(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> x
            is MobileSegment.Wireframe.TextWireframe -> x
        }
    }

    private fun MobileSegment.Wireframe.y(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> y
            is MobileSegment.Wireframe.TextWireframe -> y
        }
    }

    private fun MobileSegment.Wireframe.width(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> width
            is MobileSegment.Wireframe.TextWireframe -> width
        }
    }

    private fun MobileSegment.Wireframe.height(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> height
            is MobileSegment.Wireframe.TextWireframe -> height
        }
    }

    private fun Forge.getForgeryWithIntRangeCoordinates(): MobileSegment.Wireframe {
        return getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(
                x = aLong(min = 1, max = 100),
                y = aLong(min = 1, max = 100),
                width = aLong(min = 1, max = 100),
                height = aLong(min = 1, max = 100)
            )
    }
    // endregion

    companion object {
        val forge = Forge()

        private fun MobileSegment.Wireframe.copy(
            x: Long,
            y: Long,
            width: Long,
            height: Long,
            clip: MobileSegment.WireframeClip?
        ):
            MobileSegment.Wireframe {
            return when (this) {
                is MobileSegment.Wireframe.ShapeWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    clip = clip
                )
                is MobileSegment.Wireframe.TextWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    clip = clip
                )
            }
        }

        private fun MobileSegment.Wireframe.copy(x: Long, y: Long, width: Long, height: Long):
            MobileSegment.Wireframe {
            return when (this) {
                is MobileSegment.Wireframe.ShapeWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height
                )
                is MobileSegment.Wireframe.TextWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height
                )
            }
        }

        @JvmStatic
        fun resolveClipWireframes(): List<MobileSegment.Wireframe> {
            ForgeConfigurator().configure(forge)
            val negativeCoordinatesWireframe = forge.getForgery<MobileSegment.Wireframe>()
                .copy(
                    x = forge.aLong(min = -100, max = 0),
                    y = forge.aLong(min = -100, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null
                )
            val positiveCoordinatesWireframe = forge.getForgery<MobileSegment.Wireframe>()
                .copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null
                )
            return listOf(negativeCoordinatesWireframe, positiveCoordinatesWireframe)
        }

        @JvmStatic
        fun coverAllWireframes(): List<MobileSegment.Wireframe> {
            ForgeConfigurator().configure(forge)
            val negativeCoordinatesWireframe = forge.getForgery<MobileSegment.Wireframe>()
                .copy(
                    x = forge.aLong(min = -100, max = 0),
                    y = forge.aLong(min = -100, max = 0),
                    width = forge.aLong(min = 1, max = 100),
                    height = forge.aLong(min = 1, max = 100),
                    clip = forge.getForgery()
                )
            val positiveCoordinatesWireframe = forge.getForgery<MobileSegment.Wireframe>()
                .copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery()
                )
            val negativeCoordinatesWireframeNoClip = forge.getForgery<MobileSegment.Wireframe>()
                .copy(
                    x = forge.aLong(min = -100, max = 0),
                    y = forge.aLong(min = -100, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null
                )
            val positiveCoordinatesWireframeNoClip = forge.getForgery<MobileSegment.Wireframe>()
                .copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null
                )
            return listOf(
                negativeCoordinatesWireframe,
                positiveCoordinatesWireframe,
                negativeCoordinatesWireframeNoClip,
                positiveCoordinatesWireframeNoClip
            )
        }
    }
}
