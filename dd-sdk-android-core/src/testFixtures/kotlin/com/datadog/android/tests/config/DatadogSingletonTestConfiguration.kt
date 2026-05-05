/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.config

import com.datadog.android.Datadog
import com.datadog.android.core.InternalSdkCore
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge

class DatadogSingletonTestConfiguration :
    MockTestConfiguration<InternalSdkCore>(InternalSdkCore::class.java) {

    override fun setUp(forge: Forge) {
        super.setUp(forge)

        Datadog.registry.register(null, mockInstance)
    }

    override fun tearDown(forge: Forge) {
        clearRegistry()
        super.tearDown(forge)
    }

    fun clearRegistry() {
        Datadog.registry.clear()
    }
}
