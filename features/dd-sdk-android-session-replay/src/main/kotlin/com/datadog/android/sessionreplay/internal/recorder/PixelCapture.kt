/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.PixelCaptureCallback
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Implements [PixelCaptureCallback] using **only [View.draw]** — no [android.view.PixelCopy]. Every
 * capture calls `View.draw` on the isolation view, clipped to its region, into a software
 * [Canvas]; this is always isolated from anything drawn on top of it, works on any API level,
 * and needs no window/handler-thread plumbing the way a GPU readback would.
 *
 * "Region" below is used in the same generic sense as [PixelCaptureCallback] — see that interface's
 * doc for exactly what it means concretely today: as of this writing, every region this class
 * captures is one whole view, not an arbitrary sub-rectangle.
 *
 * **Content cache, to keep `View.draw` calls as rare as possible:** each captured region is
 * cached by [PendingPixelCapture.nodeId], keyed alongside the region's pixel **size**. On the next
 * cycle, a cache entry is reused (and `View.draw` skipped) if the region is still the same size
 * and isn't older than its currently-applicable trust window (see below) — a wireframe's
 * on-screen position (x/y) is stored separately from its image content, so a region that only
 * *moved* (e.g. scrolled) without resizing or redrawing still reuses the exact same pixels
 * correctly.
 *
 * [View.isDirty] is cleared by `View.draw(Canvas)` — the exact method this class calls — and set
 * by `invalidate()`, so in principle it directly answers "has this view's drawn content changed
 * since it was last captured," making [MAX_CACHE_AGE_MS] the trust window when it reads false. In
 * practice it's an approximation, not exact dirty-tracking, in two opposite directions:
 * - It can under-report: the same flag is also cleared by the view's own *real*,
 *   hardware-accelerated on-screen render pass, which runs independently of (and is not
 *   synchronized with) this class's own, deliberately debounced capture cadence. A single,
 *   isolated content change can therefore be "consumed" by that real render pass and read back as
 *   clean by the time this class checks it — silently missing a change made once and then left
 *   alone. [MAX_CACHE_AGE_MS] bounds this: whatever [View.isDirty] misses self-corrects within
 *   that window instead of a region being stuck on stale content indefinitely.
 * - It can over-report: a view can invalidate continuously without meaningfully new content each
 *   time — a blinking text cursor invalidates roughly every 500ms whether or not anything else on
 *   screen changed. Treating every `true` reading as an immediate redraw would turn a real,
 *   sensitive signal into a liability, recapturing a whole screen indefinitely for as long as a
 *   field stays focused. [MIN_REDRAW_INTERVAL_MS] is the (shorter) trust window used instead when
 *   [View.isDirty] reads true — still redraws sooner than an unconfirmed region would, just not on
 *   every single cycle.
 *
 * The cache is cleared on navigation (see [onPreTraversal]) since node ids are not guaranteed
 * to mean the same thing across screens.
 *
 * **Capture budget:** the whole snapshot cycle — traversal through every mapper, plus this
 * class's own capture processing — is bounded by [CAPTURE_BUDGET_MS], timed from [onPreTraversal].
 * Any pending capture [processPendingCaptures] hasn't reached once that elapses is replaced with a
 * placeholder instead of being captured, so a slow traversal can't stall the UI thread
 * indefinitely.
 *
 * Separately, [captureTimeBank] bounds real [View.draw] work specifically to
 * [PIXEL_CAPTURE_BUDGET_PER_SEC_MS] per second — [CAPTURE_BUDGET_MS] only protects a single
 * cycle from running long; it says nothing about how often *capture* work (as opposed to cheap
 * traversal work) recurs across many cycles. A view invalidating continuously — an active scroll
 * fling, a continuous animation — can otherwise keep [View.isDirty] warranting a redraw as often
 * as [MIN_REDRAW_INTERVAL_MS] allows, each one competing for time on the same UI thread doing
 * everything else that frame. Deliberately a token bucket, not a "detect and defer" heuristic:
 * there's no reliable, generic way to tell "the user is scrolling" apart from "the screen has a
 * legitimate fast animation" from just an invalidation-frequency signal, and a heuristic that
 * defers indefinitely while "busy" would leave a continuously-animating screen never captured at
 * all. The budget instead only bounds the *rate* — it keeps refilling with elapsed wall-clock time
 * regardless of how continuously busy the screen is, so it never withholds capture work forever,
 * only spreads it out. This gate applies unconditionally, including to a node's first-ever
 * capture: not stalling the host app's UI thread always outranks capture fidelity, with no
 * exception. When the budget is exhausted: a same-size cache entry, if one exists, is reused
 * as-is (a little staler than usual) rather than drawing; otherwise — nothing to fall back on —
 * this capture is deferred exactly like a [CAPTURE_BUDGET_MS] timeout (see [timeoutPending]),
 * self-correcting the same way: the same node is re-registered and re-attempted next cycle.
 *
 * **Diagnostics:** activity since the last summary is tallied silently (see the counters below)
 * and flushed as one Logcat line by [onPreTraversal] — either when navigation is detected, or
 * after [HEALTH_LOG_INTERVAL_MS] elapses on the same screen, whichever comes first. This gives a
 * recurring "is this screen's pipeline actually healthy" signal instead of a one-off "it worked
 * at some point" ping, without flooding Logcat on every snapshot cycle. The logged counts are
 * *event* totals across every snapshot cycle in that window, not counts of distinct views —
 * e.g. 2 views that never resize get drawn once each, then reused from cache on every subsequent
 * cycle while that screen is shown, so cache-reuse count growing far past the draw count across
 * many cycles is the caching optimization working as intended, not missing data. The summary also
 * reports the average whole-snapshot-cycle duration across every cycle in that window — the same
 * [onPreTraversal]-to-end-of-[processPendingCaptures] window [CAPTURE_BUDGET_MS] is checked
 * against, so it directly shows how close to (or past) budget a screen is actually running,
 * not just the cheap tree-walk portion of it.
 */
internal class PixelCapture(
    private val resourceResolver: ResourceResolver,
    // Injectable for testing — SystemClock.elapsedRealtime() is not advanceable in local unit
    // tests (the Android unit-test stub returns a fixed 0L).
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    // Dedicated to real View.draw work specifically — see the class doc's capture-budget section.
    // Separate from CAPTURE_BUDGET_MS (a per-cycle ceiling) and from Debouncer's own RecordingTimeBank
    // (which budgets the whole traversal+capture cycle, not capture work specifically).
    private val captureTimeBank: TimeBank = RecordingTimeBank(PIXEL_CAPTURE_BUDGET_PER_SEC_MS)
) : PixelCaptureCallback {

    private val pendingCaptures = CopyOnWriteArrayList<PendingPixelCapture>()

    /** Last-captured content per nodeId — see the class doc for the reuse/invalidation rules. */
    private val cache = ConcurrentHashMap<Long, CachedRegion>()

    // Tallied since the last health-summary flush (see logHealthSummary) — @UiThread-only, same
    // as every other method here, so no synchronization is needed.
    //
    // captured/cacheReuse are EVENT counts across every snapshot cycle in the window, not counts
    // of distinct views: a screen with, say, 2 unmapped views that never resize gets each of them
    // drawn once (2 total) then reused from cache on every later cycle while that screen is shown
    // — cacheReuseCount growing far past capturedCount across many cycles is the caching
    // optimization working as intended, not a discrepancy. cycleCount (snapshot cycles observed
    // this window) is included in the log specifically so that ratio reads as expected instead
    // of looking like missing data.
    private var capturedCount = 0
    private var cacheReuseCount = 0
    private var budgetThrottledReuseCount = 0
    private var budgetThrottledPlaceholderCount = 0
    private var captureFailureCount = 0
    private var budgetExceededCount = 0
    private var cycleCount = 0

    // The most recent composition-tree build's stats — reported by WindowsOnDrawListener via
    // [recordCompositionTreeStats] right after CompositionTreeBuilder.build() returns, so the
    // next health summary reflects what the current screen is actually producing.
    private var lastCompositionTreeLayerCount = 0
    private var lastCompositionTreeWireframeCount = 0

    // Sum of every whole snapshot cycle's duration (onPreTraversal through the end of
    // processPendingCaptures — the same window CAPTURE_BUDGET_MS is checked against) since the
    // last flush, divided by cycleCount in logHealthSummary for the average. Incremented at the
    // end of processPendingCaptures — including its early-return path, see there — so it always
    // increments exactly once per cycleCount increment, making cycleCount the correct,
    // already-available divisor.
    private var snapshotCycleDurationTotalMs = 0L

    private var lastHealthLogAtMs = 0L

    // Accumulated since the last flush via recordDebouncerStats — see DebouncerHealthStats for
    // what each counts. Reported alongside the rest of this health summary so a low capture
    // cadence is self-diagnosing from Logcat alone, after the fact: no need to catch it live.
    private var debouncerCallCount = 0
    private var debouncerExecutedCount = 0
    private var debouncerSkippedByTimeBankCount = 0

    /**
     * elapsedRealtime at which the current snapshot cycle's [onPreTraversal] was called.
     * Anchors the [CAPTURE_BUDGET_MS] deadline checked in [processPendingCaptures], so the budget
     * covers the **whole** snapshot — traversal through every mapper included — not just the
     * post-traversal capture-processing loop. Both methods are `@UiThread` and always called on
     * the same thread within one snapshot cycle, so no synchronization is needed here.
     */
    private var snapshotCycleStartMs = 0L

    private var lastCapturedViewUrl: String? = null

    /**
     * Must be called **before** the SR traversal. Marks the start of this snapshot cycle for
     * the [CAPTURE_BUDGET_MS] budget. Flushes the health summary — see [logHealthSummary] — when
     * navigation is detected (URL change) or, failing that, once [HEALTH_LOG_INTERVAL_MS] has
     * elapsed since the last flush. On navigation, also clears the content cache, since a
     * nodeId from the previous screen has no guaranteed relationship to the same id here.
     */
    @UiThread
    fun onPreTraversal(currentViewUrl: String?) {
        snapshotCycleStartMs = elapsedRealtimeMs()
        val navigated = currentViewUrl != lastCapturedViewUrl
        if (navigated || elapsedRealtimeMs() - lastHealthLogAtMs >= HEALTH_LOG_INTERVAL_MS) {
            logHealthSummary(navigated)
        }
        cycleCount++
        if (navigated) {
            cache.clear()
            pendingCaptures.clear()
            lastCapturedViewUrl = currentViewUrl
        }
    }

    /**
     * One Logcat line summarizing activity on [lastCapturedViewUrl] (the screen being left, or
     * — if [navigated] is false — the screen still being shown) since the last flush, then
     * resets the counters. [lastCapturedViewUrl] is read *before* [onPreTraversal] updates it,
     * so a "left" summary always describes the screen you were actually just on.
     */
    private fun logHealthSummary(navigated: Boolean) {
        lastHealthLogAtMs = elapsedRealtimeMs()
        val screenLabel = lastCapturedViewUrl ?: "(app start)"
        val verb = if (navigated) "left" else "still on"
        val averageCycleMs = if (cycleCount > 0) snapshotCycleDurationTotalMs / cycleCount else 0
        Log.i(
            CAPTURE_PIPELINE_LOG_TAG,
            "[Health] $verb \"$screenLabel\" — composition tree: $lastCompositionTreeLayerCount " +
                "layer(s)/$lastCompositionTreeWireframeCount wireframe(s), avg cycle ${averageCycleMs}ms " +
                "(budget ${CAPTURE_BUDGET_MS}ms); $debouncerCallCount debounce() call(s) -> " +
                "$debouncerExecutedCount executed, $debouncerSkippedByTimeBankCount skipped by " +
                "Debouncer's own time bank; over $cycleCount snapshot cycle(s): $capturedCount " +
                "drawn via View.draw, $cacheReuseCount reused from cache, $budgetThrottledReuseCount " +
                "throttled by capture budget (stale reuse), $budgetThrottledPlaceholderCount throttled by " +
                "capture budget (placeholder), $captureFailureCount failed, $budgetExceededCount budget-exceeded"
        )
        capturedCount = 0
        cacheReuseCount = 0
        budgetThrottledReuseCount = 0
        budgetThrottledPlaceholderCount = 0
        captureFailureCount = 0
        budgetExceededCount = 0
        cycleCount = 0
        snapshotCycleDurationTotalMs = 0
        debouncerCallCount = 0
        debouncerExecutedCount = 0
        debouncerSkippedByTimeBankCount = 0
    }

    /**
     * Feeds this cycle's [Debouncer] activity into the running totals reported by the next
     * [logHealthSummary] — called once per executed cycle, right before [onPreTraversal], from
     * `WindowsOnDrawListener.runCompositionTreePipeline`. See [DebouncerHealthStats] for what a
     * call-vs-executed gap in the resulting log line implies.
     */
    @UiThread
    fun recordDebouncerStats(stats: DebouncerHealthStats) {
        debouncerCallCount += stats.callCount
        debouncerExecutedCount += stats.executedCount
        debouncerSkippedByTimeBankCount += stats.skippedByTimeBankCount
    }

    /**
     * Records the current screen's composition-tree shape for the next health summary — see
     * [logHealthSummary]. Called by `WindowsOnDrawListener` right after
     * `CompositionTreeBuilder.build()` returns.
     */
    @UiThread
    fun recordCompositionTreeStats(layerCount: Int, wireframeCount: Int) {
        lastCompositionTreeLayerCount = layerCount
        lastCompositionTreeWireframeCount = wireframeCount
    }

    // region PixelCaptureCallback

    /**
     * Registers a pending capture, calling [asyncJobStatusCallback.jobStarted] immediately.
     * The actual capture (or cache reuse) is resolved in [processPendingCaptures].
     */
    override fun registerPendingCapture(
        nodeId: Long,
        dpBounds: GlobalBounds,
        isolationView: View,
        isolationClipRect: Rect,
        wireframe: MobileSegment.Wireframe.ImageWireframe,
        wireframeSlot: WireframeSlot,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        asyncJobStatusCallback.jobStarted()
        pendingCaptures.add(
            PendingPixelCapture(
                nodeId = nodeId,
                dpBounds = dpBounds,
                isolationView = isolationView,
                isolationClipRect = isolationClipRect,
                wireframe = wireframe,
                wireframeSlot = wireframeSlot,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        )
    }

    // endregion

    // region Post-traversal processing

    /**
     * Must be called **after** traversal — the last step of the whole snapshot cycle, so this
     * is also where [snapshotCycleDurationTotalMs] is tallied (including the early-return path
     * below, so a screen with nothing pending doesn't skip contributing its — real, non-zero —
     * traversal time to the average). For each pending capture, in order: if the
     * [CAPTURE_BUDGET_MS] budget for the whole snapshot cycle has been exceeded,
     * [timeoutPending] swaps it for a placeholder instead of attempting a capture (tallied for
     * the next health summary); otherwise [captureOrReuse] either reuses a cached capture or
     * draws a fresh one.
     */
    @UiThread
    fun processPendingCaptures() {
        if (pendingCaptures.isEmpty()) {
            snapshotCycleDurationTotalMs += elapsedRealtimeMs() - snapshotCycleStartMs
            return
        }

        val snapshot = pendingCaptures.toList()
        pendingCaptures.clear()

        val deadlineMs = snapshotCycleStartMs + CAPTURE_BUDGET_MS

        for (pending in snapshot) {
            if (elapsedRealtimeMs() >= deadlineMs) {
                timeoutPending(pending)
                budgetExceededCount++
                continue
            }
            captureOrReuse(pending)
        }

        snapshotCycleDurationTotalMs += elapsedRealtimeMs() - snapshotCycleStartMs
    }

    /**
     * Reuses [cache] when [pending]'s region is still the same size and the entry isn't older
     * than its currently-applicable trust window; otherwise captures a fresh [View.draw] and
     * refreshes the cache entry on success. That window is [MIN_REDRAW_INTERVAL_MS] when
     * [View.isDirty] is true, or the much longer [MAX_CACHE_AGE_MS] when it's false — seeing
     * isDirty true doesn't force an immediate redraw, only a sooner one, so a view invalidating
     * continuously without genuinely new content each time (e.g. a blinking text cursor) doesn't
     * force a full recapture on every single cycle (see the class doc for the full reasoning).
     *
     * Even when the above says a redraw is warranted, [captureTimeBank] gets the final say —
     * unconditionally, regardless of whether this is this node's first-ever capture: not stalling
     * the host app's UI thread always outranks capture fidelity, full stop, so there is no case
     * where drawing is allowed to bypass this budget. When it's exhausted: a same-size cache
     * entry, if one exists, is reused as-is instead of drawing (a little staler than usual, never
     * a placeholder); otherwise this capture is deferred exactly like a [CAPTURE_BUDGET_MS]
     * timeout — [timeoutPending] — since there is nothing to fall back on and drawing anyway is
     * not an option. Both are self-correcting: the same node gets re-registered and re-attempted
     * next cycle, whenever the budget allows it.
     */
    private fun captureOrReuse(pending: PendingPixelCapture) {
        val width = pending.isolationClipRect.width()
        val height = pending.isolationClipRect.height()

        val cached = cache[pending.nodeId]
        val cacheMatchesSize = cached != null && cached.width == width && cached.height == height
        // Only reads View.isDirty (a real interaction, not free) when there's actually a same-size
        // entry to weigh it against — with no cache at all, or a size mismatch, the entry can't be
        // fresh regardless of what isDirty reports, so checking it would be pure waste.
        val cacheIsFresh = cacheMatchesSize &&
            elapsedRealtimeMs() - checkNotNull(cached).capturedAtMs <
            if (pending.isolationView.isDirty) MIN_REDRAW_INTERVAL_MS else MAX_CACHE_AGE_MS
        if (cacheIsFresh) {
            cacheReuseCount++
            applyResourceId(pending, checkNotNull(cached).resourceId)
            return
        }

        if (!captureTimeBank.updateAndCheck(elapsedRealtimeMs())) {
            if (cacheMatchesSize) {
                budgetThrottledReuseCount++
                applyResourceId(pending, checkNotNull(cached).resourceId)
            } else {
                budgetThrottledPlaceholderCount++
                timeoutPending(pending)
            }
            return
        }

        val drawStartMs = elapsedRealtimeMs()
        val bitmap = captureViewRegion(pending.isolationView, pending.isolationClipRect)
        captureTimeBank.consume(elapsedRealtimeMs() - drawStartMs)
        if (bitmap == null) {
            pending.asyncJobStatusCallback.jobFinished()
            return
        }

        capturedCount++

        resourceResolver.resolveResourceIdFromBitmap(
            bitmap = bitmap,
            resourceResolverCallback = object : ResourceResolverCallback {
                override fun onSuccess(resourceId: String) {
                    cache[pending.nodeId] = CachedRegion(width, height, resourceId, elapsedRealtimeMs())
                    applyResourceId(pending, resourceId)
                }

                override fun onFailure() {
                    pending.asyncJobStatusCallback.jobFinished()
                }
            }
        )
    }

    private fun applyResourceId(pending: PendingPixelCapture, resourceId: String) {
        pending.wireframe.resourceId = resourceId
        pending.wireframe.isEmpty = false
        pending.asyncJobStatusCallback.jobFinished()
    }

    /**
     * Replaces [pending]'s stub wireframe with a [MobileSegment.Wireframe.PlaceholderWireframe]
     * via [PendingPixelCapture.wireframeSlot] — used both when [CAPTURE_BUDGET_MS] is exceeded
     * before this capture can be processed, and from [captureOrReuse] when [captureTimeBank] is
     * exhausted and there's no cache entry to fall back on instead. No capture work ([View.draw])
     * is attempted either way — not stalling the host app's UI thread always outranks capture
     * fidelity.
     */
    private fun timeoutPending(pending: PendingPixelCapture) {
        pending.wireframeSlot.replace(
            MobileSegment.Wireframe.PlaceholderWireframe(
                id = pending.nodeId,
                x = pending.dpBounds.x,
                y = pending.dpBounds.y,
                width = pending.dpBounds.width,
                height = pending.dpBounds.height,
                label = CAPTURE_BUDGET_EXCEEDED_LABEL
            )
        )
        pending.asyncJobStatusCallback.jobFinished()
    }

    // endregion

    // region Lifecycle

    fun release() {
        pendingCaptures.clear()
        cache.clear()
    }

    // endregion

    /**
     * Seeds [cache] directly — real [View.draw]/[Bitmap] capture can't run in a plain JVM unit
     * test (no Android graphics runtime), so this is the only way to exercise the cache-reuse
     * branch of [captureOrReuse] deterministically. [capturedAtMs] defaults to "now" (per the
     * injected clock) so a seeded entry reads as fresh unless a test deliberately backdates it
     * to exercise [MAX_CACHE_AGE_MS] expiry.
     */
    @VisibleForTesting
    internal fun seedCacheForTesting(
        nodeId: Long,
        width: Int,
        height: Int,
        resourceId: String,
        capturedAtMs: Long = elapsedRealtimeMs()
    ) {
        cache[nodeId] = CachedRegion(width, height, resourceId, capturedAtMs)
    }

    // region Capture primitives

    /**
     * Captures [sourceView] clipped to [clipRect] (in [sourceView]'s coordinate space), isolated
     * from any overlying content. Returns null if the region can't be captured — the caller
     * leaves the wireframe unresolved (`isEmpty=true`) rather than surfacing a broken image.
     *
     * Uses a software [Canvas] via [View.draw]. Two safeguards, layered:
     * - [containsHardwareSurface] skips capture entirely — before even attempting it — when
     *   [sourceView] hosts a [SurfaceView]/[TextureView]. Those composite through a path
     *   [View.draw] never touches at all; drawing over one throws no exception, it just
     *   silently produces blank content for that region.
     * - [View.draw] also cannot render content backed by a `Bitmap.Config.HARDWARE` bitmap —
     *   the default decode format for Coil, Glide, and similar image loaders on API 26+ —
     *   throwing `IllegalArgumentException` instead; caught below, tallied for the next health
     *   summary, and treated as "can't capture."
     * - [isBitmapLikelyEmpty] is a best-effort check on whatever [View.draw] *did* produce, for
     *   cases the type check doesn't catch (e.g. an unanticipated rendering path). It is not
     *   comprehensive: an opaque background drawn by an ancestor behind a "hole" would not be
     *   flagged, and a legitimately fully-transparent capture would be (incorrectly) discarded.
     */
    private fun captureViewRegion(sourceView: View, clipRect: Rect): Bitmap? {
        if (clipRect.width() <= 0 || clipRect.height() <= 0) return null
        if (containsHardwareSurface(sourceView)) return null

        val drawn = try {
            captureViewRegionViaDraw(sourceView, clipRect)
        } catch (e: IllegalArgumentException) {
            // "Software rendering doesn't support hardware bitmaps" — thrown when the view
            // (or a descendant, e.g. an ImageView showing a Coil/Glide-decoded image) holds
            // a Bitmap.Config.HARDWARE bitmap. There is no way to render that content through
            // a software Canvas. Tallied for the next health summary rather than logged
            // immediately here — see the class doc.
            captureFailureCount++
            null
        }

        if (drawn != null && isBitmapLikelyEmpty(drawn)) {
            drawn.recycle()
            return null
        }

        return drawn
    }

    /**
     * Returns true if [view], or any descendant, is a [SurfaceView] or [TextureView].
     *
     * These composite through a path entirely separate from the normal View/Canvas pipeline —
     * [SurfaceView] punches a hole composited directly by SurfaceFlinger; [TextureView] renders
     * into a [android.graphics.SurfaceTexture]. Neither participates in [View.draw]'s software
     * [Canvas] rendering, so calling draw() on (or above) one produces no content for that
     * region, with no exception to signal the failure.
     *
     * Note: for the Compose isolation path, [view] is the whole host `AndroidComposeView`, not
     * just the target composable — so this check is coarser than ideal there (an unrelated
     * hardware surface elsewhere on the same screen would also trigger it). That only costs
     * leaving that one region unresolved rather than an incorrect result.
     */
    private fun containsHardwareSurface(view: View): Boolean {
        if (view is SurfaceView || view is TextureView) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsHardwareSurface(view.getChildAt(i))) return true
            }
        }
        return false
    }

    /**
     * Samples a sparse grid of pixels in [bitmap] and returns true if every sampled pixel is
     * fully transparent — a signal that nothing was actually drawn into this region. Not
     * comprehensive (see [captureViewRegion] doc for the known gaps); intended as a cheap
     * secondary safety net alongside [containsHardwareSurface], not a replacement for it.
     */
    private fun isBitmapLikelyEmpty(bitmap: Bitmap): Boolean {
        val samplesPerAxis = 8
        val maxIndex = samplesPerAxis - 1
        for (row in 0..maxIndex) {
            for (col in 0..maxIndex) {
                val x = (bitmap.width - 1) * col / maxIndex
                val y = (bitmap.height - 1) * row / maxIndex
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (alpha != 0) return false
            }
        }
        return true
    }

    /**
     * Renders [sourceView] in software clipped to [clipRect]. Captures only what [sourceView]
     * draws — no overlying views, no composited overlays. Throws [IllegalArgumentException]
     * if the view (or a descendant) holds a `Bitmap.Config.HARDWARE` bitmap — see
     * [captureViewRegion] for how that is handled.
     */
    private fun captureViewRegionViaDraw(sourceView: View, clipRect: Rect): Bitmap {
        val bitmap = Bitmap.createBitmap(clipRect.width(), clipRect.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-clipRect.left.toFloat(), -clipRect.top.toFloat())
        sourceView.draw(canvas)
        return bitmap
    }

    // endregion

    /**
     * The cached content for one nodeId — valid as long as the region's size still matches,
     * [View.isDirty] reads false, and [capturedAtMs] isn't older than [MAX_CACHE_AGE_MS].
     */
    private data class CachedRegion(val width: Int, val height: Int, val resourceId: String, val capturedAtMs: Long)

    companion object {
        /**
         * Wall-clock budget for an entire SR snapshot cycle — from [onPreTraversal], through
         * traversal and every mapper, to the end of [processPendingCaptures] — not just the
         * post-traversal capture-processing loop on its own. Pending captures not yet processed once
         * this elapses are replaced with a placeholder instead of being captured, so a slow
         * traversal or a large/slow batch of unmapped views can't stall the UI thread beyond
         * this limit.
         */
        internal const val CAPTURE_BUDGET_MS = 90L

        internal const val CAPTURE_BUDGET_EXCEEDED_LABEL = "Content"

        /**
         * Upper bound on how long a cache entry can be trusted without a real recapture, even if
         * [View.isDirty] reads false — see the class doc's [View.isDirty] section for why that
         * signal alone can miss an isolated content change. Short enough that a missed change is
         * only ever stale for a barely-noticeable window, long enough to preserve almost all of
         * the caching benefit for content that's genuinely unchanged.
         */
        internal const val MAX_CACHE_AGE_MS = 1_000L

        /**
         * How long a cache entry is still trusted even while [View.isDirty] reads true — the
         * floor on redraw frequency for a view that keeps invalidating without necessarily having
         * new content each time (e.g. a blinking text cursor, invalidating roughly every 500ms
         * with nothing meaningfully different about the screen). Without this, [View.isDirty]
         * being a real, sensitive signal becomes a liability: a view that's continuously (but not
         * meaningfully) dirty would force a full recapture on every single cycle indefinitely,
         * rather than the rare one-off case it's meant to catch. Deliberately shorter than
         * [MAX_CACHE_AGE_MS] — a genuinely dirty view should still refresh sooner than a merely
         * unconfirmed one.
         */
        internal const val MIN_REDRAW_INTERVAL_MS = 500L

        /**
         * Per-second budget dedicated to real [View.draw] work specifically, via [captureTimeBank]
         * — separate from [CAPTURE_BUDGET_MS] (a hard per-cycle ceiling) and from `Debouncer`'s own
         * `RecordingTimeBank` (which budgets the whole traversal+capture cycle uniformly, with no
         * notion of "capture work" as its own thing). A `View.draw` call is cheap in isolation
         * (single digits of ms) but happens on the same UI thread doing everything else that
         * frame — a scroll fling or a continuous animation invalidates repeatedly enough that,
         * without this, [View.isDirty] being a real signal would compete for UI-thread time on
         * every redraw MIN_REDRAW_INTERVAL_MS allows, risking a dropped frame each time. This is a
         * token bucket (see [RecordingTimeBank]): it never permanently withholds capture work no
         * matter how continuously busy the screen is — the budget keeps refilling with elapsed
         * wall-clock time regardless of activity — it only bounds the *rate*. This gate is never
         * bypassed, not even for a node's first-ever capture: not stalling the host app's UI
         * thread always outranks capture fidelity. When exhausted, a same-size cache entry, if one
         * exists, is reused as-is rather than drawing; otherwise the capture is deferred exactly
         * like a [CAPTURE_BUDGET_MS] timeout (see [captureOrReuse]).
         */
        internal const val PIXEL_CAPTURE_BUDGET_PER_SEC_MS = 20L

        /**
         * Fallback interval for [logHealthSummary] when the screen never changes — without this,
         * staying on one screen (e.g. to test scrolling) would produce zero diagnostic output.
         */
        internal const val HEALTH_LOG_INTERVAL_MS = 5_000L
    }
}
