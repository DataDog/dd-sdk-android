/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import android.content.res.Configuration
import androidx.annotation.WorkerThread
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.TouchEventRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class RecordedDataProcessor(
    private val writer: RecordWriter,
    private val mutationResolver: MutationResolver = MutationResolver(),
    private val nodeFlattener: NodeFlattener = NodeFlattener()
) : Processor {

    private var prevSnapshot: List<MobileSegment.Wireframe> = emptyList()
    private var lastSnapshotTimestamp = 0L
    private var previousOrientation = Configuration.ORIENTATION_UNDEFINED

    @WorkerThread
    override fun processScreenSnapshots(
        item: SnapshotRecordedDataQueueItem
    ) {
        handleSnapshots(
            newRumContext = item.rumContextData.newRumContext,
            prevRumContext = item.rumContextData.prevRumContext,
            timestamp = item.rumContextData.timestamp,
            snapshots = item.nodes,
            systemInformation = item.systemInformation
        )
    }

    @WorkerThread
    override fun processTouchEventsRecords(item: TouchEventRecordedDataQueueItem) {
        handleTouchRecords(
            rumContext = item.rumContextData.newRumContext,
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
        prevRumContext: SessionReplayRumContext,
        timestamp: Long,
        snapshots: List<Node>,
        systemInformation: SystemInformation
    ) {
        val wireframes = snapshots.flatMap { nodeFlattener.flattenNode(it) }

        if (wireframes.isEmpty()) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            return
        }

        val records: MutableList<MobileSegment.MobileRecord> = LinkedList()
        val isNewView = isNewView(prevRumContext, newRumContext)
        val isTimeForFullSnapshot = isTimeForFullSnapshot()
        val screenOrientationChanged = systemInformation.screenOrientation != previousOrientation
        val fullSnapshotRequired = isNewView || isTimeForFullSnapshot || screenOrientationChanged

        if (isNewView) {
            handleViewEndRecord(prevRumContext, timestamp)
            val screenBounds = systemInformation.screenBounds
            val metaRecord = MobileSegment.MobileRecord.MetaRecord(
                timestamp,
                MobileSegment.Data1(screenBounds.width, screenBounds.height)
            )
            val focusRecord = MobileSegment.MobileRecord.FocusRecord(
                timestamp,
                MobileSegment.Data2(true)
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
                timestamp,
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
        return if (System.nanoTime() - lastSnapshotTimestamp >= FULL_SNAPSHOT_INTERVAL_IN_NS) {
            lastSnapshotTimestamp = System.nanoTime()
            true
        } else {
            false
        }
    }

    private fun handleViewEndRecord(prevRumContext: SessionReplayRumContext, timestamp: Long) {
        if (prevRumContext.isValid()) {
            // send first the ViewEndRecord for the previous RUM context (View)
            val viewEndRecord = MobileSegment.MobileRecord.ViewEndRecord(timestamp)
            writer.write(bundleRecordInEnrichedRecord(prevRumContext, listOf(viewEndRecord)))
        }
    }

    private fun bundleRecordInEnrichedRecord(
        rumContext: SessionReplayRumContext,
        records: List<MobileSegment.MobileRecord>
    ):
        EnrichedRecord {
        return EnrichedRecord(
            rumContext.applicationId,
            rumContext.sessionId,
            rumContext.viewId,
            records
        )
    }

    private fun isNewView(
        newContext: SessionReplayRumContext,
        currentContext: SessionReplayRumContext
    ): Boolean {
        return newContext.applicationId != currentContext.applicationId ||
            newContext.sessionId != currentContext.sessionId ||
            newContext.viewId != currentContext.viewId
    }

    // endregion

    companion object {
        internal val FULL_SNAPSHOT_INTERVAL_IN_NS = TimeUnit.MILLISECONDS.toNanos(3000)
    }
}
