/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.internal.recorder.Recorder

internal class CapturePipelineSelector(
    private val compositionEnabled: Boolean,
    private val legacyFactory: () -> Recorder,
    private val compositionFactory: () -> Recorder
) {
    fun create(): Recorder = if (compositionEnabled) compositionFactory() else legacyFactory()
}

/**
 * Lifecycle entry point for the composition capture pipeline. Traversal and capture are added by
 * later workstreams; keeping this recorder isolated prevents legacy state from crossing pipelines.
 */
internal class CompositionCapturePipeline : Recorder {
    override fun registerCallbacks() = Unit

    override fun unregisterCallbacks() = Unit

    override fun stopProcessingRecords() = Unit

    override fun resumeRecorders() = Unit

    override fun stopRecorders() = Unit
}
