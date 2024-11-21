/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PlaceholderWireframeForgeryFactory :
    ForgeryFactory<MobileSegment.Wireframe.PlaceholderWireframe> {
    override fun getForgery(forge: Forge): MobileSegment.Wireframe.PlaceholderWireframe {
        return MobileSegment.Wireframe.PlaceholderWireframe(
            id = forge.aPositiveInt().toLong(),
            x = forge.aPositiveLong(),
            y = forge.aPositiveLong(),
            width = forge.aPositiveLong(strict = true),
            height = forge.aPositiveLong(strict = true),

            label = forge.aNullable { aString() },
            clip = forge.aNullable {
                getForgery()
            }
        )
    }
}
