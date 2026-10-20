/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import java.util.concurrent.ExecutorService

/**
 * Owns the composition capture lifecycle and its construction and completion workers.
 */
internal class CompositionCapturePipeline(
    private val orchestrator: SnapshotCaptureOrchestrator,
    private val lifecycle: CompositionCaptureLifecycle,
    private val completionQueue: SnapshotCompletionQueue,
    private val constructionExecutor: ExecutorService,
    private val resourceResolver: ResourceResolver? = null,
    private val resourceDataQueueHandler: DataQueueHandler? = null
) : Recorder {
    override fun registerCallbacks() {
        lifecycle.registerCallbacks()
        resourceResolver?.registerCallbacks()
    }

    override fun unregisterCallbacks() {
        lifecycle.unregisterCallbacks()
        resourceResolver?.unregisterCallbacks()
    }

    override fun stopProcessingRecords() {
        orchestrator.shutdown()
        constructionExecutor.shutdownNow()
        completionQueue.stop()
        resourceDataQueueHandler?.clearAndStopProcessingQueue()
    }

    override fun resumeRecorders() {
        orchestrator.start()
        lifecycle.start()
    }

    override fun onRumViewChanged() {
        orchestrator.requestCapture()
    }

    /** A full generation includes current slot geometry, including slots in otherwise idle hosts. */
    override fun requestCapture(slotIds: Set<String>) {
        if (slotIds.isEmpty()) return
        orchestrator.requestCapture()
    }

    override fun stopRecorders() {
        lifecycle.stop()
        orchestrator.stop()
    }
}
