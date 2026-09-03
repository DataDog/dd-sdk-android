/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistration
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Direct port of legacy `EmbeddedContentViewMapper` onto the composition-tree wire model: a tagged
 * view's own content is fully replaced by a single [CapturedWireframe.EmbeddedContent] placeholder,
 * correlated by `slotId` with content recorded independently by the embedding SDK (see
 * `_SessionReplayInternalProxy.setEmbeddedContentSlotId`/`addEmbeddedContentRecords`). Shared as the
 * SAME instance between `AndroidWindowTraversal` (per-view mapping, via [map]/[hasSlotId]) and
 * `AndroidCapturedSnapshotProducer` (the [beginCapture]/[finishCapture] lifecycle, spanning every
 * window in one generation) - mirrors legacy's own single `EmbeddedContentViewMapper` instance
 * shared between `TreeViewTraversal` and `SnapshotProducer`.
 */
internal class CapturedEmbeddedContentMapper(
    private val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal(),
    private val backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver =
        CapturedBackgroundShapeStyleResolver(),
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : CapturedViewMapper<View> {

    private val cache = EmbeddedContentCache(embeddedContentSlotRegistry)

    @UiThread
    fun hasSlotId(view: View): Boolean = view.sessionReplaySlotId() != null

    /** Call once before walking any window for a new capture generation. */
    @UiThread
    fun beginCapture() {
        cache.beginCapture()
    }

    /**
     * Call once after every window has been walked for this generation. Returns a hidden,
     * zero-bounds placeholder for every slot whose registration is still active but wasn't
     * refreshed by [map] this round (e.g. its view scrolled off-screen, was recycled, or is
     * otherwise temporarily absent from the walk) - without this, such a slot would simply vanish
     * from the wireframe list rather than being explicitly marked hidden, leaving the embedding
     * SDK's last-known placeholder position stale. A slot the registry no longer considers active
     * at all (explicitly detached via `setEmbeddedContentSlotId(view, null)`) is dropped entirely
     * instead, matching that method's own "removes its embedded-content wireframe from the next
     * capture" contract.
     */
    @UiThread
    fun finishCapture(): List<CapturedWireframe> = cache.hiddenWireframes()

    @UiThread
    @Suppress("ReturnCount")
    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val registration = view.sessionReplaySlotRegistration() ?: return CapturedViewMapperResult.None
        val slotId = registration.slotId
        embeddedContentSlotRegistry.track(registration)
        val identity = mappingContext.identityFactory.embeddedContentWireframe(mappingContext.ownerIdentity)
        cache.record(slotId, identity)

        val wireframe = if (viewUtilsInternal.isNotVisible(view)) {
            hiddenWireframe(identity, slotId)
        } else {
            val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
            CapturedWireframe.EmbeddedContent(
                identity = identity,
                bounds = bounds.toCaptured(),
                style = backgroundShapeStyleResolver.resolve(view, internalLogger),
                slotId = slotId,
                isVisible = true
            )
        }
        return CapturedViewMapperResult.Wireframes(listOf(wireframe), pixelFallbackTerminal = true)
    }

    private fun hiddenWireframe(identity: CapturedIdentity, slotId: String) = CapturedWireframe.EmbeddedContent(
        identity = identity,
        bounds = CapturedBounds(0, 0, 0, 0),
        slotId = slotId,
        isVisible = false
    )

    private fun View.sessionReplaySlotId(): String? =
        getTag(R.id.datadog_session_replay_slot_id) as? String

    private fun View.sessionReplaySlotRegistration(): EmbeddedContentSlotRegistration? =
        getTag(R.id.datadog_session_replay_slot_registration) as? EmbeddedContentSlotRegistration

    /** Direct port of legacy `EmbeddedContentViewMapper.EmbeddedContentViewCache`. */
    private class EmbeddedContentCache(
        private val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry
    ) {
        private data class Entry(val identity: CapturedIdentity, val lastSeenCapture: Long)

        private val entries = mutableMapOf<String, Entry>()
        private var currentCapture: Long = 0

        @UiThread
        fun beginCapture() {
            currentCapture++
        }

        @UiThread
        fun record(slotId: String, identity: CapturedIdentity) {
            entries[slotId] = Entry(identity, currentCapture)
        }

        @UiThread
        // Iterator is locally guarded by hasNext; this mutable map supports iterator removal.
        @Suppress("UnsafeThirdPartyFunctionCall")
        fun hiddenWireframes(): List<CapturedWireframe> {
            val hidden = mutableListOf<CapturedWireframe>()
            val activeSlotIds = embeddedContentSlotRegistry.activeSlotIds()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val (slotId, entry) = iterator.next()
                if (slotId !in activeSlotIds) {
                    iterator.remove()
                } else if (entry.lastSeenCapture != currentCapture) {
                    hidden += CapturedWireframe.EmbeddedContent(
                        identity = entry.identity,
                        bounds = CapturedBounds(0, 0, 0, 0),
                        slotId = slotId,
                        isVisible = false
                    )
                }
            }
            return hidden
        }
    }
}
