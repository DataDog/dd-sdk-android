/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.Recorder

internal class CapturePipelineSelector(
    private val compositionEnabled: Boolean,
    private val legacyFactory: () -> Recorder,
    private val compositionFactory: () -> Recorder
) {
    fun create(): Recorder = if (compositionEnabled) compositionFactory() else legacyFactory()
}

/**
 * Lifecycle entry point for the composition capture pipeline. Platform callback wiring and
 * traversal are added by later workstreams; keeping this recorder and its orchestration isolated
 * prevents legacy state from crossing pipelines.
 */
internal class CompositionCapturePipeline(
    private val orchestrator: SnapshotCaptureOrchestrator,
    private val lifecycle: CompositionCaptureLifecycle,
    private val completionQueue: SnapshotCompletionQueue,
    private val internalLogger: InternalLogger
) : Recorder {
    override fun registerCallbacks() {
        lifecycle.registerCallbacks()
    }

    override fun unregisterCallbacks() {
        lifecycle.unregisterCallbacks()
    }

    override fun stopProcessingRecords() {
        orchestrator.shutdown()
        completionQueue.stop()
    }

    override fun resumeRecorders() {
        orchestrator.start()
        lifecycle.start()
    }

    /** Embedded content is not captured by this pipeline yet, so the request is only reported. */
    override fun requestCapture(slotIds: Set<String>) {
        if (slotIds.isEmpty()) return
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { EMBEDDED_CONTENT_UNSUPPORTED_WITH_COMPOSITION_RECORDING_MESSAGE },
            onlyOnce = true
        )
    }

    override fun stopRecorders() {
        lifecycle.stop()
        orchestrator.stop()
    }

    internal companion object {
        internal const val EMBEDDED_CONTENT_UNSUPPORTED_WITH_COMPOSITION_RECORDING_MESSAGE = "Embedded content is " +
            "not supported by the composition-tree recording pipeline yet. No embedded content will be recorded " +
            "for this session."
    }
}
