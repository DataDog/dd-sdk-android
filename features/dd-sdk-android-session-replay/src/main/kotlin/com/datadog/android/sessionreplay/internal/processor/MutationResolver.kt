/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList
import java.util.Locale

internal class MutationResolver(private val internalLogger: InternalLogger) {

    /**
     * Computes a diff between two arrays.
     * This implementation is based on Paul Heckel's algorithm for finding differences
     * between files. It isolates differences in a way that corresponds
     * closely to our intuitive notion of difference (it finds the longest common subsequence).
     * It is computationally efficient: `O(n)` in time and memory.
     *
     * Unlike original Heckel's algorithm, our implementation assumes that elements are unique
     * within each of two arrays. It means that all elements in
     * `oldArray` are guaranteed to have different `id` (same for `newArray`). Elements with
     * the same `id` can appear in both arrays, which
     * indicates one of two things determined by `newElement == oldElement`
     * either the element was not altered and can be skipped in diff,
     * or the element was changed and it should be reflect in the output.
     *
     *
     * Like original algorithm, our implementation uses 6 passes over both arrays to determine diff
     *
     * Ref.:
     * [A technique for isolating differences between files_ Paul Heckel (1978)]
     * (https://dl.acm.org/citation.cfm?id=359467)
     * @param oldSnapshot the prev snapshot
     * @param newSnapshot the current snapshot
     * @return the mutations as [MobileSegment.MobileIncrementalData.MobileMutationData]
     * describing changes from `oldArray` to `newArray` or null if no difference was found.
     */
    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth", "ReturnCount")
    internal fun resolveMutations(
        oldSnapshot: List<MobileSegment.Wireframe>,
        newSnapshot: List<MobileSegment.Wireframe>
    ): MobileSegment.MobileIncrementalData.MobileMutationData? {
        val table = mutableMapOf<Long, Symbol>()
        // old array entries
        val oa = mutableListOf<Entry>()
        // new array entries
        val na = mutableListOf<Entry>()

        // 1st pass
        // Read `newArray` and store info on each element in symbols `table`:
        newSnapshot.forEach {
            val elementId = it.id()
            table[elementId] = Symbol(inOld = false, inNew = true)
            na.add(Entry.Reference(elementId))
        }

        // 2nd pass
        // Read `oldArray` and store info on each element in symbols `table`.
        // If certain element already exists, update its information (otherwise create new entry):
        oldSnapshot.forEachIndexed { index, element ->
            val elementId = element.id()
            if (!table.containsKey(elementId)) {
                table[elementId] = Symbol(inOld = true, inNew = false, indexInOld = index)
            } else {
                table[elementId]?.inOld = true
                table[elementId]?.indexInOld = index
            }
            oa.add(Entry.Reference(elementId))
        }

        // 3rd pass
        // Uses "Observation 1":
        // > If a line occurs only once in each file, then it must be the same line,
        // although it may have been moved.
        // > We use this observation to locate unaltered lines that we subsequently
        // exclude from further treatment.
        na.forEachIndexed { index, entry ->
            if (entry is Entry.Reference) {
                // make sure the entry is in the old
                val symbol = table[entry.id] ?: return null // something really bad happened
                if (symbol.inOld && symbol.inNew) {
                    val indexInOld = symbol.indexInOld ?: return null
                    na[index] = Entry.Index(indexInOld)
                    oa[indexInOld] = Entry.Index(index)
                }
            }
        }

        // 4th pass
        // > If a line has been found to be unaltered, and the lines immediately adjacent
        // to it in both files are identical,
        // > then these lines must be the same line.
        // This information can be used to find blocks of unchanged lines.
        for (i in 1 until (na.size - 1)) {
            val entry = na[i]
            if (entry is Entry.Index && (entry.index + 1) < oa.size) {
                val nextNewEntry = na[i + 1]
                val nextOldEntry = oa[entry.index + 1]

                if (nextNewEntry is Entry.Reference &&
                    nextOldEntry is Entry.Reference &&
                    nextOldEntry.id == nextNewEntry.id
                ) {
                    na[i + 1] = Entry.Index(entry.index + 1)
                    oa[entry.index + 1] = Entry.Index(i + 1)
                }
            }
        }

        // 5th pass
        // Similar to 4th pass, except it processes entries in descending order.
        for (i in (1 until (na.size - 1)).reversed()) {
            val entry = na[i]
            if (entry is Entry.Index && entry.index - 1 >= 0) {
                val prevNewEntry = na[i - 1]
                val prevOldEntry = oa[entry.index - 1]
                if (prevNewEntry is Entry.Reference &&
                    prevOldEntry is Entry.Reference &&
                    prevOldEntry.id == prevNewEntry.id
                ) {
                    na[i - 1] = Entry.Index(entry.index - 1)
                    oa[entry.index - 1] = Entry.Index(i - 1)
                }
            }
        }

        // Final pass
        // Constructing the actual diff from information stored in `oa` and `na`.
        val updates = LinkedList<MobileSegment.WireframeUpdateMutation>()
        val adds = LinkedList<MobileSegment.Add>()
        val removes = LinkedList<MobileSegment.Remove>()
        val removalOffsets = IntArray(oldSnapshot.size)
        var runningOffset = 0

        oa.forEachIndexed { index, entry ->
            removalOffsets[index] = runningOffset
            if (entry is Entry.Reference) {
                // Old element was removed
                removes.add(MobileSegment.Remove(oldSnapshot[index].id()))
                runningOffset++
            }
        }

        runningOffset = 0

        na.forEachIndexed { index, entry ->
            when (entry) {
                is Entry.Index -> {
                    val indexInOld = entry.index
                    val removalOffset = removalOffsets[indexInOld]
                    val newElement = newSnapshot[index]
                    val oldElement = oldSnapshot[indexInOld]
                    if ((indexInOld - removalOffset + runningOffset) != index) {
                        // Old element was moved to another position:
                        val previousId = if (index > 0) newSnapshot[index - 1].id() else null
                        removes.add(MobileSegment.Remove(newSnapshot[index].id()))
                        adds.add(MobileSegment.Add(previousId = previousId, newSnapshot[index]))
                    } else if (newElement != oldElement) {
                        // Existing element is on the right position, but its data is different
                        resolveUpdateMutation(
                            prevWireframe = oldElement,
                            currentWireframe = newElement
                        )?.let {
                            updates.add(it)
                        }
                    } // else - element was not moved and not changed, so: skip
                }
                is Entry.Reference -> {
                    // New element was added:
                    val previousId = if (index > 0) newSnapshot[index - 1].id() else null
                    adds.add(
                        MobileSegment.Add(
                            previousId = previousId,
                            wireframe = newSnapshot[index]
                        )
                    )
                    runningOffset++
                }
            }
        }

        return if (adds.isNotEmpty() || removes.isNotEmpty() || updates.isNotEmpty()) {
            MobileSegment.MobileIncrementalData.MobileMutationData(
                adds = adds,
                removes = removes,
                updates = updates
            )
        } else {
            null
        }
    }

    // TODO: RUMM-2481 Use the `diff` method int the ShapeWireframe type when available
    @Suppress("ComplexMethod")
    private fun resolveShapeMutation(
        prevWireframe: MobileSegment.Wireframe.ShapeWireframe,
        currentWireframe: MobileSegment.Wireframe.ShapeWireframe
    ): MobileSegment.WireframeUpdateMutation {
        var mutation = MobileSegment.WireframeUpdateMutation
            .ShapeWireframeUpdate(currentWireframe.id)
        if (prevWireframe.x != currentWireframe.x) {
            mutation = mutation.copy(x = currentWireframe.x)
        }
        if (prevWireframe.y != currentWireframe.y) {
            mutation = mutation.copy(y = currentWireframe.y)
        }
        if (prevWireframe.width != currentWireframe.width) {
            mutation = mutation.copy(width = currentWireframe.width)
        }
        if (prevWireframe.height != currentWireframe.height) {
            mutation = mutation.copy(height = currentWireframe.height)
        }
        if (prevWireframe.border != currentWireframe.border) {
            mutation = mutation.copy(border = currentWireframe.border)
        }
        if (prevWireframe.shapeStyle != currentWireframe.shapeStyle) {
            mutation = mutation.copy(shapeStyle = currentWireframe.shapeStyle)
        }
        if (prevWireframe.clip != currentWireframe.clip) {
            mutation = mutation.copy(
                clip = currentWireframe.clip
                    ?: MobileSegment.WireframeClip(0, 0, 0, 0)
            )
        }

        return mutation
    }

    private fun resolvePlaceholderMutation(
        prevWireframe: MobileSegment.Wireframe.PlaceholderWireframe,
        currentWireframe: MobileSegment.Wireframe.PlaceholderWireframe
    ): MobileSegment.WireframeUpdateMutation {
        var mutation = MobileSegment.WireframeUpdateMutation
            .PlaceholderWireframeUpdate(currentWireframe.id)
        if (prevWireframe.x != currentWireframe.x) {
            mutation = mutation.copy(x = currentWireframe.x)
        }
        if (prevWireframe.y != currentWireframe.y) {
            mutation = mutation.copy(y = currentWireframe.y)
        }
        if (prevWireframe.width != currentWireframe.width) {
            mutation = mutation.copy(width = currentWireframe.width)
        }
        if (prevWireframe.height != currentWireframe.height) {
            mutation = mutation.copy(height = currentWireframe.height)
        }
        if (prevWireframe.label != currentWireframe.label) {
            mutation = mutation.copy(label = currentWireframe.label)
        }
        if (prevWireframe.clip != currentWireframe.clip) {
            mutation = mutation.copy(
                clip = currentWireframe.clip
                    ?: MobileSegment.WireframeClip(0, 0, 0, 0)
            )
        }

        return mutation
    }

    private fun resolveImageMutation(
        prevWireframe: MobileSegment.Wireframe.ImageWireframe,
        currentWireframe: MobileSegment.Wireframe.ImageWireframe
    ): MobileSegment.WireframeUpdateMutation {
        var mutation = MobileSegment.WireframeUpdateMutation
            .ImageWireframeUpdate(currentWireframe.id)
        if (prevWireframe.x != currentWireframe.x) {
            mutation = mutation.copy(x = currentWireframe.x)
        }
        if (prevWireframe.y != currentWireframe.y) {
            mutation = mutation.copy(y = currentWireframe.y)
        }
        if (prevWireframe.width != currentWireframe.width) {
            mutation = mutation.copy(width = currentWireframe.width)
        }
        if (prevWireframe.height != currentWireframe.height) {
            mutation = mutation.copy(height = currentWireframe.height)
        }
        if (prevWireframe.border != currentWireframe.border) {
            mutation = mutation.copy(border = currentWireframe.border)
        }
        if (prevWireframe.shapeStyle != currentWireframe.shapeStyle) {
            mutation = mutation.copy(shapeStyle = currentWireframe.shapeStyle)
        }
        if (prevWireframe.clip != currentWireframe.clip) {
            mutation = mutation.copy(
                clip = currentWireframe.clip
                    ?: MobileSegment.WireframeClip(0, 0, 0, 0)
            )
        }
        if (prevWireframe.base64 != currentWireframe.base64) {
            mutation = mutation.copy(base64 = currentWireframe.base64)
        }
        if (prevWireframe.mimeType != currentWireframe.mimeType) {
            mutation = mutation.copy(mimeType = currentWireframe.mimeType)
        }
        if (prevWireframe.isEmpty != currentWireframe.isEmpty) {
            mutation = mutation.copy(isEmpty = currentWireframe.isEmpty)
        }

        return mutation
    }

    private fun resolveUpdateMutation(
        currentWireframe: MobileSegment.Wireframe,
        prevWireframe: MobileSegment.Wireframe
    ): MobileSegment.WireframeUpdateMutation? {
        return if (prevWireframe == currentWireframe) {
            null
        } else if (!prevWireframe.javaClass.isAssignableFrom(currentWireframe.javaClass)) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                {
                    MISS_MATCHING_TYPES_IN_SNAPSHOTS_ERROR_MESSAGE_FORMAT
                        .format(
                            Locale.ENGLISH,
                            prevWireframe.javaClass.name,
                            currentWireframe.javaClass.name
                        )
                }
            )
            null
        } else {
            when (prevWireframe) {
                is MobileSegment.Wireframe.TextWireframe -> resolveTextMutation(
                    prevWireframe,
                    currentWireframe as MobileSegment.Wireframe.TextWireframe
                )
                is MobileSegment.Wireframe.ShapeWireframe -> resolveShapeMutation(
                    prevWireframe,
                    currentWireframe as MobileSegment.Wireframe.ShapeWireframe
                )
                is MobileSegment.Wireframe.ImageWireframe -> resolveImageMutation(
                    prevWireframe,
                    currentWireframe as MobileSegment.Wireframe.ImageWireframe
                )
                is MobileSegment.Wireframe.PlaceholderWireframe -> resolvePlaceholderMutation(
                    prevWireframe,
                    currentWireframe as MobileSegment.Wireframe.PlaceholderWireframe
                )
            }
        }
    }

    // TODO: RUMM-2481 Use the `diff` method int the TextWireframe type when available
    @Suppress("ComplexMethod")
    private fun resolveTextMutation(
        prevWireframe: MobileSegment.Wireframe.TextWireframe,
        currentWireframe: MobileSegment.Wireframe.TextWireframe
    ): MobileSegment.WireframeUpdateMutation {
        var mutation = MobileSegment.WireframeUpdateMutation
            .TextWireframeUpdate(currentWireframe.id)
        if (prevWireframe.x != currentWireframe.x) {
            mutation = mutation.copy(x = currentWireframe.x)
        }
        if (prevWireframe.y != currentWireframe.y) {
            mutation = mutation.copy(y = currentWireframe.y)
        }
        if (prevWireframe.width != currentWireframe.width) {
            mutation = mutation.copy(width = currentWireframe.width)
        }
        if (prevWireframe.height != currentWireframe.height) {
            mutation = mutation.copy(height = currentWireframe.height)
        }
        if (prevWireframe.border != currentWireframe.border) {
            mutation = mutation.copy(border = currentWireframe.border)
        }
        if (prevWireframe.shapeStyle != currentWireframe.shapeStyle) {
            mutation = mutation.copy(shapeStyle = currentWireframe.shapeStyle)
        }
        if (prevWireframe.textStyle != currentWireframe.textStyle) {
            mutation = mutation.copy(textStyle = currentWireframe.textStyle)
        }
        if (prevWireframe.text != currentWireframe.text) {
            mutation = mutation.copy(text = currentWireframe.text)
        }
        if (prevWireframe.textPosition != currentWireframe.textPosition) {
            mutation = mutation.copy(textPosition = currentWireframe.textPosition)
        }
        if (prevWireframe.clip != currentWireframe.clip) {
            mutation = mutation.copy(
                clip = currentWireframe.clip
                    ?: MobileSegment.WireframeClip(0, 0, 0, 0)
            )
        }

        return mutation
    }

    @Suppress("FunctionMinLength")
    private fun MobileSegment.Wireframe.id(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.id
            is MobileSegment.Wireframe.TextWireframe -> this.id
            is MobileSegment.Wireframe.ImageWireframe -> this.id
            is MobileSegment.Wireframe.PlaceholderWireframe -> this.id
        }
    }

    /**
     * An item in symbols table (`[Int: Symbol]`) in Heckel's algorithm.
     * Note: Unlike original algorithm, our implementation doesn't
     * use `OC`, `NC` and `OLNO` counters. In our case, elements within each array are unique,
     * so instead of repetition counters, we define basic `Boolean`
     * flags to track occurrence of certain `id` in one or both files.
     *
     * @param inOld if element with certain `id` occurs in `newArray`.
     * @param inNew if element with certain `id` occurs in `oldArray`.
     * @param indexInOld the index of element in `oldArray`.
     *
     */
    private data class Symbol(var inOld: Boolean, var inNew: Boolean, var indexInOld: Int? = null)

    /** An entry in `oa` (old array) and `na` (new array) arrays in Heckel's algorithm. */
    private sealed class Entry {
        // Reference to `Symbol` in `table: [Int: Symbol]`.
        class Reference(val id: Long) : Entry()

        // Index of element in other array (in `oldArray` for `na: [Entry]`
        // and in `newArray` for `oa: [Entry]`).
        class Index(val index: Int) : Entry()
    }

    companion object {
        const val MISS_MATCHING_TYPES_IN_SNAPSHOTS_ERROR_MESSAGE_FORMAT =
            "SR MutationResolver: wireframe of type [%1s] is " +
                "not matching the wireframe of type [%2s]"
    }
}
