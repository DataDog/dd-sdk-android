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
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.PixelCaptureCallback
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

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
    private val captureTimeBank: TimeBank = RecordingTimeBank(PIXEL_CAPTURE_BUDGET_PER_SEC_MS),
    // Experimental, optional: scopes which unmapped views actually contain text — see
    // DefaultTextDetector's doc. Invoked from captureOrReuse only on a freshly-drawn bitmap, never
    // a cache-reused one, so a region already known static isn't re-scanned every cycle.
    private val textDetector: TextDetector? = null,
    // Feeds textDetector's blinking-cursor signal — see BlinkingCursorTracker's doc. Only
    // meaningful alongside textDetector, but kept unconditional (not nulled out with it) since
    // it's cheap regardless and simpler than threading a second nullability check through
    // captureOrReuse.
    private val blinkingCursorTracker: BlinkingCursorTracker = BlinkingCursorTracker()
) : PixelCaptureCallback {

    private val pendingCaptures = CopyOnWriteArrayList<PendingPixelCapture>()

    // Diagnostic-logging-only support — see debugLog/debugLogOnce. Not on the pixel-capture
    // critical path itself (no capture decision reads either of these), so a single background
    // thread and an unbounded (but debug-build-scale) dedup set are an acceptable tradeoff for
    // keeping every debugLog call site's actual Log.d write off the UI thread.
    private val debugLogExecutor = Executors.newSingleThreadExecutor()

    // Every distinct message ever passed to debugLogOnce, so a call site whose message doesn't
    // change cycle-to-cycle (the common case for a static node) only ever reaches Logcat once —
    // see debugLogOnce. Not cleared on navigation: a message that already fired once for a given
    // node/state on an earlier screen visit isn't worth repeating verbatim if that screen is
    // revisited, and this is diagnostic-only, not user-facing telemetry.
    private val loggedOnceMessages: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Last-resolved outcome per nodeId — see the class doc for the reuse/invalidation rules.
     * Covers *both* a successfully uploaded capture and a decision to show a placeholder instead
     * (see [CachedOutcome]/[cachePlaceholderAndApply]'s doc) — either one needs a cache entry so a
     * later cycle can re-apply it synchronously rather than waiting on another async round-trip.
     */
    private val cache = ConcurrentHashMap<Long, CachedOutcome>()

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
     * Round-robin starting index into next cycle's [pendingCaptures] snapshot — see
     * [processPendingCaptures]'s doc for why this exists: without it, [CAPTURE_BUDGET_MS] running
     * out partway through a cycle always times out the *same* tail of nodes (registration order is
     * stable cycle-to-cycle for a static screen), which never draw, never populate [cache], and so
     * never benefit from cache-reuse either — starving indefinitely rather than eventually
     * converging. Advanced, each cycle, to just past the last node this cycle actually reached
     * (drawn or cache-reused, not timed-out-by-budget) — see the end of [processPendingCaptures].
     */
    private var pendingCaptureRotationOffset = 0

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
            blinkingCursorTracker.clear()
            pendingCaptureRotationOffset = 0
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
     * The actual capture (or cache reuse) is resolved in [processPendingCaptures]. Overrides only
     * the privacy-aware overload — [PixelCaptureCallback]'s default implementation of the other
     * one already delegates here with [com.datadog.android.sessionreplay.TextAndInputPrivacy.MASK_SENSITIVE_INPUTS],
     * matching the only privacy level pixel capture is eligible under today.
     */
    override fun registerPendingCapture(
        nodeId: Long,
        dpBounds: GlobalBounds,
        isolationView: View,
        isolationClipRect: Rect,
        wireframe: MobileSegment.Wireframe.ImageWireframe,
        wireframeSlot: WireframeSlot,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy
    ) {
        debugLogOnce(
            "registerPendingCapture: node=$nodeId dpBounds=$dpBounds " +
                "isolationClipRect=$isolationClipRect view=${isolationView.javaClass.name} " +
                "textAndInputPrivacy=$textAndInputPrivacy imagePrivacy=$imagePrivacy"
        )
        asyncJobStatusCallback.jobStarted()
        pendingCaptures.add(
            PendingPixelCapture(
                nodeId = nodeId,
                dpBounds = dpBounds,
                isolationView = isolationView,
                isolationClipRect = isolationClipRect,
                wireframe = wireframe,
                wireframeSlot = wireframeSlot,
                asyncJobStatusCallback = asyncJobStatusCallback,
                textAndInputPrivacy = textAndInputPrivacy,
                imagePrivacy = imagePrivacy
            )
        )
    }

    /**
     * Synchronous fast path for `PixelCaptureFallbackMapper`: true if [cache] still holds a fresh
     * [CachedOutcome.Placeholder] decision for [nodeId] at this size — i.e. this node was already
     * decided, on a *previous* cycle, to need a placeholder rather than an uploaded capture.
     *
     * This exists because [CompositionTreeBuilder] copies each leaf's wireframe list into its own
     * combined list (`wireframes.addAll(leafWireframes)`) as soon as that leaf is visited — well
     * before [processPendingCaptures] (called once, after the *entire* traversal/`build()` call
     * has already returned) could ever swap a stub for a placeholder via [WireframeSlot]. That
     * swap only ever reaches the *leaf-local* list, which by then has already been copied from —
     * mutating it has no effect on what [CompositionTreeBuilder.build] actually returns. An
     * uploaded capture doesn't have this problem: [applyResourceId] mutates the *same*
     * [MobileSegment.Wireframe.ImageWireframe] instance that was already copied by reference, so
     * the change is visible regardless of which list holds it. A placeholder can't use that trick
     * — [MobileSegment.Wireframe.PlaceholderWireframe] is a different concrete type, not a
     * mutable-field variant of the same one — so the only way it can ever reach the tree
     * `CompositionTreeBuilder` actually returns is for the mapper to emit it directly, from the
     * start, before any copying happens. [PixelCaptureFallbackMapper] calls this before deciding
     * what to emit for [nodeId] this cycle; on a hit, it constructs the placeholder wireframe
     * itself and skips [registerPendingCapture] entirely — no pending capture, no ImageWireframe
     * stub, nothing left to race. The decision naturally re-verifies once [cache]'s
     * [MAX_CACHE_AGE_MS]/[MIN_REDRAW_INTERVAL_MS] trust window lapses, the same as any other
     * cached outcome.
     */
    @UiThread
    internal fun hasFreshPlaceholderDecision(nodeId: Long, width: Int, height: Int, isDirty: Boolean): Boolean {
        val cached = cache[nodeId] as? CachedOutcome.Placeholder ?: return false
        if (cached.width != width || cached.height != height) return false
        val ageMs = elapsedRealtimeMs() - cached.capturedAtMs
        return ageMs < if (isDirty) MIN_REDRAW_INTERVAL_MS else MAX_CACHE_AGE_MS
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
     *
     * Processed in **round-robin order**, not [pendingCaptures]' registration order — see
     * [pendingCaptureRotationOffset]'s doc. [CAPTURE_BUDGET_MS] can only ever run out once, and
     * once it does, `elapsedRealtimeMs() >= deadlineMs` stays true for every remaining item in
     * this same pass (time doesn't go backwards) — so processing in a fixed order always times
     * out the same tail every cycle on a busy screen. Starting from a different index each cycle
     * spreads that timeout tail across different nodes over time instead, letting every node
     * eventually get its one real [View.draw] and populate [cache] — after which it's a cheap
     * cache-reuse hit on every later cycle regardless of processing order, so this rotation only
     * needs to matter for a node's *first* resolution, not indefinitely.
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
        val size = snapshot.size
        val startIndex = pendingCaptureRotationOffset % size
        val rotated = ArrayList<PendingPixelCapture>(size)
        for (i in 0 until size) rotated.add(snapshot[(startIndex + i) % size])
        var reachedCount = 0

        // Every index this pass has already resolved — either directly, or as another member of
        // an earlier index's batched group (see below) — so the loop can skip straight past it
        // when it reaches that original position.
        val handled = BooleanArray(size)

        // Deliberately still ONE index at a time, in rotation order — not "classify everything,
        // then draw everything" — so CAPTURE_BUDGET_MS is rechecked against real elapsed time
        // right before each unit of real work (a single draw or a shared-group draw), the same
        // way it always was before batching existed. A slow capture's own real work (or, in a
        // test, a stubbed side effect on its AsyncJobStatusCallback) can push the clock past
        // budget for everything still to come — that must be visible to the *next* index's check,
        // not decided in advance from a snapshot of "was it over budget before any drawing began."
        for (i in 0 until size) {
            if (handled[i]) continue
            val pending = rotated[i]
            if (elapsedRealtimeMs() >= deadlineMs) {
                timeoutPending(pending)
                budgetExceededCount++
                handled[i] = true
                continue
            }
            reachedCount++
            handled[i] = true

            val cached = freshCacheEntry(pending)
            if (cached != null) {
                cacheReuseCount++
                applyCachedOutcome(pending, cached)
                continue
            }

            // This index needs a real capture — look ahead (not yet handled, not yet visited by
            // the outer loop, so nothing has drawn or consumed time since pending's own check
            // above) for every other pending node sharing pending.isolationView that also needs
            // one, so they can share a single View.draw instead of one each — see
            // captureSharedRegions' doc for why. A look-ahead candidate's own cache freshness is
            // still checked (cheap, no drawing involved) so a same-view node that's actually a
            // cache hit isn't needlessly pulled into the group.
            val group = mutableListOf(pending)
            for (j in i + 1 until size) {
                if (handled[j]) continue
                val candidate = rotated[j]
                if (candidate.isolationView !== pending.isolationView) continue
                if (freshCacheEntry(candidate) != null) continue
                group.add(candidate)
                handled[j] = true
                reachedCount++
            }

            if (group.size == 1) {
                captureSingle(pending)
            } else {
                captureSharedRegions(pending.isolationView, group)
            }
        }
        pendingCaptureRotationOffset = (startIndex + reachedCount) % size

        snapshotCycleDurationTotalMs += elapsedRealtimeMs() - snapshotCycleStartMs
    }

    /**
     * [pending]'s current [cache] entry if [pending]'s region is still the same size and the
     * entry isn't older than its currently-applicable trust window — [MIN_REDRAW_INTERVAL_MS] when
     * [View.isDirty] is true, or the much longer [MAX_CACHE_AGE_MS] when it's false — seeing
     * isDirty true doesn't force an immediate redraw, only a sooner one, so a view invalidating
     * continuously without genuinely new content each time (e.g. a blinking text cursor) doesn't
     * force a full recapture on every single cycle (see the class doc for the full reasoning).
     * Null otherwise, meaning a real capture is needed.
     */
    private fun freshCacheEntry(pending: PendingPixelCapture): CachedOutcome? {
        val width = pending.isolationClipRect.width()
        val height = pending.isolationClipRect.height()
        val cached = cache[pending.nodeId] ?: return null
        if (cached.width != width || cached.height != height) return null
        val ageMs = elapsedRealtimeMs() - cached.capturedAtMs
        // Only reads View.isDirty (a real interaction, not free) once a same-size entry is
        // already known to exist — with no cache at all, or a size mismatch, checking it first
        // would be pure waste since freshness is already decided regardless of what it reports.
        val isDirty = pending.isolationView.isDirty
        return if (ageMs < if (isDirty) MIN_REDRAW_INTERVAL_MS else MAX_CACHE_AGE_MS) cached else null
    }

    /**
     * [captureTimeBank]'s exhausted-budget fallback, shared by [captureSingle] and
     * [captureSharedRegions] — a same-size cache entry, if one exists, is reused as-is (a little
     * staler than usual, never a placeholder) rather than drawing; otherwise this capture is
     * deferred exactly like a [CAPTURE_BUDGET_MS] timeout — [timeoutPending] — since there is
     * nothing to fall back on and drawing anyway is not an option. Both are self-correcting: the
     * same node gets re-registered and re-attempted next cycle, whenever the budget allows it.
     */
    private fun applyCaptureBudgetExhausted(pending: PendingPixelCapture) {
        val width = pending.isolationClipRect.width()
        val height = pending.isolationClipRect.height()
        val cached = cache[pending.nodeId]
        if (cached != null && cached.width == width && cached.height == height) {
            budgetThrottledReuseCount++
            applyCachedOutcome(pending, cached)
        } else {
            budgetThrottledPlaceholderCount++
            timeoutPending(pending)
        }
    }

    /**
     * The only pending capture needing a real draw for its [PendingPixelCapture.isolationView]
     * this cycle — captures just its own (typically small) [PendingPixelCapture.isolationClipRect]
     * directly, exactly as before batching existed. Not worth the overhead of a full-View draw
     * (see [captureSharedRegions]) when there's nothing else to share it with.
     *
     * [captureTimeBank] gets the final say on whether to draw at all — unconditionally, regardless
     * of whether this is this node's first-ever capture: not stalling the host app's UI thread
     * always outranks capture fidelity, full stop, so there is no case where drawing is allowed to
     * bypass this budget.
     */
    private fun captureSingle(pending: PendingPixelCapture) {
        val width = pending.isolationClipRect.width()
        val height = pending.isolationClipRect.height()

        if (!captureTimeBank.updateAndCheck(elapsedRealtimeMs())) {
            applyCaptureBudgetExhausted(pending)
            return
        }

        val drawStartMs = elapsedRealtimeMs()
        val bitmap = captureViewRegion(pending.isolationView, pending.isolationClipRect)
        captureTimeBank.consume(elapsedRealtimeMs() - drawStartMs)
        if (bitmap == null) {
            debugLogOnce(
                "captureSingle: captureViewRegion returned null, node=${pending.nodeId} " +
                    "view=${pending.isolationView.javaClass.name} size=${width}x$height — wireframe " +
                    "stays isEmpty=true this cycle"
            )
            pending.asyncJobStatusCallback.jobFinished()
            return
        }

        finishCapture(pending, bitmap, width, height)
    }

    /**
     * Two or more pending captures sharing the same [view] this cycle — captured with **one**
     * [View.draw] of the whole [view] (not each member's own small region) into a single bitmap,
     * then each member's own region is cropped out of that one bitmap via [Bitmap.createBitmap]
     * (a pixel copy, not another draw). This is the whole reason batching exists: before it, a
     * Compose screen decomposing into N separate small leaf captures meant N separate full-tree
     * `View.draw` passes on the same shared host View each cycle (translated/clipped down
     * afterward, but the draw itself still walks the *entire* composition every time) — confirmed
     * on-device to risk corrupting a stateful [androidx.compose.ui.graphics.painter.Painter]'s own
     * rendering state (Coil's `AsyncImagePainter` reading a spurious size from an SDK-injected
     * extra draw pass), the more often it happens the more likely it manifests. One shared, full
     * (not degenerately small) draw per cycle per host View — instead of one per leaf — cuts that
     * risk back down regardless of how many leaves this screen decomposes into.
     *
     * [captureTimeBank] gates the one shared draw exactly as [captureSingle] gates its own single
     * draw — if exhausted, every member falls back to [applyCaptureBudgetExhausted] individually
     * (some may have their own stale cache entry to reuse, others may not).
     */
    private fun captureSharedRegions(view: View, group: List<PendingPixelCapture>) {
        if (!captureTimeBank.updateAndCheck(elapsedRealtimeMs())) {
            group.forEach { applyCaptureBudgetExhausted(it) }
            return
        }

        val drawStartMs = elapsedRealtimeMs()
        val shared = captureViewRegion(view, Rect(0, 0, view.width, view.height))
        captureTimeBank.consume(elapsedRealtimeMs() - drawStartMs)

        if (shared == null) {
            group.forEach { pending ->
                debugLogOnce(
                    "captureSharedRegions: shared capture returned null, node=${pending.nodeId} " +
                        "view=${view.javaClass.name} — wireframe stays isEmpty=true this cycle"
                )
                pending.asyncJobStatusCallback.jobFinished()
            }
            return
        }

        group.forEach { pending -> applySharedCapture(pending, shared) }
        shared.recycle()
    }

    /**
     * Crops [pending]'s own region out of [shared] (see [captureSharedRegions]) and finishes it
     * exactly as [captureSingle] would with its own directly-drawn bitmap. [Rect.intersect] guards
     * against [PendingPixelCapture.isolationClipRect] extending beyond [shared]'s bounds — it
     * shouldn't in practice (both derive from the same View's own coordinate space), but
     * [Bitmap.createBitmap] throws on an out-of-bounds subset rather than clamping, and a stale
     * bounds mismatch is far cheaper to leave unresolved for one cycle than to crash on.
     */
    private fun applySharedCapture(pending: PendingPixelCapture, shared: Bitmap) {
        val bounded = Rect(pending.isolationClipRect)
        if (!bounded.intersect(0, 0, shared.width, shared.height) || bounded.isEmpty) {
            debugLogOnce(
                "applySharedCapture: node=${pending.nodeId} clipRect=${pending.isolationClipRect} " +
                    "outside shared bitmap bounds (${shared.width}x${shared.height}) — wireframe " +
                    "stays isEmpty=true this cycle"
            )
            pending.asyncJobStatusCallback.jobFinished()
            return
        }

        val bitmap = Bitmap.createBitmap(shared, bounded.left, bounded.top, bounded.width(), bounded.height())
        if (isBitmapLikelyEmpty(bitmap)) {
            debugLogOnce(
                "applySharedCapture: node=${pending.nodeId} cropped region is fully transparent " +
                    "(isBitmapLikelyEmpty) — wireframe stays isEmpty=true this cycle"
            )
            bitmap.recycle()
            pending.asyncJobStatusCallback.jobFinished()
            return
        }

        finishCapture(pending, bitmap, bounded.width(), bounded.height())
    }

    /**
     * Common tail of [captureSingle]/[applySharedCapture] once a real, non-empty [bitmap] for
     * [pending] is in hand — tallies it, feeds [blinkingCursorTracker], and either uploads it
     * directly or routes it through [textDetector] first, exactly as this used to be inlined at
     * the end of the pre-batching `captureOrReuse`.
     */
    private fun finishCapture(pending: PendingPixelCapture, bitmap: Bitmap, width: Int, height: Int) {
        capturedCount++
        val looksLikeBlinkingCursor = blinkingCursorTracker.recordFreshCapture(pending.nodeId, elapsedRealtimeMs())

        val detector = textDetector
        if (detector == null) {
            resolveAndCache(pending, bitmap, width, height)
        } else {
            // Text detection must run to completion — masking any region privacy requires,
            // directly on this same bitmap (see TextDetector's doc) — before the resolver ever
            // sees it. Calling both at once, the way this used to purely for logging, would race:
            // the resolver could compress and upload the bitmap before a mask is ever painted onto it.
            detector.detectText(
                bitmap,
                pending.nodeId,
                looksLikeBlinkingCursor,
                pending.textAndInputPrivacy,
                pending.imagePrivacy
            ) { outcome ->
                when (outcome) {
                    is CaptureOutcome.Upload -> resolveAndCache(pending, outcome.bitmap, width, height)
                    CaptureOutcome.ReplaceWithPlaceholder -> cachePlaceholderAndApply(pending, width, height)
                }
            }
        }
    }

    /**
     * Applies [cached]'s outcome to [pending] — the exact same result this node resolved to last
     * time, without waiting on another [View.draw]/detection round-trip. Synchronous, unlike the
     * first time an outcome is decided (via [resolveAndCache]/[cachePlaceholderAndApply], both
     * off [TextDetector.detectText]'s async callback): this is what makes a cache hit self-correct
     * within the *same* cycle instead of one cycle late — see [cache]'s doc for why that
     * distinction is what actually matters here.
     */
    private fun applyCachedOutcome(pending: PendingPixelCapture, cached: CachedOutcome) {
        when (cached) {
            is CachedOutcome.Uploaded -> applyResourceId(pending, cached.resourceId)
            is CachedOutcome.Placeholder -> replaceWithImagePrivacyPlaceholder(pending)
        }
    }

    /**
     * Hands [bitmap] off to [resourceResolver] for compression/upload, caching the result under
     * [pending]'s nodeId on success. Called either directly from [captureOrReuse] (no
     * [textDetector] configured) or from [TextDetector.detectText]'s completion callback — either
     * way, [bitmap] here is whatever text detection decided to hand back (masked or untouched),
     * never a stale reference to what [captureViewRegion] originally produced.
     */
    private fun resolveAndCache(pending: PendingPixelCapture, bitmap: Bitmap, width: Int, height: Int) {
        debugLogOnce(
            "resolveAndCache: handing bitmap (${bitmap.width}x${bitmap.height}px) to resourceResolver, " +
                "node=${pending.nodeId} isolationSize=${width}x$height"
        )
        resourceResolver.resolveResourceIdFromBitmap(
            bitmap = bitmap,
            resourceResolverCallback = object : ResourceResolverCallback {
                override fun onSuccess(resourceId: String) {
                    debugLogOnce("resolveAndCache: onSuccess, node=${pending.nodeId} resourceId=$resourceId")
                    dumpBitmapForDebugging(pending, resourceId, bitmap)
                    cache[pending.nodeId] = CachedOutcome.Uploaded(width, height, resourceId, elapsedRealtimeMs())
                    applyResourceId(pending, resourceId)
                }

                override fun onFailure() {
                    debugLogOnce(
                        "resolveAndCache: onFailure, node=${pending.nodeId} — wireframe stays " +
                            "isEmpty=true, nothing cached"
                    )
                    pending.asyncJobStatusCallback.jobFinished()
                }
            }
        )
    }

    /**
     * Caches the placeholder decision under [pending]'s nodeId — mirroring [resolveAndCache]'s own
     * caching of a successful upload — before applying it to *this* cycle's wireframe. Without
     * this, every single cycle would re-decide from scratch: [captureOrReuse] never short-circuits
     * to a cache hit, so it always re-draws and re-runs (async) detection, and by the time that
     * resolves, this cycle's own wireframe list has already been superseded by a newer one — the
     * replacement lands on a discarded object and is silently lost, forever, every cycle. Caching
     * the decision here means the *next* cycle's [captureOrReuse] finds it via [cache] and applies
     * it through [applyCachedOutcome] synchronously instead, the same way a cached upload already
     * did — self-correcting from the second cycle on rather than never at all.
     */
    private fun cachePlaceholderAndApply(pending: PendingPixelCapture, width: Int, height: Int) {
        debugLogOnce("cachePlaceholderAndApply: node=${pending.nodeId} size=${width}x$height")
        cache[pending.nodeId] = CachedOutcome.Placeholder(width, height, elapsedRealtimeMs())
        replaceWithImagePrivacyPlaceholder(pending)
    }

    private fun applyResourceId(pending: PendingPixelCapture, resourceId: String) {
        debugLogOnce("applyResourceId: node=${pending.nodeId} resourceId=$resourceId")
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
        debugLogOnce(
            "timeoutPending: node=${pending.nodeId} bounds=${pending.dpBounds} — " +
                "\"$CAPTURE_BUDGET_EXCEEDED_LABEL\" placeholder (budget exceeded, no capture attempted)"
        )
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

    /**
     * Replaces [pending]'s stub wireframe with a placeholder, the same mechanism as
     * [timeoutPending] but for a different reason: [TextDetector.detectText] decided this
     * capture's content requires it — [ImagePrivacy.MASK_ALL] found non-text content within it
     * (see [ImageContentDetector]) — rather than the capture budget running out. Reuses
     * [DefaultImageWireframeHelper.MASK_ALL_CONTENT_LABEL] —
     * the same label [ImagePrivacy.MASK_ALL] already uses for a masked image everywhere else in
     * SR — so this reads consistently in a session replay regardless of which path produced it.
     */
    private fun replaceWithImagePrivacyPlaceholder(pending: PendingPixelCapture) {
        debugLogOnce(
            "replaceWithImagePrivacyPlaceholder: node=${pending.nodeId} bounds=${pending.dpBounds} — " +
                "\"${DefaultImageWireframeHelper.MASK_ALL_CONTENT_LABEL}\" placeholder"
        )
        pending.wireframeSlot.replace(
            MobileSegment.Wireframe.PlaceholderWireframe(
                id = pending.nodeId,
                x = pending.dpBounds.x,
                y = pending.dpBounds.y,
                width = pending.dpBounds.width,
                height = pending.dpBounds.height,
                label = DefaultImageWireframeHelper.MASK_ALL_CONTENT_LABEL
            )
        )
        pending.asyncJobStatusCallback.jobFinished()
    }

    // endregion

    // region Lifecycle

    fun release() {
        pendingCaptures.clear()
        cache.clear()
        textDetector?.release()
        debugLogExecutor.shutdown()
    }

    // endregion

    // Deliberately kept in (not removed after investigation) at the user's explicit request —
    // see the git history/PR discussion for the "full screen broken images" investigation this
    // was added for. android.util.Log rather than InternalLogger: this is meant to be read
    // straight off Logcat on a real device/app while reproducing the issue, not routed through
    // the SDK's own telemetry/user-facing channels.
    //
    // The actual Log.d call (a syscall into logd, not free) is dispatched onto
    // debugLogExecutor rather than made inline here — every caller of this method runs on the
    // UI thread, inside the same CAPTURE_BUDGET_MS window this class is trying to protect, so a
    // synchronous log write on that thread competes with capture work for the same budget it's
    // diagnosing. Ordering across threads is not preserved (Logcat's own timestamps disambiguate),
    // which is an acceptable tradeoff for diagnostic-only output.
    private fun debugLog(message: String) {
        debugLogExecutor.execute { android.util.Log.d(DEBUG_LOG_TAG, "[PixelCapture] $message") }
    }

    /**
     * Same as [debugLog], but only the first time [message] is seen — see [loggedOnceMessages]'s
     * doc. Used at call sites that would otherwise repeat the identical line every single snapshot
     * cycle for a node whose state hasn't changed (the overwhelming common case for a static
     * screen) — keeping the log statement itself (per the standing "never remove" instruction)
     * while not flooding Logcat with hundreds of identical lines per second.
     */
    private fun debugLogOnce(message: String) {
        if (loggedOnceMessages.add(message)) {
            debugLog(message)
        }
    }

    // Every resourceId this process has already dumped to disk for visual inspection — see
    // dumpBitmapForDebugging. Keeps this a one-shot-per-distinct-resource operation (a static
    // icon's bitmap is identical across cycles, same resourceId every time) rather than writing
    // the same PNG to disk repeatedly.
    private val dumpedResourceIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Deliberately kept in (not removed after investigation) at the user's explicit request —
     * see the git history/PR discussion for the "full screen broken images" investigation this
     * was added for. Writes [bitmap] — the *exact* pixels handed to [resourceResolver] for
     * [resourceId] — to this app's own external files dir as a plain PNG, so it can be pulled
     * off-device and inspected directly (`adb pull`). Every other signal available in this
     * investigation (onSuccess firing, isBitmapLikelyEmpty passing, a resource getting
     * enqueued/uploaded) only proves the pipeline *believes* it captured something real —
     * isBitmapLikelyEmpty in particular only checks the alpha channel, so a fully-opaque bitmap
     * that's just the surrounding background color with no actual icon glyph in it (a legitimate
     * possibility if the real content hadn't finished composing/laying out at the exact instant
     * the shared View.draw happened — see captureSharedRegions' doc) would pass every check here
     * and still render as a blank square in the replay. This is the only way to settle that by
     * looking at the actual pixels instead of inferring from logs.
     */
    private fun dumpBitmapForDebugging(pending: PendingPixelCapture, resourceId: String, bitmap: Bitmap) {
        if (!dumpedResourceIds.add(resourceId)) return
        try {
            val dir = pending.isolationView.context.getExternalFilesDir(null) ?: return
            val file = java.io.File(dir, "sr_debug_$resourceId.png")
            java.io.FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            debugLog(
                "dumpBitmapForDebugging: node=${pending.nodeId} resourceId=$resourceId " +
                    "wrote ${file.absolutePath} (${bitmap.width}x${bitmap.height}px)"
            )
        } catch (e: Exception) {
            debugLog(
                "dumpBitmapForDebugging: node=${pending.nodeId} resourceId=$resourceId failed: " +
                    "${e.javaClass.simpleName}(${e.message})"
            )
        }
    }

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
        cache[nodeId] = CachedOutcome.Uploaded(width, height, resourceId, capturedAtMs)
    }

    /** Same as [seedCacheForTesting], but for the placeholder-decision branch of [cache]. */
    @VisibleForTesting
    internal fun seedPlaceholderCacheForTesting(
        nodeId: Long,
        width: Int,
        height: Int,
        capturedAtMs: Long = elapsedRealtimeMs()
    ) {
        cache[nodeId] = CachedOutcome.Placeholder(width, height, capturedAtMs)
    }

    // region Capture primitives

    /**
     * Captures [sourceView] clipped to [clipRect] (in [sourceView]'s coordinate space), isolated
     * from any overlying content. Returns null if the region can't be captured — the caller
     * leaves the wireframe unresolved (`isEmpty=true`) rather than surfacing a broken image.
     *
     * Uses a software [Canvas] via [View.draw] — specifically [HardwareBitmapConvertingCanvas],
     * which converts any `Bitmap.Config.HARDWARE` bitmap (the default decode format for Coil,
     * Glide, and similar image loaders on API 26+) to a CPU-readable copy as it's drawn, instead
     * of a plain [Canvas]'s `IllegalArgumentException`. That still isn't exhaustive — content
     * promoted onto its own cached hardware layer (a `RenderNode` played back as a single opaque
     * call, bypassing per-bitmap interception) can still throw — so this stays layered:
     * - [containsHardwareSurface] skips capture entirely — before even attempting it — when
     *   [sourceView] hosts a [SurfaceView]/[TextureView]. Those composite through a path
     *   [View.draw] never touches at all; drawing over one throws no exception, it just
     *   silently produces blank content for that region.
     * - Whatever [HardwareBitmapConvertingCanvas] doesn't catch still throws
     *   `IllegalArgumentException`; caught below, tallied for the next health summary, and
     *   treated as "can't capture" — the same fallback as before, just for a narrower set of cases.
     * - [isBitmapLikelyEmpty] is a best-effort check on whatever [View.draw] *did* produce, for
     *   cases the type check doesn't catch (e.g. an unanticipated rendering path) — see that
     *   method's doc for why it scans every real pixel rather than a downscaled thumbnail.
     */
    private fun captureViewRegion(sourceView: View, clipRect: Rect): Bitmap? {
        if (clipRect.width() <= 0 || clipRect.height() <= 0) {
            debugLogOnce(
                "captureViewRegion: clipRect has zero/negative size ($clipRect), " +
                    "view=${sourceView.javaClass.name}"
            )
            return null
        }
        if (containsHardwareSurface(sourceView)) {
            debugLogOnce(
                "captureViewRegion: contains a SurfaceView/TextureView, skipping capture, " +
                    "view=${sourceView.javaClass.name} clipRect=$clipRect"
            )
            return null
        }

        val drawn = try {
            captureViewRegionViaDraw(sourceView, clipRect)
        } catch (e: IllegalArgumentException) {
            // "Software rendering doesn't support hardware bitmaps" — thrown when the view
            // (or a descendant, e.g. an ImageView showing a Coil/Glide-decoded image) holds
            // a Bitmap.Config.HARDWARE bitmap. There is no way to render that content through
            // a software Canvas. Tallied for the next health summary rather than logged
            // immediately here — see the class doc.
            debugLogOnce(
                "captureViewRegion: View.draw threw ${e.javaClass.simpleName}(${e.message}), " +
                    "view=${sourceView.javaClass.name} clipRect=$clipRect"
            )
            captureFailureCount++
            null
        }

        if (drawn != null && isBitmapLikelyEmpty(drawn)) {
            debugLogOnce(
                "captureViewRegion: drawn bitmap is fully transparent (isBitmapLikelyEmpty), " +
                    "view=${sourceView.javaClass.name} clipRect=$clipRect " +
                    "bitmapSize=${drawn.width}x${drawn.height}"
            )
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
     * Returns true if every pixel in [bitmap] is fully transparent — a signal that nothing was
     * actually drawn into this region. Not comprehensive (see [captureViewRegion] doc for the
     * known gaps); intended as a cheap secondary safety net alongside [containsHardwareSurface],
     * not a replacement for it.
     *
     * Reads every pixel via one bulk [Bitmap.getPixels] call rather than downscaling to a
     * thumbnail first: an earlier version of this check used `Bitmap.createScaledBitmap(...,
     * filter = true)` to shrink the capture to a small grid before inspecting it, on the theory
     * that averaging every source pixel into the thumbnail would guarantee any non-transparent
     * content anywhere in the bitmap shows up in at least one thumbnail pixel. That guarantee
     * doesn't actually hold — `createScaledBitmap`'s bilinear filter samples only the 2x2 source
     * texels nearest each output pixel, it does not average an output cell's full source region —
     * so real, sparse content (confirmed on-device: a nav bar item's icon+label filling roughly
     * 3% of its full capture bounds) can have every one of those sample points land in the
     * surrounding transparent gaps and be misreported as empty. Scanning every real pixel has no
     * such gap; the one-time bulk read keeps it cheap despite the larger pixel count.
     */
    private fun isBitmapLikelyEmpty(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.none { (it ushr 24) != 0 }
    }

    /**
     * Renders [sourceView] in software clipped to [clipRect]. Captures only what [sourceView]
     * draws — no overlying views, no composited overlays. Uses [HardwareBitmapConvertingCanvas]
     * so most `Bitmap.Config.HARDWARE` content renders correctly instead of throwing; still can
     * throw [IllegalArgumentException] for hardware bitmaps that Canvas doesn't catch — see
     * [captureViewRegion] for how that remaining case is handled.
     */
    private fun captureViewRegionViaDraw(sourceView: View, clipRect: Rect): Bitmap {
        val bitmap = Bitmap.createBitmap(clipRect.width(), clipRect.height(), Bitmap.Config.ARGB_8888)
        val canvas = HardwareBitmapConvertingCanvas(bitmap)
        canvas.translate(-clipRect.left.toFloat(), -clipRect.top.toFloat())
        sourceView.draw(canvas)
        return bitmap
    }

    // endregion

    /**
     * The last-resolved outcome for one nodeId — valid as long as the region's size still
     * matches, [View.isDirty] reads false, and [capturedAtMs] isn't older than [MAX_CACHE_AGE_MS].
     */
    private sealed class CachedOutcome {
        abstract val width: Int
        abstract val height: Int
        abstract val capturedAtMs: Long

        data class Uploaded(
            override val width: Int,
            override val height: Int,
            val resourceId: String,
            override val capturedAtMs: Long
        ) : CachedOutcome()

        data class Placeholder(
            override val width: Int,
            override val height: Int,
            override val capturedAtMs: Long
        ) : CachedOutcome()
    }

    companion object {
        // Shared literally (not a cross-file constant) with PixelCaptureFallbackMapper.kt/
        // CompositionTreeBuilder.kt's own debug logging added for the same investigation — kept
        // as a plain string in each file rather than introducing a shared dependency just for a
        // log tag.
        private const val DEBUG_LOG_TAG = "DD_SessionReplay"

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
