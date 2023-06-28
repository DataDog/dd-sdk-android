/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import java.lang.ClassCastException
import java.lang.NullPointerException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Responsible for storing the Snapshot and Interaction events in a queue.
 * Allows for asynchronous enrichment, which still preserving the event order.
 * The items are added to the queue from the main thread and processed on a background thread.
 */
internal class RecordedDataQueueHandler {

    private var executorService: ExecutorService
    private var processor: RecordedDataProcessor
    private var rumContextDataHandler: RumContextDataHandler
    private var timeProvider: TimeProvider

    internal constructor(
        processor: RecordedDataProcessor,
        rumContextDataHandler: RumContextDataHandler,
        timeProvider: TimeProvider
    ) : this(
        processor = processor,
        rumContextDataHandler = rumContextDataHandler,
        timeProvider = timeProvider,

        /**
         * TODO: RUMM-0000 consider change to LoggingThreadPoolExecutor once V2 is merged.
         * if we ever decide to make the poolsize greater than 1, we need to ensure
         * synchronization works correctly in the triggerProcessingLoop method below
         */
        executorService = ThreadPoolExecutor(
            CORE_DEFAULT_POOL_SIZE,
            CORE_DEFAULT_POOL_SIZE,
            THREAD_POOL_MAX_KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS,
            LinkedBlockingDeque()
        )
    )

    @VisibleForTesting
    internal constructor(
        processor: RecordedDataProcessor,
        rumContextDataHandler: RumContextDataHandler,
        timeProvider: TimeProvider,
        executorService: ExecutorService
    ) {
        this.processor = processor
        this.rumContextDataHandler = rumContextDataHandler
        this.executorService = executorService
        this.timeProvider = timeProvider
    }

    // region internal
    internal val recordedDataQueue = ConcurrentLinkedQueue<RecordedDataQueueItem>()

    @MainThread
    internal fun addTouchEventItem(
        pointerInteractions: List<MobileSegment.MobileRecord>
    ): TouchEventRecordedDataQueueItem? {
        val rumContextData = rumContextDataHandler.createRumContextData()
            ?: return null

        val item = TouchEventRecordedDataQueueItem(
            rumContextData = rumContextData,
            touchData = pointerInteractions
        )

        insertIntoRecordedDataQueue(item)

        return item
    }

    @MainThread
    internal fun addSnapshotItem(
        systemInformation: SystemInformation
    ): SnapshotRecordedDataQueueItem? {
        val rumContextData = rumContextDataHandler.createRumContextData()
            ?: return null

        val item = SnapshotRecordedDataQueueItem(
            rumContextData = rumContextData,
            systemInformation = systemInformation
        )

        insertIntoRecordedDataQueue(item)

        return item
    }

    /**
     * Goes through the queue one item at a time for as long as there are items in the queue.
     * If an item is ready to be consumed, it is processed.
     * If an invalid item is encountered, it is removed (invalid items are possible
     * for example if a snapshot failed to traverse the tree).
     * If neither of the previous conditions occurs, the loop breaks.
     */
    @MainThread
    internal fun tryToConsumeItems() {
        // no need to create a thread if the queue is empty
        if (recordedDataQueue.isEmpty()) {
            return
        }

        // currentTime needs to be obtained on the uithread
        val currentTime = timeProvider.getDeviceTimestamp()

        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            executorService.execute {
                triggerProcessingLoop(currentTime)
            }
        } catch (e: RejectedExecutionException) {
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
        } catch (e: NullPointerException) {
            // in theory will never happen but we'll log it
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
        }
    }

    /**
     * The threadpool that triggers this method has a size/maxsize of 1.
     * This makes it implicitly synchronised so we should never have any multithreading issues
     * where one thread peeks while another removes the element.
     */
    @WorkerThread
    @Synchronized
    private fun triggerProcessingLoop(currentTime: Long) {
        while (recordedDataQueue.isNotEmpty()) {
            val nextItem = recordedDataQueue.peek()

            if (nextItem != null) {
                if (shouldRemoveItem(nextItem, currentTime)) {
                    recordedDataQueue.poll()
                } else if (nextItem.isReady()) {
                    processItem(recordedDataQueue.poll())
                } else {
                    break
                }
            }
        }
    }

    private fun processItem(nextItem: RecordedDataQueueItem?) {
        when (nextItem) {
            is SnapshotRecordedDataQueueItem ->
                processSnapshotEvent(nextItem)
            is TouchEventRecordedDataQueueItem ->
                processTouchEvent(nextItem)
        }
    }

    private fun processSnapshotEvent(item: SnapshotRecordedDataQueueItem) {
        processor.processScreenSnapshots(item)
    }

    private fun processTouchEvent(item: TouchEventRecordedDataQueueItem) {
        processor.processTouchEventsRecords(item)
    }

    private fun shouldRemoveItem(recordedDataQueueItem: RecordedDataQueueItem, currentTime: Long) =
        !recordedDataQueueItem.isValid() || isTooOld(currentTime, recordedDataQueueItem)

    private fun isTooOld(currentTime: Long, recordedDataQueueItem: RecordedDataQueueItem): Boolean =
        (currentTime - recordedDataQueueItem.rumContextData.timestamp) > MAX_DELAY_MS

    private fun insertIntoRecordedDataQueue(recordedDataQueueItem: RecordedDataQueueItem) {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            recordedDataQueue.offer(recordedDataQueueItem)
        } catch (e: IllegalArgumentException) {
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
        } catch (e: ClassCastException) {
            // in theory will never happen but we'll log it
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
        } catch (e: NullPointerException) {
            // in theory will never happen but we'll log it
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
        }
    }

    // end region

    internal companion object {
        internal const val MAX_DELAY_MS = 200L
        private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
        private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive
    }
}
