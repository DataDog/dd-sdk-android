/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.VectorPainter
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.mock

/** Exercises the real version-specific fields while avoiding Android bitmap rendering in JVM tests. */
internal class VectorPainterStateTest {
    private val testedUtils = ReflectionUtils()
    private val painter: VectorPainter = mock()
    private val vector = Mockito.mock(requireNotNull(ComposeReflection.VectorComponent))
    private val mirror = mutableStateOf(false)
    private val intrinsic = mutableStateOf<ColorFilter?>(null)
    private val viewport = mutableStateOf(Size(24f, 24f))

    @BeforeEach
    fun prepareRendererFields() {
        assumeTrue(
            ComposeReflection.VectorStateFields != null && ComposeReflection.BlendModeColorFilterClass != null,
            "Optional renderer fields are unavailable; modern runtime runs cover the reuse path"
        )
        requireNotNull(ComposeReflection.VectorField).set(painter, vector)
        requireNotNull(ComposeReflection.VectorDirtyField).setBoolean(vector, false)
        requireNotNull(ComposeReflection.VectorAutoMirrorField).set(painter, mirror)
        requireNotNull(ComposeReflection.VectorIntrinsicFilterField).set(vector, intrinsic)
        requireNotNull(ComposeReflection.VectorCachedTintField).set(vector, null)
        requireNotNull(ComposeReflection.VectorViewportField).set(vector, viewport)
        requireNotNull(ComposeReflection.VectorPreviousDrawSizeField).setLong(vector, 123L)
    }

    @Test
    fun `M read clean renderer state W getVectorPainterState`() {
        // When
        val result = testedUtils.getVectorPainterState(painter)

        // Then
        assertThat(result).isEqualTo(VectorPainterState(false, Size(24f, 24f), 123L, null, null))
    }

    @Test
    fun `M detect dirty geometry before a redraw W getVectorPainterState`() {
        // Given
        val clean = testedUtils.getVectorPainterState(painter)
        requireNotNull(ComposeReflection.VectorDirtyField).setBoolean(vector, true)

        // When + Then
        assertThat(clean).isNotNull()
        assertThat(testedUtils.getVectorPainterState(painter)).isNull()
    }

    @Test
    fun `M read immediate snapshot paint changes W getVectorPainterState`() {
        // Given
        val clean = testedUtils.getVectorPainterState(painter)
        val tint = tint(42L, 3)

        // When - no apply-notification callback or UI redraw is required.
        mirror.value = true
        intrinsic.value = tint
        viewport.value = Size(48f, 24f)
        val result = testedUtils.getVectorPainterState(painter)

        // Then
        assertThat(result).isNotEqualTo(clean)
        assertThat(result).isEqualTo(VectorPainterState(true, Size(48f, 24f), 123L, VectorPainterTint(42L, 3), null))
    }

    @Test
    fun `M include cached alpha mask tint W getVectorPainterState`() {
        // Given
        requireNotNull(ComposeReflection.VectorCachedTintField).set(vector, tint(43L, 4))

        // When + Then
        assertThat(testedUtils.getVectorPainterState(painter)?.cachedTint).isEqualTo(VectorPainterTint(43L, 4))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["mirror", "viewport", "nonFiniteViewport", "emptyViewport", "intrinsicFilter", "cachedFilter"]
    )
    fun `M reject unreadable or unsupported renderer state W getVectorPainterState`(invalid: String) {
        // Given
        when (invalid) {
            "mirror" -> requireNotNull(ComposeReflection.VectorAutoMirrorField).set(painter, null)
            "viewport" -> requireNotNull(ComposeReflection.VectorViewportField).set(vector, null)
            "nonFiniteViewport" -> viewport.value = Size(Float.NaN, 24f)
            "emptyViewport" -> viewport.value = Size(0f, 24f)
            "intrinsicFilter" -> intrinsic.value = mock()
            "cachedFilter" -> requireNotNull(ComposeReflection.VectorCachedTintField).set(vector, mock<ColorFilter>())
        }

        // When + Then
        assertThat(testedUtils.getVectorPainterState(painter)).isNull()
    }

    @Test
    fun `M copy tint values without keeping the native filter W getVectorColorFilterKey`() {
        // Given
        val filter = tint(44L, 5)

        // When
        val key = testedUtils.getVectorColorFilterKey(filter)

        // Then
        assertThat(key).isEqualTo(VectorPainterTint(44L, 5))
        assertThat(testedUtils.getVectorColorFilterKey(mock())).isNull()
    }

    private fun tint(color: Long, mode: Int): ColorFilter {
        val filter = Mockito.mock(requireNotNull(ComposeReflection.BlendModeColorFilterClass)) as ColorFilter
        requireNotNull(ComposeReflection.TintColorField).setLong(filter, color)
        requireNotNull(ComposeReflection.TintBlendModeField).setInt(filter, mode)
        return filter
    }
}
