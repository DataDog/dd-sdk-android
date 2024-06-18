/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.model.ViewEvent
import java.util.concurrent.TimeUnit

/**
 * Metric for rum session ended event.
 */
@Suppress("TooManyFunctions")
internal class SessionEndedMetric(
    private val sessionId: String,
    private val startReason: RumSessionScope.StartReason
) {

    private val trackedViewsById = mutableMapOf<String, TrackedView>()

    private val errorKindFrequencies = mutableMapOf<String, Int>()

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
            durationNs = rumViewEvent.view.timeSpent
        )

        trackedViewsById[rumViewEvent.view.id] = trackedView
        if (firstTrackedView == null) {
            firstTrackedView = trackedView
        }
        lastTrackedView = trackedView
        return true
    }

    fun onErrorTracked(sdkErrorKind: String) {
        errorKindFrequencies[sdkErrorKind] = (errorKindFrequencies[sdkErrorKind] ?: 0) + 1
    }

    fun onSessionStopped() {
        wasStopped = true
    }

    fun toMetricAttributes(): Map<String, Any?> {
        return mapOf(
            METRIC_TYPE_KEY to METRIC_TYPE_VALUE,
            RSE_KEY to resolveRseAttributes()
        )
    }

    private fun calculateDuration(): Long {
        return lastTrackedView?.let { last ->
            firstTrackedView?.let { first ->
                TimeUnit.MILLISECONDS.toNanos(last.startMs - first.startMs) + last.durationNs
            }
        } ?: 0L
    }

    private fun resolveRseAttributes(): Map<String, Any?> {
        return mapOf(
            PROCESS_TYPE_KEY to PROCESS_TYPE_VALUE,
            PRECONDITION_KEY to startReason.asString,
            DURATION_KEY to calculateDuration(),
            WAS_STOPPED_KEY to wasStopped,
            VIEW_COUNTS_KEY to resolveViewCountsAttributes(),
            SDK_ERRORS_COUNT_KEY to resolveSDKErrorsCountAttributes()
        )
    }

    private fun resolveViewCountsAttributes(): Map<String, Any?> {
        return mapOf(
            VIEW_COUNTS_TOTAL_KEY
                to trackedViewsById.size,
            VIEW_COUNTS_BG_KEY
                to trackedViewsById.values.count { it.viewUrl == RumViewManagerScope.RUM_BACKGROUND_VIEW_URL },
            VIEW_COUNTS_APP_LAUNCH_KEY
                to trackedViewsById.values.count { it.viewUrl == RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL }
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
    }

    /**
     * Class to hold the view information tracked by the metric.
     */
    internal data class TrackedView(
        val viewUrl: String,
        val startMs: Long,
        val durationNs: Long
    )
}
