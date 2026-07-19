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
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.QueueStatusCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.TraverseAllChildrenMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.NoOpAsyncJobStatusCallback

internal class TreeViewTraversal(
    private val mappers: List<MapperTypeWrapper<*>>,
    private val defaultViewMapper: WireframeMapper<View>,
    private val hiddenViewMapper: HiddenViewMapper,
    private val decorViewMapper: WireframeMapper<View>,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val internalLogger: InternalLogger,
    // Optional pixel-perfect fallback for views with no registered mapper, present only when
    // pixelCaptureEnabled is set. When non-null, unrecognised leaf views are captured via
    // View.draw instead of being mapped by defaultViewMapper.
    private val pixelCaptureFallbackMapper: WireframeMapper<View>? = null
) {

    @Suppress("ReturnCount")
    @UiThread
    fun traverse(
        view: View,
        mappingContext: MappingContext,
        recordedDataQueueRefs: RecordedDataQueueRefs
    ): TraversedTreeView {
        if (viewUtilsInternal.isNotVisible(view) ||
            viewUtilsInternal.isSystemNoise(view) ||
            viewUtilsInternal.isOnSecondaryDisplay(view)
        ) {
            return TraversedTreeView(emptyList(), TraversalStrategy.STOP_AND_DROP_NODE)
        }

        val traversalStrategy: TraversalStrategy

        val noOpCallback = NoOpAsyncJobStatusCallback()
        val jobStatusCallback: AsyncJobStatusCallback

        // try to resolve from the exhaustive type mappers
        var mapper = findMapperForView(view)
        updateTouchOverrideAreas(view, mappingContext)

        if (isHidden(view)) {
            traversalStrategy = TraversalStrategy.STOP_AND_RETURN_NODE
            mapper = hiddenViewMapper
            jobStatusCallback = noOpCallback
        } else if (mapper != null) {
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
            // Use the pixel-copy fallback mapper when available — it captures the view via
            // View.draw (reusing a cached capture when only its position changed), falling
            // back to defaultViewMapper if no capture is available or the view is partially
            // off-screen.
            mapper = pixelCaptureFallbackMapper ?: defaultViewMapper
            jobStatusCallback = if (pixelCaptureFallbackMapper != null) {
                // PixelCaptureFallbackMapper may start an async job (ImageWireframe resource
                // upload), so we need a real QueueStatusCallback to gate queue consumption.
                QueueStatusCallback(recordedDataQueueRefs)
            } else {
                noOpCallback
            }
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
            MethodCallSamplingRate.RARE.rate
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

    // Shared with CompositionTreeBuilder — see isViewHiddenForSessionReplay's doc.
    private fun isHidden(view: View): Boolean = isViewHiddenForSessionReplay(view)

    // Shared with CompositionTreeBuilder — see resolveTouchPrivacyOverride's doc for why this
    // isn't duplicated locally anymore.
    @UiThread
    private fun updateTouchOverrideAreas(view: View, mappingContext: MappingContext) =
        resolveTouchPrivacyOverride(view, mappingContext, internalLogger)

    data class TraversedTreeView(
        val mappedWireframes: List<MobileSegment.Wireframe>,
        val nextActionStrategy: TraversalStrategy
    )

    internal companion object {
        internal const val METHOD_CALL_MAP_PREFIX = "Map with"
    }
}
