/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeTextCaptureCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ComposeTextBitmapCacheTest {
    private val testedCache = ComposeTextBitmapCache()
    private val fakeParagraph = Any()
    private val fakeState = ComposeTextCaptureCache.State(fakeParagraph, 10L, 20L)
    private val stubBitmap: Bitmap = mock()
    private val fakePaint = ComposeTextBitmapCache.Paint(mock(), 3L, "Current text")

    @Test
    fun `M reuse glyph bitmap W measured text and immutable paint are unchanged`() {
        testedCache.put(1, fakeState, fakePaint, TextStyle.Default, stubBitmap)

        assertThat(testedCache.get(1, fakeState, fakePaint)).isSameAs(stubBitmap)
    }

    @Test
    fun `M invalidate glyph bitmap W measured paragraph constraints size or owner changes`() {
        testedCache.put(1, fakeState, fakePaint, TextStyle.Default, stubBitmap)
        val fakeChangedStates = listOf(
            ComposeTextCaptureCache.State(Any(), 10L, 20L),
            ComposeTextCaptureCache.State(fakeParagraph, 11L, 20L),
            ComposeTextCaptureCache.State(fakeParagraph, 10L, 21L),
            null
        )

        fakeChangedStates.forEach { assertThat(testedCache.get(1, it, fakePaint)).isNull() }
        assertThat(testedCache.get(2, fakeState, fakePaint)).isNull()
    }

    @Test
    fun `M invalidate glyph bitmap W immutable text modifier paint or text changes`() {
        testedCache.put(1, fakeState, fakePaint, TextStyle.Default, stubBitmap)
        val fakeChangedPaints = listOf(
            fakePaint.copy(modifier = mock<Modifier>()),
            fakePaint.copy(modifier = null),
            fakePaint.copy(color = 4L),
            fakePaint.copy(sourceText = "Updated text")
        )

        fakeChangedPaints.forEach { assertThat(testedCache.get(1, fakeState, it)).isNull() }
    }

    @Test
    fun `M avoid caching mutable shader pixels W text has a brush`() {
        val fakeStyle = TextStyle(brush = Brush.linearGradient(listOf(Color.Red, Color.Blue)))
        testedCache.put(1, fakeState, fakePaint, fakeStyle, stubBitmap)

        assertThat(testedCache.get(1, fakeState, fakePaint)).isNull()
    }

    @Test
    fun `M reject recycled pixels W bitmap was released`() {
        testedCache.put(1, fakeState, fakePaint, TextStyle.Default, stubBitmap)
        whenever(stubBitmap.isRecycled).thenReturn(true)

        assertThat(testedCache.get(1, fakeState, fakePaint)).isNull()
    }

    @Test
    fun `M evict oldest glyph pixels W cache reaches entry limit`() {
        repeat(129) { testedCache.put(it, fakeState, fakePaint, TextStyle.Default, stubBitmap) }

        assertThat(testedCache.get(0, fakeState, fakePaint)).isNull()
        assertThat(testedCache.get(128, fakeState, fakePaint)).isSameAs(stubBitmap)
    }
}
