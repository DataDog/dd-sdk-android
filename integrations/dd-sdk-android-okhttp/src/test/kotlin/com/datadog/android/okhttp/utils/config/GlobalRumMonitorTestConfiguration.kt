/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils.config

import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class GlobalRumMonitorTestConfiguration(
    private val datadogSingletonTestConfiguration: DatadogSingletonTestConfiguration? = null
) : MockTestConfiguration<FakeRumMonitor>(FakeRumMonitor::class.java) {

    lateinit var mockSdkCore: InternalSdkCore

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        mockSdkCore = datadogSingletonTestConfiguration?.mockInstance ?: mock()
        GlobalRumMonitor.registerIfAbsent(mockInstance, mockSdkCore)
    }

    override fun tearDown(forge: Forge) {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
        super.tearDown(forge)
    }
}

internal interface FakeRumMonitor : RumMonitor, AdvancedNetworkRumMonitor
