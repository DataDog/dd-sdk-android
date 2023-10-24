/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import android.content.res.Configuration
import android.util.Log
import androidx.annotation.WorkerThread
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.TouchEventRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.WebViewRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonParser
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Suppress("TooManyFunctions")
internal class RecordedDataProcessor(
    private val writer: RecordWriter,
    private val mutationResolver: MutationResolver,
    private val timeProvider: TimeProvider,
    private val nodeFlattener: NodeFlattener = NodeFlattener()
) : Processor {
    private var prevSnapshot: List<MobileSegment.Wireframe> = emptyList()
    private var lastSnapshotTimestamp = 0L
    private var previousOrientation = Configuration.ORIENTATION_UNDEFINED
    private var prevRumContext: SessionReplayRumContext = SessionReplayRumContext()
    private var lastBrowserTimestamp: Long = 0L
    private var lastMobileTimestamp: Long = 0L
    private var lastBrowserEvent: String = ""

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
//        lastMobileTimestamp = item.recordedQueuedItemContext.timestamp
//        logTimestampDifference()
    }

    @WorkerThread
    override fun processTouchEventsRecords(item: TouchEventRecordedDataQueueItem) {
        handleTouchRecords(
            rumContext = item.recordedQueuedItemContext.newRumContext,
            touchData = item.touchData
        )
        lastMobileTimestamp = item.recordedQueuedItemContext.timestamp
        logTimestampDifference()
    }

    @WorkerThread
    override fun processWebViewRecord(item: WebViewRecordedDataQueueItem) {
        JsonParser.parseString(item.serializedRecord)?.asJsonObject?.let {
            val event = it.get("event").asJsonObject
            val timestampOffset = timeProvider.getTimestampOffset()
            val viewId = event.get("viewId")?.asString
            val recordType = event.get("data")?.asJsonObject?.get("type")?.asString
            if (viewId != null) {
                event.get("timestamp")?.let { timestamp ->
                    val asLong = timestamp.asLong
                    if (recordType == "7" || recordType == "9") {
                        lastBrowserTimestamp = asLong
                        lastBrowserEvent = item.serializedRecord
                    }
                    val difference = System.currentTimeMillis() - asLong
//                    if (difference < 0) {
//                        Log.v("WebViewRecord", "Timestamp difference: $difference")
//                    }
                    val correctedTimestamp = asLong + timestampOffset
                    it.addProperty("timestamp", correctedTimestamp)
                }
                val rumContext = item.recordedQueuedItemContext.newRumContext
                val enrichedRecord = EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    viewId,
                    listOf(event),
                    parentViewId = item.recordedQueuedItemContext.newRumContext.viewId,
                    isBrowser = true
                )
                writer.write(enrichedRecord)
            }
            if (recordType == "7" || recordType == "9") {
                logTimestampDifference()
            }
        }
    }

    // region Internal

    private fun logTimestampDifference() {
        val difference = lastMobileTimestamp - lastBrowserTimestamp
        Log.v("WebViewRecord", "Timestamp difference: $difference")
        if (abs(difference) > 3000) {
            Log.v("WebViewRecord", "Diff was greater than 3 seconds for event:\n$lastBrowserEvent")
        }
    }

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
    ):
        EnrichedRecord {
        return EnrichedRecord(
            rumContext.applicationId,
            rumContext.sessionId,
            rumContext.viewId,
            records.map { it.toJson() }
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
