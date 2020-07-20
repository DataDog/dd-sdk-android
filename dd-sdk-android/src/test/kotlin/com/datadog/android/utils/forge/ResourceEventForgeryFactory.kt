/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.model.ResourceEvent
import com.datadog.android.rum.internal.domain.scope.connect
import com.datadog.android.rum.internal.domain.scope.dns
import com.datadog.android.rum.internal.domain.scope.download
import com.datadog.android.rum.internal.domain.scope.firstByte
import com.datadog.android.rum.internal.domain.scope.ssl
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class ResourceEventForgeryFactory :
    ForgeryFactory<ResourceEvent> {
    override fun getForgery(forge: Forge): ResourceEvent {
        val timing = forge.aNullable<ResourceTiming>()
        return ResourceEvent(
            date = forge.aTimestamp(),
            resource = ResourceEvent.Resource(
                type = forge.getForgery(),
                url = forge.aStringMatching("https://[a-z]+.com/[a-z0-9_/]+"),
                duration = forge.aPositiveLong(),
                method = forge.aNullable(),
                statusCode = forge.aNullable { aLong(200, 600) },
                size = forge.aNullable { aPositiveLong() },
                dns = timing?.dns(),
                connect = timing?.connect(),
                ssl = timing?.ssl(),
                firstByte = timing?.firstByte(),
                download = timing?.download(),
                redirect = null
            ),
            view = ResourceEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.com/[a-z0-9_/]+"),
                referrer = null
            ),
            usr = ResourceEvent.Usr(
                id = forge.anHexadecimalString(),
                name = forge.aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+"),
                email = forge.aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
            ),
            action = forge.aNullable { ResourceEvent.Action(getForgery<UUID>().toString()) },
            application = ResourceEvent.Application(forge.getForgery<UUID>().toString()),
            session = ResourceEvent.Session(
                id = forge.getForgery<UUID>().toString(),
                type = ResourceEvent.Type.USER
            ),
            dd = ResourceEvent.Dd()
        )
    }
}
