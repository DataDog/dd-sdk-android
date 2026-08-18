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
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal class CompositionTouchPrivacyTest {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M suppress touches W startup or new window awaits complete privacy traversal`(fakeNewWindow: Boolean) {
        // Given: global SHOW must not expose a HIDE area before a complete traversal.
        val fixture = Fixture(TouchPrivacy.SHOW)
        val fakeRoot = if (fakeNewWindow) {
            fixture.onMap = {}
            fixture.startCapture()
            fixture.window(200).also { fixture.windowSource.update(listOf(fixture.root, it)) }
        } else {
            fixture.root
        }
        fixture.onMap = {}
        whenever(fakeRoot.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        val mockWriter: RecordWriter = mock()
        val mockDelegate: Window.Callback = mock()
        val testedCallback = fixture.callback(fakeRoot, mockWriter, mockDelegate)
        val fakeHiddenX = if (fakeNewWindow) 210f else 10f
        val fakeDown = fixture.event(MotionEvent.ACTION_DOWN, fakeHiddenX)
        val fakeUp = fixture.event(MotionEvent.ACTION_UP, fakeHiddenX)
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()

        // The Android mockable jar does not initialize Point's fields in its constructor.
        mockConstruction(Point::class.java) { point, context ->
            point.x = context.arguments()[0] as Int
            point.y = context.arguments()[1] as Int
        }.use {
            // When: a gesture starts before the timer, continues during copying, and ends after completion.
            fixture.onMap = { view ->
                if (view === fakeRoot) {
                    assertThat(fixture.windowSource.hasTouchPrivacy(fakeRoot, RumViewIdentityScope("view"))).isFalse()
                    testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_MOVE, fakeHiddenX))
                }
            }
            testedCallback.dispatchTouchEvent(fakeDown)
            fixture.captureScheduler.runNext()
            fixture.mainThreadExecutor.runNext()
            testedCallback.dispatchTouchEvent(fakeUp)
            testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, fakeHiddenX))
            testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, fakeHiddenX))

            // Then: early gestures and subsequent gestures inside HIDE remain excluded.
            assertThat(fixture.windowSource.hasTouchPrivacy(fakeRoot, RumViewIdentityScope("view"))).isTrue()
            verify(mockWriter, never()).write(any(), any())
            verify(mockDelegate).dispatchTouchEvent(fakeDown)
            verify(mockDelegate).dispatchTouchEvent(fakeUp)
            testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 150f))
            testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 150f))
            verify(mockWriter).write(any(), any())
        }
    }

    @Test
    fun `M require fresh privacy W root is re-added or RUM scope changes`() {
        // Given
        val fixture = Fixture(TouchPrivacy.SHOW)
        fixture.onMap = {}
        fixture.startCapture()
        val fakeScope = RumViewIdentityScope("view")
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()

        // When
        fixture.windowSource.update(emptyList())
        fixture.windowSource.update(listOf(fixture.root))

        // Then
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isFalse()
        fixture.startCapture()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()
        val fakeNextScope = RumViewIdentityScope("next-view")
        fixture.rumViewScope = CapturedRumViewScope(fakeNextScope, 0L)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeNextScope)).isFalse()
        fixture.startCapture()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeNextScope)).isTrue()
    }

    @Test
    fun `M keep touches disabled W traversal filters the root`() {
        // Given
        val fixture = Fixture(TouchPrivacy.SHOW)
        whenever(fixture.root.isShown).thenReturn(false)

        // When
        fixture.startCapture()

        // Then
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isFalse()
    }

    @ParameterizedTest
    @EnumSource(TouchPrivacy::class)
    fun `M suppress touches W redraw makes the committed privacy map stale`(fakeGlobalPrivacy: TouchPrivacy) {
        // Given: a committed SHOW region must stop allowing gestures as soon as the window redraws.
        val fixture = Fixture(fakeGlobalPrivacy)
        fixture.onMap = {}
        fixture.startCapture()
        val mockWriter: RecordWriter = mock()
        val mockDelegate: Window.Callback = mock()
        val testedCallback = fixture.callback(fixture.root, mockWriter, mockDelegate)
        val fakeDown = fixture.event(MotionEvent.ACTION_DOWN, 10f)
        val fakeUp = fixture.event(MotionEvent.ACTION_UP, 10f)
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(
            if (fakeGlobalPrivacy == TouchPrivacy.SHOW) TouchPrivacy.HIDE.name else null
        )

        // When: capture has only been requested; its delayed task has not run yet.
        fixture.draw()
        testedCallback.dispatchTouchEvent(fakeDown)
        testedCallback.dispatchTouchEvent(fakeUp)

        // Then: neither the old SHOW area nor the global SHOW fallback may bypass readiness.
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
        verify(mockWriter, never()).write(any(), any())
        verify(mockDelegate).dispatchTouchEvent(fakeDown)
        verify(mockDelegate).dispatchTouchEvent(fakeUp)

        // Once the replacement map is committed, the now-hidden location stays excluded.
        fixture.captureScheduler.runNext()
        fixture.mainThreadExecutor.runNext()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))
        verify(mockWriter, never()).write(any(), any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M keep stale touch privacy disabled W replacement expires or is denied`(fakeDenied: Boolean) {
        // Given
        val fixture = Fixture()
        fixture.onMap = {}
        fixture.startCapture()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fixture.callback(fixture.root, mockWriter, mock())
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fixture.draw()
        fixture.captureAllowed = !fakeDenied
        fixture.onMap = { fixture.nowNs = GENERATION_BUDGET_NS }

        // When
        fixture.captureScheduler.runNext()
        fixture.mainThreadExecutor.runNext()
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))

        // Then: a failed or deferred replacement cannot reactivate the old SHOW rectangle.
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
        verify(mockWriter, never()).write(any(), any())
        fixture.captureAllowed = true
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.consumed).hasSize(2)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain visual capture without stale privacy W redraw overtakes native copy`(fakeFinalView: Boolean) {
        // Given: a redraw during either the root or final child copy overtakes the old SHOW area.
        val fixture = Fixture()
        fixture.onMap = {}
        fixture.startCapture()
        val fakePreviousAreas = fixture.touchPrivacyManager.getCurrentOverrideAreas()
        fixture.onMap = { view ->
            if (if (fakeFinalView) view !== fixture.root else view === fixture.root) {
                whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
                fixture.draw()
            }
        }

        // When
        fixture.startCapture()

        // Then: visual completion is allowed; live refresh, rather than the stale candidate, restores readiness.
        assertThat(fixture.consumed).hasSize(2)
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).containsExactlyEntriesOf(fakePreviousAreas)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
        fixture.onMap = {}
        fixture.captureScheduler.runNext()
        fixture.mainThreadExecutor.runNext()
        assertThat(fixture.consumed).hasSize(3)
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
    }

    @Test
    fun `M refresh touch privacy W window redraws before worker construction finishes`() {
        // Given: the UI copy finishes and publishes privacy while snapshot construction is queued.
        val fixture = Fixture(deferProcessing = true)
        fixture.onMap = {}
        fixture.startCapture()
        fixture.processNext()
        fixture.startCapture()
        val fakeScope = RumViewIdentityScope("view")
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()

        // When: a redraw dirties the copied map before the worker completes the visual snapshot.
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fixture.draw()
        fixture.processNext()

        // Then: a live privacy check restores readiness with the new policy, without the old SHOW area.
        assertThat(fixture.consumed).hasSize(2)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()
        assertThat(fixture.windowSource.currentTouchOverrideAreas(fixture.root, fakeScope)).isEmpty()
        fixture.captureScheduler.runNext()
        fixture.mainThreadExecutor.runNext()
        fixture.processNext()
        assertThat(fixture.consumed).hasSize(3)
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard a gesture across privacy revisions W redraw and recapture`(fakeEventWhileDirty: Boolean) {
        // Given
        val fixture = Fixture()
        fixture.onMap = {}
        fixture.startCapture()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fixture.callback(fixture.root, mockWriter, mock())
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))

        // When: invalidation may be followed by recapture before the next touch event arrives.
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fixture.draw()
        if (fakeEventWhileDirty) testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_MOVE, 10f))
        fixture.captureScheduler.runNext()
        fixture.mainThreadExecutor.runNext()
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))

        // Then: an old buffered down and its gesture tail are discarded even within the same RUM view.
        verify(mockWriter, never()).write(any(), any())
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fixture.draw()
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 20f))
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 20f))
        val records = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(records.capture(), any())
        assertThat(records.firstValue.records).hasSize(2)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain unchanged window policies W either tracked window redraws`(fakeSecondWindow: Boolean) {
        // Given: both windows use the same committed override map.
        val fixture = Fixture()
        val fakeOtherWindow = fixture.window(200)
        fixture.windowSource.update(listOf(fixture.root, fakeOtherWindow))
        fixture.onMap = {}
        fixture.startCapture()
        val fakeWindowSnapshot = fixture.windowSource.snapshot()
        val fakeScope = RumViewIdentityScope("view")
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()
        assertThat(fixture.windowSource.hasTouchPrivacy(fakeOtherWindow, fakeScope)).isTrue()

        // When
        fixture.draw(if (fakeSecondWindow) fakeOtherWindow else fixture.root)

        // Then: refresh the affected window without revoking either unchanged policy.
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()
        assertThat(fixture.windowSource.hasTouchPrivacy(fakeOtherWindow, fakeScope)).isTrue()
        assertThat(fixture.windowSource.isCurrent(fakeWindowSnapshot)).isTrue()
        fixture.captureScheduler.runNext()
        fixture.mainThreadExecutor.runNext()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()
        assertThat(fixture.windowSource.hasTouchPrivacy(fakeOtherWindow, fakeScope)).isTrue()
    }

    @Test
    fun `M retain gestures W animation redraws and equivalent captures refresh privacy`() {
        // Given
        val fixture = Fixture(TouchPrivacy.SHOW)
        fixture.onMap = {}
        fixture.startCapture()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fixture.callback(fixture.root, mockWriter, mock())
        val fakeScope = fixture.windowSource.currentTouchPrivacyScope(fixture.root, RumViewIdentityScope("view"))
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))

        // When: frames and complete refreshes arrive between the beginning and end of a gesture.
        repeat(10) {
            fixture.draw()
            fixture.captureScheduler.runNext()
            fixture.mainThreadExecutor.runNext()
            assertThat(fixture.windowSource.currentTouchPrivacyScope(fixture.root, RumViewIdentityScope("view")))
                .isEqualTo(fakeScope)
        }
        fixture.draw()
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))

        // Then
        val records = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(records.capture(), any())
        assertThat(records.firstValue.records).hasSize(2)
    }

    @Test
    fun `M establish current privacy W redraw overtakes the initial native copy`() {
        // Given: draws during native mapping dirty the candidate without invalidating the copy.
        val fixture = Fixture(TouchPrivacy.SHOW)
        fixture.onMap = { view ->
            if (view === fixture.root) {
                whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
            }
            fixture.draw()
        }

        // When
        fixture.startCapture()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fixture.callback(fixture.root, mockWriter, mock())
        val fakeScope = RumViewIdentityScope("view")
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))

        // Then: live privacy converges without granting the copy's old SHOW permission.
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, fakeScope)).isTrue()
        assertThat(fixture.windowSource.currentTouchOverrideAreas(fixture.root, fakeScope))
            .containsValue(TouchPrivacy.HIDE).doesNotContainValue(TouchPrivacy.SHOW)
        verify(mockWriter, never()).write(any(), any())
    }

    @Test
    fun `M deny dirty privacy W live refresh fails before visual recapture`() {
        // Given
        val fixture = Fixture(TouchPrivacy.SHOW)
        fixture.onMap = {}
        fixture.startCapture()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fixture.callback(fixture.root, mockWriter, mock())
        fixture.privacyReadAllowed = false
        fixture.draw()

        // When
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))

        // Then: even global SHOW and previously granted overrides cannot bypass a failed refresh.
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isFalse()
        verify(mockWriter, never()).write(any(), any())
        fixture.privacyReadAllowed = true
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))
        testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))
        verify(mockWriter).write(any(), any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reject privacy refresh W draw or cancellation overtakes live read`(fakeCancel: Boolean) {
        // Given
        val fixture = Fixture(TouchPrivacy.SHOW)
        fixture.onMap = {}
        fixture.startCapture()
        fixture.draw()
        fixture.onPrivacyRead = {
            if (fakeCancel) fixture.windowSource.clearTouchPrivacy() else fixture.draw()
        }

        // When
        val result = fixture.windowSource.currentTouchOverrideAreas(fixture.root, RumViewIdentityScope("view"))

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @EnumSource(Cancellation::class)
    fun `M discard partial touch overrides W copying is cancelled`(cancellation: Cancellation) {
        // Given: copy one window, then cancel while visiting a SHOW area in the second.
        val fixture = Fixture()
        val firstWindow = fixture.window(200)
        whenever(firstWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fixture.windowSource.update(listOf(firstWindow, fixture.root))
        fixture.onMap = { view ->
            if (view === fixture.root) {
                when (cancellation) {
                    Cancellation.EXPIRY -> {
                        fixture.nowNs = GENERATION_BUDGET_NS
                        runCancellation { fixture.expiryScheduler.runNext() }
                    }
                    Cancellation.STOP -> runCancellation { fixture.orchestrator.stop() }
                    Cancellation.DEADLINE -> fixture.nowNs = GENERATION_BUDGET_NS
                }
            }
        }

        // When
        fixture.startCapture()

        // Then
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.windowSource.hasTouchPrivacy(firstWindow, RumViewIdentityScope("view"))).isFalse()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isFalse()
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fixture.windowSource.update(listOf(fixture.root))
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.touchPrivacyManager.getNextOverrideAreas()).isEmpty()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve committed overrides W copying aborts or throws`(throws: Boolean) {
        // Given
        val fixture = Fixture()
        val previousArea = Rect(200, 200, 300, 300)
        fixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(previousArea to TouchPrivacy.SHOW))
        fixture.onMap = { view ->
            if (view !== fixture.root) {
                if (throws) error("Traversal failed") else fixture.nowNs = GENERATION_BUDGET_NS
            }
        }

        // When
        fixture.startCapture()

        // Then
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas())
            .containsExactlyEntriesOf(mapOf(previousArea to TouchPrivacy.SHOW))
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard stale overrides W RUM scope changes while copying`(fakeMissingScope: Boolean) {
        // Given
        val fixture = Fixture()
        fixture.onMap = { view ->
            if (view !== fixture.root) {
                fixture.rumViewScope =
                    if (fakeMissingScope) null else CapturedRumViewScope(RumViewIdentityScope("new"), 0L)
            }
        }

        // When
        fixture.startCapture()

        // Then
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M stop before next window W RUM scope changes while copying`(fakeMissingScope: Boolean) {
        // Given: the first window changes scope before the next window is copied.
        val fixture = Fixture()
        val fakeFirstWindow = fixture.window(200)
        fixture.windowSource.update(listOf(fakeFirstWindow, fixture.root))
        val fakePreviousAreas = mapOf(Rect(400, 0, 500, 100) to TouchPrivacy.SHOW)
        fixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(fakePreviousAreas)
        var copiedLaterViews = 0
        fixture.onMap = { view ->
            if (view === fakeFirstWindow) {
                fixture.rumViewScope =
                    if (fakeMissingScope) null else CapturedRumViewScope(RumViewIdentityScope("new"), 0L)
            } else {
                copiedLaterViews++
            }
        }

        // When
        fixture.startCapture()

        // Then
        assertThat(copiedLaterViews).isZero()
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).containsExactlyEntriesOf(fakePreviousAreas)
    }

    @ParameterizedTest
    @EnumSource(WindowChange::class)
    fun `M discard stale capture W window roots change before next window`(fakeChange: WindowChange) {
        // Given: copying the first window changes the roots before the second window is visited.
        val fixture = Fixture()
        val fakeFirstWindow = fixture.window(200)
        fixture.windowSource.update(listOf(fakeFirstWindow, fixture.root))
        val fakePreviousAreas = mapOf(Rect(400, 0, 500, 100) to TouchPrivacy.SHOW)
        fixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(fakePreviousAreas)
        var copiedLaterViews = 0
        fixture.onMap = { view ->
            if (view === fakeFirstWindow) changeWindows(fixture, fakeFirstWindow, fakeChange) else copiedLaterViews++
        }

        // When
        fixture.startCapture()

        // Then: stale roots are not read, and their partial privacy map is never published.
        assertThat(copiedLaterViews).isZero()
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).containsExactlyEntriesOf(fakePreviousAreas)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isFalse()
        assertThat(fixture.windowSource.hasTouchPrivacy(fakeFirstWindow, RumViewIdentityScope("view"))).isFalse()

        // A fresh capture uses the new ordered roots and can complete normally.
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.consumed.single().snapshot.root?.children)
            .hasSize(fixture.windowSource.currentWindows().size)
    }

    @ParameterizedTest
    @EnumSource(WindowChange::class)
    fun `M discard stale overrides W window roots change in final copied view`(fakeChange: WindowChange) {
        // Given: the final child changes the source after an earlier window was already copied.
        val fixture = Fixture()
        val fakeFirstWindow = fixture.window(200)
        fixture.windowSource.update(listOf(fakeFirstWindow, fixture.root))
        val fakePreviousAreas = mapOf(Rect(400, 0, 500, 100) to TouchPrivacy.SHOW)
        fixture.touchPrivacyManager.replaceCurrentTouchOverrideAreas(fakePreviousAreas)
        fixture.onMap = { view ->
            if (view !== fakeFirstWindow && view !== fixture.root) changeWindows(fixture, fakeFirstWindow, fakeChange)
        }

        // When
        fixture.startCapture()

        // Then: finishing the copy does not publish a snapshot or touch overrides from stale roots.
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).containsExactlyEntriesOf(fakePreviousAreas)
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isFalse()
        assertThat(fixture.windowSource.hasTouchPrivacy(fakeFirstWindow, RumViewIdentityScope("view"))).isFalse()
    }

    @Test
    fun `M complete capture W window refresh keeps the same ordered roots`() {
        // Given
        val fixture = Fixture()
        fixture.onMap = { fixture.windowSource.update(listOf(fixture.root)) }

        // When
        fixture.startCapture()

        // Then: polling the same roots does not invalidate a capture or its privacy map.
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
    }

    private fun changeWindows(fixture: Fixture, firstWindow: View, change: WindowChange) {
        val source = fixture.windowSource
        when (change) {
            WindowChange.ADD -> source.update(listOf(firstWindow, fixture.root, fixture.window(300)))
            WindowChange.REMOVE_COMPLETED -> source.update(listOf(fixture.root))
            WindowChange.REMOVE_CURRENT -> source.update(listOf(firstWindow))
            WindowChange.REORDER -> source.update(listOf(fixture.root, firstWindow))
            WindowChange.REMOVE_AND_READD -> {
                source.update(listOf(firstWindow))
                source.update(listOf(firstWindow, fixture.root))
            }
            WindowChange.CLEAR_AND_READD -> {
                source.clear()
                source.update(listOf(firstWindow, fixture.root))
            }
        }
    }

    @Test
    fun `M complete capture W time offset changes within same RUM view`() {
        // Given
        val fixture = Fixture()
        fixture.onMap = { fixture.rumViewScope = fixture.rumViewScope?.copy(viewTimeOffsetMs = 100L) }

        // When
        fixture.startCapture()

        // Then
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
    }

    @Test
    fun `M publish overrides from every window W capture exceeds former slice budget`() {
        // Given
        val fixture = Fixture()
        val firstWindow = fixture.window(200)
        whenever(firstWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fixture.windowSource.update(listOf(firstWindow, fixture.root))
        fixture.onMap = { fixture.nowNs += SLICE_BUDGET_NS }

        // When
        fixture.startCapture()

        // Then: the complete multi-window copy is published together without a continuation.
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.mainThreadExecutor.size).isEqualTo(1)
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(210, 10))).isTrue()
        assertThat(fixture.touchPrivacyManager.getNextOverrideAreas()).isEmpty()
    }

    @Test
    fun `M clear committed overrides W no windows remain`() {
        // Given
        val fixture = Fixture()
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()

        // When
        fixture.windowSource.update(emptyList())
        fixture.startCapture()

        // Then
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @Test
    fun `M clear previous screen overrides W capture has no RUM scope`() {
        // Given
        val fixture = Fixture()
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isNotEmpty()

        // When
        fixture.rumViewScope = null
        fixture.startCapture()

        // Then: the previous screen's SHOW area no longer overrides global HIDE.
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point())).isFalse()
    }

    @ParameterizedTest
    @CsvSource("199, 50, false", "300, 50, false", "250, -1, false", "250, 100, false", "200, 0, true", "299, 99, true")
    fun `M constrain SHOW override to outer bounds W tagged view has padding`(
        fakeX: Int,
        fakeY: Int,
        fakeExpectedRecord: Boolean
    ) {
        // Given: the view occupies [200, 0, 300, 100], including all of its padding.
        val fixture = Fixture()
        val fakeWindow = fixture.window(200)
        whenever(fakeWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        whenever(fakeWindow.paddingLeft).thenReturn(20)
        whenever(fakeWindow.paddingTop).thenReturn(30)
        whenever(fakeWindow.paddingRight).thenReturn(40)
        whenever(fakeWindow.paddingBottom).thenReturn(50)
        fixture.windowSource.update(listOf(fakeWindow))
        fixture.onMap = {}
        val fakePoint = Point().apply { x = fakeX; y = fakeY }

        // When
        fixture.startCapture()

        // Then
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(fakePoint)).isEqualTo(fakeExpectedRecord)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M isolate window overrides W dispatchTouchEvent { overlapping windows }`(fakeGlobalShow: Boolean) {
        // Given: the underlying window overrides the global policy; the overlay has no override.
        val fixture = Fixture(if (fakeGlobalShow) TouchPrivacy.SHOW else TouchPrivacy.HIDE)
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy))
            .thenReturn(if (fakeGlobalShow) TouchPrivacy.HIDE.name else TouchPrivacy.SHOW.name)
        val fakeOverlay = fixture.window(0)
        fixture.windowSource.update(listOf(fixture.root, fakeOverlay))
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.windowSource.hasTouchPrivacy(fakeOverlay, RumViewIdentityScope("view"))).isTrue()
        val mockWriter: RecordWriter = mock()
        val testedCallback = fixture.callback(fakeOverlay, mockWriter, mock())
        mockConstruction(Point::class.java) { point, context ->
            point.x = context.arguments()[0] as Int
            point.y = context.arguments()[1] as Int
        }.use {
            // When
            testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_DOWN, 10f))
            testedCallback.dispatchTouchEvent(fixture.event(MotionEvent.ACTION_UP, 10f))

            // Then: only the overlay's own overrides and the global policy apply to its touches.
            if (fakeGlobalShow) {
                verify(mockWriter).write(any(), any())
            } else {
                verify(mockWriter, never()).write(any(), any())
            }
        }
    }

    @Test
    fun `M preserve HIDE W capture { nested overrides have identical rectangles }`() {
        // Given
        val fixture = Fixture(TouchPrivacy.SHOW)
        val fakeChild = fixture.window(0)
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(fakeChild.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        whenever(fixture.root.getChildAt(0)).thenReturn(fakeChild)
        fixture.onMap = {}

        // When
        fixture.startCapture()

        // Then
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point().apply { x = 10; y = 10 })).isFalse()
        fixture.draw()
        val fakeAreas = fixture.windowSource.currentTouchOverrideAreas(fixture.root, RumViewIdentityScope("view"))
        assertThat(fakeAreas).containsValue(TouchPrivacy.HIDE).doesNotContainValue(TouchPrivacy.SHOW)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M collect descendant touch overrides W capture { hidden container }`(fakeShowChild: Boolean) {
        // Given: visual hiding suppresses layers, while nested touch tags override the global policy.
        val fixture = Fixture(if (fakeShowChild) TouchPrivacy.HIDE else TouchPrivacy.SHOW)
        val fakeChild = fixture.window(0)
        val fakeGrandchild = fixture.window(0)
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        whenever(fixture.root.getTag(R.id.datadog_hidden)).thenReturn(true)
        whenever(fakeGrandchild.getTag(R.id.datadog_touch_privacy))
            .thenReturn(if (fakeShowChild) TouchPrivacy.SHOW.name else TouchPrivacy.HIDE.name)
        whenever(fakeChild.childCount).thenReturn(1)
        whenever(fakeChild.getChildAt(0)).thenReturn(fakeGrandchild)
        whenever(fixture.root.getChildAt(0)).thenReturn(fakeChild)
        fixture.onMap = {}

        // When
        fixture.startCapture()

        // Then
        fixture.draw()
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("view"))).isTrue()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point().apply { x = 10; y = 10 }))
            .isEqualTo(fakeShowChild)
        assertThat(fixture.consumed.single().snapshot.layers).hasSize(1)
        assertThat(fixture.consumed.single().snapshot.wireframes.single())
            .isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M capture new RUM view W onRumViewChanged { native copy in progress }`() {
        // Given
        val fixture = Fixture()
        val testedPipeline = CompositionCapturePipeline(fixture.orchestrator, mock(), mock(), mock(), mock())
        fixture.onMap = { view ->
            if (view === fixture.root) {
                fixture.rumViewScope = CapturedRumViewScope(RumViewIdentityScope("next-view"), 0L)
                testedPipeline.onRumViewChanged()
            }
        }

        // When
        fixture.startCapture()

        // Then: the old copy is discarded and the coalesced request captures the new scope.
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.captureScheduler.pendingCount()).isGreaterThan(0)
        fixture.onMap = {}
        fixture.captureScheduler.runNext()
        fixture.mainThreadExecutor.runNext()
        assertThat(fixture.consumed.single().snapshot.scope).isEqualTo(RumViewIdentityScope("next-view"))
        assertThat(fixture.windowSource.hasTouchPrivacy(fixture.root, RumViewIdentityScope("next-view"))).isTrue()
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

    private class Fixture(
        globalTouchPrivacy: TouchPrivacy = TouchPrivacy.HIDE,
        deferProcessing: Boolean = false
    ) {
        var nowNs = 0L
        var captureAllowed = true
        var rumViewScope: CapturedRumViewScope? = CapturedRumViewScope(RumViewIdentityScope("view"), 0L)
        val touchPrivacyManager = TouchPrivacyManager(globalTouchPrivacy)
        private val mockEventUtils: MotionEventUtils = mock()
        val captureScheduler = TaskQueue()
        val expiryScheduler = TaskQueue()
        val mainThreadExecutor = TaskQueue()
        val consumed = mutableListOf<CompletedSnapshotCapture>()
        private val processingTasks = mutableListOf<Runnable>()
        private val viewIdentifierResolver: ViewIdentifierResolver = mock()
        private val viewBoundsResolver: ViewBoundsResolver = mock()
        private val drawListeners = mutableMapOf<View, ViewTreeObserver.OnDrawListener>()
        private var nextViewId = 1L
        val root = window(0)
        private val child = window(0)
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
        }.apply { update(listOf(root)) }
        var onMap: (View) -> Unit = { view -> if (view === root) nowNs += SLICE_BUDGET_NS }

        init {
            whenever(root.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
            whenever(root.childCount).thenReturn(1)
            whenever(root.getChildAt(0)).thenReturn(child)
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
                viewIdentifierResolver = viewIdentifierResolver,
                viewBoundsResolver = viewBoundsResolver
            ),
            touchPrivacyManager = touchPrivacyManager,
            viewIdentifierResolver = viewIdentifierResolver,
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

        fun draw(view: View = root) {
            drawInterceptor.intercept(windowSource.currentWindows())
            drawListeners.getValue(view).onDraw()
        }

        fun processNext() = processingTasks.removeAt(0).run()

        fun startCapture() {
            orchestrator.start()
            orchestrator.requestCapture()
            captureScheduler.runNext()
            mainThreadExecutor.runNext()
        }

        fun window(x: Int): ViewGroup {
            val view: ViewGroup = mock()
            val resources: Resources = mock()
            whenever(resources.displayMetrics).thenReturn(DisplayMetrics().apply { density = 1f })
            whenever(view.resources).thenReturn(resources)
            val stubObserver: ViewTreeObserver = mock()
            whenever(stubObserver.isAlive).thenReturn(true)
            whenever(view.viewTreeObserver).thenReturn(stubObserver)
            whenever(stubObserver.addOnDrawListener(any())).thenAnswer {
                drawListeners[view] = it.getArgument(0)
                null
            }
            whenever(view.isShown).thenReturn(true)
            whenever(view.width).thenReturn(100)
            whenever(view.height).thenReturn(100)
            whenever(view.getLocationOnScreen(any())).thenAnswer {
                it.getArgument<IntArray>(0)[0] = x
                null
            }
            whenever(viewIdentifierResolver.resolveViewId(view)).thenReturn(nextViewId++)
            whenever(viewBoundsResolver.resolveViewGlobalBounds(view, 1f))
                .thenReturn(GlobalBounds(x.toLong(), 0, 100, 100))
            return view
        }

        fun callback(root: View, writer: RecordWriter, delegate: Window.Callback): CompositionWindowTouchCallback {
            val mockContext: Context = mock()
            val stubResources = root.resources
            whenever(mockContext.resources).thenReturn(stubResources)
            val mockRumProvider: RumContextProvider = mock()
            whenever(mockRumProvider.getRumContext()).thenAnswer {
                SessionReplayRumContext("app", "session", rumViewScope?.scope?.value ?: "view")
            }
            return CompositionWindowTouchCallback(
                appContext = mockContext,
                wrappedCallback = delegate,
                recordWriter = writer,
                timeProvider = mock(),
                rumContextProvider = mockRumProvider,
                touchPrivacyManager = touchPrivacyManager,
                internalLogger = mock(),
                touchRecordingScope = { rumViewScope?.scope?.let { windowSource.currentTouchPrivacyScope(root, it) } },
                shouldRecordTouch = { point ->
                    val areas = rumViewScope?.scope?.let { windowSource.currentTouchOverrideAreas(root, it) }
                    areas != null && touchPrivacyManager.shouldRecordTouch(point, areas)
                },
                copyEvent = { it },
                motionEventUtils = mockEventUtils
            )
        }

        fun event(action: Int, x: Float): MotionEvent {
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
