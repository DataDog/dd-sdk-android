/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material.forge

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class WireframeClipForgeryFactory :
    ForgeryFactory<MobileSegment.WireframeClip> {
    override fun getForgery(forge: Forge): MobileSegment.WireframeClip {
        return MobileSegment.WireframeClip(
            top = forge.aNullable { aLong(min = 0, max = 100) },
            bottom = forge.aNullable { aLong(min = 0, max = 100) },
            left = forge.aNullable { aLong(min = 0, max = 100) },
            right = forge.aNullable { aLong(min = 0, max = 100) }
        )
    }
}
