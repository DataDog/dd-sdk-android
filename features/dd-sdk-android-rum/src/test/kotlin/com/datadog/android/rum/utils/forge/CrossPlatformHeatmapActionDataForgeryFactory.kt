/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.heatmaps.CrossPlatformHeatmapActionData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class CrossPlatformHeatmapActionDataForgeryFactory : ForgeryFactory<CrossPlatformHeatmapActionData> {

    override fun getForgery(forge: Forge): CrossPlatformHeatmapActionData {
        return CrossPlatformHeatmapActionData(
            elementPath = forge.aList(size = forge.anInt(min = 1, max = 7)) { anAlphabeticalString() },
            viewUrl = forge.aString(),
            positionX = forge.aPositiveLong(),
            positionY = forge.aPositiveLong(),
            targetWidth = forge.aNullable { aPositiveLong() },
            targetHeight = forge.aNullable { aPositiveLong() }
        )
    }
}
