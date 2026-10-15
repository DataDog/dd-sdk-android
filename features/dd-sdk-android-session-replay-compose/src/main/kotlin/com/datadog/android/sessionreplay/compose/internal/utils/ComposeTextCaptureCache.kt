/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.font.FontFamily
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.getClassSafe
import com.datadog.android.sessionreplay.compose.internal.reflection.getDeclaredFieldSafe
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import java.lang.ref.WeakReference

/**
 * Reuses mapped text only while the actual measured paragraph and its copied layout inputs match.
 * Entries weakly reference the paragraph, modifier, text and font family so the cache cannot retain
 * a disposed Compose hierarchy or extend the lifetime of captured text. Unknown internals and stale
 * asynchronous fonts use the normal semantics action instead. Used only by the composition pipeline.
 */
internal class ComposeTextCaptureCache {
    class State(val paragraph: Any, val constraints: Long, val size: Long)
    private data class Entry(
        val paragraph: WeakReference<Any>,
        val modifier: WeakReference<Modifier>,
        val constraints: Long,
        val size: Long,
        val fontFamily: WeakReference<FontFamily>?,
        val text: WeakReference<String>,
        val textLength: Int,
        val result: TextLayoutInfo
    )
    private val entries = LinkedHashMap<Int, Entry>()
    private var cachedCharacters = 0

    @Suppress("ReturnCount") // Missing or stale internals must fall back without caching.
    fun state(node: SemanticsNode, action: Any): State? {
        val chain = ComposeReflection.NodesFieldOfLayoutNode?.getSafe(node.layoutInfo)
        var current = ComposeReflection.HeadFieldOfNodeChain?.getSafe(chain)
        repeat(MAX_MODIFIER_NODES) {
            if (current == null) return null
            if (simpleNode?.isInstance(current) == true) {
                if (semanticsAction?.getSafe(current) !== action) return null
                val cache = layoutCache?.getSafe(current) ?: return null
                val currentParagraph = paragraph?.getSafe(cache) ?: return null
                val currentIntrinsics = intrinsics?.getSafe(cache) as? ParagraphIntrinsics ?: return null
                if (currentIntrinsics.hasStaleResolvedFonts) return null
                val currentConstraints = constraints?.getSafe(cache) as? Long ?: return null
                val currentSize = size?.getSafe(cache) as? Long ?: return null
                return State(currentParagraph, currentConstraints, currentSize)
            }
            current = ComposeReflection.ChildFieldOfModifierNode?.getSafe(current)
        }
        return null
    }

    @Suppress("ReturnCount") // Reclaimed weak references invalidate the entry immediately.
    fun get(nodeId: Int, state: State?, modifier: Modifier?): TextLayoutInfo? {
        if (state == null || modifier == null) return null
        val entry = entries[nodeId] ?: return null
        val sameOwners = entry.paragraph.get() === state.paragraph && entry.modifier.get() === modifier
        val sameLayout = entry.constraints == state.constraints && entry.size == state.size
        if (!sameOwners || !sameLayout) return null
        val text = entry.text.get() ?: return null
        val family = entry.fontFamily?.get()
        if (entry.fontFamily != null && family == null) return null
        return entry.result.copy(text = text, fontFamily = family)
    }

    // Private map, valid non-null weak referents, and eviction only while the map is nonempty.
    @Suppress("UnsafeThirdPartyFunctionCall")
    fun put(nodeId: Int, state: State?, modifier: Modifier?, result: TextLayoutInfo) {
        if (state == null || modifier == null) return
        entries.remove(nodeId)?.let { cachedCharacters -= it.textLength }
        if (result.text.length > MAX_ENTRY_CHARACTERS) return
        while (entries.size >= MAX_ENTRIES || cachedCharacters + result.text.length > MAX_CACHED_CHARACTERS) {
            val iterator = entries.entries.iterator()
            val oldest = iterator.next()
            cachedCharacters -= oldest.value.textLength
            iterator.remove()
        }
        val family = result.fontFamily?.let { WeakReference(it) }
        entries[nodeId] =
            Entry(
                WeakReference(state.paragraph),
                WeakReference(modifier),
                state.constraints,
                state.size,
                family,
                WeakReference(result.text),
                result.text.length,
                result.copy(text = "", fontFamily = null)
            )
        cachedCharacters += result.text.length
    }

    private companion object {
        const val MAX_MODIFIER_NODES = 64
        const val MAX_ENTRIES = 256
        const val MAX_ENTRY_CHARACTERS = 16_384
        const val MAX_CACHED_CHARACTERS = 131_072
        private val simpleNode = getClassSafe("androidx.compose.foundation.text.modifiers.TextStringSimpleNode", false)
        private val layoutCache = simpleNode?.getDeclaredFieldSafe("_layoutCache", false)
        private val semanticsAction = simpleNode?.getDeclaredFieldSafe("semanticsTextLayoutResult", false)
        private val cacheClass = getClassSafe("androidx.compose.foundation.text.modifiers.ParagraphLayoutCache", false)
        private val paragraph = cacheClass?.getDeclaredFieldSafe("paragraph", false)
        private val intrinsics = cacheClass?.getDeclaredFieldSafe("paragraphIntrinsics", false)
        private val constraints = cacheClass?.getDeclaredFieldSafe("prevConstraints", false)
        private val size = cacheClass?.getDeclaredFieldSafe("layoutSize", false)
    }
}
