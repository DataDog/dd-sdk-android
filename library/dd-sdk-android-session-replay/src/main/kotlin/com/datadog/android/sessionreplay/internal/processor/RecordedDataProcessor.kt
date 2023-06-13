/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import android.content.res.Configuration
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import java.lang.NullPointerException
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class RecordedDataProcessor(
    private val rumContextProvider: RumContextProvider,
    private val timeProvider: TimeProvider,
    private val executorService: ExecutorService,
    private val writer: RecordWriter,
    private val recordCallback: RecordCallback,
    private val mutationResolver: MutationResolver = MutationResolver(),
    private val nodeFlattener: NodeFlattener = NodeFlattener()
) : Processor {

    internal var prevRumContext: SessionReplayRumContext = SessionReplayRumContext()
    private var prevSnapshot: List<MobileSegment.Wireframe> = emptyList()
    private var lastSnapshotTimestamp = 0L
    private var previousOrientation = Configuration.ORIENTATION_UNDEFINED

    @MainThread
    override fun processScreenSnapshots(
        nodes: List<Node>,
        systemInformation: SystemInformation
    ) {
        buildRunnable { timestamp, newContext, currentContext ->
            Runnable {
                @Suppress("ThreadSafety") // this runs inside an executor
                handleSnapshots(
                    newContext,
                    currentContext,
                    timestamp,
                    nodes,
                    systemInformation
                )
            }
        }?.let { executeRunnable(it) }
    }

    @MainThread
    override fun processTouchEventsRecords(touchEventsRecords: List<MobileSegment.MobileRecord>) {
        buildRunnable { _, newContext, _ ->
            Runnable {
                @Suppress("ThreadSafety") // this runs inside an executor
                handleTouchRecords(newContext, touchEventsRecords)
            }
        }?.let { executeRunnable(it) }
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

    private fun executeRunnable(runnable: Runnable) {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            executorService.submit(runnable)
        } catch (e: RejectedExecutionException) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
        } catch (e: NullPointerException) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // the task will never be null so normally this exception should not be triggered.
            // In any case we will add a log here later.
        }
    }

    private fun buildRunnable(
        runnableFactory: (
            timestamp: Long,
            newContext: SessionReplayRumContext,
            prevRumContext: SessionReplayRumContext
        ) -> Runnable
    ): Runnable? {
        // we will make sure we get the timestamp on the UI thread to avoid time skewing
        val timestamp = timeProvider.getDeviceTimestamp()

        // TODO: RUMM-2426 Fetch the RumContext from the core SDKContext when available
        val newRumContext = rumContextProvider.getRumContext()

        if (newRumContext.isNotValid()) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            return null
        }

        // Because the runnable will be executed in another thread it can happen in case there is
        // an exception in the chain that the record cannot be sent. In this case we will have
        // a RUM view with `has_replay:true` but with no actual records. This is a corner case
        // that we discussed with the RUM team and unfortunately and was accepted as there is
        // another safety net logic in the player that handles this situation. Unfortunately this
        // is a constraint that we must accept as this whole `has_replay` logic was thought for
        // the browser SR sdk and not for mobile which handles features inter - communication
        // completely differently. In any case have in mind that after a discussion with the
        // browser team it appears that this situation may arrive also on their end and was
        // accepted.
        recordCallback.onRecordForViewSent(newRumContext.viewId)
        val runnable = runnableFactory(timestamp, newRumContext.copy(), prevRumContext.copy())

        prevRumContext = newRumContext

        return runnable
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
