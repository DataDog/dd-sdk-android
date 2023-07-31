/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ImageWireframeForgeryFactory :
    ForgeryFactory<MobileSegment.Wireframe.ImageWireframe> {
    override fun getForgery(forge: Forge): MobileSegment.Wireframe.ImageWireframe {
        return MobileSegment.Wireframe.ImageWireframe(
            id = forge.aPositiveLong(),
            x = forge.aPositiveLong(),
            y = forge.aPositiveLong(),
            width = forge.aPositiveLong(strict = true),
            height = forge.aPositiveLong(strict = true),
            shapeStyle = forge.aNullable {
                MobileSegment.ShapeStyle(
                    forge.aStringMatching("#[0-9A-F]{6}FF"),
                    opacity = forge.aFloat(min = 0f, max = 1f),
                    cornerRadius = forge.aPositiveLong()
                )
            },
            base64 = forge.aNullable { aString() },
            clip = forge.aNullable {
                getForgery()
            }
        )
    }
}
