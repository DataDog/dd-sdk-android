/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Rect
import android.view.SurfaceView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.PixelCapture.Companion.CAPTURE_BUDGET_MS
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * [SurfaceView] is used as the isolation view for cases that don't need a real capture to
 * succeed: [PixelCapture.containsHardwareSurface] short-circuits capture for it before any
 * real [android.graphics.Bitmap]/[android.graphics.Canvas] work is attempted, keeping those
 * tests free of native graphics calls.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PixelCaptureTest {

    private lateinit var testedPixelCapture: PixelCapture

    @Mock
    lateinit var mockResourceResolver: ResourceResolver

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockCaptureTimeBank: TimeBank

    @Forgery
    lateinit var fakeDpBounds: GlobalBounds

    @Forgery
    lateinit var fakeWireframe: MobileSegment.Wireframe.ImageWireframe

    private lateinit var fakeIsolationClipRect: Rect

    // SystemClock.elapsedRealtime() is not advanceable in local unit tests (the Android
    // unit-test stub returns a fixed 0L), so PixelCapture's time source is injected here.
    private var fakeElapsedRealtimeMs = 0L

    // Reused by tests that need a SECOND onPreTraversal call (e.g. to reset the CAPTURE_BUDGET_MS
    // baseline for a later, simulated cycle) without that call being treated as a navigation,
    // which would clear the cache this class's own cache-reuse logic is under test.
    private var fakeViewUrl: String? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeIsolationClipRect = Rect(0, 0, forge.aSmallInt() + 1, forge.aSmallInt() + 1)
        fakeViewUrl = forge.aNullable { aString() }
        // Defaults to "budget always available" — matches a real, freshly-constructed
        // RecordingTimeBank's full initial balance, so existing tests (one draw each) are
        // unaffected; tests exercising the throttle override this explicitly.
        whenever(mockCaptureTimeBank.updateAndCheck(any())).thenReturn(true)
        testedPixelCapture = PixelCapture(
            resourceResolver = mockResourceResolver,
            elapsedRealtimeMs = { fakeElapsedRealtimeMs },
            captureTimeBank = mockCaptureTimeBank
        )
        // Mirrors production call order (WindowsOnDrawListener): onPreTraversal marks the start
        // of the snapshot cycle that the CAPTURE_BUDGET_MS deadline is anchored to.
        testedPixelCapture.onPreTraversal(fakeViewUrl)
    }

    @Test
    fun `M call jobStarted immediately W registerPendingCapture()`() {
        // When
        testedPixelCapture.registerPendingCapture(
            nodeId = 1L,
            dpBounds = fakeDpBounds,
            isolationView = mock<SurfaceView>(),
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verify(mockAsyncJobStatusCallback).jobStarted()
    }

    @Test
    fun `M do nothing W processPendingCaptures() {no pending captures}`() {
        // When / Then
        assertThatCode { testedPixelCapture.processPendingCaptures() }
            .doesNotThrowAnyException()
    }

    @Test
    fun `M finish the job without a resourceId W processPendingCaptures() {capture unavailable}`() {
        // Given — a SurfaceView can never be captured via View.draw (see containsHardwareSurface)
        testedPixelCapture.registerPendingCapture(
            nodeId = 1L,
            dpBounds = fakeDpBounds,
            isolationView = mock<SurfaceView>(),
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { fail("Wireframe should not be replaced") },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — no capture available, but the pipeline still completes cleanly
        verify(mockAsyncJobStatusCallback).jobFinished()
        verifyNoInteractions(mockResourceResolver)
    }

    @Test
    fun `M reuse the cached resourceId and skip View draw W processPendingCaptures() {same-size region already cached}`(
        forge: Forge
    ) {
        // Given — a cache entry already exists for this node at exactly this region size
        val nodeId = forge.aPositiveInt().toLong()
        val fakeResourceId = forge.aStringMatching("[a-f0-9]{32}")
        testedPixelCapture.seedCacheForTesting(
            nodeId = nodeId,
            width = fakeIsolationClipRect.width(),
            height = fakeIsolationClipRect.height(),
            resourceId = fakeResourceId
        )

        val isolationView = mock<SurfaceView>()
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { fail("Wireframe should not be replaced by a placeholder") },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — no capture work at all (isDirty defaults to false on an unstubbed mock, so the
        // cache is trusted); the cached resourceId is applied directly
        verify(isolationView, never()).draw(any())
        verifyNoInteractions(mockResourceResolver)
        assertThat(fakeWireframe.resourceId).isEqualTo(fakeResourceId)
        assertThat(fakeWireframe.isEmpty).isFalse()
        verify(mockAsyncJobStatusCallback).jobFinished()
    }

    @Test
    fun `M not reuse the cache W processPendingCaptures() {region resized since last capture}`(forge: Forge) {
        // Given — a cache entry exists, but at a DIFFERENT size than the new pending capture
        val nodeId = forge.aPositiveInt().toLong()
        testedPixelCapture.seedCacheForTesting(
            nodeId = nodeId,
            width = fakeIsolationClipRect.width() + 1,
            height = fakeIsolationClipRect.height(),
            resourceId = forge.aStringMatching("[a-f0-9]{32}")
        )
        val resourceIdBeforeCapture = fakeWireframe.resourceId

        // A SurfaceView can never actually be captured (see containsHardwareSurface), so if the
        // cache is (correctly) not reused here, this falls through to a real capture attempt,
        // which fails cleanly for this view type — proving the cache short-circuit was NOT taken.
        val isolationView = mock<SurfaceView>()
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — capture failed (no cache reuse, no successful View.draw), so the wireframe is
        // left exactly as it was, unpopulated by either path
        assertThat(fakeWireframe.resourceId).isEqualTo(resourceIdBeforeCapture)
        verify(mockAsyncJobStatusCallback).jobFinished()
    }

    @Test
    fun `M not reuse the cache W processPendingCaptures() {view is dirty, past the min redraw interval}`(
        forge: Forge
    ) {
        // Given — a same-size cache entry exists, but the view has been invalidated since, and
        // MIN_REDRAW_INTERVAL_MS has already passed — View.isDirty being true only shortens the
        // trust window, it doesn't force an immediate redraw (see PixelCapture's class doc), so
        // this entry must be old enough relative to that shorter window to actually be rejected.
        val nodeId = forge.aPositiveInt().toLong()
        testedPixelCapture.seedCacheForTesting(
            nodeId = nodeId,
            width = fakeIsolationClipRect.width(),
            height = fakeIsolationClipRect.height(),
            resourceId = forge.aStringMatching("[a-f0-9]{32}"),
            capturedAtMs = fakeElapsedRealtimeMs
        )
        val resourceIdBeforeCapture = fakeWireframe.resourceId

        // Advance to a new simulated cycle, past MIN_REDRAW_INTERVAL_MS but with its OWN fresh
        // CAPTURE_BUDGET_MS baseline (same URL so this isn't a navigation that would clear the
        // cache) — otherwise this would trip the per-cycle budget instead of exercising the
        // min-redraw-interval check specifically.
        fakeElapsedRealtimeMs += PixelCapture.MIN_REDRAW_INTERVAL_MS
        testedPixelCapture.onPreTraversal(fakeViewUrl)

        // A SurfaceView can never actually be captured (see containsHardwareSurface), so if the
        // cache is (correctly) not reused here, this falls through to a real capture attempt,
        // which fails cleanly for this view type — proving the cache short-circuit was NOT taken.
        val isolationView = mock<SurfaceView>()
        whenever(isolationView.isDirty).thenReturn(true)
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — capture failed (no cache reuse, no successful View.draw), so the wireframe is
        // left exactly as it was, unpopulated by either path
        assertThat(fakeWireframe.resourceId).isEqualTo(resourceIdBeforeCapture)
        verify(mockAsyncJobStatusCallback).jobFinished()
    }

    @Test
    fun `M still reuse the cache W processPendingCaptures() {view is dirty, within the min redraw interval}`(
        forge: Forge
    ) {
        // Given — the view is dirty, but the cache entry is fresh enough that MIN_REDRAW_INTERVAL_MS
        // hasn't elapsed yet. This is the throttle a continuously-but-not-meaningfully invalidating
        // view (e.g. a blinking text cursor) relies on: isDirty=true alone must NOT force an
        // immediate redraw on every single cycle.
        val nodeId = forge.aPositiveInt().toLong()
        val fakeResourceId = forge.aStringMatching("[a-f0-9]{32}")
        testedPixelCapture.seedCacheForTesting(
            nodeId = nodeId,
            width = fakeIsolationClipRect.width(),
            height = fakeIsolationClipRect.height(),
            resourceId = fakeResourceId
        )

        val isolationView = mock<SurfaceView>()
        whenever(isolationView.isDirty).thenReturn(true)
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { fail("Wireframe should not be replaced by a placeholder") },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — no capture work at all; the cached resourceId is applied directly
        verify(isolationView, never()).draw(any())
        verifyNoInteractions(mockResourceResolver)
        assertThat(fakeWireframe.resourceId).isEqualTo(fakeResourceId)
        assertThat(fakeWireframe.isEmpty).isFalse()
        verify(mockAsyncJobStatusCallback).jobFinished()
    }

    @Test
    fun `M not reuse the cache W processPendingCaptures() {cache entry expired}`(forge: Forge) {
        // Given — a same-size, non-dirty cache entry exists, but it's older than
        // MAX_CACHE_AGE_MS — the backstop for what View.isDirty alone can miss (see PixelCapture's
        // class doc): the entry must expire even though nothing here reports being dirty.
        val nodeId = forge.aPositiveInt().toLong()
        testedPixelCapture.seedCacheForTesting(
            nodeId = nodeId,
            width = fakeIsolationClipRect.width(),
            height = fakeIsolationClipRect.height(),
            resourceId = forge.aStringMatching("[a-f0-9]{32}"),
            capturedAtMs = fakeElapsedRealtimeMs
        )
        val resourceIdBeforeCapture = fakeWireframe.resourceId

        // Advance to a new simulated cycle, past the cache's expiry but with its OWN fresh
        // CAPTURE_BUDGET_MS baseline (via onPreTraversal, same URL so this isn't a navigation
        // that would clear the cache) — otherwise this would trip the per-cycle budget instead
        // of exercising cache-age expiry specifically.
        fakeElapsedRealtimeMs += PixelCapture.MAX_CACHE_AGE_MS
        testedPixelCapture.onPreTraversal(fakeViewUrl)

        // A SurfaceView can never actually be captured (see containsHardwareSurface), so if the
        // cache is (correctly) not reused here, this falls through to a real capture attempt,
        // which fails cleanly for this view type — proving the cache short-circuit was NOT taken.
        val isolationView = mock<SurfaceView>()
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — capture failed (no cache reuse, no successful View.draw), so the wireframe is
        // left exactly as it was, unpopulated by either path
        assertThat(fakeWireframe.resourceId).isEqualTo(resourceIdBeforeCapture)
        verify(mockAsyncJobStatusCallback).jobFinished()
    }

    @Test
    fun `M reuse the stale cache W processPendingCaptures() {capture time budget exhausted}`(forge: Forge) {
        // Given — the per-node check wants a redraw (the entry is past MAX_CACHE_AGE_MS), but the
        // global capture time budget is exhausted — a same-size entry exists to fall back on, so
        // the stale entry is reused rather than drawing (never a placeholder — see PixelCapture's
        // class doc on captureTimeBank, this is what keeps a sustained scroll/animation from
        // forcing a real draw on every redraw MIN_REDRAW_INTERVAL_MS would otherwise allow).
        val nodeId = forge.aPositiveInt().toLong()
        val fakeResourceId = forge.aStringMatching("[a-f0-9]{32}")
        testedPixelCapture.seedCacheForTesting(
            nodeId = nodeId,
            width = fakeIsolationClipRect.width(),
            height = fakeIsolationClipRect.height(),
            resourceId = fakeResourceId,
            capturedAtMs = fakeElapsedRealtimeMs
        )
        fakeElapsedRealtimeMs += PixelCapture.MAX_CACHE_AGE_MS
        testedPixelCapture.onPreTraversal(fakeViewUrl)
        whenever(mockCaptureTimeBank.updateAndCheck(any())).thenReturn(false)

        val isolationView = mock<SurfaceView>()
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { fail("Wireframe should not be replaced by a placeholder") },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — no capture work at all; the (stale) cached resourceId is applied directly
        verify(isolationView, never()).draw(any())
        verifyNoInteractions(mockResourceResolver)
        assertThat(fakeWireframe.resourceId).isEqualTo(fakeResourceId)
        assertThat(fakeWireframe.isEmpty).isFalse()
        verify(mockAsyncJobStatusCallback).jobFinished()
    }

    @Test
    fun `M replace with placeholder W processPendingCaptures() {capture time budget exhausted, no cache yet}`(
        forge: Forge
    ) {
        // Given — the capture time budget is exhausted, and this node has no prior cache entry to
        // fall back on either. Not stalling the host app's UI thread always outranks capture
        // fidelity, with no exception for a first-ever capture — so this must defer exactly like a
        // CAPTURE_BUDGET_MS timeout, never draw.
        whenever(mockCaptureTimeBank.updateAndCheck(any())).thenReturn(false)

        val nodeId = forge.aPositiveInt().toLong()
        val isolationView = mock<SurfaceView>()
        var replacedWireframe: MobileSegment.Wireframe? = null
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { replacedWireframe = it },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — no capture work is attempted at all, not even View.draw
        verifyNoInteractions(isolationView)
        verifyNoInteractions(mockResourceResolver)
        verify(mockAsyncJobStatusCallback).jobFinished()

        val placeholder = replacedWireframe as? MobileSegment.Wireframe.PlaceholderWireframe
        assertThat(placeholder).isNotNull
        assertThat(placeholder!!.id).isEqualTo(nodeId)
        assertThat(placeholder.x).isEqualTo(fakeDpBounds.x)
        assertThat(placeholder.y).isEqualTo(fakeDpBounds.y)
        assertThat(placeholder.width).isEqualTo(fakeDpBounds.width)
        assertThat(placeholder.height).isEqualTo(fakeDpBounds.height)
    }

    @Test
    fun `M replace with placeholder and skip capture W processPendingCaptures() {traversal alone exceeds budget}`(
        forge: Forge
    ) {
        // Given — the budget is anchored at onPreTraversal (start of the whole snapshot cycle,
        // called in set up above), not at the start of this method. Simulate the traversal
        // phase (mapping every other view in the tree) alone taking longer than the budget,
        // before this pending capture is even registered.
        fakeElapsedRealtimeMs += CAPTURE_BUDGET_MS + 50L

        val nodeId = forge.aPositiveInt().toLong()
        val isolationView = mock<SurfaceView>()
        var replacedWireframe: MobileSegment.Wireframe? = null
        testedPixelCapture.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = fakeDpBounds,
            isolationView = isolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { replacedWireframe = it },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — no capture work is attempted at all
        verifyNoInteractions(isolationView)
        verify(mockAsyncJobStatusCallback).jobFinished()

        val placeholder = replacedWireframe as? MobileSegment.Wireframe.PlaceholderWireframe
        assertThat(placeholder).isNotNull
        assertThat(placeholder!!.id).isEqualTo(nodeId)
        assertThat(placeholder.x).isEqualTo(fakeDpBounds.x)
        assertThat(placeholder.y).isEqualTo(fakeDpBounds.y)
        assertThat(placeholder.width).isEqualTo(fakeDpBounds.width)
        assertThat(placeholder.height).isEqualTo(fakeDpBounds.height)
    }

    @Test
    fun `M replace only the captures still pending once the budget is exceeded W processPendingCaptures()`(
        forge: Forge
    ) {
        // Given — the first capture's completion is stubbed to push the clock past the budget,
        // simulating slow processing mid-cycle; the second capture registered afterwards should
        // then be skipped.
        val firstCallback = mock<AsyncJobStatusCallback>()
        whenever(firstCallback.jobFinished()).then {
            fakeElapsedRealtimeMs += CAPTURE_BUDGET_MS + 50L
            Unit
        }
        testedPixelCapture.registerPendingCapture(
            nodeId = 1L,
            dpBounds = fakeDpBounds,
            isolationView = mock<SurfaceView>(),
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = firstCallback
        )

        val skippedNodeId = forge.aPositiveInt().toLong()
        val skippedIsolationView = mock<SurfaceView>()
        val skippedCallback = mock<AsyncJobStatusCallback>()
        var replacedWireframe: MobileSegment.Wireframe? = null
        testedPixelCapture.registerPendingCapture(
            nodeId = skippedNodeId,
            dpBounds = fakeDpBounds,
            isolationView = skippedIsolationView,
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { replacedWireframe = it },
            asyncJobStatusCallback = skippedCallback
        )

        // When
        testedPixelCapture.processPendingCaptures()

        // Then — the second (skipped) capture never attempts any capture work
        verifyNoInteractions(skippedIsolationView)
        verify(skippedCallback).jobFinished()
        assertThat(replacedWireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }

    @Test
    fun `M accumulate debouncer stats across cycles W recordDebouncerStats()`(forge: Forge) {
        // Given — several cycles' worth of Debouncer activity, as WindowsOnDrawListener would
        // feed in right before each onPreTraversal call
        val fakeStats = forge.aList(size = forge.anInt(min = 2, max = 5)) {
            DebouncerHealthStats(
                callCount = forge.anInt(min = 0, max = 20),
                executedCount = forge.anInt(min = 0, max = 20),
                skippedByTimeBankCount = forge.anInt(min = 0, max = 20)
            )
        }

        // When
        fakeStats.forEach { testedPixelCapture.recordDebouncerStats(it) }

        // Then — flushing the health summary (via a navigation-triggered onPreTraversal) must not
        // throw even though debouncer stats were accumulated across multiple cycles beforehand
        assertThatCode {
            testedPixelCapture.onPreTraversal(forge.aStringMatching("https://[a-z]+\\.example/[a-z]+"))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `M clear pending captures and cache W onPreTraversal() {navigation}`(forge: Forge) {
        // Given
        testedPixelCapture.registerPendingCapture(
            nodeId = 1L,
            dpBounds = fakeDpBounds,
            isolationView = mock<SurfaceView>(),
            isolationClipRect = fakeIsolationClipRect,
            wireframe = fakeWireframe,
            wireframeSlot = WireframeSlot { },
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // When — a different URL than the one used in `set up` triggers the navigation branch
        testedPixelCapture.onPreTraversal(forge.aStringMatching("https://[a-z]+\\.example/[a-z]+"))
        testedPixelCapture.processPendingCaptures()

        // Then — jobStarted() (above) is the only interaction; the pending capture was dropped by
        // navigation before processPendingCaptures could reach it, so jobFinished() never fires
        verify(mockAsyncJobStatusCallback).jobStarted()
        verifyNoMoreInteractions(mockAsyncJobStatusCallback)
    }
}
