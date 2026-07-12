/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

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
import com.datadog.android.sessionreplay.internal.recorder.PixelCapture
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.withinSRBenchmarkSpan
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.recorder.SystemInformation
import java.lang.ref.WeakReference

internal class WindowsOnDrawListener(
    decorViews: List<View>,
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
    private val pixelCapture: PixelCapture? = null,
    // When non-null, this snapshot cycle goes entirely through the composition-tree pipeline
    // instead of snapshotProducer — the two never both run for the same cycle.
    private val compositionTreeBuilder: CompositionTreeBuilder? = null
) : ViewTreeObserver.OnDrawListener {

    internal val weakReferencedDecorViews: List<WeakReference<View>> = decorViews.map { WeakReference(it) }

    @MainThread
    override fun onDraw() {
        debouncer.debounce(snapshotRunnable)
    }

    // Note: we declare the anonymous object explicitly to annotate the run method as @UiThread
    private val snapshotRunnable: Runnable = object : Runnable {

        @UiThread
        override fun run() {
            val rootViews = weakReferencedDecorViews.mapNotNull { it.get() }

            // Any live window's Context resolves the same SystemInformation (same device, same
            // process) — which window this comes from doesn't matter here, unlike in
            // runCompositionTreePipeline's rootView selection below.
            val context = rootViews.firstOrNull()?.context ?: return
            val systemInformation = miscUtils.resolveSystemInformation(context)
            val item = recordedDataQueueHandler.addSnapshotItem(systemInformation) ?: return

            val currentViewUrl = rumContextProvider.getRumContext().viewUrl

            val recordedDataQueueRefs = RecordedDataQueueRefs(recordedDataQueueHandler)
            recordedDataQueueRefs.recordedDataQueueItem = item

            if (compositionTreeBuilder != null) {
                runCompositionTreePipeline(rootViews, systemInformation, currentViewUrl, item, recordedDataQueueRefs)
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
        }

        /**
         * The composition-tree pipeline, entirely separate from the block above: every
         * currently-shown window is captured and merged into one tree, ordered by
         * [orderRootViewsForComposition].
         */
        @UiThread
        private fun runCompositionTreePipeline(
            rootViews: List<View>,
            systemInformation: SystemInformation,
            currentViewUrl: String?,
            item: SnapshotRecordedDataQueueItem,
            recordedDataQueueRefs: RecordedDataQueueRefs
        ) {
            val builder = compositionTreeBuilder ?: return

            // Marks the start of this snapshot cycle's capture budget, and clears the content
            // cache on navigation — see PixelCapture's doc.
            pixelCapture?.onPreTraversal(currentViewUrl)

            val output = sdkCore.internalLogger.measureMethodCallPerf(
                METHOD_CALL_CALLER_CLASS,
                METHOD_CALL_CAPTURE_RECORD,
                methodCallSamplingRate
            ) {
                withinSRBenchmarkSpan(BENCHMARK_SPAN_SNAPSHOT_PRODUCER, isContainer = true) {
                    builder.build(
                        rootViews = orderRootViewsForComposition(rootViews),
                        systemInformation = systemInformation,
                        textAndInputPrivacy = textAndInputPrivacy,
                        imagePrivacy = imagePrivacy,
                        recordedDataQueueRefs = recordedDataQueueRefs,
                        internalLogger = sdkCore.internalLogger
                    )
                }
            }

            if (output.wireframes.isNotEmpty()) {
                item.compositionTreeOutput = output
            }

            // Feeds this screen's shape into PixelCapture's next health-summary log.
            val layerCount = output.compositionTree?.let { 1 + (it.layers?.size ?: 0) } ?: 0
            pixelCapture?.recordCompositionTreeStats(layerCount, output.wireframes.size)

            item.isFinishedTraversal = true

            if (item.isReady()) {
                recordedDataQueueHandler.tryToConsumeItems()
            }

            touchPrivacyManager.updateCurrentTouchOverrideAreas()

            // Post-traversal: capture (or reuse cached content for) every pending capture.
            pixelCapture?.processPendingCaptures()
        }
    }

    /**
     * Orders [rootViews] so the currently-focused window — the practical, verifiable signal for
     * "the topmost, actually-visible window" — renders last (on top; composition-layer children
     * are ordered back-to-front per schema). [rootViews] itself carries no ordering guarantee:
     * it is built from whatever `WindowInspector` (or the legacy reflection fallback — see
     * `WindowInspector.getGlobalWindowViews`) returns, which reflects internal bookkeeping
     * order, not z-order or visibility. [sortedBy] is stable, so relative order among unfocused
     * windows — still no true z-order signal for those — is preserved rather than randomized.
     * Visibility filtering (e.g. detached/zero-size windows) happens downstream in
     * [CompositionTreeBuilder.build], not here — this only orders.
     */
    private fun orderRootViewsForComposition(rootViews: List<View>): List<View> =
        rootViews.sortedBy { it.hasWindowFocus() }

    companion object {
        private const val METHOD_CALL_CAPTURE_RECORD: String = "Capture Record"
        private const val BENCHMARK_SPAN_SNAPSHOT_PRODUCER = "SnapshotProducer"
        private val METHOD_CALL_CALLER_CLASS = WindowsOnDrawListener::class.java
    }
}
