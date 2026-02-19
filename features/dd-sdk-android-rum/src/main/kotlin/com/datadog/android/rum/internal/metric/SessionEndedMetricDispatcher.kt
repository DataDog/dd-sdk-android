/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger
import com.datadog.android.rum.model.ViewEvent
import java.util.concurrent.ConcurrentHashMap

internal class SessionEndedMetricDispatcher(
    private val internalLogger: InternalLogger,
    private val sessionSamplingRate: Float
) :
    SessionMetricDispatcher {

    private val logger = DdSdkAndroidRumLogger(internalLogger)

    private val metricsBySessionId = ConcurrentHashMap<String, SessionEndedMetric>()

    override fun startMetric(
        sessionId: String,
        startReason: RumSessionScope.StartReason,
        ntpOffsetAtStartMs: Long,
        backgroundEventTracking: Boolean
    ) {
        metricsBySessionId[sessionId] = SessionEndedMetric(
            sessionId = sessionId,
            startReason = startReason,
            ntpOffsetAtStartMs = ntpOffsetAtStartMs,
            hasTrackBackgroundEventsEnabled = backgroundEventTracking
        )
    }

    override fun endMetric(sessionId: String, ntpOffsetAtEndMs: Long) {
        // the argument is always non - null, so we can suppress the warning
        @Suppress("UnsafeThirdPartyFunctionCall")
        metricsBySessionId.remove(sessionId)?.let { metric: SessionEndedMetric ->
            logger.logRumSessionEnded(
                creationSampleRate = sessionSamplingRate,
                rse = metric.toGeneratedRse(ntpOffsetAtEndMs)
            )
        }
    }

    override fun onSessionStopped(sessionId: String) {
        metricsBySessionId[sessionId]?.onSessionStopped()
    }

    override fun onViewTracked(sessionId: String, viewEvent: ViewEvent) {
        val result = metricsBySessionId[sessionId]?.onViewTracked(viewEvent) ?: false
        if (result.not()) {
            val viewType = when (viewEvent.view.url) {
                RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL -> "AppLaunch"
                RumViewManagerScope.RUM_BACKGROUND_VIEW_URL -> "Background"
                else -> "Custom"
            }
            logger.logSessionViewTrackError(viewType = viewType, sessionId = sessionId)
        }
    }

    override fun onSdkErrorTracked(sessionId: String, errorKind: String?) {
        metricsBySessionId[sessionId]?.onErrorTracked(errorKind) ?: logger.logSessionSdkErrorTrackError(
            errorKind = errorKind,
            sessionId = sessionId
        )
    }

    override fun onMissedEventTracked(sessionId: String, missedEventType: SessionEndedMetric.MissedEventType) {
        metricsBySessionId[sessionId]?.onMissedEventTracked(missedEventType)
    }

    override fun onSessionReplaySkippedFrameTracked(sessionId: String) {
        metricsBySessionId[sessionId]?.onSessionReplaySkippedFrameTracked()
    }

}
