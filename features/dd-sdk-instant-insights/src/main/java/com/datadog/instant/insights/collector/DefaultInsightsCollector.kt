/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights.collector

import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector

internal class DefaultInsightsCollector : InsightsCollector {

    override fun onAction() {
    }

    override fun onLongTask(duration: Long) {
    }

    override fun onSlowFrame(duration: Long) {
    }

    override fun onNetworkRequest(uri: String) {
    }
}
