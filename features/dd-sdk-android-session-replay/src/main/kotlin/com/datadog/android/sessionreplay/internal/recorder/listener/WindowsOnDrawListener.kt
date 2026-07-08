/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.measureMethodCallPerf
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.CompositionTreeBuilder
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.PixelCopyCapture
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.withinSRBenchmarkSpan
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.recorder.SystemInformation
import java.lang.ref.WeakReference

internal class WindowsOnDrawListener(
    zOrderedDecorViews: List<View>,
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val snapshotProducer: SnapshotProducer,
    private val textAndInputPrivacy: TextAndInputPrivacy,
    private val imagePrivacy: ImagePrivacy,
    private val miscUtils: MiscUtils = MiscUtils,
    private val sdkCore: FeatureSdkCore,
    dynamicOptimizationEnabled: Boolean,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val debouncer: Debouncer = Debouncer(
        sdkCore = sdkCore,
        dynamicOptimizationEnabled = dynamicOptimizationEnabled
    ),
    private val methodCallSamplingRate: Float,
    private val rumContextProvider: RumContextProvider,
    private val pixelCopyCapture: PixelCopyCapture? = null,
    // When non-null, this snapshot cycle goes entirely through the composition-tree pipeline
    // instead of snapshotProducer — the two never both run for the same cycle.
    private val compositionTreeBuilder: CompositionTreeBuilder? = null
) : ViewTreeObserver.OnDrawListener {

    internal val weakReferencedDecorViews: List<WeakReference<View>> = zOrderedDecorViews.map { WeakReference(it) }

    @MainThread
    override fun onDraw() {
        debouncer.debounce(snapshotRunnable)
    }

    // Note: we declare the anonymous object explicitly to annotate the run method as @UiThread
    private val snapshotRunnable: Runnable = object : Runnable {

        @UiThread
        override fun run() {
            val rootViews = weakReferencedDecorViews.mapNotNull { it.get() }

            // is is very important to have the windows sorted by their z-order
            val context = rootViews.firstOrNull()?.context ?: return
            val systemInformation = miscUtils.resolveSystemInformation(context)
            val item = recordedDataQueueHandler.addSnapshotItem(systemInformation) ?: return

            val currentViewUrl = rumContextProvider.getRumContext().viewUrl

            // Discard stale bitmap before traversal so mappers never see previous-screen pixels.
            pixelCopyCapture?.onPreTraversal(currentViewUrl)

            val recordedDataQueueRefs = RecordedDataQueueRefs(recordedDataQueueHandler)
            recordedDataQueueRefs.recordedDataQueueItem = item

            if (compositionTreeBuilder != null) {
                runCompositionTreePipeline(rootViews, systemInformation, item, recordedDataQueueRefs)
                return
            }

            val nodes = sdkCore.internalLogger.measureMethodCallPerf(
                METHOD_CALL_CALLER_CLASS,
                METHOD_CALL_CAPTURE_RECORD,
                methodCallSamplingRate
            ) {
                withinSRBenchmarkSpan(BENCHMARK_SPAN_SNAPSHOT_PRODUCER, isContainer = true) {
                    rootViews.mapNotNull {
                        snapshotProducer.produce(
                            rootView = it,
                            systemInformation = systemInformation,
                            textAndInputPrivacy = textAndInputPrivacy,
                            imagePrivacy = imagePrivacy,
                            recordedDataQueueRefs = recordedDataQueueRefs,
                            activeRumViewUrl = currentViewUrl
                        )
                    }
                }
            }

            if (nodes.isNotEmpty()) {
                item.nodes = nodes
            }

            item.isFinishedTraversal = true

            if (item.isReady()) {
                recordedDataQueueHandler.tryToConsumeItems()
            }

            touchPrivacyManager.updateCurrentTouchOverrideAreas()

            // Post-traversal: evaluate pending crops now that all wireframe bounds are known.
            // processPendingCrops decides PixelCopy vs isolation per crop based on overlap check.
            if (nodes.isNotEmpty()) {
                pixelCopyCapture?.processPendingCrops(nodes)
            }

            // Fire one full-window PixelCopy per snapshot — O(1) GPU readback.
            // The resulting bitmap is used by the NEXT cycle's processPendingCrops (PixelCopy path).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopyCapture?.captureLatestFrame()
            }
        }

        /**
         * The composition-tree pipeline, entirely separate from the block above: only one root
         * view is supported for now (the topmost decor view — matches how [PixelCopyCapture]
         * itself already only ever tracks a single current window).
         */
        @UiThread
        private fun runCompositionTreePipeline(
            rootViews: List<View>,
            systemInformation: SystemInformation,
            item: SnapshotRecordedDataQueueItem,
            recordedDataQueueRefs: RecordedDataQueueRefs
        ) {
            val builder = compositionTreeBuilder ?: return
            val output = sdkCore.internalLogger.measureMethodCallPerf(
                METHOD_CALL_CALLER_CLASS,
                METHOD_CALL_CAPTURE_RECORD,
                methodCallSamplingRate
            ) {
                withinSRBenchmarkSpan(BENCHMARK_SPAN_SNAPSHOT_PRODUCER, isContainer = true) {
                    rootViews.firstOrNull()?.let { rootView ->
                        builder.build(
                            rootView = rootView,
                            systemInformation = systemInformation,
                            textAndInputPrivacy = textAndInputPrivacy,
                            imagePrivacy = imagePrivacy,
                            recordedDataQueueRefs = recordedDataQueueRefs,
                            internalLogger = sdkCore.internalLogger
                        )
                    }
                }
            }

            if (output != null && output.wireframes.isNotEmpty()) {
                item.compositionTreeOutput = output
            }

            item.isFinishedTraversal = true

            if (item.isReady()) {
                recordedDataQueueHandler.tryToConsumeItems()
            }

            touchPrivacyManager.updateCurrentTouchOverrideAreas()

            if (output != null && output.wireframes.isNotEmpty()) {
                pixelCopyCapture?.processPendingCropsForWireframes(output.wireframes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopyCapture?.captureLatestFrame()
            }
        }
    }

    companion object {
        private const val METHOD_CALL_CAPTURE_RECORD: String = "Capture Record"
        private const val BENCHMARK_SPAN_SNAPSHOT_PRODUCER = "SnapshotProducer"
        private val METHOD_CALL_CALLER_CLASS = WindowsOnDrawListener::class.java
    }
}
