/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Events sent between the RUM feature and the profiling feature.
 */
sealed class ProfilerEvent {
    /**
     * Internal event signaling that TTID has been reached but the RUM session is not tracked
     * (unsampled session). No profiling data will be written.
     */
    object TTIDNotTracked : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature whenever an ANR is detected.
     *
     * @param id The ID of the corresponding RUM error event.
     * @param startMs Start timestamp in milliseconds since epoch (server time-adjusted).
     * @param durationNs Duration of the ANR in nanoseconds.
     * @param rumContext RUM context at the time of the ANR.
     */
    data class RumAnrEvent(
        val id: String,
        val startMs: Long,
        val durationNs: Long,
        val rumContext: ProfilingRumContext
    ) : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature when an OOM error event
     * has been successfully written. Allows the profiling feature to correlate
     * the captured heap histogram with the RUM error event.
     *
     * @param id The ID of the corresponding RUM error event.
     * @param timestamp Timestamp in milliseconds since epoch (server time-adjusted).
     * @param rumContext RUM context at the time of the OOM.
     */
    data class RumOomErrorEvent(
        val id: String,
        val timestamp: Long,
        val rumContext: ProfilingRumContext
    ) : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature when a memory anomaly
     * error event has been successfully written. Allows the profiling feature
     * to correlate the captured heap histogram with the RUM error event.
     *
     * @param id The ID of the corresponding RUM error event.
     * @param timestamp Timestamp in milliseconds since epoch (server time-adjusted).
     * @param rumContext RUM context at the time of the anomaly.
     */
    data class RumAnomalyErrorEvent(
        val id: String,
        val timestamp: Long,
        val rumContext: ProfilingRumContext
    ) : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature whenever a long task is detected.
     *
     * @param id The ID of the corresponding RUM long task event.
     * @param startMs Start timestamp in milliseconds since epoch (server time-adjusted).
     * @param durationNs Duration of the long task in nanoseconds.
     * @param rumContext RUM context at the time of the long task.
     */
    data class RumLongTaskEvent(
        val id: String,
        val startMs: Long,
        val durationNs: Long,
        val rumContext: ProfilingRumContext
    ) : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature whenever a vital is recorded.
     *
     * @param id The ID of the corresponding RUM vital event.
     * @param name The name of the corresponding vital event.
     * @param type Type of the vital event.
     * @param startMs Start timestamp in milliseconds since epoch (server time-adjusted).
     * @param durationNs Duration of the vital in nanoseconds.
     * @param rumContext RUM context at the time of the vital.
     */
    data class RumVitalEvent(
        val id: String,
        val name: String?,
        val type: Type,
        val startMs: Long,
        val durationNs: Long,
        val rumContext: ProfilingRumContext
    ) : ProfilerEvent() {
        /**
         * Type of the vital event.
         */
        enum class Type {
            /**
             * TTID (Time to initial display) type.
             */
            TTID,

            /**
             * TTFD (Time to full display) type.
             */
            TTFD,

            /**
             * Operation API type.
             */
            OPERATION
        }
    }
}
