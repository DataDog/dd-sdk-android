/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.measureMethodCallPerf
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer.Companion.INVALID_PRIVACY_LEVEL_ERROR
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.QueueStatusCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.TraverseAllChildrenMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.NoOpAsyncJobStatusCallback
import java.util.Locale

internal class TreeViewTraversal(
    private val mappers: List<MapperTypeWrapper<*>>,
    private val defaultViewMapper: WireframeMapper<View>,
    private val hiddenViewMapper: HiddenViewMapper,
    private val decorViewMapper: WireframeMapper<View>,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val internalLogger: InternalLogger,
    private val touchPrivacyManager: TouchPrivacyManager
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
        updateTouchOverrideAreas(view)

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

    private fun isHidden(view: View): Boolean =
        view.getTag(R.id.datadog_hidden) == true

    @UiThread
    private fun updateTouchOverrideAreas(view: View) {
        val touchPrivacy = view.getTag(R.id.datadog_touch_privacy)

        if (touchPrivacy != null) {
            val locationOnScreen = IntArray(2)

            // this will always have size >= 2
            @Suppress("UnsafeThirdPartyFunctionCall")
            view.getLocationOnScreen(locationOnScreen)

            val x = locationOnScreen[0]
            val y = locationOnScreen[1]
            val viewArea = Rect(
                x - view.paddingLeft,
                y - view.paddingTop,
                x + view.width + view.paddingRight,
                y + view.height + view.paddingBottom
            )

            try {
                val privacyLevel = TouchPrivacy.valueOf(touchPrivacy.toString().uppercase(Locale.US))
                touchPrivacyManager.addTouchOverrideArea(viewArea, privacyLevel)
            } catch (e: IllegalArgumentException) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                    { INVALID_PRIVACY_LEVEL_ERROR },
                    e
                )
            }
        }
    }

    data class TraversedTreeView(
        val mappedWireframes: List<MobileSegment.Wireframe>,
        val nextActionStrategy: TraversalStrategy
    )

    internal companion object {
        internal const val METHOD_CALL_MAP_PREFIX = "Map with"
    }
}
