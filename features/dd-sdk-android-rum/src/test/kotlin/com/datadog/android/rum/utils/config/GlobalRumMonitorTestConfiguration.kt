/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.config

import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock

internal class GlobalRumMonitorTestConfiguration :
    MockTestConfiguration<RumMonitor>(AdvancedRumMonitor::class.java) {

    lateinit var mockSdkCore: InternalSdkCore

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        mockSdkCore = mock()
        GlobalRumMonitor.registerIfAbsent(mockInstance, mockSdkCore)
    }

    override fun tearDown(forge: Forge) {
        GlobalRumMonitor.clear()
        super.tearDown(forge)
    }
}
