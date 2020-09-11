/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.fresco.utils

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator
import fr.xgouchet.elmyr.jvm.useJvmFactories

internal class Configurator :
    ForgeConfigurator {
    override fun configure(forge: Forge) {
        forge.addFactory(ThrowableForgeryFactory())
        forge.useJvmFactories()
    }
}
