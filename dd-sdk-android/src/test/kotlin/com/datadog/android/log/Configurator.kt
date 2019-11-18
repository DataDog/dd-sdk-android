/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogForgeryFactory
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator

class Configurator :
    ForgeConfigurator {
    override fun configure(forge: Forge) {
        forge.addFactory(Log::class.java, LogForgeryFactory())
    }
}
