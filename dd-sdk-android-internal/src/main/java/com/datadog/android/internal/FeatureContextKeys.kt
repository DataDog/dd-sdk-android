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

    /**
     * Reason profiling was denied by the quota API for the current RUM session.
     * Written by the profiling feature; read by RUM to include in event attributes.
     * Absent when profiling is allowed or no quota check has been performed.
     */
    const val PROFILING_QUOTA_REASON: String = "profiling_quota_reason"

    /**
     * RUM session id the [PROFILING_QUOTA_REASON] decision was made for. Written alongside
     * the reason by the profiling feature so consumers can detect (and ignore) a decision
     * that belongs to a previous session — e.g. while the new session's quota check is still
     * in flight.
     */
    const val PROFILING_QUOTA_SESSION_ID: String = "profiling_quota_session_id"
}
