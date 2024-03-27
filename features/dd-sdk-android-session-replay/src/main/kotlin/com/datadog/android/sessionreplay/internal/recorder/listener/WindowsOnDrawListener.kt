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
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry
import com.datadog.android.sessionreplay.internal.recorder.telemetry.TelemetryWrapper
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import java.lang.ref.WeakReference

internal class WindowsOnDrawListener(
    zOrderedDecorViews: List<View>,
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val snapshotProducer: SnapshotProducer,
    private val privacy: SessionReplayPrivacy,
    private val debouncer: Debouncer = Debouncer(),
    private val miscUtils: MiscUtils = MiscUtils,
    private val logger: InternalLogger,
    private var telemetryWrapper: TelemetryWrapper = TelemetryWrapper(
        logger = logger
    )
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

        val methodCallTelemetry = telemetryWrapper.startMetric(
            operationName = MethodCalledTelemetry.METHOD_CALL_OPERATION_NAME,
            callerClass = this.javaClass.name,
            samplingRate = METHOD_CALL_SAMPLE_RATE
        ) as? MethodCalledTelemetry

        val nodes = views
            .mapNotNull {
                snapshotProducer.produce(it, systemInformation, privacy, recordedDataQueueRefs)
            }

        methodCallTelemetry?.stopMethodCalled(isSuccessful = nodes.isNotEmpty())

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
        private const val METHOD_CALL_SAMPLE_RATE = 5f
    }
}
