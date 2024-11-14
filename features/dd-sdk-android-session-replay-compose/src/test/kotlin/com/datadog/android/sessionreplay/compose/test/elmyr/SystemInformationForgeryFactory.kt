/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import android.content.res.Configuration
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class SystemInformationForgeryFactory : ForgeryFactory<SystemInformation> {
    override fun getForgery(forge: Forge): SystemInformation {
        return SystemInformation(
            screenBounds = GlobalBounds(
                x = 0,
                y = 0,
                width = forge.aPositiveLong(),
                height = forge.aPositiveLong()
            ),
            screenOrientation = forge.anElementFrom(
                intArrayOf(
                    Configuration.ORIENTATION_PORTRAIT,
                    Configuration.ORIENTATION_LANDSCAPE,
                    Configuration.ORIENTATION_UNDEFINED
                )
            ),
            screenDensity = forge.aFloat(min = 1f, max = 10f),
            themeColor = forge.aNullable { aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}") }
        )
    }
}
