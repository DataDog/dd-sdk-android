/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal

import android.app.Application
import android.util.Log
import com.datadog.android.insights.internal.overlay.OverlayManager
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum._RumInternalProxy

@Suppress("OPT_IN_USAGE")
internal object RumDebugWidget {

    @JvmStatic
    fun enable(application: Application, builder: RumConfiguration.Builder) {
        Log.e("RUM_WIDGET", "enabling")
        val insightsCollector = DefaultInsightsCollector()
        _RumInternalProxy.setInsightsCollector(builder, insightsCollector)
        OverlayManager(application, insightsCollector)
    }
}
