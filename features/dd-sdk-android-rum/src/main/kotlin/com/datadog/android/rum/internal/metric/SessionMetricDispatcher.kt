/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.model.ViewEvent
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface to dispatch the session metric.
 */
@NoOpImplementation
internal interface SessionMetricDispatcher {

    /**
     * Starts a session metric with given session id and start reason of this session.
     */
    fun startMetric(sessionId: String, startReason: RumSessionScope.StartReason)

    /**
     * Ends the session metric with given session id.
     */
    fun endMetric(sessionId: String)

    /**
     * Called when the session is stopped.
     */
    fun onSessionStopped(sessionId: String)

    /**
     * Called when a view is tracked by this session metric.
     */
    fun onViewTracked(sessionId: String, viewEvent: ViewEvent)

    /**
     * Called when a sdk error is tracked by this session metric.
     */
    fun onSdkErrorTracked(sessionId: String, errorKind: String?)
}
