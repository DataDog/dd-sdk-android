/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistration
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class EmbeddedContentViewMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry
) : BaseWireframeMapper<View>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {
    private val cache = EmbeddedContentViewCache(embeddedContentSlotRegistry)
    private var isSnapshotActive = false

    @UiThread
    fun hasSlotId(view: View): Boolean = view.sessionReplaySlotId() != null

    @UiThread
    fun beginSnapshot() {
        isSnapshotActive = !cache.isEmpty() || embeddedContentSlotRegistry.hasMarkedSlots()
        if (isSnapshotActive) {
            cache.beginSnapshot()
        }
    }

    @UiThread
    fun finishSnapshot(): Node? {
        if (!isSnapshotActive) {
            return null
        }
        isSnapshotActive = false
        val hiddenWireframes = cache.hiddenWireframes()
        return hiddenWireframes.takeIf { it.isNotEmpty() }?.let { Node(wireframes = it) }
    }

    @UiThread
    @Suppress("ReturnCount")
    override fun map(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val registration = view.sessionReplaySlotRegistration() ?: return emptyList()
        val slotId = registration.slotId
        embeddedContentSlotRegistry.track(registration)
        isSnapshotActive = true
        val wireframeId = viewIdentifierResolver.resolveChildUniqueIdentifier(
            view,
            EMBEDDED_CONTENT_KEY_NAME
        ) ?: return emptyList()
        cache.record(slotId, wireframeId)

        if (viewUtilsInternal.isNotVisible(view)) {
            return wireframeList(hiddenWireframe(wireframeId, slotId))
        }

        val bounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val shapeStyle = view.background?.let {
            resolveShapeStyle(it, view.alpha, internalLogger)
        }

        return wireframeList(
            MobileSegment.Wireframe.EmbeddedContentWireframe(
                id = wireframeId,
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                shapeStyle = shapeStyle,
                slotId = slotId,
                isVisible = true
            )
        )
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Kotlin listOf cannot fail for this local value.
    private fun wireframeList(wireframe: MobileSegment.Wireframe): List<MobileSegment.Wireframe> {
        return listOf(wireframe)
    }

    private fun hiddenWireframe(
        wireframeId: Long,
        slotId: String
    ): MobileSegment.Wireframe.EmbeddedContentWireframe {
        return MobileSegment.Wireframe.EmbeddedContentWireframe(
            id = wireframeId,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            slotId = slotId,
            isVisible = false
        )
    }

    private fun View.sessionReplaySlotId(): String? {
        return getTag(R.id.datadog_session_replay_slot_id) as? String
    }

    private fun View.sessionReplaySlotRegistration(): EmbeddedContentSlotRegistration? {
        return getTag(R.id.datadog_session_replay_slot_registration) as? EmbeddedContentSlotRegistration
    }

    internal companion object {
        internal const val EMBEDDED_CONTENT_KEY_NAME = "embedded_content"
    }

    private class EmbeddedContentViewCache(
        private val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry
    ) {
        private data class Entry(
            val wireframeId: Long,
            var lastSeenSnapshot: Long
        )

        private val entries = mutableMapOf<String, Entry>()
        private var currentSnapshot: Long = 0

        @UiThread
        fun beginSnapshot() {
            currentSnapshot++
        }

        @UiThread
        fun isEmpty(): Boolean = entries.isEmpty()

        @UiThread
        fun record(slotId: String, wireframeId: Long) {
            entries[slotId] = Entry(wireframeId, currentSnapshot)
        }

        @UiThread
        // Iterator is locally guarded by hasNext; this mutable map supports iterator removal.
        @Suppress("UnsafeThirdPartyFunctionCall")
        fun hiddenWireframes(): List<MobileSegment.Wireframe> {
            val hiddenWireframes = mutableListOf<MobileSegment.Wireframe>()
            val activeSlotIds = embeddedContentSlotRegistry.activeSlotIds()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val (slotId, entry) = iterator.next()
                if (slotId !in activeSlotIds) {
                    iterator.remove()
                } else if (entry.lastSeenSnapshot != currentSnapshot) {
                    hiddenWireframes += MobileSegment.Wireframe.EmbeddedContentWireframe(
                        id = entry.wireframeId,
                        x = 0,
                        y = 0,
                        width = 0,
                        height = 0,
                        slotId = slotId,
                        isVisible = false
                    )
                }
            }
            return hiddenWireframes
        }
    }
}
