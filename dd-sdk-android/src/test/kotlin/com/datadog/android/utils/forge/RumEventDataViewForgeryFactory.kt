/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.RumEventData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventDataViewForgeryFactory : ForgeryFactory<RumEventData.View> {

    override fun getForgery(forge: Forge): RumEventData.View {

        return RumEventData.View(
            name = forge.aStringMatching("[a-z]+(/[a-z]+)+"),
            durationNanoSeconds = forge.aPositiveLong(),
            measures = forge.getForgery(),
            version = forge.aPositiveInt(strict = true)
        )
    }
}
