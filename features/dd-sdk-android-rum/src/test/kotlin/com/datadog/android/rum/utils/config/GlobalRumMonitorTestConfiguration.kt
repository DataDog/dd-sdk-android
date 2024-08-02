/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.config

import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("TestFunctionName")
internal abstract class InternalAdvancedRumMonitor : AdvancedRumMonitor {
    override fun _getInternal(): _RumInternalProxy? {
        return null
    }
}

internal class GlobalRumMonitorTestConfiguration :
    MockTestConfiguration<RumMonitor>(InternalAdvancedRumMonitor::class.java) {

    lateinit var mockSdkCore: InternalSdkCore

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        mockSdkCore = mock()

        (mockInstance as? InternalAdvancedRumMonitor)?.let {
            whenever(it._getInternal()).thenReturn(_RumInternalProxy(mockInstance as AdvancedRumMonitor))
        }

        GlobalRumMonitor.registerIfAbsent(mockInstance, mockSdkCore)
    }

    override fun tearDown(forge: Forge) {
        GlobalRumMonitor.clear()
        super.tearDown(forge)
    }
}
