/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class EmbeddedContentWireframeUpdateForgeryFactory :
    ForgeryFactory<MobileSegment.WireframeUpdateMutation.EmbeddedContentWireframeUpdate> {
    override fun getForgery(
        forge: Forge
    ): MobileSegment.WireframeUpdateMutation.EmbeddedContentWireframeUpdate {
        return MobileSegment.WireframeUpdateMutation.EmbeddedContentWireframeUpdate(
            id = forge.aPositiveInt().toLong(),
            x = forge.aNullable { aLong() },
            y = forge.aNullable { aLong() },
            width = forge.aNullable { aPositiveLong(strict = true) },
            height = forge.aNullable { aPositiveLong(strict = true) },
            clip = forge.aNullable { getForgery() },
            shapeStyle = forge.aNullable { getForgery() },
            border = forge.aNullable { getForgery() },
            slotId = forge.aPositiveLong().toString(),
            isVisible = forge.aNullable { aBool() }
        )
    }
}
