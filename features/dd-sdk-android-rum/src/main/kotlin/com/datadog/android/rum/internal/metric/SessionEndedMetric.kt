/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.model.ViewEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Metric for rum session ended event.
 */
@Suppress("TooManyFunctions")
internal class SessionEndedMetric(
    private val sessionId: String,
    private val startReason: RumSessionScope.StartReason,
    private val ntpOffsetAtStartMs: Long,
    private val hasTrackBackgroundEventsEnabled: Boolean
) {

    private val trackedViewsById = mutableMapOf<String, TrackedView>()

    private val errorKindFrequencies = mutableMapOf<String, Int>()

    private val missedEventCountByType = mutableMapOf<MissedEventType, Int>()

    private var sessionReplaySkippedFramesCount: AtomicInteger = AtomicInteger(0)

    private var firstTrackedView: TrackedView? = null
    private var lastTrackedView: TrackedView? = null
    private var wasStopped: Boolean = false

    /**
     * Called on view tracked event, return true if the view is recorded by the metric, false otherwise.
     */
    fun onViewTracked(rumViewEvent: ViewEvent): Boolean {
        if (rumViewEvent.session.id != sessionId) {
            return false
        }
        val trackedView = TrackedView(
            viewUrl = trackedViewsById[rumViewEvent.view.id]?.viewUrl ?: rumViewEvent.view.url,
            startMs = trackedViewsById[rumViewEvent.view.id]?.startMs ?: rumViewEvent.date,
            durationNs = rumViewEvent.view.timeSpent,
            hasReplay = rumViewEvent.session.hasReplay ?: false
        )

        trackedViewsById[rumViewEvent.view.id] = trackedView
        if (firstTrackedView == null) {
            firstTrackedView = trackedView
        }
        lastTrackedView = trackedView
        return true
    }

    fun onErrorTracked(sdkErrorKind: String?) {
        val errorKindKey = sdkErrorKind ?: SDK_ERROR_DEFAULT_KIND
        errorKindFrequencies[errorKindKey] = (errorKindFrequencies[errorKindKey] ?: 0) + 1
    }

    fun onSessionStopped() {
        wasStopped = true
    }

    fun onMissedEventTracked(missedEventType: MissedEventType) {
        missedEventCountByType[missedEventType] = (missedEventCountByType[missedEventType] ?: 0) + 1
    }

    fun onSessionReplaySkippedFrameTracked() {
        sessionReplaySkippedFramesCount.incrementAndGet()
    }

    fun toMetricAttributes(ntpOffsetAtEndMs: Long): Map<String, Any?> {
        return mapOf(
            METRIC_TYPE_KEY to METRIC_TYPE_VALUE,
            RSE_KEY to resolveRseAttributes(ntpOffsetAtEndMs)
        )
    }

    private fun calculateDuration(): Long {
        return lastTrackedView?.let { last ->
            firstTrackedView?.let { first ->
                TimeUnit.MILLISECONDS.toNanos(last.startMs - first.startMs) + last.durationNs
            }
        } ?: 0L
    }

    private fun resolveRseAttributes(ntpOffsetAtEnd: Long): Map<String, Any?> {
        return mapOf(
            PROCESS_TYPE_KEY to PROCESS_TYPE_VALUE,
            PRECONDITION_KEY to startReason.asString,
            DURATION_KEY to calculateDuration(),
            WAS_STOPPED_KEY to wasStopped,
            VIEW_COUNTS_KEY to resolveViewCountsAttributes(),
            SDK_ERRORS_COUNT_KEY to resolveSDKErrorsCountAttributes(),
            NO_VIEW_EVENTS_COUNT_KEY to resolveNoViewCountsAttributes(),
            HAS_BACKGROUND_EVENTS_TRACKING_ENABLED_KEY to hasTrackBackgroundEventsEnabled,
            NTP_OFFSET_KEY to resolveNtpOffsetAttributes(ntpOffsetAtEnd),
            SESSION_REPLAY_SKIPPED_FRAMES_COUNT to sessionReplaySkippedFramesCount.get()
        )
    }

    private fun resolveNoViewCountsAttributes(): Map<String, Int> {
        return mapOf(
            NO_VIEW_EVENTS_COUNT_ACTIONS_KEY to (
                missedEventCountByType[MissedEventType.ACTION]
                    ?: 0
                ),
            NO_VIEW_EVENTS_COUNT_RESOURCES_KEY to (
                missedEventCountByType[MissedEventType.RESOURCE]
                    ?: 0
                ),
            NO_VIEW_EVENTS_COUNT_ERRORS_KEY to (
                missedEventCountByType[MissedEventType.ERROR]
                    ?: 0
                ),
            NO_VIEW_EVENTS_COUNT_LONG_TASKS_KEY to (
                missedEventCountByType[MissedEventType.LONG_TASK]
                    ?: 0
                )
        )
    }

    private fun resolveNtpOffsetAttributes(ntpOffsetAtEnd: Long): Map<String, Long> {
        return mapOf(
            NTP_OFFSET_AT_START_KEY to ntpOffsetAtStartMs,
            NTP_OFFSET_AT_END_KEY to ntpOffsetAtEnd
        )
    }

    private fun resolveViewCountsAttributes(): Map<String, Any?> {
        return mapOf(
            VIEW_COUNTS_TOTAL_KEY
                to trackedViewsById.size,
            VIEW_COUNTS_BG_KEY
                to trackedViewsById.values.count { it.viewUrl == RumViewManagerScope.RUM_BACKGROUND_VIEW_URL },
            VIEW_COUNTS_APP_LAUNCH_KEY
                to trackedViewsById.values.count { it.viewUrl == RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL },
            VIEW_COUNT_WITH_HAS_REPLAY
                to trackedViewsById.values.count { it.hasReplay }
        )
    }

    private fun resolveSDKErrorsCountAttributes(): Map<String, Any?> {
        return mapOf(
            SDK_ERRORS_COUNT_TOTAL_KEY to errorKindFrequencies.values.sum(),
            SDK_ERRORS_COUNT_BY_KIND_KEY to resolveTop5ErrorsByKind()
        )
    }

    private fun resolveTop5ErrorsByKind(): Map<String, Int> {
        val limit = TOP_ERROR_LIMIT.coerceAtMost(errorKindFrequencies.size)
        return errorKindFrequencies.entries.sortedByDescending {
            it.value
        }.subList(0, limit).associate { entry ->
            escapeNonAlphanumericCharacters(entry.key) to entry.value
        }
    }

    private fun escapeNonAlphanumericCharacters(key: String): String {
        val regex = Regex("[^\\w']+")
        return key.replace(regex, "_")
    }

    companion object {

        /**
         * The metric holds the map of TOP 5 error kinds to the number of their occurrences in the session.
         */
        private const val TOP_ERROR_LIMIT = 5

        /**
         * Title of the metric to be sent.
         */
        internal const val RUM_SESSION_ENDED_METRIC_NAME: String = "[Mobile Metric] RUM Session Ended"

        /**
         * Basic Metric type key.
         */
        internal const val METRIC_TYPE_KEY: String = PerformanceMetric.METRIC_TYPE

        /**
         * Metric type value.
         */
        internal const val METRIC_TYPE_VALUE: String = "rum session ended"

        /**
         * Name space of bundling.
         */
        internal const val RSE_KEY = "rse"

        /**
         * Key of process type.
         */
        internal const val PROCESS_TYPE_KEY = "process_type"

        /**
         * The type of OS component where the session was tracked. Android platform has only "app" type.
         */
        internal const val PROCESS_TYPE_VALUE = "app"

        /**
         * Key of the reason that led to the creation of this session.
         */
        internal const val PRECONDITION_KEY = "precondition"

        /**
         * Key of the session's duration in nanoseconds.
         */
        internal const val DURATION_KEY = "duration"

        /**
         * Key of boolean value indicating if the session was stopped through stopSession() API.
         */
        internal const val WAS_STOPPED_KEY = "was_stopped"

        /**
         * Key of the attributes of view counts.
         */
        internal const val VIEW_COUNTS_KEY = "views_count"

        /**
         * Key of the total view counts.
         */
        internal const val VIEW_COUNTS_TOTAL_KEY = "total"

        /**
         * Key of the number of standard "Background" views tracked during this session.
         */
        internal const val VIEW_COUNTS_BG_KEY = "background"

        /**
         * Key of the number of standard "ApplicationLaunch" views tracked during this session.
         */
        internal const val VIEW_COUNTS_APP_LAUNCH_KEY = "app_launch"

        /**
         * The number of views with has_replay == true.
         */
        internal const val VIEW_COUNT_WITH_HAS_REPLAY = "with_has_replay"

        /**
         * The key of attribute for the number of events dropped due to the absence of an active view at the time they occurred.
         */
        internal const val NO_VIEW_EVENTS_COUNT_KEY = "no_view_events_count"

        /**
         * The number of actions dropped due to the absence of an active view at the time they occurred.
         */
        internal const val NO_VIEW_EVENTS_COUNT_ACTIONS_KEY = "actions"

        /**
         * The number of resources dropped because there was no active view at the time they occurred.
         */
        internal const val NO_VIEW_EVENTS_COUNT_RESOURCES_KEY = "resources"

        /**
         * The number of errors dropped because there was no active view at the time they occurred.
         */
        internal const val NO_VIEW_EVENTS_COUNT_ERRORS_KEY = "errors"

        /**
         * The number of long tasks dropped because there was no active view at the time they occurred.
         */
        internal const val NO_VIEW_EVENTS_COUNT_LONG_TASKS_KEY = "long_tasks"

        /**
         * Boolean value indicating whether tracking of background events was enabled in the RUM configuration.
         */
        internal const val HAS_BACKGROUND_EVENTS_TRACKING_ENABLED_KEY = "has_background_events_tracking_enabled"

        /**
         * The key of NTP offset attributes.
         */
        internal const val NTP_OFFSET_KEY = "ntp_offset"

        /**
         * The NTP offset at the beginning of the session, measured in milliseconds.
         */
        internal const val NTP_OFFSET_AT_START_KEY = "at_start"

        /**
         * The NTP offset at the end of the session, measured in milliseconds.
         */
        internal const val NTP_OFFSET_AT_END_KEY = "at_end"

        /**
         * Key of the sdk errors attribute.
         */
        internal const val SDK_ERRORS_COUNT_KEY = "sdk_errors_count"

        /**
         * Key of the counts the total number of SDK errors that occurred during the session.
         */
        internal const val SDK_ERRORS_COUNT_TOTAL_KEY = "total"

        /**
         * Key of TOP [TOP_ERROR_LIMIT] error kinds to the number of their occurrences in the session.
         */
        internal const val SDK_ERRORS_COUNT_BY_KIND_KEY = "by_kind"

        /**
         * Placeholder of error kind if the attribute is absent.
         */
        internal const val SDK_ERROR_DEFAULT_KIND = "Empty error kind"

        /**
         * Key of the counts the total frames skipped in session replay by dynamic optimisation.
         */
        internal const val SESSION_REPLAY_SKIPPED_FRAMES_COUNT = "sr_skipped_frames_count"
    }

    /**
     * Class to hold the view information tracked by the metric.
     */
    internal data class TrackedView(
        val viewUrl: String,
        val startMs: Long,
        val durationNs: Long,
        val hasReplay: Boolean
    )

    internal enum class MissedEventType {
        ACTION,
        RESOURCE,
        ERROR,
        LONG_TASK;

        companion object {
            fun fromRawEvent(rawEvent: RumRawEvent): MissedEventType? {
                return when (rawEvent) {
                    is RumRawEvent.AddError,
                    is RumRawEvent.StopResourceWithError -> ERROR

                    is RumRawEvent.StartAction -> ACTION
                    is RumRawEvent.StartResource -> RESOURCE
                    is RumRawEvent.AddLongTask -> LONG_TASK
                    else -> null
                }
            }
        }
    }
}
