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
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.SystemInformation
import java.util.Locale
import java.util.Queue
import java.util.concurrent.ExecutorService

/**
 * Responsible for storing the Snapshot and Interaction events in a queue.
 * Allows for asynchronous enrichment, which still preserving the event order.
 * The items are added to the queue from the main thread and processed on a background thread.
 */
internal class RecordedDataQueueHandler(
    private val processor: RecordedDataProcessor,
    private val rumContextDataHandler: RumContextDataHandler,
    private val internalLogger: InternalLogger,
    private val executorService: ExecutorService,
    internal val recordedDataQueue: Queue<RecordedDataQueueItem>,
    private val telemetrySampleRate: Float = TELEMETRY_SAMPLE_RATE_PERCENT,
    private val sampler: RateBasedSampler<Unit> = RateBasedSampler(telemetrySampleRate)
) : DataQueueHandler {

    @Synchronized
    override fun clearAndStopProcessingQueue() {
        recordedDataQueue.clear()
        executorService.shutdown()
    }

    @MainThread
    override fun addResourceItem(
        identifier: String,
        resourceData: ByteArray
    ): ResourceRecordedDataQueueItem? {
        val rumContextData = rumContextDataHandler.createRumContextData()
            ?: return null

        val item = ResourceRecordedDataQueueItem(
            recordedQueuedItemContext = rumContextData,
            identifier = identifier,
            resourceData = resourceData
        )

        insertIntoRecordedDataQueue(item)

        return item
    }

    @MainThread
    override fun addTouchEventItem(
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
    override fun addSnapshotItem(systemInformation: SystemInformation): SnapshotRecordedDataQueueItem? {
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
    override fun tryToConsumeItems() {
        // no need to create a thread if the queue is empty
        if (recordedDataQueue.isEmpty()) {
            return
        }

        executorService.executeSafe("Recorded Data queue processing", internalLogger) {
            triggerProcessingLoop()
        }
    }

    /**
     * The threadpool that triggers this method has a size/maxsize of 1.
     * This makes it implicitly synchronised so we should never have any multithreading issues
     * where one thread peeks while another removes the element.
     */
    @Suppress("NestedBlockDepth")
    @WorkerThread
    @Synchronized
    private fun triggerProcessingLoop() {
        while (recordedDataQueue.isNotEmpty()) {
            // peeking is safe here because we are in a synchronized block
            // and we check for isEmpty first
            @SuppressWarnings("UnsafeThirdPartyFunctionCall")
            val nextItem = recordedDataQueue.peek()

            if (nextItem != null) {
                val nextItemAgeInNs = System.nanoTime() - nextItem.creationTimeStampInNs
                if (!nextItem.isValid()) {
                    if (sampler.sample(Unit)) {
                        logInvalidQueueItemException(nextItem)
                    }
                    recordedDataQueue.poll()
                } else if (nextItemAgeInNs > MAX_DELAY_NS) {
                    if (sampler.sample(Unit)) {
                        logExpiredItemException(nextItemAgeInNs)
                    }
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

            is ResourceRecordedDataQueueItem ->
                processResourceEvent(nextItem)
        }
    }

    @WorkerThread
    private fun processSnapshotEvent(item: SnapshotRecordedDataQueueItem) {
        processor.processScreenSnapshots(item)
    }

    @WorkerThread
    private fun processResourceEvent(item: ResourceRecordedDataQueueItem) {
        processor.processResources(item)
    }

    @WorkerThread
    private fun processTouchEvent(item: TouchEventRecordedDataQueueItem) {
        processor.processTouchEventsRecords(item)
    }

    private fun insertIntoRecordedDataQueue(recordedDataQueueItem: RecordedDataQueueItem) {
        @Suppress("TooGenericExceptionCaught")
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

    private fun logInvalidQueueItemException(item: RecordedDataQueueItem) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            { ITEM_DROPPED_INVALID_MESSAGE.format(Locale.US, item.javaClass.simpleName) }
        )
    }

    private fun logAddToQueueException(e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { FAILED_TO_ADD_RECORDS_TO_QUEUE_ERROR_MESSAGE },
            e
        )
    }

    private fun logExpiredItemException(nextItemAgeInNs: Long) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            { ITEM_DROPPED_EXPIRED_MESSAGE.format(Locale.US, nextItemAgeInNs) }
        )
    }

    // end region

    internal companion object {
        @VisibleForTesting
        internal const val MAX_DELAY_NS = 1_000_000_000L // 1 second in ns

        internal const val FAILED_TO_ADD_RECORDS_TO_QUEUE_ERROR_MESSAGE =
            "SR RecordedDataQueueHandler: failed to add records into the queue"

        @VisibleForTesting
        internal const val ITEM_DROPPED_INVALID_MESSAGE =
            "SR RecordedDataQueueHandler: dropped item from the queue. isValid=false, type=%s"

        @VisibleForTesting
        internal const val ITEM_DROPPED_EXPIRED_MESSAGE =
            "SR RecordedDataQueueHandler: dropped item from the queue. age=%d ns"

        private const val TELEMETRY_SAMPLE_RATE_PERCENT =
            1f // 1% of the items will be logged
    }
}
