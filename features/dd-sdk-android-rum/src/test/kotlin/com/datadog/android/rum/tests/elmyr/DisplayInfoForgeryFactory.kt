/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tests.elmyr

import com.datadog.android.rum.internal.domain.display.DisplayInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class DisplayInfoForgeryFactory : ForgeryFactory<DisplayInfo> {
    override fun getForgery(forge: Forge): DisplayInfo {
        return DisplayInfo(
            screenBrightness = forge.aNullable { aFloat() }
        )
    }
}
