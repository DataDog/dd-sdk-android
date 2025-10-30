/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("OPT_IN_USAGE")

package com.datadog.android.insights

import android.app.Application
import com.datadog.android.BuildConfig
import com.datadog.android.insights.internal.DefaultInsightsCollector
import com.datadog.android.insights.internal.overlay.OverlayManager
import com.datadog.android.rum.RumConfiguration

fun RumConfiguration.Builder.enableDebugWidget(application: Application, enabled: Boolean = BuildConfig.DEBUG) = apply {
    if (!enabled) return@apply

    val insightsCollector = DefaultInsightsCollector()
    setInsightsCollector(insightsCollector)
    OverlayManager.start(application, insightsCollector)
}
