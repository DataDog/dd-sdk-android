/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator
import fr.xgouchet.elmyr.jvm.useJvmFactories

internal class Configurator :
    ForgeConfigurator {
    override fun configure(forge: Forge) {

        // Datadog Core
        forge.addFactory(ConfigurationCoreForgeryFactory())
        forge.addFactory(ConfigurationFeatureForgeryFactory())
        forge.addFactory(ConfigurationLogForgeryFactory())
        forge.addFactory(ConfigurationCrashReportForgeryFactory())
        forge.addFactory(ConfigurationTracingForgeryFactory())
        forge.addFactory(ConfigurationRumForgeryFactory())
        forge.addFactory(CredentialsForgeryFactory())
        forge.addFactory(NetworkInfoForgeryFactory())
        forge.addFactory(UserInfoForgeryFactory())

        // IO
        forge.addFactory(BatchForgeryFactory())
        forge.addFactory(WorkerParametersForgeryFactory())

        // LOG
        forge.addFactory(LogForgeryFactory())

        // APM
        forge.addFactory(SpanForgeryFactory())

        // RUM
        forge.addFactory(ActionEventForgeryFactory())
        forge.addFactory(RumEventForgeryFactory())
        forge.addFactory(RumContextForgeryFactory())
        forge.addFactory(ResourceEventForgeryFactory())
        forge.addFactory(ResourceTimingForgeryFactory())
        forge.addFactory(ErrorEventForgeryFactory())
        forge.addFactory(ViewEventForgeryFactory())
        forge.addFactory(MotionEventForgeryFactory())
        forge.addFactory(RumEventMapperFactory())

        // MISC
        forge.addFactory(BigIntegerFactory())
        forge.addFactory(JsonArrayForgeryFactory())
        forge.addFactory(JsonObjectForgeryFactory())
        forge.addFactory(JsonPrimitiveForgeryFactory())
        forge.addFactory(ThrowableForgeryFactory())
        forge.useJvmFactories()
    }
}
