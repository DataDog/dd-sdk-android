/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.RumSessionType
import java.util.concurrent.ScheduledExecutorService

internal class DefaultTimeseriesCollectorFactory(
    private val internalLogger: InternalLogger,
    private val collectInBackground: Boolean,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val pipelinesProvider: (
        applicationId: String,
        sessionId: String,
        sessionType: RumSessionType
    ) -> List<Pipeline<*>>
) : TimeseriesCollector.Factory {

    override fun create(applicationId: String, sessionId: String, sessionType: RumSessionType) =
        DefaultTimeseriesCollector(
            internalLogger = internalLogger,
            collectInBackground = collectInBackground,
            scheduledExecutorService = scheduledExecutorService,
            pipelines = pipelinesProvider(applicationId, sessionId, sessionType)
        )
}
