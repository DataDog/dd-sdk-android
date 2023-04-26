/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material.forge

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ShapeWireframeForgeryFactory :
    ForgeryFactory<MobileSegment.Wireframe.ShapeWireframe> {
    override fun getForgery(forge: Forge): MobileSegment.Wireframe.ShapeWireframe {
        return MobileSegment.Wireframe.ShapeWireframe(
            id = forge.aPositiveLong(),
            x = forge.aPositiveLong(),
            y = forge.aPositiveLong(),
            width = forge.aPositiveLong(strict = true),
            height = forge.aPositiveLong(strict = true),
            shapeStyle = forge.aNullable { getForgery() },
            border = forge.aNullable {
                getForgery()
            },
            clip = forge.aNullable {
                getForgery()
            }
        )
    }
}
