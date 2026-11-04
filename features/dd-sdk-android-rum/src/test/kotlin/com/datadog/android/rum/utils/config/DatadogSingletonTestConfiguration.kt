/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.config

import com.datadog.android.Datadog
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.utils.clearInstances
import com.datadog.android.utils.registerInstance
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge

internal class DatadogSingletonTestConfiguration :
    MockTestConfiguration<InternalSdkCore>(InternalSdkCore::class.java) {

    override fun setUp(forge: Forge) {
        super.setUp(forge)

        Datadog.registerInstance(null, mockInstance)
    }

    override fun tearDown(forge: Forge) {
        clearRegistry()
        super.tearDown(forge)
    }

    fun clearRegistry() {
        Datadog.clearInstances()
    }
}
