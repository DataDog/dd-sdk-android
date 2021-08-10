/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.model.LongTaskEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class LongTaskEventForgeryFactory :
    ForgeryFactory<LongTaskEvent> {
    override fun getForgery(forge: Forge): LongTaskEvent {
        return LongTaskEvent(
            date = forge.aTimestamp(),
            longTask = LongTaskEvent.LongTask(
                duration = forge.aPositiveLong()
            ),
            view = LongTaskEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.com/[a-z0-9_/]+"),
                referrer = null
            ),
            usr = LongTaskEvent.Usr(
                id = forge.anHexadecimalString(),
                name = forge.aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+"),
                email = forge.aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
            ),
            action = forge.aNullable { LongTaskEvent.Action(getForgery<UUID>().toString()) },
            application = LongTaskEvent.Application(forge.getForgery<UUID>().toString()),
            session = LongTaskEvent.LongTaskEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = LongTaskEvent.Type.USER
            ),
            dd = LongTaskEvent.Dd(),
            service = forge.aNullable { forge.aString() },
            connectivity = LongTaskEvent.Connectivity(
                status = forge.aValueFrom(LongTaskEvent.Status::class.java),
                interfaces = forge.aSubListOf(LongTaskEvent.Interface.values().asList()),
                cellular = LongTaskEvent.Cellular(
                    technology = forge.aNullable { forge.aString() },
                    carrierName = forge.aNullable { forge.aString() }
                )
            )
        )
    }
}
