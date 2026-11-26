/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import com.datadog.android.sessionreplay.recorder.privacy.TextDetectionOutcome
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves each [PendingPixelCapture] whose wireframe survived construction: text-region
 * masking (fails closed to a placeholder when text presence can't be verified), then hash/encode/
 * dedup/register via the existing [ResourceResolver] pipeline. Only ever replaces the exact
 * wireframes/child-references its own pending captures produced - everything else in the snapshot
 * passes through untouched.
 *
 * The [TextDetector] is optional third-party code, so nothing it does can leave a capture unresolved
 * or upload unmasked pixels: a detector that throws, or does not complete within
 * [DETECTION_BUDGET_PERCENT] of the time left when processing starts, resolves that capture to a
 * placeholder, and the rest of the snapshot still completes. Only the first result a detector
 * reports counts. A detection that has no chance of being used is not acted on: once the generation has
 * expired or been accepted, [CaptureGenerationContext] invalidates every [CaptureWorkToken] this
 * processor created, and a result arriving then is neither masked nor encoded. A detection already
 * in flight cannot be interrupted, as [TextDetector] has no cancellation handle.
 */
internal class PixelFallbackSnapshotProcessor(
    private val resourceResolver: ResourceResolver,
    private val textDetector: TextDetector?,
    private val internalLogger: InternalLogger,
    private val detectionTimeoutScheduler: CaptureTaskScheduler,
    private val canvasWrapper: CanvasWrapper = CanvasWrapper(internalLogger)
) : CapturedSnapshotProcessor {

    override fun process(
        request: SnapshotProcessingRequest,
        callback: SnapshotProcessingCallback
    ): CancellableCaptureWork {
        val pending = request.capturesInSnapshot()
        val identityFactory = request.identityFactory
        if (pending.isEmpty() || identityFactory == null) {
            callback.onProcessed(
                SnapshotProcessingResult.Completed(request.generation.id, request.snapshot as CapturedFullSnapshot)
            )
            return CancellableCaptureWork.NONE
        }

        val outcomes = ConcurrentHashMap<Long, PixelOutcome>()
        val remaining = AtomicInteger(pending.size)

        @Suppress("UnsafeThirdPartyFunctionCall") // AtomicReference accepts this non-null input.
        val detectionTimeout = AtomicReference(CancellableCaptureWork.NONE)
        val onCaptureDone: () -> Unit = {
            if (remaining.decrementAndGet() == 0) {
                detectionTimeout.get().cancel()
                finish(request, outcomes, callback)
            }
        }

        val resolutions = pending.mapNotNull { capture ->
            val token = request.generation.createWorkToken()
            if (token == null) {
                onCaptureDone()
                return@mapNotNull null
            }
            CaptureResolution(capture, identityFactory, token) { outcome ->
                if (token.isValid()) outcomes[capture.wireframeIdentity.wireId] = outcome
                token.complete()
                onCaptureDone()
            }
        }
        // Armed before any detector is called, so a detector that blocks in the call is timed out too.
        if (textDetector != null && remaining.get() > 0 && pending.any { !it.skipsTextDetection }) {
            @Suppress("UnsafeThirdPartyFunctionCall") // AtomicReference accepts this non-null input.
            detectionTimeout.set(scheduleDetectionTimeout(request.generation, resolutions))
        }
        resolutions.forEach { it.start() }

        return CancellableCaptureWork { detectionTimeout.get().cancel() }
    }

    private fun scheduleDetectionTimeout(
        generation: CaptureGenerationContext,
        resolutions: List<CaptureResolution>
    ): CancellableCaptureWork {
        val delayNs = generation.remainingBudgetNs() * DETECTION_BUDGET_PERCENT / PERCENT
        return detectionTimeoutScheduler.schedule(delayNs) {
            resolutions.forEach { it.failClosedIfAwaitingDetection() }
        }
    }

    private fun finish(
        request: SnapshotProcessingRequest,
        outcomes: Map<Long, PixelOutcome>,
        callback: SnapshotProcessingCallback
    ) {
        val resolvedSnapshot = applyOutcomes(request.snapshot as CapturedFullSnapshot, outcomes)
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

    /**
     * Resolves one [PendingPixelCapture] to exactly one [PixelOutcome], whichever of the detector's
     * callback, a failure or the detection timeout comes first.
     */
    private inner class CaptureResolution(
        private val capture: PendingPixelCapture,
        private val identityFactory: CompositionIdentityFactory,
        private val token: CaptureWorkToken,
        private val onOutcome: (PixelOutcome) -> Unit
    ) {
        private val resolved = AtomicBoolean(false)
        private val awaitingDetection = AtomicBoolean(false)

        fun start() {
            val detector = textDetector
            when {
                !token.isValid() -> failClosed()
                capture.skipsTextDetection -> resolveBitmap()
                detector == null -> failClosed()
                else -> detect(detector)
            }
        }

        fun failClosedIfAwaitingDetection() {
            if (awaitingDetection.compareAndSet(true, false)) failClosed()
        }

        private fun detect(detector: TextDetector) {
            awaitingDetection.set(true)
            val called = guarded {
                detector.detectTextRegions(capture.bitmap) { detection -> onDetection(detection) }
            }
            if (!called) failClosedIfAwaitingDetection()
        }

        private fun onDetection(detection: TextDetectionOutcome) {
            if (!awaitingDetection.compareAndSet(true, false)) {
                internalLogger.log(
                    level = InternalLogger.Level.WARN,
                    target = InternalLogger.Target.MAINTAINER,
                    messageBuilder = { IGNORED_DETECTION_MESSAGE },
                    onlyOnce = true
                )
                return
            }
            resolveDetection(detection)
        }

        private fun resolveDetection(detection: TextDetectionOutcome) {
            if (!token.isValid()) {
                failClosed()
                return
            }
            when (detection) {
                is TextDetectionOutcome.Unavailable -> failClosed()
                is TextDetectionOutcome.Detected ->
                    if (maskRegions(capture.bitmap, detection.regions)) resolveBitmap() else failClosed()
            }
        }

        private fun resolveBitmap() {
            resourceResolver.resolveResourceIdFromBitmap(
                capture.bitmap,
                object : ResourceResolverCallback {
                    override fun onSuccess(resourceId: String) {
                        resolve(PixelOutcome.Resolved(resourceId))
                    }

                    override fun onFailure() {
                        failClosed()
                    }
                }
            )
        }

        private fun failClosed() {
            resolve(PixelOutcome.Placeholder(identityFactory.placeholderWireframe(capture.ownerIdentity)))
        }

        private fun resolve(outcome: PixelOutcome) {
            awaitingDetection.set(false)
            if (resolved.compareAndSet(false, true)) onOutcome(outcome)
        }
    }

    /**
     * Painted directly onto the pixels, pre-upload - never a separate overlay wireframe. False when
     * the pixels can't be painted on, in which case the bitmap must not be uploaded.
     */
    private fun maskRegions(bitmap: Bitmap, regions: List<Rect>): Boolean {
        if (regions.isEmpty()) return true
        val canvas = canvasWrapper.createCanvas(bitmap)
        return canvas != null && guarded {
            val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
            regions.forEach { canvas.drawRect(it, paint) }
        }
    }

    // Only for the optional detector and the pixels it is given: its failures must fail closed.
    @Suppress("TooGenericExceptionCaught")
    private fun guarded(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: Exception) {
        logDetectionFailure(e)
        false
    } catch (e: LinkageError) {
        logDetectionFailure(e)
        false
    } catch (e: AssertionError) {
        logDetectionFailure(e)
        false
    }

    private fun logDetectionFailure(error: Throwable) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { DETECTION_FAILED_MESSAGE },
            throwable = error,
            onlyOnce = true
        )
    }

    private sealed interface PixelOutcome {
        data class Resolved(val resourceId: String) : PixelOutcome
        data class Placeholder(val identity: CapturedIdentity) : PixelOutcome
    }

    internal companion object {
        // Detection gets this share of the time left when processing starts; the rest is for
        // encoding the captures that did resolve, and for completing the snapshot.
        internal const val DETECTION_BUDGET_PERCENT = 50L
        private const val PERCENT = 100L
        private const val MIME_TYPE_WEBP = "image/webp"
        private const val MASK_ALL_CONTENT_LABEL = "Image"
        private const val DETECTION_FAILED_MESSAGE =
            "Text detection failed; the pixel capture is replaced by a placeholder"
        private const val IGNORED_DETECTION_MESSAGE =
            "Text detection completed more than once or too late; the extra result is ignored"
    }
}

// Construction drops occluded views' wireframes but keeps their pending captures.
private fun SnapshotProcessingRequest.capturesInSnapshot(): List<PendingPixelCapture> {
    if (pendingPixelCaptures.isEmpty()) return pendingPixelCaptures
    val pixelWireIds = (snapshot as CapturedFullSnapshot).wireframes
        .filterIsInstance<CapturedWireframe.Pixel>()
        .mapTo(mutableSetOf()) { it.identity.wireId }
    return pendingPixelCaptures.filter { it.wireframeIdentity.wireId in pixelWireIds }
}
