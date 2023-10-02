/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.test.forge.DatadogContextForgeryFactory
import com.datadog.android.test.forge.DeviceInfoForgeryFactory
import com.datadog.android.test.forge.NetworkInfoForgeryFactory
import com.datadog.android.test.forge.ProcessInfoForgeryFactory
import com.datadog.android.test.forge.TimeInfoForgeryFactory
import com.datadog.android.test.forge.UserInfoForgeryFactory
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.useJvmFactories

internal class Configurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.useJvmFactories()

        // Core
        forge.addFactory(DatadogContextForgeryFactory())
        forge.addFactory(TimeInfoForgeryFactory())
        forge.addFactory(ProcessInfoForgeryFactory())
        forge.addFactory(NetworkInfoForgeryFactory())
        forge.addFactory(UserInfoForgeryFactory())
        forge.addFactory(DeviceInfoForgeryFactory())

        // RUM
        forge.addFactory(ConfigurationRumForgeryFactory())
        forge.addFactory(RumConfigurationForgeryFactory())
        forge.addFactory(ActionEventForgeryFactory())
        forge.addFactory(ErrorEventForgeryFactory())
        forge.addFactory(LongTaskEventForgeryFactory())
        forge.addFactory(MotionEventForgeryFactory())
        forge.addFactory(RumEventMapperFactory())
        forge.addFactory(RumContextForgeryFactory())
        forge.addFactory(ResourceEventForgeryFactory())
        forge.addFactory(ResourceTimingForgeryFactory())
        forge.addFactory(ViewEventForgeryFactory())
        forge.addFactory(VitalInfoForgeryFactory())
        forge.addFactory(TelemetryCoreConfigurationForgeryFactory())

        // Telemetry
        forge.addFactory(TelemetryDebugEventForgeryFactory())
        forge.addFactory(TelemetryErrorEventForgeryFactory())
        forge.addFactory(TelemetryConfigurationEventForgeryFactory())
    }
}
