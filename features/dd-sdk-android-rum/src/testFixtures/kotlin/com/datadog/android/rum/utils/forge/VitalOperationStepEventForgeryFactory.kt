/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.RumVitalOperationStepEvent
import com.datadog.android.rum.model.RumVitalOperationStepEvent.FailureReason
import com.datadog.android.rum.model.RumVitalOperationStepEvent.StepType
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

class VitalOperationStepEventForgeryFactory : ForgeryFactory<RumVitalOperationStepEvent> {
    override fun getForgery(forge: Forge): RumVitalOperationStepEvent {
        return RumVitalOperationStepEvent(
            date = forge.aTimestamp(),
            application = RumVitalOperationStepEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = RumVitalOperationStepEvent.RumVitalOperationStepEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = RumVitalOperationStepEvent.RumVitalOperationStepEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable {
                aValueFrom(
                    RumVitalOperationStepEvent.RumVitalOperationStepEventSource::class.java
                )
            },
            ciTest = forge.aNullable {
                RumVitalOperationStepEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                RumVitalOperationStepEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString()
                )
            },
            device = forge.aNullable {
                RumVitalOperationStepEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aValueFrom(RumVitalOperationStepEvent.DeviceType::class.java),
                    architecture = forge.aString()
                )
            },
            context = forge.aNullable {
                RumVitalOperationStepEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = RumVitalOperationStepEvent.Dd(
                session = forge.aNullable { RumVitalOperationStepEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            ),
            ddtags = forge.aNullable { ddTagsString() },
            view = RumVitalOperationStepEvent.RumVitalOperationStepEventView(
                id = forge.getForgery<UUID>().toString(),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                name = forge.aNullable { anAlphabeticalString() }
            ),
            vital = RumVitalOperationStepEvent.Vital(
                id = forge.aString(),
                operationKey = forge.aNullable { aString() },
                name = forge.anAlphabeticalString(),
                stepType = forge.aValueFrom(StepType::class.java),
                failureReason = forge.aNullable { aValueFrom(FailureReason::class.java) }
            )
        )
    }
}
