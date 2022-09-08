/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ShapeWireframeMutationForgeryFactory :
    ForgeryFactory<MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate> {
    override fun getForgery(forge: Forge):
        MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate {
        return MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
            id = forge.aPositiveLong(),
            x = forge.aNullable { aPositiveLong() },
            y = forge.aNullable { aPositiveLong() },
            width = forge.aNullable { aPositiveLong(strict = true) },
            height = forge.aNullable { aPositiveLong(strict = true) },
            shapeStyle = forge.aNullable {
                MobileSegment.ShapeStyle(
                    backgroundColor = forge.aNullable {
                        forge.aStringMatching("#[0-9A-F]{6}FF")
                    },
                    opacity = forge.aFloat(min = 0f, max = 1f),
                    cornerRadius = forge.aPositiveLong()
                )
            },
            border = forge.aNullable {
                MobileSegment.ShapeBorder(
                    forge.aStringMatching("#[0-9A-F]{6}FF"),
                    forge.aPositiveLong(strict = true)
                )
            }
        )
    }
}
