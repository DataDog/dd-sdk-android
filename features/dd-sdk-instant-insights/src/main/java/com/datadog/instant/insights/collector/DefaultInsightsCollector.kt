/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights.collector

import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector

class DefaultInsightsCollector : InsightsCollector {

    override fun onSlowFrame(startedTimestamp: Long, durationNs: Long) {
        TODO("Not yet implemented")
    }

    override fun onAction() {
        TODO("Not yet implemented")
    }

    override fun onLongTask(startedTimestamp: Long, durationNs: Long) {
        TODO("Not yet implemented")
    }

    override fun onNetworkRequest(startedTimestamp: Long, durationNs: Long) {
        TODO("Not yet implemented")
    }

    companion object {
        val DEFAULT = DefaultInsightsCollector()
    }
}
