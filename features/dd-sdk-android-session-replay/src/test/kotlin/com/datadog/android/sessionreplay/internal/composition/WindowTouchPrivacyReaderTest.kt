/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.utils.verifyLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class WindowTouchPrivacyReaderTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard partial permissions W read() { live privacy pass exhausts budget }`(fakeOverrunInChild: Boolean) {
        // Given
        val mockRoot: ViewGroup = mock()
        val mockChild: View = mock()
        for (view in listOf(mockRoot, mockChild)) {
            view.stub {
                on { isShown } doReturn true
                on { width } doReturn 100
                on { height } doReturn 100
            }
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

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M deny privacy W read() { custom view repeatedly throws during refresh }`() {
        // Given
        val mockInternalLogger: InternalLogger = mock()
        val fakeFailure = IllegalStateException("custom view")
        val mockRoot: View = mock {
            on { isShown } doReturn true
            on { width } doReturn 100
            on { height } doReturn 100
            on { getTag(R.id.datadog_touch_privacy) } doThrow fakeFailure
        }
        val testedReader = WindowTouchPrivacyReader(
            timeProvider = CaptureTimeProvider { 0L },
            internalLogger = mockInternalLogger,
            isMainThreadWindow = { true }
        )

        // When
        val firstResult = testedReader.read(mockRoot)
        val secondResult = testedReader.read(mockRoot)

        // Then
        assertThat(firstResult).isNull()
        assertThat(secondResult).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "Unable to refresh composition touch privacy",
            fakeFailure,
            onlyOnce = true,
            mode = times(2)
        )
    }

    @ParameterizedTest
    @EnumSource(TouchPrivacy::class)
    fun `M apply touch overrides to padded bounds W read() { view has padding }`(fakeTouchPrivacy: TouchPrivacy) {
        // Given
        val mockRoot: View = mock()
        whenever(mockRoot.isShown).thenReturn(true)
        whenever(mockRoot.width).thenReturn(80)
        whenever(mockRoot.height).thenReturn(60)
        whenever(mockRoot.paddingLeft).thenReturn(5)
        whenever(mockRoot.paddingTop).thenReturn(10)
        whenever(mockRoot.paddingRight).thenReturn(15)
        whenever(mockRoot.paddingBottom).thenReturn(20)
        whenever(mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(fakeTouchPrivacy.name)
        doAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = 100
            location[1] = 200
            null
        }.whenever(mockRoot).getLocationOnScreen(any())
        val fakeGlobalPrivacy = if (fakeTouchPrivacy == TouchPrivacy.HIDE) TouchPrivacy.SHOW else TouchPrivacy.HIDE
        val testedManager = TouchPrivacyManager(fakeGlobalPrivacy)
        val testedReader = WindowTouchPrivacyReader(
            timeProvider = CaptureTimeProvider { 0L },
            isMainThreadWindow = { true }
        )

        // When
        val areas = testedReader.read(mockRoot).orEmpty()

        // Then
        assertThat(areas).containsExactlyEntriesOf(mapOf(Rect(95, 190, 195, 280) to fakeTouchPrivacy))
        val paddedMarginPoints = listOf(pointAt(97, 230), pointAt(140, 195), pointAt(190, 230), pointAt(140, 270))
        paddedMarginPoints.forEach { point ->
            assertThat(testedManager.shouldRecordTouch(point, areas))
                .describedAs("touch inside padded bounds at %s", point)
                .isEqualTo(fakeTouchPrivacy == TouchPrivacy.SHOW)
        }
        val outsidePoints = listOf(pointAt(94, 230), pointAt(140, 189), pointAt(195, 230), pointAt(140, 280))
        outsidePoints.forEach { point ->
            assertThat(testedManager.shouldRecordTouch(point, areas))
                .describedAs("touch outside padded bounds at %s", point)
                .isEqualTo(fakeGlobalPrivacy == TouchPrivacy.SHOW)
        }
    }

    // Android's JVM test jar stubs Point constructors, so initialize the coordinates explicitly.
    private fun pointAt(x: Int, y: Int): Point = Point().apply {
        this.x = x
        this.y = y
    }
}
