/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge

internal class SessionScopeTestConfiguration :
    MockTestConfiguration<DatadogRumMonitor>(DatadogRumMonitor::class.java) {

    lateinit var mockApplicationScope: RumApplicationScope
    lateinit var mockSessionScope: RumSessionScope
    lateinit var fakeRumContext: RumContext

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        fakeRumContext = forge.getForgery()
        mockApplicationScope = mock()
        mockSessionScope = mock()
        GlobalRum.monitor = mockInstance
        whenever(mockInstance.rootScope).thenReturn(mockApplicationScope)
        whenever(mockApplicationScope.activeSession).thenReturn(mockSessionScope)
        whenever(mockSessionScope.getRumContext()).thenReturn(fakeRumContext)
    }

    override fun tearDown(forge: Forge) {
        GlobalRum.monitor = NoOpRumMonitor()
    }
}
