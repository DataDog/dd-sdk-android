/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.domain.scope.HeatmapActionData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class HeatmapActionDataForgeryFactory : ForgeryFactory<HeatmapActionData> {

    override fun getForgery(forge: Forge): HeatmapActionData {
        return HeatmapActionData(
            viewKey = forge.aLong(),
            positionX = forge.aLong(),
            positionY = forge.aLong(),
            targetWidth = forge.aNullable { aPositiveLong() },
            targetHeight = forge.aNullable { aPositiveLong() }
        )
    }
}
