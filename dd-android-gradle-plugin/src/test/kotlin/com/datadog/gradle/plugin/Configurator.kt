/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator

internal class Configurator : ForgeConfigurator {

    override fun configure(forge: Forge) {
        forge.addFactory(IdentifierForgeryFactory())
        forge.addFactory(ConfigurationForgeryFactory())
    }
}
