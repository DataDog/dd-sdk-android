/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.RumVitalEvent
import com.datadog.android.rum.model.RumVitalEvent.FailureReason
import com.datadog.android.rum.model.RumVitalEvent.RumVitalEventSessionType
import com.datadog.android.rum.model.RumVitalEvent.RumVitalEventVitalType
import com.datadog.android.rum.model.RumVitalEvent.StepType
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

class RumVitalEventForgeryFactory : ForgeryFactory<RumVitalEvent> {
    override fun getForgery(forge: Forge): RumVitalEvent {
        return RumVitalEvent(
            date = forge.aTimestamp(),
            application = RumVitalEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = RumVitalEvent.RumVitalEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = RumVitalEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable { aValueFrom(RumVitalEvent.RumVitalEventSource::class.java) },
            ciTest = forge.aNullable {
                RumVitalEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                RumVitalEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString()
                )
            },
            device = forge.aNullable {
                RumVitalEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aValueFrom(RumVitalEvent.DeviceType::class.java),
                    architecture = forge.aString()
                )
            },
            context = forge.aNullable {
                RumVitalEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = RumVitalEvent.Dd(
                session = forge.aNullable { RumVitalEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            ),
            ddtags = forge.aNullable { ddTagsString() },
            view = RumVitalEvent.RumVitalEventView(
                id = forge.getForgery<UUID>().toString(),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                name = forge.aNullable { anAlphabeticalString() }
            ),
            vital = RumVitalEvent.RumVitalEventVital(
                id = forge.aString(),
                type = forge.aValueFrom(RumVitalEventVitalType::class.java),
                operationKey = forge.aNullable { aString() },
                name = forge.anAlphabeticalString(),
                stepType = forge.aValueFrom(StepType::class.java),
                failureReason = forge.aNullable { aValueFrom(FailureReason::class.java) }
            )
        )
    }
}
