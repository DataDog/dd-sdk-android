/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.VitalAppLaunchEvent
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

class VitalAppLaunchEventForgeryFactory : ForgeryFactory<VitalAppLaunchEvent> {
    override fun getForgery(forge: Forge): VitalAppLaunchEvent {
        return VitalAppLaunchEvent(
            date = forge.aTimestamp(),
            application = VitalAppLaunchEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = VitalAppLaunchEvent.VitalAppLaunchEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = VitalAppLaunchEvent.VitalAppLaunchEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable {
                aValueFrom(
                    VitalAppLaunchEvent.VitalAppLaunchEventSource::class.java
                )
            },
            ciTest = forge.aNullable {
                VitalAppLaunchEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                VitalAppLaunchEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString()
                )
            },
            device = forge.aNullable {
                VitalAppLaunchEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aValueFrom(VitalAppLaunchEvent.DeviceType::class.java),
                    architecture = forge.aString(),
                    isLowRam = forge.aNullable { aBool() },
                    logicalCpuCount = forge.aNullable { anInt() },
                    totalRam = forge.aNullable { anInt() }
                )
            },
            context = forge.aNullable {
                VitalAppLaunchEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = VitalAppLaunchEvent.Dd(
                session = forge.aNullable { VitalAppLaunchEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            ),
            ddtags = forge.aNullable { ddTagsString() },
            view = forge.aNullable {
                VitalAppLaunchEvent.VitalAppLaunchEventView(
                    id = forge.getForgery<UUID>().toString(),
                    referrer = forge.aNullable { getForgery<URL>().toString() },
                    url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                    name = forge.aNullable { anAlphabeticalString() }
                )
            },
            vital = VitalAppLaunchEvent.Vital(
                id = forge.aString(),
                name = forge.aNullable { aString() },
                description = forge.aNullable { aString() },
                appLaunchMetric = forge.aValueFrom(VitalAppLaunchEvent.AppLaunchMetric::class.java),
                duration = forge.aLong(),
                startupType = forge.aValueFrom(VitalAppLaunchEvent.StartupType::class.java),
                isPrewarmed = forge.aNullable { aBool() },
                hasSavedInstanceStateBundle = forge.aNullable { aBool() }
            )
        )
    }
}
