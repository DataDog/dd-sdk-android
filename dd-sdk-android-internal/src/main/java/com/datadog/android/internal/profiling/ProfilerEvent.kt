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
     * Sent by the RUM feature to the profiling feature when a fatal OOM error event is held
     * until the outcome of its OOM profile is known, and only then. Allows the profiling feature
     * to correlate the captured heap histogram with the RUM error event.
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
     * Sent by the RUM feature to the profiling feature on launch, when a fatal OOM error held by a
     * previous process is loaded and waits for the outcome of its OOM profile.
     *
     * @param id The ID of the held RUM error event.
     */
    data class RumHeldOomErrorEvent(
        val id: String
    ) : ProfilerEvent()

    /**
     * Sent by the profiling feature to the RUM feature once it can answer about OOM profiles, so
     * that a held RUM error announced before profiling was registered is announced again.
     */
    object OomProfilingReady : ProfilerEvent()

    /**
     * Sent by the profiling feature to the RUM feature before writing an OOM profile, to ask
     * whether it may be attached to the held RUM error. Answered with [RumOomProfileAttachReply].
     *
     * @param rumErrorId The ID of the RUM error event the profile belongs to.
     */
    data class OomProfileAttachRequest(
        val rumErrorId: String
    ) : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature in reply to [OomProfileAttachRequest].
     * A grant is sent only once the reservation is durably stored.
     *
     * @param rumErrorId The ID of the RUM error event the profile belongs to.
     * @param granted Whether the profile may be written.
     */
    data class RumOomProfileAttachReply(
        val rumErrorId: String,
        val granted: Boolean
    ) : ProfilerEvent()

    /**
     * Sent by the profiling feature to the RUM feature with the outcome of the OOM profile of a
     * held RUM error. Sent again until acknowledged with [RumOomProfileResolutionAck].
     *
     * @param rumErrorId The ID of the held RUM error event.
     * @param outcome Whether the OOM profile was written.
     */
    data class OomProfileResolution(
        val rumErrorId: String,
        val outcome: Outcome
    ) : ProfilerEvent() {
        /**
         * Outcome of an OOM profile.
         */
        enum class Outcome {
            /**
             * The OOM profile was written.
             */
            ATTACHED,

            /**
             * No OOM profile was or will be written.
             */
            NOT_ATTACHED
        }
    }

    /**
     * Sent by the RUM feature to the profiling feature once the outcome received in
     * [OomProfileResolution] is durably stored.
     *
     * @param rumErrorId The ID of the held RUM error event.
     */
    data class RumOomProfileResolutionAck(
        val rumErrorId: String
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
