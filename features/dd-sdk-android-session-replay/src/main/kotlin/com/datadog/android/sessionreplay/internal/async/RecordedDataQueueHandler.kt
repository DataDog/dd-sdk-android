/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import java.lang.ClassCastException
import java.lang.NullPointerException
import java.util.Locale
import java.util.Queue
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
@Suppress("TooManyFunctions")
internal class RecordedDataQueueHandler {
    private var executorService: ExecutorService
    private var processor: RecordedDataProcessor
    private var rumContextDataHandler: RumContextDataHandler
    private var timeProvider: TimeProvider
    private val internalLogger: InternalLogger
    internal val recordedDataQueue: Queue<RecordedDataQueueItem>

    internal constructor(
        processor: RecordedDataProcessor,
        rumContextDataHandler: RumContextDataHandler,
        timeProvider: TimeProvider,
        internalLogger: InternalLogger
    ) : this(
        processor = processor,
        rumContextDataHandler = rumContextDataHandler,
        timeProvider = timeProvider,
        internalLogger = internalLogger,

        /**
         * TODO: RUMM-0000 consider change to LoggingThreadPoolExecutor once V2 is merged.
         * if we ever decide to make the poolsize greater than 1, we need to ensure
         * synchronization works correctly in the triggerProcessingLoop method below
         */
        // all parameters are non-negative and queue is not null
        executorService = @Suppress("UnsafeThirdPartyFunctionCall") ThreadPoolExecutor(
            CORE_DEFAULT_POOL_SIZE,
            CORE_DEFAULT_POOL_SIZE,
            THREAD_POOL_MAX_KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS,
            LinkedBlockingDeque()
        ),
        recordedQueue = ConcurrentLinkedQueue()
    )

    @VisibleForTesting
    internal constructor(
        processor: RecordedDataProcessor,
        rumContextDataHandler: RumContextDataHandler,
        timeProvider: TimeProvider,
        executorService: ExecutorService,
        internalLogger: InternalLogger,
        recordedQueue: Queue<RecordedDataQueueItem> = ConcurrentLinkedQueue()
    ) {
        this.processor = processor
        this.rumContextDataHandler = rumContextDataHandler
        this.executorService = executorService
        this.timeProvider = timeProvider
        this.internalLogger = internalLogger
        this.recordedDataQueue = recordedQueue
    }

    @Synchronized
    internal fun clearAndStopProcessingQueue() {
        recordedDataQueue.clear()
        executorService.shutdown()
    }

    @MainThread
    internal fun addTouchEventItem(
        pointerInteractions: List<MobileSegment.MobileRecord>
    ): TouchEventRecordedDataQueueItem? {
        val rumContextData = rumContextDataHandler.createRumContextData()
            ?: return null

        val item = TouchEventRecordedDataQueueItem(
            recordedQueuedItemContext = rumContextData,
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
            recordedQueuedItemContext = rumContextData,
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
                @Suppress("ThreadSafety") // already in the worker thread context
                triggerProcessingLoop(currentTime)
            }
        } catch (e: RejectedExecutionException) {
            logConsumeQueueException(e)
        } catch (e: NullPointerException) {
            logConsumeQueueException(e)
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
            // peeking is safe here because we are in a synchronized block
            // and we check for isEmpty first
            @SuppressWarnings("UnsafeThirdPartyFunctionCall")
            val nextItem = recordedDataQueue.peek()

            if (nextItem != null) {
                if (shouldRemoveItem(nextItem, currentTime)) {
                    // this should never happen, so if it does we should send telemetry
                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        listOf(
                            InternalLogger.Target.MAINTAINER,
                            InternalLogger.Target.TELEMETRY
                        ),
                        {
                            ITEM_DROPPED_FROM_QUEUE_ERROR_MESSAGE
                                .format(Locale.US, nextItem.isValid())
                        }
                    )
                    recordedDataQueue.poll()
                } else if (nextItem.isReady()) {
                    processItem(recordedDataQueue.poll())
                } else {
                    break
                }
            }
        }
    }

    @WorkerThread
    private fun processItem(nextItem: RecordedDataQueueItem?) {
        when (nextItem) {
            is SnapshotRecordedDataQueueItem ->
                processSnapshotEvent(nextItem)

            is TouchEventRecordedDataQueueItem ->
                processTouchEvent(nextItem)
        }
    }

    @WorkerThread
    private fun processSnapshotEvent(item: SnapshotRecordedDataQueueItem) {
        processor.processScreenSnapshots(item)
    }

    @WorkerThread
    private fun processTouchEvent(item: TouchEventRecordedDataQueueItem) {
        processor.processTouchEventsRecords(item)
    }

    private fun shouldRemoveItem(recordedDataQueueItem: RecordedDataQueueItem, currentTime: Long) =
        !recordedDataQueueItem.isValid() || isTooOld(currentTime, recordedDataQueueItem)

    private fun isTooOld(currentTime: Long, recordedDataQueueItem: RecordedDataQueueItem): Boolean =
        (currentTime - recordedDataQueueItem.recordedQueuedItemContext.timestamp) > MAX_DELAY_MS

    private fun insertIntoRecordedDataQueue(recordedDataQueueItem: RecordedDataQueueItem) {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            recordedDataQueue.offer(recordedDataQueueItem)
        } catch (e: IllegalArgumentException) {
            logAddToQueueException(e)
        } catch (e: ClassCastException) {
            logAddToQueueException(e)
        } catch (e: NullPointerException) {
            logAddToQueueException(e)
        }
    }

    private fun logAddToQueueException(e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { FAILED_TO_ADD_RECORDS_TO_QUEUE_ERROR_MESSAGE },
            e
        )
    }

    private fun logConsumeQueueException(e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { FAILED_TO_CONSUME_RECORDS_QUEUE_ERROR_MESSAGE },
            e
        )
    }

    // end region

    internal companion object {
        @VisibleForTesting
        internal const val MAX_DELAY_MS = 200L

        private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
        private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive
        internal const val FAILED_TO_CONSUME_RECORDS_QUEUE_ERROR_MESSAGE =
            "SR RecordedDataQueueHandler: failed to consume records from queue"
        internal const val FAILED_TO_ADD_RECORDS_TO_QUEUE_ERROR_MESSAGE =
            "SR RecordedDataQueueHandler: failed to add records into the queue"

        @VisibleForTesting
        internal const val ITEM_DROPPED_FROM_QUEUE_ERROR_MESSAGE =
            "SR RecordedDataQueueHandler: dropped item from the queue. Valid: %s"
    }
}
