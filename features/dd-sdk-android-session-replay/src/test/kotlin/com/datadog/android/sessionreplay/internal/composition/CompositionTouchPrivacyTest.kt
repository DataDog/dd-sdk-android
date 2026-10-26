/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.recorder.callback.MotionEventUtils
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionTouchPrivacyTest {

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M publish privacy atomically W capture() { startup or new window }`(fakeNewWindow: Boolean) {
        // Given
        val fakeFixture = StartupPrivacyFixture(fakeNewWindow)
        var fakeReadyDuringCopy: Boolean? = null
        fakeFixture.fakeScreen.onMap = { view ->
            if (view === fakeFixture.mockRoot) {
                fakeReadyDuringCopy = fakeFixture.fakeScreen.windowSource.hasTouchPrivacy(view, fakeFixture.fakeScope)
            }
        }

        // When
        fakeFixture.fakeScreen.capture()

        // Then
        assertThat(fakeReadyDuringCopy).isFalse()
        assertThat(
            fakeFixture.fakeScreen.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeFixture.fakeScope)
        ).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M suppress initial gesture W dispatchTouchEvent() { privacy unpublished at gesture start }`(
        fakeNewWindow: Boolean
    ) {
        // Given
        val fakeFixture = StartupPrivacyFixture(fakeNewWindow)
        fakeFixture.fakeScreen.onMap = { view ->
            if (view === fakeFixture.mockRoot) fakeFixture.dispatchMove()
        }
        fakeFixture.requestCapture()
        mockPointCoordinates().use {
            // When
            fakeFixture.testedCallback.dispatchTouchEvent(fakeFixture.mockDownEvent)
            fakeFixture.finishCopy()
            fakeFixture.testedCallback.dispatchTouchEvent(fakeFixture.mockUpEvent)

            // Then
            verifyNoInteractions(fakeFixture.mockWriter)
            verify(fakeFixture.mockDelegate).dispatchTouchEvent(fakeFixture.mockDownEvent)
            verify(fakeFixture.mockDelegate).dispatchTouchEvent(fakeFixture.mockUpEvent)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M suppress hidden gesture W dispatchTouchEvent() { privacy published after startup }`(
        fakeNewWindow: Boolean
    ) {
        // Given
        val fakeFixture = StartupPrivacyFixture(fakeNewWindow)
        mockPointCoordinates().use {
            fakeFixture.finishInitialGesture()

            // When
            fakeFixture.testedCallback.dispatchTouchEvent(fakeFixture.mockDownAtHiddenLocation())
            fakeFixture.testedCallback.dispatchTouchEvent(fakeFixture.mockUpAtHiddenLocation())

            // Then
            verifyNoInteractions(fakeFixture.mockWriter)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M record visible gesture W dispatchTouchEvent() { startup privacy published }`(
        fakeNewWindow: Boolean
    ) {
        // Given
        val fakeFixture = StartupPrivacyFixture(fakeNewWindow)
        mockPointCoordinates().use {
            fakeFixture.finishInitialGesture()
            fakeFixture.testedCallback.dispatchTouchEvent(fakeFixture.mockDownAtHiddenLocation())
            fakeFixture.testedCallback.dispatchTouchEvent(fakeFixture.mockUpAtHiddenLocation())

            // When
            fakeFixture.testedCallback.dispatchTouchEvent(
                fakeFixture.fakeScreen.mockEvent(MotionEvent.ACTION_DOWN, 150f)
            )
            fakeFixture.testedCallback.dispatchTouchEvent(fakeFixture.fakeScreen.mockEvent(MotionEvent.ACTION_UP, 150f))

            // Then
            verify(fakeFixture.mockWriter).write(isA(), isA())
        }
    }

    @Test
    fun `M invalidate privacy W update() { root removed and re-added }`() {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)

        // When
        fakeFixture.windowSource.update(emptyList())
        fakeFixture.windowSource.update(listOf(fakeFixture.mockRoot))

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
    }

    @Test
    fun `M republish root privacy W capture() { root removed and re-added }`() {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        fakeFixture.windowSource.update(emptyList())
        fakeFixture.windowSource.update(listOf(fakeFixture.mockRoot))

        // When
        fakeFixture.capture()

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
    }

    @Test
    fun `M reject previous RUM privacy W hasTouchPrivacy() { RUM view changed }`() {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        val fakeNextScope = RumViewIdentityScope(fakeRumContext.viewId + "-next-view")
        fakeFixture.rumViewScope = CapturedRumViewScope(fakeNextScope, 0L)

        // When
        val result = fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeNextScope)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M publish new RUM privacy W capture() { RUM view changed }`() {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        val fakeNextScope = RumViewIdentityScope(fakeRumContext.viewId + "-next-view")
        fakeFixture.rumViewScope = CapturedRumViewScope(fakeNextScope, 0L)

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeNextScope)).isTrue()
    }

    @Test
    fun `M keep touches disabled W capture() { traversal filters the root }`() {
        // Given
        val fakeFixture = Fixture(TouchPrivacy.SHOW)
        whenever(fakeFixture.mockRoot.isShown).thenReturn(false)

        // When
        fakeFixture.capture()

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
    }

    @ParameterizedTest
    @EnumSource(TouchPrivacy::class)
    fun `M suppress touches W onDraw() { redraw makes the committed privacy map stale }`(
        fakeGlobalPrivacy: TouchPrivacy
    ) {
        // Given
        val fakeFixture = recordedScreen(fakeGlobalPrivacy)
        val mockWriter: RecordWriter = mock()
        val mockDelegate: Window.Callback = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mockDelegate)
        val mockDownEvent = fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f)
        val mockUpEvent = fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f)
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(
            if (fakeGlobalPrivacy == TouchPrivacy.SHOW) TouchPrivacy.HIDE.name else null
        )

        // When
        fakeFixture.onDraw()
        testedCallback.dispatchTouchEvent(mockDownEvent)
        testedCallback.dispatchTouchEvent(mockUpEvent)

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
        verifyNoInteractions(mockWriter)
        verify(mockDelegate).dispatchTouchEvent(mockDownEvent)
        verify(mockDelegate).dispatchTouchEvent(mockUpEvent)
    }

    @ParameterizedTest
    @EnumSource(TouchPrivacy::class)
    fun `M suppress hidden gesture W dispatchTouchEvent() { replacement copy finished after redraw }`(
        fakeGlobalPrivacy: TouchPrivacy
    ) {
        // Given
        val fakeFixture = recordedScreen(fakeGlobalPrivacy)
        val mockWriter: RecordWriter = mock()
        val mockDelegate: Window.Callback = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mockDelegate)
        val mockDownEvent = fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f)
        val mockUpEvent = fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f)
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(
            if (fakeGlobalPrivacy == TouchPrivacy.SHOW) TouchPrivacy.HIDE.name else null
        )

        fakeFixture.onDraw()
        testedCallback.dispatchTouchEvent(mockDownEvent)
        testedCallback.dispatchTouchEvent(mockUpEvent)

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))

        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()

        // When
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
        verifyNoInteractions(mockWriter)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M keep stale touch privacy disabled W dispatchTouchEvent() { replacement expires or is denied }`(
        fakeDenied: Boolean
    ) {
        // Given
        val fakeFixture = recordedScreen()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.onDraw()
        fakeFixture.captureAllowed = !fakeDenied
        fakeFixture.onMap = { fakeFixture.nowNs = GENERATION_BUDGET_NS }

        // When
        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
        verifyNoInteractions(mockWriter)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M publish fresh privacy W capture() { previous replacement expired or was denied }`(
        fakeDenied: Boolean
    ) {
        // Given
        val fakeFixture = recordedScreen()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.onDraw()
        fakeFixture.captureAllowed = !fakeDenied
        fakeFixture.onMap = { fakeFixture.nowNs = GENERATION_BUDGET_NS }

        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))

        fakeFixture.captureAllowed = true
        fakeFixture.onMap = {}

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(2)
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain visual capture without stale privacy W capture() { redraw overtakes native copy }`(
        fakeFinalView: Boolean
    ) {
        // Given
        val fakeFixture = recordedScreen()
        val fakePreviousAreas = fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()
        fakeFixture.onMap = { view ->
            if (if (fakeFinalView) view !== fakeFixture.mockRoot else view === fakeFixture.mockRoot) {
                whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
                fakeFixture.onDraw()
            }
        }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(2)
        assertThat(
            fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()
        ).containsExactlyEntriesOf(fakePreviousAreas)
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M replace stale privacy W runNext() { follow-up copy after redraw }`(
        fakeFinalView: Boolean
    ) {
        // Given
        val fakeFixture = recordedScreen()
        fakeFixture.onMap = { view ->
            if (if (fakeFinalView) view !== fakeFixture.mockRoot else view === fakeFixture.mockRoot) {
                whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
                fakeFixture.onDraw()
            }
        }

        fakeFixture.capture()

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        fakeFixture.onMap = {}

        // When
        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()

        // Then
        assertThat(fakeFixture.consumed).hasSize(3)
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
    }

    @Test
    fun `M refresh touch privacy W onDraw() { window redraws before worker construction finishes }`() {
        // Given
        val fakeFixture = Fixture(deferProcessing = true)
        fakeFixture.onMap = {}
        fakeFixture.capture()
        fakeFixture.processNext()
        fakeFixture.capture()
        val fakeScope = RumViewIdentityScope(fakeRumContext.viewId)

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)

        // When
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.onDraw()
        fakeFixture.processNext()

        // Then
        assertThat(fakeFixture.consumed).hasSize(2)
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)).isTrue()
        assertThat(fakeFixture.windowSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeScope)).isEmpty()
    }

    @Test
    fun `M retain refreshed privacy W processNext() { replacement finishes construction }`() {
        // Given
        val fakeFixture = Fixture(deferProcessing = true)
        fakeFixture.onMap = {}
        fakeFixture.capture()
        fakeFixture.processNext()
        fakeFixture.capture()
        val fakeScope = RumViewIdentityScope(fakeRumContext.viewId)

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)

        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.onDraw()
        fakeFixture.processNext()

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)
        fakeFixture.windowSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeScope)
        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()

        // When
        fakeFixture.processNext()

        // Then
        assertThat(fakeFixture.consumed).hasSize(3)
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard a gesture across privacy revisions W onDraw() { redraw and recapture }`(
        fakeEventWhileDirty: Boolean
    ) {
        // Given
        val fakeFixture = recordedScreen()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))

        // When
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.onDraw()
        if (fakeEventWhileDirty) testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_MOVE, 10f))
        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        // Then
        verifyNoInteractions(mockWriter)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M record fresh gesture W dispatchTouchEvent() { previous privacy revision discarded }`(
        fakeEventWhileDirty: Boolean
    ) {
        // Given
        val fakeFixture = recordedScreen()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))

        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.onDraw()
        if (fakeEventWhileDirty) testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_MOVE, 10f))
        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fakeFixture.onDraw()

        // When
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 20f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 20f))
        val records = argumentCaptor<EnrichedRecord>()

        // Then
        verify(mockWriter).write(records.capture(), isA())
        assertThat(records.firstValue.records).hasSize(2)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain unchanged window policies W onDraw() { either tracked window redraws }`(fakeSecondWindow: Boolean) {
        // Given
        val fakeFixture = Fixture()
        val mockOtherWindowRoot = fakeFixture.mockWindowRoot(200)
        fakeFixture.windowSource.update(listOf(fakeFixture.mockRoot, mockOtherWindowRoot))
        fakeFixture.onMap = {}
        fakeFixture.capture()
        val fakeWindowSnapshot = fakeFixture.windowSource.snapshot()
        val fakeScope = RumViewIdentityScope(fakeRumContext.viewId)
        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)
        fakeFixture.windowSource.hasTouchPrivacy(mockOtherWindowRoot, fakeScope)

        // When
        fakeFixture.onDraw(if (fakeSecondWindow) mockOtherWindowRoot else fakeFixture.mockRoot)

        // Then
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)).isTrue()
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(mockOtherWindowRoot, fakeScope)).isTrue()
        assertThat(fakeFixture.windowSource.isCurrent(fakeWindowSnapshot)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain window policies W runNext() { unchanged windows recaptured }`(fakeSecondWindow: Boolean) {
        // Given
        val fakeFixture = Fixture()
        val mockOtherWindowRoot = fakeFixture.mockWindowRoot(200)
        fakeFixture.windowSource.update(listOf(fakeFixture.mockRoot, mockOtherWindowRoot))
        fakeFixture.onMap = {}
        fakeFixture.capture()
        val fakeWindowSnapshot = fakeFixture.windowSource.snapshot()
        val fakeScope = RumViewIdentityScope(fakeRumContext.viewId)
        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)
        fakeFixture.windowSource.hasTouchPrivacy(mockOtherWindowRoot, fakeScope)

        fakeFixture.onDraw(if (fakeSecondWindow) mockOtherWindowRoot else fakeFixture.mockRoot)

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)
        fakeFixture.windowSource.hasTouchPrivacy(mockOtherWindowRoot, fakeScope)
        fakeFixture.windowSource.isCurrent(fakeWindowSnapshot)

        // When
        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()

        // Then
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)).isTrue()
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(mockOtherWindowRoot, fakeScope)).isTrue()
    }

    @Test
    fun `M retain gestures W onDraw() { animation redraws and equivalent captures refresh privacy }`() {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))

        // When
        repeat(10) {
            fakeFixture.onDraw()
            fakeFixture.captureScheduler.runNext()
            fakeFixture.mainThreadExecutor.runNext()
            fakeFixture.windowSource.currentTouchPrivacyScope(
                fakeFixture.mockRoot,
                RumViewIdentityScope(fakeRumContext.viewId)
            )
        }
        fakeFixture.onDraw()
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        // Then
        val records = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(records.capture(), isA())
        assertThat(records.firstValue.records).hasSize(2)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    fun `M retain privacy scope W onDraw() { repeated equivalent captures }`(fakeFrameCount: Int) {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        val fakeScope = fakeFixture.windowSource.currentTouchPrivacyScope(
            fakeFixture.mockRoot,
            RumViewIdentityScope(fakeRumContext.viewId)
        )

        // When
        repeat(fakeFrameCount) {
            fakeFixture.onDraw()
            fakeFixture.captureScheduler.runNext()
            fakeFixture.mainThreadExecutor.runNext()
        }
        val result = fakeFixture.windowSource.currentTouchPrivacyScope(
            fakeFixture.mockRoot,
            RumViewIdentityScope(fakeRumContext.viewId)
        )

        // Then
        assertThat(result).isEqualTo(fakeScope)
    }

    @Test
    fun `M establish current privacy W capture() { redraw overtakes the initial native copy }`() {
        // Given
        val fakeFixture = Fixture(TouchPrivacy.SHOW)
        fakeFixture.onMap = { view ->
            if (view === fakeFixture.mockRoot) {
                whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
            }
            fakeFixture.onDraw()
        }

        // When
        fakeFixture.capture()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        val fakeScope = RumViewIdentityScope(fakeRumContext.viewId)
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, fakeScope)).isTrue()
        assertThat(fakeFixture.windowSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeScope))
            .containsValue(TouchPrivacy.HIDE).doesNotContainValue(TouchPrivacy.SHOW)
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M deny dirty privacy W dispatchTouchEvent() { live refresh fails before visual recapture }`() {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        fakeFixture.privacyReadAllowed = false
        fakeFixture.onDraw()

        // When
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M record gesture W dispatchTouchEvent() { live privacy refresh recovers }`() {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(fakeFixture.mockRoot, mockWriter, mock())
        fakeFixture.privacyReadAllowed = false
        fakeFixture.onDraw()

        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))

        fakeFixture.privacyReadAllowed = true

        // When
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

        // Then
        verify(mockWriter).write(isA(), isA())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reject privacy refresh W currentTouchOverrideAreas() { draw or cancellation overtakes live read }`(
        fakeCancel: Boolean
    ) {
        // Given
        val fakeFixture = recordedScreen(TouchPrivacy.SHOW)
        fakeFixture.onDraw()
        fakeFixture.onPrivacyRead = {
            if (fakeCancel) fakeFixture.windowSource.clearTouchPrivacy() else fakeFixture.onDraw()
        }

        // When
        val result = fakeFixture.windowSource.currentTouchOverrideAreas(
            fakeFixture.mockRoot,
            RumViewIdentityScope(fakeRumContext.viewId)
        )

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @EnumSource(Cancellation::class)
    fun `M discard partial touch overrides W capture() { copying is cancelled }`(cancellation: Cancellation) {
        // Given
        val fakeFixture = Fixture()
        val mockFirstWindow = fakeFixture.mockWindowRoot(200)
        whenever(mockFirstWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fakeFixture.windowSource.update(listOf(mockFirstWindow, fakeFixture.mockRoot))
        fakeFixture.cancelOnRootCopy(cancellation)

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).isEmpty()
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(mockFirstWindow, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
    }

    @ParameterizedTest
    @EnumSource(Cancellation::class)
    fun `M publish only fresh overrides W capture() { previous copy cancelled }`(cancellation: Cancellation) {
        // Given
        val fakeFixture = Fixture()
        val mockFirstWindow = fakeFixture.mockWindowRoot(200)
        whenever(mockFirstWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fakeFixture.windowSource.update(listOf(mockFirstWindow, fakeFixture.mockRoot))
        fakeFixture.cancelOnRootCopy(cancellation)

        fakeFixture.capture()

        fakeFixture.windowSource.hasTouchPrivacy(mockFirstWindow, RumViewIdentityScope(fakeRumContext.viewId))
        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.windowSource.update(listOf(fakeFixture.mockRoot))
        fakeFixture.onMap = {}

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fakeFixture.touchPrivacyManager.getNextOverrideAreas()).isEmpty()
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve committed overrides W capture() { copying aborts or throws }`(throws: Boolean) {
        // Given
        val fakeFixture = Fixture()
        val fakePreviousArea = Rect(200, 200, 300, 300)
        fakeFixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(fakePreviousArea to TouchPrivacy.SHOW))
        fakeFixture.onMap = { view ->
            if (view !== fakeFixture.mockRoot) {
                if (throws) error("Traversal failed") else fakeFixture.nowNs = GENERATION_BUDGET_NS
            }
        }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).isEmpty()
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas())
            .containsExactlyEntriesOf(mapOf(fakePreviousArea to TouchPrivacy.SHOW))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M replace previous overrides W capture() { earlier copy aborted or threw }`(throws: Boolean) {
        // Given
        val fakeFixture = Fixture()
        val fakePreviousArea = Rect(200, 200, 300, 300)
        fakeFixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(fakePreviousArea to TouchPrivacy.SHOW))
        fakeFixture.onMap = { view ->
            if (view !== fakeFixture.mockRoot) {
                if (throws) error("Traversal failed") else fakeFixture.nowNs = GENERATION_BUDGET_NS
            }
        }

        fakeFixture.capture()

        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fakeFixture.onMap = {}

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard stale overrides W capture() { RUM scope changes while copying }`(fakeMissingScope: Boolean) {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.onMap = { view ->
            if (view !== fakeFixture.mockRoot) {
                fakeFixture.rumViewScope =
                    if (fakeMissingScope) {
                        null
                    } else {
                        CapturedRumViewScope(
                            RumViewIdentityScope(
                                fakeRumContext.viewId + "-new"
                            ),
                            0L
                        )
                    }
            }
        }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).isEmpty()
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M stop before next window W capture() { RUM scope changes while copying }`(fakeMissingScope: Boolean) {
        // Given
        val fakeFixture = Fixture()
        val mockFirstWindowRoot = fakeFixture.mockWindowRoot(200)
        fakeFixture.windowSource.update(listOf(mockFirstWindowRoot, fakeFixture.mockRoot))
        val fakePreviousAreas = mapOf(Rect(400, 0, 500, 100) to TouchPrivacy.SHOW)
        fakeFixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(fakePreviousAreas)
        var fakeCopiedLaterViews = 0
        fakeFixture.onMap = { view ->
            if (view === mockFirstWindowRoot) {
                fakeFixture.rumViewScope =
                    if (fakeMissingScope) {
                        null
                    } else {
                        CapturedRumViewScope(
                            RumViewIdentityScope(
                                fakeRumContext.viewId + "-new"
                            ),
                            0L
                        )
                    }
            } else {
                fakeCopiedLaterViews++
            }
        }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeCopiedLaterViews).isZero()
        assertThat(fakeFixture.consumed).isEmpty()
        assertThat(
            fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()
        ).containsExactlyEntriesOf(fakePreviousAreas)
    }

    @ParameterizedTest
    @EnumSource(WindowChange::class)
    fun `M discard stale capture W capture() { window roots change before next window }`(fakeChange: WindowChange) {
        // Given
        val fakeFixture = Fixture()
        val mockFirstWindowRoot = fakeFixture.mockWindowRoot(200)
        fakeFixture.windowSource.update(listOf(mockFirstWindowRoot, fakeFixture.mockRoot))
        val fakePreviousAreas = mapOf(Rect(400, 0, 500, 100) to TouchPrivacy.SHOW)
        fakeFixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(fakePreviousAreas)
        var fakeCopiedLaterViews = 0
        fakeFixture.onMap = { view ->
            if (view ===
                mockFirstWindowRoot
            ) {
                changeWindows(fakeFixture, mockFirstWindowRoot, fakeChange)
            } else {
                fakeCopiedLaterViews++
            }
        }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeCopiedLaterViews).isZero()
        assertThat(fakeFixture.consumed).isEmpty()
        assertThat(
            fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()
        ).containsExactlyEntriesOf(fakePreviousAreas)
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(mockFirstWindowRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
    }

    @ParameterizedTest
    @EnumSource(WindowChange::class)
    fun `M capture current windows W capture() { previous copy abandoned }`(fakeChange: WindowChange) {
        // Given
        val fakeFixture = Fixture()
        val mockFirstWindowRoot = fakeFixture.mockWindowRoot(200)
        fakeFixture.windowSource.update(listOf(mockFirstWindowRoot, fakeFixture.mockRoot))
        val fakePreviousAreas = mapOf(Rect(400, 0, 500, 100) to TouchPrivacy.SHOW)
        fakeFixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(fakePreviousAreas)
        var fakeCopiedLaterViews = 0
        fakeFixture.onMap = { view ->
            if (view ===
                mockFirstWindowRoot
            ) {
                changeWindows(fakeFixture, mockFirstWindowRoot, fakeChange)
            } else {
                fakeCopiedLaterViews++
            }
        }

        fakeFixture.capture()

        fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        fakeFixture.windowSource.hasTouchPrivacy(mockFirstWindowRoot, RumViewIdentityScope(fakeRumContext.viewId))

        fakeFixture.onMap = {}

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        val root = checkNotNull(fakeFixture.consumed.single().snapshot.root) { "Expected a captured screen root" }
        assertThat(root.children)
            .hasSize(fakeFixture.windowSource.currentWindows().size)
    }

    @ParameterizedTest
    @EnumSource(WindowChange::class)
    fun `M discard stale overrides W capture() { window roots change in final copied view }`(fakeChange: WindowChange) {
        // Given
        val fakeFixture = Fixture()
        val mockFirstWindowRoot = fakeFixture.mockWindowRoot(200)
        fakeFixture.windowSource.update(listOf(mockFirstWindowRoot, fakeFixture.mockRoot))
        val fakePreviousAreas = mapOf(Rect(400, 0, 500, 100) to TouchPrivacy.SHOW)
        fakeFixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(fakePreviousAreas)
        fakeFixture.onMap = { view ->
            if (view !== mockFirstWindowRoot &&
                view !== fakeFixture.mockRoot
            ) {
                changeWindows(fakeFixture, mockFirstWindowRoot, fakeChange)
            }
        }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).isEmpty()
        assertThat(
            fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()
        ).containsExactlyEntriesOf(fakePreviousAreas)
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(mockFirstWindowRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
    }

    @Test
    fun `M complete capture W capture() { window refresh keeps the same ordered roots }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.onMap = { fakeFixture.windowSource.update(listOf(fakeFixture.mockRoot)) }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
    }

    private fun changeWindows(fakeFixture: Fixture, firstWindow: View, change: WindowChange) {
        val source = fakeFixture.windowSource
        when (change) {
            WindowChange.ADD -> source.update(
                listOf(firstWindow, fakeFixture.mockRoot, fakeFixture.mockWindowRoot(300))
            )
            WindowChange.REMOVE_COMPLETED -> source.update(listOf(fakeFixture.mockRoot))
            WindowChange.REMOVE_CURRENT -> source.update(listOf(firstWindow))
            WindowChange.REORDER -> source.update(listOf(fakeFixture.mockRoot, firstWindow))
            WindowChange.REMOVE_AND_READD -> {
                source.update(listOf(firstWindow))
                source.update(listOf(firstWindow, fakeFixture.mockRoot))
            }
            WindowChange.CLEAR_AND_READD -> {
                source.clear()
                source.update(listOf(firstWindow, fakeFixture.mockRoot))
            }
        }
    }

    @Test
    fun `M complete capture W capture() { time offset changes within same RUM view }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.onMap = { fakeFixture.rumViewScope = fakeFixture.rumViewScope?.copy(viewTimeOffsetMs = 100L) }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
    }

    @Test
    fun `M publish overrides from every window W capture exceeds former slice budget`() {
        // Given
        val fakeFixture = Fixture()
        val mockFirstWindow = fakeFixture.mockWindowRoot(200)
        whenever(mockFirstWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fakeFixture.windowSource.update(listOf(mockFirstWindow, fakeFixture.mockRoot))
        fakeFixture.onMap = { fakeFixture.nowNs += SLICE_BUDGET_NS }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(fakeFixture.mainThreadExecutor.size).isEqualTo(1)
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point(210, 10))).isTrue()
        assertThat(fakeFixture.touchPrivacyManager.getNextOverrideAreas()).isEmpty()
    }

    @Test
    fun `M clear committed overrides W capture() { no windows remain }`() {
        // Given
        val fakeFixture = recordedScreen()

        // When
        fakeFixture.windowSource.update(emptyList())
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @Test
    fun `M clear previous screen overrides W capture has no RUM scope`() {
        // Given
        val fakeFixture = recordedScreen()

        // When
        fakeFixture.rumViewScope = null
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).hasSize(1)
        assertThat(fakeFixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point())).isFalse()
    }

    @ParameterizedTest
    @CsvSource(
        "179, 50, false",
        "340, 50, false",
        "250, -31, false",
        "250, 150, false",
        "199, 50, true",
        "300, 50, true",
        "250, -1, true",
        "250, 100, true",
        "180, -30, true",
        "339, 149, true",
        "200, 0, true",
        "299, 99, true"
    )
    fun `M apply SHOW override to padded bounds W capture() { tagged view has padding }`(
        fakeX: Int,
        fakeY: Int,
        fakeExpectedRecord: Boolean
    ) {
        // Given: the view's [200, 0, 300, 100] bounds expand to [180, -30, 340, 150] for touch privacy.
        val fakeFixture = Fixture()
        val mockWindowRoot = fakeFixture.mockWindowRoot(200)
        mockWindowRoot.stub {
            on { getTag(R.id.datadog_touch_privacy) } doReturn TouchPrivacy.SHOW.name
            on { paddingLeft } doReturn 20
            on { paddingTop } doReturn 30
            on { paddingRight } doReturn 40
            on { paddingBottom } doReturn 50
        }
        fakeFixture.windowSource.update(listOf(mockWindowRoot))
        fakeFixture.onMap = {}
        val fakePoint = Point().apply { x = fakeX; y = fakeY }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(fakePoint)).isEqualTo(fakeExpectedRecord)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M isolate window overrides W dispatchTouchEvent { overlapping windows }`(fakeGlobalShow: Boolean) {
        // Given
        val fakeFixture = Fixture(if (fakeGlobalShow) TouchPrivacy.SHOW else TouchPrivacy.HIDE)
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy))
            .thenReturn(if (fakeGlobalShow) TouchPrivacy.HIDE.name else TouchPrivacy.SHOW.name)
        val mockOverlay = fakeFixture.mockWindowRoot(0)
        fakeFixture.windowSource.update(listOf(fakeFixture.mockRoot, mockOverlay))
        fakeFixture.onMap = {}
        fakeFixture.capture()
        val fakeOverlayReady = fakeFixture.windowSource.hasTouchPrivacy(
            mockOverlay,
            RumViewIdentityScope(fakeRumContext.viewId)
        )
        val mockWriter: RecordWriter = mock()
        val testedCallback = fakeFixture.callback(mockOverlay, mockWriter, mock())
        mockPointCoordinates().use {
            // When
            testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_DOWN, 10f))
            testedCallback.dispatchTouchEvent(fakeFixture.mockEvent(MotionEvent.ACTION_UP, 10f))

            // Then
            assertThat(fakeOverlayReady).isTrue()
            if (fakeGlobalShow) {
                verify(mockWriter).write(isA(), isA())
            } else {
                verifyNoInteractions(mockWriter)
            }
        }
    }

    @Test
    fun `M preserve HIDE W capture { nested overrides have identical rectangles }`() {
        // Given
        val fakeFixture = Fixture(TouchPrivacy.SHOW)
        val mockChild = fakeFixture.mockWindowRoot(0)
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(mockChild.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        whenever(fakeFixture.mockRoot.getChildAt(0)).thenReturn(mockChild)
        fakeFixture.onMap = {}

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point().apply { x = 10; y = 10 })).isFalse()
    }

    @Test
    fun `M preserve nested HIDE W onDraw() { live privacy refresh }`() {
        // Given
        val fakeFixture = Fixture(TouchPrivacy.SHOW)
        val mockChild = fakeFixture.mockWindowRoot(0)
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(mockChild.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        whenever(fakeFixture.mockRoot.getChildAt(0)).thenReturn(mockChild)
        fakeFixture.onMap = {}

        fakeFixture.capture()

        // When
        fakeFixture.onDraw()
        val fakeAreas = fakeFixture.windowSource.currentTouchOverrideAreas(
            fakeFixture.mockRoot,
            RumViewIdentityScope(fakeRumContext.viewId)
        )

        // Then
        assertThat(fakeAreas).containsValue(TouchPrivacy.HIDE).doesNotContainValue(TouchPrivacy.SHOW)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M collect hidden descendant privacy W capture()`(fakeShowChild: Boolean) {
        // Given
        val fakeFixture = hiddenSubtree(showDescendant = fakeShowChild)

        // When
        fakeFixture.capture()

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point().apply { x = 10; y = 10 }))
            .isEqualTo(fakeShowChild)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M replace hidden subtree with placeholder W capture()`(fakeShowChild: Boolean) {
        // Given
        val fakeFixture = hiddenSubtree(showDescendant = fakeShowChild)

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed.single().snapshot.layers).hasSize(1)
        assertThat(fakeFixture.consumed.single().snapshot.wireframes.single())
            .isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M refresh hidden descendant privacy W onDraw()`(fakeShowChild: Boolean) {
        // Given
        val fakeFixture = hiddenSubtree(showDescendant = fakeShowChild)

        fakeFixture.capture()

        // When
        fakeFixture.onDraw()

        // Then
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isTrue()
        assertThat(fakeFixture.touchPrivacyManager.shouldRecordTouch(Point().apply { x = 10; y = 10 }))
            .isEqualTo(fakeShowChild)
    }

    @Test
    fun `M capture new RUM view W capture() { onRumViewChanged, native copy in progress }`() {
        // Given
        val fakeFixture = Fixture()
        val testedPipeline = CompositionCapturePipeline(fakeFixture.orchestrator, mock(), mock(), mock(), mock())
        fakeFixture.onMap = { view ->
            if (view === fakeFixture.mockRoot) {
                fakeFixture.rumViewScope =
                    CapturedRumViewScope(RumViewIdentityScope(fakeRumContext.viewId + "-next-view"), 0L)
                testedPipeline.onRumViewChanged()
            }
        }

        // When
        fakeFixture.capture()

        // Then
        assertThat(fakeFixture.consumed).isEmpty()
        assertThat(fakeFixture.captureScheduler.pendingCount()).isGreaterThan(0)
    }

    @Test
    fun `M publish next RUM view W runNext() { previous native copy invalidated }`() {
        // Given
        val fakeFixture = Fixture()
        val testedPipeline = CompositionCapturePipeline(fakeFixture.orchestrator, mock(), mock(), mock(), mock())
        fakeFixture.onMap = { view ->
            if (view === fakeFixture.mockRoot) {
                fakeFixture.rumViewScope =
                    CapturedRumViewScope(RumViewIdentityScope(fakeRumContext.viewId + "-next-view"), 0L)
                testedPipeline.onRumViewChanged()
            }
        }

        fakeFixture.capture()

        fakeFixture.captureScheduler.pendingCount()
        fakeFixture.onMap = {}

        // When
        fakeFixture.captureScheduler.runNext()
        fakeFixture.mainThreadExecutor.runNext()

        // Then
        assertThat(fakeFixture.consumed.single().snapshot.scope).isEqualTo(
            RumViewIdentityScope(
                fakeRumContext.viewId + "-next-view"
            )
        )
        assertThat(
            fakeFixture.windowSource.hasTouchPrivacy(
                fakeFixture.mockRoot,
                RumViewIdentityScope(
                    fakeRumContext.viewId + "-next-view"
                )
            )
        ).isTrue()
    }

    private fun recordedScreen(globalPrivacy: TouchPrivacy = TouchPrivacy.HIDE): Fixture =
        Fixture(globalPrivacy).apply {
            onMap = {}
            capture()
        }

    private fun hiddenSubtree(showDescendant: Boolean): Fixture {
        val fakeFixture = Fixture(if (showDescendant) TouchPrivacy.HIDE else TouchPrivacy.SHOW)
        val mockChild = fakeFixture.mockWindowRoot(0)
        val mockGrandchild = fakeFixture.mockWindowRoot(0)
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        whenever(fakeFixture.mockRoot.getTag(R.id.datadog_hidden)).thenReturn(true)
        whenever(mockGrandchild.getTag(R.id.datadog_touch_privacy))
            .thenReturn(if (showDescendant) TouchPrivacy.SHOW.name else TouchPrivacy.HIDE.name)
        whenever(mockChild.childCount).thenReturn(1)
        whenever(mockChild.getChildAt(0)).thenReturn(mockGrandchild)
        whenever(fakeFixture.mockRoot.getChildAt(0)).thenReturn(mockChild)
        fakeFixture.onMap = {}
        return fakeFixture
    }

    private fun mockPointCoordinates() = mockConstruction(Point::class.java) { mockPoint, construction ->
        mockPoint.x = construction.arguments()[0] as Int
        mockPoint.y = construction.arguments()[1] as Int
    }

    private inner class StartupPrivacyFixture(newWindow: Boolean) {
        val fakeScreen = Fixture(TouchPrivacy.SHOW)
        val mockRoot = if (newWindow) {
            fakeScreen.onMap = {}
            fakeScreen.capture()
            fakeScreen.mockWindowRoot(200).also { fakeScreen.windowSource.update(listOf(fakeScreen.mockRoot, it)) }
        } else {
            fakeScreen.mockRoot
        }
        val fakeScope = RumViewIdentityScope(fakeRumContext.viewId)
        private val fakeHiddenX = if (newWindow) 210f else 10f

        init {
            fakeScreen.onMap = {}
            whenever(mockRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        }

        val mockWriter: RecordWriter = mock()
        val mockDelegate: Window.Callback = mock()
        val testedCallback = fakeScreen.callback(mockRoot, mockWriter, mockDelegate)
        val mockDownEvent = mockDownAtHiddenLocation()
        val mockUpEvent = mockUpAtHiddenLocation()

        fun mockDownAtHiddenLocation(): MotionEvent = fakeScreen.mockEvent(MotionEvent.ACTION_DOWN, fakeHiddenX)
        fun mockUpAtHiddenLocation(): MotionEvent = fakeScreen.mockEvent(MotionEvent.ACTION_UP, fakeHiddenX)
        fun dispatchMove() = testedCallback.dispatchTouchEvent(
            fakeScreen.mockEvent(MotionEvent.ACTION_MOVE, fakeHiddenX)
        )

        fun requestCapture() {
            fakeScreen.orchestrator.start()
            fakeScreen.orchestrator.requestCapture()
        }

        fun finishCopy() {
            fakeScreen.captureScheduler.runNext()
            fakeScreen.mainThreadExecutor.runNext()
        }

        fun finishInitialGesture() {
            fakeScreen.onMap = { view ->
                if (view === mockRoot) {
                    fakeScreen.windowSource.hasTouchPrivacy(mockRoot, fakeScope)
                    dispatchMove()
                }
            }
            requestCapture()
            testedCallback.dispatchTouchEvent(mockDownEvent)
            finishCopy()
            testedCallback.dispatchTouchEvent(mockUpEvent)
        }
    }

    private fun Fixture.cancelOnRootCopy(cancellation: Cancellation) {
        onMap = { view ->
            if (view === mockRoot) {
                when (cancellation) {
                    Cancellation.EXPIRY -> {
                        nowNs = GENERATION_BUDGET_NS
                        runCancellation { expiryScheduler.runNext() }
                    }
                    Cancellation.STOP -> runCancellation { orchestrator.stop() }
                    Cancellation.DEADLINE -> nowNs = GENERATION_BUDGET_NS
                }
            }
        }
    }

    private fun runCancellation(task: () -> Unit) {
        val fakeTask = FutureTask { task(); Unit }
        val fakeWorker = Thread(fakeTask).apply { isDaemon = true }
        fakeWorker.start()
        try {
            fakeTask.get(5, TimeUnit.SECONDS)
        } finally {
            fakeWorker.interrupt()
            fakeWorker.join(5_000L)
        }
        assertThat(fakeWorker.isAlive).isFalse()
    }

    private inner class Fixture(
        globalTouchPrivacy: TouchPrivacy = TouchPrivacy.HIDE,
        deferProcessing: Boolean = false
    ) {
        var nowNs = 0L
        var captureAllowed = true
        var rumViewScope: CapturedRumViewScope? = CapturedRumViewScope(RumViewIdentityScope(fakeRumContext.viewId), 0L)
        val touchPrivacyManager = TouchPrivacyManager(globalTouchPrivacy)
        private val mockEventUtils: MotionEventUtils = mock()
        val captureScheduler = TaskQueue()
        val expiryScheduler = TaskQueue()
        val mainThreadExecutor = TaskQueue()
        val consumed = mutableListOf<CompletedSnapshotCapture>()
        private val processingTasks = mutableListOf<Runnable>()
        private val mockViewIdentifierResolver: ViewIdentifierResolver = mock()
        private val mockViewBoundsResolver: ViewBoundsResolver = mock()
        private val drawListeners = mutableMapOf<View, ViewTreeObserver.OnDrawListener>()
        private var nextViewId = 1L
        val mockRoot = mockWindowRoot(0)
        private val mockChild = mockWindowRoot(0)
        var onPrivacyRead: () -> Unit = {}
        var privacyReadAllowed = true
        private val privacyReader = WindowTouchPrivacyReader(
            timeProvider = CaptureTimeProvider { nowNs },
            budgetNs = SLICE_BUDGET_NS,
            isMainThreadWindow = { true }
        )
        val windowSource = ActiveWindowSource { view ->
            onPrivacyRead()
            if (privacyReadAllowed) privacyReader.read(view) else null
        }.apply { update(listOf(mockRoot)) }
        var onMap: (View) -> Unit = { view -> if (view === mockRoot) nowNs += SLICE_BUDGET_NS }

        init {
            mockRoot.stub {
                on { getTag(R.id.datadog_touch_privacy) } doReturn TouchPrivacy.SHOW.name
                on { childCount } doReturn 1
                on { getChildAt(0) } doReturn mockChild
            }
        }

        private val producer = AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = RumViewScopeProvider { rumViewScope },
            timeProvider = mock(),
            traversal = AndroidWindowTraversal(
                mapperRegistry = CapturedViewMapperRegistry(
                    emptyList(),
                    CapturedViewMapper { view, _ ->
                        onMap(view)
                        CapturedViewMapperResult.None
                    },
                    mock()
                ),
                internalLogger = mock(),
                viewIdentifierResolver = mockViewIdentifierResolver,
                viewBoundsResolver = mockViewBoundsResolver
            ),
            touchPrivacyManager = touchPrivacyManager,
            viewIdentifierResolver = mockViewIdentifierResolver,
            isMainThreadWindow = { true }
        )
        val orchestrator = SnapshotCaptureOrchestrator(
            producer = producer,
            processor = if (deferProcessing) {
                ExecutorCapturedSnapshotProcessor(Executor(processingTasks::add), mock())
            } else {
                ImmediateCapturedSnapshotProcessor()
            },
            consumer = CompletedSnapshotConsumer(consumed::add),
            timeProvider = CaptureTimeProvider { nowNs },
            captureScheduler = captureScheduler,
            mainThreadExecutor = mainThreadExecutor,
            expiryScheduler = expiryScheduler,
            timeBudget = object : CaptureTimeBudget {
                override fun canStart(timestampNs: Long): Boolean = captureAllowed
                override fun consume(durationNs: Long) = Unit
            },
            captureDelayNs = 0L,
            generationBudgetNs = GENERATION_BUDGET_NS
        )
        private val drawInterceptor = CompositionViewOnDrawInterceptor(
            windowSource,
            { orchestrator.requestCapture() },
            mock()
        )

        fun onDraw(view: View = mockRoot) {
            drawInterceptor.intercept(windowSource.currentWindows())
            drawListeners.getValue(view).onDraw()
        }

        fun processNext() = processingTasks.removeAt(0).run()

        fun capture() {
            orchestrator.start()
            orchestrator.requestCapture()
            captureScheduler.runNext()
            mainThreadExecutor.runNext()
        }

        fun mockWindowRoot(x: Int): ViewGroup {
            val fakeMetrics = DisplayMetrics().apply { density = 1f }
            val mockResources: Resources = mock { on { displayMetrics } doReturn fakeMetrics }
            val mockObserver: ViewTreeObserver = mock { on { isAlive } doReturn true }
            val mockView: ViewGroup = mock {
                on { resources } doReturn mockResources
                on { viewTreeObserver } doReturn mockObserver
                on { isShown } doReturn true
                on { width } doReturn 100
                on { height } doReturn 100
                on { getLocationOnScreen(any()) } doAnswer { invocation ->
                    invocation.getArgument<IntArray>(0)[0] = x
                    null
                }
            }
            whenever(mockObserver.addOnDrawListener(any())).thenAnswer {
                drawListeners[mockView] = it.getArgument(0)
                null
            }
            whenever(mockViewIdentifierResolver.resolveViewId(mockView)).thenReturn(nextViewId++)
            whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, 1f))
                .thenReturn(GlobalBounds(x.toLong(), 0, 100, 100))
            return mockView
        }

        fun callback(mockRoot: View, writer: RecordWriter, delegate: Window.Callback): CompositionWindowTouchCallback {
            val mockContext: Context = mock()
            val mockResources = mockRoot.resources
            whenever(mockContext.resources).thenReturn(mockResources)
            val mockRumProvider: RumContextProvider = mock()
            whenever(mockRumProvider.getRumContext()).thenAnswer {
                fakeRumContext.copy(viewId = rumViewScope?.scope?.value ?: fakeRumContext.viewId, viewTimeOffsetMs = 0L)
            }
            return CompositionWindowTouchCallback(
                appContext = mockContext,
                wrappedCallback = delegate,
                recordWriter = writer,
                timeProvider = mock(),
                rumContextProvider = mockRumProvider,
                touchPrivacyManager = touchPrivacyManager,
                internalLogger = mock(),
                touchRecordingScope = {
                    rumViewScope?.scope?.let { windowSource.currentTouchPrivacyScope(mockRoot, it) }
                },
                shouldRecordTouch = { point ->
                    val areas = rumViewScope?.scope?.let { windowSource.currentTouchOverrideAreas(mockRoot, it) }
                    areas != null && touchPrivacyManager.shouldRecordTouch(point, areas)
                },
                copyEvent = { it },
                motionEventUtils = mockEventUtils
            )
        }

        fun mockEvent(action: Int, x: Float): MotionEvent {
            val mockEvent: MotionEvent = mock()
            whenever(mockEvent.action).thenReturn(action)
            whenever(mockEvent.pointerCount).thenReturn(1)
            whenever(mockEventUtils.getPointerAbsoluteX(mockEvent, 0)).thenReturn(x)
            whenever(mockEventUtils.getPointerAbsoluteY(mockEvent, 0)).thenReturn(10f)
            return mockEvent
        }
    }

    private class TaskQueue : CaptureTaskScheduler, CaptureMainThreadExecutor {
        val size: Int get() = tasks.size
        private val tasks = mutableListOf<Task>()

        override fun execute(task: () -> Unit): CancellableCaptureWork = schedule(0L, task)

        override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork =
            Task(task).also(tasks::add)

        fun pendingCount() = tasks.count { !it.cancelled && !it.executed }

        fun runNext() = tasks.first { !it.cancelled && !it.executed }.run()
    }

    private class Task(private val action: () -> Unit) : CancellableCaptureWork {
        var cancelled = false
        var executed = false

        override fun cancel() {
            cancelled = true
        }

        fun run() {
            executed = true
            action()
        }
    }

    enum class Cancellation { EXPIRY, STOP, DEADLINE }

    enum class WindowChange { ADD, REMOVE_COMPLETED, REMOVE_CURRENT, REORDER, REMOVE_AND_READD, CLEAR_AND_READD }

    private companion object {
        const val GENERATION_BUDGET_NS = 100L
        const val SLICE_BUDGET_NS = 10L
    }
}
