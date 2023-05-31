/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class TelemetryConfigurationEventForgeryFactory :
    ForgeryFactory<TelemetryConfigurationEvent> {

    override fun getForgery(forge: Forge): TelemetryConfigurationEvent {
        return TelemetryConfigurationEvent(
            date = forge.aTimestamp(),
            source = forge.aValueFrom(TelemetryConfigurationEvent.Source::class.java),
            service = forge.anAlphabeticalString(),
            version = forge.anAlphabeticalString(),
            application = forge.aNullable {
                TelemetryConfigurationEvent.Application(
                    forge.getForgery<UUID>().toString()
                )
            },
            session = forge.aNullable {
                TelemetryConfigurationEvent.Session(
                    forge.getForgery<UUID>().toString()
                )
            },
            view = forge.aNullable {
                TelemetryConfigurationEvent.View(
                    forge.getForgery<UUID>().toString()
                )
            },
            action = forge.aNullable {
                TelemetryConfigurationEvent.Action(
                    forge.getForgery<UUID>().toString()
                )
            },
            dd = TelemetryConfigurationEvent.Dd(),
            telemetry = TelemetryConfigurationEvent.Telemetry(
                configuration = TelemetryConfigurationEvent.Configuration(
                    forge.aNullable { aLong() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aString() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable {
                        aList {
                            aValueFrom(TelemetryConfigurationEvent.SelectedTracingPropagator::class.java)
                        }
                    },
                    forge.aNullable { aString() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aList { aString() } },
                    forge.aNullable { aList { aString() } },
                    forge.aNullable { aBool() },
                    forge.aNullable { aValueFrom(TelemetryConfigurationEvent.ViewTrackingStrategy::class.java) },
                    forge.aNullable { aBool() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aString() },
                    forge.aNullable { aBool() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aLong() },
                    forge.aNullable { aString() },
                    forge.aNullable { aString() },
                    forge.aNullable { aString() }
                )
            )
        )
    }
}
