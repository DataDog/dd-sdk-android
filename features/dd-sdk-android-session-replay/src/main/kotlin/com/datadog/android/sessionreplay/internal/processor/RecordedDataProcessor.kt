/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import android.content.res.Configuration
import androidx.annotation.WorkerThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.internal.async.ResourceRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.TouchEventRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.SystemInformation
import java.util.LinkedList
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class RecordedDataProcessor(
    private val resourceDataStoreManager: ResourceDataStoreManager,
    private val resourcesWriter: ResourcesWriter,
    private val writer: RecordWriter,
    private val mutationResolver: MutationResolver,
    private val timeProvider: TimeProvider,
    private val nodeFlattener: NodeFlattener = NodeFlattener()
) : Processor {
    private var prevSnapshot: List<MobileSegment.Wireframe> = emptyList()
    private var lastSnapshotTimestamp = 0L
    private var previousOrientation = Configuration.ORIENTATION_UNDEFINED
    private var prevRumContext: SessionReplayRumContext = SessionReplayRumContext()

    @WorkerThread
    override fun processResources(
        item: ResourceRecordedDataQueueItem
    ) {
        val resourceHash = item.identifier
        val isKnownResource = resourceDataStoreManager.isPreviouslySentResource(resourceHash)

        if (!isKnownResource) {
            // the cacheResourceHash method overwrites the datastore entry and we don't want that if we haven't finished
            // initializing
            if (resourceDataStoreManager.isReady()) {
                resourceDataStoreManager.cacheResourceHash(resourceHash)
            }

            val enrichedResource = EnrichedResource(
                resource = item.resourceData,
                filename = resourceHash,
                mimeType = item.mimeType
            )

            resourcesWriter.write(enrichedResource)
        }
    }

    @WorkerThread
    override fun processScreenSnapshots(
        item: SnapshotRecordedDataQueueItem
    ) {
        handleSnapshots(
            newRumContext = item.recordedQueuedItemContext.newRumContext,
            timestamp = item.recordedQueuedItemContext.timestamp,
            snapshots = item.nodes,
            systemInformation = item.systemInformation
        )
        prevRumContext = item.recordedQueuedItemContext.newRumContext
    }

    @WorkerThread
    override fun processTouchEventsRecords(item: TouchEventRecordedDataQueueItem) {
        handleTouchRecords(
            rumContext = item.recordedQueuedItemContext.newRumContext,
            touchData = item.touchData
        )
    }

    // region Internal

    @WorkerThread
    private fun handleTouchRecords(
        rumContext: SessionReplayRumContext,
        touchData: List<MobileSegment.MobileRecord>
    ) {
        val enrichedRecord = bundleRecordInEnrichedRecord(rumContext, touchData)
        writer.write(enrichedRecord)
    }

    @WorkerThread
    private fun handleSnapshots(
        newRumContext: SessionReplayRumContext,
        timestamp: Long,
        snapshots: List<Node>,
        systemInformation: SystemInformation
    ) {
        val wireframes = snapshots.flatMap { nodeFlattener.flattenNode(it) }

        if (wireframes.isEmpty()) {
            return
        }

        val records: MutableList<MobileSegment.MobileRecord> = LinkedList()
        val isNewView = isNewView(newRumContext)
        val isTimeForFullSnapshot = isTimeForFullSnapshot()
        val screenOrientationChanged = systemInformation.screenOrientation != previousOrientation
        val fullSnapshotRequired = isNewView || isTimeForFullSnapshot || screenOrientationChanged

        if (isNewView) {
            handleViewEndRecord(timestamp)
            val screenBounds = systemInformation.screenBounds
            val metaRecord = MobileSegment.MobileRecord.MetaRecord(
                timestamp = timestamp,
                data = MobileSegment.Data1(screenBounds.width, screenBounds.height)
            )
            val focusRecord = MobileSegment.MobileRecord.FocusRecord(
                timestamp = timestamp,
                data = MobileSegment.Data2(true)
            )
            records.add(metaRecord)
            records.add(focusRecord)
        }

        if (screenOrientationChanged) {
            val screenBounds = systemInformation.screenBounds
            val viewPortResizeData = MobileSegment.MobileIncrementalData.ViewportResizeData(
                screenBounds.width,
                screenBounds.height
            )
            val viewportRecord = MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
                timestamp = timestamp,
                data = viewPortResizeData
            )
            records.add(viewportRecord)
        }

        if (fullSnapshotRequired) {
            records.add(
                MobileSegment.MobileRecord.MobileFullSnapshotRecord(
                    timestamp,
                    MobileSegment.Data(wireframes)
                )
            )
        } else {
            mutationResolver.resolveMutations(prevSnapshot, wireframes)?.let {
                records.add(
                    MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
                        timestamp,
                        it
                    )
                )
            }
        }
        prevSnapshot = wireframes
        previousOrientation = systemInformation.screenOrientation
        if (records.isNotEmpty()) {
            writer.write(bundleRecordInEnrichedRecord(newRumContext, records))
        }
    }

    private fun isTimeForFullSnapshot(): Boolean {
        return if (timeProvider.getDeviceElapsedTimeNanos() - lastSnapshotTimestamp >= FULL_SNAPSHOT_INTERVAL_IN_NS) {
            lastSnapshotTimestamp = timeProvider.getDeviceElapsedTimeNanos()
            true
        } else {
            false
        }
    }

    private fun handleViewEndRecord(timestamp: Long) {
        if (prevRumContext.isValid()) {
            // send first the ViewEndRecord for the previous RUM context (View)
            val viewEndRecord = MobileSegment.MobileRecord.ViewEndRecord(timestamp)
            writer.write(bundleRecordInEnrichedRecord(prevRumContext, listOf(viewEndRecord)))
        }
    }

    private fun bundleRecordInEnrichedRecord(
        rumContext: SessionReplayRumContext,
        records: List<MobileSegment.MobileRecord>
    ): EnrichedRecord {
        return EnrichedRecord(
            rumContext.applicationId,
            rumContext.sessionId,
            rumContext.viewId,
            records
        )
    }

    private fun isNewView(
        newContext: SessionReplayRumContext
    ): Boolean {
        return newContext.applicationId != prevRumContext.applicationId ||
            newContext.sessionId != prevRumContext.sessionId ||
            newContext.viewId != prevRumContext.viewId
    }

    // endregion

    companion object {
        internal val FULL_SNAPSHOT_INTERVAL_IN_NS = TimeUnit.MILLISECONDS.toNanos(3000)
    }
}
