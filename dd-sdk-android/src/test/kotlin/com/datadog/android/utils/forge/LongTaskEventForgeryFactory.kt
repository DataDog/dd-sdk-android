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
import java.net.URL
import java.util.UUID

internal class LongTaskEventForgeryFactory :
    ForgeryFactory<LongTaskEvent> {
    override fun getForgery(forge: Forge): LongTaskEvent {
        return LongTaskEvent(
            date = forge.aTimestamp(),
            longTask = LongTaskEvent.LongTask(
                id = forge.aNullable { getForgery<UUID>().toString() },
                duration = forge.aPositiveLong(),
                isFrozenFrame = forge.aNullable { aBool() }
            ),
            view = LongTaskEvent.View(
                id = forge.getForgery<UUID>().toString(),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                name = forge.aNullable { anAlphabeticalString() }
            ),
            connectivity = forge.aNullable {
                LongTaskEvent.Connectivity(
                    status = getForgery(),
                    interfaces = aList { getForgery() },
                    cellular = aNullable {
                        LongTaskEvent.Cellular(
                            technology = aNullable { anAlphabeticalString() },
                            carrierName = aNullable { anAlphabeticalString() }
                        )
                    }
                )
            },
            synthetics = forge.aNullable {
                LongTaskEvent.Synthetics(
                    testId = forge.anHexadecimalString(),
                    resultId = forge.anHexadecimalString()
                )
            },
            usr = forge.aNullable {
                LongTaskEvent.Usr(
                    id = aNullable { anHexadecimalString() },
                    name = aNullable { aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
                    email = aNullable { aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}") },
                    additionalProperties = exhaustiveAttributes()
                )
            },
            action = forge.aNullable { LongTaskEvent.Action(getForgery<UUID>().toString()) },
            application = LongTaskEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = LongTaskEvent.LongTaskEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = LongTaskEvent.Type.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable { aValueFrom(LongTaskEvent.Source::class.java) },
            context = forge.aNullable {
                LongTaskEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = LongTaskEvent.Dd(
                session = forge.aNullable { LongTaskEvent.DdSession(getForgery()) }
            )
        )
    }
}
