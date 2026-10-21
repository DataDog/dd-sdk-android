/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.utils.GlobalBounds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class TouchOverrideBoundsTest {
    @ParameterizedTest
    @CsvSource(
        "20, 20, 80, 80, true",
        "20, 0, 80, 100, true",
        "0, 20, 100, 80, true",
        "0, 0, 100, 100, false",
        "-1, 20, 80, 80, false",
        "0, 0, 20, 20, false"
    )
    fun `M prove rectangular permissions fit W isInsideRoundedTouchOutline`(
        fakeLeft: Int,
        fakeTop: Int,
        fakeRight: Int,
        fakeBottom: Int,
        fakeExpected: Boolean
    ) {
        // Given / When
        val result = isInsideRoundedTouchOutline(
            GlobalBounds(0, 0, 100, 100),
            20.0,
            Rect(fakeLeft, fakeTop, fakeRight, fakeBottom)
        )

        // Then
        assertThat(result).isEqualTo(fakeExpected)
    }

    @ParameterizedTest
    @EnumSource(TouchPrivacy::class)
    fun `M clip screen areas without mutating input W clipTouchOverrideArea`(fakePrivacy: TouchPrivacy) {
        // Given: visible bounds are in root coordinates, while the override is in screen pixels.
        val stubView = view(Rect(0, 20, 70, 80))
        val fakeArea = Rect(80, 210, 200, 300)

        // When
        val result = clipTouchOverrideArea(stubView, fakeArea, fakePrivacy)

        // Then
        assertThat(result).isEqualTo(if (fakePrivacy == TouchPrivacy.HIDE) fakeArea else Rect(100, 220, 170, 280))
        assertThat(fakeArea).isEqualTo(Rect(80, 210, 200, 300))
    }

    @Test
    fun `M apply own explicit clip W clipTouchOverrideArea`() {
        // Given: Android's query only applied ancestor clips.
        val stubView = view(Rect(0, 0, 100, 100))
        whenever(stubView.clipBounds).thenReturn(Rect(10, 15, 40, 50))

        // When
        val result = clipTouchOverrideArea(stubView, Rect(100, 200, 200, 300), TouchPrivacy.SHOW)

        // Then
        assertThat(result).isEqualTo(Rect(110, 215, 140, 250))
    }

    @ParameterizedTest
    @ValueSource(strings = ["outline", "animation"])
    fun `M reject uncertain SHOW geometry W clipTouchOverrideArea`(fakeConstraint: String) {
        // Given
        val stubView = view(Rect(0, 0, 100, 100))
        val stubParent: ViewGroup = mock()
        whenever(stubView.parent).thenReturn(stubParent)
        if (fakeConstraint == "outline") {
            whenever(stubParent.clipToOutline).thenReturn(true)
        } else {
            whenever(stubParent.animation).thenReturn(mock())
        }
        val fakeArea = Rect(100, 200, 200, 300)

        // When / Then: uncertain geometry may mask HIDE, but cannot authorize SHOW.
        assertThat(clipTouchOverrideArea(stubView, fakeArea, TouchPrivacy.SHOW)).isNull()
        assertThat(clipTouchOverrideArea(stubView, fakeArea, TouchPrivacy.HIDE)).isEqualTo(fakeArea)
    }

    @Test
    fun `M reject invisible and expired permissions W clipTouchOverrideArea`() {
        // Given
        val stubView = view(Rect(0, 0, 100, 100))
        val fakeArea = Rect(100, 200, 200, 300)

        // When / Then
        assertThat(clipTouchOverrideArea(stubView, fakeArea, TouchPrivacy.SHOW) { false }).isNull()
        whenever(stubView.getGlobalVisibleRect(any())).thenReturn(false)
        assertThat(clipTouchOverrideArea(stubView, fakeArea, TouchPrivacy.SHOW)).isNull()
    }

    @Test
    fun `M use identical visible geometry W collectTouchOverrideArea and live refresh`() {
        // Given
        val stubView = view(Rect(0, 20, 70, 80))
        whenever(stubView.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        val fakeCaptured = mutableMapOf<Rect, TouchPrivacy>()
        val testedReader = WindowTouchPrivacyReader(
            timeProvider = CaptureTimeProvider { 0L },
            isMainThreadWindow = { true }
        )

        // When
        collectTouchOverrideArea(stubView, fakeCaptured, InternalLogger.UNBOUND)
        val fakeRefreshed = testedReader.read(stubView)

        // Then: redraw refresh cannot restore the original oversized permission.
        assertThat(fakeCaptured).containsExactlyEntriesOf(mapOf(Rect(100, 220, 170, 280) to TouchPrivacy.SHOW))
        assertThat(fakeRefreshed).isEqualTo(fakeCaptured)
    }

    private fun view(visible: Rect): View {
        val stubView: View = mock()
        whenever(stubView.isShown).thenReturn(true)
        whenever(stubView.width).thenReturn(100)
        whenever(stubView.height).thenReturn(100)
        whenever(stubView.rootView).thenReturn(stubView)
        whenever(stubView.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = 100
            location[1] = 200
            null
        }
        whenever(stubView.getGlobalVisibleRect(any())).thenAnswer {
            it.getArgument<Rect>(0).set(visible)
            true
        }
        return stubView
    }
}
