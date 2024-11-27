/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.internal.metric.interactiontonextview.InternalInteractionContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class InternalInteractionContextFactory : ForgeryFactory<InternalInteractionContext> {
    override fun getForgery(forge: Forge): InternalInteractionContext {
        return InternalInteractionContext(
            viewId = forge.getForgery<UUID>().toString(),
            actionType = forge.aValueFrom(RumActionType::class.java),
            eventCreatedAtNanos = forge.aPositiveLong()
        )
    }
}
