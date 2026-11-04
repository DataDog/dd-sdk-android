/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.insets.ProtectionLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AndroidXProtectionViewMatcherTest {
    private val testedMatcher = AndroidXProtectionViewMatcher()

    private fun protectionChild(tag: Any? = ProtectionLayout.PROTECTION_VIEW): View {
        val stubParent: ProtectionLayout = mock()
        return mock<View>().apply {
            whenever(parent).thenReturn(stubParent)
            whenever(this.tag).thenReturn(tag)
        }
    }

    @Test
    fun `M identify empty protection child W isEmptyProtectionView`() {
        val stubView = protectionChild()
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isTrue()
        assertThat(ViewUtilsInternal().isSystemNoise(stubView)).isTrue()
    }

    @Test
    fun `M retain app child W isEmptyProtectionView { tag is different }`() {
        assertThat(testedMatcher.isEmptyProtectionView(protectionChild(Any()))).isFalse()
    }

    @Test
    fun `M retain untagged child W isEmptyProtectionView`() {
        assertThat(testedMatcher.isEmptyProtectionView(protectionChild(null))).isFalse()
    }

    @Test
    fun `M retain detached view W isEmptyProtectionView`() {
        val stubView: View = mock()
        whenever(stubView.tag).thenReturn(ProtectionLayout.PROTECTION_VIEW)
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isFalse()
    }

    @Test
    fun `M retain view under other parent W isEmptyProtectionView`() {
        val stubView = protectionChild()
        val stubParent: FrameLayout = mock()
        whenever(stubView.parent).thenReturn(stubParent)
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isFalse()
    }

    @Test
    fun `M retain custom drawing subclass W isEmptyProtectionView`() {
        val stubView: TextView = mock()
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["focused", "focusable", "clickable", "longClickable"])
    fun `M retain interactive or focus drawing W isEmptyProtectionView`(fakeState: String) {
        val stubView = protectionChild()
        when (fakeState) {
            "focused" -> whenever(stubView.isFocused).thenReturn(true)
            "focusable" -> whenever(stubView.isFocusable).thenReturn(true)
            "clickable" -> whenever(stubView.isClickable).thenReturn(true)
            "longClickable" -> whenever(stubView.isLongClickable).thenReturn(true)
        }
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain decorated view W isEmptyProtectionView`(fakeForeground: Boolean) {
        val stubView = protectionChild()
        val stubDrawable: Drawable = mock()
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isTrue()
        if (fakeForeground) {
            whenever(stubView.foreground).thenReturn(stubDrawable)
        } else {
            whenever(stubView.background).thenReturn(stubDrawable)
        }
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isFalse()
        if (fakeForeground) {
            whenever(stubView.foreground).thenReturn(null)
        } else {
            whenever(stubView.background).thenReturn(null)
        }
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain scrollbar drawing W isEmptyProtectionView`(fakeHorizontal: Boolean) {
        val stubView = protectionChild()
        if (fakeHorizontal) {
            whenever(stubView.isHorizontalScrollBarEnabled).thenReturn(true)
        } else {
            whenever(stubView.isVerticalScrollBarEnabled).thenReturn(true)
        }
        assertThat(testedMatcher.isEmptyProtectionView(stubView)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M cache supported or missing marker W isEmptyProtectionView`(fakeAvailable: Boolean) {
        var fakeReads = 0
        val testedCached = AndroidXProtectionViewMatcher {
            fakeReads++
            if (fakeAvailable) ProtectionLayout.PROTECTION_VIEW else null
        }
        val stubView = protectionChild()
        repeat(3) { assertThat(testedCached.isEmptyProtectionView(stubView)).isEqualTo(fakeAvailable) }
        assertThat(fakeReads).isEqualTo(1)
    }

    @Test
    fun `M retain unknown optional versions W marker read`() {
        assertThat(ProtectionViewMarkerReader.read(Any::class.java)).isNull()
        assertThat(ProtectionViewMarkerReader.read(WrongMarkerType::class.java)).isNull()
        assertThat(ProtectionViewMarkerReader.read(NonFinalMarker::class.java)).isNull()
        assertThat(ProtectionViewMarkerReader.read(UnsupportedMarkerValue::class.java)).isNull()
    }

    private class WrongMarkerType private constructor() {
        companion object {
            const val PROTECTION_VIEW = "unsupported"
        }
    }

    private class NonFinalMarker private constructor() {
        companion object {
            @JvmField var PROTECTION_VIEW: Any = Any()
        }
    }

    private class UnsupportedMarkerValue private constructor() {
        companion object {
            @JvmField val PROTECTION_VIEW: Any = listOf("unsupported")
        }
    }
}
