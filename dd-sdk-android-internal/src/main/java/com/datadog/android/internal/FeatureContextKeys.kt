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
    /** Immutable [com.datadog.android.internal.rum.RumContext] snapshot published by RUM. */
    const val RUM_CONTEXT: String = "rum_context"

    /** Whether Session Replay has persisted records for a view, stored in that view's metadata. */
    const val SESSION_REPLAY_VIEW_HAS_REPLAY: String = "has_replay"

    /** Number of persisted Session Replay records for a view, stored as a [Long] in its metadata. */
    const val SESSION_REPLAY_VIEW_RECORDS_COUNT: String = "records_count"

    /**
     * Indicates whether the profiler is currently running.
     * Written by the profiling feature and read by RUM and debug widget consumers.
     */
    const val PROFILER_IS_RUNNING: String = "profiler_is_running"

    /**
     * Sample rate (0..100) configured for continuous profiling. Written by the profiling
     * feature and read by RUM when building the SDK configuration telemetry event.
     */
    const val PROFILING_SAMPLE_RATE: String = "profiling_sample_rate"

    /**
     * Sample rate (0..100) configured for application launch profiling. Written by the
     * profiling feature and read by RUM when building the SDK configuration telemetry event.
     */
    const val PROFILING_APPLICATION_LAUNCH_SAMPLE_RATE: String = "profiling_application_launch_sample_rate"

    /**
     * Whether ANR-triggered profiling is enabled. Written by the profiling feature and read
     * by RUM when building the SDK configuration telemetry event.
     */
    const val PROFILING_ANR_ENABLED: String = "profiling_anr_enabled"

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
