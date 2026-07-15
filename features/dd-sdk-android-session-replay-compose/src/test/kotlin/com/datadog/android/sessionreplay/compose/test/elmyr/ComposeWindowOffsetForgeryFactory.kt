/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ComposeWindowOffsetForgeryFactory : ForgeryFactory<ComposeWindowOffset> {
    override fun getForgery(forge: Forge): ComposeWindowOffset {
        return ComposeWindowOffset(
            xPx = forge.anInt(min = 0, max = 65536),
            yPx = forge.anInt(min = 0, max = 65536),
            xDp = forge.aLong(min = 0L, max = 65536L),
            yDp = forge.aLong(min = 0L, max = 65536L)
        )
    }
}
