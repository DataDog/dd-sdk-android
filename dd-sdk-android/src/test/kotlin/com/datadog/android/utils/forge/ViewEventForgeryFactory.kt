/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.model.ViewEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class ViewEventForgeryFactory : ForgeryFactory<ViewEvent> {

    override fun getForgery(forge: Forge): ViewEvent {
        return ViewEvent(
            date = forge.aTimestamp(),
            view = ViewEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.com/[a-z0-9_/]+"),
                referrer = null,
                timeSpent = forge.aPositiveLong(),
                error = ViewEvent.Error(forge.aPositiveLong()),
                action = ViewEvent.Action(forge.aPositiveLong()),
                resource = ViewEvent.Resource(forge.aPositiveLong()),
                longTask = ViewEvent.LongTask(forge.aPositiveLong()),
                loadingType = forge.aNullable(),
                loadingTime = forge.aNullable { aPositiveLong() },
                firstContentfulPaint = forge.aNullable { aPositiveLong() },
                domInteractive = forge.aNullable { aPositiveLong() },
                domContentLoaded = forge.aNullable { aPositiveLong() },
                domComplete = forge.aNullable { aPositiveLong() },
                loadEvent = forge.aNullable { aPositiveLong() }
            ),
            usr = ViewEvent.Usr(
                id = forge.anHexadecimalString(),
                name = forge.aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+"),
                email = forge.aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
            ),
            application = ViewEvent.Application(forge.getForgery<UUID>().toString()),
            session = ViewEvent.Session(
                id = forge.getForgery<UUID>().toString(),
                type = ViewEvent.Type.USER
            ),
            dd = ViewEvent.Dd(
                documentVersion = forge.aPositiveLong(strict = true)
            )
        )
    }
}
