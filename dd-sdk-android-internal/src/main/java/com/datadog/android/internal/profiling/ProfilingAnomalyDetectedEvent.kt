/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Sent by the profiling feature to the RUM feature when ProfilingManager
 * notifies us of an anomaly trigger via the trigger-based capture API.
 *
 * Not limited to memory anomalies. May originate from any anomaly-style
 * system trigger, such as [ProfilingTrigger.TRIGGER_TYPE_ANOMALY]
 * where anomalous behaviors span all areas and the artifact varies by anomaly.
 *
 * @param detectedAtMs Timestamp (device clock, millis since epoch) when the
 *   profiling feature handled the anomaly trigger callback.
 */
data class ProfilingAnomalyDetectedEvent(
    val detectedAtMs: Long
)
