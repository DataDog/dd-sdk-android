/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ShapeStyleForgeryFactory :
    ForgeryFactory<MobileSegment.ShapeStyle> {
    override fun getForgery(forge: Forge): MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = forge.aNullable {
                forge.aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}")
            },
            opacity = forge.aFloat(min = 0f, max = 1f),
            cornerRadius = forge.aPositiveLong()
        )
    }
}
