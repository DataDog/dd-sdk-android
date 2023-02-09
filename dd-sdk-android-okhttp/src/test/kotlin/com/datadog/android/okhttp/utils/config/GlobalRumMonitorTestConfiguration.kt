/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils.config

import com.datadog.android.okhttp.utils.reset
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class GlobalRumMonitorTestConfiguration : TestConfiguration {

    lateinit var mockInstance: FakeRumMonitor

    override fun setUp(forge: Forge) {
        mockInstance = mock()
        GlobalRum.registerIfAbsent(mockInstance)
    }

    override fun tearDown(forge: Forge) {
        GlobalRum.reset()
    }
}

internal interface FakeRumMonitor : RumMonitor, AdvancedNetworkRumMonitor
