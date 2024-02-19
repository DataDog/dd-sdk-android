/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.unit.IntSize
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.max
import kotlin.math.min

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoxTest {

    @Test
    fun `M return Box with layout size W from() {node is LayoutInfo, isAttached=false}`(
        @LongForgery(min = 1L, max = 65536L) fakeWidth: Long,
        @LongForgery(min = 1L, max = 65536L) fakeHeight: Long
    ) {
        // Given
        val mockLayoutInfo = mock<LayoutInfo>()
        whenever(mockLayoutInfo.isAttached) doReturn false
        whenever(mockLayoutInfo.width) doReturn fakeWidth.toInt()
        whenever(mockLayoutInfo.height) doReturn fakeHeight.toInt()
        val mockGroup = mock<CompositionGroup>()
        whenever(mockGroup.node) doReturn mockLayoutInfo

        // When
        val result = Box.from(mockGroup)

        // Then
        requireNotNull(result)
        assertThat(result.x).isEqualTo(0L)
        assertThat(result.y).isEqualTo(0L)
        assertThat(result.width).isEqualTo(fakeWidth)
        assertThat(result.height).isEqualTo(fakeHeight)
    }

    @Test
    fun `M return Box with layout size W from() {node is LayoutInfo, isAttached=true}`(
        @LongForgery(min = -65536L, max = 65536L) fakeX: Long,
        @LongForgery(min = -65536L, max = 65536L) fakeY: Long,
        @LongForgery(min = 1, max = 65536L) fakeWidth: Long,
        @LongForgery(min = 1, max = 65536L) fakeHeight: Long
    ) {
        // Given
        val fakeOffset = Offset.Zero.copy(fakeX.toFloat(), fakeY.toFloat())
        val fakeSize = IntSize(fakeWidth.toInt(), fakeHeight.toInt())
        val mockCoordinates = mock<LayoutCoordinates>()
        whenever(mockCoordinates.localToWindow(Offset.Zero)) doReturn fakeOffset
        whenever(mockCoordinates.size) doReturn fakeSize
        val mockLayoutInfo = mock<LayoutInfo>()
        whenever(mockLayoutInfo.isAttached) doReturn true
        whenever(mockLayoutInfo.coordinates) doReturn mockCoordinates
        val mockGroup = mock<CompositionGroup>()
        whenever(mockGroup.node) doReturn mockLayoutInfo

        // When
        val result = Box.from(mockGroup)

        // Then
        requireNotNull(result)
        assertThat(result.x).isEqualTo(fakeX)
        assertThat(result.y).isEqualTo(fakeY)
        assertThat(result.width).isEqualTo(fakeWidth)
        assertThat(result.height).isEqualTo(fakeHeight)
    }

    @Test
    fun `M return Box with child size W from() {child has node}`(
        @LongForgery(min = -65536L, max = 65536L) fakeX: Long,
        @LongForgery(min = -65536L, max = 65536L) fakeY: Long,
        @LongForgery(min = 1, max = 65536L) fakeWidth: Long,
        @LongForgery(min = 1, max = 65536L) fakeHeight: Long
    ) {
        // Given
        val fakeOffset = Offset.Zero.copy(fakeX.toFloat(), fakeY.toFloat())
        val fakeSize = IntSize(fakeWidth.toInt(), fakeHeight.toInt())
        val mockCoordinates = mock<LayoutCoordinates>()
        whenever(mockCoordinates.localToWindow(Offset.Zero)) doReturn fakeOffset
        whenever(mockCoordinates.size) doReturn fakeSize
        val mockLayoutInfo = mock<LayoutInfo>()
        whenever(mockLayoutInfo.isAttached) doReturn true
        whenever(mockLayoutInfo.coordinates) doReturn mockCoordinates
        val mockChildGroup = mock<CompositionGroup>()
        whenever(mockChildGroup.node) doReturn mockLayoutInfo
        val mockGroup = mock<CompositionGroup>()
        whenever(mockGroup.compositionGroups) doReturn listOf(mockChildGroup)

        // When
        val result = Box.from(mockGroup)

        // Then
        requireNotNull(result)
        assertThat(result.x).isEqualTo(fakeX)
        assertThat(result.y).isEqualTo(fakeY)
        assertThat(result.width).isEqualTo(fakeWidth)
        assertThat(result.height).isEqualTo(fakeHeight)
    }

    @Test
    fun `M return Box with combined size W from() {multiple child with node same size}`(
        @LongForgery(min = -65536L, max = 0L) fakeX1: Long,
        @LongForgery(min = -65536L, max = 0L) fakeY1: Long,
        @LongForgery(min = 0L, max = 65536L) fakeX2: Long,
        @LongForgery(min = 0L, max = 65536L) fakeY2: Long,
        @LongForgery(min = 1, max = 65536L) fakeWidth: Long,
        @LongForgery(min = 1, max = 65536L) fakeHeight: Long
    ) {
        // Given
        val fakeOffset1 = Offset.Zero.copy(fakeX1.toFloat(), fakeY1.toFloat())
        val fakeSize1 = IntSize(fakeWidth.toInt(), fakeHeight.toInt())
        val mockCoordinates1 = mock<LayoutCoordinates>()
        whenever(mockCoordinates1.localToWindow(Offset.Zero)) doReturn fakeOffset1
        whenever(mockCoordinates1.size) doReturn fakeSize1
        val mockLayoutInfo1 = mock<LayoutInfo>()
        whenever(mockLayoutInfo1.isAttached) doReturn true
        whenever(mockLayoutInfo1.coordinates) doReturn mockCoordinates1
        val mockChildGroup1 = mock<CompositionGroup>()
        whenever(mockChildGroup1.node) doReturn mockLayoutInfo1
        val fakeOffset2 = Offset.Zero.copy(fakeX2.toFloat(), fakeY2.toFloat())
        val fakeSize2 = IntSize(fakeWidth.toInt(), fakeHeight.toInt())
        val mockCoordinates2 = mock<LayoutCoordinates>()
        whenever(mockCoordinates2.localToWindow(Offset.Zero)) doReturn fakeOffset2
        whenever(mockCoordinates2.size) doReturn fakeSize2
        val mockLayoutInfo2 = mock<LayoutInfo>()
        whenever(mockLayoutInfo2.isAttached) doReturn true
        whenever(mockLayoutInfo2.coordinates) doReturn mockCoordinates2
        val mockChildGroup2 = mock<CompositionGroup>()
        whenever(mockChildGroup2.node) doReturn mockLayoutInfo2
        val mockGroup = mock<CompositionGroup>()
        whenever(mockGroup.compositionGroups) doReturn listOf(mockChildGroup1, mockChildGroup2)

        // When
        val result = Box.from(mockGroup)

        // Then
        requireNotNull(result)
        assertThat(result.x).isEqualTo(min(fakeX1, fakeX2))
        assertThat(result.y).isEqualTo(min(fakeY1, fakeY2))
        assertThat(result.width).isEqualTo(fakeWidth + max(fakeX1, fakeX2) - min(fakeX1, fakeX2))
        assertThat(result.height).isEqualTo(fakeHeight + max(fakeY1, fakeY2) - min(fakeY1, fakeY2))
    }

    @Test
    fun `M return Box with combined size W from() {multiple child with node same position}`(
        @LongForgery(min = -65536L, max = 65536L) fakeX: Long,
        @LongForgery(min = -65536L, max = 65536L) fakeY: Long,
        @LongForgery(min = 1, max = 65536L) fakeWidth1: Long,
        @LongForgery(min = 1, max = 65536L) fakeHeight1: Long,
        @LongForgery(min = 1, max = 65536L) fakeWidth2: Long,
        @LongForgery(min = 1, max = 65536L) fakeHeight2: Long
    ) {
        // Given
        val fakeOffset1 = Offset.Zero.copy(fakeX.toFloat(), fakeY.toFloat())
        val fakeSize1 = IntSize(fakeWidth1.toInt(), fakeHeight1.toInt())
        val mockCoordinates1 = mock<LayoutCoordinates>()
        whenever(mockCoordinates1.localToWindow(Offset.Zero)) doReturn fakeOffset1
        whenever(mockCoordinates1.size) doReturn fakeSize1
        val mockLayoutInfo1 = mock<LayoutInfo>()
        whenever(mockLayoutInfo1.isAttached) doReturn true
        whenever(mockLayoutInfo1.coordinates) doReturn mockCoordinates1
        val mockChildGroup1 = mock<CompositionGroup>()
        whenever(mockChildGroup1.node) doReturn mockLayoutInfo1
        val fakeOffset2 = Offset.Zero.copy(fakeX.toFloat(), fakeY.toFloat())
        val fakeSize2 = IntSize(fakeWidth2.toInt(), fakeHeight2.toInt())
        val mockCoordinates2 = mock<LayoutCoordinates>()
        whenever(mockCoordinates2.localToWindow(Offset.Zero)) doReturn fakeOffset2
        whenever(mockCoordinates2.size) doReturn fakeSize2
        val mockLayoutInfo2 = mock<LayoutInfo>()
        whenever(mockLayoutInfo2.isAttached) doReturn true
        whenever(mockLayoutInfo2.coordinates) doReturn mockCoordinates2
        val mockChildGroup2 = mock<CompositionGroup>()
        whenever(mockChildGroup2.node) doReturn mockLayoutInfo2
        val mockGroup = mock<CompositionGroup>()
        whenever(mockGroup.compositionGroups) doReturn listOf(mockChildGroup1, mockChildGroup2)

        // When
        val result = Box.from(mockGroup)

        // Then
        requireNotNull(result)
        assertThat(result.x).isEqualTo(fakeX)
        assertThat(result.y).isEqualTo(fakeY)
        assertThat(result.width).isEqualTo(max(fakeWidth1, fakeWidth2))
        assertThat(result.height).isEqualTo(max(fakeHeight1, fakeHeight2))
    }

    @Test
    fun `M return scaled Box W withDensity()`(
        @LongForgery(min = -65536L, max = 65536L) fakeX: Long,
        @LongForgery(min = -65536L, max = 65536L) fakeY: Long,
        @LongForgery(min = 1, max = 65536L) fakeWidth: Long,
        @LongForgery(min = 1, max = 65536L) fakeHeight: Long,
        @FloatForgery(min = 0.01f, max = 100f) fakeDensity: Float
    ) {
        // Given
        val fakeOffset = Offset.Zero.copy(fakeX.toFloat(), fakeY.toFloat())
        val fakeSize = IntSize(fakeWidth.toInt(), fakeHeight.toInt())
        val mockCoordinates = mock<LayoutCoordinates>()
        whenever(mockCoordinates.localToWindow(Offset.Zero)) doReturn fakeOffset
        whenever(mockCoordinates.size) doReturn fakeSize
        val mockLayoutInfo = mock<LayoutInfo>()
        whenever(mockLayoutInfo.isAttached) doReturn true
        whenever(mockLayoutInfo.coordinates) doReturn mockCoordinates
        val mockChildGroup = mock<CompositionGroup>()
        whenever(mockChildGroup.node) doReturn mockLayoutInfo
        val mockGroup = mock<CompositionGroup>()
        whenever(mockGroup.compositionGroups) doReturn listOf(mockChildGroup)

        // When
        val result = Box.from(mockGroup)!!.withDensity(fakeDensity)

        // Then
        assertThat(result.x).isEqualTo((fakeX / fakeDensity).toLong())
        assertThat(result.y).isEqualTo((fakeY / fakeDensity).toLong())
        assertThat(result.width).isCloseTo((fakeWidth / fakeDensity).toLong(), offset(1L))
        assertThat(result.height).isCloseTo((fakeHeight / fakeDensity).toLong(), offset(1L))
    }
}
