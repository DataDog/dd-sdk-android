/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.tests.elmyr.useCoreFactories
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.useJvmFactories

class SessionReplayComposeForgeConfigurator : BaseConfigurator() {
    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.useJvmFactories()
        forge.useCoreFactories()

        // Session Replay
        forge.addFactory(ShapeStyleForgeryFactory())
        forge.addFactory(ShapeBorderForgeryFactory())
        forge.addFactory(WireframeClipForgeryFactory())
        forge.addFactory(ShapeWireframeForgeryFactory())
        forge.addFactory(TextWireframeForgeryFactory())
        forge.addFactory(ImageWireframeForgeryFactory())
        forge.addFactory(PlaceholderWireframeForgeryFactory())
        forge.addFactory(WireframeForgeryFactory())

        // Compose Specifics
        forge.addFactory(UIContextForgeryFactory())
        forge.addFactory(ComposeWireframeForgeryFactory())
        forge.addFactory(TextLayoutInfoForgeryFactory())
        forge.addFactory(MappingContextForgeryFactory())
        forge.addFactory(SystemInformationForgeryFactory())
        forge.addFactory(GlobalBoundsForgeryFactory())
    }
}
