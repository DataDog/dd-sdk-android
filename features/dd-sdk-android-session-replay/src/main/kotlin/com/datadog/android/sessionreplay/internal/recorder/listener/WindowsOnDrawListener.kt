/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import java.io.File
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit

internal class WindowsOnDrawListener(
        private val appContext: Context,
        zOrderedDecorViews: List<View>,
        private val recordedDataQueueHandler: RecordedDataQueueHandler,
        private val snapshotProducer: SnapshotProducer,
        private val debouncer: Debouncer = Debouncer(),
        private val miscUtils: MiscUtils = MiscUtils,
        private val recordedDataQueueRefs: RecordedDataQueueRefs =
                RecordedDataQueueRefs(recordedDataQueueHandler)
) : ViewTreeObserver.OnDrawListener {

    internal val weakReferencedDecorViews: List<WeakReference<View>>
    private val  handlerThread = HandlerThread("SnapshotProducer")
    private val handler:Handler

    init {
        weakReferencedDecorViews = zOrderedDecorViews.map { WeakReference(it) }
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        if (takeSnapshotTraceFile == null) {
            takeSnapshotTraceFile = File(appContext.externalCacheDir,
                    GregorianCalendar.getInstance().time.toString() + ".txt")

        }
    }

    @MainThread
    override fun onDraw() {
        debouncer.debounce {
            val start = System.nanoTime()
            resolveTakeSnapshotRunnable().run()
            val end = System.nanoTime()
            lastSnapshotViewCount = snapshotProducer.getLastSnapshotViewsCount()
            // execute on handler thread to avoid blocking the main thread

            handler.post{
                val duration = end - start
                val mean = (previousMean * previousCount + duration) / (previousCount + 1)
                previousMean = mean
                previousCount++
                sortedCounts.add(duration)
                sortedCounts.sort()
                val snapshotViewCountsMean =
                        (previousSnapshotCountMean * snapshotCount + lastSnapshotViewCount ) / (snapshotCount + 1)
                previousSnapshotCountMean = snapshotViewCountsMean
                snapshotCount++
                val p95 = sortedCounts[(sortedCounts.size * 0.95).toInt()]

                takeSnapshotTraceFile?.let { file ->
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    file.appendText(duration.toString() + "  " + lastSnapshotViewCount + "   " + TimeUnit.NANOSECONDS.toMillis(start) + "\n")
                }
                val durationToMillis = TimeUnit.NANOSECONDS.toMillis(duration)
                Log.v("SnapshotProducer",
                        "duration: $durationToMillis, mean: " +
                                "${TimeUnit.NANOSECONDS.toMillis(mean.toLong())}")
                Log.v("SnapshotProducer", "duration p95: ${TimeUnit.NANOSECONDS.toMillis(p95)}")
                Log.v("SnapshotProducer", "views per snapshot mean: $snapshotViewCountsMean")
            }
        }
    }

    @MainThread
    private fun resolveTakeSnapshotRunnable(): Runnable = Runnable {
        if (weakReferencedDecorViews.isEmpty()) {
            return@Runnable
        }
        // is is very important to have the windows sorted by their z-order
        val systemInformation = miscUtils.resolveSystemInformation(appContext)
        val item = recordedDataQueueHandler.addSnapshotItem(systemInformation)
                ?: return@Runnable

        recordedDataQueueRefs.recordedDataQueueItem = item

        val nodes = weakReferencedDecorViews
                .mapNotNull { it.get() }
                .mapNotNull {
//                Debug.startMethodTracing(start.toString())
                    val node = snapshotProducer.produce(it, systemInformation, recordedDataQueueRefs)
//                  Debug.stopMethodTracing()
                    node
                }

        if (nodes.isNotEmpty()) {
            item.nodes = nodes
            if (item.isReady()) {
                recordedDataQueueHandler.tryToConsumeItems()
            }
        }
    }

    companion object {
        @Volatile
        private var previousMean: Double = 0.0
        @Volatile
        private var previousCount: Double = 0.0
        private var sortedCounts = mutableListOf<Long>()
        @Volatile
        private var snapshotCount: Long = 0
        @Volatile
        private var lastSnapshotViewCount: Long = 0
        @Volatile
        private var previousSnapshotCountMean: Long = 0

        var takeSnapshotTraceFile: File? = null
    }
}
