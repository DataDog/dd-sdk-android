/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Thread-safe buffer pairing a trigger-captured profiling artifact with the gating RUM error
 * event that promotes it to an upload.
 */
@NoOpImplementation
internal interface PendingTriggerProfiles {

    /**
     * Buffers a trigger-captured [PerfettoResult]. If a matching RUM gating event is already
     * pending, the pair is dispatched immediately via the on-match callback; otherwise the
     * result self-cleans after [EXPIRY_TIMEOUT_MS].
     */
    fun addProfilingResult(result: PerfettoResult)

    /**
     * Buffers a RUM gating [ProfilerEvent]. If a matching profiling result is already pending,
     * the pair is dispatched immediately via the on-match callback; otherwise the event
     * self-cleans after [EXPIRY_TIMEOUT_MS]. Non-ANR events are silently rejected.
     */
    fun addRumGatingEvent(event: ProfilerEvent)

    /**
     * Cancels any pending cleanup tasks and deletes any still-pending profiling result's
     * artifact file in place. Safe to call once.
     */
    fun stop()

    companion object {
        /**
         * How long a profiling result or RUM gating event may wait for its counterpart
         * before it is considered stale and dropped.
         */
        internal const val EXPIRY_TIMEOUT_MS = 5_000L
    }
}
