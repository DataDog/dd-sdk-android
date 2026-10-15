/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Matrix
import android.view.ViewGroup
import com.datadog.android.sessionreplay.utils.GlobalBounds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ViewTransformResolverTest {
    @Test
    fun `M preserve scale around pivot and window position W resolve`() {
        // Given: a 2x scale around (50, 25), in a window at (100, 200).
        val fakeValues = floatArrayOf(2f, 0f, -50f, 0f, 2f, -25f, 0f, 0f, 1f)
        val stubView = view(100, 50, 50, 175, fakeValues)

        // When
        val result = checkNotNull(ViewTransformResolver.resolve(stubView))

        // Then
        assertThat(result.pixelBounds).isEqualTo(GlobalBounds(50, 175, 200, 100))
        assertThat(result.matrix).containsExactly(2f, 0f, 50f, 0f, 2f, 175f, 0f, 0f, 1f)
        assertThat(result.isAxisAligned).isTrue()
    }

    @Test
    fun `M preserve rotation around pivot W resolve`() {
        // Given: a 90-degree rotation around (50, 25), in a window at (100, 200).
        val fakeValues = floatArrayOf(0f, -1f, 75f, 1f, 0f, -25f, 0f, 0f, 1f)
        val stubView = view(100, 50, 175, 175, fakeValues)

        // When
        val result = checkNotNull(ViewTransformResolver.resolve(stubView))

        // Then
        assertThat(result.pixelBounds).isEqualTo(GlobalBounds(125, 175, 50, 100))
        assertThat(result.matrix).containsExactly(0f, -1f, 175f, 1f, 0f, 175f, 0f, 0f, 1f)
    }

    @Test
    fun `M round coverage outward W resolve arbitrary rotation`() {
        // Given
        val fakeCos = 0.70710677f
        val stubView = view(100, 50, 100, 200, floatArrayOf(fakeCos, -fakeCos, 0f, fakeCos, fakeCos, 0f, 0f, 0f, 1f))

        // When
        val result = checkNotNull(ViewTransformResolver.resolve(stubView))

        // Then
        assertThat(result.pixelBounds).isEqualTo(GlobalBounds(64, 200, 107, 107))
        assertThat(result.isAxisAligned).isFalse()
    }

    @Test
    fun `M concatenate ancestor transforms and scrolling W resolve child`() {
        // Given
        val stubRoot = view(400, 600, 100, 200)
        val stubParent = view(100, 50, 0, 0, floatArrayOf(2f, 0f, -50f, 0f, 2f, -25f, 0f, 0f, 1f))
        whenever(stubParent.parent).thenReturn(stubRoot)
        whenever(stubParent.left).thenReturn(10)
        whenever(stubParent.top).thenReturn(20)
        whenever(stubParent.scrollX).thenReturn(3)
        whenever(stubParent.scrollY).thenReturn(4)
        val stubChild = view(20, 10, 0, 0, floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        whenever(stubChild.parent).thenReturn(stubParent)
        whenever(stubChild.left).thenReturn(7)
        whenever(stubChild.top).thenReturn(9)

        // When
        val result = checkNotNull(ViewTransformResolver.resolve(stubChild))

        // Then
        assertThat(result.pixelBounds).isEqualTo(GlobalBounds(48, 205, 20, 40))
        assertThat(ViewTransformResolver.hasTransformedAncestor(stubChild)).isTrue()
    }

    @Test
    fun `M preserve fractional negative coverage W resolve`() {
        // Given
        val stubView = view(100, 50, -13, -7, floatArrayOf(1.25f, 0f, 0f, 0f, 1.25f, 0f, 0f, 0f, 1f))

        // When / Then
        assertThat(ViewTransformResolver.resolve(stubView)?.pixelBounds).isEqualTo(GlobalBounds(-13, -7, 125, 63))
    }

    @Test
    fun `M reject unbounded perspective W resolve`() {
        // Given: the plane crosses the camera inside the view's rectangle.
        val stubView = view(100, 50, 0, 0, floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, -0.02f, 0f, 1f))

        // When / Then
        assertThat(ViewTransformResolver.resolve(stubView)).isNull()
    }

    private fun view(width: Int, height: Int, x: Int, y: Int, values: FloatArray? = null): ViewGroup {
        val stubView: ViewGroup = mock()
        whenever(stubView.width).thenReturn(width)
        whenever(stubView.height).thenReturn(height)
        whenever(stubView.getLocationOnScreen(any())).thenAnswer {
            it.getArgument<IntArray>(0).apply { this[0] = x; this[1] = y }
            null
        }
        if (values != null) {
            val stubMatrix: Matrix = mock()
            whenever(stubMatrix.getValues(any())).thenAnswer { values.copyInto(it.getArgument(0)); null }
            whenever(stubView.matrix).thenReturn(stubMatrix)
        }
        return stubView
    }
}
