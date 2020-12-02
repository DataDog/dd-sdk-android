/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator
import fr.xgouchet.elmyr.jvm.useJvmFactories

/**
 * Base implementation of a [ForgeConfigurator], adding the JVM Forgery Factories (Date, …) and
 * a [ThrowableForgeryFactory].
 */
open class BaseConfigurator :
    ForgeConfigurator {
    /** @inheritDoc */
    override fun configure(forge: Forge) {
        forge.addFactory(ThrowableForgeryFactory())
        forge.useJvmFactories()
    }
}
