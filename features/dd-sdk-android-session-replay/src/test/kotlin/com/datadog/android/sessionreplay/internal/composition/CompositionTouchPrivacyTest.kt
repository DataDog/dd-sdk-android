/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CompositionTouchPrivacyTest {

    @ParameterizedTest
    @EnumSource(Cancellation::class)
    fun `M discard partial touch overrides W yielded capture is cancelled`(cancellation: Cancellation) {
        // Given: one completed window, then a second window yielded after visiting a SHOW area.
        val fixture = Fixture()
        val firstWindow = fixture.window(200)
        whenever(firstWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fixture.windowSource.update(listOf(firstWindow, fixture.root))
        fixture.startCapture()
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()

        // When: cancellation either removes the continuation or stops it before re-entering the producer.
        when (cancellation) {
            Cancellation.EXPIRY -> {
                fixture.nowNs = GENERATION_BUDGET_NS
                Thread { fixture.expiryScheduler.runNext() }.apply {
                    start()
                    join()
                }
            }
            Cancellation.STOP -> Thread { fixture.orchestrator.stop() }.apply {
                start()
                join()
            }
            Cancellation.DEADLINE_BEFORE_RESUME -> {
                fixture.nowNs = GENERATION_BUDGET_NS
                fixture.mainThreadExecutor.runNext()
            }
        }
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fixture.windowSource.update(listOf(fixture.root))
        fixture.onMap = {}
        fixture.startCapture()

        // Then: a successful capture with no override must not publish the cancelled capture's SHOW area.
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fixture.touchPrivacyManager.getNextOverrideAreas()).isEmpty()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve committed overrides W resumed traversal aborts or throws`(throws: Boolean) {
        // Given: a previously completed capture, plus a new SHOW area in an unfinished capture.
        val fixture = Fixture()
        val previousArea = Rect(200, 200, 300, 300)
        fixture.touchPrivacyManager.addTouchOverrideArea(previousArea, TouchPrivacy.SHOW)
        fixture.touchPrivacyManager.updateCurrentTouchOverrideAreas()
        fixture.startCapture()
        fixture.onMap = {
            if (throws) error("Traversal failed") else fixture.nowNs = GENERATION_BUDGET_NS
        }

        // When
        fixture.mainThreadExecutor.runNext()

        // Then: neither cooperative abort nor an exception may publish the partial replacement.
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas())
            .containsExactlyEntriesOf(mapOf(previousArea to TouchPrivacy.SHOW))
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
        whenever(fixture.root.getTag(R.id.datadog_touch_privacy)).thenReturn(null)
        fixture.onMap = {}
        fixture.startCapture()
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isFalse()
    }

    @Test
    fun `M publish overrides from every window W yielded capture completes`() {
        // Given: the first window completes, then the second window yields with another override.
        val fixture = Fixture()
        val firstWindow = fixture.window(200)
        whenever(firstWindow.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
        fixture.windowSource.update(listOf(firstWindow, fixture.root))
        fixture.startCapture()
        assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        fixture.onMap = {}

        // When
        fixture.mainThreadExecutor.runNext()

        // Then: both windows' regions are published together only after the traversal completes.
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
        assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(210, 10))).isTrue()
        assertThat(fixture.touchPrivacyManager.getNextOverrideAreas()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, 9L, 10L, 20L, 89L, 90L])
    fun `M resume real traversal within generation deadline W continuation waits in queue`(fakeQueueDelayNs: Long) {
        // Given: the real producer/traversal consumed a full slice visiting the root, with a child remaining.
        val fixture = Fixture()
        fixture.startCapture()
        assertThat(fixture.nowNs).isEqualTo(SLICE_BUDGET_NS)
        assertThat(fixture.consumed).isEmpty()
        var resumedViews = 0
        fixture.onMap = { resumedViews++ }

        // When: a wait of 90 ns reaches the 100 ns generation deadline; all shorter waits are admissible.
        fixture.nowNs += fakeQueueDelayNs
        fixture.mainThreadExecutor.runNext()

        // Then: even a wait longer than the slice budget permits progress, but never extends the deadline.
        if (fixture.nowNs < GENERATION_BUDGET_NS) {
            assertThat(resumedViews).isEqualTo(1)
            assertThat(fixture.consumed).hasSize(1)
            assertThat(fixture.touchPrivacyManager.shouldRecordTouch(Point(10, 10))).isTrue()
        } else {
            assertThat(resumedViews).isZero()
            assertThat(fixture.consumed).isEmpty()
            assertThat(fixture.touchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
        }
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

    private class Fixture {
        var nowNs = 0L
        val touchPrivacyManager = TouchPrivacyManager(TouchPrivacy.HIDE)
        val captureScheduler = TaskQueue()
        val expiryScheduler = TaskQueue()
        val mainThreadExecutor = TaskQueue()
        val consumed = mutableListOf<CompletedSnapshotCapture>()
        private val viewIdentifierResolver: ViewIdentifierResolver = mock()
        private val viewBoundsResolver: ViewBoundsResolver = mock()
        private var nextViewId = 1L
        val root = window(0)
        private val child = window(0)
        val windowSource = ActiveWindowSource().apply { update(listOf(root)) }
        var onMap: (View) -> Unit = { view -> if (view === root) nowNs += SLICE_BUDGET_NS }

        init {
            whenever(root.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.SHOW.name)
            whenever(root.childCount).thenReturn(1)
            whenever(root.getChildAt(0)).thenReturn(child)
        }

        private val producer = AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = RumViewScopeProvider { CapturedRumViewScope(RumViewIdentityScope("view"), 0L) },
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
            processor = ImmediateCapturedSnapshotProcessor(),
            consumer = CompletedSnapshotConsumer(consumed::add),
            timeProvider = CaptureTimeProvider { nowNs },
            captureScheduler = captureScheduler,
            mainThreadExecutor = mainThreadExecutor,
            expiryScheduler = expiryScheduler,
            captureDelayNs = 0L,
            generationBudgetNs = GENERATION_BUDGET_NS,
            sliceBudgetNs = SLICE_BUDGET_NS
        )

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
    }

    private class TaskQueue : CaptureTaskScheduler, CaptureMainThreadExecutor {
        private val tasks = mutableListOf<Task>()

        override fun execute(task: () -> Unit): CancellableCaptureWork = schedule(0L, task)

        override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork =
            Task(task).also(tasks::add)

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

    enum class Cancellation { EXPIRY, STOP, DEADLINE_BEFORE_RESUME }

    private companion object {
        const val GENERATION_BUDGET_NS = 100L
        const val SLICE_BUDGET_NS = 10L
    }
}
