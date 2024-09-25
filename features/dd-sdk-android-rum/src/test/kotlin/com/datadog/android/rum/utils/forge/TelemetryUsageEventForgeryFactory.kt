/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.telemetry.model.TelemetryUsageEvent
import com.datadog.android.tests.elmyr.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TelemetryUsageEventForgeryFactory : ForgeryFactory<TelemetryUsageEvent> {
    override fun getForgery(forge: Forge): TelemetryUsageEvent {
        return TelemetryUsageEvent(
            dd = TelemetryUsageEvent.Dd(),
            date = forge.aPositiveLong(),
            service = forge.anAlphabeticalString(),
            source = forge.aValueFrom(TelemetryUsageEvent.Source::class.java),
            version = forge.anAlphabeticalString(),
            application = forge.aNullable {
                TelemetryUsageEvent.Application(
                    id = anAlphabeticalString()
                )
            },
            session = forge.aNullable {
                TelemetryUsageEvent.Session(
                    id = anAlphabeticalString()
                )
            },
            view = forge.aNullable {
                TelemetryUsageEvent.View(
                    id = anAlphabeticalString()
                )
            },
            action = forge.aNullable {
                TelemetryUsageEvent.Action(
                    id = anAlphabeticalString()
                )
            },
            experimentalFeatures = forge.aNullable { aList { anAlphabeticalString() } },
            telemetry = TelemetryUsageEvent.Telemetry(
                device = forge.aNullable {
                    TelemetryUsageEvent.Device(
                        architecture = anAlphabeticalString(),
                        brand = anAlphabeticalString(),
                        model = anAlphabeticalString()
                    )
                },
                os = forge.aNullable {
                    TelemetryUsageEvent.Os(
                        build = anAlphabeticalString(),
                        name = anAlphabeticalString(),
                        version = anAlphabeticalString()
                    )
                },
                usage = TelemetryUsageEvent.Usage.AddViewLoadingTime(
                    noView = forge.aBool(),
                    noActiveView = forge.aBool(),
                    overwritten = forge.aBool()
                ),
                additionalProperties = forge.exhaustiveAttributes()
            )
        )
    }
}
