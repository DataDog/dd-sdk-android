/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.QueueableViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.TraverseAllChildrenMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.NoOpAsyncJobStatusCallback

internal class TreeViewTraversal(
    private val mappers: List<MapperTypeWrapper>,
    private val viewMapper: ViewWireframeMapper,
    private val decorViewMapper: DecorViewMapper,
    private val viewUtilsInternal: ViewUtilsInternal
) {

    @Suppress("ReturnCount")
    fun traverse(
        view: View,
        mappingContext: MappingContext,
        recordedDataQueueRefs: RecordedDataQueueRefs
    ): TraversedTreeView {
        if (viewUtilsInternal.isNotVisible(view) ||
            viewUtilsInternal.isSystemNoise(view)
        ) {
            return TraversedTreeView(emptyList(), TraversalStrategy.STOP_AND_DROP_NODE)
        }

        val traversalStrategy: TraversalStrategy
        val resolvedWireframes: List<MobileSegment.Wireframe>

        // try to resolve from the exhaustive type mappers
        val mapper = mappers.findFirstForType(view::class.java)

        if (mapper != null) {
            val queueableViewMapper =
                QueueableViewMapper(mapper, recordedDataQueueRefs)
            traversalStrategy = if (mapper is TraverseAllChildrenMapper) {
                TraversalStrategy.TRAVERSE_ALL_CHILDREN
            } else {
                TraversalStrategy.STOP_AND_RETURN_NODE
            }
            resolvedWireframes = queueableViewMapper.map(view, mappingContext)
        } else if (isDecorView(view)) {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            resolvedWireframes = decorViewMapper.map(view, mappingContext, NoOpAsyncJobStatusCallback())
        } else {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            resolvedWireframes = viewMapper.map(view, mappingContext, NoOpAsyncJobStatusCallback())
        }

        return TraversedTreeView(resolvedWireframes, traversalStrategy)
    }

    private fun isDecorView(view: View): Boolean {
        val viewParent = view.parent ?: return true
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        return !View::class.java.isAssignableFrom(viewParent.javaClass)
    }

    private fun List<MapperTypeWrapper>.findFirstForType(type: Class<*>): WireframeMapper<View, *>? {
        return firstOrNull {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            it.type.isAssignableFrom(type)
        }?.mapper
    }

    data class TraversedTreeView(
        val mappedWireframes: List<MobileSegment.Wireframe>,
        val nextActionStrategy: TraversalStrategy
    )
}

internal enum class TraversalStrategy {
    TRAVERSE_ALL_CHILDREN,
    STOP_AND_RETURN_NODE,
    STOP_AND_DROP_NODE
}
