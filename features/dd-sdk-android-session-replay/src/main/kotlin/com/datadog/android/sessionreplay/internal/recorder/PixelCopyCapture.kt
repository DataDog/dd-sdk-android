/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.PixelCropCallback
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Implements [PixelCropCallback] with a **mixed PixelCopy + isolation strategy**.
 *
 * One full-window [PixelCopy] is fired per SR snapshot cycle (O(1) GPU readback).
 * For each pending crop, [processPendingCrops] checks whether any other wireframe
 * is physically rendered above the target region:
 *
 * - **No overlay:** crop from the stored PixelCopy bitmap — full fidelity (parent alpha,
 *   transforms, hardware effects all included because the bitmap came from the
 *   GPU-composited output).
 *
 * - **Overlay detected:** captures the isolation view's own rendering via [View.draw] into a
 *   software [Canvas], clipped to the target region — no overlay contamination. Parent
 *   effects are not included, but the region was being occluded anyway. [View.draw] cannot
 *   render content backed by a `Bitmap.Config.HARDWARE` bitmap (the default decode format
 *   for Coil, Glide, and similar image loaders on API 26+); when that happens, a best-effort
 *   fallback to a PixelCopy crop of the stored bitmap is used instead of a broken image.
 *
 * **Navigation invalidation:** [onPreTraversal] discards the stored bitmap and starts a
 * settle timer on URL change, preventing stale pixels from a previous screen.
 *
 * **Per-node settle window:** the stored PixelCopy bitmap is always at least one snapshot
 * cycle behind live view state — this is invisible for static content, but visible for a
 * view whose pixel content changes without a corresponding bounds/navigation event (e.g. an
 * async image finishing its load). [registerPendingCrop] records when each node was first
 * seen; [processPendingCrops] forces the isolation path (always live, never stale) for a
 * node until [NODE_SETTLE_MS] has elapsed since it was first observed, then allows the
 * cheaper PixelCopy crop path. This gives newly-appeared content — most commonly an
 * in-flight async load — time to settle before we start trusting a cached screenshot of it.
 */
internal class PixelCopyCapture(
    private val resourceResolver: ResourceResolver
) : PixelCropCallback {

    private val handlerThread = HandlerThread("dd-sr-pixel-copy").also { it.start() }
    private val backgroundHandler = Handler(handlerThread.looper)

    private val capturedBitmap: AtomicReference<Bitmap?> = AtomicReference(null)
    private val captureInProgress = AtomicBoolean(false)
    private val currentWindow = AtomicReference<WeakReference<Window>?>(null)
    private val lastCapturedViewUrl = AtomicReference<String?>(null)
    private val captureAllowedAfterMs = AtomicLong(0L)

    private val pendingCrops = CopyOnWriteArrayList<PendingPixelCrop>()

    /**
     * elapsedRealtime at which each nodeId was first registered. Used by [processPendingCrops]
     * to force the isolation path until [NODE_SETTLE_MS] has passed since a node first appeared
     * — gives newly-appeared content (most commonly an async image load) time to settle before
     * we trust a cached PixelCopy screenshot of it. Cleared on navigation in [onPreTraversal].
     */
    private val firstSeenAtMs = ConcurrentHashMap<Long, Long>()

    // region Window / URL tracking

    /** Updates the window used for [captureLatestFrame]. Main-thread safe. */
    fun setCurrentWindow(window: Window?) {
        currentWindow.set(window?.let { WeakReference(it) })
    }

    /**
     * Must be called **before** the SR traversal.
     * Discards the stored bitmap and starts the settle timer on URL change.
     */
    @UiThread
    fun onPreTraversal(currentViewUrl: String?) {
        if (currentViewUrl != lastCapturedViewUrl.get()) {
            capturedBitmap.getAndSet(null)?.recycle()
            pendingCrops.clear()
            firstSeenAtMs.clear()
            lastCapturedViewUrl.set(currentViewUrl)
            captureAllowedAfterMs.set(SystemClock.elapsedRealtime() + NAVIGATION_SETTLE_MS)
        }
    }

    // endregion

    // region PixelCropCallback

    /**
     * Registers a pending crop, calling [asyncJobStatusCallback.jobStarted] immediately.
     * The actual capture (PixelCopy vs isolation) is resolved in [processPendingCrops].
     */
    override fun registerPendingCrop(
        nodeId: Long,
        windowRect: Rect,
        dpBounds: GlobalBounds,
        isolationView: View,
        isolationClipRect: Rect,
        wireframe: MobileSegment.Wireframe.ImageWireframe,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        firstSeenAtMs.putIfAbsent(nodeId, SystemClock.elapsedRealtime())
        asyncJobStatusCallback.jobStarted()
        pendingCrops.add(
            PendingPixelCrop(
                nodeId = nodeId,
                windowRect = windowRect,
                dpBounds = dpBounds,
                isolationView = isolationView,
                isolationClipRect = isolationClipRect,
                wireframe = wireframe,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        )
    }

    // endregion

    // region Post-traversal processing

    /**
     * Must be called **after** traversal, **before** [captureLatestFrame].
     *
     * For each pending crop:
     * - Checks whether any other wireframe (non-parent) overlaps its bounds.
     * - **No overlay:** crops from the stored PixelCopy bitmap (full fidelity).
     * - **Overlay detected:** calls [View.draw] in isolation (no overlay contamination).
     *
     * Feeds the chosen bitmap to [ResourceResolver] and sets [wireframe.resourceId] on success.
     */
    @UiThread
    fun processPendingCrops(allNodes: List<Node>) {
        if (pendingCrops.isEmpty()) return

        val allBounds = mutableListOf<GlobalBounds>()
        collectWireframeBounds(allNodes, allBounds)

        val snapshot = pendingCrops.toList()
        pendingCrops.clear()

        for (pending in snapshot) {
            val hasOverlay = allBounds.any { other ->
                val isSelf = other.x == pending.dpBounds.x &&
                    other.y == pending.dpBounds.y &&
                    other.width == pending.dpBounds.width &&
                    other.height == pending.dpBounds.height
                !isSelf &&
                    boundsOverlap(other, pending.dpBounds) &&
                    !fullyContains(container = other, child = pending.dpBounds)
            }

            val firstSeen = firstSeenAtMs[pending.nodeId] ?: SystemClock.elapsedRealtime()
            val isSettled = SystemClock.elapsedRealtime() - firstSeen >= NODE_SETTLE_MS

            if (hasOverlay || !isSettled) {
                // Overlay detected, or the node appeared less than NODE_SETTLE_MS ago (the
                // stored PixelCopy bitmap may predate content that's still settling, e.g. an
                // async image load in progress) — use isolation, which is always live.
                captureViewRegionAsync(pending.isolationView, pending.isolationClipRect, pending.windowRect) { bitmap ->
                    completePendingCrop(pending, bitmap)
                }
            } else {
                // Prefer PixelCopy for full fidelity (parent alpha/transforms included).
                // Fall back to isolation if the PixelCopy bitmap is not yet available
                // (settle period after navigation, or first snapshot on a new screen).
                val pixelCopyBitmap = cropRect(pending.windowRect)
                if (pixelCopyBitmap != null) {
                    completePendingCrop(pending, pixelCopyBitmap)
                } else {
                    captureViewRegionAsync(pending.isolationView, pending.isolationClipRect, pending.windowRect) { bitmap ->
                        completePendingCrop(pending, bitmap)
                    }
                }
            }
        }
    }

    /**
     * Feeds [bitmap] to [ResourceResolver] and populates [PendingPixelCrop.wireframe] on
     * success. Called either synchronously (PixelCopy crop path) or from an async capture
     * completion (isolation path). Always calls [AsyncJobStatusCallback.jobFinished] exactly
     * once, regardless of outcome.
     */
    private fun completePendingCrop(pending: PendingPixelCrop, bitmap: Bitmap?) {
        if (bitmap == null) {
            pending.asyncJobStatusCallback.jobFinished()
            return
        }
        resourceResolver.resolveResourceIdFromBitmap(
            bitmap = bitmap,
            resourceResolverCallback = object : ResourceResolverCallback {
                override fun onSuccess(resourceId: String) {
                    pending.wireframe.resourceId = resourceId
                    pending.wireframe.isEmpty = false
                    pending.asyncJobStatusCallback.jobFinished()
                }
                override fun onFailure() {
                    pending.asyncJobStatusCallback.jobFinished()
                }
            }
        )
    }

    // endregion

    // region PixelCopy full-window capture

    /**
     * Must be called **after** [processPendingCrops] on each snapshot cycle.
     * Fires one full-window [PixelCopy] when settled after navigation and not in-flight.
     * The resulting bitmap is stored for the **next** cycle's [processPendingCrops].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun captureLatestFrame() {
        val now = SystemClock.elapsedRealtime()
        val allowedAfter = captureAllowedAfterMs.get()
        if (now < allowedAfter) return

        if (!captureInProgress.compareAndSet(false, true)) return

        val window = currentWindow.get()?.get()
        if (window == null) {
            captureInProgress.set(false)
            return
        }
        val decorView = window.decorView
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) {
            captureInProgress.set(false)
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, bitmap, { result ->
            captureInProgress.set(false)
            if (result == PixelCopy.SUCCESS) {
                capturedBitmap.set(bitmap)
            } else {
                Log.e(TAG, "captureLatestFrame: PixelCopy failed with code=$result")
                bitmap.recycle()
            }
        }, backgroundHandler)
    }

    // endregion

    // region Capture primitives

    /**
     * Crops [rect] (window-pixel coordinates) from the stored full-window bitmap.
     * Returns null if no capture is available or bounds are invalid.
     */
    private fun cropRect(rect: Rect): Bitmap? {
        val bitmap = capturedBitmap.get() ?: return null
        if (bitmap.isRecycled) return null
        if (rect.left < 0 || rect.top < 0) return null
        if (rect.right > bitmap.width || rect.bottom > bitmap.height) return null
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return try {
            Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "cropRect: failed for $rect — ${e.message}")
            null
        }
    }

    /**
     * Captures [sourceView] clipped to [clipRect] (in [sourceView]'s coordinate space),
     * isolated from any overlying content, and delivers the result to [onResult].
     *
     * Uses a software [Canvas] via [View.draw]. This cannot render content backed by
     * `Bitmap.Config.HARDWARE` bitmaps — the default decode format for Coil, Glide, and
     * similar image loaders on API 26+ — and calling [View.draw] on a view holding one
     * throws `IllegalArgumentException`. When that happens, [fallbackWindowRect] (the
     * corresponding window-pixel rect for a PixelCopy crop) is tried as a best-effort
     * substitute — a slightly-stale-but-valid image rather than a broken one. Note this
     * fallback is only reached when isolation itself fails, so on the rare occasion this
     * happens *because an overlay was detected*, the fallback crop may reintroduce that
     * same overlay's pixels for this one region — an acceptable trade against showing no
     * image at all. If the fallback is also unavailable, [onResult] receives null and the
     * caller falls back to a placeholder wireframe.
     *
     * Two additional safeguards, layered:
     * - [containsHardwareSurface] skips isolation entirely — before even attempting it — when
     *   [sourceView] hosts a [SurfaceView]/[TextureView]. Those composite through a path
     *   [View.draw] never touches at all; unlike the hardware-bitmap case, drawing over one
     *   throws no exception, it just silently produces blank content for that region. Detecting
     *   the view type ahead of time avoids ever attempting (and trusting) that broken capture.
     * - [isBitmapLikelyEmpty] is a best-effort check on whatever [View.draw] *did* produce, for
     *   cases the type check doesn't catch (e.g. an unanticipated rendering path). It is not
     *   comprehensive: an opaque background drawn by an ancestor behind a "hole" would not be
     *   flagged, and a legitimately fully-transparent capture would be (incorrectly) discarded.
     */
    private fun captureViewRegionAsync(
        sourceView: View,
        clipRect: Rect,
        fallbackWindowRect: Rect,
        onResult: (Bitmap?) -> Unit
    ) {
        if (clipRect.width() <= 0 || clipRect.height() <= 0) {
            onResult(null)
            return
        }

        if (containsHardwareSurface(sourceView)) {
            onResult(cropRect(fallbackWindowRect))
            return
        }

        val drawn = try {
            captureViewRegionViaDraw(sourceView, clipRect)
        } catch (e: IllegalArgumentException) {
            // "Software rendering doesn't support hardware bitmaps" — thrown when the view
            // (or a descendant, e.g. an ImageView showing a Coil/Glide-decoded image) holds
            // a Bitmap.Config.HARDWARE bitmap. There is no way to render that content through
            // a software Canvas; fall back to the stored window-level PixelCopy bitmap instead
            // of surfacing a broken image.
            Log.e(TAG, "captureViewRegionAsync: hardware bitmap in ${sourceView.javaClass.simpleName} — ${e.message}")
            null
        }

        if (drawn != null && !isBitmapLikelyEmpty(drawn)) {
            onResult(drawn)
            return
        }

        drawn?.recycle()
        onResult(cropRect(fallbackWindowRect))
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
     * hardware surface elsewhere on the same screen would also trigger it). That only costs a
     * fallback to the PixelCopy-crop-or-placeholder path in that case, never an incorrect result.
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
     * comprehensive (see [captureViewRegionAsync] doc for the known gaps); intended as a cheap
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
     * [captureViewRegionAsync] for how that is handled.
     */
    private fun captureViewRegionViaDraw(sourceView: View, clipRect: Rect): Bitmap {
        val bitmap = Bitmap.createBitmap(clipRect.width(), clipRect.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-clipRect.left.toFloat(), -clipRect.top.toFloat())
        sourceView.draw(canvas)
        return bitmap
    }

    // endregion

    // region Lifecycle

    fun release() {
        capturedBitmap.getAndSet(null)?.recycle()
        pendingCrops.clear()
        firstSeenAtMs.clear()
        currentWindow.set(null)
        handlerThread.quitSafely()
    }

    // endregion

    // region Helpers

    private fun collectWireframeBounds(nodes: List<Node>, result: MutableList<GlobalBounds>) {
        for (node in nodes) {
            for (wireframe in node.wireframes) {
                wireframeBounds(wireframe)?.let { result.add(it) }
            }
            collectWireframeBounds(node.children, result)
        }
    }

    private fun wireframeBounds(wireframe: MobileSegment.Wireframe): GlobalBounds? = when (wireframe) {
        is MobileSegment.Wireframe.TextWireframe ->
            GlobalBounds(wireframe.x, wireframe.y, wireframe.width, wireframe.height)
        is MobileSegment.Wireframe.ShapeWireframe ->
            GlobalBounds(wireframe.x, wireframe.y, wireframe.width, wireframe.height)
        is MobileSegment.Wireframe.ImageWireframe ->
            GlobalBounds(wireframe.x, wireframe.y, wireframe.width, wireframe.height)
        is MobileSegment.Wireframe.PlaceholderWireframe ->
            GlobalBounds(wireframe.x, wireframe.y, wireframe.width, wireframe.height)
        is MobileSegment.Wireframe.WebviewWireframe ->
            GlobalBounds(wireframe.x, wireframe.y, wireframe.width, wireframe.height)
        else -> null
    }

    private fun boundsOverlap(a: GlobalBounds, b: GlobalBounds): Boolean =
        a.x < b.x + b.width && a.x + a.width > b.x &&
            a.y < b.y + b.height && a.y + a.height > b.y

    private fun fullyContains(container: GlobalBounds, child: GlobalBounds): Boolean =
        container.x <= child.x && container.y <= child.y &&
            container.x + container.width >= child.x + child.width &&
            container.y + container.height >= child.y + child.height

    // endregion

    companion object {
        private const val TAG = "DD_PixelCopyCapture"
        internal const val NAVIGATION_SETTLE_MS = 250L

        /**
         * Minimum time a node must have been observed before its stored PixelCopy bitmap is
         * trusted. A few hundred milliseconds covers the common case of an async image load
         * or similar first-paint settling; only very fast navigations that don't matter to a
         * user are affected (isolation is used for that brief window instead — still correct,
         * just marginally more expensive).
         */
        internal const val NODE_SETTLE_MS = 300L
    }
}
