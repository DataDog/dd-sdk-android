/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

class ViewUpdateEventForgeryFactory : ForgeryFactory<ViewUpdateEvent> {

    override fun getForgery(forge: Forge): ViewUpdateEvent {
        return ViewUpdateEvent(
            date = forge.aTimestamp(),
            view = ViewUpdateEvent.ViewUpdateEventView(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                customTimings = forge.aNullable {
                    ViewUpdateEvent.CustomTimings(
                        aMap { anAlphabeticalString() to aPositiveLong() }.toMutableMap()
                    )
                },
                isActive = forge.aNullable { aBool() },
                isSlowRendered = forge.aNullable { aBool() }
            ),
            session = ViewUpdateEvent.ViewUpdateEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = forge.aValueFrom(ViewUpdateEvent.ViewUpdateEventSessionType::class.java)
            ),
            application = ViewUpdateEvent.Application(
                id = forge.getForgery<UUID>().toString()
            ),
            usr = forge.aNullable {
                ViewUpdateEvent.Usr(
                    id = aNullable { anHexadecimalString() },
                    name = aNullable { aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
                    email = aNullable { aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}") },
                    additionalProperties = exhaustiveAttributes(excludedKeys = setOf("id", "name", "email"))
                )
            },
            account = forge.aNullable {
                ViewUpdateEvent.Account(
                    id = anHexadecimalString(),
                    name = aNullable { aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
                    additionalProperties = exhaustiveAttributes(excludedKeys = setOf("id", "name"))
                )
            },
            connectivity = forge.aNullable {
                ViewUpdateEvent.Connectivity(
                    status = getForgery(),
                    interfaces = aNullable { aList { getForgery() } },
                    cellular = aNullable {
                        ViewUpdateEvent.Cellular(
                            technology = aNullable { anAlphabeticalString() },
                            carrierName = aNullable { anAlphabeticalString() }
                        )
                    }
                )
            },
            os = forge.aNullable {
                ViewUpdateEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString(),
                    build = forge.aNullable { anAlphabeticalString() }
                )
            },
            device = forge.aNullable {
                ViewUpdateEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aNullable { aValueFrom(ViewUpdateEvent.DeviceType::class.java) },
                    architecture = forge.aNullable { aString() },
                    locale = forge.aNullable { anAlphabeticalString() },
                    locales = forge.aNullable { aList { anAlphabeticalString() } },
                    timeZone = forge.aNullable { anAlphabeticalString() },
                    batteryLevel = forge.aNullable { aDouble(min = 0.0, max = 100.0) },
                    powerSavingMode = forge.aNullable { aBool() },
                    brightnessLevel = forge.aNullable { aDouble(min = 0.0, max = 1.0) }
                )
            },
            context = forge.aNullable {
                ViewUpdateEvent.FeatureFlags(
                    additionalProperties = exhaustiveAttributes()
                )
            },
            dd = ViewUpdateEvent.Dd(
                documentVersion = forge.aPositiveLong(strict = true)
            )
        )
    }
}
