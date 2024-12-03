/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.metric.interactiontonextview.PreviousViewLastInteractionContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PreviousViewLastActionContextFactory : ForgeryFactory<PreviousViewLastInteractionContext> {
    override fun getForgery(forge: Forge): PreviousViewLastInteractionContext {
        return PreviousViewLastInteractionContext(
            actionType = forge.aValueFrom(RumActionType::class.java),
            eventCreatedAtNanos = forge.aPositiveLong(),
            currentViewCreationTimestamp = forge.aNullable { forge.aPositiveLong() }
        )
    }
}
