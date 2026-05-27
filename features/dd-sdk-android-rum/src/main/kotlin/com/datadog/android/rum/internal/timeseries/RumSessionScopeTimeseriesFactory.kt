/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.RumSessionType
import java.util.concurrent.ScheduledExecutorService

internal class RumSessionScopeTimeseriesFactory(
    private val internalLogger: InternalLogger,
    private val collectInBackground: Boolean,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val pipelinesProvider: (
        sessionId: String,
        applicationId: String,
        sessionType: RumSessionType
    ) -> List<Pipeline<*>>
) : TimeseriesFactory {

    override fun create(sessionId: String, applicationId: String, sessionType: RumSessionType) =
        RumSessionScopeTimeseries(
            internalLogger = internalLogger,
            collectInBackground = collectInBackground,
            scheduledExecutorService = scheduledExecutorService,
            pipelines = pipelinesProvider(sessionId, applicationId, sessionType)
        )
}
