/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge

internal class GlobalRumMonitorTestConfiguration :
    MockTestConfiguration<AdvancedRumMonitor>(AdvancedRumMonitor::class.java) {

    lateinit var context: RumContext

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        GlobalRum.monitor = mockInstance
        GlobalRum.isRegistered.set(true)
        context = forge.getForgery()
        GlobalRum.updateRumContext(context)
    }

    override fun tearDown(forge: Forge) {
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
        GlobalRum.updateRumContext(RumContext())
        super.tearDown(forge)
    }
}
