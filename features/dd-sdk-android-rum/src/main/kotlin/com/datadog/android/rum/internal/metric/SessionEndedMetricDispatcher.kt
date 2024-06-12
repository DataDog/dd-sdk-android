/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.model.ViewEvent
import java.util.concurrent.ConcurrentHashMap

internal class SessionEndedMetricDispatcher(private val internalLogger: InternalLogger) :
    SessionMetricDispatcher {

    private val metricsBySessionId = ConcurrentHashMap<String, SessionEndedMetric>()

    override fun startMetric(
        sessionId: String,
        startReason: RumSessionScope.StartReason
    ) {
        metricsBySessionId[sessionId] = SessionEndedMetric(sessionId, startReason)
    }

    override fun endMetric(sessionId: String) {
        // the argument is always non - null, so we can suppress the warning
        @Suppress("UnsafeThirdPartyFunctionCall")
        val metric = metricsBySessionId.remove(sessionId)
        metric?.let {
            internalLogger.logMetric(
                messageBuilder = { SessionEndedMetric.RUM_SESSION_ENDED_METRIC_NAME },
                additionalProperties = it.toMetricAttributes()
            )
        }
    }

    override fun onSessionStopped(sessionId: String) {
        metricsBySessionId[sessionId]?.onSessionStopped()
    }

    override fun onViewTracked(sessionId: String, viewEvent: ViewEvent) {
        val result = metricsBySessionId[sessionId]?.onViewTracked(viewEvent) ?: false
        if (result.not()) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { buildViewTrackError(sessionId, viewEvent) }
            )
        }
    }

    override fun onSdkErrorTracked(sessionId: String, errorKind: String?) {
        errorKind?.let {
            metricsBySessionId[sessionId]?.onErrorTracked(it)
        }
    }

    private fun buildViewTrackError(sessionId: String, viewEvent: ViewEvent): String {
        val viewType = when (viewEvent.view.url) {
            RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL -> "AppLaunch"
            RumViewManagerScope.RUM_BACKGROUND_VIEW_URL -> "Background"
            else -> "Custom"
        }
        return "Failed to track $viewType view in session with different UUID $sessionId"
    }
}
