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
import com.datadog.android.sessionreplay.recorder.composition.CompositionTextResourceKey
import com.datadog.android.sessionreplay.recorder.composition.CompositionTextResources
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
        val bitmap: WeakReference<Bitmap>,
        val resourceKey: CompositionTextResourceKey
    )

    private val entries = LinkedHashMap<Int, Entry>()

    fun get(nodeId: Int, state: ComposeTextCaptureCache.State?, paint: Paint): Bitmap? {
        val entry = findEntry(nodeId, state, paint) ?: return null
        val bitmap = entry.bitmap.get()
        return if (bitmap == null || bitmap.isRecycled) null else bitmap
    }

    /** Called only after a bitmap miss; the same layout/paint checks guard the resource key. */
    fun findResourceId(
        nodeId: Int,
        state: ComposeTextCaptureCache.State?,
        paint: Paint,
        resources: CompositionTextResources?
    ): String? {
        if (resources == null) return null
        return findEntry(nodeId, state, paint)?.let { resources.findResourceId(it.resourceKey) }
    }

    @Suppress("ReturnCount") // Reclaimed or changed inputs cannot authorize either kind of cache hit.
    private fun findEntry(nodeId: Int, state: ComposeTextCaptureCache.State?, paint: Paint): Entry? {
        if (state == null || paint.modifier == null) return null
        val entry = entries[nodeId] ?: return null
        val sameLayout = entry.paragraph.get() === state.paragraph &&
            entry.constraints == state.constraints && entry.size == state.size
        val samePaint = entry.modifier.get() === paint.modifier && entry.color == paint.color
        if (!sameLayout || !samePaint || entry.sourceText.get() != paint.sourceText) return null
        return entry
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Owned map and eviction only while nonempty.
    fun put(
        nodeId: Int,
        state: ComposeTextCaptureCache.State?,
        paint: Paint,
        style: TextStyle,
        bitmap: Bitmap
    ): CompositionTextResourceKey? {
        // A brush can paint from mutable shader state without replacing the text modifier.
        val modifier = paint.modifier
        if (state == null || modifier == null || style.brush != null) return null
        entries.remove(nodeId)
        while (entries.size >= MAX_ENTRIES) {
            val oldest = entries.entries.iterator()
            oldest.next()
            oldest.remove()
        }
        val resourceKey = CompositionTextResourceKey()
        entries[nodeId] = Entry(
            WeakReference(state.paragraph),
            state.constraints,
            state.size,
            WeakReference(modifier),
            paint.color,
            WeakReference(paint.sourceText),
            WeakReference(bitmap),
            resourceKey
        )
        return resourceKey
    }

    private companion object {
        const val MAX_ENTRIES = 128
    }
}
