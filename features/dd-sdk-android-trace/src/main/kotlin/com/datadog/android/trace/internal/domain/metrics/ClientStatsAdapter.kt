/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.trace.common.metrics.MetricsAggregator
import com.datadog.trace.core.CoreSpan
import com.datadog.trace.core.DDSpan
import java.util.concurrent.Future

internal class ClientStatsAdapter(
    private val statsConcentrator: StatsConcentrator
) : MetricsAggregator {
    override fun start() {
        // NO - OP
    }

    override fun report(): Boolean {
        // NO - OP
        return false
    }

    override fun forceReport(): Future<Boolean?>? {
        // NO - OP
        return null
    }

    override fun publish(trace: List<CoreSpan<*>>?): Boolean {
        if (trace == null) return false

        statsConcentrator.record(trace.filterIsInstance<DDSpan>())

        return false
    }

    override fun close() {
        // NO - OP
    }
}
