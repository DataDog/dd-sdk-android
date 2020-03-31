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
        forge.addFactory(LogForgeryFactory())
        forge.addFactory(BatchForgeryFactory())
        forge.addFactory(ThrowableForgeryFactory())
        forge.addFactory(NetworkInfoForgeryFactory())
        forge.addFactory(UserInfoForgeryFactory())
        forge.addFactory(WorkerParametersForgeryFactory())
        forge.addFactory(JsonObjectForgeryFactory())
        forge.addFactory(JsonPrimitiveForgeryFactory())
        forge.addFactory(JsonArrayForgeryFactory())
        forge.addFactory(SpanForgeryFactory())
        forge.addFactory(RumEventForgeryFactory())
        forge.addFactory(RumContextForgeryFactory())
        forge.addFactory(RumEventDataResourceForgeryFactory())
        forge.addFactory(RumEventDataUserActionForgeryFactory())
        forge.addFactory(RumEventDataViewForgeryFactory())
        forge.addFactory(RumEventDataViewMeasureForgeryFactory())
        forge.addFactory(RumEventDataErrorForgeryFactory())
        forge.useJvmFactories()
    }
}
