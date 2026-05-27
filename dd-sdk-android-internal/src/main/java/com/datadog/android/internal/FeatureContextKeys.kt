/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal

/**
 * Keys used to share state across features via the features context.
 */
object FeatureContextKeys {
    /**
     * Indicates whether the profiler is currently running.
     * Written by the profiling feature and read by RUM and debug widget consumers.
     */
    const val PROFILER_IS_RUNNING: String = "profiler_is_running"

    /**
     * Current RUM session identifier. Written by the RUM feature into its own feature
     * context on every session renewal; read by other features (e.g. profiling) that
     * subscribe via [com.datadog.android.api.feature.FeatureContextUpdateReceiver].
     */
    const val RUM_SESSION_ID: String = "session_id"

    /**
     * Sample rate (0..100) of the current RUM session. Written by the RUM feature into
     * its own feature context; read by other features that need to combine it with their
     * own sampling.
     */
    const val RUM_SESSION_SAMPLE_RATE: String = "session_sample_rate"
}
