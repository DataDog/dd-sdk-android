/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ShapeBorderForgeryFactory :
    ForgeryFactory<MobileSegment.ShapeBorder> {
    override fun getForgery(forge: Forge): MobileSegment.ShapeBorder {
        return MobileSegment.ShapeBorder(
            color = forge.aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}"),
            width = forge.aPositiveLong(strict = true)
        )
    }
}
