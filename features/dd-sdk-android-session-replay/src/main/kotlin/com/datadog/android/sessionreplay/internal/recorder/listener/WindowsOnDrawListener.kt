/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.view.View
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.measureMethodCallPerf
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.OnDemandCaptureListener
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.withinSRBenchmarkSpan
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
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
    private val rumContextProvider: RumContextProvider
) : OnDemandCaptureListener {

    internal val weakReferencedDecorViews: List<WeakReference<View>> = zOrderedDecorViews.map { WeakReference(it) }

    @MainThread
    override fun onDraw() {
        debouncer.debounce(snapshotRunnable)
    }

    @MainThread
    override fun captureNow(): Boolean = takeSnapshot()

    // Note: we declare the anonymous object explicitly to annotate the run method as @UiThread
    @Suppress("ObjectLiteralToLambda")
    private val snapshotRunnable: Runnable = object : Runnable {

        @UiThread
        override fun run() {
            takeSnapshot()
        }
    }

    /**
     * @return whether a snapshot item was in fact queued: there may be no window left to traverse,
     * or the queue may have refused the item.
     */
    @UiThread
    @Suppress("ReturnCount")
    private fun takeSnapshot(): Boolean {
        val rootViews = weakReferencedDecorViews.mapNotNull { it.get() }

        // is is very important to have the windows sorted by their z-order
        val context = rootViews.firstOrNull()?.context ?: return false
        val systemInformation = miscUtils.resolveSystemInformation(context)
        val item = recordedDataQueueHandler.addSnapshotItem(systemInformation) ?: return false

        val currentViewUrl = rumContextProvider.getRumContext().viewUrl

        val nodes = sdkCore.internalLogger.measureMethodCallPerf(
            METHOD_CALL_CALLER_CLASS,
            METHOD_CALL_CAPTURE_RECORD,
            methodCallSamplingRate
        ) {
            withinSRBenchmarkSpan(BENCHMARK_SPAN_SNAPSHOT_PRODUCER, isContainer = true) {
                val recordedDataQueueRefs = RecordedDataQueueRefs(recordedDataQueueHandler)
                recordedDataQueueRefs.recordedDataQueueItem = item
                snapshotProducer.beginSnapshot()
                val snapshotNodes = rootViews.mapNotNull {
                    snapshotProducer.produce(
                        rootView = it,
                        systemInformation = systemInformation,
                        textAndInputPrivacy = textAndInputPrivacy,
                        imagePrivacy = imagePrivacy,
                        recordedDataQueueRefs = recordedDataQueueRefs,
                        activeRumViewUrl = currentViewUrl
                    )
                }
                snapshotProducer.finishSnapshot()?.let {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Kotlin listOf cannot fail for this local value.
                    val finishSnapshotNodes = listOf(it)
                    finishSnapshotNodes + snapshotNodes
                } ?: snapshotNodes
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
        return true
    }

    companion object {
        private const val METHOD_CALL_CAPTURE_RECORD: String = "Capture Record"

        private const val BENCHMARK_SPAN_SNAPSHOT_PRODUCER = "SnapshotProducer"

        private val METHOD_CALL_CALLER_CLASS = WindowsOnDrawListener::class.java
    }
}
