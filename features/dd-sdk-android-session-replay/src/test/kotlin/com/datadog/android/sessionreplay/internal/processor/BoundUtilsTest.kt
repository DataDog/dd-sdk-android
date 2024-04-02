/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.utils.WireframeExtTest
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Extensions(ExtendWith(ForgeExtension::class))
@ForgeConfiguration(ForgeConfigurator::class)
internal class BoundUtilsTest {

    @ParameterizedTest
    @MethodSource("testWireframes")
    fun`M return the correct bounds W resolveBounds() { with clipping }`(
        fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val clip = MobileSegment.WireframeClip(
            top = forge.aLong(min = 0, max = 100),
            bottom = forge.aLong(min = 0, max = 100),
            left = forge.aLong(min = 0, max = 100),
            right = forge.aLong(min = 0, max = 100)
        )

        val expectedLeft = fakeWireframe.x() + clip.left!!
        val expectedTop = fakeWireframe.y() + clip.top!!
        val expectedRight = fakeWireframe.x() + fakeWireframe.width() - clip.right!!
        val expectedBottom = fakeWireframe.y() + fakeWireframe.height() - clip.bottom!!
        val expectedBounds = WireframeBounds(
            left = expectedLeft,
            top = expectedTop,
            right = expectedRight,
            bottom = expectedBottom,
            height = fakeWireframe.height(),
            width = fakeWireframe.width()
        )

        // When
        val bounds = BoundsUtils.resolveBounds(fakeWireframe.copy(clip = clip))

        // Then
        assertThat(bounds).isEqualTo(expectedBounds)
    }

    @ParameterizedTest
    @MethodSource("testWireframes")
    fun`M return the correct bounds W resolveBounds() { without clipping }`(
        fakeWireframe: MobileSegment.Wireframe
    ) {
        // Given
        val expectedLeft = fakeWireframe.x()
        val expectedTop = fakeWireframe.y()
        val expectedRight = fakeWireframe.x() + fakeWireframe.width()
        val expectedBottom = fakeWireframe.y() + fakeWireframe.height()
        val expectedBounds = WireframeBounds(
            left = expectedLeft,
            top = expectedTop,
            right = expectedRight,
            bottom = expectedBottom,
            height = fakeWireframe.height(),
            width = fakeWireframe.width()
        )

        // When
        val bounds = BoundsUtils.resolveBounds(fakeWireframe.copy(clip = null))

        // Then
        assertThat(bounds).isEqualTo(expectedBounds)
    }

    @Test
    fun `M return true W isCovering(){ top is covering bottom }`(
        forge: Forge
    ) {
        // Given
        val top = WireframeBounds(
            left = forge.aLong(min = 0, max = 100),
            top = forge.aLong(min = 0, max = 100),
            right = forge.aLong(min = 0, max = 100),
            bottom = forge.aLong(min = 0, max = 100),
            height = forge.aLong(min = 0, max = 100),
            width = forge.aLong(min = 0, max = 100)
        )
        val bottom = WireframeBounds(
            left = top.left + forge.aLong(min = 0, max = 100),
            top = top.top + forge.aLong(min = 0, max = 100),
            right = top.right - forge.aLong(min = 0, max = 100),
            bottom = top.bottom - forge.aLong(min = 0, max = 100),
            height = forge.aLong(min = 0, max = 100),
            width = forge.aLong(min = 0, max = 100)
        )

        // When
        val isCovering = BoundsUtils.isCovering(top, bottom)

        // Then
        assertThat(isCovering).isTrue
    }

    @Test
    fun `M return false W isCovering() { bottom is covering top}`(
        forge: Forge
    ) {
        // Given
        val bottom = WireframeBounds(
            left = forge.aLong(min = 0, max = 100),
            top = forge.aLong(min = 0, max = 100),
            right = forge.aLong(min = 0, max = 100),
            bottom = forge.aLong(min = 0, max = 100),
            height = forge.aLong(min = 0, max = 100),
            width = forge.aLong(min = 0, max = 100)
        )
        val top = WireframeBounds(
            left = bottom.left + forge.aLong(min = 0, max = 100),
            top = bottom.top + forge.aLong(min = 0, max = 100),
            right = bottom.right - forge.aLong(min = 0, max = 100),
            bottom = bottom.bottom - forge.aLong(min = 0, max = 100),
            height = forge.aLong(min = 0, max = 100),
            width = forge.aLong(min = 0, max = 100)
        )

        // When
        val isCovering = BoundsUtils.isCovering(top, bottom)

        // Then
        assertThat(isCovering).isFalse
    }

    private fun MobileSegment.Wireframe.x(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.x
            is MobileSegment.Wireframe.TextWireframe -> this.x
            is MobileSegment.Wireframe.ImageWireframe -> this.x
            is MobileSegment.Wireframe.PlaceholderWireframe -> this.x
            is MobileSegment.Wireframe.WebviewWireframe -> this.x
        }
    }

    private fun MobileSegment.Wireframe.y(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.y
            is MobileSegment.Wireframe.TextWireframe -> this.y
            is MobileSegment.Wireframe.ImageWireframe -> this.y
            is MobileSegment.Wireframe.PlaceholderWireframe -> this.y
            is MobileSegment.Wireframe.WebviewWireframe -> this.y
        }
    }

    private fun MobileSegment.Wireframe.width(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.width
            is MobileSegment.Wireframe.TextWireframe -> this.width
            is MobileSegment.Wireframe.ImageWireframe -> this.width
            is MobileSegment.Wireframe.PlaceholderWireframe -> this.width
            is MobileSegment.Wireframe.WebviewWireframe -> this.width
        }
    }

    private fun MobileSegment.Wireframe.height(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.height
            is MobileSegment.Wireframe.TextWireframe -> this.height
            is MobileSegment.Wireframe.ImageWireframe -> this.height
            is MobileSegment.Wireframe.PlaceholderWireframe -> this.height
            is MobileSegment.Wireframe.WebviewWireframe -> this.height
        }
    }

    internal fun MobileSegment.Wireframe.copy(
        clip: MobileSegment.WireframeClip?,
        x: Long,
        y: Long,
        width: Long,
        height: Long
    ): MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe ->
                this.copy(clip = clip, x = x, y = y, width = width, height = height)
            is MobileSegment.Wireframe.TextWireframe ->
                this.copy(clip = clip, x = x, y = y, width = width, height = height)
            is MobileSegment.Wireframe.ImageWireframe ->
                this.copy(clip = clip, x = x, y = y, width = width, height = height)
            is MobileSegment.Wireframe.PlaceholderWireframe ->
                this.copy(clip = clip, x = x, y = y, width = width, height = height)
            is MobileSegment.Wireframe.WebviewWireframe ->
                this.copy(clip = clip, x = x, y = y, width = width, height = height)
        }
    }

    companion object {
        @JvmStatic
        fun testWireframes(): List<MobileSegment.Wireframe> {
            ForgeConfigurator().configure(WireframeExtTest.forge)
            return listOf(
                WireframeExtTest.forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>(),
                WireframeExtTest.forge.getForgery<MobileSegment.Wireframe.TextWireframe>(),
                WireframeExtTest.forge.getForgery<MobileSegment.Wireframe.ImageWireframe>(),
                WireframeExtTest.forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>(),
                WireframeExtTest.forge.getForgery<MobileSegment.Wireframe.WebviewWireframe>()
            )
        }
    }
}
