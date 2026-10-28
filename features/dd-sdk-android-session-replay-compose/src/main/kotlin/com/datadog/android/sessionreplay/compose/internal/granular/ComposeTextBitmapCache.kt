/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeTextCaptureCache
import java.lang.ref.WeakReference

/** Immutable text modifiers and measured paragraphs let unchanged glyphs bypass layout reconstruction. */
internal class ComposeTextBitmapCache {
    data class Paint(val modifier: Modifier?, val color: Long, val sourceText: String)

    private data class Entry(
        val paragraph: WeakReference<Any>,
        val constraints: Long,
        val size: Long,
        val modifier: WeakReference<Modifier>,
        val color: Long,
        val sourceText: WeakReference<String>,
        val bitmap: WeakReference<Bitmap>
    )

    private val entries = LinkedHashMap<Int, Entry>()

    @Suppress("ReturnCount") // Missing, changed and reclaimed measured inputs are ordinary cache misses.
    fun get(nodeId: Int, state: ComposeTextCaptureCache.State?, paint: Paint): Bitmap? {
        if (state == null || paint.modifier == null) return null
        val entry = entries[nodeId] ?: return null
        val sameLayout = entry.paragraph.get() === state.paragraph &&
            entry.constraints == state.constraints && entry.size == state.size
        val samePaint = entry.modifier.get() === paint.modifier && entry.color == paint.color
        if (!sameLayout || !samePaint || entry.sourceText.get() != paint.sourceText) return null
        val bitmap = entry.bitmap.get()
        return if (bitmap == null || bitmap.isRecycled) null else bitmap
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Owned map and eviction only while nonempty.
    fun put(nodeId: Int, state: ComposeTextCaptureCache.State?, paint: Paint, style: TextStyle, bitmap: Bitmap) {
        // A brush can paint from mutable shader state without replacing the text modifier.
        val modifier = paint.modifier
        if (state == null || modifier == null || style.brush != null) return
        entries.remove(nodeId)
        while (entries.size >= MAX_ENTRIES) {
            val oldest = entries.entries.iterator()
            oldest.next()
            oldest.remove()
        }
        entries[nodeId] = Entry(
            WeakReference(state.paragraph),
            state.constraints,
            state.size,
            WeakReference(modifier),
            paint.color,
            WeakReference(paint.sourceText),
            WeakReference(bitmap)
        )
    }

    private companion object {
        const val MAX_ENTRIES = 128
    }
}
