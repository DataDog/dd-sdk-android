/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.VitalEvent
import com.datadog.android.rum.model.VitalEvent.FailureReason
import com.datadog.android.rum.model.VitalEvent.StepType
import com.datadog.android.rum.model.VitalEvent.VitalEventSessionType
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

class VitalEventForgeryFactory : ForgeryFactory<VitalEvent> {
    override fun getForgery(forge: Forge): VitalEvent {
        return VitalEvent(
            date = forge.aTimestamp(),
            application = VitalEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = VitalEvent.VitalEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = VitalEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable { aValueFrom(VitalEvent.VitalEventSource::class.java) },
            ciTest = forge.aNullable {
                VitalEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                VitalEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString()
                )
            },
            device = forge.aNullable {
                VitalEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aValueFrom(VitalEvent.DeviceType::class.java),
                    architecture = forge.aString()
                )
            },
            context = forge.aNullable {
                VitalEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = VitalEvent.Dd(
                session = forge.aNullable { VitalEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            ),
            ddtags = forge.aNullable { ddTagsString() },
            view = VitalEvent.VitalEventView(
                id = forge.getForgery<UUID>().toString(),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                name = forge.aNullable { anAlphabeticalString() }
            ),
            vital = VitalEvent.VitalEventVital(
                id = forge.aString(),
                type = forge.aValueFrom(VitalEvent.VitalEventVitalType::class.java),
                operationKey = forge.aNullable { aString() },
                name = forge.anAlphabeticalString(),
                stepType = forge.aValueFrom(StepType::class.java),
                failureReason = forge.aNullable { aValueFrom(FailureReason::class.java) }
            )
        )
    }
}
