/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge

internal class Configurator :
    BaseConfigurator() {
    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.addFactory(FeatureConfigForgeryFactory())
        forge.addFactory(LogForgeryFactory())
        forge.addFactory(BatchForgeryFactory())
        forge.addFactory(NetworkInfoForgeryFactory())
        forge.addFactory(UserInfoForgeryFactory())
        forge.addFactory(WorkerParametersForgeryFactory())
        forge.addFactory(JsonObjectForgeryFactory())
        forge.addFactory(JsonPrimitiveForgeryFactory())
        forge.addFactory(JsonArrayForgeryFactory())
        forge.addFactory(SpanForgeryFactory())
        forge.addFactory(RumEventForgeryFactory())
        forge.addFactory(RumContextForgeryFactory())
        forge.addFactory(ResourceTimingForgeryFactory())
        forge.addFactory(ViewEventForgeryFactory())
        forge.addFactory(ActionEventForgeryFactory())
        forge.addFactory(ResourceEventForgeryFactory())
        forge.addFactory(ErrorEventForgeryFactory())
        forge.addFactory(MotionEventForgeryFactory())
        forge.addFactory(BigIntegerFactory())
    }
}
