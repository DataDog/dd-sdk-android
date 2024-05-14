/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.mapper.QueueStatusCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.TraverseAllChildrenMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.NoOpAsyncJobStatusCallback

internal class TreeViewTraversal(
    private val mappers: List<MapperTypeWrapper<*>>,
    private val defaultViewMapper: WireframeMapper<View>,
    private val decorViewMapper: WireframeMapper<View>,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val internalLogger: InternalLogger
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
        val noOpCallback = NoOpAsyncJobStatusCallback()

        // try to resolve from the exhaustive type mappers
        val mapper = findMapperForView(view)

        if (mapper != null) {
            val queueStatusCallback = QueueStatusCallback(recordedDataQueueRefs)
            traversalStrategy = if (mapper is TraverseAllChildrenMapper) {
                TraversalStrategy.TRAVERSE_ALL_CHILDREN
            } else {
                TraversalStrategy.STOP_AND_RETURN_NODE
            }
            resolvedWireframes = mapper.map(view, mappingContext, queueStatusCallback, internalLogger)
        } else if (isDecorView(view)) {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            resolvedWireframes = decorViewMapper.map(view, mappingContext, noOpCallback, internalLogger)
        } else if (view is ViewGroup) {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            resolvedWireframes = defaultViewMapper.map(view, mappingContext, noOpCallback, internalLogger)
        } else {
            traversalStrategy = TraversalStrategy.STOP_AND_RETURN_NODE
            resolvedWireframes = defaultViewMapper.map(view, mappingContext, noOpCallback, internalLogger)
            val viewType = view.javaClass.canonicalName ?: view.javaClass.name

            internalLogger.log(
                level = InternalLogger.Level.INFO,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { "No mapper found for view $viewType" },
                throwable = null,
                onlyOnce = true,
                additionalProperties = mapOf(
                    "replay.widget.type" to viewType
                )
            )
        }

        return TraversedTreeView(resolvedWireframes, traversalStrategy)
    }

    private fun isDecorView(view: View): Boolean {
        val viewParent = view.parent ?: return true
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        return !View::class.java.isAssignableFrom(viewParent.javaClass)
    }

    private fun findMapperForView(view: View): WireframeMapper<View>? {
        return mappers.firstOrNull { it.supportsView(view) }?.getUnsafeMapper()
    }

    data class TraversedTreeView(
        val mappedWireframes: List<MobileSegment.Wireframe>,
        val nextActionStrategy: TraversalStrategy
    )
}
