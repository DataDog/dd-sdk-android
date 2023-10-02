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
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment

internal class TreeViewTraversal(
    private val mappers: List<MapperTypeWrapper>,
    private val viewMapper: ViewWireframeMapper = ViewWireframeMapper(),
    private val decorViewMapper: DecorViewMapper = DecorViewMapper(viewMapper),
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal()
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
        val exhaustiveTypeMapper = mappers.findFirstForType(view::class.java)

        if (exhaustiveTypeMapper != null) {
            val queueableViewMapper = QueueableViewMapper(exhaustiveTypeMapper, recordedDataQueueRefs)
            traversalStrategy = exhaustiveTypeMapper.traversalStrategy
            resolvedWireframes = queueableViewMapper.map(view, mappingContext)
        } else if (isDecorView(view)) {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            resolvedWireframes = decorViewMapper.map(view, mappingContext)
        } else {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            resolvedWireframes = viewMapper.map(view, mappingContext)
        }

        return TraversedTreeView(resolvedWireframes, traversalStrategy)
    }

    private fun isDecorView(view: View): Boolean {
        val viewParent = view.parent
        return viewParent == null || !View::class.java.isAssignableFrom(viewParent::class.java)
    }

    private fun List<MapperTypeWrapper>.findFirstForType(type: Class<*>):
        WireframeMapper<View, *>? {
        return firstOrNull {
            it.type.isAssignableFrom(type)
        }?.mapper
    }

    data class TraversedTreeView(
        val mappedWireframes: List<MobileSegment.Wireframe>,
        val nextActionStrategy: TraversalStrategy
    )
}

enum class TraversalStrategy {
    TRAVERSE_ALL_CHILDREN,
    STOP_AND_RETURN_NODE,
    STOP_AND_DROP_NODE
}