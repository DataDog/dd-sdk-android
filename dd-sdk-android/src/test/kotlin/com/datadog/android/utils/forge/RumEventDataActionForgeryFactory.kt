/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEventData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class RumEventDataActionForgeryFactory :
    ForgeryFactory<RumEventData.Action> {
    override fun getForgery(forge: Forge): RumEventData.Action {
        return RumEventData.Action(
            type = forge.anAlphabeticalString(),
            id = forge.getForgery<UUID>().toString(),
            durationNanoSeconds = forge.aPositiveLong()
        )
    }
}
