/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class WireframeForgeryFactory : ForgeryFactory<MobileSegment.Wireframe> {
    override fun getForgery(forge: Forge): MobileSegment.Wireframe {
        return when (forge.anInt(min = 0, max = 4)) {
            0 -> forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            1 -> forge.getForgery<MobileSegment.Wireframe.ImageWireframe>()
            2 -> forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>()
            else -> forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
        }
    }
}
