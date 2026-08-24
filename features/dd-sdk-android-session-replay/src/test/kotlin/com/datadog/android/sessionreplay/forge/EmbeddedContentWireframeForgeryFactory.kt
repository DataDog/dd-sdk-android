/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class EmbeddedContentWireframeForgeryFactory :
    ForgeryFactory<MobileSegment.Wireframe.EmbeddedContentWireframe> {
    override fun getForgery(forge: Forge): MobileSegment.Wireframe.EmbeddedContentWireframe {
        return MobileSegment.Wireframe.EmbeddedContentWireframe(
            id = forge.aPositiveInt().toLong(),
            x = forge.aPositiveLong(),
            y = forge.aPositiveLong(),
            width = forge.aPositiveLong(strict = true),
            height = forge.aPositiveLong(strict = true),
            clip = forge.aNullable { getForgery() },
            shapeStyle = forge.aNullable { getForgery() },
            border = forge.aNullable { getForgery() },
            slotId = forge.aPositiveLong().toString()
        )
    }
}
