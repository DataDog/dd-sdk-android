/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.model.ActionEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class ActionEventForgeryFactory :
    ForgeryFactory<ActionEvent> {
    override fun getForgery(forge: Forge): ActionEvent {
        return ActionEvent(
            date = forge.aTimestamp(),
            action = ActionEvent.Action(
                type = forge.getForgery(),
                id = forge.getForgery<UUID>().toString(),
                target = forge.aNullable { ActionEvent.Target(anAlphabeticalString()) },
                error = forge.aNullable { ActionEvent.Error(aLong(0, 512)) },
                resource = forge.aNullable { ActionEvent.Resource(aLong(0, 512)) },
                longTask = null,
                loadingTime = forge.aNullable { aPositiveLong(strict = true) }
            ),
            view = ActionEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.com/[a-z0-9_/]+"),
                referrer = null
            ),
            usr = ActionEvent.Usr(
                id = forge.anHexadecimalString(),
                name = forge.aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+"),
                email = forge.aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
            ),
            application = ActionEvent.Application(forge.getForgery<UUID>().toString()),
            session = ActionEvent.Session(
                id = forge.getForgery<UUID>().toString(),
                type = ActionEvent.Type.USER
            ),
            dd = ActionEvent.Dd()
        )
    }
}
