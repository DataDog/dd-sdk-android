/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.forge

import com.datadog.android.internal.tests.elmyr.ProfilerStopEventTTIDFactory
import com.datadog.android.internal.tests.elmyr.TTIDRumContextFactory
import com.datadog.android.tests.elmyr.useCoreFactories
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge

class Configurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.useCoreFactories()
        forge.addFactory(ProfilingConfigurationForgeryFactory())
        forge.addFactory(PerfettoResultFactory())
        forge.addFactory(ProfilerStopEventTTIDFactory())
        forge.addFactory(TTIDRumContextFactory())
    }
}
