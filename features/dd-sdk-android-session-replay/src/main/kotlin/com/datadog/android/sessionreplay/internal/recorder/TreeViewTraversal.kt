/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.measureMethodCallPerf
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ImageViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.QueueStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.mapper.TraverseAllChildrenMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.NoOpAsyncJobStatusCallback

internal class TreeViewTraversal(
    private val mappers: List<MapperTypeWrapper<*>>,
    private val defaultViewMapper: WireframeMapper<View>,
    private val decorViewMapper: WireframeMapper<View>,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val internalLogger: InternalLogger
) {

    @Suppress("ReturnCount")
    @UiThread
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

        val noOpCallback = NoOpAsyncJobStatusCallback()
        val jobStatusCallback: AsyncJobStatusCallback

        // try to resolve from the exhaustive type mappers
        var mapper = findMapperForView(view)

        if (mapper != null) {
            jobStatusCallback = QueueStatusCallback(recordedDataQueueRefs)
            traversalStrategy = if (mapper is TraverseAllChildrenMapper) {
                TraversalStrategy.TRAVERSE_ALL_CHILDREN
            } else {
                TraversalStrategy.STOP_AND_RETURN_NODE
            }
        } else if (isDecorView(view)) {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            mapper = decorViewMapper
            jobStatusCallback = noOpCallback
        } else if (view is ViewGroup) {
            traversalStrategy = TraversalStrategy.TRAVERSE_ALL_CHILDREN
            mapper = defaultViewMapper
            jobStatusCallback = noOpCallback
        } else {
            traversalStrategy = TraversalStrategy.STOP_AND_RETURN_NODE
            mapper = defaultViewMapper
            jobStatusCallback = noOpCallback
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

        val resolvedWireframes = internalLogger.measureMethodCallPerf(
            javaClass,
            "$METHOD_CALL_MAP_PREFIX ${mapper.javaClass.simpleName}",
            getSamplingRateForMapper(mapper.javaClass.simpleName)
        ) {
            mapper.map(view, mappingContext, jobStatusCallback, internalLogger)
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

    private fun getSamplingRateForMapper(mapperName: String): Float {
        return when (mapperName) {
            ViewWireframeMapper::class.simpleName -> MethodCallSamplingRate.RARE.rate
            TextViewMapper::class.simpleName -> MethodCallSamplingRate.RARE.rate
            ImageViewMapper::class.simpleName -> MethodCallSamplingRate.RARE.rate
            DecorViewMapper::class.simpleName -> MethodCallSamplingRate.REDUCED.rate
            ButtonMapper::class.simpleName -> MethodCallSamplingRate.REDUCED.rate
            else -> MethodCallSamplingRate.DEFAULT.rate
        }
    }

    data class TraversedTreeView(
        val mappedWireframes: List<MobileSegment.Wireframe>,
        val nextActionStrategy: TraversalStrategy
    )

    internal enum class MethodCallSamplingRate(val rate: Float) {
        DEFAULT(rate = 0.1f),
        REDUCED(rate = 0.01f),
        RARE(rate = 0.001f)
    }

    internal companion object {
        internal const val METHOD_CALL_MAP_PREFIX = "Map with"
    }
}
