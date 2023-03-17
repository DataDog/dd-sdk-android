/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import com.datadog.android.Datadog
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class DatadogSingletonTestConfiguration :
    MockTestConfiguration<SdkCore>(SdkCore::class.java) {

    override fun setUp(forge: Forge) {
        super.setUp(forge)

        val registry = registryField.get(null)
        registryRegisterMethod.invoke(registry, null, mockInstance)
    }

    override fun tearDown(forge: Forge) {
        val registry = registryField.get(null)
        registryClearMethod.invoke(registry)
        super.tearDown(forge)
    }

    companion object {
        private val registryField = Datadog::class.java.getDeclaredField("registry").apply {
            isAccessible = true
        }
        private val registryRegisterMethod = registryField.type.getMethod(
            "register",
            String::class.java,
            SdkCore::class.java
        )
        private val registryClearMethod = registryField.type.getMethod("clear")
    }
}
