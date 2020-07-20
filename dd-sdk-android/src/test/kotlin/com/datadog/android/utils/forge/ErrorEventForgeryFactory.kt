/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class ErrorEventForgeryFactory : ForgeryFactory<ErrorEvent> {

    override fun getForgery(forge: Forge): ErrorEvent {
        return ErrorEvent(
            date = forge.aTimestamp(),
            error = ErrorEvent.Error(
                message = forge.anAlphabeticalString(),
                source = forge.getForgery(),
                stack = forge.aNullable { aThrowable().loggableStackTrace() },
                resource = forge.aNullable {
                    ErrorEvent.Resource(
                        url = aStringMatching("https://[a-z]+.com/[a-z0-9_/]+"),
                        method = getForgery(),
                        statusCode = aLong(200, 600)
                    )
                }
            ),
            view = ErrorEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.com/[a-z0-9_/]+"),
                referrer = null
            ),
            usr = ErrorEvent.Usr(
                id = forge.anHexadecimalString(),
                name = forge.aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+"),
                email = forge.aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
            ),
            action = forge.aNullable { ErrorEvent.Action(getForgery<UUID>().toString()) },
            application = ErrorEvent.Application(forge.getForgery<UUID>().toString()),
            session = ErrorEvent.Session(
                id = forge.getForgery<UUID>().toString(),
                type = ErrorEvent.Type.USER
            ),
            dd = ErrorEvent.Dd()
        )
    }
}
