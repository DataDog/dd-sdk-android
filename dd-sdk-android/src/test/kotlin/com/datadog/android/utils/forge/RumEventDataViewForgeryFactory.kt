/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEventData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventDataViewForgeryFactory : ForgeryFactory<RumEventData.View> {

    override fun getForgery(forge: Forge): RumEventData.View {

        return RumEventData.View(
            name = forge.aStringMatching("[a-z]+(/[a-z]+)+"),
            durationNanoSeconds = forge.aPositiveLong(),
            errorCount = forge.anInt(0, forge.aSmallInt()),
            resourceCount = forge.anInt(0, forge.aSmallInt()),
            actionCount = forge.anInt(0, forge.aSmallInt()),
            version = forge.aPositiveInt(strict = true)
        )
    }
}
