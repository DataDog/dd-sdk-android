/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class GlobalBoundsForgeryFactory : ForgeryFactory<GlobalBounds> {
    override fun getForgery(forge: Forge): GlobalBounds {
        return GlobalBounds(
            x = forge.aLong(min = 128L, max = 65536L),
            y = forge.aLong(min = 128L, max = 65536L),
            width = forge.aLong(min = 32L, max = 65536L),
            height = forge.aLong(min = 32L, max = 65536L)
        )
    }
}
