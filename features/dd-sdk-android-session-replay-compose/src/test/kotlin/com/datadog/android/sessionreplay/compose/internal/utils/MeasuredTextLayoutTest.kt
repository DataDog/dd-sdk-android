/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class MeasuredTextLayoutTest {
    private val stubParagraph: Paragraph = mock()
    private val stubIntrinsics: ParagraphIntrinsics = mock()
    private val fakeBounds = Constraints(maxWidth = 100, maxHeight = 100)
    private val fakeSize = IntSize(100, 40)

    @BeforeEach
    fun setUp() {
        whenever(stubParagraph.width) doReturn 100f
        whenever(stubParagraph.height) doReturn 40f
        whenever(stubParagraph.lineCount) doReturn 2
        whenever(stubIntrinsics.maxIntrinsicWidth) doReturn 240f
    }

    @Test
    fun `M reuse fitting measured layout W canReuse() {same width}`() {
        assertThat(canReuse()).isTrue()
    }

    @Test
    fun `M reuse intrinsic lines W canReuse() {narrower measured width already fits}`() {
        whenever(stubParagraph.width) doReturn 80f
        whenever(stubIntrinsics.maxIntrinsicWidth) doReturn 79.2f
        assertThat(canReuse()).isTrue()
    }

    @Test
    fun `M fall back W canReuse() {reconstruction width can change wrapping}`() {
        whenever(stubParagraph.width) doReturn 80f
        assertThat(canReuse()).isFalse()
    }

    @Test
    fun `M fall back W canReuse() {intrinsic width does not fit after rounding}`() {
        whenever(stubParagraph.width) doReturn 80f
        whenever(stubIntrinsics.maxIntrinsicWidth) doReturn 80.2f
        assertThat(canReuse()).isFalse()
    }

    @Test
    fun `M fall back W canReuse() {stale font}`() {
        whenever(stubIntrinsics.hasStaleResolvedFonts) doReturn true
        assertThat(canReuse()).isFalse()
    }

    @Test
    fun `M fall back W canReuse() {max lines overflow}`() {
        whenever(stubParagraph.didExceedMaxLines) doReturn true
        assertThat(canReuse()).isFalse()
    }

    @Test
    fun `M fall back W canReuse() {no wrap}`() {
        assertThat(MeasuredTextLayout.canReuse(stubParagraph, stubIntrinsics, fakeBounds, fakeSize, false)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(floats = [0f, -1f, 101f, Float.NaN, Float.POSITIVE_INFINITY])
    fun `M fall back W canReuse() {invalid or clipped paragraph width}`(width: Float) {
        whenever(stubParagraph.width) doReturn width
        assertThat(canReuse()).isFalse()
    }

    @ParameterizedTest
    @ValueSource(floats = [0f, -1f, 41f, Float.NaN, Float.POSITIVE_INFINITY])
    fun `M fall back W canReuse() {invalid or clipped paragraph height}`(height: Float) {
        whenever(stubParagraph.height) doReturn height
        assertThat(canReuse()).isFalse()
    }

    @Test
    fun `M fall back W canReuse() {parent height clips}`() {
        val bounds = Constraints(maxWidth = 100, maxHeight = 39)
        assertThat(MeasuredTextLayout.canReuse(stubParagraph, stubIntrinsics, bounds, fakeSize, true)).isFalse()
    }

    @Test
    fun `M fall back W canReuse() {unbounded width}`() {
        assertThat(MeasuredTextLayout.canReuse(stubParagraph, stubIntrinsics, Constraints(), fakeSize, true)).isFalse()
    }

    @Test
    fun `M fall back W canReuse() {empty measured size or lines}`() {
        for (size in listOf(IntSize(0, 40), IntSize(100, 0))) {
            assertThat(MeasuredTextLayout.canReuse(stubParagraph, stubIntrinsics, fakeBounds, size, true)).isFalse()
        }
        whenever(stubParagraph.lineCount) doReturn 0
        assertThat(canReuse()).isFalse()
    }

    private fun canReuse() = MeasuredTextLayout.canReuse(stubParagraph, stubIntrinsics, fakeBounds, fakeSize, true)
}
