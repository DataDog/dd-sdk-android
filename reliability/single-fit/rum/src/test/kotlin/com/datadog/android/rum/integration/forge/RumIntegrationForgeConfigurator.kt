/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.forge

import com.datadog.android.test.forge.DatadogContextForgeryFactory
import com.datadog.android.test.forge.DeviceInfoForgeryFactory
import com.datadog.android.test.forge.NetworkInfoForgeryFactory
import com.datadog.android.test.forge.ProcessInfoForgeryFactory
import com.datadog.android.test.forge.TimeInfoForgeryFactory
import com.datadog.android.test.forge.UserInfoForgeryFactory
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge

class RumIntegrationForgeConfigurator : BaseConfigurator() {
    override fun configure(forge: Forge) {
        super.configure(forge)

        forge.addFactory(DatadogContextForgeryFactory())
        forge.addFactory(DeviceInfoForgeryFactory())
        forge.addFactory(NetworkInfoForgeryFactory())
        forge.addFactory(ProcessInfoForgeryFactory())
        forge.addFactory(TimeInfoForgeryFactory())
        forge.addFactory(UserInfoForgeryFactory())
    }
}
