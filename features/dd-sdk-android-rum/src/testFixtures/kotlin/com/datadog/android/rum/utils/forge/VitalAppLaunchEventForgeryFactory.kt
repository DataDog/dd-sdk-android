/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.RumVitalAppLaunchEvent
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

class VitalAppLaunchEventForgeryFactory : ForgeryFactory<RumVitalAppLaunchEvent> {
    override fun getForgery(forge: Forge): RumVitalAppLaunchEvent {
        return RumVitalAppLaunchEvent(
            date = forge.aTimestamp(),
            application = RumVitalAppLaunchEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = RumVitalAppLaunchEvent.RumVitalAppLaunchEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = RumVitalAppLaunchEvent.RumVitalAppLaunchEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable {
                aValueFrom(
                    RumVitalAppLaunchEvent.RumVitalAppLaunchEventSource::class.java
                )
            },
            ciTest = forge.aNullable {
                RumVitalAppLaunchEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                RumVitalAppLaunchEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString()
                )
            },
            device = forge.aNullable {
                RumVitalAppLaunchEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aValueFrom(RumVitalAppLaunchEvent.DeviceType::class.java),
                    architecture = forge.aString()
                )
            },
            context = forge.aNullable {
                RumVitalAppLaunchEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = RumVitalAppLaunchEvent.Dd(
                session = forge.aNullable { RumVitalAppLaunchEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            ),
            ddtags = forge.aNullable { ddTagsString() },
            view = forge.aNullable {
                RumVitalAppLaunchEvent.RumVitalAppLaunchEventView(
                    id = forge.getForgery<UUID>().toString(),
                    referrer = forge.aNullable { getForgery<URL>().toString() },
                    url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                    name = forge.aNullable { anAlphabeticalString() }
                )
            },
            vital = RumVitalAppLaunchEvent.Vital(
                id = forge.aString(),
                name = forge.aNullable { aString() },
                description = forge.aNullable { aString() },
                appLaunchMetric = forge.aValueFrom(RumVitalAppLaunchEvent.AppLaunchMetric::class.java),
                duration = forge.aLong(),
                startupType = forge.aValueFrom(RumVitalAppLaunchEvent.StartupType::class.java),
                isPrewarmed = forge.aNullable { aBool() },
                hasSavedInstanceStateBundle = forge.aNullable { aBool() }
            )
        )
    }
}
