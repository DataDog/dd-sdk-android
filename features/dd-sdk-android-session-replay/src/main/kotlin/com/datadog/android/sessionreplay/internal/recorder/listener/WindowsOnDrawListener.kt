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
    private val debouncer: Debouncer = Debouncer(),
    private val miscUtils: MiscUtils = MiscUtils
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

        val nodes = views
            .mapNotNull {
                snapshotProducer.produce(it, systemInformation, recordedDataQueueRefs)
            }

        if (nodes.isNotEmpty()) {
            item.nodes = nodes
            if (item.isReady()) {
                recordedDataQueueHandler.tryToConsumeItems()
            }
        }
    }

    private fun resolveContext(views: List<View>): Context? {
        return views.firstOrNull()?.context
    }
}
