/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger
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

    private val sessionReplaySkippedFramesCount: AtomicInteger = AtomicInteger(0)

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

    fun toGeneratedRse(ntpOffsetAtEndMs: Long): DdSdkAndroidRumLogger.Rse {
        return DdSdkAndroidRumLogger.Rse(
            precondition = startReason.asString,
            duration = calculateDuration(),
            wasStopped = wasStopped,
            viewsCount = DdSdkAndroidRumLogger.Rse.ViewsCount(
                total = trackedViewsById.size,
                background = trackedViewsById.values.count {
                    it.viewUrl == RumViewManagerScope.RUM_BACKGROUND_VIEW_URL
                },
                appLaunch = trackedViewsById.values.count {
                    it.viewUrl == RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL
                },
                withHasReplay = trackedViewsById.values.count { it.hasReplay }
            ),
            sdkErrorsCount = DdSdkAndroidRumLogger.Rse.SdkErrorsCount(
                total = errorKindFrequencies.values.sum(),
                byKind = resolveTop5ErrorsByKind()
            ),
            noViewEventsCount = DdSdkAndroidRumLogger.Rse.NoViewEventsCount(
                actions = missedEventCountByType[MissedEventType.ACTION] ?: 0,
                resources = missedEventCountByType[MissedEventType.RESOURCE] ?: 0,
                errors = missedEventCountByType[MissedEventType.ERROR] ?: 0,
                longTasks = missedEventCountByType[MissedEventType.LONG_TASK] ?: 0
            ),
            hasBackgroundEventsTrackingEnabled = hasTrackBackgroundEventsEnabled,
            ntpOffset = DdSdkAndroidRumLogger.Rse.NtpOffset(
                atStart = ntpOffsetAtStartMs,
                atEnd = ntpOffsetAtEndMs
            ),
            srSkippedFramesCount = sessionReplaySkippedFramesCount.get()
        )
    }

    private fun calculateDuration(): Long {
        return lastTrackedView?.let { last ->
            firstTrackedView?.let { first ->
                TimeUnit.MILLISECONDS.toNanos(last.startMs - first.startMs) + last.durationNs
            }
        } ?: 0L
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

        private const val TOP_ERROR_LIMIT = 5

        internal const val SDK_ERROR_DEFAULT_KIND = "Empty error kind"
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
