/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import java.lang.ref.WeakReference

internal class WindowsOnDrawListener(
    zOrderedDecorViews: List<View>,
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val snapshotProducer: SnapshotProducer,
    private val privacy: SessionReplayPrivacy,
    private val debouncer: Debouncer = Debouncer(),
    private val miscUtils: MiscUtils = MiscUtils,
    private val sdkCore: SdkCore = Datadog.getInstance(),
    private val sessionReplayFeature: FeatureScope? = (sdkCore as FeatureSdkCore).getFeature(
        Feature.SESSION_REPLAY_FEATURE_NAME
    ),
    private val methodCallTelemetrySamplingRate: Float = METHOD_CALL_SAMPLING_RATE
) : ViewTreeObserver.OnDrawListener {

    internal val weakReferencedDecorViews: List<WeakReference<View>>

    init {
        weakReferencedDecorViews = zOrderedDecorViews.map { WeakReference(it) }
    }

    @MainThread
    override fun onDraw() {
        debouncer.debounce(resolveTakeSnapshotRunnable())
    }

    @MainThread
    private fun resolveTakeSnapshotRunnable(): Runnable = Runnable {
        if (weakReferencedDecorViews.isEmpty()) {
            return@Runnable
        }

        val views = weakReferencedDecorViews
            .mapNotNull { it.get() }
        if (views.isEmpty()) {
            return@Runnable
        }

        // is is very important to have the windows sorted by their z-order
        val context = resolveContext(views) ?: return@Runnable
        val systemInformation = miscUtils.resolveSystemInformation(context)
        val item = recordedDataQueueHandler.addSnapshotItem(systemInformation)
            ?: return@Runnable

        val recordedDataQueueRefs =
            RecordedDataQueueRefs(recordedDataQueueHandler)
        recordedDataQueueRefs.recordedDataQueueItem = item

        val performanceMetric = sessionReplayFeature?.startPerformanceMeasure(
            callerClass = this.javaClass.name,
            metric = TelemetryMetricType.MethodCalled,
            samplingRate = methodCallTelemetrySamplingRate
        )

        val nodes = views
            .mapNotNull {
                snapshotProducer.produce(it, systemInformation, privacy, recordedDataQueueRefs)
            }

        val isSuccessful = nodes.isNotEmpty()
        performanceMetric?.stopAndSend(isSuccessful)

        if (nodes.isNotEmpty()) {
            item.nodes = nodes
        }

        item.isFinishedTraversal = true

        if (item.isReady()) {
            recordedDataQueueHandler.tryToConsumeItems()
        }
    }

    private fun resolveContext(views: List<View>): Context? {
        return views.firstOrNull()?.context
    }

    private companion object {
        private const val METHOD_CALL_SAMPLING_RATE = 5f
    }
}
