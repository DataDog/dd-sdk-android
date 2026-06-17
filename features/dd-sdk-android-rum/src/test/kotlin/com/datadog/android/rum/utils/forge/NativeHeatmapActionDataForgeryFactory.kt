/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.heatmaps.NativeHeatmapActionData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class NativeHeatmapActionDataForgeryFactory : ForgeryFactory<NativeHeatmapActionData> {

    override fun getForgery(forge: Forge): NativeHeatmapActionData {
        return NativeHeatmapActionData(
            viewKey = forge.aLong(),
            positionX = forge.aLong(),
            positionY = forge.aLong(),
            targetWidth = forge.aNullable { aPositiveLong() },
            targetHeight = forge.aNullable { aPositiveLong() }
        )
    }
}
