/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
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
        val fakeExpectedClipLeft = forge.aLong(min = 1, max = 100)

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
        val fakeExpectedClipRight = forge.aLong(min = 1, max = 100)

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
        val fakeExpectedClipTop = forge.aLong(min = 1, max = 100)

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
        val fakeExpectedClipBottom = forge.aLong(min = 1, max = 100)

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
        val fakeExpectedClipBottom = forge.aLong(min = 1, max = 5)
        val fakeExpectedClipTop = forge.aLong(min = 1, max = 5)
        val fakeExpectedClipLeft = forge.aLong(min = 1, max = 5)
        val fakeExpectedClipRight = forge.aLong(min = 1, max = 5)

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

        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
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

    // region is checkWireframeIsCovered

    @ParameterizedTest
    @MethodSource("wireframesWithShapeStyle")
    fun `M return true W checkWireframeIsCovered(){ shapeStyle wireframe with solid background }`(
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
                height = fakeHeight,
                shapeStyle = forge.forgeNonTransparentShapeStyle()
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isTrue
    }

    @ParameterizedTest
    @MethodSource("wireframesWithShapeStyle")
    fun `M return false W checkWireframeIsCovered(){ shapeStyle wireframe without background }`(
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
                height = fakeHeight,
                shapeStyle = null
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithShapeStyle")
    fun `M return false W checkWireframeIsCovered(){ shapeStyleWireframe translucent background }`(
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
                height = fakeHeight,
                shapeStyle = forge.forgeNonTransparentShapeStyle()
                    .copy(opacity = forge.aFloat(min = 0f, max = 1f))
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithShapeStyle")
    fun `M return false W checkWireframeIsCovered(){shapeStyleWireframe background with no color}`(
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
                height = fakeHeight,
                shapeStyle = forge.forgeNonTransparentShapeStyle()
                    .copy(backgroundColor = null)
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithShapeStyle")
    fun `M return false W checkWireframeIsCovered(){shapeStyleWireframe translucent color}`(
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
                height = fakeHeight,
                shapeStyle = forge.forgeNonTransparentShapeStyle()
                    .copy(
                        backgroundColor = forge.aStringMatching(
                            "#[0-9A-Fa-f]{6}[0-9A-Ea-e]{2}"
                        )
                    )
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("placeholderWireframes")
    fun `M return true W checkWireframeIsCovered(){ PlaceHolder wireframe }`(
        fakeWireframe: MobileSegment.Wireframe.PlaceholderWireframe,
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isTrue
    }

    @ParameterizedTest
    @MethodSource("imageWireframesWithoutShapeStyle")
    fun `M return true W checkWireframeIsCovered { valid ImageWireframe, no solid background }`(
        fakeWireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeX = forge.aLong(min = -100, max = fakeWireframe.x())
            val fakeY = forge.aLong(min = -100, max = fakeWireframe.y())
            val fakeMinWidth = abs(fakeX) - abs(fakeWireframe.x()) +
                fakeWireframe.width()
            val fakeMinHeight = abs(fakeY) - abs(fakeWireframe.y()) +
                fakeWireframe.height()
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isTrue
    }

    @ParameterizedTest
    @MethodSource("imageWireframesWithoutShapeStyle")
    fun `M return false W checkWireframeIsCovered { invalid ImageWireframe, no solid background }`(
        fakeWireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        // Given
        val topWireframes = forge.aList {
            val fakeX = forge.aLong(min = -100, max = fakeWireframe.x())
            val fakeY = forge.aLong(min = -100, max = fakeWireframe.y())
            val fakeMinWidth = abs(fakeX) - abs(fakeWireframe.x()) +
                fakeWireframe.width()
            val fakeMinHeight = abs(fakeY) - abs(fakeWireframe.y()) +
                fakeWireframe.height()
            val fakeWidth = forge.aLong(min = fakeMinWidth, max = Int.MAX_VALUE.toLong())
            val fakeHeight = forge.aLong(min = fakeMinHeight, max = Int.MAX_VALUE.toLong())
            val fakeCoverAllWireframe = fakeWireframe.copy(
                x = fakeX,
                y = fakeY,
                width = fakeWidth,
                height = fakeHeight,
                base64 = null
            )
            fakeCoverAllWireframe
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ top is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ bottom is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ left is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ right is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ all sides are bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ parent clip left is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ parent clip right is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ parent clip top is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @ParameterizedTest
    @MethodSource("wireframesWithSolidBackground")
    fun `M return false W checkWireframeIsCovered(){ parent clip bottom is bigger }`(
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
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    // endregion

    // region checkWireframesIsValid

    @Test
    fun `M return false W checkWireframeIsValid(){ wireframe width is 0 }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgeryWithIntRangeCoordinates().copyWithWidth(width = 0)

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isFalse
    }

    @Test
    fun `M return false W checkWireframeIsValid(){ wireframe height is 0 }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgeryWithIntRangeCoordinates().copyWithHeight(height = 0)

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isFalse
    }

    @Test
    fun `M return false W checkWireframeIsValid(){ shape wireframe with no border and background }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(shapeStyle = null, border = null)

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isFalse
    }

    @Test
    fun `M return true W checkWireframeIsValid(){ shape wireframe with no border }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(shapeStyle = forge.getForgery(), border = null)

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isTrue
    }

    @Test
    fun `M return true W checkWireframeIsValid(){ shape wireframe with no background }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(shapeStyle = null, border = forge.getForgery())

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isTrue
    }

    @Test
    fun `M return true W checkWireframeIsValid(){ text wireframe with no border and background }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
            .copy(shapeStyle = null, border = null)

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isTrue
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
            is MobileSegment.Wireframe.ImageWireframe -> copy(
                x = x,
                y = y,
                width = width,
                height = height,
                clip = clip
            )
            is MobileSegment.Wireframe.PlaceholderWireframe -> copy(
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
            is MobileSegment.Wireframe.ImageWireframe -> copy(
                x = x,
                y = y,
                width = width,
                height = height
            )
            is MobileSegment.Wireframe.PlaceholderWireframe -> copy(
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
            is MobileSegment.Wireframe.ImageWireframe -> copy(width = width)
            is MobileSegment.Wireframe.PlaceholderWireframe -> copy(width = width)
        }
    }

    private fun MobileSegment.Wireframe.copyWithHeight(height: Long):
        MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> copy(height = height)
            is MobileSegment.Wireframe.TextWireframe -> copy(height = height)
            is MobileSegment.Wireframe.ImageWireframe -> copy(height = height)
            is MobileSegment.Wireframe.PlaceholderWireframe -> copy(height = height)
        }
    }

    private fun MobileSegment.Wireframe.clip(): MobileSegment.WireframeClip? {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> clip
            is MobileSegment.Wireframe.TextWireframe -> clip
            is MobileSegment.Wireframe.ImageWireframe -> clip
            is MobileSegment.Wireframe.PlaceholderWireframe -> clip
        }
    }

    private fun MobileSegment.Wireframe.x(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> x
            is MobileSegment.Wireframe.TextWireframe -> x
            is MobileSegment.Wireframe.ImageWireframe -> x
            is MobileSegment.Wireframe.PlaceholderWireframe -> x
        }
    }

    private fun MobileSegment.Wireframe.y(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> y
            is MobileSegment.Wireframe.TextWireframe -> y
            is MobileSegment.Wireframe.ImageWireframe -> y
            is MobileSegment.Wireframe.PlaceholderWireframe -> y
        }
    }

    private fun MobileSegment.Wireframe.width(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> width
            is MobileSegment.Wireframe.TextWireframe -> width
            is MobileSegment.Wireframe.ImageWireframe -> width
            is MobileSegment.Wireframe.PlaceholderWireframe -> width
        }
    }

    private fun MobileSegment.Wireframe.height(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> height
            is MobileSegment.Wireframe.TextWireframe -> height
            is MobileSegment.Wireframe.ImageWireframe -> height
            is MobileSegment.Wireframe.PlaceholderWireframe -> height
        }
    }

    private fun MobileSegment.Wireframe.shapeStyle(): MobileSegment.ShapeStyle? {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> shapeStyle
            is MobileSegment.Wireframe.TextWireframe -> shapeStyle
            is MobileSegment.Wireframe.ImageWireframe -> shapeStyle
            is MobileSegment.Wireframe.PlaceholderWireframe -> null
        }
    }

    private fun Forge.getForgeryWithIntRangeCoordinates(): MobileSegment.Wireframe {
        return when (val wireframe = aValidWireframe()) {
            is MobileSegment.Wireframe.ShapeWireframe -> {
                wireframe.copy(
                    x = aLong(min = 1, max = 100),
                    y = aLong(min = 1, max = 100),
                    width = aLong(min = 1, max = 100),
                    height = aLong(min = 1, max = 100)
                )
            }
            is MobileSegment.Wireframe.TextWireframe -> {
                wireframe.copy(
                    x = aLong(min = 1, max = 100),
                    y = aLong(min = 1, max = 100),
                    width = aLong(min = 1, max = 100),
                    height = aLong(min = 1, max = 100)
                )
            }
            is MobileSegment.Wireframe.ImageWireframe -> {
                wireframe.copy(
                    x = aLong(min = 1, max = 100),
                    y = aLong(min = 1, max = 100),
                    width = aLong(min = 1, max = 100),
                    height = aLong(min = 1, max = 100)
                )
            }
            is MobileSegment.Wireframe.PlaceholderWireframe -> {
                wireframe.copy(
                    x = aLong(min = 1, max = 100),
                    y = aLong(min = 1, max = 100),
                    width = aLong(min = 1, max = 100),
                    height = aLong(min = 1, max = 100)
                )
            }
        }
    }

    private fun Forge.forgeNonTransparentShapeStyle(): MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}"),
            opacity = 1f,
            cornerRadius = aPositiveLong()
        )
    }

    // endregion

    companion object {
        val forge = Forge()

        private fun Forge.forgeNonTransparentShapeStyle(): MobileSegment.ShapeStyle {
            return MobileSegment.ShapeStyle(
                backgroundColor = aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}"),
                opacity = 1f,
                cornerRadius = aPositiveLong()
            )
        }
        private fun aValidWireframe(): MobileSegment.Wireframe {
            return when (val wireframe = forge.getForgery<MobileSegment.Wireframe>()) {
                is MobileSegment.Wireframe.ShapeWireframe ->
                    wireframe.copy(shapeStyle = forge.getForgery(), border = forge.getForgery())
                is MobileSegment.Wireframe.TextWireframe ->
                    wireframe.copy(shapeStyle = forge.getForgery(), border = forge.getForgery())
                is MobileSegment.Wireframe.ImageWireframe ->
                    wireframe.copy(shapeStyle = forge.getForgery(), border = forge.getForgery())
                else -> wireframe
            }
        }
        private fun aValidShapeStyleWireframe(): MobileSegment.Wireframe {
            // in case of Image we make sure the base64 is null to not influence the test
            // an give false positives
            return when (forge.anInt(min = 0, max = 3)) {
                0 -> forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                    .copy(shapeStyle = forge.getForgery(), border = forge.getForgery())
                1 -> forge.getForgery<MobileSegment.Wireframe.ImageWireframe>()
                    .copy(shapeStyle = forge.getForgery(), border = forge.getForgery(), base64 = null)
                else -> forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
                    .copy(shapeStyle = forge.getForgery(), border = forge.getForgery())
            }
        }
        private fun aValidSolidBackgroundWireframe(): MobileSegment.Wireframe {
            return when (forge.anInt(min = 0, max = 4)) {
                0 -> forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                    .copy(
                        shapeStyle = forge.forgeNonTransparentShapeStyle(),
                        border = forge.getForgery()
                    )
                1 -> forge.getForgery<MobileSegment.Wireframe.ImageWireframe>()
                    .copy(
                        shapeStyle = forge.forgeNonTransparentShapeStyle(),
                        border = forge.getForgery(),
                        base64 = forge.aString()
                    )
                2 -> forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
                    .copy(
                        shapeStyle = forge.forgeNonTransparentShapeStyle(),
                        border = forge.getForgery()
                    )
                else -> forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>()
            }
        }

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
                is MobileSegment.Wireframe.ImageWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    clip = clip
                )
                is MobileSegment.Wireframe.PlaceholderWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    clip = clip
                )
            }
        }

        private fun MobileSegment.Wireframe.copy(
            x: Long,
            y: Long,
            width: Long,
            height: Long,
            shapeStyle: MobileSegment.ShapeStyle?
        ):
            MobileSegment.Wireframe {
            return when (this) {
                is MobileSegment.Wireframe.ShapeWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    shapeStyle = shapeStyle
                )
                is MobileSegment.Wireframe.TextWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    shapeStyle = shapeStyle
                )
                is MobileSegment.Wireframe.ImageWireframe -> copy(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    shapeStyle = shapeStyle
                )
                is MobileSegment.Wireframe.PlaceholderWireframe -> copy(
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
        fun wireframesWithSolidBackground(): List<MobileSegment.Wireframe> {
            ForgeConfigurator().configure(forge)
            // we need to start from 2 when generating the random width and height as we have
            // some scenarios in our tests where we generate a test wireframe from these
            // wireframe using forge.min(0, fakeWireframe.height/width - 1). If we do not
            // start from 2 the code will crash in the case of: forge.min(0, 0) with IAE
            val negativeCoordinatesWireframe = aValidSolidBackgroundWireframe()
                .copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery()
                )
            val positiveCoordinatesWireframe = aValidSolidBackgroundWireframe()
                .copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery()
                )
            val negativeCoordinatesWireframeNoClip = aValidSolidBackgroundWireframe()
                .copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null
                )
            val positiveCoordinatesWireframeNoClip = aValidSolidBackgroundWireframe()
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

        @JvmStatic
        fun wireframesWithShapeStyle(): List<MobileSegment.Wireframe> {
            ForgeConfigurator().configure(forge)
            // we need to start from 2 when generating the random width and height as we have
            // some scenarios in our tests where we generate a test wireframe from these
            // wireframe using forge.min(0, fakeWireframe.height/width - 1). If we do not
            // start from 2 the code will crash in the case of: forge.min(0, 0) with IAE
            val negativeCoordinatesWireframe = aValidShapeStyleWireframe()
                .copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery()
                )
            val positiveCoordinatesWireframe = aValidShapeStyleWireframe()
                .copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery()
                )
            val negativeCoordinatesWireframeNoClip = aValidShapeStyleWireframe()
                .copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null
                )
            val positiveCoordinatesWireframeNoClip = aValidShapeStyleWireframe()
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

        @JvmStatic
        fun imageWireframesWithoutShapeStyle(): List<MobileSegment.Wireframe.ImageWireframe> {
            ForgeConfigurator().configure(forge)
            // we need to start from 2 when generating the random width and height as we have
            // some scenarios in our tests where we generate a test wireframe from these
            // wireframe using forge.min(0, fakeWireframe.height/width - 1). If we do not
            // start from 2 the code will crash in the case of: forge.min(0, 0) with IAE
            val negativeCoordinatesWireframe =
                forge.getForgery<MobileSegment.Wireframe.ImageWireframe>().copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery(),
                    shapeStyle = null,
                    border = null,
                    base64 = forge.aString()
                )
            val positiveCoordinatesWireframe =
                forge.getForgery<MobileSegment.Wireframe.ImageWireframe>().copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery(),
                    shapeStyle = null,
                    border = null,
                    base64 = forge.aString()
                )
            val negativeCoordinatesWireframeNoClip =
                forge.getForgery<MobileSegment.Wireframe.ImageWireframe>().copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null,
                    shapeStyle = null,
                    border = null,
                    base64 = forge.aString()
                )
            val positiveCoordinatesWireframeNoClip =
                forge.getForgery<MobileSegment.Wireframe.ImageWireframe>().copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null,
                    shapeStyle = null,
                    border = null,
                    base64 = forge.aString()
                )
            return listOf(
                negativeCoordinatesWireframe,
                positiveCoordinatesWireframe,
                negativeCoordinatesWireframeNoClip,
                positiveCoordinatesWireframeNoClip
            )
        }

        @JvmStatic
        fun placeholderWireframes(): List<MobileSegment.Wireframe.PlaceholderWireframe> {
            ForgeConfigurator().configure(forge)
            // we need to start from 2 when generating the random width and height as we have
            // some scenarios in our tests where we generate a test wireframe from these
            // wireframe using forge.min(0, fakeWireframe.height/width - 1). If we do not
            // start from 2 the code will crash in the case of: forge.min(0, 0) with IAE
            val negativeCoordinatesWireframe =
                forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>().copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery()
                )
            val positiveCoordinatesWireframe =
                forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>().copy(
                    x = forge.aLong(min = 0, max = 100),
                    y = forge.aLong(min = 0, max = 100),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = forge.getForgery()
                )
            val negativeCoordinatesWireframeNoClip =
                forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>().copy(
                    x = forge.aLong(min = -99, max = 0),
                    y = forge.aLong(min = -99, max = 0),
                    width = forge.aLong(min = 2, max = 100),
                    height = forge.aLong(min = 2, max = 100),
                    clip = null
                )
            val positiveCoordinatesWireframeNoClip =
                forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>().copy(
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
