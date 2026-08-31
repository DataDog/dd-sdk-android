/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.collection.LruCache
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapSignatureGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultBitmapSignatureGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import com.datadog.android.sessionreplay.recorder.privacy.TextDetectionOutcome
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Resolves every [PendingPixelCapture] a generation collected during traversal: text-region
 * masking (fails closed to a placeholder when text presence can't be verified), then hash/encode/
 * dedup/register via the existing [ResourceResolver] pipeline. Only ever replaces the exact
 * wireframes/child-references its own pending captures produced - everything else in the snapshot
 * passes through untouched. [process]'s own returned [CancellableCaptureWork] is intentionally a
 * no-op: once a generation expires or is accepted, [CaptureGenerationContext] itself invalidates
 * every [CaptureWorkToken] this processor created, and this processor checks
 * [CaptureWorkToken.isValid] before writing any result back - cancellation of the overall request is
 * enforced by the token, not by this class needing to interrupt in-flight work. Each individual
 * detection call is separately bounded by [taskScheduler] (see [resolveOne]) so one slow/stuck
 * detector degrades to a placeholder for just that capture, rather than silently starving the
 * generation's own much shorter deadline of the whole snapshot. [TextDetector.detectTextRegions]
 * may invoke its callback on any thread, so [resolveBitmap] hops onto [mainThreadExecutor] before
 * calling [ResourceResolver.resolveResourceIdFromBitmap], which is
 * [androidx.annotation.MainThread]-only.
 *
 * A detection call that loses that race isn't wasted, only its result was going to be discarded:
 * [TextDetector.detectTextRegions] keeps running in the background regardless of which generation
 * "owns" it - nothing in [CaptureGenerationContext] cancels the underlying detector call itself,
 * only the safety-margin timeout is a trackable, cancellable unit of work. [resolveOne] now uses
 * that: [resolvedContentCache] remembers a real, successful resolution by [BitmapSignatureGenerator]
 * signature, and [recaptureTrigger] asks for a fresh generation the moment one lands too late to
 * help the generation that requested it. The *next* capture of that same (unchanged) content then
 * resolves straight from the cache instead of racing detection again - the 90ms generation budget
 * only ever has to cover traversal and rasterization, never a full detector round-trip.
 */
internal class PixelFallbackSnapshotProcessor(
    private val resourceResolver: ResourceResolver,
    private val textDetector: TextDetector?,
    private val mainThreadExecutor: CaptureMainThreadExecutor,
    private val taskScheduler: CaptureTaskScheduler = CaptureTaskScheduler { _, _ -> CancellableCaptureWork.NONE },
    private val detectionTimeoutSafetyMarginNs: Long = DEFAULT_SAFETY_MARGIN_NS,
    private val bitmapSignatureGenerator: BitmapSignatureGenerator = DefaultBitmapSignatureGenerator(),
    private val recaptureTrigger: RecaptureTrigger = RecaptureTrigger {},
    @Suppress("UnsafeThirdPartyFunctionCall") // MAX_RESOLVED_CONTENT_CACHE_SIZE is a positive constant
    private val resolvedContentCache: LruCache<Long, String> = LruCache(MAX_RESOLVED_CONTENT_CACHE_SIZE)
) : CapturedSnapshotProcessor {

    override fun process(
        request: SnapshotProcessingRequest,
        callback: SnapshotProcessingCallback
    ): CancellableCaptureWork {
        val pending = request.pendingPixelCaptures
        val identityFactory = request.identityFactory
        if (pending.isEmpty() || identityFactory == null) {
            callback.onProcessed(SnapshotProcessingResult.Completed(request.generation.id, request.snapshot))
            return CancellableCaptureWork.NONE
        }

        val outcomes = ConcurrentHashMap<Long, PixelOutcome>()
        val remaining = AtomicInteger(pending.size)

        pending.forEach { capture ->
            val token = request.generation.createWorkToken()
            if (token == null) {
                if (remaining.decrementAndGet() == 0) finish(request, outcomes, callback)
                return@forEach
            }
            resolveOne(capture, identityFactory, request.generation) { outcome ->
                if (token.isValid()) outcomes[capture.wireframeIdentity.wireId] = outcome
                token.complete()
                if (remaining.decrementAndGet() == 0) finish(request, outcomes, callback)
            }
        }

        return CancellableCaptureWork { }
    }

    /**
     * Detection is bounded by a timeout relative to [generation]'s own remaining deadline, not a
     * fixed constant - [TextDetector] implementations already fail closed on their own (much
     * looser) internal timeout, which exists to bound the detector itself, not this generation's
     * much shorter budget. Whichever of {detector callback, timeout} resolves first wins via
     * [resolved]; the loser is a no-op *for this generation's own outcome* - see the class doc for
     * why the detector call itself is never actually cancelled, and what a late-arriving success
     * still does ([resolvedContentCache]/[recaptureTrigger]) even after losing that race. This CAS
     * is a different concern from the [CaptureWorkToken] guard in [process]: that one decides
     * whether an outcome still counts once the whole *generation* has expired, while this one
     * decides which of two concurrent callers - which can run on any thread, per
     * [TextDetector.detectTextRegions]'s contract - gets to report the outcome for *this one*
     * pending capture at all.
     */
    @Suppress("ReturnCount")
    private fun resolveOne(
        capture: PendingPixelCapture,
        identityFactory: CompositionIdentityFactory,
        generation: CaptureGenerationContext,
        onResolved: (PixelOutcome) -> Unit
    ) {
        if (capture.isTextFree) {
            resolveBitmap(capture.bitmap, identityFactory, capture.ownerIdentity, onResolved)
            return
        }

        val detector = textDetector
        if (detector == null) {
            onResolved(PixelOutcome.Placeholder(identityFactory.placeholderWireframe(capture.ownerIdentity)))
            return
        }

        val signature = bitmapSignatureGenerator.generateSignature(capture.bitmap)

        @Suppress("UnsafeThirdPartyFunctionCall") // LruCache.get never throws for a valid Long key
        val cachedResourceId = signature?.let { resolvedContentCache.get(it) }
        if (cachedResourceId != null) {
            onResolved(PixelOutcome.Resolved(cachedResourceId))
            return
        }

        val resolved = AtomicBoolean(false)
        // Returns whether this call was the one that actually delivered an outcome, so a
        // late-arriving success can tell it lost the race and ask for a fresh capture instead.
        val resolveOnce: (PixelOutcome) -> Boolean = { outcome ->
            val won = resolved.compareAndSet(false, true)
            if (won) onResolved(outcome)
            won
        }

        val timeoutNs = (generation.remainingBudgetNs() - detectionTimeoutSafetyMarginNs).coerceAtLeast(0L)
        val timeoutWork = taskScheduler.schedule(timeoutNs) {
            resolveOnce(PixelOutcome.Placeholder(identityFactory.placeholderWireframe(capture.ownerIdentity)))
        }
        generation.track(timeoutWork)

        detector.detectTextRegions(capture.bitmap) { detection ->
            timeoutWork.cancel()
            when (detection) {
                is TextDetectionOutcome.Unavailable ->
                    resolveOnce(PixelOutcome.Placeholder(identityFactory.placeholderWireframe(capture.ownerIdentity)))

                is TextDetectionOutcome.Detected -> {
                    maskRegions(capture.bitmap, detection.regions)
                    @Suppress("ThreadSafety") // mainThreadExecutor posts this block onto the main thread.
                    mainThreadExecutor.execute {
                        resourceResolver.resolveResourceIdFromBitmap(
                            capture.bitmap,
                            object : ResourceResolverCallback {
                                override fun onSuccess(resourceId: String) {
                                    if (signature != null) {
                                        @Suppress("UnsafeThirdPartyFunctionCall") // never throws for valid arguments
                                        resolvedContentCache.put(signature, resourceId)
                                    }
                                    val wonRace = resolveOnce(PixelOutcome.Resolved(resourceId))
                                    // This generation already placeholder'd this exact capture -
                                    // don't wait for the next unrelated redraw to show the content
                                    // this detection call just proved is safe to display.
                                    if (!wonRace) recaptureTrigger.requestRecapture()
                                }

                                override fun onFailure() {
                                    resolveOnce(
                                        PixelOutcome.Placeholder(
                                            identityFactory.placeholderWireframe(capture.ownerIdentity)
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun resolveBitmap(
        bitmap: Bitmap,
        identityFactory: CompositionIdentityFactory,
        ownerIdentity: CapturedIdentity,
        onResolved: (PixelOutcome) -> Unit
    ) {
        @Suppress("ThreadSafety") // mainThreadExecutor posts this block onto the main thread.
        mainThreadExecutor.execute {
            resourceResolver.resolveResourceIdFromBitmap(
                bitmap,
                object : ResourceResolverCallback {
                    override fun onSuccess(resourceId: String) {
                        onResolved(PixelOutcome.Resolved(resourceId))
                    }

                    override fun onFailure() {
                        onResolved(PixelOutcome.Placeholder(identityFactory.placeholderWireframe(ownerIdentity)))
                    }
                }
            )
        }
    }

    /** Painted directly onto the pixels, pre-upload - never a separate overlay wireframe. */
    private fun maskRegions(bitmap: Bitmap, regions: List<Rect>) {
        if (regions.isEmpty()) return
        // bitmap is always produced by a *Rasterizer via Bitmap.createBitmap(...) (mutable by
        // default), never decoded from a resource/file, so Canvas() can't hit the "immutable
        // bitmap" IllegalStateException here.
        @Suppress("UnsafeThirdPartyFunctionCall")
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        regions.forEach { canvas.drawRect(it, paint) }
    }

    private fun finish(
        request: SnapshotProcessingRequest,
        outcomes: Map<Long, PixelOutcome>,
        callback: SnapshotProcessingCallback
    ) {
        val resolvedSnapshot = applyOutcomes(request.snapshot, outcomes)
        callback.onProcessed(SnapshotProcessingResult.Completed(request.generation.id, resolvedSnapshot))
    }

    private fun applyOutcomes(snapshot: CapturedFullSnapshot, outcomes: Map<Long, PixelOutcome>): CapturedFullSnapshot {
        if (outcomes.isEmpty()) return snapshot

        val wireframesById = snapshot.wireframes.associateBy { it.identity.wireId }.toMutableMap()
        val childRewrites = mutableMapOf<Long, CapturedIdentity>()

        outcomes.forEach { (wireId, outcome) ->
            val original = wireframesById[wireId] as? CapturedWireframe.Pixel ?: return@forEach
            when (outcome) {
                is PixelOutcome.Resolved -> {
                    wireframesById[wireId] = original.copy(
                        resource = PixelResource.Resolved(outcome.resourceId, MIME_TYPE_WEBP)
                    )
                }

                is PixelOutcome.Placeholder -> {
                    wireframesById.remove(wireId)
                    wireframesById[outcome.identity.wireId] = CapturedWireframe.PrivacyPlaceholder(
                        identity = outcome.identity,
                        bounds = original.bounds,
                        clip = original.clip,
                        label = MASK_ALL_CONTENT_LABEL
                    )
                    childRewrites[wireId] = outcome.identity
                }
            }
        }

        val layers = if (childRewrites.isEmpty()) {
            snapshot.layers
        } else {
            snapshot.layers.map { it.withRewrittenChildren(childRewrites) }
        }

        return snapshot.copy(wireframes = wireframesById.values.toList(), layers = layers)
    }

    private fun CapturedLayer.withRewrittenChildren(rewrites: Map<Long, CapturedIdentity>): CapturedLayer {
        val hasRewrite = children.any { it is CapturedChild.Wireframe && rewrites.containsKey(it.identity.wireId) }
        if (!hasRewrite) return this
        return copy(
            children = children.map { child ->
                if (child is CapturedChild.Wireframe) {
                    rewrites[child.identity.wireId]?.let { CapturedChild.Wireframe(it) } ?: child
                } else {
                    child
                }
            }
        )
    }

    private sealed interface PixelOutcome {
        data class Resolved(val resourceId: String) : PixelOutcome
        data class Placeholder(val identity: CapturedIdentity) : PixelOutcome
    }

    private companion object {
        const val MIME_TYPE_WEBP = "image/webp"
        const val MASK_ALL_CONTENT_LABEL = "Image"
        val DEFAULT_SAFETY_MARGIN_NS = TimeUnit.MILLISECONDS.toNanos(10)

        // Entries are a signature Long plus a short resource-id String each - generous headroom
        // for every distinct pixel-fallback view on screen at once without unbounded growth over a
        // long session.
        const val MAX_RESOLVED_CONTENT_CACHE_SIZE = 200
    }
}

/**
 * Asks for a fresh capture generation outside the normal draw-triggered flow - used by
 * [PixelFallbackSnapshotProcessor] when a detection result arrives too late for the generation
 * that requested it, so the corrected content reaches the recording without waiting for the next
 * unrelated redraw.
 */
internal fun interface RecaptureTrigger {
    fun requestRecapture()
}
