/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.config

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge

internal class GlobalRumMonitorTestConfiguration :
    MockTestConfiguration<RumMonitor>(AdvancedRumMonitor::class.java) {

    lateinit var mockSdkCore: InternalSdkCore

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        mockSdkCore = mock()
        GlobalRum.registerIfAbsent(mockSdkCore, mockInstance)
    }

    override fun tearDown(forge: Forge) {
        GlobalRum.clear()
        super.tearDown(forge)
    }
}
