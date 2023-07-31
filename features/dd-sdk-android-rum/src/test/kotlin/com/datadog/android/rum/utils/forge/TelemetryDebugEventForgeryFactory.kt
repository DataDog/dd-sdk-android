/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.telemetry.model.TelemetryDebugEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class TelemetryDebugEventForgeryFactory : ForgeryFactory<TelemetryDebugEvent> {

    override fun getForgery(forge: Forge): TelemetryDebugEvent {
        return TelemetryDebugEvent(
            date = forge.aTimestamp(),
            source = forge.aValueFrom(TelemetryDebugEvent.Source::class.java),
            service = forge.anAlphabeticalString(),
            version = forge.anAlphabeticalString(),
            application = forge.aNullable {
                TelemetryDebugEvent.Application(
                    forge.getForgery<UUID>().toString()
                )
            },
            session = forge.aNullable {
                TelemetryDebugEvent.Session(
                    forge.getForgery<UUID>().toString()
                )
            },
            view = forge.aNullable {
                TelemetryDebugEvent.View(
                    forge.getForgery<UUID>().toString()
                )
            },
            action = forge.aNullable {
                TelemetryDebugEvent.Action(
                    forge.getForgery<UUID>().toString()
                )
            },
            dd = TelemetryDebugEvent.Dd(),
            telemetry = TelemetryDebugEvent.Telemetry(
                message = forge.anAlphabeticalString()
            )
        )
    }
}
