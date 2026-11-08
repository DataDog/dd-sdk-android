/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
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
    class State(
        val paragraph: Any,
        val constraints: Long,
        val size: Long,
        val measured: MeasuredComposeText? = null,
        val sourceNode: Any? = null,
        val sourceCache: Any? = null,
        val intrinsics: ParagraphIntrinsics? = null
    )
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
                if (substitution?.getSafe(current) != null) return null
                val cache = layoutCache?.getSafe(current) ?: return null
                val currentParagraph = paragraph?.getSafe(cache) ?: return null
                val currentIntrinsics = intrinsics?.getSafe(cache) as? ParagraphIntrinsics ?: return null
                if (currentIntrinsics.hasStaleResolvedFonts) return null
                val currentConstraints = constraints?.getSafe(cache) as? Long ?: return null
                val currentSize = size?.getSafe(cache) as? Long ?: return null
                return State(
                    currentParagraph,
                    currentConstraints,
                    currentSize,
                    measuredText(current, cache, currentParagraph, currentSize),
                    current,
                    cache,
                    currentIntrinsics
                )
            }
            current = ComposeReflection.ChildFieldOfModifierNode?.getSafe(current)
        }
        return null
    }

    /** A metadata miss can reuse the measured paragraph without reconstructing a MultiParagraph. */
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Checked owners and UI-thread paragraph access.
    fun measuredLayout(state: State?): MeasuredTextLayout? {
        val node = state?.sourceNode ?: return null
        val cache = state.sourceCache ?: return null
        if (!hasNoSubstitution(node)) return null
        val text = sourceText?.getSafe(node) as? String ?: return null
        if (cachedText?.getSafe(cache) != text) return null
        val paragraph = state.paragraph as? Paragraph ?: return null
        val intrinsics = state.intrinsics ?: return null
        val wrap = softWrap?.getSafe(cache) as? Boolean ?: return null
        val size = IntSize((state.size shr Int.SIZE_BITS).toInt(), state.size.toInt())
        if (!MeasuredTextLayout.canReuse(paragraph, intrinsics, Constraints(state.constraints), size, wrap)) return null
        val style = measuredStyle(node) ?: return null
        return MeasuredTextLayout(text, style, paragraph.lineCount > 1)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Checked instance field; reflection failures use fallback.
    private fun hasNoSubstitution(node: Any): Boolean {
        val field = substitution ?: return false
        return try {
            field.get(node) == null
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ReflectiveOperationException) {
            false
        }
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Checked field and immutable style merge.
    private fun measuredStyle(node: Any): TextStyle? {
        val style = sourceStyle?.getSafe(node) as? TextStyle ?: return null
        if (style.brush != null) return null
        val field = overrideColor ?: return null
        val color = try {
            when (val producer = field.get(node)) {
                null -> Color.Unspecified
                is ColorProducer -> producer.invoke()
                else -> return null
            }
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: ReflectiveOperationException) {
            return null
        }
        // Preserve the action's first producer read, then the mapper reads the modifier color.
        return style.merge(color = color)
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Guard measured inputs and copied size.
    private fun measuredText(node: Any?, cache: Any, paragraph: Any, size: Long): MeasuredComposeText? {
        if (substitution == null || substitution.getSafe(node) != null) return null
        val text = sourceText?.getSafe(node) as? String ?: return null
        if (cachedText?.getSafe(cache) != text) return null
        val style = sourceStyle?.getSafe(node) as? TextStyle ?: return null
        val measuredParagraph = paragraph as? Paragraph ?: return null
        val paint = MeasuredComposeText.readPaint(measuredParagraph) ?: return null
        return MeasuredComposeText(
            measuredParagraph,
            text,
            style,
            IntSize((size shr Int.SIZE_BITS).toInt(), size.toInt()),
            paint
        )
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
        private val sourceText = simpleNode?.getDeclaredFieldSafe("text", false)
        private val sourceStyle = simpleNode?.getDeclaredFieldSafe("style", false)
        private val substitution = simpleNode?.getDeclaredFieldSafe("textSubstitution", false)
        private val overrideColor = simpleNode?.getDeclaredFieldSafe("overrideColor", false)
        private val layoutCache = simpleNode?.getDeclaredFieldSafe("_layoutCache", false)
        private val semanticsAction = simpleNode?.getDeclaredFieldSafe("semanticsTextLayoutResult", false)
        private val cacheClass = getClassSafe("androidx.compose.foundation.text.modifiers.ParagraphLayoutCache", false)
        private val cachedText = cacheClass?.getDeclaredFieldSafe("text", false)
        private val softWrap = cacheClass?.getDeclaredFieldSafe("softWrap", false)
        private val paragraph = cacheClass?.getDeclaredFieldSafe("paragraph", false)
        private val intrinsics = cacheClass?.getDeclaredFieldSafe("paragraphIntrinsics", false)
        private val constraints = cacheClass?.getDeclaredFieldSafe("prevConstraints", false)
        private val size = cacheClass?.getDeclaredFieldSafe("layoutSize", false)
    }
}
