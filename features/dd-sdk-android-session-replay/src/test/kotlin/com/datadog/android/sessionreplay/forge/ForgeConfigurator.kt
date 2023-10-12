/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.useJvmFactories

internal class ForgeConfigurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)

        // Core
        forge.addFactory(DatadogContextForgeryFactory())

        // Session Replay
        forge.addFactory(NodeForgeryFactory())
        forge.addFactory(SessionReplayRumContextForgeryFactory())
        forge.addFactory(ShapeWireframeForgeryFactory())
        forge.addFactory(TextWireframeForgeryFactory())
        forge.addFactory(WireframeForgeryFactory())
        forge.addFactory(TextWireframeMutationForgeryFactory())
        forge.addFactory(ShapeWireframeMutationForgeryFactory())
        forge.addFactory(WireframeUpdateMutationForgeryFactory())
        forge.addFactory(MutationDataForgeryFactory())
        forge.addFactory(FocusRecordForgeryFactory())
        forge.addFactory(FullSnapshotRecordForgeryFactory())
        forge.addFactory(IncrementalSnapshotRecordForgeryFactory())
        forge.addFactory(MetaRecordForgeryFactory())
        forge.addFactory(ViewEndRecordForgeryFactory())
        forge.addFactory(EnrichedRecordForgeryFactory())
        forge.addFactory(WireframeClipForgeryFactory())
        forge.addFactory(PointerInteractionDataForgeryFactory())
        forge.addFactory(ShapeStyleForgeryFactory())
        forge.addFactory(ShapeBorderForgeryFactory())
        forge.addFactory(MobileSegmentForgeryFactory())
        forge.addFactory(GlobalBoundsForgeryFactory())
        forge.addFactory(SystemInformationForgeryFactory())
        forge.addFactory(MappingContextForgeryFactory())
        forge.addFactory(SessionReplayConfigurationForgeryFactory())
        forge.addFactory(RumContextDataForgeryFactory())
        forge.addFactory(ImageWireframeForgeryFactory())
        forge.addFactory(PlaceholderWireframeForgeryFactory())
        forge.addFactory(SnapshotRecordedDataQueueItemForgeryFactory())
        forge.addFactory(TouchEventRecordedDataQueueItemForgeryFactory())
        forge.addFactory(WireframeBoundsForgeryFactory())

        forge.useJvmFactories()
    }
}
