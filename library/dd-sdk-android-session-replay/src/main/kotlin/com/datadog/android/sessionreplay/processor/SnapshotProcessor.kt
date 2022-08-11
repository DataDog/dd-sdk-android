/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.Node
import com.datadog.android.sessionreplay.recorder.OrientationChanged
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.datadog.android.sessionreplay.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.utils.TimeProvider
import com.datadog.android.sessionreplay.writer.RecordWriter
import com.datadog.android.sessionreplay.writer.Writer
import java.lang.NullPointerException
import java.util.LinkedList
import java.util.Stack
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

internal class SnapshotProcessor(
    private val rumContextProvider: RumContextProvider,
    private val timeProvider: TimeProvider,
    private val executorService: ExecutorService,
    private val writer: Writer = RecordWriter()
) : Processor {

    private var currentRumContext: SessionReplayRumContext = SessionReplayRumContext()

    @MainThread
    override fun process(node: Node) {
        if (node.wireframes.isEmpty()) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            return
        }

        // we will make sure we get the timestamp on the UI thread to avoid time skewing
        val recordsTimestamp = timeProvider.getDeviceTimestamp()

        // TODO: RUMM-2426 Fetch the RumContext from the core SDKContext when available
        val newRumContext = rumContextProvider.getRumContext()

        if (newRumContext.isNotValid()) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            return
        }

        // For the time being until we can log those.
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            executorService.submit {
                @Suppress("ThreadSafety") // this runs inside an executor
                handleSnapshot(newRumContext, currentRumContext, recordsTimestamp, node)
            }
        } catch (e: RejectedExecutionException) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
        } catch (e: NullPointerException) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // the task will never be null so normally this exception should not be triggered.
            // In any case we will add a log here later.
        }

        currentRumContext = newRumContext
    }

    @MainThread
    override fun process(event: OrientationChanged) {
        // TODO RUMM-2271
    }

    @MainThread
    override fun process(touchData: MobileSegment.MobileIncrementalData.TouchData) {
        // TODO RUMM-2271
    }

    // region Internal

    @WorkerThread
    private fun handleSnapshot(
        newRumContext: SessionReplayRumContext,
        currentRumContext: SessionReplayRumContext,
        timestamp: Long,
        snapshot: Node
    ) {
        val wireframes = filterOutInvalidWireframes(flattenNode(snapshot))

        if (wireframes.isEmpty()) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            return
        }

        val records: MutableList<MobileSegment.MobileRecord> = LinkedList()
        val rootWireframeBounds = wireframes[0].bounds()

        if (checkForNewView(currentRumContext, newRumContext)) {
            val metaRecord = MobileSegment.MobileRecord.MetaRecord(
                timestamp,
                MobileSegment.Data1(rootWireframeBounds.width, rootWireframeBounds.height)
            )
            records.add(metaRecord)
        }
        val record = MobileSegment.MobileRecord.MobileFullSnapshotRecord(
            timeProvider.getDeviceTimestamp(),
            MobileSegment.Data(wireframes)
        )
        records.add(record)
        writer.write(
            EnrichedRecord(
                newRumContext.applicationId,
                newRumContext.sessionId,
                newRumContext.viewId,
                records
            )
        )
    }

    private fun checkForNewView(
        newContext: SessionReplayRumContext,
        currentContext: SessionReplayRumContext
    ): Boolean {
        return newContext.applicationId != currentContext.applicationId ||
            newContext.sessionId != currentContext.sessionId ||
            newContext.viewId != currentContext.viewId
    }

    private fun filterOutInvalidWireframes(wireframes: List<MobileSegment.Wireframe>):
        List<MobileSegment.Wireframe> {
        return wireframes.filterIndexed { index, wireframe ->
            checkWireframe(wireframe, wireframes.drop(index + 1))
        }
    }

    private fun flattenNode(root: Node): List<MobileSegment.Wireframe> {
        val stack = Stack<Node>()
        val list = LinkedList<MobileSegment.Wireframe>()
        stack.push(root)
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            list.addAll(node.wireframes)
            for (i in node.children.count() - 1 downTo 0) {
                stack.push(node.children[i])
            }
        }
        return list
    }

    private fun checkWireframe(
        wireframe: MobileSegment.Wireframe,
        topWireframes: List<MobileSegment.Wireframe>
    ): Boolean {
        val wireframeBounds = wireframe.bounds()
        if (wireframeBounds.width <= 0 || wireframeBounds.height <= 0) {
            return false
        }
        topWireframes.forEach {
            if (it.bounds().isCovering(wireframeBounds)) {
                return false
            }
        }
        return true
    }

    private fun MobileSegment.Wireframe.bounds(): Bounds {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.bounds()
            is MobileSegment.Wireframe.TextWireframe -> this.bounds()
        }
    }

    private fun MobileSegment.Wireframe.ShapeWireframe.bounds(): Bounds {
        return Bounds(x, y, width, height)
    }

    private fun MobileSegment.Wireframe.TextWireframe.bounds(): Bounds {
        return Bounds(x, y, width, height)
    }

    private fun Bounds.isCovering(other: Bounds): Boolean {
        return this.x <= other.x &&
            x + width >= other.x + other.width &&
            y <= other.y &&
            y + height >= other.y + other.height
    }

    private data class Bounds(val x: Long, val y: Long, val width: Long, val height: Long)

    // endregion
}
