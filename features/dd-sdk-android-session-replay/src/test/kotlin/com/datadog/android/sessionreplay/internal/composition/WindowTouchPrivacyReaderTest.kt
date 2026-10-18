/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewGroup
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class WindowTouchPrivacyReaderTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard partial permissions W live privacy pass exhausts budget`(fakeOverrunInChild: Boolean) {
        // Given
        val mockRoot: ViewGroup = mock()
        val mockChild: View = mock()
        for (view in listOf(mockRoot, mockChild)) {
            whenever(view.isShown).thenReturn(true)
            whenever(view.width).thenReturn(100)
            whenever(view.height).thenReturn(100)
        }
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        var fakeNowNs = 0L
        val fakeExpensiveView = if (fakeOverrunInChild) mockChild else mockRoot
        whenever(fakeExpensiveView.getTag(R.id.datadog_touch_privacy)).thenAnswer {
            fakeNowNs = 8L
            TouchPrivacy.SHOW.name
        }
        val testedReader = WindowTouchPrivacyReader(
            timeProvider = CaptureTimeProvider { fakeNowNs },
            budgetNs = 8L,
            isMainThreadWindow = { true }
        )

        // When
        val result = testedReader.read(mockRoot)

        // Then: neither child enumeration nor the final node may overrun and publish a partial SHOW map.
        assertThat(result).isNull()
    }

    @Test
    fun `M deny privacy W custom view throws during refresh`() {
        // Given
        val mockRoot: View = mock()
        whenever(mockRoot.isShown).thenReturn(true)
        whenever(mockRoot.width).thenReturn(100)
        whenever(mockRoot.height).thenReturn(100)
        whenever(mockRoot.getTag(R.id.datadog_touch_privacy)).thenThrow(IllegalStateException("custom view"))
        val testedReader = WindowTouchPrivacyReader(isMainThreadWindow = { true })

        // When
        val result = testedReader.read(mockRoot)

        // Then
        assertThat(result).isNull()
    }
}
