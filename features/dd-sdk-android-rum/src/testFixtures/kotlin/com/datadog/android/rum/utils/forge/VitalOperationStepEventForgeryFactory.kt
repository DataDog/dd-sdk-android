/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.VitalOperationStepEvent
import com.datadog.android.rum.model.VitalOperationStepEvent.FailureReason
import com.datadog.android.rum.model.VitalOperationStepEvent.StepType
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

class VitalOperationStepEventForgeryFactory : ForgeryFactory<VitalOperationStepEvent> {
    override fun getForgery(forge: Forge): VitalOperationStepEvent {
        return VitalOperationStepEvent(
            date = forge.aTimestamp(),
            application = VitalOperationStepEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = VitalOperationStepEvent.VitalOperationStepEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = VitalOperationStepEvent.VitalOperationStepEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable {
                aValueFrom(
                    VitalOperationStepEvent.VitalOperationStepEventSource::class.java
                )
            },
            ciTest = forge.aNullable {
                VitalOperationStepEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                VitalOperationStepEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString()
                )
            },
            device = forge.aNullable {
                VitalOperationStepEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aValueFrom(VitalOperationStepEvent.DeviceType::class.java),
                    architecture = forge.aString()
                )
            },
            context = forge.aNullable {
                VitalOperationStepEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = VitalOperationStepEvent.Dd(
                session = forge.aNullable { VitalOperationStepEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            ),
            ddtags = forge.aNullable { ddTagsString() },
            view = VitalOperationStepEvent.VitalOperationStepEventView(
                id = forge.getForgery<UUID>().toString(),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                name = forge.aNullable { anAlphabeticalString() }
            ),
            vital = VitalOperationStepEvent.Vital(
                id = forge.aString(),
                operationKey = forge.aNullable { aString() },
                name = forge.anAlphabeticalString(),
                stepType = forge.aValueFrom(StepType::class.java),
                failureReason = forge.aNullable { aValueFrom(FailureReason::class.java) }
            )
        )
    }
}
