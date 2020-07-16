/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.model.ActionEvent
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.android.rum.internal.domain.model.ResourceEvent
import com.datadog.android.rum.internal.domain.model.ViewEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventForgeryFactory : ForgeryFactory<RumEvent> {

    override fun getForgery(forge: Forge): RumEvent {
        val eventData = forge.anElementFrom(
            forge.getForgery<ViewEvent>(),
            forge.getForgery<ActionEvent>(),
            forge.getForgery<ResourceEvent>(),
            forge.getForgery<ErrorEvent>()
        )

        return RumEvent(
            event = eventData,
            attributes = forge.exhaustiveAttributes()
        )
    }
}
