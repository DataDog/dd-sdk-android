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
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolverCallback
import com.datadog.android.sessionreplay.recorder.privacy.TextDetectionOutcome
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Resolves every [PendingPixelCapture] a generation collected during traversal: text-region
 * masking (fails closed to a placeholder when text presence can't be verified), then hash/encode/
 * dedup/register via the existing [ResourceResolver] pipeline. Only ever replaces the exact
 * wireframes/child-references its own pending captures produced - everything else in the snapshot
 * passes through untouched. [cancel] is intentionally a no-op: once a generation expires or is
 * accepted, [CaptureGenerationContext] itself invalidates every [CaptureWorkToken] this processor
 * created, and this processor checks [CaptureWorkToken.isValid] before writing any result back -
 * cancellation is enforced by the token, not by this class needing to interrupt in-flight work.
 */
internal class PixelFallbackSnapshotProcessor(
    private val resourceResolver: ResourceResolver,
    private val textDetector: TextDetector?
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
            resolveOne(capture, identityFactory) { outcome ->
                if (token.isValid()) outcomes[capture.wireframeIdentity.wireId] = outcome
                token.complete()
                if (remaining.decrementAndGet() == 0) finish(request, outcomes, callback)
            }
        }

        return CancellableCaptureWork { }
    }

    private fun resolveOne(
        capture: PendingPixelCapture,
        identityFactory: CompositionIdentityFactory,
        onResolved: (PixelOutcome) -> Unit
    ) {
        val detector = textDetector
        if (detector == null) {
            onResolved(PixelOutcome.Placeholder(identityFactory.placeholderWireframe(capture.ownerIdentity)))
            return
        }
        detector.detectTextRegions(capture.bitmap) { detection ->
            when (detection) {
                is TextDetectionOutcome.Unavailable ->
                    onResolved(PixelOutcome.Placeholder(identityFactory.placeholderWireframe(capture.ownerIdentity)))

                is TextDetectionOutcome.Detected -> {
                    maskRegions(capture.bitmap, detection.regions)
                    resourceResolver.resolveResourceIdFromBitmap(
                        capture.bitmap,
                        object : ResourceResolverCallback {
                            override fun onSuccess(resourceId: String) {
                                onResolved(PixelOutcome.Resolved(resourceId))
                            }

                            override fun onFailure() {
                                onResolved(
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

    /** Painted directly onto the pixels, pre-upload - never a separate overlay wireframe. */
    private fun maskRegions(bitmap: Bitmap, regions: List<Rect>) {
        if (regions.isEmpty()) return
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
                        label = DefaultImageWireframeHelper.MASK_ALL_CONTENT_LABEL
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
    }
}
