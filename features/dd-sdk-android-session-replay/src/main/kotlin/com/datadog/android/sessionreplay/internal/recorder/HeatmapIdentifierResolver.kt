/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.heatmaps.heatmapViewKey
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.utils.isValidTapTarget

internal class HeatmapIdentifierResolver(
    private val appPackageName: String,
    private val registry: HeatmapIdentifierRegistry,
    private val internalLogger: InternalLogger
) {

    private var lastPublishedScreenName: String? = null
    private val lastPublishedEntries: MutableMap<Long, CachedHeatmapEntry> = mutableMapOf()
    private val resourceNameCache: HashMap<Int, String> = HashMap()

    @UiThread
    fun beginTraversal(viewUrl: String): TraversalContext = TraversalContext(viewUrl)

    @UiThread
    private fun resolveIdentity(
        view: View,
        nodePath: List<String>,
        typeIndex: Int,
        context: TraversalContext
    ): HeatmapIdentity {
        if (!view.isValidTapTarget()) {
            return HeatmapIdentity(viewPath = nodePath + pathComponentFor(view, typeIndex), identifier = null)
        }

        val identityHash = heatmapViewKey(view)
        val pathComponent = pathComponentFor(view, typeIndex)
        val cachedEntry = lastPublishedEntries[identityHash]
            ?.takeIf { context.viewUrl == lastPublishedScreenName }
            // Guards against stale entries when a view or any ancestor shifts among same-type
            // siblings without being detached (e.g. notifyItemMoved with an in-place animator).
            ?.takeIf { it.viewPath == nodePath + pathComponent }
        val viewPath: List<String>
        val identifier: HeatmapIdentifier?
        if (cachedEntry != null) {
            context.entries[identityHash] = cachedEntry
            viewPath = cachedEntry.viewPath
            identifier = cachedEntry.identifier
        } else {
            viewPath = nodePath + pathComponent
            identifier = HeatmapIdentifier.create(
                elementPath = viewPath,
                screenName = context.viewUrl,
                appPackageName = appPackageName,
                onHashingFailure = { error -> logHashingFailure(error) }
            )?.also { context.entries[identityHash] = CachedHeatmapEntry(it, viewPath) }
        }
        return HeatmapIdentity(viewPath = viewPath, identifier = identifier)
    }

    // #typeIndex is appended to containers too: rows sharing a resource name (e.g. RecyclerView
    // items) must produce distinct path components so their descendants get distinct permanentIds.
    @VisibleForTesting
    internal fun pathComponentFor(view: View, typeIndex: Int): String {
        val viewId = view.id
        if (viewId != View.NO_ID) {
            val name = resourceNameCache[viewId] ?: resolveAndCacheResourceName(viewId, view.resources)
            if (!name.isNullOrEmpty()) {
                return "$name#$typeIndex"
            }
        }
        return "$LOCAL_KEY_CLASS_PREFIX${view.javaClass.name}#$typeIndex"
    }

    private fun resolveAndCacheResourceName(viewId: Int, resources: Resources?): String? {
        val resolved = resources?.let {
            try {
                @Suppress("UnsafeThirdPartyFunctionCall") // can throw Resources.NotFoundException
                it.getResourceName(viewId)
            } catch (_: Resources.NotFoundException) {
                null
            }
        }
        if (resolved != null) resourceNameCache[viewId] = resolved
        return resolved
    }

    @VisibleForTesting
    internal fun computeChildTypeIndices(parent: ViewGroup): IntArray {
        val typeCounts = mutableMapOf<Class<*>, Int>()
        val result = IntArray(parent.childCount)
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            val cls = child.javaClass
            val index = typeCounts[cls] ?: 0
            typeCounts[cls] = index + 1
            result[i] = index
        }
        return result
    }

    @UiThread
    private fun publishIfChanged(context: TraversalContext) {
        if (context.entries.isEmpty()) {
            lastPublishedScreenName = null
            lastPublishedEntries.clear()
        } else {
            val anyIdentifierChangedOrNew = context.entries.any { (k, v) ->
                lastPublishedEntries[k]?.identifier != v.identifier
            }
            val hierarchyChanged = context.viewUrl != lastPublishedScreenName ||
                context.entries.size != lastPublishedEntries.size ||
                anyIdentifierChangedOrNew
            if (hierarchyChanged) {
                lastPublishedScreenName = context.viewUrl
                lastPublishedEntries.clear()
                lastPublishedEntries.putAll(context.entries)
                registry.setHeatmapIdentifiers(
                    context.entries.mapValues { it.value.identifier },
                    context.viewUrl
                )
            }
        }
    }

    private fun logHashingFailure(error: Throwable) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { HASHING_FAILURE_MESSAGE },
            error
        )
    }

    data class HeatmapIdentity(
        val viewPath: List<String>,
        val identifier: HeatmapIdentifier?
    )

    internal class CachedHeatmapEntry(
        val identifier: HeatmapIdentifier,
        val viewPath: List<String>
    )

    @UiThread
    inner class TraversalContext(val viewUrl: String) {
        val entries: MutableMap<Long, CachedHeatmapEntry> = mutableMapOf()

        @UiThread
        fun resolveIdentity(view: View, nodePath: List<String>, typeIndex: Int): HeatmapIdentity =
            this@HeatmapIdentifierResolver.resolveIdentity(view, nodePath, typeIndex, this)

        @UiThread
        fun computeChildTypeIndices(parent: ViewGroup): IntArray =
            this@HeatmapIdentifierResolver.computeChildTypeIndices(parent)

        @UiThread
        fun publish() = this@HeatmapIdentifierResolver.publishIfChanged(this)
    }

    companion object {
        internal const val HASHING_FAILURE_MESSAGE =
            "Failed to hash heatmap identifier; this view will not be correlated with RUM action events."

        @VisibleForTesting
        internal const val LOCAL_KEY_CLASS_PREFIX = "cls:"
    }
}
