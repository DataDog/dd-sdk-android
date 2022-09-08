/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList

internal class MutationResolver {

    fun resolveMutations(
        prevSnapshot: List<MobileSegment.Wireframe>,
        currentSnapshot: List<MobileSegment.Wireframe>
    ): MobileSegment.MobileIncrementalData.MobileMutationData {
        val prevSnapshotAsMap = prevSnapshot.associateBy { it.id() }.toMutableMap()
        val updates: MutableList<MobileSegment.WireframeUpdateMutation> = LinkedList()
        val adds: MutableList<MobileSegment.Add> = LinkedList()
        var prevWireframe: MobileSegment.Wireframe? = null
        currentSnapshot.forEach { currentWireframe ->
            val prevMatchedWireframe = prevSnapshotAsMap.remove(currentWireframe.id())
            if (prevMatchedWireframe != null) {
                resolveUpdateMutation(prevMatchedWireframe, currentWireframe)?.let {
                    updates.add(it)
                }
            } else {
                adds.add(MobileSegment.Add(prevWireframe?.id(), currentWireframe))
            }
            prevWireframe = currentWireframe
        }
        val removes = prevSnapshotAsMap.map { MobileSegment.Remove(it.key) }
        return MobileSegment.MobileIncrementalData.MobileMutationData(
            adds = adds,
            removes = removes,
            updates = updates
        )
    }

    private fun resolveUpdateMutation(
        prevWireframe: MobileSegment.Wireframe,
        currentWireframe: MobileSegment.Wireframe
    ): MobileSegment.WireframeUpdateMutation? {
        if (prevWireframe == currentWireframe) {
            return null
        }
        if (!prevWireframe.javaClass.isAssignableFrom(currentWireframe.javaClass)) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            return null
        }
        return when (prevWireframe) {
            is MobileSegment.Wireframe.TextWireframe -> resolveTextUpdateMutation(
                prevWireframe,
                currentWireframe as MobileSegment.Wireframe.TextWireframe
            )
            is MobileSegment.Wireframe.ShapeWireframe -> resolveShapeUpdateMutation(
                prevWireframe,
                currentWireframe as MobileSegment.Wireframe.ShapeWireframe
            )
        }
    }

    // TODO: RUMM-2481 Use the `diff` method int the ShapeWireframe type when available
    @Suppress("ComplexMethod")
    private fun resolveShapeUpdateMutation(
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

        return mutation
    }

    // TODO: RUMM-2481 Use the `diff` method int the TextWireframe type when available
    @Suppress("ComplexMethod")
    private fun resolveTextUpdateMutation(
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

        return mutation
    }

    @Suppress("FunctionMinLength")
    private fun MobileSegment.Wireframe.id(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.id
            is MobileSegment.Wireframe.TextWireframe -> this.id
        }
    }
}
